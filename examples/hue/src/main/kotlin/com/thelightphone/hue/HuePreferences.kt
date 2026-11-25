package com.thelightphone.hue

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

internal object HuePreferences {
    val BRIDGE_IP = stringPreferencesKey("bridge_ip")
    val BRIDGE_ID = stringPreferencesKey("bridge_id")
    val APP_KEY = stringPreferencesKey("app_key")
}

internal data class SavedBridge(val ip: String, val id: String, val key: String)

internal suspend fun DataStore<Preferences>.saveHueBridge(bridge: SavedBridge): SavedBridge {
    edit { prefs ->
        prefs[HuePreferences.BRIDGE_IP] = bridge.ip
        prefs[HuePreferences.BRIDGE_ID] = bridge.id
        prefs[HuePreferences.APP_KEY] = bridge.key
    }
    return bridge
}
