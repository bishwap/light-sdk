package com.thelightphone.hue

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HueApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun onlyTheUnauthorizedUserCodeRejectsTheSavedKey() {
        assertFailsWith<UnauthorizedException> {
            throwOnHueError(json, """[{"error":{"type":1,"description":"user not recognized"}}]""")
        }
        val otherError = assertFailsWith<IllegalStateException> {
            throwOnHueError(json, """[{"error":{"type":3,"description":"unauthorized resource"}}]""")
        }
        assertEquals("unauthorized resource", otherError.message)
    }

    @Test
    fun successfulReadsAndCommandsAreNotErrors() {
        throwOnHueError(json, """{"1":{"name":"Lamp"}}""")
        throwOnHueError(json, """[{"success":{"/lights/1/state/on":true}}]""")
    }
}
