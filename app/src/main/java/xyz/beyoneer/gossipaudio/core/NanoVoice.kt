package xyz.beyoneer.gossipaudio.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * NanoVoice — GossipAudio's own speech synthesiser.
 *
 * It is a small cascaded-formant (Klatt-style) engine with an English
 * letter-to-sound front end. It needs no downloads, no network, no GPU and no
 * system TTS engine, which is why the "Beyoneer Bot" voice and the offline
 * fallback can never fail. It is intentionally robotic — that is the brand.
 *
 * Pure Kotlin: no android.* imports, so it is unit-tested on the JVM.
 */
object NanoVoice {

    const val SAMPLE_RATE = 22050

    data class Options(
        val baseF0: Double = 118.0,
        val formantScale: Double = 1.0,
        val breathiness: Double = 0.035,
        val roughness: Double = 0.0,
        val vibrato: Double = 0.0,
        val robotic: Boolean = false,
        val rate: Double = 1.0,
        val pitch: Double = 1.0,
        val sampleRate: Int = SAMPLE_RATE
    )

    fun speak(text: String, options: Options = Options()): Pcm {
        val clean = TextKit.sanitize(text)
        if (clean.isBlank()) return Pcm(options.sampleRate, 1, ShortArray(0))
        val frames = ArrayList<Frame>(512)
        val sentences = splitSentences(clean)
        var absolute = 0.0
        var total = 0.0
        for (s in sentences) total += s.length + 12
        for (sentence in sentences) {
            val words = parseWords(sentence.text)
            appendWords(words, sentence, frames, options)
            absolute += sentence.text.length + 12
            val progress = if (total <= 0) 1.0 else (absolute / total).coerceIn(0.0, 1.0)
            frames.add(Frame.pause(msToSamples(sentence.pauseMs, options), declination(progress, options)))
        }
        if (frames.isEmpty()) return Pcm(options.sampleRate, 1, ShortArray(0))
        val samples = render(frames, options)
        val pcm = AudioFx.normalize(Pcm(options.sampleRate, 1, samples), -2.0)
        return AudioFx.fade(pcm, 8, 30)
    }

    // ---------------------------------------------------------------- language

    private data class Sentence(val text: String, val question: Boolean, val exclaim: Boolean, val pauseMs: Double)

