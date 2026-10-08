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

    fun open(
        relay: String,
        token: String,
        onMessage: (JSONObject) -> Unit,
        onFile: (JSONObject, ByteArray) -> Unit = { _, _ -> },
        onConnection: (Boolean) -> Unit = {},
    ): WebSocket {
        val base = relay.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        val request = Request.Builder().url("$base/v1/ws?token=$token").build()
        return http.newWebSocket(request, object : WebSocketListener() {
            private var pendingFile: JSONObject? = null

            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                onConnection(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (pendingFile != null) {
                    pendingFile = null
                    onMessage(JSONObject().put("op", "error").put("message", "文件帧不完整"))
                }
                if (json.optString("op") == "file") {
                    pendingFile = json
                } else {
                    onMessage(json)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                val header = pendingFile
                pendingFile = null
                if (header == null) return
                val size = header.optInt("size", -1)
                if (size != bytes.size) {
                    onMessage(JSONObject().put("op", "error").put("message", "文件长度不匹配"))
                    return
                }
                onFile(header, bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
                onConnection(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                onConnection(false)
            }
        })
    }
}

fun socketURL(relay: String, token: String): String {
    val base = relay.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
    return "$base/v1/ws?token=$token"
}
