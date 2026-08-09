package com.thelightphone.sample

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bridges LightOS push deliveries (which arrive in [ToolEntryPoint], outside of
 * any screen) to whichever view models are currently on screen.
 */
object PushBus {
    private val _incoming = MutableSharedFlow<Message>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val incoming: SharedFlow<Message> = _incoming.asSharedFlow()

    fun publish(message: Message) {
        _incoming.tryEmit(message)
    }
}
