package xyz.beyoneer.gossipaudio.core

enum class VoiceGender { FEMALE, MALE, NONHUMAN }

enum class VoiceEngine {
    NEURAL,
    /** Android system TTS engine installed on the phone (Google TTS, Samsung, etc.). */
    SYSTEM_TTS,

    /** GossipAudio's own zero-dependency formant engine ("NanoVoice"), always available. */
    NANO_VOICE
}

/**
 * The app's single bundled neural voice profile.
 */
data class VoiceProfile(
    val id: String,
    val name: String,
    val gender: VoiceGender,
    val locale: String,
    val flag: String,
    val region: String,
    val pitch: Float,
    val rate: Float,
    val timbre: Float,
    val vibe: String,
    val bio: String,
    val engine: VoiceEngine,
    val accent: Int
) {
    val genderLabel: String get() = "Neural"
}

object VoiceCatalog {

    // One full-precision pretrained Piper voice; riya preserves older clip IDs.
    val all: List<VoiceProfile> = listOf(
        VoiceProfile(
            id = "riya", name = "Lessac", gender = VoiceGender.FEMALE,
            locale = "en-US", flag = "🇺🇸", region = "English (US)",
            pitch = 1f, rate = 1f, timbre = 1f,
            vibe = "Full-precision neural voice",
            bio = "Bundled for evaluation only: its training recordings have non-commercial restrictions. Do not use commercially without permission.",
            engine = VoiceEngine.NEURAL, accent = 0xFFF5C518.toInt()
        ),
        VoiceProfile(
            id = "cori", name = "Cori", gender = VoiceGender.FEMALE,
            locale = "en-GB", flag = "🇬🇧", region = "English (UK)",
            pitch = 1f, rate = 1f, timbre = 1f,
            vibe = "British English · neural",
            bio = "Full-precision Piper voice trained from scratch on LibriVox recordings. Download once, then synthesize offline.",
            engine = VoiceEngine.NEURAL, accent = 0xFFF5C518.toInt()
        ),
        VoiceProfile(
            id = "ljspeech", name = "LJ Speech", gender = VoiceGender.FEMALE,
            locale = "en-US", flag = "🇺🇸", region = "English (US)",
            pitch = 1f, rate = 1f, timbre = 1f,
            vibe = "American English · neural",
            bio = "Full-precision Piper voice trained from scratch on the public-domain LJ Speech dataset. Download once for offline use.",
            engine = VoiceEngine.NEURAL, accent = 0xFFF5C518.toInt()
        ),
        VoiceProfile(id = "hi_super", name = "Hindi", gender = VoiceGender.FEMALE,
            locale = "hi-IN", flag = "🇮🇳", region = "Hindi", pitch = 1f, rate = 1f, timbre = 1f,
            vibe = "Supertonic 3 · shared pack", bio = "Hindi neural voice. One shared download unlocks Hindi, Japanese and French. OpenRAIL-M use restrictions apply.", engine = VoiceEngine.NEURAL, accent = 0xFFF5C518.toInt()),
        VoiceProfile(id = "ja_super", name = "Japanese", gender = VoiceGender.FEMALE,
            locale = "ja-JP", flag = "🇯🇵", region = "Japanese", pitch = 1f, rate = 1f, timbre = 1f,
            vibe = "Supertonic 3 · shared pack", bio = "Japanese neural voice. Shared pack with Hindi and French. OpenRAIL-M use restrictions apply.", engine = VoiceEngine.NEURAL, accent = 0xFFF5C518.toInt()),
        VoiceProfile(id = "fr_super", name = "French", gender = VoiceGender.FEMALE,
            locale = "fr-FR", flag = "🇫🇷", region = "French", pitch = 1f, rate = 1f, timbre = 1f,
            vibe = "Supertonic 3 · shared pack", bio = "French neural voice. Shared pack with Hindi and Japanese. OpenRAIL-M use restrictions apply.", engine = VoiceEngine.NEURAL, accent = 0xFFF5C518.toInt()),
        VoiceProfile(id = "zh_kokoro", name = "Mandarin", gender = VoiceGender.FEMALE,
            locale = "zh-CN", flag = "🇨🇳", region = "Mandarin Chinese", pitch = 1f, rate = 1f, timbre = 1f,
            vibe = "Kokoro 1.1 · full precision", bio = "Mandarin Chinese neural voice. Apache 2.0 model; a separate large download is required.", engine = VoiceEngine.NEURAL, accent = 0xFFF5C518.toInt()),
        VoiceProfile(
            id = "padmavathi", name = "Padmavathi", gender = VoiceGender.FEMALE,
            locale = "te-IN", flag = "🇮🇳", region = "Telugu (India)",
            pitch = 1f, rate = 1f, timbre = 1f,
            vibe = "Telugu · neural",
            bio = "Piper voice using AI4Bharat IndicVoices-R (CC BY 4.0). Attribution required; confirm model licensing and training lineage before commercial use.",
            engine = VoiceEngine.NEURAL, accent = 0xFFF5C518.toInt()
        )
    )

    fun byId(id: String?): VoiceProfile = all.firstOrNull { it.id == id } ?: all[0]

    val femaleCount: Int get() = all.count { it.gender == VoiceGender.FEMALE }
    val maleCount: Int get() = all.count { it.gender == VoiceGender.MALE }
    val botCount: Int get() = all.count { it.gender == VoiceGender.NONHUMAN }
}

