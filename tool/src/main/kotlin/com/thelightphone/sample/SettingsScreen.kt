package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsField { None, ServerUrl, Password }

data class SettingsUiState(
    val serverUrl: String = "",
    val hasPassword: Boolean = false,
    val editing: SettingsField = SettingsField.None,
    val status: String? = null,
)

class SettingsViewModel(
    dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {

    private val session = ServerSession(dataStore, viewModelScope)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            val credentials = session.awaitCredentials()
            _uiState.update {
                it.copy(
                    serverUrl = credentials.serverUrl,
                    hasPassword = credentials.password.isNotBlank(),
                )
            }
        }
    }

    override fun onBackPressed(): Boolean {
        if (_uiState.value.editing != SettingsField.None) {
            cancelEditing()
            return true
        }
        return false
    }

    fun edit(field: SettingsField) {
        _uiState.update { it.copy(editing = field, status = null) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(editing = SettingsField.None) }
    }

    fun submit(value: String) {
        val field = _uiState.value.editing
        viewModelScope.launch {
            when (field) {
                SettingsField.ServerUrl -> {
                    session.settings.setServerUrl(value)
                    _uiState.update { it.copy(serverUrl = value.trim()) }
                }

                SettingsField.Password -> {
                    session.settings.setPassword(value)
                    _uiState.update { it.copy(hasPassword = value.isNotBlank()) }
                }

                SettingsField.None -> Unit
            }
            _uiState.update { it.copy(editing = SettingsField.None) }
        }
    }

    fun testConnection() {
        _uiState.update { it.copy(status = "Contacting server…") }
        viewModelScope.launch {
            val credentials = session.awaitCredentials()
            if (!credentials.isConfigured) {
                _uiState.update { it.copy(status = NOT_CONFIGURED_MESSAGE) }
                return@launch
            }
            session.client.ping()
                .onSuccess { info -> _uiState.update { it.copy(status = info) } }
                .onFailure { error -> _uiState.update { it.copy(status = error.userMessage()) } }
        }
    }

    override fun onCleared() {
        super.onCleared()
        session.close()
    }
}

class SettingsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SettingsViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsViewModel>
        get() = SettingsViewModel::class.java

    override fun createViewModel() = SettingsViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val fieldState = rememberTextFieldState("")
        val keyboardOptions = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                if (state.editing != SettingsField.None) {
                    LightTextInputEditor(
                        title = if (state.editing == SettingsField.ServerUrl) "Server URL" else "Password",
                        state = fieldState,
                        editorKey = state.editing,
                        keyboardOptionsFlow = keyboardOptions,
                        singleLine = true,
                        onSubmit = { viewModel.submit(it.toString()) },
                        onBack = viewModel::cancelEditing,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SettingsContent(state)
                }
            }
        }
    }

    @Composable
    private fun SettingsContent(state: SettingsUiState) {
        Column(modifier = Modifier.fillMaxSize()) {
            LightTopBar(
                leftButton = LightBarButton.LightIcon(
                    icon = LightIcons.BACK,
                    onClick = { goBack() },
                ),
                center = LightTopBarCenter.Text("Settings"),
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )

            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                SettingsRow(
                    label = "Server URL",
                    value = state.serverUrl.ifBlank { "Not set" },
                    onClick = { viewModel.edit(SettingsField.ServerUrl) },
                )
                SettingsRow(
                    label = "Password",
                    value = if (state.hasPassword) "••••••" else "Not set",
                    onClick = { viewModel.edit(SettingsField.Password) },
                )
                state.status?.let {
                    LightText(
                        text = it,
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                    )
                }
            }

            LightBottomBar(
                items = listOf(
                    LightBarButton.Text(
                        text = "TEST CONNECTION",
                        onClick = viewModel::testConnection,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.75f.gridUnitsAsDp()),
    ) {
        LightText(text = label, variant = LightTextVariant.Detail, lighten = true)
        LightText(text = value, variant = LightTextVariant.Copy)
    }
}
