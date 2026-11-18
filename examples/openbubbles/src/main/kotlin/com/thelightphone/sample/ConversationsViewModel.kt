package com.thelightphone.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val conversations: List<Conversation> = emptyList(),
    val status: String? = LOADING_MESSAGE,
    val query: String = "",
    val searching: Boolean = false,
) {
    val title: String get() = if (query.isBlank()) "OpenBubbles" else "\"$query\""
}

internal const val LOADING_MESSAGE = "Loading conversations…"
internal const val NOT_CONFIGURED_MESSAGE =
    "Add your BlueBubbles server URL and password in settings."

class ConversationsViewModel(
    dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {

    private val session = ServerSession(dataStore, viewModelScope)

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    private var allConversations: List<Conversation> = emptyList()

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
        if (_uiState.value.searching) {
            cancelSearch()
            return true
        }
        if (_uiState.value.query.isNotBlank()) {
            applySearch("")
            return true
        }
        return false
    }

    fun refresh() {
        viewModelScope.launch {
            if (!session.awaitCredentials().isConfigured) {
                allConversations = emptyList()
                _uiState.update { it.copy(conversations = emptyList(), status = NOT_CONFIGURED_MESSAGE) }
                return@launch
            }
            if (allConversations.isEmpty()) {
                _uiState.update { it.copy(status = LOADING_MESSAGE) }
            }
            session.client.fetchChats()
                .onSuccess { chats ->
                    allConversations = chats
                    publish()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(status = error.userMessage()) }
                }
        }
    }

    fun startSearch() {
        _uiState.update { it.copy(searching = true) }
    }

    fun cancelSearch() {
        _uiState.update { it.copy(searching = false) }
    }

    fun clearSearch() {
        applySearch("")
    }

    fun applySearch(query: String) {
        _uiState.update { it.copy(query = query.trim(), searching = false) }
        publish()
    }

    private fun publish() {
        val query = _uiState.value.query
        val filtered = if (query.isBlank()) {
            allConversations
        } else {
            allConversations.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.preview.contains(query, ignoreCase = true)
            }
        }
        _uiState.update {
            it.copy(
                conversations = filtered,
                status = when {
                    filtered.isNotEmpty() -> null
                    query.isNotBlank() -> "No conversations match \"$query\"."
                    else -> "No conversations yet."
                },
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        session.close()
    }
}
