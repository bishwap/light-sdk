package com.thelightphone.hue

import java.io.IOException

internal suspend fun <T> requestWithBridgeRediscovery(
    ip: String,
    bridgeId: String,
    discover: suspend () -> Result<List<DiscoveredBridge>>,
    request: suspend (String) -> Result<T>,
): Pair<String, Result<T>> {
    val initial = request(ip)
    if (initial.exceptionOrNull() !is IOException) return ip to initial
    val replacement = discover().getOrNull()?.firstOrNull {
        it.id.equals(bridgeId, ignoreCase = true) &&
            !it.internalipaddress.equals(ip, ignoreCase = true) &&
            isValidHost(it.internalipaddress)
    }?.internalipaddress ?: return ip to initial
    return replacement to request(replacement)
}
