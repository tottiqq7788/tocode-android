package app.tocode.android

import java.net.URLDecoder

data class PairingPayload(val relay: String, val code: String)

fun parsePairingPayload(raw: String): PairingPayload? {
    val text = raw.trim()
    val prefix = "tocode-app://pair?"
    if (!text.startsWith(prefix)) return null
    val params = text.removePrefix(prefix).split("&").mapNotNull { part ->
        val bits = part.split("=", limit = 2)
        if (bits.size != 2) null else bits[0] to URLDecoder.decode(bits[1], "UTF-8")
    }.toMap()
    val relay = params["relay"] ?: return null
    val code = params["code"] ?: return null
    val local = relay.startsWith("http://127.0.0.1") ||
        relay.startsWith("http://localhost") ||
        relay.startsWith("http://[::1]")
    if (!(relay.startsWith("https://") || local)) return null
    if (code.length != 8 || code.any { !it.isLetterOrDigit() }) return null
    return PairingPayload(relay.trimEnd('/'), code)
}
