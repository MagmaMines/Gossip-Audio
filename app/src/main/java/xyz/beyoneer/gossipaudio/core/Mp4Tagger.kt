package xyz.beyoneer.gossipaudio.core

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Adds iTunes-style metadata (title / artist / cover art) to an M4A written by
 * Android's MediaMuxer.
 *
 * MediaMuxer always appends `moov` after `mdat`, so we can safely splice a
 * `udta` atom inside `moov` without touching any chunk offsets. If a file is
 * laid out differently we refuse to touch it instead of corrupting it.
 */
object Mp4Tagger {

    data class Meta(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val comment: String? = null,
        val genre: String? = null,
        val year: String? = null,
        val coverJpeg: ByteArray? = null,
        val coverPng: ByteArray? = null
    )

    private const val COPYRIGHT = "\u00A9"

    fun tagFile(file: File, meta: Meta): Boolean {
        if (!file.exists() || file.length() < 32) return false
        return try {
            val bytes = file.readBytes()
            val tagged = tag(bytes, meta) ?: return false
            file.writeBytes(tagged)
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun tag(bytes: ByteArray, meta: Meta): ByteArray? {
        val boxes = topLevelBoxes(bytes) ?: return null
        val moov = boxes.firstOrNull { it.type == "moov" } ?: return null
        val mdat = boxes.firstOrNull { it.type == "mdat" }
        if (mdat != null && moov.offset < mdat.offset) {
            // moov before mdat: inserting bytes would invalidate stco offsets.
            return null
        }
        val moovBody = bytes.copyOfRange(moov.offset + 8, moov.offset + moov.size)
        if (containsBox(moovBody, "udta")) return null
        val udta = buildUdta(meta)
        val newBody = ByteArray(moovBody.size + udta.size)
        System.arraycopy(moovBody, 0, newBody, 0, moovBody.size)
        System.arraycopy(udta, 0, newBody, moovBody.size, udta.size)
        val newMoov = box("moov", newBody)
        val out = ByteArray(bytes.size - moov.size + newMoov.size)
        System.arraycopy(bytes, 0, out, 0, moov.offset)
        System.arraycopy(newMoov, 0, out, moov.offset, newMoov.size)
        val tailStart = moov.offset + moov.size
        System.arraycopy(bytes, tailStart, out, moov.offset + newMoov.size, bytes.size - tailStart)
        return out
    }

    private data class Box(val type: String, val offset: Int, val size: Int)

    private fun topLevelBoxes(b: ByteArray): List<Box>? {
        val out = ArrayList<Box>()
        var p = 0
        while (p + 8 <= b.size) {
            var size = Wav.getIntLE(b, p)
            val type = String(b, p + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1 && p + 16 <= b.size) {
                val hi = Wav.getIntLE(b, p + 8).toLong() and 0xFFFFFFFFL
                val lo = Wav.getIntLE(b, p + 12).toLong() and 0xFFFFFFFFL
                size = ((hi shl 32) or lo).toInt()
                header = 16
            }
            if (size < header || p + size > b.size) return out
            out.add(Box(type, p, size))
            p += size
        }
        return out
    }

    private fun containsBox(body: ByteArray, type: String): Boolean {
        var p = 0
        while (p + 8 <= body.size) {
            val size = Wav.getIntLE(body, p)
            if (size < 8 || p + size > body.size) return false
            val t = String(body, p + 4, 4, Charsets.ISO_8859_1)
            if (t == type) return true
            p += size
        }
        return false
    }

    private fun buildUdta(meta: Meta): ByteArray {
        val ilst = ByteArrayOutputStream()
        meta.title?.let { ilst.write(ilstItem(COPYRIGHT + "nam", dataAtom(1, it.toByteArray(Charsets.UTF_8)))) }
        meta.artist?.let { ilst.write(ilstItem(COPYRIGHT + "ART", dataAtom(1, it.toByteArray(Charsets.UTF_8)))) }
        meta.album?.let { ilst.write(ilstItem(COPYRIGHT + "alb", dataAtom(1, it.toByteArray(Charsets.UTF_8)))) }
        meta.comment?.let { ilst.write(ilstItem(COPYRIGHT + "cmt", dataAtom(1, it.toByteArray(Charsets.UTF_8)))) }
        meta.genre?.let { ilst.write(ilstItem(COPYRIGHT + "gen", dataAtom(1, it.toByteArray(Charsets.UTF_8)))) }
        meta.year?.let { ilst.write(ilstItem(COPYRIGHT + "day", dataAtom(1, it.toByteArray(Charsets.UTF_8)))) }
        ilst.write(ilstItem(COPYRIGHT + "too", dataAtom(1, "GossipAudio by Beyoneer XYZ".toByteArray(Charsets.UTF_8))))
        val cover = meta.coverJpeg ?: meta.coverPng
        if (cover != null && cover.isNotEmpty()) {
            val type = if (meta.coverJpeg != null) 13 else 14
            ilst.write(ilstItem("covr", dataAtom(type, cover)))
        }
        val hdlr = buildHdlr()
        val metaBox = fullBox("meta", 0, 0, concat(hdlr, box("ilst", ilst.toByteArray())))
        return box("udta", metaBox)
    }

    private fun buildHdlr(): ByteArray {
        val payload = ByteArray(4 + 4 + 4 + 12 + 1)
        // pre_defined = 0
        payload[4] = 'm'.code.toByte()
        payload[5] = 'd'.code.toByte()
        payload[6] = 'i'.code.toByte()
        payload[7] = 'r'.code.toByte()
        payload[8] = 'a'.code.toByte()
        payload[9] = 'p'.code.toByte()
        payload[10] = 'p'.code.toByte()
        payload[11] = 'l'.code.toByte()
        return fullBox("hdlr", 0, 0, payload)
    }

    private fun ilstItem(name: String, data: ByteArray): ByteArray {
        val nameBytes = name.toByteArray(Charsets.ISO_8859_1)
        val typeBytes = if (nameBytes.size == 4) nameBytes else {
            val t = ByteArray(4)
            System.arraycopy(nameBytes, 0, t, 0, minOf(4, nameBytes.size))
            t
        }
        return box(typeBytes, data)
    }

    private fun dataAtom(typeIndicator: Int, data: ByteArray): ByteArray {
        val payload = ByteArray(8 + data.size)
        payload[0] = ((typeIndicator shr 24) and 0xFF).toByte()
        payload[1] = ((typeIndicator shr 16) and 0xFF).toByte()
        payload[2] = ((typeIndicator shr 8) and 0xFF).toByte()
        payload[3] = (typeIndicator and 0xFF).toByte()
        // locale (4 bytes) stays zero
        System.arraycopy(data, 0, payload, 8, data.size)
        return box("data", payload)
    }

    private fun fullBox(type: String, version: Int, flags: Int, payload: ByteArray): ByteArray {
        val body = ByteArray(4 + payload.size)
        body[0] = version.toByte()
        body[1] = ((flags shr 16) and 0xFF).toByte()
        body[2] = ((flags shr 8) and 0xFF).toByte()
        body[3] = (flags and 0xFF).toByte()
        System.arraycopy(payload, 0, body, 4, payload.size)
        return box(type, body)
    }

    private fun box(type: String, payload: ByteArray): ByteArray =
        box(type.toByteArray(Charsets.ISO_8859_1), payload)

    private fun box(typeBytes: ByteArray, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        val size = out.size
        out[0] = ((size shr 24) and 0xFF).toByte()
        out[1] = ((size shr 16) and 0xFF).toByte()
        out[2] = ((size shr 8) and 0xFF).toByte()
        out[3] = (size and 0xFF).toByte()
        System.arraycopy(typeBytes, 0, out, 4, 4)
        System.arraycopy(payload, 0, out, 8, payload.size)
        return out
    }

    private fun concat(a: ByteArray, b: ByteArray): ByteArray {
        val out = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, out, 0, a.size)
        System.arraycopy(b, 0, out, a.size, b.size)
        return out
    }
}
