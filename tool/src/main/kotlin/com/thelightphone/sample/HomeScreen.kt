package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
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

internal const val CONVERSATION_ROW_UNITS = 4f

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ConversationsViewModel>(sealedActivity) {

    override val viewModelClass: Class<ConversationsViewModel>
        get() = ConversationsViewModel::class.java

    override fun createViewModel() = ConversationsViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val searchState = rememberTextFieldState("")
        val keyboardOptions = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                if (state.searching) {
                    LightTextInputEditor(
                        title = "Search",
                        state = searchState,
                        keyboardOptionsFlow = keyboardOptions,
                        submitIcon = LightIcons.SEARCH,
                        singleLine = true,
                        onSubmit = { viewModel.applySearch(it.toString()) },
                        onBack = viewModel::cancelSearch,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ConversationsContent(state)
                }
            }
        }
    }

    @Composable
    private fun ConversationsContent(state: ConversationsUiState) {
        Column(modifier = Modifier.fillMaxSize()) {
            LightTopBar(
                center = LightTopBarCenter.Text(state.title),
                rightButton = LightBarButton.LightIcon(
                    icon = LightIcons.SETTINGS,
                    onClick = { navigateTo(::SettingsScreen) },
                ),
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                when {
                    state.status != null -> StatusMessage(state.status)

                    else -> LightLazyScrollView(
                        modifier = Modifier.fillMaxSize(),
                        uniformItemHeightGridUnits = CONVERSATION_ROW_UNITS,
                    ) {
                        items(state.conversations.size) { index ->
                            val conversation = state.conversations[index]
                            ConversationRow(conversation) {
                                navigateTo({ activity ->
                                    ConversationScreen(activity, conversation)
                                })
                            }
                        }
                    }
                }
            }

            LightBottomBar(
                items = listOf(
                    LightBarButton.LightIcon(
                        icon = LightIcons.COMPOSE_MESSAGE,
                        onClick = { navigateTo(::NewConversationScreen) },
                    ),
                    LightBarButton.LightIcon(
                        icon = LightIcons.REFRESH,
                        onClick = viewModel::refresh,
                    ),
                    LightBarButton.LightIcon(
                        icon = LightIcons.SEARCH,
                        onClick = viewModel::startSearch,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(CONVERSATION_ROW_UNITS.gridUnitsAsDp())
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.5f.gridUnitsAsDp()),
    ) {
        LightText(
            text = conversation.title,
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LightText(
            text = conversation.preview.ifBlank { "No messages yet" },
            variant = LightTextVariant.Detail,
            lighten = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusMessage(status: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = status,
            variant = LightTextVariant.Copy,
            lighten = true,
            align = TextAlign.Center,
        )
    }
}
