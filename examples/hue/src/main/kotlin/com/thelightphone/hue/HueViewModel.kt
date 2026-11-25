package com.thelightphone.hue

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class HueScreenMode {
    data object Loading : HueScreenMode()
    data object Discovering : HueScreenMode()
    data class BridgePicker(val bridges: List<DiscoveredBridge>) : HueScreenMode()
    data object ManualIp : HueScreenMode()
    data class Pairing(val ip: String, val message: String, val discoveredId: String?) : HueScreenMode()
    data class Lights(
        val bridgeName: String,
        val lights: List<UiLight>,
        val busy: Boolean = false,
    ) : HueScreenMode()
}

data class HueUiState(
    val mode: HueScreenMode = HueScreenMode.Loading,
    val manualIpSession: Int = 0,
    val errorModal: String? = null,
)

private const val NETWORK_ERROR =
    "The Hue tool needs to reach your bridge over Wi-Fi. Connect to the same network as your Hue bridge and try again."

class HueViewModel(
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {
    private val api = HueApi()

    private val _uiState = MutableStateFlow(HueUiState())
    val uiState: StateFlow<HueUiState> = _uiState.asStateFlow()

    private val savedBridge = MutableStateFlow<SavedBridge?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = dataStore.data.first()
            val ip = prefs[HuePreferences.BRIDGE_IP]
            val key = prefs[HuePreferences.APP_KEY]
            if (ip.isNullOrBlank() || key.isNullOrBlank()) {
                startDiscovery()
            } else {
                savedBridge.value = SavedBridge(ip, prefs[HuePreferences.BRIDGE_ID].orEmpty(), key)
                loadLights()
            }
        }
    }

    private suspend fun setMode(mode: HueScreenMode) = withContext(Dispatchers.Main) {
        _uiState.update { it.copy(mode = mode) }
    }

    private suspend fun showError(message: String) = withContext(Dispatchers.Main) {
        _uiState.update { it.copy(errorModal = message) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorModal = null) }
    }

    fun startDiscovery() {
        viewModelScope.launch(Dispatchers.IO) {
            setMode(HueScreenMode.Discovering)
            api.discoverBridges().fold(
                onSuccess = { bridges ->
                    if (bridges.isEmpty()) {
                        openManualIp()
                    } else {
                        setMode(HueScreenMode.BridgePicker(bridges))
                    }
                },
                onFailure = { openManualIp() },
            )
        }
    }

    fun selectBridge(bridge: DiscoveredBridge) {
        beginPairing(bridge.internalipaddress, bridge.id.ifBlank { null })
    }

    fun openManualIp() {
        _uiState.update {
            it.copy(mode = HueScreenMode.ManualIp, manualIpSession = it.manualIpSession + 1)
        }
    }

    fun submitManualIp(raw: CharSequence) {
        val ip = raw.toString().trim()
        if (!isValidHost(ip)) {
            _uiState.update { it.copy(errorModal = "Enter a valid IP address, e.g. 192.168.1.42") }
            return
        }
        beginPairing(ip)
    }

    private fun beginPairing(ip: String, discoveredId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            setMode(
                HueScreenMode.Pairing(
                    ip = ip,
                    message = "Press the link button on top of your Hue bridge, then tap Pair.",
                    discoveredId = discoveredId,
                ),
            )
        }
    }

    fun pair() {
        val mode = _uiState.value.mode as? HueScreenMode.Pairing ?: return
        val ip = mode.ip
        viewModelScope.launch(Dispatchers.IO) {
            setMode(mode.copy(message = "Pairing…"))
            api.pair(ip, mode.discoveredId).fold(
                onSuccess = { (bridgeId, created) ->
                    try {
                        savedBridge.value = dataStore.saveHueBridge(SavedBridge(ip, bridgeId, created.username))
                    } catch (_: IOException) {
                        setMode(mode)
                        showError("Couldn't save this pairing. Press the bridge's link button and try again.")
                        return@fold
                    }
                    loadLights()
                },
                onFailure = { error ->
                    when (error) {
                        is LinkButtonNotPressedException -> setMode(
                            mode.copy(
                                message = "Link button not detected yet. Press it on your bridge, then tap Pair.",
                            ),
                        )
                        else -> {
                            setMode(mode.copy(message = "Press the link button, then tap Pair."))
                            showError(error.friendlyMessage())
                        }
                    }
                },
            )
        }
    }

    fun loadLights() {
        val bridge = savedBridge.value
        if (bridge == null) {
            startDiscovery()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (!isHueBridgeId(bridge.id)) {
                setMode(HueScreenMode.Lights(bridgeName = "Hue", lights = emptyList()))
                showError("This saved pairing has no verified bridge ID. Use the gear to forget it and pair again.")
                return@launch
            }
            val current = _uiState.value.mode
            if (current !is HueScreenMode.Lights) setMode(HueScreenMode.Loading)
            val (ip, result) = requestWithBridgeRediscovery(
                bridge.ip, bridge.id, api::discoverBridges,
            ) { api.listLights(it, bridge.id, bridge.key) }
            if (savedBridge.value != bridge) return@launch
            result.fold(
                onSuccess = { lights ->
                    val name = api.fetchConfig(ip, bridge.id).getOrNull()?.name?.ifBlank { null } ?: "Hue"
                    if (savedBridge.value != bridge) return@fold
                    if (ip != bridge.ip && !updateBridgeIp(bridge, ip)) return@fold
                    setMode(HueScreenMode.Lights(bridgeName = name, lights = lights))
                },
                onFailure = { error ->
                    if (error is UnauthorizedException) {
                        forgetBridge()
                    } else {
                        val name = (current as? HueScreenMode.Lights)?.bridgeName ?: "Hue"
                        setMode(HueScreenMode.Lights(bridgeName = name, lights = emptyList()))
                        showError(error.friendlyMessage())
                    }
                },
            )
        }
    }

    fun toggleLight(light: UiLight) {
        val bridge = savedBridge.value?.takeIf { isHueBridgeId(it.id) } ?: return
        val target = !light.on
        updateLightsMode { it.copy(lights = it.lights.replacing(light.id, target), busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            api.setLightOn(bridge.ip, bridge.id, bridge.key, light.id, target).fold(
                onSuccess = { loadLights() },
                onFailure = { error -> onCommandFailure(error) },
            )
        }
    }

    fun turnAllOff() = setAll(false)

    fun turnAllOn() = setAll(true)

    private fun setAll(on: Boolean) {
        val bridge = savedBridge.value?.takeIf { isHueBridgeId(it.id) } ?: return
        updateLightsMode { mode ->
            mode.copy(lights = mode.lights.map { it.copy(on = on) }, busy = true)
        }
        viewModelScope.launch(Dispatchers.IO) {
            api.setAllLightsOn(bridge.ip, bridge.id, bridge.key, on).fold(
                onSuccess = { loadLights() },
                onFailure = { error -> onCommandFailure(error) },
            )
        }
    }

    fun forgetBridge() {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs.remove(HuePreferences.BRIDGE_IP)
                prefs.remove(HuePreferences.BRIDGE_ID)
                prefs.remove(HuePreferences.APP_KEY)
            }
            savedBridge.value = null
            startDiscovery()
        }
    }

    fun cancelToLights() {
        if (savedBridge.value == null) startDiscovery() else loadLights()
    }

    private suspend fun onCommandFailure(error: Throwable) {
        if (error is UnauthorizedException) {
            forgetBridge()
            return
        }
        showError(error.friendlyMessage())
        loadLights()
    }

    private fun updateLightsMode(transform: (HueScreenMode.Lights) -> HueScreenMode.Lights) {
        _uiState.update { state ->
            val mode = state.mode as? HueScreenMode.Lights ?: return@update state
            state.copy(mode = transform(mode))
        }
    }

    private suspend fun updateBridgeIp(bridge: SavedBridge, ip: String): Boolean {
        runCatching {
            dataStore.edit { prefs ->
                if (
                    prefs[HuePreferences.BRIDGE_IP] == bridge.ip &&
                    prefs[HuePreferences.BRIDGE_ID] == bridge.id &&
                    prefs[HuePreferences.APP_KEY] == bridge.key
                ) {
                    prefs[HuePreferences.BRIDGE_IP] = ip
                }
            }
        }
        return savedBridge.compareAndSet(bridge, bridge.copy(ip = ip))
    }

    override fun onCleared() {
        super.onCleared()
        api.close()
    }
}

private fun List<UiLight>.replacing(id: String, on: Boolean): List<UiLight> =
    map { if (it.id == id) it.copy(on = on) else it }

private fun Throwable.friendlyMessage(): String = when (this) {
    is UnauthorizedException -> message ?: "The bridge no longer recognizes this tool."
    is IllegalStateException -> message ?: NETWORK_ERROR
    else -> NETWORK_ERROR
}

internal fun isValidHost(value: String): Boolean {
    if (value.isBlank()) return false
    val parts = value.split(".")
    if (parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }) {
        return true
    }
    // Allow hostnames too (e.g. a static DNS name for the bridge).
    return value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9.-]*$"))
}
