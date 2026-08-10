package com.thelightphone.sample

import androidx.datastore.preferences.core.stringPreferencesKey

internal object AskClaudePreferences {
    val ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
}
