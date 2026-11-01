package xyz.beyoneer.gossipaudio.speech

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import xyz.beyoneer.gossipaudio.core.AudioFx
import xyz.beyoneer.gossipaudio.core.NanoVoice
import xyz.beyoneer.gossipaudio.core.Pcm
import xyz.beyoneer.gossipaudio.core.StylePreset
import xyz.beyoneer.gossipaudio.core.TextKit
import xyz.beyoneer.gossipaudio.core.VoiceEngine
import xyz.beyoneer.gossipaudio.core.VoiceGender
import xyz.beyoneer.gossipaudio.core.VoiceProfile
import xyz.beyoneer.gossipaudio.core.VoiceTuning
import xyz.beyoneer.gossipaudio.core.Wav
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Turns text into PCM using the best available on-device engine.
 *
 * 1. The phone's own neural TTS engine (Google/Samsung/…) — natural, offline,
 *    GPU/NNAPI accelerated where available.
 * 2. NanoVoice, GossipAudio's built-in formant engine — always available, so a
 *    missing language pack can never leave the user with a dead button.
 */
class SpeechSynth(private val context: Context) {

    data class Outcome(
        val pcm: Pcm,
        val engine: String,
        val note: String?,
        val tookMs: Long,
        val peakCpu: Float,
        val peakMem: Float,
        val samples: List<Pair<Float, Float>>
    )

    interface Callback {
        fun onProgress(fraction: Float, label: String)
        fun onSuccess(outcome: Outcome)
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())

    fun synthesize(
        text: String,
        voice: VoiceProfile,
        style: StylePreset,
        pitch: Float,
        rate: Float,
        callback: Callback
    ) {
        val clean = applyWordMap(text.trim())
        if (clean.isEmpty()) {
            callback.onError("Type some gossip first \uD83D\uDC40")
            return
        }
        val telemetry = xyz.beyoneer.gossipaudio.util.Telemetry(context)
        val sampler = xyz.beyoneer.gossipaudio.util.Telemetry.Sampler(telemetry).also { it.start() }
        val startedAt = System.currentTimeMillis()
        val systemUsable = voice.engine == VoiceEngine.SYSTEM_TTS && hasSystemEngine()

        // Two different worlds, two different mixes.
        //
        // NanoVoice is a synthesiser: it *needs* voice.pitch to become a woman or a man,
        // so the multipliers stack and the range is wide.
        //
        // A device voice is already a finished human recording at the right pitch and
        // tempo. Stacking 1.05 (style) x 1.12 (voice) x 1.0 (user) on top of it is how
        // 1.0.4 ended up sounding like a cartoon: +3 semitones on a bright female voice.
        // Here the style only nudges, and the clamps are what a human throat can do.
        val finalPitch: Float
        val finalRate: Float
        if (systemUsable) {
            finalPitch = (pitch * style.pitchFactor).coerceIn(0.92f, 1.08f)
            finalRate = (rate * style.rateFactor).coerceIn(0.88f, 1.18f)
        } else {
            finalPitch = (pitch * style.pitchFactor * voice.pitch).coerceIn(0.3f, 2.6f)
            finalRate = (rate * style.rateFactor * voice.rate).coerceIn(0.4f, 2.2f)
        }
        if (systemUsable) {
            systemPath(clean, voice, finalPitch, finalRate, callback) { pcm, note ->
                val usable = pcm != null && looksLikeSpeech(pcm, clean)
                if (usable) {
                    finish(pcm!!, "system", note, startedAt, sampler, callback)
                } else {
                    nanoPath(clean, voice, style, finalPitch, finalRate, callback, startedAt, sampler)
                }
            }
        } else {
            nanoPath(clean, voice, style, finalPitch, finalRate, callback, startedAt, sampler)
        }
    }

    /**
     * "Say it your way": the user's own written→spoken pairs, applied before either
     * engine sees the text. Works for names ("Beyoneer" → "Be-yo-neer"), acronyms and
     * whatever else a TTS engine insists on mangling.
     */
    private fun applyWordMap(text: String): String {
        val raw = runCatching { xyz.beyoneer.gossipaudio.data.Prefs(context).wordMap }.getOrNull()
        if (raw.isNullOrBlank()) return text
        var out = text
        raw.split(';').forEach { pair ->
            val bits = pair.split('=', limit = 2)
            if (bits.size == 2) {
                val from = bits[0].trim()
                val to = bits[1].trim()
                if (from.isNotEmpty() && to.isNotEmpty()) {
                    out = out.replace(from, to, ignoreCase = true)
                }
            }
        }
        return out
    }

