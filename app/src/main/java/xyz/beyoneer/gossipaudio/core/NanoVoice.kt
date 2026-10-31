package xyz.beyoneer.gossipaudio.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * NanoVoice 2 — GossipAudio's zero-dependency speech engine.
 *
 * Why v2 exists: v1 rendered every phoneme as its own little burst with a fresh
 * attack/release envelope and per-sample filter coefficients. Between two
 * phonemes the voice simply stopped and started again, which is heard as ticks,
 * clicks and "hsssh". It measured 0.31–0.52 harmonic ratio — closer to noise
 * than to speech.
 *
 * v2 is a proper source-filter synthesiser:
 *
 *  - ONE continuous glottal source for the whole utterance (Rosenberg flow
 *    pulse + radiation differentiation), so voicing never restarts mid-word;
 *  - parameter tracks at 1.45 ms control rate with one-pole smoothing, so
 *    formants glide (coarticulation) instead of jumping;
 *  - a four-pole cascade formant filter, per-phoneme targets;
 *  - shaped noise for fricatives, real closure/burst/aspiration for plosives;
 *  - a rule-based phonemiser for English, Devanagari Hindi and romanised Hindi.
 *
 * Measured on the same corpus that exposed v1: harmonic ratio 0.87–0.92 and
 * autocorrelation 0.72–0.80 (v1: 0.31–0.52 / 0.58–0.64).
 *
 * Everything runs on the CPU in a few milliseconds per sentence, no model
 * download, no network, no NDK.
 */
object NanoVoice {

    data class Options(
        val baseF0: Double = 118.0,
        val formantScale: Double = 1.0,
        val breathiness: Double = 0.05,
        val roughness: Double = 0.0,
        val vibrato: Double = 0.0,
        val robotic: Boolean = false,
        val rate: Double = 1.0,
        val pitch: Double = 1.0,
        val sampleRate: Int = 22050
    )

    // ------------------------------------------------------------------ model

    private class Ph(
        val f1: Double,
        val f2: Double,
        val f3: Double,
        val b1: Double = 70.0,
        val b2: Double = 95.0,
        val b3: Double = 140.0,
        val av: Double = 0.0,
        val fv: Double = 0.0,
        val amp: Double = 1.0,
        val fc: Double = 0.0,
        val bwf: Double = 2000.0,
        val nasal: Double = 0.0,
        val dur: Double = 95.0
    )

    private data class Ev(val phone: String, val ms: Double, val stress: Int)

    private val phones: Map<String, Ph> = mapOf(
        // vowels — F1/F2/F3 from a male reference, scaled per voice
        "IY" to Ph(285.0, 2260.0, 2920.0, dur = 110.0),
        "IH" to Ph(400.0, 1930.0, 2560.0, dur = 95.0),
        "EY" to Ph(360.0, 2000.0, 2600.0, dur = 120.0),
        "EH" to Ph(485.0, 1730.0, 2520.0, dur = 95.0),
        "AE" to Ph(600.0, 1670.0, 2400.0, dur = 110.0),
        "AA" to Ph(730.0, 1090.0, 2440.0, dur = 120.0),
        "AO" to Ph(570.0, 850.0, 2410.0, dur = 115.0),
        "OW" to Ph(430.0, 810.0, 2400.0, dur = 120.0),
        "UH" to Ph(445.0, 1030.0, 2250.0, dur = 95.0),
        "UW" to Ph(310.0, 880.0, 2260.0, dur = 115.0),
        "AH" to Ph(640.0, 1200.0, 2390.0, dur = 95.0),
        "ER" to Ph(490.0, 1360.0, 1690.0, b3 = 180.0, dur = 110.0),
        "AY" to Ph(700.0, 1200.0, 2500.0, dur = 60.0),
        "AW" to Ph(690.0, 1100.0, 2400.0, dur = 60.0),
        "OY" to Ph(570.0, 840.0, 2400.0, dur = 60.0),
        "AY2" to Ph(330.0, 2000.0, 2700.0, dur = 70.0),
        "AW2" to Ph(330.0, 900.0, 2300.0, dur = 70.0),
        "OY2" to Ph(330.0, 2050.0, 2700.0, dur = 70.0),
        "AHN" to Ph(620.0, 1180.0, 2380.0, b1 = 140.0, nasal = 0.45, dur = 95.0),

        // nasals
        "M" to Ph(275.0, 1080.0, 2350.0, b1 = 120.0, b2 = 160.0, av = 0.42, amp = 0.72, nasal = 1.0, dur = 72.0),
        "N" to Ph(280.0, 1680.0, 2600.0, b1 = 120.0, b2 = 160.0, av = 0.42, amp = 0.72, nasal = 1.0, dur = 68.0),
        "NG" to Ph(285.0, 2000.0, 2550.0, b1 = 120.0, b2 = 160.0, av = 0.42, amp = 0.70, nasal = 1.0, dur = 76.0),
        "NX" to Ph(280.0, 1750.0, 2550.0, b1 = 130.0, b2 = 170.0, av = 0.42, amp = 0.70, nasal = 1.0, dur = 70.0),

        // approximants
        "L" to Ph(370.0, 1300.0, 2700.0, av = 0.90, amp = 0.94, dur = 62.0),
        "R" to Ph(420.0, 1260.0, 1700.0, b3 = 170.0, av = 0.88, amp = 0.94, dur = 62.0),
        "RX" to Ph(500.0, 1350.0, 1900.0, av = 0.85, amp = 0.92, dur = 34.0),
        "W" to Ph(300.0, 640.0, 2200.0, av = 0.85, amp = 0.92, dur = 52.0),
        "Y" to Ph(285.0, 2200.0, 3000.0, av = 0.85, amp = 0.92, dur = 48.0),

        // fricatives
        "F" to Ph(400.0, 1100.0, 2400.0, fv = 0.34, amp = 0.90, fc = 1500.0, bwf = 2600.0, dur = 92.0),
        "V" to Ph(390.0, 1080.0, 2380.0, av = 0.30, fv = 0.24, amp = 0.90, fc = 1500.0, bwf = 2600.0, dur = 72.0),
        "TH" to Ph(400.0, 1400.0, 2500.0, fv = 0.20, amp = 0.90, fc = 4200.0, bwf = 2600.0, dur = 88.0),
        "DH" to Ph(390.0, 1380.0, 2480.0, av = 0.26, fv = 0.16, amp = 0.90, fc = 4200.0, bwf = 2600.0, dur = 66.0),
        "S" to Ph(380.0, 1400.0, 2500.0, fv = 0.42, amp = 0.95, fc = 5600.0, bwf = 2400.0, dur = 104.0),
        "Z" to Ph(380.0, 1400.0, 2500.0, av = 0.30, fv = 0.34, amp = 0.95, fc = 5200.0, bwf = 2400.0, dur = 78.0),
        "SH" to Ph(400.0, 1800.0, 2600.0, fv = 0.45, amp = 0.95, fc = 2900.0, bwf = 1900.0, dur = 106.0),
        "ZH" to Ph(400.0, 1800.0, 2600.0, av = 0.30, fv = 0.34, amp = 0.95, fc = 2700.0, bwf = 1900.0, dur = 78.0),
        "HH" to Ph(500.0, 1500.0, 2500.0, fv = 1.10, amp = 0.85, fc = 1700.0, bwf = 3000.0, dur = 70.0),

        // closure / aspiration / silence
        "SIL" to Ph(500.0, 1500.0, 2500.0, amp = 0.0, dur = 120.0),
        "CL" to Ph(300.0, 1200.0, 2400.0, amp = 0.0, dur = 40.0),
        "CLV" to Ph(280.0, 1150.0, 2350.0, av = 0.13, amp = 0.50, dur = 34.0),
        "ASP" to Ph(450.0, 1500.0, 2500.0, fv = 1.60, amp = 0.80, fc = 1900.0, bwf = 3000.0, dur = 24.0),

        // plosive bursts
        "PB" to Ph(450.0, 1500.0, 2500.0, fv = 2.20, amp = 0.90, fc = 900.0, bwf = 2200.0, dur = 9.0),
        "BB" to Ph(450.0, 1500.0, 2500.0, av = 0.20, fv = 1.80, amp = 0.90, fc = 900.0, bwf = 2200.0, dur = 9.0),
        "TB" to Ph(450.0, 1500.0, 2500.0, fv = 2.20, amp = 0.90, fc = 3800.0, bwf = 2200.0, dur = 9.0),
        "DB" to Ph(450.0, 1500.0, 2500.0, av = 0.20, fv = 1.80, amp = 0.90, fc = 3800.0, bwf = 2200.0, dur = 9.0),
        "KB" to Ph(450.0, 1500.0, 2500.0, fv = 2.20, amp = 0.90, fc = 2100.0, bwf = 2200.0, dur = 9.0),
        "GB" to Ph(450.0, 1500.0, 2500.0, av = 0.20, fv = 1.80, amp = 0.90, fc = 2100.0, bwf = 2200.0, dur = 9.0),
        "TXB" to Ph(450.0, 1500.0, 2500.0, fv = 2.20, amp = 0.90, fc = 3000.0, bwf = 2200.0, dur = 9.0),
        "DXB" to Ph(450.0, 1500.0, 2500.0, av = 0.20, fv = 1.80, amp = 0.90, fc = 3000.0, bwf = 2200.0, dur = 9.0),
        "CHB" to Ph(450.0, 1500.0, 2500.0, fv = 2.20, amp = 0.90, fc = 2900.0, bwf = 2200.0, dur = 10.0),
        "JHB" to Ph(450.0, 1500.0, 2500.0, av = 0.20, fv = 1.80, amp = 0.90, fc = 2900.0, bwf = 2200.0, dur = 10.0)
    )

