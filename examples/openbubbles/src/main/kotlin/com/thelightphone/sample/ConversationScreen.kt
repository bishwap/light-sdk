package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationUiState(
    val messages: List<Message> = emptyList(),
    val status: String? = "Loading messages…",
    val composing: Boolean = false,
    val sending: Boolean = false,
    val composerSession: Int = 0,
)

class ConversationViewModel(
    dataStore: DataStore<Preferences>,
    private val conversation: Conversation,
) : LightViewModel<Unit>() {

    private val session = ServerSession(dataStore, viewModelScope)

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    val title: String get() = conversation.title

    init {
        viewModelScope.launch {
            PushBus.incoming.collect { refresh() }
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        refresh()
    }

    override fun onBackPressed(): Boolean {
        if (_uiState.value.composing) {
            closeComposer()
            return true
        }
        return false
    }

    fun refresh() {
        viewModelScope.launch {
            if (!session.awaitCredentials().isConfigured) {
                _uiState.update { it.copy(status = NOT_CONFIGURED_MESSAGE) }
                return@launch
            }
            session.client.fetchMessages(conversation.guid)
                .onSuccess { messages ->
                    _uiState.update {
                        it.copy(
                            messages = messages,
                            status = if (messages.isEmpty()) "No messages in this thread." else null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(status = error.userMessage()) }
                }
        }
    }

    fun openComposer() {
        _uiState.update { it.copy(composing = true, composerSession = it.composerSession + 1) }
    }

    fun closeComposer() {
        _uiState.update { it.copy(composing = false) }
    }

    fun send(text: String) {
        val body = text.trim()
        if (body.isEmpty()) {
            closeComposer()
            return
        }
        _uiState.update { it.copy(composing = false, sending = true) }
        viewModelScope.launch {
            if (!session.awaitCredentials().isConfigured) {
                _uiState.update { it.copy(sending = false, status = NOT_CONFIGURED_MESSAGE) }
                return@launch
            }
            session.client.sendMessage(conversation.guid, body)
                .onSuccess { sent ->
                    _uiState.update {
                        it.copy(messages = it.messages + sent, status = null, sending = false)
                    }
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(sending = false, status = error.userMessage()) }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        session.close()
    }
}

class ConversationScreen(
    sealedActivity: SealedLightActivity,
    private val conversation: Conversation,
) : LightScreen<Unit, ConversationViewModel>(sealedActivity) {

    override val viewModelClass: Class<ConversationViewModel>
        get() = ConversationViewModel::class.java

    override fun createViewModel() = ConversationViewModel(lightContext.dataStore, conversation)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val composerState = remember(state.composerSession) { TextFieldState("") }
        val keyboardOptions = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                if (state.composing) {
                    LightTextInputEditor(
                        title = viewModel.title,
                        state = composerState,
                        editorKey = state.composerSession,
                        keyboardOptionsFlow = keyboardOptions,
                        submitIcon = LightIcons.SEND,
                        onSubmit = { viewModel.send(it.toString()) },
                        onBack = viewModel::closeComposer,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ThreadContent(state)
                }
            }
        }
    }

    @Composable
    private fun ThreadContent(state: ConversationUiState) {
        val scrollState = rememberScrollState()
        LaunchedEffect(state.messages.size) {
            scrollState.scrollTo(scrollState.maxValue)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            LightTopBar(
                leftButton = LightBarButton.LightIcon(
                    icon = LightIcons.BACK,
                    onClick = { goBack() },
                ),
                center = LightTopBarCenter.Text(viewModel.title),
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                if (state.messages.isEmpty() && state.status != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LightText(
                            text = state.status,
                            variant = LightTextVariant.Copy,
                            lighten = true,
                            align = TextAlign.Center,
                        )
                    }
                } else {
                    LightScrollView(
                        modifier = Modifier.fillMaxSize(),
                        scrollState = scrollState,
                    ) {
                        state.messages.forEach { message ->
                            MessageBubble(message)
                        }
                        if (state.sending) {
                            LightText(
                                text = "Sending…",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                align = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            LightBottomBar(
                items = listOf(
                    LightBarButton.LightIcon(
                        icon = LightIcons.REFRESH,
                        onClick = viewModel::refresh,
                    ),
                    LightBarButton.LightIcon(
                        icon = LightIcons.COMPOSE_MESSAGE,
                        onClick = viewModel::openComposer,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val alignment = if (message.isFromMe) TextAlign.End else TextAlign.Start
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.5f.gridUnitsAsDp()),
    ) {
        LightText(
            text = message.sender.ifBlank { "Unknown" },
            variant = LightTextVariant.Detail,
            lighten = true,
            align = alignment,
            modifier = Modifier.fillMaxWidth(),
        )
        LightText(
            text = message.body.ifBlank { "(empty message)" },
            variant = LightTextVariant.Copy,
            align = alignment,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
