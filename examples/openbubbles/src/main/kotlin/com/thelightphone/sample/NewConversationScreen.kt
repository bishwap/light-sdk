package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NewConversationStep {
    data object Address : NewConversationStep
    data class Body(val address: String) : NewConversationStep
    data object Sending : NewConversationStep
    data class Failed(val message: String) : NewConversationStep
    data class Sent(val address: String) : NewConversationStep
}

data class NewConversationUiState(
    val step: NewConversationStep = NewConversationStep.Address,
    val editorSession: Int = 0,
)

class NewConversationViewModel(
    dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {

    private val session = ServerSession(dataStore, viewModelScope)

    private val _uiState = MutableStateFlow(NewConversationUiState())
    val uiState: StateFlow<NewConversationUiState> = _uiState.asStateFlow()

    fun submitAddress(address: String) {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return
        _uiState.update {
            it.copy(step = NewConversationStep.Body(trimmed), editorSession = it.editorSession + 1)
        }
    }

    fun submitBody(body: String) {
        val step = _uiState.value.step
        if (step !is NewConversationStep.Body) return
        val text = body.trim()
        if (text.isEmpty()) return
        _uiState.update { it.copy(step = NewConversationStep.Sending) }
        viewModelScope.launch {
            if (!session.awaitCredentials().isConfigured) {
                _uiState.update { state ->
                    state.copy(step = NewConversationStep.Failed(NOT_CONFIGURED_MESSAGE))
                }
                return@launch
            }
            session.client.startChat(step.address, text)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(step = NewConversationStep.Sent(step.address))
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(step = NewConversationStep.Failed(error.userMessage()))
                    }
                }
        }
    }

    fun backToAddress() {
        _uiState.update {
            it.copy(step = NewConversationStep.Address, editorSession = it.editorSession + 1)
        }
    }

    override fun onCleared() {
        super.onCleared()
        session.close()
    }
}

class NewConversationScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, NewConversationViewModel>(sealedActivity) {

    override val viewModelClass: Class<NewConversationViewModel>
        get() = NewConversationViewModel::class.java

    override fun createViewModel() = NewConversationViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val editorState = remember(state.editorSession) { TextFieldState("") }
        val keyboardOptions = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
                contentAlignment = Alignment.Center,
            ) {
                when (val step = state.step) {
                    is NewConversationStep.Address -> LightTextInputEditor(
                        title = "To",
                        state = editorState,
                        editorKey = "address-${state.editorSession}",
                        keyboardOptionsFlow = keyboardOptions,
                        singleLine = true,
                        submitLabel = "NEXT",
                        onSubmit = { viewModel.submitAddress(it.toString()) },
                        onBack = { goBack() },
                        modifier = Modifier.fillMaxSize(),
                    )

                    is NewConversationStep.Body -> LightTextInputEditor(
                        title = step.address,
                        state = editorState,
                        editorKey = "body-${state.editorSession}",
                        keyboardOptionsFlow = keyboardOptions,
                        submitIcon = LightIcons.SEND,
                        onSubmit = { viewModel.submitBody(it.toString()) },
                        onBack = viewModel::backToAddress,
                        modifier = Modifier.fillMaxSize(),
                    )

                    is NewConversationStep.Sending -> LightText(
                        text = "Sending…",
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Center,
                    )

                    is NewConversationStep.Sent -> LightFullscreenModal(
                        message = "Message sent to ${step.address}.",
                        onClose = { goBack() },
                    )

                    is NewConversationStep.Failed -> LightFullscreenModal(
                        message = step.message,
                        onClose = viewModel::backToAddress,
                    )
                }
            }
        }
    }
}