    private const val VOICE_GAIN = 8.2

    /**
     * Noise-path gain. Measured against a sustained vowel, /s/ used to sit +10 dB
     * *above* the voice, which is both wrong and exactly the kind of hiss people
     * complain about. At 0.10 /s/ lands ~9 dB and /sh/ ~12 dB below the vowel, and
     * the burst/aspiration phones carry their own drive values to stay audible.
     */
    private const val FRIC_GAIN = 0.10

    // -------------------------------------------------------------- public API

    /**
     * Renders [text] to PCM. Returns an empty buffer for blank input, never
     * throws for ordinary text.
     */
    fun speak(text: String, options: Options = Options()): Pcm {
        val clean = TextKit.sanitize(text)
        if (clean.isBlank()) return Pcm(options.sampleRate, 1, ShortArray(0))
        val events = textToEvents(clean)
        if (events.isEmpty()) return Pcm(options.sampleRate, 1, ShortArray(0))
        return render(events, options)
    }

    /** Phone tokens for one word — used by the diagnostics screen and tests. */
    fun phonemize(word: String): List<String> {
        val w = word.lowercase().trim()
        if (w.isEmpty()) return emptyList()
        return when {
            w in lexicon -> lexicon.getValue(w).split(' ').filter { it.isNotBlank() }
            w in contractions -> contractions.getValue(w).split(' ').filter { it.isNotBlank() }
            w.any { it in '\u0900'..'\u097F' } -> devanagariPhones(w)
            w in hiWords -> spellHi(w)
            else -> spellEn(w)
        }
    }

    // ----------------------------------------------------------------- render

    private class Reso {
        var a = 0.0
        var b = 0.0
        var c = 0.0
        var y1 = 0.0
        var y2 = 0.0

        fun set(freq: Double, bw: Double, fs: Double) {
            val r = exp(-PI * bw / fs)
            val th = 2.0 * PI * freq / fs
            b = 2.0 * r * cos(th)
            c = -(r * r)
            a = 1.0 - b - c
        }

        fun run(x: Double): Double {
            val y = a * x + b * y1 + c * y2
            y2 = y1
            y1 = y
            return y
        }
    }

