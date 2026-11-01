package xyz.beyoneer.gossipaudio.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * Playback, rebuilt on Media3 ExoPlayer (Apache-2.0).
 *
 * What went wrong with the old one: it was a raw [android.media.MediaPlayer] with an
 * `android.media.audiofx.BassBoost` bolted on at strength 320/1000 and a
 * `PresetReverb`. BassBoost on an already peak-normalised speech file is +8…+12 dB
 * below 200 Hz — that is the booming, crackling "distorted" sound. MediaPlayer also
 * recreated the whole effect chain per clip and leaked sessions.
 *
 * ExoPlayer gives us the things a media app is supposed to have:
 *
 *  - **Sonic** time-stretching: speed 0.5×–2× changes tempo while the pitch stays put,
 *    so 1.5× still sounds like a person and not a chipmunk;
 *  - **true audio focus**: `setAudioAttributes(..., handleAudioFocus = true)` pauses for
 *    calls and ducks under notifications instead of talking over them;
 *  - **gapless queueing** and reliable `seekTo`;
 *  - no DSP toys in the signal path. The audio that leaves the engine is the audio you
 *    hear — nothing is boosted, cut or reverbed behind your back.
 *
 * Every call is defensive: a broken file, a missing decoder or a device that refuses the
 * codec must never take the app down.
 */
object PlaybackHub {

    data class Item(val clipId: String, val title: String, val file: File, val cover: File?)

    data class State(
        val clipId: String? = null,
        val title: String = "",
        val playing: Boolean = false,
        val prepared: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val speed: Float = 1f,
        val coverPath: String? = null,
        val queueSize: Int = 0,
        val queueIndex: Int = 0,
        val buffering: Boolean = false
    )

    private var player: ExoPlayer? = null
    private var appContext: Context? = null
    private val handlers = ArrayList<(State) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var queue: List<Item> = emptyList()
    private var index = 0
    private var sleepStopAt: Long = 0L
    private var speed = 1f
    /** Kept for API compatibility with callers; there is no EQ any more. */
    private var effectsEnabled = false

    var state = State()
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Historically this attached BassBoost + a preset reverb. Both are gone: the only
     * honest "polish" is not damaging the file. The flag is remembered so old prefs
     * still round-trip, but nothing in the signal path changes.
     */
    fun setEffects(enabled: Boolean) {
        effectsEnabled = enabled
    }

    fun addListener(listener: (State) -> Unit) {
        if (!handlers.contains(listener)) handlers.add(listener)
        listener(state)
    }

    fun removeListener(listener: (State) -> Unit) {
        handlers.remove(listener)
    }

