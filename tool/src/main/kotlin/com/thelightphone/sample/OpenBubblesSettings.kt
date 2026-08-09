package com.thelightphone.sample

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val SERVER_URL = stringPreferencesKey("server_url")
private val SERVER_PASSWORD = stringPreferencesKey("server_password")

/** Server URL + password, persisted in the tool's shared Preferences DataStore. */
class OpenBubblesSettings(private val dataStore: DataStore<Preferences>) {

    val credentials: Flow<ServerCredentials> = dataStore.data.map { prefs ->
        ServerCredentials(
            serverUrl = prefs[SERVER_URL].orEmpty(),
            password = prefs[SERVER_PASSWORD].orEmpty(),
        )
    }

    suspend fun setServerUrl(url: String) {
        dataStore.edit { it[SERVER_URL] = url.trim() }
    }

    suspend fun setPassword(password: String) {
        dataStore.edit { it[SERVER_PASSWORD] = password.trim() }
    }
}