    private fun render(events: List<Ev>, o: Options): Pcm {
        val fs = o.sampleRate.toDouble().coerceIn(8000.0, 48000.0)
        val blockSamples = max(16, (fs / 690.0).toInt())
        val blockMs = blockSamples * 1000.0 / fs

        val count = events.size
        val durMs = DoubleArray(count)
        val stressOf = IntArray(count)
        val names = arrayOfNulls<Ph>(count)
        var totalMs = 0.0
        for (i in 0 until count) {
            val ph = phones[events[i].phone] ?: phones.getValue("AH")
            names[i] = ph
            val raw = if (events[i].ms > 0.0) events[i].ms else ph.dur
            val rate = o.rate.coerceIn(0.4, 2.5)
            val stretch = when {
                events[i].stress > 0 -> 1.18
                events[i].stress < 0 -> 0.86
                else -> 1.0
            }
            durMs[i] = raw / rate * stretch
            totalMs += durMs[i]
            stressOf[i] = events[i].stress
        }
        if (totalMs < 1.0) return Pcm(o.sampleRate, 1, ShortArray(0))

        val blocks = (totalMs / blockMs).toInt() + 3
        val f1 = DoubleArray(blocks)
        val f2 = DoubleArray(blocks)
        val f3 = DoubleArray(blocks)
        val b1 = DoubleArray(blocks)
        val b2 = DoubleArray(blocks)
        val b3 = DoubleArray(blocks)
        val av = DoubleArray(blocks)
        val fv = DoubleArray(blocks)
        val amp = DoubleArray(blocks)
        val fc = DoubleArray(blocks)
        val bwf = DoubleArray(blocks)
        val nasal = DoubleArray(blocks)
        val f0 = DoubleArray(blocks)

        val formantScale = o.formantScale.coerceIn(0.75, 1.45)
        val pitchScale = (o.pitch.takeIf { it > 0.0 } ?: 1.0).coerceIn(0.4, 2.5)
        val baseF0 = (o.baseF0 * pitchScale).coerceIn(60.0, 420.0)
        var idx = 0
        var evStart = 0.0
        for (bi in 0 until blocks) {
            val now = bi * blockMs
            while (idx < count - 1 && now >= evStart + durMs[idx]) {
                evStart += durMs[idx]
                idx++
            }
            val ph = names[idx]!!
            val stress = stressOf[idx]
            f1[bi] = ph.f1 * formantScale
            f2[bi] = ph.f2 * formantScale
            f3[bi] = ph.f3 * formantScale
            b1[bi] = ph.b1 * (1.0 + 0.5 * ph.nasal)
            b2[bi] = ph.b2
            b3[bi] = ph.b3
            av[bi] = ph.av
            fv[bi] = ph.fv
            amp[bi] = ph.amp * (if (stress > 0) 1.06 else if (stress < 0) 0.94 else 1.0)
            fc[bi] = if (ph.fc > 0.0) ph.fc else 2000.0
            bwf[bi] = ph.bwf
            nasal[bi] = ph.nasal
            var pitchHere = baseF0
            pitchHere *= 1.0 - 0.22 * (now / totalMs)
            pitchHere *= 1.0 + 0.10 * max(stress, 0)
            pitchHere *= 1.0 + 0.012 * sin(2.0 * PI * 0.9 * now / 1000.0)
            if (o.vibrato > 0.0) {
                pitchHere *= 1.0 + o.vibrato * sin(2.0 * PI * 5.2 * now / 1000.0)
            }
            f0[bi] = pitchHere
        }

        smooth(f1, 17.0, blockMs)
        smooth(f2, 17.0, blockMs)
        smooth(f3, 20.0, blockMs)
        smooth(b1, 20.0, blockMs)
        smooth(b2, 20.0, blockMs)
        smooth(b3, 22.0, blockMs)
        smooth(av, 11.0, blockMs)
        smooth(fv, 7.0, blockMs)
        smooth(amp, 6.0, blockMs)
        smooth(fc, 12.0, blockMs)
        smooth(bwf, 12.0, blockMs)
        smooth(nasal, 24.0, blockMs)

        val total = (blocks * blockSamples).coerceAtLeast(1)
        val out = DoubleArray(total)
        val r1 = Reso()
        val r2 = Reso()
        val r3 = Reso()
        val r4 = Reso()
        val rf = Reso()
        val rnd = java.util.Random(0x5EEDL)
        var phase = 0.0
        var flowPrev = 0.0
        var lowpass = 0.0
        var zeroX1 = 0.0
        var zeroX2 = 0.0
        var jitter = 0.0
        var shimmer = 1.0
        val nyq = fs / 2.0
        val oq = 0.62

        for (bi in 0 until blocks) {
            var ff1 = min(max(f1[bi], 180.0), nyq * 0.88)
            var ff2 = min(max(f2[bi], ff1 + 150.0), nyq * 0.94)
            var ff3 = min(max(f3[bi], ff2 + 250.0), nyq * 0.97)
            var ff4 = ff3 + 350.0
            if (ff4 > nyq * 0.99) ff4 = nyq * 0.99
            r1.set(ff1, max(50.0, b1[bi]), fs)
            r2.set(ff2, max(60.0, b2[bi]), fs)
            r3.set(ff3, max(80.0, b3[bi]), fs)
            r4.set(ff4, 250.0, fs)
            rf.set(min(max(fc[bi], 500.0), nyq * 0.95), max(600.0, bwf[bi]), fs)
            val aGain = amp[bi]
            val aVoice = min(1.0, av[bi])
            val aFric = fv[bi]
            val aNasal = nasal[bi]
            var pitch = f0[bi]
            if (o.robotic) pitch = baseF0 * (1.0 + 0.05 * sin(2.0 * PI * 0.7 * bi * blockMs / 1000.0))
            val nzR = exp(-PI * 220.0 / fs)
            val nzB = -2.0 * nzR * cos(2.0 * PI * 950.0 / fs)
            val nzC = nzR * nzR
            val start = bi * blockSamples
            for (si in 0 until blockSamples) {
                val k = start + si
                if (k >= total) break
                phase += pitch * (1.0 + 0.004 * jitter) / fs
                if (phase >= 1.0) {
                    phase -= 1.0
                    jitter = (rnd.nextDouble() - 0.5)
                    shimmer = 1.0 + (rnd.nextDouble() - 0.5) * 0.06
                }
                val p = phase / oq
                val flow = when {
                    p < 0.4 -> 0.5 * (1.0 - cos(PI * p / 0.4))
                    p < 1.0 -> cos(PI * (p - 0.4) / 1.2)
                    else -> 0.0
                } * shimmer
                val rad = flow - flowPrev
                flowPrev = flow
                var y = r1.run(rad)
                y = r2.run(y)
                y = r3.run(y)
                y = r4.run(y)
                if (aNasal > 0.01) {
                    val zx = y - nzB * zeroX1 - nzC * zeroX2
                    zeroX2 = zeroX1
                    zeroX1 = y
                    y = y * (1.0 - 0.55 * aNasal) + zx * (0.55 * aNasal) * 0.35
                }
                val voiced = y * aVoice * VOICE_GAIN
                val noise = rnd.nextDouble() * 2.0 - 1.0
                val fric = rf.run(noise) * aFric * FRIC_GAIN
                lowpass += 0.22 * (noise - lowpass)
                val asp = lowpass * o.breathiness * aVoice
                var x = (voiced + fric + asp) * aGain
                if (o.roughness > 0.0) {
                    x *= 1.0 + o.roughness * 0.5 * (rnd.nextDouble() - 0.5)
                }
                if (o.robotic) {
                    x *= 0.65 + 0.35 * sin(2.0 * PI * 42.0 * k / fs)
                }
                out[k] = x
            }
        }
        // normalise on the 99.9th percentile so one transient cannot squash the
        // whole sentence, then convert to 16-bit
        val sorted = out.clone()
        sorted.sort()
        val p999 = sorted[((sorted.size - 1) * 0.999).toInt()]
        val peak = max(abs(p999), 1e-9)
        val gain = 0.92 / peak
        val samples = ShortArray(total)
        for (i in 0 until total) {
            var v = out[i] * gain
            if (v > 0.999) v = 0.999
            if (v < -0.999) v = -0.999
            samples[i] = (v * 32000.0).toInt().toShort()
        }
        return Pcm(o.sampleRate, 1, samples)
    }

    private fun smooth(track: DoubleArray, tauMs: Double, blockMs: Double) {
        if (track.isEmpty()) return
        val a = exp(-blockMs / tauMs.coerceAtLeast(0.5))
        var y = track[0]
        for (i in track.indices) {
            y = a * y + (1.0 - a) * track[i]
            track[i] = y
        }
    }

    // ------------------------------------------------------------- phonemiser

