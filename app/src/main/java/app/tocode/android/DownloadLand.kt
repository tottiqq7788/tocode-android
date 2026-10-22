package app.tocode.android

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/** 下载落盘：`Download/tocode/yyMMdd/`；zip 仅传输，落盘必为解压后的原结构。 */
object DownloadLand {
    data class Result(val ok: Boolean, val folderLabel: String, val notice: String)

    fun save(context: Context, name: String, mode: String, bytes: ByteArray): Result {
        val day = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        val relativeRoot = "Download/tocode/$day"
        return try {
            when (mode) {
                "zip" -> {
                    unzip(context, bytes, relativeRoot, day)
                    Result(true, relativeRoot, "已保存到 $relativeRoot")
                }
                else -> {
                    writeOne(context, relativeRoot, name.ifBlank { "download.bin" }, bytes)
                    Result(true, relativeRoot, "已保存到 $relativeRoot/$name")
                }
            }
        } catch (error: Exception) {
            Result(false, relativeRoot, error.message?.ifBlank { null } ?: "保存失败")
        }
    }

    private fun unzip(context: Context, bytes: ByteArray, relativeRoot: String, day: String) {
        ZipInputStream(BufferedInputStream(ByteArrayInputStream(bytes))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.trimStart('/').replace('\\', '/')
                if (name.isEmpty() || name.contains("..")) {
                    zip.closeEntry()
                    continue
                }
                if (entry.isDirectory) {
                    ensureDir(context, relativeRoot, name.trimEnd('/'), day)
                    zip.closeEntry()
                    continue
                }
                val data = zip.readBytes()
                writeOne(context, relativeRoot, name, data)
                zip.closeEntry()
            }
        }
    }

    private fun ensureDir(context: Context, relativeRoot: String, relative: String, day: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        val base = publicDayFolder(day)
        val target = if (relative.isEmpty()) base else File(base, relative)
        if (!target.exists()) target.mkdirs()
    }

    private fun writeOne(context: Context, relativeRoot: String, relativePath: String, bytes: ByteArray) {
        val clean = relativePath.trimStart('/').replace('\\', '/')
        require(clean.isNotEmpty() && !clean.contains("..")) { "路径无效" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val parent = clean.substringBeforeLast('/', "")
            val fileName = clean.substringAfterLast('/')
            val relativePathValue = if (parent.isEmpty()) {
                relativeRoot
            } else {
                "$relativeRoot/$parent"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePathValue)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法写入下载目录")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入下载目录")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return
        }
        val day = relativeRoot.substringAfterLast('/')
        val base = publicDayFolder(day)
        val target = File(base, clean)
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { it.write(bytes) }
    }

    private fun publicDayFolder(day: String): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val folder = File(downloads, "tocode/$day")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }
}
