package xyz.beyoneer.gossipaudio.speech

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.URL
import java.security.MessageDigest

/** Explicit, integrity-pinned downloads; text is never sent to these servers. */
object VoiceDownloads {
    private const val CORI = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-cori-medium.tar.bz2"
    private const val TELUGU = "https://github.com/jesusanswersapp/jesusanswers/releases/download/voices-v1/te_IN-padmavathi-medium--"
    private const val CORI_HASH = "49c9a5361bbdd95d7ca9687c4de11e5908481f65e7c7c368960df79949fdac2b"
    private const val TELUGU_HASH = "104d1d0b1d88bd243b3543a208bd98405c69e3d77f05a18055359ce867a5649b"
    private const val TOKENS_HASH = "ef3a7e4a8d1af0c9d4dc45aaae1a6242ebe24a7ed6f3d025a49eb29682784c6d"

    fun directory(context: Context, id: String): File {
        require(id == "cori" || id == "padmavathi")
        return File(context.filesDir, "voices/$id")
    }
    fun installed(context: Context, id: String): Boolean = id == "riya" || try {
        val dir = directory(context, id)
        File(dir, ".complete").isFile && File(dir, "model.onnx").length() > 1000000 && File(dir, "tokens.txt").length() > 0
    } catch (_: IllegalArgumentException) { false }

    @Synchronized fun download(context: Context, id: String) {
        if (installed(context, id)) return
        val dir = directory(context, id)
        val tmp = File(dir.parentFile, "$id.pending")
        tmp.deleteRecursively()
        check(tmp.mkdirs()) { "Cannot create voice directory." }
        try {
            if (id == "cori") {
                val archive = File(tmp, "voice.tar.bz2")
                fetch(CORI, archive, CORI_HASH, 70_000_000)
                TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
                    var model = false
                    var tokens = false
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        if (!entry.isFile) continue
                        val leaf = entry.name.substringAfterLast('/')
                        val target = when (leaf) {
                            "en_GB-cori-medium.onnx" -> { check(!model); model = true; File(tmp, "model.onnx") }
                            "tokens.txt" -> { check(!tokens); tokens = true; File(tmp, "tokens.txt") }
                            else -> null
                        }
                        if (target != null) {
                            check(entry.size in 1..70_000_000) { "Invalid voice archive." }
                            target.outputStream().use { tar.copyTo(it) }
                        }
                    }
                    check(model && tokens) { "Voice archive is incomplete." }
                }
                archive.delete()
            } else {
                fetch(TELUGU + "te_IN-padmavathi-medium.onnx", File(tmp, "model.onnx"), TELUGU_HASH, 70_000_000)
                fetch(TELUGU + "tokens.txt", File(tmp, "tokens.txt"), TOKENS_HASH, 100_000)
            }
            File(tmp, ".complete").writeText("1")
            if (dir.exists()) check(dir.deleteRecursively()) { "Cannot replace voice files." }
            check(tmp.renameTo(dir)) { "Cannot install voice files." }
        } finally { tmp.deleteRecursively() }
    }

    private fun fetch(url: String, target: File, hash: String, maxBytes: Long) {
        val digest = MessageDigest.getInstance("SHA-256")
        var current = URL(url)
        var redirects = 0
        while (true) {
            check(current.protocol == "https") { "Insecure download rejected." }
            val connection = current.openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    check(++redirects <= 8) { "Too many redirects." }
                    current = URL(current, connection.getHeaderField("Location") ?: error("Invalid redirect."))
                    continue
                }
                check(code == 200) { "Download failed (HTTP $code)." }
                var count = 0L
                connection.inputStream.use { input -> target.outputStream().use { output ->
                    val buf = ByteArray(32768)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        count += n
                        check(count <= maxBytes) { "Voice download is too large." }
                        digest.update(buf, 0, n)
                        output.write(buf, 0, n)
                    }
                } }
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                check(actual == hash) { "Voice checksum mismatch; download rejected." }
                return
            } finally { connection.disconnect() }
        }
    }
}