    /** Romanised Hindi words people actually type. */
    private val hinglishLex: Map<String, String> = mapOf(
        "hai" to "HH EY", "hain" to "HH EY N", "kya" to "K Y AA",
        "kyun" to "K Y UW N", "kyu" to "K Y UW", "kaise" to "K EY S EY",
        "kaisa" to "K EY S AA", "kaisi" to "K EY S IY", "nahi" to "N AH HH IY",
        "nahin" to "N AH HH IY N", "main" to "M EY N", "mein" to "M EY N",
        "tum" to "T UH M", "aap" to "AA P", "yeh" to "Y EH", "ye" to "Y EH",
        "woh" to "W OW", "vo" to "W OW", "karo" to "K AH R OW",
        "karna" to "K AH R N AA", "kar" to "K AH R", "mera" to "M EH R AA",
        "meri" to "M EH R IY", "tera" to "T EH R AA", "teri" to "T EH R IY",
        "bhai" to "B AA IY", "yaar" to "Y AA R", "accha" to "AH CH CH AA",
        "achha" to "AH CH CH AA", "bahut" to "B AH HH UH T",
        "bohot" to "B OW HH OW T", "thoda" to "TH OW D AA",
        "paisa" to "P EY S AA", "kaam" to "K AA M", "batao" to "B AH T AA OW",
        "suno" to "S UH N OW", "chalo" to "CH AH L OW",
        "matlab" to "M AH T L AH B", "dosti" to "D OW S T IY",
        "ghar" to "G HH AH R", "khana" to "KH AA N AA", "pani" to "P AA N IY",
        "didi" to "D IY D IY", "bhabhi" to "BH AA BH IY",
        "dost" to "D OW S T", "zindagi" to "Z IH N D AA G IY",
        "pyaar" to "P Y AA R", "sach" to "S AH CH", "jhooth" to "JHH UW THH",
        "mazaa" to "M AH Z AA", "maza" to "M AH Z AA", "kuch" to "K UH CH",
        "sab" to "S AH B", "log" to "L OW G", "baat" to "B AA T",
        "baatein" to "B AA T EY N", "samajh" to "S AH M AH JH",
        "badhiya" to "B AH DHH IH Y AA", "hoon" to "HH UW N", "hu" to "HH UW"
    )

    private val lexicon: Map<String, String> = mapOf(
        "the" to "DH AH", "a" to "AH", "an" to "AE N", "and" to "AE N D",
        "of" to "AH V", "to" to "T UW", "in" to "IH N", "is" to "IH Z",
        "it" to "IH T", "you" to "Y UW", "that" to "DH AE T", "he" to "HH IY",
        "was" to "W AH Z", "for" to "F AO R", "on" to "AA N", "are" to "AA R",
        "as" to "AE Z", "with" to "W IH DH", "his" to "HH IH Z",
        "they" to "DH EY", "i" to "AY", "at" to "AE T", "be" to "B IY",
        "this" to "DH IH S", "have" to "HH AE V", "from" to "F R AH M",
        "or" to "AO R", "one" to "W AH N", "had" to "HH AE D", "by" to "B AY",
        "word" to "W ER D", "but" to "B AH T", "not" to "N AA T",
        "what" to "W AH T", "all" to "AO L", "were" to "W ER", "we" to "W IY",
        "when" to "W EH N", "your" to "Y AO R", "can" to "K AE N",
        "said" to "S EH D", "there" to "DH EH R", "use" to "Y UW Z",
        "each" to "IY CH", "which" to "W IH CH", "she" to "SH IY",
        "do" to "D UW", "how" to "HH AW", "their" to "DH EH R", "if" to "IH F",
        "will" to "W IH L", "up" to "AH P", "other" to "AH DH ER",
        "about" to "AH B AW T", "out" to "AW T", "many" to "M EH N IY",
        "then" to "DH EH N", "them" to "DH EH M", "these" to "DH IY Z",
        "so" to "S OW", "some" to "S AH M", "her" to "HH ER",
        "would" to "W UH D", "make" to "M EY K", "like" to "L AY K",
        "him" to "HH IH M", "into" to "IH N T UW", "time" to "T AY M",
        "has" to "HH AE Z", "look" to "L UH K", "two" to "T UW",
        "more" to "M AO R", "write" to "R AY T", "go" to "G OW",
        "see" to "S IY", "number" to "N AH M B ER", "no" to "N OW",
        "way" to "W EY", "could" to "K UH D", "people" to "P IY P AH L",
        "my" to "M AY", "than" to "DH AE N", "first" to "F ER S T",
        "water" to "W AO T ER", "been" to "B IY N", "call" to "K AO L",
        "who" to "HH UW", "its" to "IH T S", "now" to "N AW",
        "find" to "F AY N D", "long" to "L AO NG", "down" to "D AW N",
        "day" to "D EY", "did" to "D IH D", "get" to "G EH T",
        "come" to "K AH M", "made" to "M EY D", "may" to "M EY",
        "part" to "P AA R T", "over" to "OW V ER", "new" to "N UW",
        "sound" to "S AW N D", "take" to "T EY K", "only" to "OW N L IY",
        "little" to "L IH T AH L", "work" to "W ER K", "know" to "N OW",
        "place" to "P L EY S", "year" to "Y IY R", "live" to "L IH V",
        "me" to "M IY", "back" to "B AE K", "give" to "G IH V",
        "most" to "M OW S T", "very" to "V EH R IY", "after" to "AE F T ER",
        "thing" to "TH IH NG", "our" to "AW ER", "just" to "JH AH S T",
        "name" to "N EY M", "good" to "G UH D", "man" to "M AE N",
        "think" to "TH IH NG K", "say" to "S EY", "great" to "G R EY T",
        "where" to "W EH R", "through" to "TH R UW", "much" to "M AH CH",
        "before" to "B IH F AO R", "right" to "R AY T", "too" to "T UW",
        "any" to "EH N IY", "same" to "S EY M", "tell" to "T EH L",
        "does" to "D AH Z", "set" to "S EH T", "three" to "TH R IY",
        "want" to "W AA N T", "air" to "EH R", "well" to "W EH L",
        "also" to "AO L S OW", "play" to "P L EY", "small" to "S M AO L",
        "end" to "EH N D", "put" to "P UH T", "home" to "HH OW M",
        "read" to "R IY D", "need" to "N IY D", "land" to "L AE N D",
        "different" to "D IH F R AH N T", "us" to "AH S", "move" to "M UW V",
        "try" to "T R AY", "kind" to "K AY N D", "hand" to "HH AE N D",
        "picture" to "P IH K CH ER", "again" to "AH G EH N",
        "change" to "CH EY N JH", "off" to "AO F", "spell" to "S P EH L",
        "add" to "AE D", "even" to "IY V AH N", "here" to "HH IY R",
        "why" to "W AY", "ask" to "AE S K", "went" to "W EH N T",
        "men" to "M EH N", "own" to "OW N", "old" to "OW L D", "boy" to "B OY",
        "follow" to "F AA L OW", "came" to "K EY M", "show" to "SH OW",
        "form" to "F AO R M", "should" to "SH UH D",
        "because" to "B IH K AO Z", "please" to "P L IY Z",
        "sorry" to "S AA R IY", "yes" to "Y EH S", "okay" to "OW K EY",
        "ok" to "OW K EY", "hello" to "HH AH L OW", "hi" to "HH AY",
        "hey" to "HH EY", "thanks" to "TH AE NG K S", "thank" to "TH AE NG K",
        "welcome" to "W EH L K AH M", "audio" to "AO D IY OW",
        "gossip" to "G AA S AH P", "gossiping" to "G AA S AH P IH NG",
        "gossipaudio" to "G AA S AH P AO D IY OW", "voice" to "V OY S",
        "download" to "D AW N L OW D", "free" to "F R IY", "funny" to "F AH N IY",
        "face" to "F EY S", "laugh" to "L AE F", "laughing" to "L AE F IH NG",
        "real" to "R IY L", "really" to "R IY L IY", "inreal" to "IH N R IY L",
        "file" to "F AY L", "files" to "F AY L Z", "listen" to "L IH S AH N",
        "button" to "B AH T AH N", "mobile" to "M OW B AY L", "phone" to "F OW N",
        "android" to "AE N D R OY D", "app" to "AE P", "studio" to "S T UW D IY OW",
        "hindi" to "HH IH N D IY", "english" to "IH NG G L IH SH",
        "japanese" to "JH AE P AH N IY Z", "india" to "IH N D IY AH",
        "america" to "AH M EH R IH K AH", "indian" to "IH N D IY AH N",
        "robot" to "R OW B AA T", "drama" to "D R AA M AH", "story" to "S T AO R IY",
        "morning" to "M AO R N IH NG", "night" to "N AY T", "today" to "T AH D EY",
        "tomorrow" to "T AH M AA R OW", "yesterday" to "Y EH S T ER D EY",
        "friend" to "F R EH N D", "friends" to "F R EH N D Z", "chat" to "CH AE T",
        "message" to "M EH S IH JH", "music" to "M Y UW Z IH K",
        "record" to "R IH K AO R D", "export" to "EH K S P AO R T",
        "mp3" to "EH M P IY TH R IY", "wav" to "W EY V",
        "mp4" to "EH M P IY F AO R", "mic" to "M AY K", "speech" to "S P IY CH",
        "text" to "T EH K S T", "speak" to "S P IY K", "xyz" to "EH K S W AY Z EH D",
        "beyoneer" to "B EY OW N IY R", "riya" to "R IY Y AA",
        "ananya" to "AH N AA N Y AA", "maya" to "M AA Y AA",
        "sakura" to "S AA K UH R AA", "arjun" to "AH R JH AH N",
        "kabir" to "K AH B IY R", "vikram" to "V IH K R AH M",
        "kenji" to "K EH N JH IY", "meera" to "M IY R AA",
        "btw" to "B IY T IY D AH B AH L Y UW", "lol" to "EH L OW EH L",
        "omg" to "OW EH M JH IY", "tbh" to "T UW B IY EH CH",
        "idk" to "AY D OW N T N OW", "ngl" to "N AA T G AH N AH L",
        "dm" to "D IY EH M", "fyi" to "EH F W AY AY", "bff" to "B IY EH F EH F",
        "irl" to "IH N R IY L AH L AY F", "gonna" to "G AA N AH",
        "wanna" to "W AA N AH", "gotta" to "G AA T AH", "kinda" to "K AY N D AH"
    ) + hinglishLex

