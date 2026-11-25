package com.thelightphone.hue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@InitialScreen
class HueHomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HueViewModel>(sealedActivity) {

    override val viewModelClass: Class<HueViewModel>
        get() = HueViewModel::class.java

    override fun createViewModel(): HueViewModel = HueViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        var confirmForget by remember { mutableStateOf(false) }

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                val error = state.errorModal
                if (error != null) {
                    LightFullscreenModal(message = error, onClose = viewModel::dismissError)
                    return@Box
                }
                if (confirmForget && state.mode is HueScreenMode.Lights) {
                    ForgetBridgeConfirmation(
                        onCancel = { confirmForget = false },
                        onForget = {
                            confirmForget = false
                            viewModel.forgetBridge()
                        },
                    )
                    return@Box
                }
                when (val mode = state.mode) {
                    is HueScreenMode.Loading ->
                        StatusScreen(title = "Hue", message = "Loading…")

                    is HueScreenMode.Discovering ->
                        StatusScreen(title = "Hue", message = "Looking for your bridge…")

                    is HueScreenMode.BridgePicker ->
                        BridgePickerContent(
                            bridges = mode.bridges,
                            onSelect = viewModel::selectBridge,
                            onEnterIp = viewModel::openManualIp,
                            onBack = { goBack() },
                        )

                    is HueScreenMode.ManualIp ->
                        LaunchedEffect(state.manualIpSession) {
                            navigateTo({ ManualIpScreen(it, viewModel) })
                        }

                    is HueScreenMode.Pairing ->
                        PairingContent(
                            message = mode.message,
                            onPair = viewModel::pair,
                            onBack = viewModel::startDiscovery,
                        )

                    is HueScreenMode.Lights ->
                        LightsContent(
                            bridgeName = mode.bridgeName,
                            lights = mode.lights,
                            onToggle = viewModel::toggleLight,
                            onAllOff = viewModel::turnAllOff,
                            onAllOn = viewModel::turnAllOn,
                            onRefresh = viewModel::loadLights,
                            onForget = { confirmForget = true },
                        )
                }
            }
        }
    }
}

private class ManualIpScreen(
    sealedActivity: SealedLightActivity,
    private val viewModel: HueViewModel,
) : SimpleLightScreen<Unit>(sealedActivity) {
    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val uiState by viewModel.uiState.collectAsState()
        val textFieldState = rememberTextFieldState("")
        val keyboardOptionsFlow = rememberKeyboardOptions()
        LightTheme(colors = themeColors) {
            val error = uiState.errorModal
            if (error != null) {
                LightFullscreenModal(message = error, onClose = viewModel::dismissError)
            } else {
                LightTextInputEditor(
                    title = "Bridge IP address",
                    keyboardOptionsFlow = keyboardOptionsFlow,
                    state = textFieldState,
                    onSubmit = { if (viewModel.submitManualIp(it)) goBack() },
                    onBack = { goBack() },
                    submitIcon = LightIcons.ACCEPT,
                    singleLine = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onScreenDestroy() {
        viewModel.dismissError()
        viewModel.closeManualIp()
    }
}

@Composable
private fun ForgetBridgeConfirmation(onCancel: () -> Unit, onForget: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = "Forget this bridge? You'll need to pair it again.",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
            )
        }
        LightBottomBar(
            items = listOf(
                LightBarButton.Text(text = "CANCEL", onClick = onCancel),
                LightBarButton.Text(text = "FORGET", onClick = onForget),
            ),
        )
    }
}

@Composable
private fun StatusScreen(title: String, message: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text(title),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(text = message, variant = LightTextVariant.Copy, align = TextAlign.Center)
        }
    }
}

@Composable
private fun BridgePickerContent(
    bridges: List<DiscoveredBridge>,
    onSelect: (DiscoveredBridge) -> Unit,
    onEnterIp: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(if (bridges.isEmpty()) "Pair bridge" else "Choose bridge"),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )
        LightScrollView(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 1f.gridUnitsAsDp()),
        ) {
            if (bridges.isEmpty()) {
                LightText(text = "No bridge found. Enter its IP address to pair.", variant = LightTextVariant.Copy)
            }
            bridges.forEach { bridge ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable { onSelect(bridge) }
                        .padding(bottom = 1f.gridUnitsAsDp()),
                ) {
                    LightText(text = bridge.internalipaddress, variant = LightTextVariant.Copy)
                    if (bridge.id.isNotBlank()) {
                        LightText(text = bridge.id, variant = LightTextVariant.Detail, lighten = true)
                    }
                }
            }
        }
        LightBottomBar(
            items = listOf(
                null,
                LightBarButton.Text(text = "ENTER IP", onClick = onEnterIp),
                null,
            ),
        )
    }
}

@Composable
private fun PairingContent(
    message: String,
    onPair: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text("Pair bridge"),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(text = message, variant = LightTextVariant.Copy, align = TextAlign.Center)
        }
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
                LightBarButton.Text(text = "PAIR", onClick = onPair),
                null,
            ),
        )
    }
}

@Composable
private fun LightsContent(
    bridgeName: String,
    lights: List<UiLight>,
    onToggle: (UiLight) -> Unit,
    onAllOff: () -> Unit,
    onAllOn: () -> Unit,
    onRefresh: () -> Unit,
    onForget: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text(bridgeName),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )
        LightScrollView(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = "Turn everything off",
                variant = LightTextVariant.Heading,
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable(onClick = onAllOff)
                    .padding(vertical = 0.5f.gridUnitsAsDp()),
            )
            LightText(
                text = "Turn everything on",
                variant = LightTextVariant.Copy,
                lighten = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable(onClick = onAllOn)
                    .padding(bottom = 1f.gridUnitsAsDp()),
            )

            if (lights.isEmpty()) {
                LightText(
                    text = "No lights found on this bridge.",
                    variant = LightTextVariant.Copy,
                    lighten = true,
                )
            } else {
                lights.forEach { light ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable { onToggle(light) }
                            .padding(vertical = 0.5f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        LightText(
                            text = light.name,
                            variant = LightTextVariant.Copy,
                            lighten = !light.reachable,
                            modifier = Modifier.weight(1f).padding(end = 1f.gridUnitsAsDp()),
                        )
                        LightIcon(
                            icon = if (light.on) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
                            contentDescription = if (light.on) "On" else "Off",
                        )
                    }
                }
            }
        }
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.SETTINGS,
                    onClick = onForget,
                    contentDescription = "Forget bridge",
                ),
                LightBarButton.Text(text = "ALL OFF", onClick = onAllOff),
                LightBarButton.LightIcon(
                    icon = LightIcons.REFRESH,
                    onClick = onRefresh,
                    contentDescription = "Refresh",
                ),
            ),
        )
    }
}
