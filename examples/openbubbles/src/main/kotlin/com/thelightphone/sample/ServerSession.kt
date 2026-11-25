package com.thelightphone.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Holds the DataStore-backed credentials plus the REST client that reads them,
 * so every view model talks to the server the same way.
 */
class ServerSession(
    dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    val settings = OpenBubblesSettings(dataStore)

    private val credentials = MutableStateFlow(ServerCredentials())

    val client = BlueBubblesClient { credentials.value }

    init {
        scope.launch {
            settings.credentials.collect { credentials.value = it }
        }
    }

    /** Latest persisted credentials, waiting for the first DataStore read. */
    suspend fun awaitCredentials(): ServerCredentials =
        settings.credentials.first().also { credentials.value = it }

    fun close() = client.close()
}