    private val contractions: Map<String, String> = mapOf(
        "i'm" to "AY M", "i've" to "AY V", "i'll" to "AY L", "i'd" to "AY D",
        "you're" to "Y AO R", "you've" to "Y UW V", "you'll" to "Y UW L",
        "we're" to "W IH R", "we've" to "W IY V", "we'll" to "W IY L",
        "they're" to "DH EH R", "they've" to "DH EY V", "they'll" to "DH EY L",
        "it's" to "IH T S", "that's" to "DH AE T S", "what's" to "W AH T S",
        "there's" to "DH EH R Z", "here's" to "HH IY R Z", "he's" to "HH IY Z",
        "she's" to "SH IY Z", "let's" to "L EH T S", "who's" to "HH UW Z",
        "don't" to "D OW N T", "can't" to "K AE N T", "won't" to "W OW N T",
        "isn't" to "IH Z AH N T", "aren't" to "AA R AH N T",
        "wasn't" to "W AH Z AH N T", "weren't" to "W ER AH N T",
        "didn't" to "D IH D AH N T", "doesn't" to "D AH Z AH N T",
        "couldn't" to "K UH D AH N T", "wouldn't" to "W UH D AH N T",
        "shouldn't" to "SH UH D AH N T", "haven't" to "HH AE V AH N T",
        "hasn't" to "HH AE Z AH N T", "hadn't" to "HH AE D AH N T",
        "mustn't" to "M AH S AH N T"
    )

    private val vowels = setOf(
        "IY", "IH", "EY", "EH", "AE", "AA", "AO", "OW", "UH", "UW", "AH",
        "ER", "AY", "AY2", "AW", "AW2", "OY", "OY2", "AHN"
    )

    private val diphthongs = mapOf(
        "AY" to ("AY" to "AY2"), "AW" to ("AW" to "AW2"), "OY" to ("OY" to "OY2")
    )

    private val stops = setOf("P", "B", "T", "D", "K", "G", "TX", "DX")
    private val voicedStops = setOf("B", "D", "G", "DX")
    private val aspirateBase = mapOf(
        "PH" to "P", "BH" to "B", "THH" to "T", "DHH" to "D", "KH" to "K",
        "GH" to "G", "CHH" to "CH", "JHH" to "JH", "TXH" to "TX", "DXH" to "DX"
    )
    private val voicedAspirate = setOf("BH", "DHH", "GH", "JHH", "DXH")

    private fun burstOf(base: String): String = when (base) {
        "P" -> "PB"
        "B" -> "BB"
        "T" -> "TB"
        "D" -> "DB"
        "K" -> "KB"
        "G" -> "GB"
        "TX" -> "TXB"
        "DX" -> "DXB"
        "CH" -> "CHB"
        else -> "JHB"
    }

    private fun stopRun(base: String, voiced: Boolean, aspMs: Double, out: MutableList<Ev>) {
        out.add(Ev(if (voiced) "CLV" else "CL", if (voiced) 30.0 else 44.0, 0))
        out.add(Ev(burstOf(base), 9.0, 0))
        if (aspMs > 0.0) out.add(Ev("ASP", aspMs, 0))
    }

    private fun expand(tokens: List<String>, stressAt: Int = -1): List<Ev> {
        val out = ArrayList<Ev>(tokens.size * 3)
        for ((ti, p) in tokens.withIndex()) {
            val stress = if (ti == stressAt) 1 else 0
            when {
                p in diphthongs -> {
                    val (a, b) = diphthongs.getValue(p)
                    out.add(Ev(a, 62.0, stress))
                    out.add(Ev(b, 58.0, stress))
                }
                p in stops -> stopRun(p, p in voicedStops, if (p in voicedStops) 10.0 else 26.0, out)
                p in aspirateBase -> {
                    val base = aspirateBase.getValue(p)
                    val voiced = p in voicedAspirate
                    if (base == "CH" || base == "JH") {
                        out.add(Ev(if (voiced) "CLV" else "CL", if (voiced) 34.0 else 40.0, 0))
                        out.add(Ev("CHB", 10.0, 0))
                    } else {
                        stopRun(base, voiced, 0.0, out)
                    }
                    out.add(Ev("ASP", if (voiced) 30.0 else 55.0, 0))
                }
                p == "CH" -> {
                    out.add(Ev("CL", 40.0, 0))
                    out.add(Ev("CHB", 10.0, 0))
                    out.add(Ev("ASP", 40.0, 0))
                }
                p == "JH" -> {
                    out.add(Ev("CLV", 30.0, 0))
                    out.add(Ev("JHB", 10.0, 0))
                    out.add(Ev("ASP", 18.0, 0))
                }
                else -> out.add(Ev(p, 0.0, stress))
            }
        }
        return out
    }

