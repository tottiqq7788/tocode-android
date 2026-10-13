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
                relay = relay.trimEnd('/'),
                token = json.getString("link_token"),
            )
        }
    }

    fun delete(link: AppLink) {
        val request = Request.Builder()
            .url("${link.relay}/v1/links/${link.id}")
            .header("Authorization", "Bearer ${link.token}")
            .delete()
            .build()
        http.newCall(request).execute().close()
    }

    fun open(
        relay: String,
        token: String,
        onMessage: (JSONObject) -> Unit,
        onConnection: (Boolean) -> Unit = {},
    ): WebSocket {
        val request = Request.Builder().url(socketURL(relay, token)).build()
        return http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                onConnection(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                onMessage(json)
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

val remoteActions = listOf(
    "status" to "设备状态",
    "blackout" to "临时黑屏",
    "hidden" to "隐藏文件",
    "finder-follow" to "访达跟随",
    "codex-follow" to "Codex 跟随",
    "wheel-vertical" to "垂直滚轮",
    "wheel-horizontal" to "横向滚轮",
    "finder-move" to "访达 X/V 移动",
    "finder-cmdq" to "访达强退快捷键",
    "double-cmdq" to "双击 Cmd-Q",
    "login" to "开机自启",
)
