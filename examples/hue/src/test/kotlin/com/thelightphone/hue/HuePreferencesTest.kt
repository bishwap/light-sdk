package com.thelightphone.hue

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class HuePreferencesTest {
    private val bridge = SavedBridge("192.168.1.42", "001788FFFE123ABC", "test-key")

    @Test
    fun savedBridgeIsReturnedOnlyAfterThePreferencesWrite() = runBlocking {
        val store = TestDataStore()
        assertEquals(bridge, store.saveHueBridge(bridge))
        assertEquals(bridge.ip, store.data.value[HuePreferences.BRIDGE_IP])
        assertEquals(bridge.id, store.data.value[HuePreferences.BRIDGE_ID])
        assertEquals(bridge.key, store.data.value[HuePreferences.APP_KEY])
    }

    @Test
    fun failedWriteDoesNotReturnASavedBridge() = runBlocking {
        val failure = IOException("write failed")
        val store = TestDataStore(failure)
        assertSame(failure, assertFailsWith<IOException> { store.saveHueBridge(bridge) })
        assertEquals(emptyPreferences(), store.data.value)
    }

    private class TestDataStore(private val failure: IOException? = null) : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(data.value)
            failure?.let { throw it }
            data.value = updated
            return updated
        }
    }
}