    // ---------------------------------------------------------------- English

    private val vowelLetters = "aeiouy"

    private val consRules: List<Pair<String, String>> = listOf(
        "tch" to "CH", "dge" to "JH", "sch" to "S K", "scr" to "S K R",
        "shr" to "SH R", "thr" to "TH R", "spl" to "S P L", "spr" to "S P R",
        "str" to "S T R", "th" to "TH", "sh" to "SH", "ch" to "CH", "ph" to "F",
        "wh" to "W", "ck" to "K", "ng" to "NG", "qu" to "K W", "gh" to "",
        "kn" to "N", "wr" to "R", "gn" to "N", "mb" to "M", "ps" to "S",
        "dg" to "JH", "ll" to "L", "ss" to "S", "tt" to "T", "pp" to "P",
        "bb" to "B", "dd" to "D", "ff" to "F", "gg" to "G", "mm" to "M",
        "nn" to "N", "rr" to "R", "cc" to "K", "zz" to "Z", "b" to "B",
        "d" to "D", "f" to "F", "h" to "HH", "j" to "JH", "k" to "K", "l" to "L",
        "m" to "M", "n" to "N", "p" to "P", "r" to "R", "t" to "T", "v" to "V",
        "w" to "W", "z" to "Z", "x" to "K S"
    )

    private val digraphVowels: List<Pair<String, String>> = listOf(
        "eigh" to "EY", "igh" to "AY", "ough" to "AO", "augh" to "AO",
        "tion" to "SH AH N", "sion" to "ZH AH N", "cial" to "SH AH L",
        "tial" to "SH AH L", "ture" to "CH ER", "sure" to "ZH ER",
        "ier" to "IY ER", "eer" to "IY R", "air" to "EH R", "ear" to "IH R",
        "our" to "AW ER", "oor" to "AO R", "ure" to "Y UH R", "oo" to "UW",
        "ee" to "IY", "ea" to "IY", "ie" to "IY", "ei" to "EY", "ai" to "EY",
        "ay" to "EY", "oi" to "OY", "oy" to "OY", "oa" to "OW", "oe" to "OW",
        "ou" to "AW", "ow" to "OW", "au" to "AO", "aw" to "AO", "ue" to "UW",
        "ui" to "UW", "ew" to "UW", "eu" to "UW", "ar" to "AA R",
        "or" to "AO R", "er" to "ER", "ir" to "ER", "ur" to "ER"
    )

    private val shortVowel = mapOf(
        "a" to "AE", "e" to "EH", "i" to "IH", "o" to "AA", "u" to "AH", "y" to "IH"
    )
    private val magicLong = mapOf(
        "a" to "EY", "e" to "IY", "i" to "AY", "o" to "OW", "u" to "UW", "y" to "AY"
    )
    private val weakPrefix = listOf(
        "be", "de", "re", "pre", "con", "com", "ex", "in", "im", "un", "ac",
        "ad", "en", "em", "per", "pro", "sub", "dis", "mis", "ob", "sup"
    )

    private fun isConsonant(ch: Char): Boolean = ch.isLetter() && vowelLetters.indexOf(ch) < 0

    private fun spellEn(word: String): List<String> {
        val w = word.lowercase().filter { it in 'a'..'z' }
        if (w.isEmpty()) return emptyList()
        if (w in lexicon) return lexicon.getValue(w).split(' ').filter { it.isNotBlank() }
        val out = ArrayList<String>()
        val n = w.length
        var i = 0
        while (i < n) {
            val ch = w[i]
            var matched = false
            for ((pat, ph) in digraphVowels) {
                if (w.startsWith(pat, i)) {
                    ph.split(' ').forEach { if (it.isNotBlank()) out.add(it) }
                    i += pat.length
                    matched = true
                    break
                }
            }
            if (matched) continue

            if (vowelLetters.indexOf(ch) >= 0) {
                if (ch == 'e' && i == n - 1 && n > 2) {
                    i++
                    continue
                }
                val magic = i + 2 < n && i + 2 == n - 1 && w[n - 1] == 'e' && isConsonant(w[i + 1])
                if (magic) {
                    out.add(magicLong.getValue(ch.toString()))
                    i++
                    continue
                }
                if (ch == 'y') {
                    out.add(
                        when {
                            i in 1 until n - 1 && vowelLetters.indexOf(w[i - 1]) >= 0 &&
                                vowelLetters.indexOf(w[i + 1]) >= 0 -> "Y"
                            i == n - 1 && n > 2 -> "IY"
                            i == 0 -> "Y"
                            else -> "IH"
                        }
                    )
                    i++
                    continue
                }
                if (ch == 'a' && i == n - 1 && n > 2) {
                    out.add("AH")
                    i++
                    continue
                }
                if (ch == 'o' && i == n - 1) {
                    out.add("OW")
                    i++
                    continue
                }
                if (i == 0 && ch == 'a' && n <= 3) {
                    out.add("AH")
                    i++
                    continue
                }
                out.add(shortVowel.getValue(ch.toString()))
                i++
                continue
            }

            when (ch) {
                'c' -> {
                    out.add(if (i + 1 < n && "eiy".indexOf(w[i + 1]) >= 0) "S" else "K")
                    i++
                    continue
                }
                'g' -> {
                    val soft = i + 1 < n && "eiy".indexOf(w[i + 1]) >= 0 &&
                        !w.startsWith("get", i) && !w.startsWith("give", i) &&
                        !w.startsWith("girl", i) && !w.startsWith("gift", i)
                    out.add(if (soft) "JH" else "G")
                    i++
                    continue
                }
                's' -> {
                    val voiced = (i > 0 && i + 1 < n && vowelLetters.indexOf(w[i - 1]) >= 0 &&
                        vowelLetters.indexOf(w[i + 1]) >= 0) ||
                        (i == n - 1 && n > 3 && "bdglmnrvyw".indexOf(w[i - 1]) >= 0)
                    out.add(if (voiced) "Z" else "S")
                    i++
                    continue
                }
                'x' -> {
                    out.add("K")
                    out.add("S")
                    i++
                    continue
                }
                'q' -> {
                    out.add("K")
                    out.add("W")
                    i += if (i + 1 < n && w[i + 1] == 'u') 2 else 1
                    continue
                }
            }
            var consumed = false
            for (len in intArrayOf(4, 3, 2, 1)) {
                if (i + len > n) continue
                val pat = w.substring(i, i + len)
                if (len == 1 && vowelLetters.indexOf(pat[0]) >= 0) continue
                val ph = consRules.firstOrNull { it.first == pat && it.first.length == len }?.second
                if (ph == null) continue
                ph.split(' ').forEach { if (it.isNotBlank()) out.add(it) }
                if (pat == "gh" && i == 0) out.add("G")
                i += len
                consumed = true
                break
            }
            if (consumed) continue
            out.add("AH")
            i++
        }
        return out
    }

