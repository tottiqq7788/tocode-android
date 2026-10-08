package app.tocode.android

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RelayClient(private val http: OkHttpClient = OkHttpClient.Builder()
    .pingInterval(20, TimeUnit.SECONDS)
    .build()
) {
    fun redeem(relay: String, code: String, androidId: String, androidName: String): AppLink {
        val body = JSONObject()
            .put("android_id", androidId)
            .put("android_name", androidName)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${relay.trimEnd('/')}/v1/pairings/$code/redeem")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val codeName = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                error(codeName.ifBlank { "http_${response.code}" })
            }
            val json = JSONObject(text)
            return AppLink(
                id = json.getString("link_id"),
                macName = json.optString("device_name", "Mac"),
                roleName = json.optString("role_name", ""),
                displayName = json.optString("display_name", json.optString("role_name", "关联")),
                relay = relay.trimEnd('/'),
                token = json.getString("link_token"),
            )
        }
    }

    fun open(relay: String, token: String, onMessage: (JSONObject) -> Unit): WebSocket {
        val base = relay.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        val request = Request.Builder().url("$base/v1/ws?token=$token").build()
        return http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                onMessage(json)
            }
        })
    }
}

fun socketURL(relay: String, token: String): String {
    val base = relay.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
    return "$base/v1/ws?token=$token"
}
