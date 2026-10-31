package xyz.beyoneer.gossipaudio.core

enum class VoiceGender { FEMALE, MALE, NONHUMAN }

enum class VoiceEngine {
    /** On-device neural TTS engine shipped with the phone (Google TTS, Samsung, etc.). */
    SYSTEM_TTS,

    /** GossipAudio's own zero-dependency formant engine ("NanoVoice"), always available. */
    NANO_VOICE
}

/**
 * A selectable GossipAudio voice. Ten of them: four girls, five boys, one that
 * is politely described as "not human".
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
    val genderLabel: String
        get() = when (gender) {
            VoiceGender.FEMALE -> "Female"
            VoiceGender.MALE -> "Male"
            VoiceGender.NONHUMAN -> "Not human"
        }
}

object VoiceCatalog {

    // brand palette reused by cards so each voice owns a colour accent
    private const val YELLOW = 0xFFF5C518.toInt()
    private const val AMBER = 0xFFFFB300.toInt()
    private const val LEMON = 0xFFFFE066.toInt()
    private const val GOLD = 0xFFE0A800.toInt()
    private const val SAFFRON = 0xFFFF9F1C.toInt()
    private const val BUTTER = 0xFFFFD54F.toInt()
    private const val HONEY = 0xFFD9A400.toInt()
    private const val SUN = 0xFFFFC82E.toInt()
    private const val SAND = 0xFFC9A227.toInt()
    private const val NEON = 0xFFB9FF3C.toInt()

    val all: List<VoiceProfile> = listOf(
        VoiceProfile(
            id = "riya",
            name = "Riya",
            gender = VoiceGender.FEMALE,
            locale = "en-IN",
            flag = "\uD83C\uDDEE\uD83C\uDDF3",
            region = "India",
            pitch = 1.12f,
            rate = 1.02f,
            timbre = 0.62f,
            vibe = "Hostel gossip, 2 a.m.",
            bio = "Warm Indian-English delivery with a gossipy lift at the end of every sentence. Tells you the truth, then tells everyone else too.",
            engine = VoiceEngine.SYSTEM_TTS,
            accent = YELLOW
        ),
        VoiceProfile(
            id = "ananya",
            name = "Ananya",
            gender = VoiceGender.FEMALE,
            locale = "hi-IN",
            flag = "\uD83C\uDDEE\uD83C\uDDF3",
            region = "India",
            pitch = 1.20f,
            rate = 0.98f,
            timbre = 0.70f,
            vibe = "Playful, chai in hand",
            bio = "Hindi-leaning cadence with bright vowels. Great for dramatic readings of your group chat.",
            engine = VoiceEngine.SYSTEM_TTS,
            accent = SAFFRON
        ),
        VoiceProfile(
            id = "maya",
            name = "Maya",
            gender = VoiceGender.FEMALE,
            locale = "en-US",
            flag = "\uD83C\uDDFA\uD83C\uDDF8",
            region = "USA",
            pitch = 1.16f,
            rate = 1.06f,
            timbre = 0.58f,
            vibe = "Bubbly podcast host",
            bio = "Bright Californian energy. Says 'literally' without being asked. Perfect for pop-culture breakdowns.",
            engine = VoiceEngine.SYSTEM_TTS,
            accent = LEMON
        ),
        VoiceProfile(
            id = "sakura",
            name = "Sakura",
            gender = VoiceGender.FEMALE,
            locale = "ja-JP",
            flag = "\uD83C\uDDEF\uD83C\uDDF5",
            region = "Japan",
            pitch = 1.24f,
            rate = 1.00f,
            timbre = 0.74f,
            vibe = "Cute anime neighbour",
            bio = "Japanese voice model with a soft high register. Yes, it will sound like an anime side character. No, we cannot fix that.",
            engine = VoiceEngine.SYSTEM_TTS,
            accent = BUTTER
        ),
        VoiceProfile(
            id = "arjun",
            name = "Arjun",
            gender = VoiceGender.MALE,
            locale = "en-IN",
            flag = "\uD83C\uDDEE\uD83C\uDDF3",
            region = "India",
            pitch = 0.86f,
            rate = 1.02f,
            timbre = 0.44f,
            vibe = "Prime-time news anchor",
            bio = "Crisp Indian-English newsreader. Will report your gossip with breaking-news gravity and zero mercy.",
            engine = VoiceEngine.SYSTEM_TTS,
            accent = AMBER
        ),
        VoiceProfile(
            id = "rohan",
            name = "Rohan",
            gender = VoiceGender.MALE,
            locale = "hi-IN",
            flag = "\uD83C\uDDEE\uD83C\uDDF3",
            region = "India",
            pitch = 0.82f,
            rate = 0.96f,
            timbre = 0.40f,
            vibe = "Movie-trailer drama",
            bio = "Deep Hindi-English storyteller. Every sentence sounds like the twist before the interval.",
            engine = VoiceEngine.SYSTEM_TTS,
            accent = GOLD
        ),
        VoiceProfile(
            id = "jordan",
            name = "Jordan",
            gender = VoiceGender.MALE,
            locale = "en-US",
            flag = "\uD83C\uDDFA\uD83C\uDDF8",
            region = "USA",
            pitch = 0.74f,
            rate = 0.95f,
            timbre = 0.34f,
            vibe = "Late-night radio",
            bio = "Low, slow American baritone. Designed for whispering scandal directly into your headphones.",
            engine = VoiceEngine.SYSTEM_TTS,
            accent = HONEY
        ),
        VoiceProfile(
            id = "oliver",
            name = "Oliver",
            gender = VoiceGender.MALE,
            locale = "en-GB",
            flag = "\uD83C\uDDEC\uD83C\uDDE7",
            region = "UK",
            pitch = 0.90f,
            rate = 1.00f,
            timbre = 0.50f,
            vibe = "Posh documentary",
            bio = "Received Pronunciation with a suspicious amount of confidence. Ideal for narrating other people's mistakes.",
            engine = VoiceEngine.SYSTEM_TTS,
            accent = SUN
        ),
        VoiceProfile(
            id = "kenji",
            name = "Kenji",
            gender = VoiceGender.MALE,
            locale = "ja-JP",
            flag = "\uD83C\uDDEF\uD83C\uDDF5",
            region = "Japan",
            pitch = 0.95f,
            rate = 0.98f,
            timbre = 0.46f,
            vibe = "Calm sensei",
            bio = "Measured Japanese delivery. Says the most unhinged thing you have ever typed in the calmest possible tone.",
            engine = VoiceEngine.SYSTEM_TTS,
            accent = SAND
        ),
        VoiceProfile(
            id = "bot",
            name = "Beyoneer Bot",
            gender = VoiceGender.NONHUMAN,
            locale = "en-US",
            flag = "\uD83E\uDD16",
            region = "The Internet",
            pitch = 1.00f,
            rate = 1.04f,
            timbre = 0.92f,
            vibe = "Definitely not a human",
            bio = "Runs on GossipAudio's own NanoVoice engine, so it works on literally any phone, forever. Zero samples were harmed.",
            engine = VoiceEngine.NANO_VOICE,
            accent = NEON
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
        "system" -> "On-device neural TTS"
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