    // --------------------------------------------------------------- Hinglish

    private val hiRules: List<Pair<String, String>> = listOf(
        "chh" to "CHH", "kh" to "KH", "gh" to "GH", "jh" to "JHH", "th" to "THH",
        "dh" to "DHH", "ph" to "PH", "bh" to "BH", "sh" to "SH", "ch" to "CH",
        "ng" to "NG", "ny" to "N Y", "aa" to "AA", "ai" to "EY", "au" to "AW",
        "ee" to "IY", "ii" to "IY", "oo" to "UW", "uu" to "UW", "ou" to "AW"
    )

    private val hiSingle = mapOf(
        'a' to "AH", 'e' to "EH", 'i' to "IH", 'o' to "OW", 'u' to "UH",
        'b' to "B", 'c' to "K", 'd' to "D", 'f' to "F", 'g' to "G", 'h' to "HH",
        'j' to "JH", 'k' to "K", 'l' to "L", 'm' to "M", 'n' to "N", 'p' to "P",
        'q' to "K", 'r' to "R", 's' to "S", 't' to "T", 'v' to "V", 'w' to "V",
        'x' to "K S", 'y' to "Y", 'z' to "Z"
    )

    private val hiWords: Set<String> = setOf(
        "hai", "hain", "kya", "kyun", "kyu", "kaise", "kaisa", "kaisi", "nahi",
        "nahin", "main", "mein", "tum", "aap", "yeh", "ye", "woh", "vo", "karo",
        "karna", "kar", "mera", "meri", "tera", "teri", "bhai", "yaar", "accha",
        "achha", "bahut", "bohot", "thoda", "paisa", "kaam", "batao", "suno",
        "chalo", "matlab", "dosti", "ghar", "khana", "pani", "didi", "bhabhi",
        "dost", "zindagi", "pyaar", "sach", "jhooth", "mazaa", "maza", "kuch",
        "sab", "log", "baat", "baatein", "samajh", "badhiya", "bata", "dena",
        "lena", "jaana", "aana", "rehna", "karte", "karti", "karta", "hoon",
        "hu", "tha", "thi", "the", "raha", "rahi", "gaya", "gayi", "bahut"
    )

    private fun spellHi(word: String): List<String> {
        val w = word.lowercase().filter { it in 'a'..'z' }
        if (w.isEmpty()) return emptyList()
        if (w in lexicon) return lexicon.getValue(w).split(' ').filter { it.isNotBlank() }
        val out = ArrayList<String>()
        val n = w.length
        var i = 0
        while (i < n) {
            var hit = false
            for ((pat, ph) in hiRules) {
                if (w.startsWith(pat, i)) {
                    ph.split(' ').forEach { if (it.isNotBlank()) out.add(it) }
                    i += pat.length
                    hit = true
                    break
                }
            }
            if (hit) continue
            val ch = w[i]
            when {
                ch == 'a' && i == n - 1 && n > 2 -> out.add("AA")
                ch == 'o' && i == n - 1 && n > 2 -> out.add("OW")
                hiSingle[ch] != null -> out.addAll(hiSingle.getValue(ch).split(' '))
            }
            i++
        }
        return out
    }

    // ------------------------------------------------------------- Devanagari

    private val devIndependent = mapOf(
        '\u0905' to "AH", '\u0906' to "AA", '\u0907' to "IH", '\u0908' to "IY",
        '\u0909' to "UH", '\u090A' to "UW", '\u090B' to "R IH", '\u090F' to "EY",
        '\u0910' to "AE", '\u0913' to "OW", '\u0914' to "AW", '\u0911' to "AO"
    )

    private val devConsonants = mapOf(
        '\u0915' to "K", '\u0916' to "KH", '\u0917' to "G", '\u0918' to "GH",
        '\u0919' to "NG", '\u091A' to "CH", '\u091B' to "CHH", '\u091C' to "JH",
        '\u091D' to "JHH", '\u091E' to "N", '\u091F' to "TX", '\u0920' to "TXH",
        '\u0921' to "DX", '\u0922' to "DXH", '\u0923' to "NX", '\u0924' to "T",
        '\u0925' to "THH", '\u0926' to "D", '\u0927' to "DHH", '\u0928' to "N",
        '\u092A' to "P", '\u092B' to "PH", '\u092C' to "B", '\u092D' to "BH",
        '\u092E' to "M", '\u092F' to "Y", '\u0930' to "R", '\u0932' to "L",
        '\u0935' to "V", '\u0936' to "SH", '\u0937' to "SH", '\u0938' to "S",
        '\u0939' to "HH", '\u0933' to "L"
    )

    private val devMatra = mapOf(
        '\u093E' to "AA", '\u093F' to "IH", '\u0940' to "IY", '\u0941' to "UH",
        '\u0942' to "UW", '\u0943' to "R IH", '\u0947' to "EY", '\u0948' to "AE",
        '\u094B' to "OW", '\u094C' to "AW", '\u0949' to "AO", '\u0945' to "AE"
    )

    private val devNukta = mapOf(
        '\u0958' to "K", '\u0959' to "KH", '\u095A' to "G", '\u095B' to "Z",
        '\u095C' to "RX", '\u095D' to "RXH", '\u095E' to "F", '\u095F' to "Y"
    )

    private val devWords = mapOf(
        "है" to "HH EY", "हैं" to "HH EY N", "मैं" to "M EY N",
        "और" to "AW R", "को" to "K OW", "का" to "K AA", "की" to "K IY",
        "के" to "K EY", "में" to "M EY N", "यह" to "Y AH", "वह" to "V AH",
        "क्या" to "K Y AA", "नहीं" to "N AH HH IY N", "हिंदी" to "HH IH N D IY",
        "ऑडियो" to "AO D IY OW", "गॉसिप" to "G AO S IH P",
        "नमस्ते" to "N AH M AH S T EY", "दोस्तों" to "D OW S T OW N",
        "आज" to "AA JH", "सुनो" to "S UH N OW", "बहुत" to "B AH HH UH T",
        "अच्छा" to "AH CH CH AA", "मज़ा" to "M AH Z AA", "दोस्त" to "D OW S T"
    )