    private fun emit() {
        val snapshot = state
        main.post {
            handlers.toList().forEach {
                try {
                    it(snapshot)
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun playQueue(items: List<Item>, startIndex: Int) {
        if (items.isEmpty()) return
        queue = items
        index = startIndex.coerceIn(0, items.size - 1)
        playItem(queue[index])
    }

    fun playFile(file: File, clipId: String, title: String, cover: File?) {
        queue = listOf(Item(clipId, title, file, cover))
        index = 0
        playItem(queue[0])
    }

    // ------------------------------------------------------------- player setup

    private fun engine(): ExoPlayer? {
        player?.let { return it }
        val ctx = appContext ?: return null
        return try {
            val p = ExoPlayer.Builder(ctx).build()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            p.repeatMode = Player.REPEAT_MODE_OFF
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            state = state.copy(
                                prepared = true,
                                buffering = false,
                                durationMs = p.duration.coerceAtLeast(0L)
                            )
                            emit()
                        }

                        Player.STATE_BUFFERING -> {
                            state = state.copy(buffering = true)
                            emit()
                        }

                        Player.STATE_ENDED -> {
                            if (index + 1 < queue.size) {
                                index += 1
                                playItem(queue[index])
                            } else {
                                state = state.copy(playing = false, prepared = false, positionMs = 0)
                                emit()
                                stopTicker()
                                stopServiceIfIdle()
                            }
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    state = state.copy(playing = isPlaying)
                    emit()
                    if (isPlaying) startTicker() else stopTicker()
                    if (isPlaying) startService() else stopServiceIfIdle()
                }

                override fun onPlayerError(error: PlaybackException) {
                    state = state.copy(playing = false, prepared = false, buffering = false)
                    emit()
                    stopTicker()
                }
            })
            player = p
            p
        } catch (_: Throwable) {
            null
        }
    }

    private fun playItem(item: Item) {
        val itemState = State(
            clipId = item.clipId,
            title = item.title,
            playing = false,
            prepared = false,
            positionMs = 0,
            durationMs = state.durationMs.takeIf { state.clipId == item.clipId } ?: 0L,
            speed = speed,
            coverPath = item.cover?.absolutePath,
            queueSize = queue.size,
            queueIndex = index,
            buffering = true
        )
        state = itemState
        emit()

        val p = engine()
        if (p == null || !item.file.exists()) {
            // No decoder, no engine, or the file vanished: report it instead of pretending
            // to play silence.
            state = itemState.copy(playing = false, buffering = false)
            emit()
            return
        }
        try {
            p.setMediaItem(MediaItem.fromUri(Uri.fromFile(item.file)))
            p.setPlaybackParameters(PlaybackParameters(speed, 1f))
            p.prepare()
            p.play()
            startService()
        } catch (_: Throwable) {
            state = state.copy(playing = false, buffering = false)
            emit()
        }
    }

    // ------------------------------------------------------------------ controls

    fun toggle() {
        val p = player
        if (p == null || queue.isEmpty()) {
            if (queue.isNotEmpty()) playItem(queue[index])
            return
        }
        try {
            if (p.isPlaying) p.pause() else p.play()
        } catch (_: Throwable) {
        }
    }

    fun pause() {
        try {
            player?.pause()
        } catch (_: Throwable) {
        }
        state = state.copy(playing = false)
        emit()
        stopServiceIfIdle()
    }

    fun resume() {
        val p = player
        if (p == null) {
            if (queue.isNotEmpty()) playItem(queue[index])
            return
        }
        try {
            p.play()
        } catch (_: Throwable) {
        }
        startService()
    }

    fun stop() {
        try {
            player?.stop()
            player?.clearMediaItems()
        } catch (_: Throwable) {
        }
        queue = emptyList()
        index = 0
        state = State()
        emit()
        stopTicker()
        stopServiceIfIdle()
    }

    fun replay() {
        val p = player ?: return
        try {
            p.seekTo(0)
            p.play()
        } catch (_: Throwable) {
        }
        startService()
    }

    fun seekTo(ms: Long) {
        try {
            player?.seekTo(ms.coerceAtLeast(0))
        } catch (_: Throwable) {
        }
        state = state.copy(positionMs = ms.coerceAtLeast(0))
        emit()
    }

    fun seekBy(deltaMs: Long) {
        val target = (state.positionMs + deltaMs).coerceIn(0, state.durationMs)
        seekTo(target)
    }

    /**
     * Speed without the chipmunk: Sonic preserves pitch, so 0.5× is a slow narrator and
     * 2× is a fast one — both still the same human.
     */
    fun setSpeed(newSpeed: Float) {
        speed = newSpeed.coerceIn(0.5f, 2.0f)
        try {
            player?.setPlaybackParameters(PlaybackParameters(speed, 1f))
        } catch (_: Throwable) {
        }
        state = state.copy(speed = speed)
        emit()
    }

    /** Independent pitch shift (semitone-ish), 0.5×–2×, on top of the speed. */
    fun setPitch(pitch: Float) {
        try {
            player?.setPlaybackParameters(PlaybackParameters(speed, pitch.coerceIn(0.5f, 2.0f)))
        } catch (_: Throwable) {
        }
    }

    fun next() {
        if (index + 1 < queue.size) {
            index += 1
            playItem(queue[index])
        }
    }

    fun previous() {
        if (index - 1 >= 0) {
            index -= 1
            playItem(queue[index])
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepStopAt = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
    }

    fun sleepRemainingMs(): Long {
        if (sleepStopAt == 0L) return 0
        return (sleepStopAt - System.currentTimeMillis()).coerceAtLeast(0)
    }

    // -------------------------------------------------------------------- ticker

    private var ticker: Runnable? = null

    private fun startTicker() {
        stopTicker()
        val runnable = object : Runnable {
            override fun run() {
                val p = player ?: return
                try {
                    if (p.isPlaying) {
                        state = state.copy(
                            positionMs = p.currentPosition.coerceAtLeast(0L),
                            durationMs = p.duration.coerceAtLeast(0L)
                        )
                        if (sleepStopAt > 0 && System.currentTimeMillis() >= sleepStopAt) {
                            sleepStopAt = 0
                            pause()
                            return
                        }
                        emit()
                        main.postDelayed(this, 120)
                    }
                } catch (_: Throwable) {
                }
            }
        }
        ticker = runnable
        main.postDelayed(runnable, 120)
    }

    private fun stopTicker() {
        ticker?.let { main.removeCallbacks(it) }
        ticker = null
    }

    // ------------------------------------------------------------------- service

    private fun startService() {
        val ctx = appContext ?: return
        try {
            xyz.beyoneer.gossipaudio.playback.PlaybackService.start(ctx)
        } catch (_: Throwable) {
        }
    }

    private fun stopServiceIfIdle() {
        if (state.playing) return
        val ctx = appContext ?: return
        try {
            xyz.beyoneer.gossipaudio.playback.PlaybackService.stop(ctx)
        } catch (_: Throwable) {
        }
    }

    fun isPlaying(): Boolean = state.playing

    fun release() {
        stopTicker()
        try {
            player?.release()
        } catch (_: Throwable) {
        }
        player = null
        queue = emptyList()
        state = State()
        handlers.clear()
    }
}
