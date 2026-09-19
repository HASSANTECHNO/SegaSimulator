package ir.segasim.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * «بازی‌های من» — ROMهایی که کاربر خودش انتخاب کرده، در حافظه‌ی داخلی
 * برنامه کپی و فهرستشان ماندگار می‌شود؛ پس از بستن برنامه هم باقی می‌مانند.
 *
 * برای فایل‌های SAF (content://) فقط یک‌بار اجازه‌ی خواندن گرفتیم، پس باید
 * بایت‌ها را کنار خودمان نگه داریم — نه اینکه هر بار دوباره به Uri تکیه کنیم.
 */
class MyGamesStore(private val context: Context) {

    data class Entry(
        val id: String,
        val name: String,
        val path: String,
        val twoPlayer: Boolean,
        val sizeBytes: Long,
    ) {
        val file: File get() = File(path)
        fun exists(): Boolean = file.exists()
    }

    private val dir: File = File(context.filesDir, "myroms").apply { if (!exists()) mkdirs() }

    fun list(): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, null)
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val path = o.optString("path")
                if (path.isBlank() || !File(path).exists()) return@mapNotNull null
                Entry(
                    id = o.optString("id", File(path).name),
                    name = o.optString("name", File(path).name),
                    path = path,
                    twoPlayer = o.optBoolean("twoPlayer", false),
                    sizeBytes = o.optLong("size", File(path).length()),
                )
            }
        } catch (e: Exception) {
            Log.w("MyGames", "خواندن فهرست بازی‌ها ناموفق: ${e.message}")
            emptyList()
        }
    }

    /** فایل SAF را داخل پوشه‌ی برنامه کپی می‌کند و به فهرست اضافه می‌کند. */
    fun import(uri: Uri, displayName: String): Entry? {
        return try {
        val safe = sanitize(displayName)
        val target = uniqueFile(safe)
        context.contentResolver.openInputStream(uri)?.use { ins ->
            target.outputStream().use { outs -> ins.copyTo(outs, 64 * 1024) }
        } ?: return null

        if (target.length() < 1024) { // فایل خالی/نامعتبر
            target.delete()
            null
        } else {
            val entry = Entry(
                id = target.name,
                name = displayName,
                path = target.absolutePath,
                twoPlayer = false,
                sizeBytes = target.length(),
            )
            save(list() + entry)
            entry
        }
    } catch (e: Exception) {
        Log.w("MyGames", "افزودن ROM ناموفق: ${e.message}")
        null
    }
    }

    fun delete(entry: Entry) {
        try { entry.file.delete() } catch (_: Exception) {}
        save(list().filterNot { it.id == entry.id })
    }

    fun setTwoPlayer(entry: Entry, value: Boolean) {
        save(list().map { if (it.id == entry.id) it.copy(twoPlayer = value) else it })
    }

    fun readBytes(entry: Entry): ByteArray? = try {
        entry.file.readBytes()
    } catch (e: Exception) {
        Log.w("MyGames", "خواندن ROM ناموفق: ${e.message}")
        null
    }

    // ----------------- internals -----------------

    private fun save(entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("name", e.name)
                    put("path", e.path)
                    put("twoPlayer", e.twoPlayer)
                    put("size", e.sizeBytes)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("[^\\p{L}\\p{N}._\\-]"), "_").trim('_')
        return cleaned.ifBlank { "rom_${System.currentTimeMillis()}" }
    }

    private fun uniqueFile(name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "${stem}_$i" else "${stem}_$i.$ext")
            i++
        }
        return f
    }

    private companion object {
        const val PREFS = "my_games"
        const val KEY_LIST = "list_v1"
    }
}
