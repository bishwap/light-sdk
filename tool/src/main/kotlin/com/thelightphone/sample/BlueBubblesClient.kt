package com.thelightphone.sample

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.random.Random

private const val CHAT_PAGE_SIZE = 100
private const val MESSAGE_PAGE_SIZE = 50
private const val REQUEST_TIMEOUT_MILLIS = 20_000L

class BlueBubblesException(message: String) : Exception(message)

/**
 * Thin REST client for a BlueBubbles/OpenBubbles server.
 *
 * The server authenticates every call with a static password passed as a query
 * parameter, which is what the official clients do as well.
 */
class BlueBubblesClient(private val credentials: () -> ServerCredentials) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
    }

    suspend fun ping(): Result<String> = call {
        val response = client.get(url("server/info")) { auth() }
        val info: ApiEnvelope<ServerInfo> = response.decode()
        listOfNotNull(
            info.data?.serverVersion?.let { "BlueBubbles $it" },
            info.data?.osVersion?.let { "macOS $it" },
        ).joinToString(" · ").ifEmpty { "Connected" }
    }

    suspend fun fetchChats(): Result<List<Conversation>> = call {
        val response = client.post(url("chat/query")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(
                ChatQueryRequest(
                    limit = CHAT_PAGE_SIZE,
                    offset = 0,
                    with = listOf("lastMessage", "participants"),
                    sort = "lastmessage",
                ),
            )
        }
        val envelope: ApiEnvelope<List<ApiChat>> = response.decode()
        envelope.data.orEmpty()
            .map { it.toConversation() }
            .sortedByDescending { it.timestampMillis }
    }

    suspend fun fetchMessages(chatGuid: String): Result<List<Message>> = call {
        val response = client.get(url("chat/$chatGuid/message")) {
            auth()
            parameter("limit", MESSAGE_PAGE_SIZE)
            parameter("offset", 0)
            parameter("with", "handle,attachment")
            parameter("sort", "DESC")
        }
        val envelope: ApiEnvelope<List<ApiMessage>> = response.decode()
        envelope.data.orEmpty()
            .map { it.toMessage() }
            .sortedBy { it.timestampMillis }
    }

    suspend fun sendMessage(chatGuid: String, text: String): Result<Message> = call {
        val response = client.post(url("message/text")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(
                SendTextRequest(
                    chatGuid = chatGuid,
                    tempGuid = newTempGuid(),
                    message = text,
                ),
            )
        }
        val envelope: ApiEnvelope<ApiMessage> = response.decode()
        envelope.data?.toMessage()
            ?: throw BlueBubblesException("Server accepted the message but returned no data")
    }

    /** Starts a new chat with [address] and sends [text] as its first message. */
    suspend fun startChat(address: String, text: String): Result<String> = call {
        val response = client.post(url("chat/new")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(NewChatRequest(addresses = listOf(address), message = text))
        }
        val envelope: ApiEnvelope<ApiChat> = response.decode()
        envelope.data?.guid
            ?: throw BlueBubblesException("Server did not return a chat for $address")
    }

    fun close() = client.close()

    private fun url(path: String): String {
        val host = credentials().serverUrl.trim().trimEnd('/')
        if (host.isEmpty()) throw BlueBubblesException("No server URL configured")
        val normalized = if (host.startsWith("http://") || host.startsWith("https://")) {
            host
        } else {
            "http://$host"
        }
        return "$normalized/api/v1/$path"
    }

    private fun HttpRequestBuilder.auth() {
        parameter("password", credentials().password)
    }

    private suspend inline fun <reified T> HttpResponse.decode(): T {
        if (!status.isSuccess()) {
            val detail = runCatching { json.decodeFromString<ApiEnvelope<String>>(bodyAsText()).message }
                .getOrNull()
                ?: bodyAsText().take(200)
            throw BlueBubblesException("HTTP ${status.value}: $detail")
        }
        return body()
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching { block() }
    }
}

data class ServerCredentials(
    val serverUrl: String = "",
    val password: String = "",
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && password.isNotBlank()
}

private fun newTempGuid(): String =
    "light-" + Random.nextLong(0, Long.MAX_VALUE).toString(16)

fun Throwable.userMessage(): String = when (this) {
    is BlueBubblesException -> message ?: "Server error"
    else -> message?.takeIf { it.isNotBlank() } ?: "Could not reach the server"
}
