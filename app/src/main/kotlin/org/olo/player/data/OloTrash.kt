package org.olo.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.olo.player.art.MediaTagSaver
import java.io.File
import java.util.UUID

/** 휴지통에 들어간 한 항목: 지금 있는 자리(휴지통)·원래 자리·삭제 시각. */
data class TrashEntry(val trashedPath: String, val originalPath: String, val deletedAt: Long)

/**
 * 삭제 안전망(휴지통). 삭제하면 바로 지우지 않고 숨김 폴더([DIR])로 옮기고, 원래 경로·시각을
 * 기록해 '되돌리기'(restore)를 지원한다. 같은 볼륨 rename이라 큰 파일도 즉시 들어간다. 오래된
 * 항목은 [cleanup]으로 영구 정리한다. 로컬 저장소 전용(원격은 추후).
 */
object OloTrash {
    private const val PREFS = "olo_player"
    private const val KEY = "trash_entries"
    private const val DIR = ".olo_trash"
    private const val MAX_AGE = 7L * 24 * 60 * 60 * 1000 // 7일 지나면 자동 영구삭제

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    private fun storageRoot() = android.os.Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")

    private fun trashDir(): File {
        val d = File(storageRoot(), DIR)
        if (!d.exists()) {
            d.mkdirs()
            // 미디어 스캐너가 휴지통 내용을 색인하지 않도록(갤러리 등에 안 뜨게).
            runCatching { File(d, ".nomedia").createNewFile() }
        }
        return d
    }

    /** [file]을 휴지통으로 옮긴다. 성공 시 되돌릴 수 있는 [TrashEntry], 실패 시 null. */
    fun trash(context: Context, file: File): TrashEntry? = runCatching {
        if (!file.exists()) return null
        val target = File(trashDir(), "${UUID.randomUUID()}__${file.name}")
        val moved = file.renameTo(target) || run {
            // 볼륨이 다르면 rename이 실패 -- 복사 후 원본 삭제로 대체.
            if (file.isDirectory) file.copyRecursively(target, false) else file.copyTo(target, false)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
        if (!moved) return null
        val entry = TrashEntry(target.path, file.path, System.currentTimeMillis())
        write(context, read(context) + entry)
        MediaTagSaver.rescan(context, listOf(file))
        entry
    }.getOrNull()

    /** [entry]를 원래 자리로 되돌린다. 성공 true. */
    fun restore(context: Context, entry: TrashEntry): Boolean = runCatching {
        val src = File(entry.trashedPath)
        val dst = File(entry.originalPath)
        if (!src.exists()) { removeRecord(context, entry); return false }
        dst.parentFile?.mkdirs()
        val ok = src.renameTo(dst) || run {
            if (src.isDirectory) src.copyRecursively(dst, false) else src.copyTo(dst, false)
            if (src.isDirectory) src.deleteRecursively() else src.delete()
        }
        if (ok) {
            removeRecord(context, entry)
            MediaTagSaver.rescan(context, listOf(dst))
        }
        ok
    }.getOrDefault(false)

    /** 7일 지난(또는 사라진) 휴지통 항목을 영구 삭제한다. 앱 시작 때 한 번 부른다. */
    fun cleanup(context: Context) {
        val now = System.currentTimeMillis()
        val keep = ArrayList<TrashEntry>()
        for (e in read(context)) {
            val f = File(e.trashedPath)
            if (now - e.deletedAt > MAX_AGE || !f.exists()) {
                runCatching { if (f.isDirectory) f.deleteRecursively() else f.delete() }
            } else {
                keep.add(e)
            }
        }
        write(context, keep)
    }

    private fun read(context: Context): List<TrashEntry> = runCatching {
        val arr = JSONArray(prefs(context).getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                runCatching { TrashEntry(o.getString("t"), o.getString("o"), o.optLong("d")) }.getOrNull()
            }
        }
    }.getOrDefault(emptyList())

    private fun write(context: Context, items: List<TrashEntry>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("t", it.trashedPath).put("o", it.originalPath).put("d", it.deletedAt)) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun removeRecord(context: Context, e: TrashEntry) {
        write(context, read(context).filterNot { it.trashedPath == e.trashedPath })
    }
}
