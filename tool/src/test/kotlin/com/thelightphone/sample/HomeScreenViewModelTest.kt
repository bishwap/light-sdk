package com.thelightphone.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenViewModelTest {
    @Test
    fun editQuestionAfterMissingKeyPreservesTheExactDraft() = runTest {
        withViewModel(flowOf(emptyPreferences())) { viewModel ->
            val draft = "  A question with whitespace\n"
            viewModel.questionState.edit { append(draft) }
            viewModel.ask()
            assertEquals(HomeScreenViewModel.State.Loading(draft.trim()), viewModel.state.value)
            runCurrent()
            assertIs<HomeScreenViewModel.State.Error>(viewModel.state.value)

            viewModel.editQuestion()
            assertEquals(HomeScreenViewModel.State.Idle, viewModel.state.value)
            assertEquals(draft, viewModel.questionState.text.toString())
        }
    }

    @Test
    fun cancelStopsTheInFlightRequestAndAllowsResubmission() = runTest {
        var attempts = 0
        var requestCanceled = false
        val keys = flow {
            if (++attempts == 1) {
                try {
                    awaitCancellation()
                } finally {
                    requestCanceled = true
                }
            }
            emit(emptyPreferences())
        }
        withViewModel(keys) { viewModel ->
            val draft = "Keep this question"
            viewModel.questionState.edit { append(draft) }
            viewModel.ask()
            runCurrent()
            viewModel.ask()
            runCurrent()
            assertEquals(1, attempts)
            assertIs<HomeScreenViewModel.State.Loading>(viewModel.state.value)

            viewModel.editQuestion()
            runCurrent()
            assertTrue(requestCanceled)
            assertEquals(HomeScreenViewModel.State.Idle, viewModel.state.value)
            assertEquals(draft, viewModel.questionState.text.toString())

            viewModel.ask()
            runCurrent()
            assertEquals(2, attempts)
            assertIs<HomeScreenViewModel.State.Error>(viewModel.state.value)
        }
    }

    @Test
    fun preferencesReadFailureDoesNotLeaveTheScreenLoading() = runTest {
        withViewModel(flow { throw IOException("Cannot read preferences") }) { viewModel ->
            viewModel.questionState.edit { append("A question") }
            viewModel.ask()
            runCurrent()
            assertEquals(HomeScreenViewModel.State.Error("Cannot read preferences"), viewModel.state.value)
        }
    }

    private fun TestScope.withViewModel(
        keys: Flow<Preferences>,
        block: (HomeScreenViewModel) -> Unit,
    ) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val viewModel = HomeScreenViewModel(object : DataStore<Preferences> {
                override val data = keys
                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                    error("Unexpected preferences write")
            })
            store.put("askClaude", viewModel)
            block(viewModel)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
