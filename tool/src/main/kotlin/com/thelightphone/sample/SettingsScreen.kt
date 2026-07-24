package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {

    private val _initialKey = MutableStateFlow<String?>(null)
    val initialKey: StateFlow<String?> = _initialKey

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch(Dispatchers.IO) {
            val stored = dataStore.data.first()[AskClaudePreferences.ANTHROPIC_API_KEY]
            _initialKey.value = stored ?: ""
        }
    }

    fun save(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[AskClaudePreferences.ANTHROPIC_API_KEY] = key.trim()
            }
        }
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
        val initialKey by viewModel.initialKey.collectAsState()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                initialKey?.let { existing ->
                    val textState = rememberTextFieldState(existing)
                    val keyboardOptionsFlow = rememberKeyboardOptions()
                    LightTextInputEditor(
                        title = "Anthropic API Key",
                        state = textState,
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        singleLine = true,
                        submitLabel = "SAVE",
                        submitIcon = LightIcons.TOGGLE_STATE_ON,
                        onSubmit = { result ->
                            viewModel.save(result.toString())
                            goBack()
                        },
                        onBack = { goBack() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
