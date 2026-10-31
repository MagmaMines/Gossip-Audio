package xyz.beyoneer.gossipaudio.data

import org.json.JSONObject

enum class AudioFormat(val extension: String, val mime: String, val label: String) {
    WAV("wav", "audio/wav", "WAV"),
    MP3("mp3", "audio/mpeg", "MP3"),
    M4A("m4a", "audio/mp4", "M4A");

    companion object {
        fun from(id: String?): AudioFormat = values().firstOrNull { it.name.equals(id, true) } ?: MP3
    }
}

/**
 * One generated piece of gossip. The audio lives next to the metadata in the
 * app's private storage; exporting copies it into MediaStore/Downloads.
 */
data class Clip(
    val id: String,
    var title: String,
    val text: String,
    val voiceId: String,
    val styleId: String,
    val engine: String,
    val format: String,
    val sampleRate: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val createdAt: Long,
    var liked: Boolean,
    val fileName: String,
    var coverName: String?,
    var lastExportUri: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("text", text)
        put("voiceId", voiceId)
        put("styleId", styleId)
        put("engine", engine)
        put("format", format)
        put("sampleRate", sampleRate)
        put("durationMs", durationMs)
        put("sizeBytes", sizeBytes)
        put("createdAt", createdAt)
        put("liked", liked)
        put("fileName", fileName)
        put("coverName", coverName ?: "")
        put("lastExportUri", lastExportUri ?: "")
    }

    companion object {
        fun fromJson(o: JSONObject): Clip = Clip(
            id = o.optString("id"),
            title = o.optString("title", "Untitled gossip"),
            text = o.optString("text", ""),
            voiceId = o.optString("voiceId", "riya"),
            styleId = o.optString("styleId", "natural"),
            engine = o.optString("engine", "system"),
            format = o.optString("format", "mp3"),
            sampleRate = o.optInt("sampleRate", 22050),
            durationMs = o.optLong("durationMs", 0L),
            sizeBytes = o.optLong("sizeBytes", 0L),
            createdAt = o.optLong("createdAt", 0L),
            liked = o.optBoolean("liked", false),
            fileName = o.optString("fileName", ""),
            coverName = o.optString("coverName", "").ifBlank { null },
            lastExportUri = o.optString("lastExportUri", "").ifBlank { null }
        )
    }
}