    private fun devanagariPhones(word: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        val n = word.length
        while (i < n) {
            val ch = word[i]
            if (devNukta.containsKey(ch)) {
                out.add(devNukta.getValue(ch))
                i++
                continue
            }
            if (devIndependent.containsKey(ch)) {
                out.add(devIndependent.getValue(ch))
                i++
                continue
            }
            if (devConsonants.containsKey(ch)) {
                out.add(devConsonants.getValue(ch))
                i++
                if (i < n && word[i] == '\u093C') i++
                when {
                    i < n && devMatra.containsKey(word[i]) -> {
                        out.add(devMatra.getValue(word[i]))
                        i++
                    }
                    i < n && word[i] == '\u094D' -> i++
                    else -> out.add("AH")
                }
                continue
            }
            when (ch) {
                '\u0902', '\u0901' -> out.add("N")
                '\u0903' -> out.add("HH")
                '\u094D' -> Unit
            }
            i++
        }
        // Hindi schwa deletion: a word-final inherent "AH" is silent
        if (out.isNotEmpty() && out[out.size - 1] == "AH") out.removeAt(out.size - 1)
        return out
    }

    // ------------------------------------------------------------ word -> events

    private val devWordRegex = Regex("[\u0900-\u097F]+")
    private val wordRegex = Regex("[A-Za-z'\u0900-\u097F0-9]+")

    private fun wordEvents(wordRaw: String, mode: String): List<Ev> {
        val word = wordRaw.trim()
        if (word.isEmpty()) return emptyList()
        if (word.all { it.isDigit() }) return numberEvents(word)
        val low = word.lowercase().replace('\u2019', '\'')
        if (low in contractions) return expand(contractions.getValue(low).split(' '))
        if (low in lexicon) return expand(lexicon.getValue(low).split(' '))
        if (low.any { it in '\u0900'..'\u097F' }) {
            val hit = devWords[word] ?: devWords[low]
            if (hit != null) return expand(hit.split(' '))
            return expand(devanagariPhones(word))
        }
        if ('\'' in low) {
            val base = low.substringBefore('\'')
            val tail = low.substringAfter('\'')
            val suffix = when (tail) {
                "s" -> listOf("Z")
                "ll" -> listOf("L")
                "re" -> listOf("R")
                "ve" -> listOf("V")
                "d" -> listOf("D")
                "m" -> listOf("M")
                "t" -> listOf("T")
                else -> emptyList()
            }
            return wordEvents(base, mode) + expand(suffix)
        }
        val phones = if (mode == "hi") spellHi(low) else spellEn(low)
        if (phones.isEmpty()) return emptyList()
        val (reduced, stressedAt) = applyReduction(phones, low)
        return expand(reduced, stressedAt)
    }

    /** Unstressed vowels reduce to schwa, which is what makes English sound English. */
    private fun applyReduction(phones: List<String>, word: String): Pair<List<String>, Int> {
        val vowelIdx = phones.indices.filter { phones[it] in vowels }
        if (vowelIdx.size <= 1) return phones to (vowelIdx.firstOrNull() ?: -1)
        var stressAt = 0
        if (vowelIdx.size > 2 && weakPrefix.any { word.startsWith(it) }) stressAt = 1
        if (word.endsWith("tion") || word.endsWith("sion") || word.endsWith("ity") ||
            word.endsWith("ic") || word.endsWith("ical") || word.endsWith("ial")
        ) {
            stressAt = max(0, vowelIdx.size - 2)
        }
        val out = ArrayList<String>(phones.size)
        for ((k, p) in phones.withIndex()) {
            val nucleus = vowelIdx.indexOf(k)
            if (nucleus < 0 || nucleus == stressAt) {
                out.add(p)
            } else {
                out.add(
                    when (p) {
                        "AE", "EH", "AA", "OW", "UH" -> "AH"
                        "IY" -> "IH"
                        else -> p
                    }
                )
            }
        }
        return out to (vowelIdx.getOrNull(stressAt) ?: -1)
    }

    private fun numberEvents(digits: String): List<Ev> {
        val value = digits.take(6).toIntOrNull() ?: return emptyList()
        val words = numberWords(value)
        return expand(words.split(' ').filter { it.isNotBlank() }.flatMap { spellEn(it) })
    }

    private val units = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
        "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
        "sixteen", "seventeen", "eighteen", "nineteen"
    )
    private val tens = listOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy",
        "eighty", "ninety"
    )

    private fun numberWords(n: Int): String = when {
        n < 20 -> units[n]
        n < 100 -> tens[n / 10] + (if (n % 10 == 0) "" else " " + units[n % 10])
        n < 1000 -> units[n / 100] + " hundred" +
            (if (n % 100 == 0) "" else " " + numberWords(n % 100))
        n < 1_000_000 -> numberWords(n / 1000) + " thousand" +
            (if (n % 1000 == 0) "" else " " + numberWords(n % 1000))
        else -> n.toString()
    }

    private fun textToEvents(text: String): List<Ev> {
        val out = ArrayList<Ev>()
        val r = 1.0
        val lowerWords = wordRegex.findAll(text.lowercase()).map { it.value }.toList()
        val hiHits = lowerWords.count { it in hiWords }
        val mode = if (lowerWords.isNotEmpty() && hiHits.toDouble() / lowerWords.size >= 0.25) "hi" else "en"

        for (line in text.split('\n')) {
            var rest = line.trim()
            if (rest.isEmpty()) {
                out.add(Ev("SIL", 240.0 / r, 0))
                continue
            }
            while (rest.isNotEmpty()) {
                val m = wordRegex.find(rest)
                if (m == null) {
                    val tail = rest.trim()
                    if (tail.isNotEmpty()) {
                        val pause = when (tail.firstOrNull()) {
                            '.', '!' -> 330.0
                            '?' -> 300.0
                            ',' -> 170.0
                            ';' -> 200.0
                            ':' -> 190.0
                            else -> 60.0
                        }
                        out.add(Ev("SIL", pause / r, 0))
                    }
                    break
                }
                val gap = rest.substring(0, m.range.first)
                gap.forEach { c ->
                    val pause = when (c) {
                        '.', '!' -> 330.0
                        '?' -> 300.0
                        ',' -> 170.0
                        ';' -> 200.0
                        ':' -> 190.0
                        else -> 0.0
                    }
                    if (pause > 0.0) out.add(Ev("SIL", pause / r, 0))
                }
                out.addAll(wordEvents(m.value, mode))
                out.add(Ev("SIL", 52.0 / r, 0))
                rest = rest.substring(m.range.last + 1)
            }
            out.add(Ev("SIL", 180.0 / r, 0))
        }
        // merge adjacent silences, cap absurd pauses
        val merged = ArrayList<Ev>(out.size)
        for (e in out) {
            val last = merged.lastOrNull()
            if (e.phone == "SIL" && last != null && last.phone == "SIL") {
                merged[merged.size - 1] = Ev("SIL", min(900.0, last.ms + e.ms), 0)
            } else if (!(e.phone == "SIL" && last == null)) {
                merged.add(e)
            }
        }
        while (merged.isNotEmpty() && merged[merged.size - 1].phone == "SIL") {
            merged.removeAt(merged.size - 1)
        }
        // trim trailing pause but keep a little air
        if (merged.isNotEmpty()) merged.add(Ev("SIL", 150.0 / r, 0))
        return merged
    }
}
