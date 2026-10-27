package app.tocode.android

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RelayClient(private val http: OkHttpClient = OkHttpClient.Builder()
    .pingInterval(20, TimeUnit.SECONDS)
    .build()
) {
    fun redeem(
        relay: String,
        code: String,
        androidId: String,
        androidName: String,
        bindingKey: String,
    ): AppLink {
        val body = JSONObject()
            .put("android_id", androidId)
            .put("android_name", androidName)
            .put("binding_key", bindingKey)
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
        linkId: String = "",
        onMessage: (JSONObject) -> Unit,
        onBinary: (ByteArray) -> Unit = {},
        onConnection: (Boolean) -> Unit = {},
    ): WebSocket {
        val corr = DiagnosticLog.bumpConnectionCorr()
        DiagnosticLog.event(
            "ws.connect_intent",
            linkId = linkId,
            corrId = corr,
            detail = mapOf("host" to relay.trimEnd('/').substringAfter("://").substringBefore('/')),
        )
        val request = Request.Builder().url(socketURL(relay)).build()
        var announced = false
        return http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                DiagnosticLog.event(
                    "ws.open",
                    linkId = linkId,
                    corrId = corr,
                    detail = mapOf("http" to response.code),
                )
                val auth = JSONObject()
                auth.put("op", "auth")
                auth.put("token", token)
                webSocket.send(auth.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (!announced && json.optString("op") == "ready") {
                    announced = true
                    onConnection(true)
                }
                onMessage(json)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onBinary(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                DiagnosticLog.event(
                    "ws.closing",
                    linkId = linkId,
                    corrId = corr,
                    detail = mapOf("code" to code, "reason" to reason.take(120)),
                )
                webSocket.close(code, reason)
                onConnection(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                DiagnosticLog.event(
                    "ws.failure",
                    linkId = linkId,
                    corrId = corr,
                    detail = mapOf(
                        "error" to (t::class.java.simpleName),
                        "message" to (t.message?.take(120) ?: ""),
                        "http" to (response?.code ?: 0),
                    ),
                )
                onConnection(false)
            }
        })
    }
}

fun socketURL(relay: String): String {
    val base = relay.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
    return "$base/v1/ws"
}

data class RemoteButton(val id: String, val name: String, val detail: String, val icon: String)

fun visibleRemoteButton(id: String, name: String, detail: String, icon: String): RemoteButton? {
    val trimmed = id.trim()
    val title = name.trim()
    if (trimmed.isEmpty() || title.isEmpty()) return null
    return RemoteButton(trimmed, title, detail.trim(), icon.trim())
}

fun remotePressFields(id: String): Map<String, String> = mapOf("button_id" to id)

fun parseRemoteButtons(body: JSONObject): List<RemoteButton> {
    val rows = body.optJSONArray("buttons") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val button = visibleRemoteButton(
                row.optString("id"),
                row.optString("name"),
                row.optString("detail"),
                row.optString("icon"),
            ) ?: continue
            add(button)
        }
    }
}

data class RemoteWindow(val title: String, val windowId: Long, val pid: Int, val front: Boolean)

data class RemoteSpace(
    val title: String,
    val current: Boolean,
    val fullscreen: Boolean,
    val displayId: String,
    val spaceId: Long,
    val windows: List<RemoteWindow>,
)

data class RemoteDisplay(val title: String, val spaces: List<RemoteSpace>)

fun parseRemoteDisplays(body: JSONObject): List<RemoteDisplay> {
    val rows = body.optJSONArray("displays") ?: return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val spaces = row.optJSONArray("spaces")
            add(RemoteDisplay(row.optString("title"), parseRemoteSpaces(spaces)))
        }
    }
}

private fun parseRemoteSpaces(rows: org.json.JSONArray?): List<RemoteSpace> {
    if (rows == null) return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val windows = row.optJSONArray("windows")
            add(
                RemoteSpace(
                    title = row.optString("title").ifBlank { "桌面" },
                    current = row.optBoolean("current"),
                    fullscreen = row.optString("kind") == "fullscreen",
                    displayId = row.optString("display_id"),
                    spaceId = row.optLong("space_id"),
                    windows = parseRemoteWindows(windows),
                )
            )
        }
    }
}

private fun parseRemoteWindows(rows: org.json.JSONArray?): List<RemoteWindow> {
    if (rows == null) return emptyList()
    return buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val title = row.optString("title").ifBlank { "窗口" }
            add(RemoteWindow(title, row.optLong("window_id"), row.optInt("pid"), row.optBoolean("front")))
        }
    }
}
