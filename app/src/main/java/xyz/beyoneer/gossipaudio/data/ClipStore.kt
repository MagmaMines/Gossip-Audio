package xyz.beyoneer.gossipaudio.data

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * Disk-backed library of generated clips.
 *
 * Layout:
 *   files/clips/<id>.<ext>      audio
 *   files/covers/<id>.jpg       optional artwork
 *   files/clips.json            metadata (atomic replace, never half-written)
 */
class ClipStore(private val context: Context) {

    private val lock = Any()
    private val clips = ArrayList<Clip>()
    private val listeners = ArrayList<() -> Unit>()

    val audioDir: File = File(context.filesDir, "clips").apply { mkdirs() }
    val coverDir: File = File(context.filesDir, "covers").apply { mkdirs() }
    val exportDir: File = File(context.filesDir, "exports").apply { mkdirs() }
    private val indexFile = File(context.filesDir, "clips.json")

    init {
        load()
    }

    fun observe(listener: () -> Unit) {
        synchronized(lock) { listeners.add(listener) }
    }

    fun unobserve(listener: () -> Unit) {
        synchronized(lock) { listeners.remove(listener) }
    }

    private fun notifyChanged() {
        val copy = synchronized(lock) { listeners.toList() }
        for (l in copy) {
            try {
                l()
            } catch (_: Throwable) {
            }
        }
    }

    fun all(): List<Clip> = synchronized(lock) { clips.sortedByDescending { it.createdAt } }

    fun liked(): List<Clip> = all().filter { it.liked }

    fun byId(id: String): Clip? = synchronized(lock) { clips.firstOrNull { it.id == id } }

    fun fileFor(clip: Clip): File = File(audioDir, clip.fileName)

    fun coverFor(clip: Clip): File? {
        val name = clip.coverName ?: return null
        val f = File(coverDir, name)
        return if (f.exists()) f else null
    }

    fun search(query: String): List<Clip> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all()
        return all().filter {
            it.title.lowercase().contains(q) || it.text.lowercase().contains(q) ||
                it.voiceId.lowercase().contains(q)
        }
    }

    fun add(clip: Clip) {
        synchronized(lock) {
            clips.add(clip)
            persistLocked()
        }
        notifyChanged()
    }

    fun update(block: (Clip) -> Unit) {
        synchronized(lock) {
            clips.forEach(block)
            persistLocked()
        }
        notifyChanged()
    }

    fun toggleLike(id: String) {
        synchronized(lock) {
            clips.firstOrNull { it.id == id }?.let { it.liked = !it.liked }
            persistLocked()
        }
        notifyChanged()
    }

    fun rename(id: String, newTitle: String) {
        val clean = newTitle.trim().ifBlank { "Untitled gossip" }
        synchronized(lock) {
            clips.firstOrNull { it.id == id }?.let { it.title = clean }
            persistLocked()
        }
        notifyChanged()
    }

    fun delete(id: String) {
        synchronized(lock) {
            val clip = clips.firstOrNull { it.id == id }
            if (clip != null) {
                clips.remove(clip)
                fileFor(clip).delete()
                clip.coverName?.let { File(coverDir, it).delete() }
                persistLocked()
            }
        }
        notifyChanged()
    }

    fun totalBytes(): Long {
        var total = 0L
        audioDir.listFiles()?.forEach { total += it.length() }
        coverDir.listFiles()?.forEach { total += it.length() }
        return total
    }

    /** Removes audio files that no longer have metadata (e.g. after a crash). */
    fun sweepOrphans(): Int {
        var removed = 0
        val known = synchronized(lock) { clips.map { it.fileName }.toSet() }
        audioDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name !in known) {
                f.delete()
                removed++
            }
        }
        return removed
    }

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    private fun load() {
        synchronized(lock) {
            clips.clear()
            if (!indexFile.exists()) return
            try {
                val array = JSONArray(indexFile.readText())
                for (i in 0 until array.length()) {
                    val clip = Clip.fromJson(array.getJSONObject(i))
                    if (clip.id.isNotBlank()) clips.add(clip)
                }
            } catch (_: Throwable) {
                // A corrupted index must never brick the app: keep whatever is
                // readable, the orphan sweep cleans up the rest.
            }
        }
    }

    private fun persistLocked() {
        try {
            val array = JSONArray()
            clips.forEach { array.put(it.toJson()) }
            val tmp = File(context.filesDir, "clips.json.tmp")
            tmp.writeText(array.toString())
            if (indexFile.exists()) indexFile.delete()
            tmp.renameTo(indexFile)
        } catch (_: Throwable) {
        }
    }
}
