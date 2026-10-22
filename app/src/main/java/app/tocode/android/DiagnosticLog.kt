package app.tocode.android

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 关联通道诊断：本地 JSONL，不含 token / 正文 / 密钥。 */
object DiagnosticLog {
    private val writer = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var root: File? = null
    @Volatile private var corr: String = UUID.randomUUID().toString().lowercase()

    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val tsFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val blocked = setOf(
        "token", "device_token", "link_token", "authorization", "binding_key",
        "binding_key_digest", "text", "content", "body", "path", "caption",
        "bytes", "data", "password", "secret",
    )

    private const val maxFileBytes = 8L * 1024 * 1024
    private const val keepDays = 7

    fun bind(context: Context) {
        synchronized(lock) {
            root = File(context.applicationContext.filesDir, "diagnostics").also { it.mkdirs() }
        }
    }

    fun bumpConnectionCorr(): String {
        corr = UUID.randomUUID().toString().lowercase()
        return corr
    }

    fun currentCorr(): String = corr

    fun event(
        name: String,
        linkId: String? = null,
        deviceId: String? = null,
        corrId: String? = null,
        detail: Map<String, Any?> = emptyMap(),
    ) {
        val row = JSONObject()
            .put("ts", tsFormat.format(Date()))
            .put("side", "android")
            .put("event", name)
            .put("corr", corrId ?: currentCorr())
        if (!linkId.isNullOrBlank()) row.put("link_id", linkId)
        if (!deviceId.isNullOrBlank()) row.put("device_id", deviceId)
        val cleaned = sanitize(detail)
        if (cleaned.isNotEmpty()) row.put("detail", JSONObject(cleaned))
        writer.execute { append(row.toString()) }
    }

    fun exportZip(context: Context): Intent? {
        val dir = synchronized(lock) { root } ?: return null
        val today = dayFormat.format(Date())
        val yesterday = dayFormat.format(Date(System.currentTimeMillis() - 86_400_000L))
        val sources = listOf("android-$today.jsonl", "android-$yesterday.jsonl")
            .map { File(dir, it) }
            .filter { it.isFile && it.length() > 0 }
        if (sources.isEmpty()) return null
        val zip = File(context.cacheDir, "tocode-diagnostics-$today.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { out ->
            sources.forEach { file ->
                out.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, "tocode 诊断日志")
        }
    }

    internal fun sanitize(detail: Map<String, Any?>): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        detail.forEach { (key, value) ->
            val lower = key.lowercase()
            if (lower in blocked) return@forEach
            if ("token" in lower || "secret" in lower || "password" in lower) return@forEach
            when (value) {
                null -> Unit
                is String -> out[key] = value.take(240)
                is Number, is Boolean -> out[key] = value
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val child = sanitize(value as Map<String, Any?>)
                    if (child.isNotEmpty()) out[key] = child
                }
                is Iterable<*> -> {
                    out[key] = value.take(20).map { item ->
                        when (item) {
                            is String -> item.take(80)
                            is Number, is Boolean -> item
                            else -> item.toString().take(80)
                        }
                    }
                }
                else -> out[key] = value.toString().take(80)
            }
        }
        return out
    }

    private fun append(line: String) {
        val dir = synchronized(lock) { root } ?: return
        try {
            prune(dir)
            val file = File(dir, "android-${dayFormat.format(Date())}.jsonl")
            if (file.exists() && file.length() > maxFileBytes) return
            file.appendText(line + "\n")
        } catch (_: Exception) {
        }
    }

    private fun prune(dir: File) {
        val cutoff = System.currentTimeMillis() - keepDays * 86_400_000L
        dir.listFiles()?.forEach { file ->
            if (file.extension == "jsonl" && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }
}
