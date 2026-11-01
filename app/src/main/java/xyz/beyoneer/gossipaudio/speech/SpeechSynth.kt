package xyz.beyoneer.gossipaudio.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import xyz.beyoneer.gossipaudio.core.Pcm
import xyz.beyoneer.gossipaudio.core.StylePreset
import xyz.beyoneer.gossipaudio.core.VoiceProfile
import xyz.beyoneer.gossipaudio.util.Telemetry

/** English neural TTS running locally through the bundled Piper/VITS model. */
class SpeechSynth(private val context: Context) {
    data class Outcome(val pcm: Pcm, val engine: String, val note: String?, val tookMs: Long,
                       val peakCpu: Float, val peakMem: Float, val samples: List<Pair<Float, Float>>)
    interface Callback {
        fun onProgress(fraction: Float, label: String)
        fun onSuccess(outcome: Outcome)
        fun onError(message: String)
    }
    private val main = Handler(Looper.getMainLooper())

    fun synthesize(text: String, voice: VoiceProfile, style: StylePreset, pitch: Float, rate: Float, callback: Callback) {
        val clean = text.trim()
        if (clean.isEmpty()) { callback.onError("Type some text first."); return }
        // Guard against selecting a voice for an unrelated writing system.
        val allowed = when (voice.id) {
            "padmavathi" -> setOf(Character.UnicodeScript.TELUGU)
            "hi_super" -> setOf(Character.UnicodeScript.DEVANAGARI, Character.UnicodeScript.LATIN)
            "ja_super" -> setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.LATIN)
            "zh_kokoro" -> setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.LATIN)
            else -> setOf(Character.UnicodeScript.LATIN)
        }
        if (clean.codePoints().anyMatch { Character.isLetter(it) && Character.UnicodeScript.of(it) !in allowed && Character.UnicodeScript.of(it) != Character.UnicodeScript.COMMON && Character.UnicodeScript.of(it) != Character.UnicodeScript.INHERITED }) {
            callback.onError("Enter text supported by the selected ${voice.region} voice.")
            return
        }
        val speaker = 0
        val sampler = Telemetry.Sampler(Telemetry(context)).also { it.start() }
        val started = System.currentTimeMillis()
        val appContext = context.applicationContext
        Thread {
            try {
                val chunks = chunkText(clean, 220)
                val pcm = NeuralEngine.render(appContext, chunks, speaker, rate * style.rateFactor, voice.id) { index, total ->
                    main.post { callback.onProgress(0.15f + 0.75f * index / total, "Neural voice is speaking…") }
                }
                sampler.stop()
                val result = Outcome(pcm, "neural", null, System.currentTimeMillis() - started,
                    sampler.peakCpu(), sampler.peakMem(), sampler.samples())
                main.post { callback.onProgress(1f, "Ready"); callback.onSuccess(result) }
            } catch (t: Throwable) {
                sampler.stop()
                main.post { callback.onError("Neural synthesis failed: ${t.message ?: t.javaClass.simpleName}") }
            }
        }.start()
    }

    fun chunkText(text: String, maxChars: Int): List<String> {
        require(maxChars >= 2)
        val out = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxChars, text.length)
            if (end < text.length) {
                val punctuation = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ';'), end - 1)
                val space = text.lastIndexOf(' ', end - 1)
                end = if (punctuation >= start + maxChars / 3) punctuation + 1 else if (space > start) space + 1 else end
                if (end < text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
            }
            val chunk = text.substring(start, end).trim()
            if (chunk.isNotEmpty()) out.add(chunk)
            start = end
        }
        return out
    }
}
