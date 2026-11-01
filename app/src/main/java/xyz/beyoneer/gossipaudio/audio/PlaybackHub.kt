package xyz.beyoneer.gossipaudio.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * One shared MediaPlayer for the whole app (UI + foreground service + media
 * notification). Everything is driven on the main thread; listeners get a new
 * immutable [State] snapshot on every meaningful change.
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

    private var player: MediaPlayer? = null
    private var appContext: Context? = null
    private val handlers = ArrayList<(State) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var queue: List<Item> = emptyList()
    private var index = 0
    private var equalizer: Equalizer? = null
    private var reverb: PresetReverb? = null
    private var sleepStopAt: Long = 0L
    private var speed = 1f
    private var prepared = false
    private var effectsEnabled = true

    var state = State()
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun setEffects(enabled: Boolean) {
        effectsEnabled = enabled
        if (!enabled) releaseEffects()
        else player?.let { attachEffects(it) }
    }

    fun addListener(listener: (State) -> Unit) {
        handlers.add(listener)
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

    private fun playItem(item: Item) {
        try {
            stopInternal(keepQueue = true)
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            p.setDataSource(item.file.absolutePath)
            p.setOnPreparedListener {
                prepared = true
                applySpeed(it)
                attachEffects(it)
                it.start()
                state = state.copy(
                    clipId = item.clipId,
                    title = item.title,
                    playing = true,
                    prepared = true,
                    durationMs = it.duration.toLong(),
                    positionMs = 0,
                    coverPath = item.cover?.absolutePath,
                    queueSize = queue.size,
                    queueIndex = index,
                    buffering = false
                )
                emit()
                startTicker()
                startService()
            }
            p.setOnCompletionListener {
                prepared = false
                if (index + 1 < queue.size) {
                    index += 1
                    playItem(queue[index])
                } else {
                    state = state.copy(playing = false, prepared = false, positionMs = 0)
                    emit()
                    stopServiceIfIdle()
                }
            }
            p.setOnErrorListener { _, what, extra ->
                state = state.copy(playing = false, prepared = false, buffering = false)
                emit()
                false
            }
            p.setOnBufferingUpdateListener { _, _ -> }
            p.prepareAsync()
            player = p
            speed = speed.coerceIn(0.5f, 2.0f)
            state = state.copy(
                clipId = item.clipId,
                title = item.title,
                playing = false,
                prepared = false,
                positionMs = 0,
                durationMs = 0,
                buffering = true,
                coverPath = item.cover?.absolutePath,
                queueSize = queue.size,
                queueIndex = index
            )
            emit()
        } catch (t: Throwable) {
            state = state.copy(playing = false, buffering = false)
            emit()
        }
    }

    fun toggle() {
        val p = player
        if (p == null) {
            if (queue.isNotEmpty()) playItem(queue[index])
            return
        }
        try {
            if (p.isPlaying) p.pause() else p.start()
            state = state.copy(playing = p.isPlaying)
            emit()
            if (p.isPlaying) startTicker() else stopTicker()
            if (p.isPlaying) startService() else stopServiceIfIdle()
        } catch (_: Throwable) {
        }
    }

    fun pause() {
        try {
            player?.takeIf { it.isPlaying }?.pause()
            state = state.copy(playing = false)
            emit()
            stopServiceIfIdle()
        } catch (_: Throwable) {
        }
    }

    fun resume() {
        try {
            if (player == null) {
                if (queue.isNotEmpty()) playItem(queue[index])
                return
            }
            player?.start()
            state = state.copy(playing = true)
            emit()
            startTicker()
            startService()
        } catch (_: Throwable) {
        }
    }

    fun stop() {
        stopInternal(keepQueue = false)
        state = state.copy(playing = false, prepared = false, positionMs = 0, clipId = null, title = "")
        emit()
        stopTicker()
        stopServiceIfIdle()
    }

    fun replay() {
        try {
            player?.let {
                it.seekTo(0)
                it.start()
                state = state.copy(playing = true, positionMs = 0)
                emit()
                startTicker()
                startService()
            }
        } catch (_: Throwable) {
        }
    }

    fun seekTo(ms: Long) {
        try {
            player?.seekTo(ms.toInt())
            state = state.copy(positionMs = ms)
            emit()
        } catch (_: Throwable) {
        }
    }

    fun seekBy(deltaMs: Long) {
        try {
            val target = (state.positionMs + deltaMs).coerceIn(0, state.durationMs)
            seekTo(target)
        } catch (_: Throwable) {
        }
    }

    fun setSpeed(newSpeed: Float) {
        speed = newSpeed.coerceIn(0.5f, 2.0f)
        player?.let { applySpeed(it) }
        state = state.copy(speed = speed)
        emit()
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

    private fun applySpeed(p: MediaPlayer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                p.playbackParams = p.playbackParams.setSpeed(speed)
            } catch (_: Throwable) {
                try {
                    p.playbackParams = PlaybackParams().setSpeed(speed)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun attachEffects(p: MediaPlayer) {
        if (!effectsEnabled) return
        try {
            releaseEffects()
            val session = p.audioSessionId
            // Voice clarity, not "make it thump". An Equalizer is neutral until you
            // set band levels, but BassBoost(320) is not: on most devices that is
            // +8..+12 dB below 200 Hz on a signal that is already peak-normalised,
            // which is exactly the distorted, boomy playback people complained about.
            // So: cut the mud, leave the mids alone, tiny lift on consonant presence.
            equalizer = Equalizer(0, session).apply {
                val limits = bandLevelRange
                val low = limits[0].toInt()
                val high = limits[1].toInt()
                for (band in 0 until numberOfBands.toInt()) {
                    val centreHz = getCenterFreq(band.toShort()) / 1000.0
                    val level = when {
                        centreHz < 400.0 -> -1200
                        centreHz < 2000.0 -> 0
                        else -> 250
                    }.coerceIn(low, high)
                    try {
                        setBandLevel(band.toShort(), level.toShort())
                    } catch (_: Throwable) {
                    }
                }
                enabled = true
            }
            reverb = PresetReverb(0, session).apply {
                preset = PresetReverb.PRESET_SMALLROOM
                enabled = false
            }
        } catch (_: Throwable) {
            releaseEffects()
        }
    }

    fun setReverb(on: Boolean) {
        try {
            reverb?.enabled = on
        } catch (_: Throwable) {
        }
    }

    private fun releaseEffects() {
        try {
            equalizer?.release()
        } catch (_: Throwable) {
        }
        try {
            reverb?.release()
        } catch (_: Throwable) {
        }
        equalizer = null
        reverb = null
    }

    private fun stopInternal(keepQueue: Boolean) {
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (_: Throwable) {
        }
        player = null
        prepared = false
        releaseEffects()
        if (!keepQueue) {
            queue = emptyList()
            index = 0
        }
    }

    private var ticker: Runnable? = null

    private fun startTicker() {
        stopTicker()
        val runnable = object : Runnable {
            override fun run() {
                val p = player ?: return
                try {
                    if (p.isPlaying) {
                        state = state.copy(positionMs = p.currentPosition.toLong(), durationMs = p.duration.toLong())
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
        stop()
        handlers.clear()
    }

    @Suppress("unused")
    private fun audioManager(): AudioManager? =
        appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
}
