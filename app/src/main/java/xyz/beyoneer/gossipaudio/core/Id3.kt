package xyz.beyoneer.gossipaudio.core

import java.io.ByteArrayOutputStream

/**
 * Minimal, standards-compliant ID3v2.3 writer: title, artist, album, comment,
 * year, encoder and an embedded cover picture (APIC). v2.3 is used because
 * every player from Android's MediaScanner to Windows Media Player reads it.
 */
object Id3 {

    data class Tags(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val comment: String? = null,
        val year: String? = null,
        val cover: ByteArray? = null,
        val coverMime: String = "image/jpeg"
    )

    private const val PADDING = 1024

    fun buildTag(tags: Tags): ByteArray {
        val frames = ByteArrayOutputStream()
        tags.title?.let { frames.write(frame("TIT2", textBody(it))) }
        tags.artist?.let { frames.write(frame("TPE1", textBody(it))) }
        tags.album?.let { frames.write(frame("TALB", textBody(it))) }
        tags.comment?.let {
            val body = ByteArrayOutputStream()
            body.write(0x01)                    // UTF-16 w/ BOM
            body.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            body.write("eng".toByteArray(Charsets.ISO_8859_1))
            body.write("GossipAudio".toByteArray(Charsets.UTF_16LE))
            body.write(byteArrayOf(0, 0))
            body.write(it.toByteArray(Charsets.UTF_16LE))
            body.write(byteArrayOf(0, 0))
            frames.write(frame("COMM", body.toByteArray()))
        }
        tags.year?.let { frames.write(frame("TYER", textBody(it))) }
        frames.write(frame("TSSE", textBody("GossipAudio by Beyoneer XYZ")))
        val cover = tags.cover
        if (cover != null && cover.isNotEmpty()) {
            val body = ByteArrayOutputStream()
            body.write(0x00)                                             // encoding: ISO-8859-1
            body.write(tags.coverMime.toByteArray(Charsets.ISO_8859_1))
            body.write(0x00)                                             // MIME terminator
            body.write(0x03)                                             // front cover
            body.write(0x00)                                             // empty description
            body.write(cover)
            frames.write(frame("APIC", body.toByteArray()))
        }
        val frameBytes = frames.toByteArray()
        val bodySize = frameBytes.size + PADDING
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 0x03
        header[4] = 0x00
        header[5] = 0x00
        writeSyncSafe(header, 6, bodySize)
        val out = ByteArray(10 + bodySize)
        System.arraycopy(header, 0, out, 0, 10)
        System.arraycopy(frameBytes, 0, out, 10, frameBytes.size)
        return out
    }

    fun attachToMp3(mp3: ByteArray, tags: Tags): ByteArray {
        val tag = buildTag(tags)
        val out = ByteArray(tag.size + mp3.size)
        System.arraycopy(tag, 0, out, 0, tag.size)
        System.arraycopy(mp3, 0, out, tag.size, mp3.size)
        return out
    }

    /** Reads back just the tag header (used by tests + exporter diagnostics). */
    fun hasId3(b: ByteArray): Boolean =
        b.size > 10 && b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()

    fun tagSize(b: ByteArray): Int {
        if (!hasId3(b)) return 0
        val size = ((b[6].toInt() and 0x7F) shl 21) or
            ((b[7].toInt() and 0x7F) shl 14) or
            ((b[8].toInt() and 0x7F) shl 7) or
            (b[9].toInt() and 0x7F)
        return size + 10
    }

    private fun textBody(s: String): ByteArray {
        val ascii = s.all { it.code in 32..126 }
        return if (ascii) {
            val bytes = s.toByteArray(Charsets.ISO_8859_1)
            val body = ByteArray(bytes.size + 2)
            body[0] = 0x00
            System.arraycopy(bytes, 0, body, 1, bytes.size)
            body[bytes.size + 1] = 0x00
            body
        } else {
            val text = s.toByteArray(Charsets.UTF_16LE)
            val body = ByteArray(text.size + 5)
            body[0] = 0x01
            body[1] = 0xFF.toByte()   // little-endian BOM
            body[2] = 0xFE.toByte()
            System.arraycopy(text, 0, body, 3, text.size)
            body[text.size + 3] = 0x00
            body[text.size + 4] = 0x00
            body
        }
    }

    private fun frame(id: String, body: ByteArray): ByteArray {
        val out = ByteArray(10 + body.size)
        for (i in 0 until 4) out[i] = id[i].code.toByte()
        out[4] = ((body.size shr 24) and 0xFF).toByte()
        out[5] = ((body.size shr 16) and 0xFF).toByte()
        out[6] = ((body.size shr 8) and 0xFF).toByte()
        out[7] = (body.size and 0xFF).toByte()
        out[8] = 0
        out[9] = 0
        System.arraycopy(body, 0, out, 10, body.size)
        return out
    }

    private fun writeSyncSafe(buf: ByteArray, off: Int, value: Int) {
        buf[off] = ((value shr 21) and 0x7F).toByte()
        buf[off + 1] = ((value shr 14) and 0x7F).toByte()
        buf[off + 2] = ((value shr 7) and 0x7F).toByte()
        buf[off + 3] = (value and 0x7F).toByte()
    }
}
