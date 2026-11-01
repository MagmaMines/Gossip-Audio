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
    private const val LJ = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ljspeech-medium.tar.bz2"
    private const val LJ_HASH = "3dfb4b759d8be032a4903a9538d128b0fda2a06ab1de6cbc2d93a97e2dd83dba"
    private const val TELUGU = "https://github.com/jesusanswersapp/jesusanswers/releases/download/voices-v1/te_IN-padmavathi-medium--"
    private const val CORI_HASH = "49c9a5361bbdd95d7ca9687c4de11e5908481f65e7c7c368960df79949fdac2b"
    private const val TELUGU_HASH = "104d1d0b1d88bd243b3543a208bd98405c69e3d77f05a18055359ce867a5649b"
    private const val TOKENS_HASH = "ef3a7e4a8d1af0c9d4dc45aaae1a6242ebe24a7ed6f3d025a49eb29682784c6d"

    private const val SUPER_HASH = "82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427"
    private const val ZH_HASH = "b78e1ac42b852e839cceaafbe88f66806bf5860992d1d7b9a3f2ad1198427aed"
    private const val RELEASES = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
    private val superFiles = setOf("duration_predictor.int8.onnx", "text_encoder.int8.onnx", "vector_estimator.int8.onnx", "vocoder.int8.onnx", "tts.json", "unicode_indexer.bin", "voice.bin")
    private val chineseFiles = setOf("model.onnx", "voices.bin", "tokens.txt", "lexicon-us-en.txt", "lexicon-zh.txt")
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()
    private val active = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun packId(id: String): String = when (id) {
        "hi_super", "ja_super", "fr_super" -> "supertonic3"
        "zh_kokoro" -> "kokoro11"
        "riya", "cori", "ljspeech", "padmavathi" -> id
        else -> throw IllegalArgumentException("Unknown voice")
    }
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyChanged() { main.post { listeners.forEach { it() } } }
    fun downloading(id: String): Boolean = active.contains(packId(id))
    fun downloadDescription(id: String): String = when (packId(id)) {
        "supertonic3" -> "One shared download (~129 MB) unlocks Hindi, Japanese and French. OpenRAIL-M use restrictions apply."
        "kokoro11" -> "The full-precision Mandarin voice download is ~365 MB."
        else -> "This voice download is approximately 65–70 MB."
    }
    fun directory(context: Context, id: String): File = File(context.filesDir, "voices/${packId(id)}")
    fun installed(context: Context, id: String): Boolean = id == "riya" || try {
        val dir = directory(context, id)
        val required = when (packId(id)) {
            "supertonic3" -> superFiles
            "kokoro11" -> chineseFiles
            else -> setOf("model.onnx", "tokens.txt")
        }
        File(dir, ".complete").isFile && required.all { File(dir, it).isFile && File(dir, it).length() > 0 }
    } catch (_: IllegalArgumentException) { false }

    @Synchronized fun download(context: Context, id: String) {
        if (installed(context, id)) { notifyChanged(); return }
        val pack = packId(id)
        active.add(pack)
        notifyChanged()
        val dir = directory(context, id)
        val tmp = File(dir.parentFile, "$pack.pending")
        tmp.deleteRecursively()
        try {
            check(tmp.mkdirs()) { "Cannot create voice directory." }
            when (pack) {
                "cori", "ljspeech" -> {
                    val files = if (pack == "cori") mapOf("en_GB-cori-medium.onnx" to "model.onnx", "tokens.txt" to "tokens.txt")
                        else mapOf("en_US-ljspeech-medium.onnx" to "model.onnx", "tokens.txt" to "tokens.txt")
                    installArchive(tmp, if (pack == "cori") CORI else LJ, if (pack == "cori") CORI_HASH else LJ_HASH, 70_000_000, files)
                }
                "supertonic3" -> installArchive(tmp, RELEASES + "sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2", SUPER_HASH, 140_000_000, superFiles.associateWith { it })
                "kokoro11" -> installArchive(tmp, RELEASES + "kokoro-multi-lang-v1_1.tar.bz2", ZH_HASH, 380_000_000, chineseFiles.associateWith { it })
                "padmavathi" -> {
                    fetch(TELUGU + "te_IN-padmavathi-medium.onnx", File(tmp, "model.onnx"), TELUGU_HASH, 70_000_000)
                    fetch(TELUGU + "tokens.txt", File(tmp, "tokens.txt"), TOKENS_HASH, 100_000)
                }
                else -> error("Unsupported voice")
            }
            File(tmp, ".complete").writeText("1")
            if (dir.exists()) check(dir.deleteRecursively()) { "Cannot replace voice files." }
            check(tmp.renameTo(dir)) { "Cannot install voice files." }
        } finally {
            tmp.deleteRecursively()
            active.remove(pack)
            notifyChanged()
        }
    }

    private fun installArchive(tmp: File, url: String, hash: String, maxBytes: Long, files: Map<String, String>) {
        val archive = File(tmp, "voice.tar.bz2")
        fetch(url, archive, hash, maxBytes)
        val found = mutableSetOf<String>()
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                if (!entry.isFile) continue
                val leaf = entry.name.substringAfterLast('/')
                if (leaf == "LICENSE" && !File(tmp, "LICENSE").exists()) {
                    check(entry.size in 1..100_000) { "Invalid license file." }
                    File(tmp, "LICENSE").outputStream().use { tar.copyTo(it) }
                    continue
                }
                val target = files[leaf] ?: continue
                check(found.add(leaf)) { "Duplicate voice file." }
                check(entry.size in 1..400_000_000) { "Invalid voice archive." }
                File(tmp, target).outputStream().use { tar.copyTo(it) }
            }
        }
        check(found == files.keys) { "Voice archive is incomplete: ${files.keys - found}" }
        archive.delete()
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
