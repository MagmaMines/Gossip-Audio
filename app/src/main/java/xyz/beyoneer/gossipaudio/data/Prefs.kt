package xyz.beyoneer.gossipaudio.data

import android.content.Context

/** Tiny typed wrapper around SharedPreferences — no dependencies, always instant. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("gossipaudio_prefs", Context.MODE_PRIVATE)

    var themeMode: String
        get() = sp.getString("theme_mode", "dark") ?: "dark"
        set(value) = sp.edit().putString("theme_mode", value).apply()

    var defaultFormat: String
        get() = sp.getString("default_format", "mp3") ?: "mp3"
        set(value) = sp.edit().putString("default_format", value).apply()

    var defaultBitrate: Int
        get() = sp.getInt("default_bitrate", 128)
        set(value) = sp.edit().putInt("default_bitrate", value).apply()

    var selectedVoiceId: String
        get() = sp.getString("selected_voice", "riya") ?: "riya"
        set(value) = sp.edit().putString("selected_voice", value).apply()

    var selectedStyleId: String
        get() = sp.getString("selected_style", "natural") ?: "natural"
        set(value) = sp.edit().putString("selected_style", value).apply()

    var pitch: Float
        get() = sp.getFloat("pitch", 1.0f)
        set(value) = sp.edit().putFloat("pitch", value).apply()

    var speed: Float
        get() = sp.getFloat("speed", 1.0f)
        set(value) = sp.edit().putFloat("speed", value).apply()

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(value) = sp.edit().putBoolean("haptics", value).apply()

    var reduceMotion: Boolean
        get() = sp.getBoolean("reduce_motion", false)
        set(value) = sp.edit().putBoolean("reduce_motion", value).apply()

    var autoPlay: Boolean
        get() = sp.getBoolean("auto_play", true)
        set(value) = sp.edit().putBoolean("auto_play", value).apply()

    var notifications: Boolean
        get() = sp.getBoolean("notifications", true)
        set(value) = sp.edit().putBoolean("notifications", value).apply()

    var dailyReminder: Boolean
        get() = sp.getBoolean("daily_reminder", false)
        set(value) = sp.edit().putBoolean("daily_reminder", value).apply()

    var reminderHour: Int
        get() = sp.getInt("reminder_hour", 20)
        set(value) = sp.edit().putInt("reminder_hour", value).apply()

    var reminderMinute: Int
        get() = sp.getInt("reminder_minute", 0)
        set(value) = sp.edit().putInt("reminder_minute", value).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(value) = sp.edit().putBoolean("onboarding_done", value).apply()

    var preferOfflineStt: Boolean
        get() = sp.getBoolean("offline_stt", false)
        set(value) = sp.edit().putBoolean("offline_stt", value).apply()

    /**
     * One-shot repair for installs that stored the 1.0.4 defaults: a "Gossip" style
     * (+5 % pitch, +8 % rate) on top of a +12 % voice multiplier, and an equaliser
     * that cut 12 dB out of the body of a perfectly good neural voice.
     */
    fun migrateAudioDefaults() {
        if (sp.getInt("audio_defaults_version", 1) < 2) {
            sp.edit()
                .putString("selected_style", "natural")
                .putBoolean("effects_enabled", false)
                .putInt("audio_defaults_version", 2)
                .apply()
        }
    }

    var effectsEnabled: Boolean
        get() = sp.getBoolean("effects_enabled", false)
        set(value) = sp.edit().putBoolean("effects_enabled", value).apply()

    /** Bump every text size ~15 % — the cheapest accessibility win there is. */
    var bigText: Boolean
        get() = sp.getBoolean("big_text", false)
        set(value) = sp.edit().putBoolean("big_text", value).apply()

    /** Trim leading/trailing silence from every render (on by default). */
    var autoTrim: Boolean
        get() = sp.getBoolean("auto_trim", true)
        set(value) = sp.edit().putBoolean("auto_trim", value).apply()

    /** Peak-normalise every render to -1.5 dBFS (on by default). */
    var autoNormalize: Boolean
        get() = sp.getBoolean("auto_normalize", true)
        set(value) = sp.edit().putBoolean("auto_normalize", value).apply()

    /**
     * "Say it your way" — pairs of [written, spoken] separated by ';'.
     * Applied to the text before synthesis, so it works for NanoVoice and for the
     * phone's TTS engine alike.
     */
    var wordMap: String
        get() = sp.getString("word_map", "") ?: ""
        set(value) = sp.edit().putString("word_map", value).apply()

    var ringtoneHintSeen: Boolean
        get() = sp.getBoolean("ringtone_hint", false)
        set(value) = sp.edit().putBoolean("ringtone_hint", value).apply()

    var sleepTimerMinutes: Int
        get() = sp.getInt("sleep_timer", 0)
        set(value) = sp.edit().putInt("sleep_timer", value).apply()

    var totalGenerations: Int
        get() = sp.getInt("total_generations", 0)
        set(value) = sp.edit().putInt("total_generations", value).apply()

    var totalSpokenChars: Long
        get() = sp.getLong("total_chars", 0L)
        set(value) = sp.edit().putLong("total_chars", value).apply()

    var lastExportFolder: String
        get() = sp.getString("last_export_folder", "GossipAudio") ?: "GossipAudio"
        set(value) = sp.edit().putString("last_export_folder", value).apply()
}
