package com.thelightphone.sample

import android.util.Log
import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

private const val TAG = "OpenBubbles"
private const val NEW_MESSAGE_EVENT = "new-message"

/**
 * Receives new-message pushes that the BlueBubbles server sends through
 * LightOS UnifiedPush and forwards them to any screen that is listening.
 */
@EntryPoint
object ToolEntryPoint : LightEntryPoint {

    override val enablePushNotifications: Boolean = true

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun onToolCreate(serverData: StateFlow<LightServerData?>) {
        serverData.collect { data ->
            // The endpoint below is what a BlueBubbles server needs in order to
            // push to this device; register it with the server out of band.
            Log.d(TAG, "LightOS push registration: ${data?.pushCredentials?.pushEndpoint}")
        }
    }

    override suspend fun onPushNotification(data: ByteArray) {
        val payload = data.decodeToString()
        val envelope = runCatching { json.decodeFromString<PushEnvelope>(payload) }.getOrNull()
        if (envelope == null || envelope.type != NEW_MESSAGE_EVENT) {
            Log.d(TAG, "Ignoring push of type ${envelope?.type ?: "unknown"}")
            return
        }
        envelope.data?.let { PushBus.publish(it.toMessage()) }
    }
}