    // ------------------------------------------------------------- system path

    private fun systemPath(
        text: String,
        voice: VoiceProfile,
        pitch: Float,
        rate: Float,
        callback: Callback,
        done: (Pcm?, String?) -> Unit
    ) {
        val chunks = chunkText(text, 700)
        val results = ArrayList<Pcm>()
        var index = 0
        var finished = false
        val watchdog = Runnable {
            if (!finished) {
                finished = true
                done(null, "System voice timed out")
            }
        }
        main.postDelayed(watchdog, 45_000)

        val onFinal: (Pcm?, String?) -> Unit = { pcm, note ->
            if (!finished) {
                finished = true
                main.removeCallbacks(watchdog)
                done(pcm, note)
            }
        }

        fun next() {
            if (finished) return
            if (index >= chunks.size) {
                if (results.isEmpty()) {
                    onFinal(null, "No audio returned")
                } else {
                    val rate0 = results[0].sampleRate
                    val merged = mergeChunks(results.map { if (it.sampleRate == rate0) it else AudioFx.resample(it, rate0) }, rate0)
                    onFinal(merged, null)
                }
                return
            }
            callback.onProgress(0.15f + 0.75f * (index.toFloat() / chunks.size), "Waking the voice\u2026")
            synthesizeOne(chunks[index], voice, pitch, rate) { pcm, note ->
                if (pcm == null) {
                    onFinal(null, note)
                } else {
                    results.add(pcm)
                    index++
                    next()
                }
            }
        }
        next()
    }

