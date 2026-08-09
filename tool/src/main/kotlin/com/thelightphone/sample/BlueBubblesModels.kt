package com.thelightphone.sample

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the BlueBubbles/OpenBubbles REST API (`/api/v1`).
 *
 * Every response is wrapped in a `{ status, message, data }` envelope.
 */
@Serializable
data class ApiEnvelope<T>(
    val status: Int = 0,
    val message: String? = null,
    val data: T? = null,
)

@Serializable
data class ServerInfo(
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("server_version") val serverVersion: String? = null,
)

@Serializable
data class ChatQueryRequest(
    val limit: Int,
    val offset: Int,
    val with: List<String>,
    val sort: String,
)

@Serializable
data class NewChatRequest(
    val addresses: List<String>,
    val message: String,
    val method: String = SEND_METHOD,
    val service: String = "iMessage",
)

@Serializable
data class SendTextRequest(
    val chatGuid: String,
    val tempGuid: String,
    val message: String,
    val method: String = SEND_METHOD,
)

@Serializable
data class ApiHandle(
    val address: String? = null,
    val uncanonicalizedId: String? = null,
)

@Serializable
data class ApiMessage(
    val guid: String = "",
    val text: String? = null,
    val subject: String? = null,
    val isFromMe: Boolean = false,
    val dateCreated: Long? = null,
    val handle: ApiHandle? = null,
    val attachments: List<ApiAttachment> = emptyList(),
)

@Serializable
data class ApiAttachment(
    val guid: String = "",
    val transferName: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class ApiChat(
    val guid: String = "",
    val chatIdentifier: String? = null,
    val displayName: String? = null,
    val participants: List<ApiHandle> = emptyList(),
    val lastMessage: ApiMessage? = null,
)

/**
 * Push envelope forwarded by the BlueBubbles server through LightOS UnifiedPush.
 */
@Serializable
data class PushEnvelope(
    val type: String = "",
    val data: ApiMessage? = null,
)

/** UI model for a row in the conversations list. */
data class Conversation(
    val guid: String,
    val title: String,
    val preview: String,
    val timestampMillis: Long,
)

/** UI model for a single bubble in a thread. */
data class Message(
    val guid: String,
    val body: String,
    val isFromMe: Boolean,
    val sender: String,
    val timestampMillis: Long,
)

/**
 * `apple-script` is the only send method every BlueBubbles server supports;
 * `private-api` requires the optional helper bundle on the Mac.
 */
const val SEND_METHOD: String = "apple-script"

fun ApiChat.toConversation(): Conversation {
    val participantNames = participants.mapNotNull { it.address }
    val resolvedTitle = displayName?.takeIf { it.isNotBlank() }
        ?: participantNames.takeIf { it.isNotEmpty() }?.joinToString(", ")
        ?: chatIdentifier
        ?: guid
    return Conversation(
        guid = guid,
        title = resolvedTitle,
        preview = lastMessage?.displayBody().orEmpty(),
        timestampMillis = lastMessage?.dateCreated ?: 0L,
    )
}

fun ApiMessage.toMessage(): Message = Message(
    guid = guid,
    body = displayBody(),
    isFromMe = isFromMe,
    sender = if (isFromMe) "Me" else handle?.address.orEmpty(),
    timestampMillis = dateCreated ?: 0L,
)

fun ApiMessage.displayBody(): String {
    val body = listOfNotNull(
        subject?.takeIf { it.isNotBlank() },
        text?.takeIf { it.isNotBlank() },
    ).joinToString(" — ")
    if (body.isNotBlank()) return body
    if (attachments.isNotEmpty()) {
        val name = attachments.first().transferName
        return if (attachments.size > 1) {
            "${attachments.size} attachments"
        } else {
            name?.let { "Attachment: $it" } ?: "Attachment"
        }
    }
    return ""
}