    private fun splitSentences(text: String): List<Sentence> {
        val out = ArrayList<Sentence>()
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(ch)
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n' || ch == ';') {
                val t = sb.toString().trim()
                if (t.isNotEmpty()) out.add(Sentence(t, ch == '?', ch == '!', if (ch == '\n') 420.0 else 300.0))
                sb.setLength(0)
            }
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) out.add(Sentence(tail, false, false, 160.0))
        // Keep utterances short so intonation stays natural and memory stays flat.
        if (out.size <= 64) return out
        return out.subList(0, 64)
    }

    private data class Word(val phones: List<Phone>, val stressedSyllable: Int)

    private data class Phone(val sym: String, val stress: Boolean = false, val sylIndex: Int = -1)

    private fun parseWords(sentence: String): List<Pair<Word, Double>> {
        val out = ArrayList<Pair<Word, Double>>()
        val regex = Regex("[A-Za-z']+|[,.!?;:]+|\\s+")
        var lastEnd = 0.0
        for (m in regex.findAll(sentence)) {
            val token = m.value
            when {
                token[0].isLetter() || token[0] == '\'' -> {
                    val phones = phonemize(token)
                    if (phones.isNotEmpty()) {
                        val syl = syllableCount(phones)
                        val stress = defaultStress(token, syl)
                        out.add(Word(phones.withIndex().map { (i, p) ->
                            Phone(p, stressIndex(phones, i) == stress, sylIndexOf(phones, i))
                        }, stress) to 0.0)
                    }
                }
                token[0] == '.' -> if (out.isNotEmpty()) lastEnd = max(lastEnd, 320.0)
                token[0] == '!' -> if (out.isNotEmpty()) lastEnd = max(lastEnd, 340.0)
                token[0] == '?' -> if (out.isNotEmpty()) lastEnd = max(lastEnd, 380.0)
                token[0] == ',' || token[0] == ';' || token[0] == ':' -> if (out.isNotEmpty()) lastEnd = max(lastEnd, 180.0)
            }
        }
        if (out.isNotEmpty() && lastEnd > 0) {
            out[out.size - 1] = out.last().first to lastEnd
        }
        return out
    }

    private fun syllableCount(phones: List<String>): Int = phones.count { isVowel(it) }.coerceAtLeast(1)

    private fun sylIndexOf(phones: List<String>, index: Int): Int {
        var syl = -1
        for (i in 0..index) if (isVowel(phones[i])) syl++
        return if (syl < 0) 0 else syl
    }

    private fun stressIndex(phones: List<String>, index: Int): Int = sylIndexOf(phones, index)

    private fun defaultStress(wordRaw: String, syllables: Int): Int {
        val word = wordRaw.lowercase()
        if (syllables <= 1) return 0
        val prefixes = listOf("be", "re", "de", "in", "un", "con", "com", "ex", "pre", "pro", "a", "en", "em")
        if (prefixes.any { word.startsWith(it) && word.length > it.length + 2 }) return 1
        if (word.endsWith("tion") || word.endsWith("sion") || word.endsWith("ic") || word.endsWith("ity")) {
            return max(0, syllables - 2)
        }
        return 0
    }

    private val exceptions: Map<String, List<String>> = mapOf(
        "a" to listOf("AX"), "the" to listOf("DH", "AX"), "to" to listOf("T", "UW"),
        "of" to listOf("AH", "V"), "and" to listOf("AE", "N", "D"), "is" to listOf("IH", "Z"),
        "are" to listOf("AA", "R"), "was" to listOf("W", "AH", "Z"), "were" to listOf("W", "ER"),
        "one" to listOf("W", "AH", "N"), "two" to listOf("T", "UW"), "who" to listOf("HH", "UW"),
        "what" to listOf("W", "AH", "T"), "where" to listOf("W", "EH", "R"), "there" to listOf("DH", "EH", "R"),
        "here" to listOf("HH", "IH", "R"), "you" to listOf("Y", "UW"), "your" to listOf("Y", "AO", "R"),
        "i" to listOf("AY"), "my" to listOf("M", "AY"), "me" to listOf("M", "IY"), "we" to listOf("W", "IY"),
        "he" to listOf("HH", "IY"), "she" to listOf("SH", "IY"), "they" to listOf("DH", "EY"),
        "their" to listOf("DH", "EH", "R"), "them" to listOf("DH", "EH", "M"), "this" to listOf("DH", "IH", "S"),
        "that" to listOf("DH", "AE", "T"), "these" to listOf("DH", "IY", "Z"), "those" to listOf("DH", "OW", "Z"),
        "have" to listOf("HH", "AE", "V"), "has" to listOf("HH", "AE", "Z"), "had" to listOf("HH", "AE", "D"),
        "do" to listOf("D", "UW"), "does" to listOf("D", "AH", "Z"), "did" to listOf("D", "IH", "D"),
        "done" to listOf("D", "AH", "N"), "go" to listOf("G", "OW"), "going" to listOf("G", "OW", "IH", "NG"),
        "said" to listOf("S", "EH", "D"), "says" to listOf("S", "EH", "Z"), "say" to listOf("S", "EY"),
        "because" to listOf("B", "IH", "K", "AO", "Z"), "people" to listOf("P", "IY", "P", "AX", "L"),
        "little" to listOf("L", "IH", "T", "AX", "L"), "very" to listOf("V", "EH", "R", "IY"),
        "sorry" to listOf("S", "AA", "R", "IY"), "okay" to listOf("OW", "K", "EY"), "ok" to listOf("OW", "K", "EY"),
        "hello" to listOf("HH", "EH", "L", "OW"), "yeah" to listOf("Y", "EH"), "yes" to listOf("Y", "EH", "S"),
        "no" to listOf("N", "OW"), "not" to listOf("N", "AA", "T"), "nothing" to listOf("N", "AH", "TH", "IH", "NG"),
        "some" to listOf("S", "AH", "M"), "come" to listOf("K", "AH", "M"), "love" to listOf("L", "AH", "V"),
        "gossip" to listOf("G", "AA", "S", "IH", "P"), "audio" to listOf("AO", "D", "IY", "OW"),
        "gossipaudio" to listOf("G", "AA", "S", "IH", "P", "AO", "D", "IY", "OW"),
        "beyoneer" to listOf("B", "IY", "AO", "N", "IY", "R"), "funny" to listOf("F", "AH", "N", "IY"),
        "really" to listOf("R", "IY", "L", "IY"), "friend" to listOf("F", "R", "EH", "N", "D"),
        "beautiful" to listOf("B", "Y", "UW", "T", "IH", "F", "AX", "L"), "the" to listOf("DH", "AX"),
        "download" to listOf("D", "AW", "N", "L", "OW", "D"), "cool" to listOf("K", "UW", "L"),
        "water" to listOf("W", "AO", "T", "ER"), "again" to listOf("AX", "G", "EH", "N"),
        "science" to listOf("S", "AY", "AX", "N", "S"), "phone" to listOf("F", "OW", "N"),
        "photo" to listOf("F", "OW", "T", "OW"), "laugh" to listOf("L", "AE", "F"),
        "though" to listOf("DH", "OW"), "through" to listOf("TH", "R", "UW"), "thought" to listOf("TH", "AO", "T"),
        "enough" to listOf("IH", "N", "AH", "F"), "night" to listOf("N", "AY", "T"),
        "light" to listOf("L", "AY", "T"), "right" to listOf("R", "AY", "T"),
        "might" to listOf("M", "AY", "T"), "eight" to listOf("EY", "T"), "height" to listOf("HH", "AY", "T"),
        "who's" to listOf("HH", "UW", "Z"), "don't" to listOf("D", "OW", "N", "T"),
        "can't" to listOf("K", "AE", "N", "T"), "won't" to listOf("W", "OW", "N", "T"),
        "isn't" to listOf("IH", "Z", "AX", "N", "T"), "aren't" to listOf("AA", "R", "AX", "N", "T"),
        "i'm" to listOf("AY", "M"), "it's" to listOf("IH", "T", "S"), "that's" to listOf("DH", "AE", "T", "S"),
        "you're" to listOf("Y", "AO", "R"), "we're" to listOf("W", "IH", "R"), "they're" to listOf("DH", "EH", "R")
    )

    /** English-ish letter-to-sound rules. Fast, tiny, and surprisingly readable. */
    fun phonemize(wordRaw: String): List<String> {
        val lower = wordRaw.lowercase().trim('\'', ' ')
        if (lower.isEmpty()) return emptyList()
        exceptions[lower]?.let { return it }
        if (exceptions[lower.removeSuffix("'s")] != null) {
            return exceptions[lower.removeSuffix("'s")]!! + listOf("Z")
        }
        val out = ArrayList<String>()
        val w = lower
        var i = 0
        val n = w.length
        fun peek(k: Int): String = if (i + k < n) w.substring(i, i + k) else ""

        // silent final e detection for magic-e words
        val endsWithSilentE = w.length > 2 && w.endsWith("e") && !w.endsWith("le") && !w.endsWith("ee") && !w.endsWith("ie")

        while (i < n) {
            val c = w[i]
            val rest = w.substring(i)
            when {
                rest.startsWith("tion") -> { out.add("SH"); out.add("AX"); out.add("N"); i += 4 }
                rest.startsWith("sion") -> { out.add("ZH"); out.add("AX"); out.add("N"); i += 4 }
                rest.startsWith("tch") -> { out.add("CH"); i += 3 }
                rest.startsWith("dge") -> { out.add("JH"); i += 3 }
                rest.startsWith("igh") -> { out.add("AY"); i += 3 }
                rest.startsWith("ough") -> { out.add("AO"); i += 4 }
                rest.startsWith("augh") -> { out.add("AO"); i += 4 }
                rest.startsWith("eigh") -> { out.add("EY"); i += 4 }
                rest.startsWith("air") -> { out.add("EH"); out.add("R"); i += 3 }
                rest.startsWith("ear") -> { out.add("IH"); out.add("R"); i += 3 }
                rest.startsWith("eer") -> { out.add("IH"); out.add("R"); i += 3 }
                rest.startsWith("oor") -> { out.add("AO"); out.add("R"); i += 3 }
                rest.startsWith("our") -> { out.add("AW"); out.add("ER"); i += 3 }
                rest.startsWith("ar") && i + 2 < n -> { out.add("AA"); out.add("R"); i += 2 }
                rest.startsWith("er") && i + 2 < n -> { out.add("ER"); i += 2 }
                rest.startsWith("ir") && i + 2 < n -> { out.add("ER"); i += 2 }
                rest.startsWith("ur") && i + 2 < n -> { out.add("ER"); i += 2 }
                rest.startsWith("or") && i + 2 < n -> { out.add("AO"); out.add("R"); i += 2 }
                rest.startsWith("oo") -> { out.add("UW"); i += 2 }
                rest.startsWith("ee") -> { out.add("IY"); i += 2 }
                rest.startsWith("ea") -> { out.add("IY"); i += 2 }
                rest.startsWith("ai") -> { out.add("EY"); i += 2 }
                rest.startsWith("ay") -> { out.add("EY"); i += 2 }
                rest.startsWith("ey") -> { out.add("EY"); i += 2 }
                rest.startsWith("oi") -> { out.add("OY"); i += 2 }
                rest.startsWith("oy") -> { out.add("OY"); i += 2 }
                rest.startsWith("ou") -> { out.add("AW"); i += 2 }
                rest.startsWith("ow") -> { out.add("OW"); i += 2 }
                rest.startsWith("oa") -> { out.add("OW"); i += 2 }
                rest.startsWith("au") -> { out.add("AO"); i += 2 }
                rest.startsWith("aw") -> { out.add("AO"); i += 2 }
                rest.startsWith("ei") -> { out.add("EY"); i += 2 }
                rest.startsWith("ie") -> { out.add("IY"); i += 2 }
                rest.startsWith("ue") -> { out.add("UW"); i += 2 }
                rest.startsWith("ui") -> { out.add("UW"); i += 2 }
                rest.startsWith("ph") -> { out.add("F"); i += 2 }
                rest.startsWith("sh") -> { out.add("SH"); i += 2 }
                rest.startsWith("ch") -> { out.add("CH"); i += 2 }
                rest.startsWith("th") -> { out.add("TH"); i += 2 }
                rest.startsWith("wh") -> { out.add("W"); i += 2 }
                rest.startsWith("ck") -> { out.add("K"); i += 2 }
                rest.startsWith("ng") -> { out.add("NG"); i += 2 }
                rest.startsWith("kn") && i == 0 -> { out.add("N"); i += 2 }
                rest.startsWith("wr") && i == 0 -> { out.add("R"); i += 2 }
                rest.startsWith("gh") -> { out.add("G"); i += 2 }
                rest.startsWith("qu") -> { out.add("K"); out.add("W"); i += 2 }
                rest.startsWith("mb") && i + 2 == n -> { out.add("M"); i += 2 }
                rest.startsWith("le") && i + 2 == n && out.isNotEmpty() && !isVowel(out.last()) -> { out.add("AX"); out.add("L"); i += 2 }
                c == '\'' -> { i += 1 }
                c == 'a' -> {
                    out.add(if (endsWithSilentE && i == vowelIndexBeforeSilentE(w)) "EY" else "AE"); i++
                }
                c == 'e' -> {
                    if (i == n - 1) { i++ } else { out.add(if (endsWithSilentE && i == vowelIndexBeforeSilentE(w)) "IY" else "EH"); i++ }
                }
                c == 'i' -> {
                    out.add(if (endsWithSilentE && i == vowelIndexBeforeSilentE(w)) "AY" else "IH"); i++
                }
                c == 'o' -> {
                    out.add(if (endsWithSilentE && i == vowelIndexBeforeSilentE(w)) "OW" else "AA"); i++
                }
                c == 'u' -> {
                    out.add(if (endsWithSilentE && i == vowelIndexBeforeSilentE(w)) "UW" else "AH"); i++
                }
                c == 'y' -> {
                    out.add(if (i == n - 1) "IY" else "IH"); i++
                }
                c == 'b' -> { out.add("B"); i++ }
                c == 'c' -> { out.add(if (peek(2).length == 2 && "eiy".contains(peek(2)[1])) "S" else "K"); i++ }
                c == 'd' -> { out.add("D"); i++ }
                c == 'f' -> { out.add("F"); i++ }
                c == 'g' -> { out.add(if (peek(2).length == 2 && "eiy".contains(peek(2)[1])) "JH" else "G"); i++ }
                c == 'h' -> { out.add("HH"); i++ }
                c == 'j' -> { out.add("JH"); i++ }
                c == 'k' -> { out.add("K"); i++ }
                c == 'l' -> { out.add("L"); i++ }
                c == 'm' -> { out.add("M"); i++ }
                c == 'n' -> { out.add("N"); i++ }
                c == 'p' -> { out.add("P"); i++ }
                c == 'q' -> { out.add("K"); i++ }
                c == 'r' -> { out.add("R"); i++ }
                c == 's' -> {
                    val prevVoiced = out.isNotEmpty() && isVoiced(out.last())
                    out.add(if (i == n - 1 && prevVoiced) "Z" else "S"); i++
                }
                c == 't' -> { out.add("T"); i++ }
                c == 'v' -> { out.add("V"); i++ }
                c == 'w' -> { out.add("W"); i++ }
                c == 'x' -> { out.add("K"); out.add("S"); i++ }
                c == 'z' -> { out.add("Z"); i++ }
                else -> i++
            }
        }
        if (out.isEmpty()) out.add("AX")
        return out
    }

    private fun vowelIndexBeforeSilentE(w: String): Int {
        for (k in w.length - 2 downTo 0) if ("aeiou".contains(w[k])) return k
        return -1
    }

    private fun isVowel(sym: String): Boolean = sym in VOWELS
    private fun isVoiced(sym: String): Boolean = sym in VOICED_CONSONANTS

    private val VOWELS = setOf("AA", "AE", "AH", "AO", "AW", "AY", "EH", "ER", "EY", "IH", "IY", "OW", "OY", "UH", "UW", "AX")
    private val DIPHTHONGS = setOf("AW", "AY", "EY", "OW", "OY")
    private val VOICED_CONSONANTS = setOf("B", "D", "G", "V", "DH", "Z", "ZH", "JH", "M", "N", "NG", "L", "R", "W", "Y")

    // ------------------------------------------------------------------ frames

    private class Frame(
        val samples: Int,
        val f1: Double,
        val f2: Double,
        val f3: Double,
        val voiced: Double,
        val noise: Double,
        val amp: Double,
        val f0: Double
    ) {
        companion object {
            fun pause(samples: Int, f0: Double) = Frame(samples, 500.0, 1500.0, 2500.0, 0.0, 0.0, 0.0, f0)
        }
    }

    private fun msToSamples(ms: Double, o: Options): Int =
        max(1, (ms / 1000.0 * o.sampleRate * (1.0 / o.rate.coerceIn(0.5, 2.0))).toInt())

    private fun declination(progress: Double, o: Options): Double =
        o.baseF0 * o.pitch * (1.0 - 0.18 * progress.coerceIn(0.0, 1.0))

    private fun appendWords(
        words: List<Pair<Word, Double>>,
        sentence: Sentence,
        frames: MutableList<Frame>,
        o: Options
    ) {
        if (words.isEmpty()) return
        val totalSyl = words.sumOf { w -> w.first.phones.count { isVowel(it) } }.coerceAtLeast(1)
        var sylSpoken = 0
        for ((wordIndex, pair) in words.withIndex()) {
            val word = pair.first
            val trailingPause = pair.second
            for ((pi, phone) in word.phones.withIndex()) {
                val base = phoneBase(phone.sym) ?: continue
                val isVowelPhone = isVowel(phone.sym)
                if (isVowelPhone) sylSpoken++
                val progress = sylSpoken.toDouble() / totalSyl
                var f0 = declination(progress, o)
                if (phone.stress) f0 *= 1.12
                if (sentence.question && wordIndex >= words.size - 2) f0 *= 1.0 + 0.16 * (wordIndex.toDouble() / words.size)
                if (o.vibrato > 0) f0 *= 1.0 + o.vibrato * sin(2.0 * PI * 5.0 * sylSpoken / 3.0)
                val durScale = if (phone.stress) 1.18 else 1.0
                val rawDur = base.durMs * durScale
                val nextSym = word.phones.getOrNull(pi + 1) ?: ""
                when (base.kind) {
                    Kind.VOWEL -> {
                        if (phone.sym in DIPHTHONGS) {
                            val half = msToSamples(rawDur * 0.55, o)
                            val second = msToSamples(rawDur * 0.65, o)
                            frames.add(Frame(half, base.f1, base.f2, base.f3, 1.0, 0.0, 0.85, f0))
                            val t = diphthongTarget(phone.sym)
                            frames.add(Frame(second, t[0], t[1], t[2], 1.0, 0.0, 0.85, f0 * 0.98))
                        } else {
                            frames.add(Frame(msToSamples(rawDur, o), base.f1, base.f2, base.f3, 1.0, 0.0, 0.85, f0))
                        }
                    }
                    Kind.NASAL -> frames.add(
                        Frame(msToSamples(base.durMs, o), base.f1, base.f2, base.f3, 0.75, 0.02, 0.55, f0 * 0.96)
                    )
                    Kind.LIQUID -> frames.add(
                        Frame(msToSamples(base.durMs, o), base.f1, base.f2, base.f3, 0.9, 0.0, 0.65, f0)
                    )
                    Kind.FRICATIVE -> {
                        val voiced = if (isVoiced(phone.sym)) 0.45 else 0.0
                        frames.add(Frame(msToSamples(base.durMs, o), base.f1, base.f2, base.f3, voiced, base.noise, 0.5, f0))
                    }
                    Kind.STOP -> {
                        val closureMs = if (phone.sym == "P" || phone.sym == "B" || phone.sym == "T" || phone.sym == "D") 42.0 else 58.0
                        frames.add(Frame(msToSamples(closureMs, o), 300.0, 1200.0, 2400.0, 0.0, 0.0, 0.0, f0))
                        frames.add(Frame(msToSamples(16.0, o), base.f1, base.f2, base.f3, 0.0, base.noise, 0.75, f0))
                        if (isVoiced(phone.sym)) {
                            frames.add(Frame(msToSamples(14.0, o), base.f1, base.f2, base.f3, 0.7, 0.05, 0.45, f0 * 0.95))
                        }
                    }
                    Kind.ASPIRATE -> {
                        val v = vowelTargets(nextSym)
                        frames.add(Frame(msToSamples(base.durMs, o), v[0], v[1], v[2], 0.0, base.noise, 0.35, f0))
                    }
                    Kind.SILENCE -> frames.add(Frame.pause(msToSamples(base.durMs, o), f0))
                }
            }
            if (trailingPause > 0) {
                frames.add(Frame.pause(msToSamples(trailingPause, o), declination(1.0, o)))
            } else if (wordIndex < words.size - 1) {
                frames.add(Frame.pause(msToSamples(28.0, o), declination(0.5, o)))
            }
        }
    }

    private enum class Kind { VOWEL, NASAL, LIQUID, FRICATIVE, STOP, ASPIRATE, SILENCE }

    private class PhoneBase(val durMs: Double, val f1: Double, val f2: Double, val f3: Double, val noise: Double, val kind: Kind)

    private fun vowelTargets(sym: String): DoubleArray = when (sym) {
        "AA" -> doubleArrayOf(730.0, 1090.0, 2440.0)
        "AE" -> doubleArrayOf(660.0, 1720.0, 2410.0)
        "AH", "AX" -> doubleArrayOf(620.0, 1200.0, 2400.0)
        "AO" -> doubleArrayOf(570.0, 840.0, 2410.0)
        "EH" -> doubleArrayOf(530.0, 1840.0, 2480.0)
        "ER" -> doubleArrayOf(490.0, 1350.0, 1690.0)
        "IH" -> doubleArrayOf(390.0, 1990.0, 2550.0)
        "IY" -> doubleArrayOf(270.0, 2290.0, 3010.0)
        "UH" -> doubleArrayOf(440.0, 1020.0, 2240.0)
        "UW" -> doubleArrayOf(300.0, 870.0, 2240.0)
        "AW" -> doubleArrayOf(700.0, 1150.0, 2450.0)
        "AY" -> doubleArrayOf(680.0, 1650.0, 2420.0)
        "EY" -> doubleArrayOf(480.0, 1720.0, 2520.0)
        "OW" -> doubleArrayOf(500.0, 900.0, 2400.0)
        "OY" -> doubleArrayOf(520.0, 880.0, 2400.0)
        else -> doubleArrayOf(620.0, 1200.0, 2400.0)
    }

    private fun diphthongTarget(sym: String): DoubleArray = when (sym) {
        "AW" -> doubleArrayOf(420.0, 1000.0, 2300.0)
        "AY" -> doubleArrayOf(300.0, 2200.0, 2900.0)
        "EY" -> doubleArrayOf(300.0, 2200.0, 2900.0)
        "OW" -> doubleArrayOf(320.0, 850.0, 2250.0)
        "OY" -> doubleArrayOf(330.0, 2100.0, 2800.0)
        else -> doubleArrayOf(600.0, 1200.0, 2400.0)
    }

    private fun phoneBase(sym: String): PhoneBase? = when (sym) {
        "AA", "AE", "AH", "AX", "AO", "EH", "ER", "IH", "IY", "UH", "UW" -> {
            val t = vowelTargets(sym)
            PhoneBase(if (sym == "AX") 72.0 else 105.0, t[0], t[1], t[2], 0.0, Kind.VOWEL)
        }
        "AW", "AY", "EY", "OW", "OY" -> {
            val t = vowelTargets(sym)
            PhoneBase(150.0, t[0], t[1], t[2], 0.0, Kind.VOWEL)
        }
        "M" -> PhoneBase(72.0, 250.0, 1100.0, 2200.0, 0.0, Kind.NASAL)
        "N" -> PhoneBase(70.0, 250.0, 1600.0, 2500.0, 0.0, Kind.NASAL)
        "NG" -> PhoneBase(76.0, 250.0, 2000.0, 2600.0, 0.0, Kind.NASAL)
        "L" -> PhoneBase(70.0, 360.0, 1100.0, 2700.0, 0.0, Kind.LIQUID)
        "R" -> PhoneBase(68.0, 350.0, 1200.0, 1600.0, 0.0, Kind.LIQUID)
        "W" -> PhoneBase(62.0, 300.0, 610.0, 2200.0, 0.0, Kind.LIQUID)
        "Y" -> PhoneBase(58.0, 260.0, 2200.0, 3000.0, 0.0, Kind.LIQUID)
        "F" -> PhoneBase(88.0, 1200.0, 4000.0, 6500.0, 0.18, Kind.FRICATIVE)
        "V" -> PhoneBase(72.0, 1200.0, 4000.0, 6500.0, 0.14, Kind.FRICATIVE)
        "TH" -> PhoneBase(86.0, 1400.0, 4200.0, 6800.0, 0.2, Kind.FRICATIVE)
        "DH" -> PhoneBase(66.0, 1400.0, 4200.0, 6800.0, 0.14, Kind.FRICATIVE)
        "S" -> PhoneBase(96.0, 1600.0, 5200.0, 7200.0, 0.42, Kind.FRICATIVE)
        "Z" -> PhoneBase(80.0, 1600.0, 5200.0, 7200.0, 0.3, Kind.FRICATIVE)
        "SH" -> PhoneBase(96.0, 1800.0, 3200.0, 4500.0, 0.4, Kind.FRICATIVE)
        "ZH" -> PhoneBase(80.0, 1800.0, 3200.0, 4500.0, 0.3, Kind.FRICATIVE)
        "HH" -> PhoneBase(62.0, 1000.0, 2200.0, 3000.0, 0.22, Kind.ASPIRATE)
        "P" -> PhoneBase(60.0, 900.0, 1200.0, 2400.0, 0.28, Kind.STOP)
        "B" -> PhoneBase(60.0, 900.0, 1200.0, 2400.0, 0.22, Kind.STOP)
        "T" -> PhoneBase(60.0, 1800.0, 4000.0, 5500.0, 0.3, Kind.STOP)
        "D" -> PhoneBase(60.0, 1800.0, 4000.0, 5500.0, 0.22, Kind.STOP)
        "K" -> PhoneBase(66.0, 1200.0, 2200.0, 3200.0, 0.3, Kind.STOP)
        "G" -> PhoneBase(66.0, 1200.0, 2200.0, 3200.0, 0.22, Kind.STOP)
        "CH" -> PhoneBase(90.0, 1800.0, 3200.0, 4500.0, 0.36, Kind.FRICATIVE)
        "JH" -> PhoneBase(80.0, 1800.0, 3200.0, 4500.0, 0.28, Kind.FRICATIVE)
        else -> null
    }

    // --------------------------------------------------------------- rendering

    private fun render(frames: List<Frame>, o: Options): ShortArray {
        val total = frames.sumOf { it.samples }
        if (total <= 0) return ShortArray(0)
        val out = ShortArray(total)
        val fs = o.sampleRate.toDouble()
        var phase = 0.0
        var seed = 0x2545F491
        // resonator state (3 cascaded formant filters)
        var y1a = 0.0; var y2a = 0.0
        var y1b = 0.0; var y2b = 0.0
        var y1c = 0.0; var y2c = 0.0
        var lp = 0.0
        // smoothed formants
        var f1 = 500.0; var f2 = 1500.0; var f3 = 2500.0
        var n = 0
        val fsScale = o.formantScale
        val robotDepth = if (o.robotic) 0.25 else 0.0
        for (frame in frames) {
            val bw1 = if (o.robotic) 55.0 else 85.0
            val bw2 = if (o.robotic) 70.0 else 110.0
            val bw3 = if (o.robotic) 90.0 else 140.0
            val targetF1 = frame.f1 * fsScale * (if (o.robotic) 1.0 else 1.0)
            val targetF2 = frame.f2 * fsScale
            val targetF3 = frame.f3 * fsScale
            val coeffA = coeff(targetF1, bw1, fs)
            val coeffB = coeff(targetF2, bw2, fs)
            val coeffC = coeff(targetF3, bw3, fs)
            val glide = 0.22
            val count = frame.samples
            for (i in 0 until count) {
                val progress = if (count <= 1) 1.0 else i.toDouble() / count
                // envelope: quick attack, soft release
                val env = when {
                    progress < 0.08 -> progress / 0.08
                    progress > 0.85 -> (1.0 - progress) / 0.15
                    else -> 1.0
                }
                f1 += (targetF1 - f1) * glide
                f2 += (targetF2 - f2) * glide
                f3 += (targetF3 - f3) * glide
                // precompute interpolated coefficients once per sample is too slow;
                // use the frame coefficients but let the smoother nudge resonance.
                val cA = if (i == 0) coeffA else coeff((f1).coerceAtLeast(200.0), bw1, fs)
                val cB = if (i == 0) coeffB else coeff((f2).coerceAtLeast(500.0), bw2, fs)
                val cC = if (i == 0) coeffC else coeff((f3).coerceAtLeast(1000.0), bw3, fs)

                var f0 = frame.f0
                if (o.robotic) f0 = (f0 / 12.0).toInt() * 12.0
                phase += f0 / fs
                if (phase >= 1.0) phase -= 1.0

                var src = 0.0
                if (frame.voiced > 0.0) {
                    val glottal = glottalPulse(phase) * frame.voiced
                    src += glottal * 0.9
                    if (o.roughness > 0) {
                        seed = seed * 1103515245 + 12345
                        src += ((seed shr 16) and 0x7FFF) / 32768.0 * o.roughness * 0.08
                    }
                }
                if (frame.noise > 0.0) {
                    seed = seed * 1103515245 + 12345
                    val rnd = ((seed shr 16) and 0x7FFF) / 32768.0 * 2.0 - 1.0
                    src += rnd * frame.noise
                }
                if (frame.voiced > 0.0 && o.breathiness > 0) {
                    seed = seed * 1103515245 + 12345
                    val rnd = ((seed shr 16) and 0x7FFF) / 32768.0 * 2.0 - 1.0
                    src += rnd * o.breathiness * frame.voiced
                }
                if (robotDepth > 0.0) {
                    src *= 1.0 - robotDepth + robotDepth * cos(2.0 * PI * 53.0 * n / fs)
                }

                // cascade of three resonators
                val oa = src + cA[0] * y1a + cA[1] * y2a
                y2a = y1a; y1a = oa
                val ob = oa + cB[0] * y1b + cB[1] * y2b
                y2b = y1b; y1b = ob
                val oc = ob + cC[0] * y1c + cC[1] * y2c
                y2c = y1c; y1c = oc
                // gentle one-pole low pass to tame hiss
                lp += (oc - lp) * 0.45
                var sample = lp * frame.amp * env * 5.2
                if (sample > 32767) sample = 32767.0
                if (sample < -32768) sample = -32768.0
                out[n] = sample.toInt().toShort()
                n++
            }
        }
        return out
    }

    /** Two-pole resonator coefficients: [b1, b2] with a0 normalised to 1. */
    private fun coeff(freq: Double, bw: Double, fs: Double): DoubleArray {
        val f = freq.coerceIn(60.0, fs / 2.0 - 200.0)
        val r = exp(-PI * bw / fs)
        val theta = 2.0 * PI * f / fs
        val b1 = 2.0 * r * cos(theta)
        val b2 = -r * r
        return doubleArrayOf(b1, b2)
    }

    private fun glottalPulse(phase: Double): Double {
        // Rosenberg-style glottal pulse, open quotient ~0.6
        return when {
            phase < 0.4 -> 0.5 * (1.0 - cos(PI * phase / 0.4))
            phase < 0.6 -> 0.5 * (1.0 + cos(PI * (phase - 0.4) / 0.2))
            else -> 0.0
        }
    }

    /** Convenience helper used by the diagnostics screen. */
    fun estimateCostMs(chars: Int): Long = (chars * 0.9).toLong() + 120

    @Suppress("unused")
    private fun dbToGain(db: Double): Double = 10.0.pow(db / 20.0)
}
