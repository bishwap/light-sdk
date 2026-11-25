package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val CLAUDE_MODEL = "claude-haiku-4-5"
private const val CLAUDE_MAX_TOKENS = 1024
private const val CLAUDE_MESSAGES_URL = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"
private const val CLAUDE_REQUEST_TIMEOUT_MILLIS = 60_000L

@Serializable
private data class ClaudeMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<ClaudeMessage>,
)

@Serializable
private data class ClaudeContentBlock(
    val type: String = "",
    val text: String = "",
)

@Serializable
private data class ClaudeResponse(
    val content: List<ClaudeContentBlock> = emptyList(),
)

@Serializable
private data class ClaudeErrorDetail(
    val type: String = "",
    val message: String = "",
)

@Serializable
private data class ClaudeErrorResponse(
    val error: ClaudeErrorDetail = ClaudeErrorDetail(),
)

class HomeScreenViewModel(
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {

    sealed class State {
        data object Idle : State()
        data class Loading(val question: String) : State()
        data class Answer(val question: String, val text: String) : State()
        data class Error(val message: String) : State()
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = CLAUDE_REQUEST_TIMEOUT_MILLIS
        }
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private var requestJob: Job? = null

    // Hoisted into the ViewModel so the instance survives navigation and reset.
    // The embedded keyboard binds to this state once; recreating it (e.g. via
    // rememberTextFieldState) would leave the cached keyboard bound to a stale one.
    val questionState = TextFieldState()

    fun newQuestion() {
        questionState.clearText()
        editQuestion()
    }

    fun editQuestion() {
        requestJob?.cancel()
        requestJob = null
        _state.value = State.Idle
    }

    fun ask() {
        val trimmed = questionState.text.toString().trim()
        if (_state.value != State.Idle || trimmed.isEmpty()) return

        _state.value = State.Loading(trimmed)
        requestJob = viewModelScope.launch {
            val result = try {
                requestAnswer(trimmed)
            } catch (e: CancellationException) {
                throw e
            } catch (_: HttpRequestTimeoutException) {
                State.Error("The request timed out. Check your connection and try again.")
            } catch (e: Exception) {
                State.Error(e.message ?: "Unknown error")
            }
            ensureActive()
            _state.value = result
        }
    }

    private suspend fun requestAnswer(question: String): State {
        val apiKey = dataStore.data.first()[AskClaudePreferences.ANTHROPIC_API_KEY]?.trim()
        if (apiKey.isNullOrEmpty()) {
            return State.Error("No API key set. Open Settings to add your Anthropic API key.")
        }

        val response: HttpResponse = client.post(CLAUDE_MESSAGES_URL) {
            header("x-api-key", apiKey)
            header("anthropic-version", ANTHROPIC_VERSION)
            contentType(ContentType.Application.Json)
            setBody(
                ClaudeRequest(
                    model = CLAUDE_MODEL,
                    maxTokens = CLAUDE_MAX_TOKENS,
                    messages = listOf(ClaudeMessage(role = "user", content = question)),
                ),
            )
        }

        if (!response.status.isSuccess()) {
            val detail = runCatching { response.body<ClaudeErrorResponse>().error.message }
                .getOrElse { if (it is CancellationException) throw it else null }
                ?.takeIf { it.isNotEmpty() }
            return State.Error(detail ?: "Request failed (${response.status.value}).")
        }

        val parsed: ClaudeResponse = response.body()
        val answer = parsed.content
            .filter { it.type == "text" }
            .joinToString("\n") { it.text }
            .trim()
        return if (answer.isEmpty()) {
            State.Error("Claude returned an empty response.")
        } else {
            State.Answer(question = question, text = answer)
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.close()
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeScreenViewModel>
        get() = HomeScreenViewModel::class.java

    override fun createViewModel() = HomeScreenViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (val s = state) {
                    is HomeScreenViewModel.State.Idle -> QuestionInput()
                    is HomeScreenViewModel.State.Loading -> LoadingContent(s.question)

                    is HomeScreenViewModel.State.Answer -> AnswerContent(
                        question = s.question,
                        answer = s.text,
                    )

                    is HomeScreenViewModel.State.Error -> ErrorContent(message = s.message)
                }
            }
        }
    }

    @Composable
    private fun QuestionInput() {
        val keyboardOptionsFlow = rememberKeyboardOptions()
        LightTextInputEditor(
            title = "Ask Claude",
            state = viewModel.questionState,
            keyboardOptionsFlow = keyboardOptionsFlow,
            submitLabel = "ASK",
            submitIcon = LightIcons.SEARCH,
            onSubmit = { viewModel.ask() },
            onBack = { goBack() },
            leftBottomBarItem = LightBarButton.LightIcon(
                icon = LightIcons.SETTINGS,
                onClick = { navigateTo(::SettingsScreen) },
                contentDescription = "Settings",
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }

    @Composable
    private fun LoadingContent(question: String) {
        Column(modifier = Modifier.fillMaxSize()) {
            LightTopBar(
                center = LightTopBarCenter.Text("Ask Claude"),
                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
            )
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                LightText(text = "Thinking…", variant = LightTextVariant.Copy)
                LightText(
                    text = question,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                )
            }
            LightBottomBar(
                items = listOf(LightBarButton.Text(text = "CANCEL", onClick = viewModel::editQuestion)),
            )
        }
    }

    @Composable
    private fun AnswerContent(question: String, answer: String) {
        Column(modifier = Modifier.fillMaxSize()) {
            LightTopBar(
                center = LightTopBarCenter.Text("Ask Claude"),
                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
            )
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                LightText(text = question, variant = LightTextVariant.Heading)
                LightText(
                    text = answer,
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                )
            }
            LightBottomBar(
                items = listOf(
                    LightBarButton.Text(text = "ASK AGAIN", onClick = viewModel::newQuestion),
                    LightBarButton.LightIcon(
                        icon = LightIcons.SETTINGS,
                        onClick = { navigateTo(::SettingsScreen) },
                        contentDescription = "Settings",
                    ),
                ),
            )
        }
    }

    @Composable
    private fun ErrorContent(message: String) {
        Column(modifier = Modifier.fillMaxSize()) {
            LightTopBar(
                center = LightTopBarCenter.Text("Ask Claude"),
                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
            )
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                LightText(
                    text = message,
                    variant = LightTextVariant.Copy,
                    lighten = true,
                )
            }
            LightBottomBar(
                items = listOf(
                    LightBarButton.Text(text = "EDIT QUESTION", onClick = viewModel::editQuestion),
                    LightBarButton.LightIcon(
                        icon = LightIcons.SETTINGS,
                        onClick = { navigateTo(::SettingsScreen) },
                        contentDescription = "Settings",
                    ),
                ),
            )
        }
    }
}
