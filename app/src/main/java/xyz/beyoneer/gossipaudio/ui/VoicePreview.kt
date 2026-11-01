package xyz.beyoneer.gossipaudio.ui

import xyz.beyoneer.gossipaudio.audio.PlaybackHub
import xyz.beyoneer.gossipaudio.core.StyleCatalog
import xyz.beyoneer.gossipaudio.core.VoiceCatalog
import xyz.beyoneer.gossipaudio.core.VoiceProfile
import xyz.beyoneer.gossipaudio.speech.SpeechSynth
import xyz.beyoneer.gossipaudio.ui.theme.Palette
import xyz.beyoneer.gossipaudio.util.Artwork
import java.io.File

/**
 * Short cached demos so users can audition all ten voices without spamming the
 * synthesis engine. Previews are generated once, stored in cacheDir and reused.
 */
object VoicePreview {

    enum class State { IDLE, LOADING, PLAYING, ERROR }

    private val busy = HashSet<String>()

    private fun sampleFor(voice: VoiceProfile): String =
        if (voice.id == "padmavathi") "నమస్కారం! మీ మాటలకు స్వాగతం." else "Hello! This is ${voice.name}. Your words, their wings."

    fun preview(activity: MainActivity, voice: VoiceProfile, onState: (State) -> Unit) {
        val key = "preview-lessac-fp32-v2-${voice.id}-${activity.prefs.selectedStyleId}-${activity.prefs.pitch}-${activity.prefs.speed}"
        val cacheDir = File(activity.cacheDir, "previews").apply { mkdirs() }
        val file = File(cacheDir, "$key.wav")
        if (file.exists() && file.length() > 2048) {
            play(activity, voice, file, onState)
            return
        }
        if (busy.contains(key)) return
        busy.add(key)
        onState(State.LOADING)
        val synth = SpeechSynth(activity)
        val style = StyleCatalog.byId(activity.prefs.selectedStyleId)
        synth.synthesize(
            sampleFor(voice),
            voice,
            style,
            activity.prefs.pitch,
            activity.prefs.speed,
            object : SpeechSynth.Callback {
                override fun onProgress(fraction: Float, label: String) = Unit

                override fun onSuccess(outcome: xyz.beyoneer.gossipaudio.speech.SpeechSynth.Outcome) {
                    busy.remove(key)
                    try {
                        file.writeBytes(xyz.beyoneer.gossipaudio.core.Wav.write(outcome.pcm))
                    } catch (_: Throwable) {
                    }
                    play(activity, voice, file, onState)
                }

                override fun onError(message: String) {
                    busy.remove(key)
                    onState(State.ERROR)
                    activity.toast("Preview failed: $message")
                }
            }
        )
    }

    private fun play(activity: MainActivity, voice: VoiceProfile, file: File, onState: (State) -> Unit) {
        val coverDir = File(activity.cacheDir, "previews").apply { mkdirs() }
        val cover = File(coverDir, "cover-${voice.id}.jpg")
        if (!cover.exists()) {
            Artwork.generateBrandCover(activity, "${voice.name} \u00B7 sample", voice.name, voice.vibe, cover)
        }
        PlaybackHub.playFile(file, "preview-${voice.id}", "${voice.name} \u00B7 voice preview", cover)
        onState(State.PLAYING)
        if (activity.prefs.notifications) activity.requestNotifications()
    }

    fun isPlaying(voiceId: String): Boolean =
        PlaybackHub.state.clipId == "preview-$voiceId" && PlaybackHub.state.playing

    @Suppress("unused")
    private fun accent(voice: VoiceProfile): Int = voice.accent

    fun defaultVoice(): VoiceProfile = VoiceCatalog.all[0]

    @Suppress("unused")
    private fun paletteOf(activity: MainActivity): Palette = activity.palette
}