    private fun synthesizeOne(
        text: String,
        voice: VoiceProfile,
        pitch: Float,
        rate: Float,
        done: (Pcm?, String?) -> Unit
    ) {
        var completed = false
        val finishOnce: (Pcm?, String?) -> Unit = { pcm, note ->
            if (!completed) {
                completed = true
                done(pcm, note)
            }
        }
        val outFile = File(context.cacheDir, "tts_${UUID.randomUUID()}.wav")
        var tts: TextToSpeech? = null
        val timeout = Runnable {
            if (!completed) {
                try {
                    tts?.stop()
                    tts?.shutdown()
                } catch (_: Throwable) {
                }
                outFile.delete()
                finishOnce(null, "Voice engine timed out")
            }
        }
        main.postDelayed(timeout, 20_000)

        try {
            tts = TextToSpeech(context) { status ->
                val engine = tts
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    main.removeCallbacks(timeout)
                    finishOnce(null, "No on-device voice engine")
                    return@TextToSpeech
                }
                try {
                    val requested = Locale.forLanguageTag(voice.locale)
                    val chosen = pickLocale(engine, requested)
                    engine.language = chosen
                    pickVoice(engine, chosen, voice.gender)?.let { engine.voice = it }
                    engine.setPitch(pitch)
                    engine.setSpeechRate(rate)
                    val utteranceId = "gossip-" + UUID.randomUUID().toString().take(8)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) {
                            val file = outFile
                            if (!file.exists() || file.length() < 512) {
                                main.removeCallbacks(timeout)
                                try {
                                    engine.shutdown()
                                } catch (_: Throwable) {
                                }
                                finishOnce(null, "Engine returned an empty file")
                                return
                            }
                            Thread {
                                val pcm = readPcm(file)
                                file.delete()
                                main.post {
                                    try {
                                        engine.shutdown()
                                    } catch (_: Throwable) {
                                    }
                                    main.removeCallbacks(timeout)
                                    finishOnce(pcm, if (pcm == null) "Unreadable audio from engine" else null)
                                }
                            }.start()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            main.removeCallbacks(timeout)
                            try {
                                engine.shutdown()
                            } catch (_: Throwable) {
                            }
                            finishOnce(null, "Voice engine error")
                        }

                        override fun onError(id: String?, errorCode: Int) {
                            main.removeCallbacks(timeout)
                            try {
                                engine.shutdown()
                            } catch (_: Throwable) {
                            }
                            finishOnce(null, "Voice engine error ($errorCode)")
                        }
                    })
                    val params = Bundle()
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                    val queued = engine.synthesizeToFile(text, params, outFile, utteranceId)
                    if (queued != TextToSpeech.SUCCESS) {
                        main.removeCallbacks(timeout)
                        try {
                            engine.shutdown()
                        } catch (_: Throwable) {
                        }
                        finishOnce(null, "Engine refused to synthesise")
                    }
                } catch (t: Throwable) {
                    main.removeCallbacks(timeout)
                    try {
                        tts?.shutdown()
                    } catch (_: Throwable) {
                    }
                    finishOnce(null, "Voice engine error: ${t.javaClass.simpleName}")
                }
            }
        } catch (t: Throwable) {
            main.removeCallbacks(timeout)
            finishOnce(null, "Voice engine unavailable")
        }
    }

    private fun pickLocale(tts: TextToSpeech, requested: Locale): Locale {
        val chain = listOf(requested, Locale.US, Locale("en", "IN"), Locale.UK)
        for (locale in chain) {
            val available = try {
                tts.isLanguageAvailable(locale)
            } catch (_: Throwable) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (available == TextToSpeech.LANG_AVAILABLE ||
                available == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                available == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            ) {
                return locale
            }
        }
        return Locale.US
    }

    private fun pickVoice(tts: TextToSpeech, locale: Locale, gender: VoiceGender): android.speech.tts.Voice? {
        val voices = try {
            tts.voices
        } catch (_: Throwable) {
            null
        } ?: return null
        val sameLocale = voices.filter { it.locale.language == locale.language }
        if (sameLocale.isEmpty()) return null
        val female = sameLocale.filter { v ->
            val n = v.name.lowercase()
            (n.contains("female") || n.contains("woman")) && !n.contains("male_")
        }
        val male = sameLocale.filter { v ->
            val n = v.name.lowercase()
            n.contains("#male") || n.contains("_male") || (n.contains("male") && !n.contains("female"))
        }
        return when (gender) {
            VoiceGender.FEMALE -> female.firstOrNull() ?: sameLocale.first()
            VoiceGender.MALE -> male.firstOrNull() ?: sameLocale.first()
            VoiceGender.NONHUMAN -> sameLocale.first()
        }
    }

    /**
     * A voice engine that is installed but has no data pack can "succeed" while
     * writing near-silence or a couple of clicks. That is exactly the bug users
     * reported as "only ticks". Anything that does not look like speech is
     * rejected here and the built-in engine takes over.
     */
    private fun looksLikeSpeech(pcm: Pcm, text: String): Boolean {
        if (pcm.samples.isEmpty()) return false
        val words = TextKit.wordCount(text).coerceAtLeast(1)
        val seconds = pcm.durationMs / 1000.0
        if (seconds < 0.25) return false
        var loud = 0
        var peak = 0
        val window = 512
        var i = 0
        while (i + window <= pcm.samples.size) {
            var sum = 0L
            for (j in i until i + window) {
                val v = pcm.samples[j].toInt()
                if (abs(v) > peak) peak = abs(v)
                sum += v.toLong() * v
            }
            val rms = sqrt(sum.toDouble() / window)
            if (rms > 900) loud++
            i += window
        }
        val voicedRatio = loud.toDouble() / (pcm.samples.size / window).coerceAtLeast(1)
        // plausible speech: some real level, and enough of it for the amount of text
        val expectedSeconds = words / 4.0
        return peak > 2500 && voicedRatio > 0.15 && seconds > expectedSeconds * 0.25
    }

    private fun readPcm(file: File): Pcm? {
        return try {
            val bytes = file.readBytes()
            if (Wav.looksLikeWav(bytes)) {
                Wav.read(bytes)
            } else if (Wav.looksLikeCompressed(bytes)) {
                null
            } else {
                Wav.wrapRawPcm(bytes, 24000, 1)
            }
        } catch (_: Throwable) {
            null
        }
    }

    // --------------------------------------------------------------- nano path

    private fun nanoPath(
        text: String,
        voice: VoiceProfile,
        style: StylePreset,
        pitch: Float,
        rate: Float,
        callback: Callback,
        startedAt: Long,
        sampler: xyz.beyoneer.gossipaudio.util.Telemetry.Sampler
    ) {
        Thread {
            try {
                val chunks = chunkText(text, 420)
                val pieces = ArrayList<Pcm>()
                chunks.forEachIndexed { i, chunk ->
                    main.post { callback.onProgress(0.2f + 0.7f * (i.toFloat() / chunks.size), "NanoVoice is thinking\u2026") }
                    val options = NanoVoice.Options(
                        baseF0 = VoiceTuning.baseF0(voice),
                        formantScale = VoiceTuning.formantScale(voice),
                        breathiness = if (voice.gender == VoiceGender.FEMALE) 0.06 else 0.03,
                        roughness = if (voice.id == "bot") 0.4 else 0.0,
                        vibrato = if (voice.gender == VoiceGender.FEMALE) 0.02 else 0.0,
                        robotic = voice.id == "bot" || style.id == "robo",
                        rate = rate.toDouble(),
                        pitch = pitch.toDouble(),
                        sampleRate = 22050
                    )
                    val pcm = NanoVoice.speak(chunk, options)
                    if (pcm.samples.isNotEmpty()) pieces.add(pcm)
                }
                if (pieces.isEmpty()) {
                    main.post { callback.onError("NanoVoice produced silence. That is a bug — sorry!") }
                    return@Thread
                }
                val rate0 = pieces[0].sampleRate
                val merged = mergeChunks(pieces, rate0)
                finish(merged, "nano", null, startedAt, sampler, callback)
            } catch (t: Throwable) {
                main.post { callback.onError("NanoVoice failed: ${t.message ?: t.javaClass.simpleName}") }
            }
        }.start()
    }

    private fun finish(
        pcm: Pcm,
        engine: String,
        note: String?,
        startedAt: Long,
        sampler: xyz.beyoneer.gossipaudio.util.Telemetry.Sampler,
        callback: Callback
    ) {
        try {
            sampler.stop()
            val prefs = xyz.beyoneer.gossipaudio.data.Prefs(context)
            var polished = pcm
            if (prefs.autoTrim) polished = AudioFx.trimSilence(polished, 180, 40)
            if (prefs.autoNormalize) polished = AudioFx.normalize(polished, -3.0)
            polished = AudioFx.fade(polished, 10, 40)
            val outcome = Outcome(
                pcm = polished,
                engine = engine,
                note = note,
                tookMs = System.currentTimeMillis() - startedAt,
                peakCpu = sampler.peakCpu(),
                peakMem = sampler.peakMem(),
                samples = sampler.samples()
            )
            main.post {
                callback.onProgress(1f, "Ready")
                callback.onSuccess(outcome)
            }
        } catch (t: Throwable) {
            main.post { callback.onError("Post-processing failed: ${t.message}") }
        }
    }

    private fun mergeChunks(chunks: List<Pcm>, sampleRate: Int): Pcm {
        val gap = ShortArray((sampleRate * 0.12).toInt())
        var total = 0
        chunks.forEach { total += it.samples.size + gap.size }
        val out = ShortArray(total)
        var p = 0
        for (c in chunks) {
            System.arraycopy(c.samples, 0, out, p, c.samples.size)
            p += c.samples.size
            System.arraycopy(gap, 0, out, p, gap.size)
            p += gap.size
        }
        return Pcm(sampleRate, 1, out)
    }

    fun chunkText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val out = ArrayList<String>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxChars) {
                out.add(remaining)
                break
            }
            var cut = remaining.lastIndexOfAny(charArrayOf('.', '!', '?', '\n', ';'), maxChars)
            if (cut < maxChars / 3) cut = remaining.lastIndexOf(' ', maxChars)
            if (cut <= 0) cut = maxChars
            out.add(remaining.substring(0, cut + 1).trim())
            remaining = remaining.substring(cut + 1).trim()
        }
        return out.filter { it.isNotBlank() }
    }

    fun hasSystemEngine(): Boolean {
        return try {
            val intent = android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            context.packageManager.queryIntentServices(intent, 0).isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }
}