/** Emotion presets applied on top of any voice. */
data class StylePreset(
    val id: String,
    val label: String,
    val emoji: String,
    val pitchFactor: Float,
    val rateFactor: Float,
    val blurb: String
)

object StyleCatalog {
    val all = listOf(
        StylePreset("natural", "Natural", "\uD83C\uDF99", 1.00f, 1.00f, "Exactly how the voice was born."),
        StylePreset("gossip", "Gossip", "\uD83D\uDC40", 1.05f, 1.08f, "Faster, higher, absolutely not your business."),
        StylePreset("cheerful", "Cheerful", "\uD83D\uDE04", 1.10f, 1.05f, "Sunshine with a side of receipts."),
        StylePreset("dramatic", "Dramatic", "\uD83C\uDFAD", 0.94f, 0.92f, "Dun dun duuun. Slow and cinematic."),
        StylePreset("whisper", "Whisper", "\uD83E\uDD2B", 0.92f, 0.90f, "Quiet, close, extremely illegal to overhear."),
        StylePreset("news", "News", "\uD83D\uDCF0", 0.98f, 1.06f, "Breaking: your friend group has no chill."),
        StylePreset("roast", "Roast", "\uD83D\uDD25", 1.08f, 1.12f, "Fast, sharp, and oddly specific."),
        StylePreset("robo", "Robo", "\uD83E\uDD16", 0.86f, 0.96f, "Maximum machine. Minimum empathy.")
    )

    fun byId(id: String?): StylePreset = all.firstOrNull { it.id == id } ?: all[0]
}

/** Text helpers: counting, estimating, sanitising and generating chaos. */
/**
 * Gender-aware synthesis tuning, shared by the app and by the CI demo renderer so a
 * published sample cannot disagree with what the phone actually plays.
 *
 * A female voice is not a male voice with a bigger number: F0 moves by ~1.8x and the
 * vocal tract is rescaled, which is why this lives in one place instead of being
 * copy-pasted into two call sites.
 */
object VoiceTuning {

    fun baseF0(voice: VoiceProfile): Double {
        val base = when (voice.gender) {
            VoiceGender.FEMALE -> 170.0
            VoiceGender.MALE -> 120.0
            VoiceGender.NONHUMAN -> 140.0
        }
        return (base * voice.pitch).coerceIn(70.0, 330.0)
    }

    fun formantScale(voice: VoiceProfile): Double {
        val pitch = voice.pitch.toDouble()
        val scale = when (voice.gender) {
            VoiceGender.FEMALE -> 1.10 + (pitch - 1.0) * 1.20
            VoiceGender.MALE -> 0.92 + (pitch - 0.86) * 0.90
            VoiceGender.NONHUMAN -> 1.0
        }
        return scale.coerceIn(0.85, 1.35)
    }
}

object TextKit {

    fun sanitize(input: CharSequence?): String {
        if (input == null) return ""
        var s = input.toString()
        s = s.replace("\r\n", "\n").replace('\r', '\n')
        s = s.replace(Regex("[ \\t]+"), " ")
        s = s.replace(Regex("\n{3,}"), "\n\n")
        return s.trim()
    }

    fun wordCount(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    /** ~165 spoken words per minute at 1.0x, tuned against device TTS. */
    fun estimateDurationMs(text: String, rate: Float = 1.0f): Long {
        val words = wordCount(text)
        if (words == 0) return 0
        val wpm = 165.0 * rate.coerceIn(0.5f, 2.0f)
        return (words / wpm * 60_000.0).toLong() + 350
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = (ms + 500) / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%d:%02d", m, s)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }

    fun safeFileName(raw: String, fallback: String = "gossip"): String {
        val base = raw.trim().lowercase()
            .replace(Regex("[^a-z0-9\\u0900-\\u097F]+"), "-")
            .trim('-')
        val clipped = if (base.length > 42) base.substring(0, 42).trim('-') else base
        return if (clipped.isBlank()) fallback else clipped
    }

    fun titleFrom(text: String): String {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
        val cleaned = firstLine.replace(Regex("\\s+"), " ")
        if (cleaned.isBlank()) return "Untitled gossip"
        return if (cleaned.length <= 44) cleaned else cleaned.substring(0, 41).trimEnd() + "\u2026"
    }

    /** Human-friendly summary used by the "engine" cards. */
    fun engineLabel(engineName: String): String = when (engineName) {
        "neural" -> "Piper / sherpa-onnx"
        "system" -> "System TTS (legacy clip)"
        "nano" -> "NanoVoice DSP"
        else -> engineName
    }

    val gossipSeeds: List<String> = listOf(
        "Okay so nobody asked but I have to say it: someone in this group chat has been screenshotting everything.",
        "Breaking news: the person who says 'I'm not dramatic' is in fact the most dramatic person in the building.",
        "She said she was five minutes away. That was forty minutes ago. The five minutes were emotional.",
        "He replied 'haha' and nothing else. The group chat has opened an official investigation.",
        "Reminder that the WiFi password is not a personality, but somehow it is his whole identity.",
        "They are not fighting, they are 'having a conversation' at volume eleven.",
        "The audacity arrived before the coffee did this morning. It is going to be one of those days."
    )

    fun randomGossip(): String = gossipSeeds[(Math.random() * gossipSeeds.size).toInt().coerceIn(0, gossipSeeds.size - 1)]
}
