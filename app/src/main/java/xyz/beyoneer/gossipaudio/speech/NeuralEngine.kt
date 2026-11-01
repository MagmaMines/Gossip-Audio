package xyz.beyoneer.gossipaudio.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import xyz.beyoneer.gossipaudio.core.Pcm
import java.io.File

/** Piper/VITS through sherpa-onnx. No Android TextToSpeech is used here. */
object NeuralEngine {
    private const val ASSET = "neural"

    fun available(context: Context): Boolean = try {
        context.assets.open("$ASSET/tokens.txt").close()
        context.assets.open("$ASSET/model.onnx").close()
        true
    } catch (_: Exception) { false }

    @Synchronized
    fun render(context: Context, chunks: List<String>, speaker: Int, speed: Float, progress: (Int, Int) -> Unit): Pcm {
        check(available(context)) { "Neural model missing from this build." }
        val data = File(context.filesDir, "neural-espeak-v1")
        if (!File(data, ".complete").exists()) {
            copyAssetTree(context, "$ASSET/espeak-ng-data", data)
            File(data, ".complete").writeText("ok")
        }
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$ASSET/model.onnx",
                    tokens = "$ASSET/tokens.txt",
                    dataDir = data.absolutePath
                ),
                numThreads = minOf(4, Runtime.getRuntime().availableProcessors()).coerceAtLeast(1),
                provider = "cpu"
            ),
            maxNumSentences = 1
        )
        val engine = OfflineTts(context.assets, config)
        try {
            val count = engine.numSpeakers()
            check(speaker in 0 until count) { "This model does not contain speaker $speaker (found $count)." }
            val pieces = ArrayList<FloatArray>()
            var sampleRate = 0
            var total = 0
            for ((index, chunk) in chunks.withIndex()) {
                progress(index, chunks.size)
                val audio = engine.generate(chunk, sid = speaker, speed = speed.coerceIn(0.5f, 2f))
                check(audio.samples.isNotEmpty() && audio.sampleRate > 0) { "The neural model returned no audio." }
                check(sampleRate == 0 || sampleRate == audio.sampleRate) { "Inconsistent sample rate." }
                sampleRate = audio.sampleRate
                pieces.add(audio.samples)
                total += audio.samples.size
            }
            check(total > 0) { "The neural model returned no audio." }
            val output = ShortArray(total)
            var pos = 0
            for (piece in pieces) for (sample in piece) {
                output[pos++] = (sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
            return Pcm(sampleRate, 1, output)
        } finally {
            engine.free()
        }
    }

    private fun copyAssetTree(context: Context, path: String, target: File) {
        val entries = context.assets.list(path) ?: error("Missing asset: $path")
        if (entries.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
        } else {
            check(target.isDirectory || target.mkdirs()) { "Cannot create model data directory." }
            entries.forEach { copyAssetTree(context, "$path/$it", File(target, it)) }
        }
    }
}
