package xyz.beyoneer.gossipaudio.playback

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import xyz.beyoneer.gossipaudio.audio.PlaybackHub
import xyz.beyoneer.gossipaudio.notify.Notifier

/**
 * Foreground playback service: keeps audio alive when the screen is off and
 * exposes a MediaSession so headsets, watches and car stereos can control it.
 */
class PlaybackService : Service() {

    private var session: MediaSession? = null
    private val listener: (PlaybackHub.State) -> Unit = { state -> render(state) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        session = MediaSession(this, "GossipAudio").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = PlaybackHub.resume()
                override fun onPause() = PlaybackHub.pause()
                override fun onStop() = PlaybackHub.stop()
                override fun onSkipToNext() = PlaybackHub.next()
                override fun onSkipToPrevious() = PlaybackHub.previous()
                override fun onSeekTo(pos: Long) = PlaybackHub.seekTo(pos)
            })
            isActive = true
        }
        PlaybackHub.addListener(listener)
        try {
            startForeground(Notifier.ID_PLAYBACK, buildNotification(PlaybackHub.state))
        } catch (_: Throwable) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> PlaybackHub.toggle()
            ACTION_STOP -> PlaybackHub.stop()
            ACTION_NEXT -> PlaybackHub.next()
            ACTION_PREV -> PlaybackHub.previous()
            else -> Unit
        }
        render(PlaybackHub.state)
        return START_STICKY
    }

    private fun render(state: PlaybackHub.State) {
        try {
            val notification = buildNotification(state)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(Notifier.ID_PLAYBACK, notification)
            if (!state.playing) {
                stopForeground(false)
            }
        } catch (_: Throwable) {
        }
        try {
            session?.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, state.title.ifBlank { "GossipAudio" })
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Beyoneer XYZ")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
                    .build()
            )
            val playbackState = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                    if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    state.positionMs,
                    if (state.playing) state.speed else 1f
                )
                .build()
            session?.setPlaybackState(playbackState)
        } catch (_: Throwable) {
        }
        if (!state.playing && state.clipId == null) {
            stopSelf()
        }
    }

    private fun buildNotification(state: PlaybackHub.State): Notification =
        Notifier.playback(this, state, session?.sessionToken)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        PlaybackHub.removeListener(listener)
        try {
            session?.isActive = false
            session?.release()
        } catch (_: Throwable) {
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE = "xyz.beyoneer.gossipaudio.TOGGLE"
        const val ACTION_STOP = "xyz.beyoneer.gossipaudio.STOP"
        const val ACTION_NEXT = "xyz.beyoneer.gossipaudio.NEXT"
        const val ACTION_PREV = "xyz.beyoneer.gossipaudio.PREV"

        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, PlaybackService::class.java))
            } catch (_: Throwable) {
            }
        }

        @Suppress("unused")
        private fun pi(context: Context, action: String): PendingIntent = PendingIntent.getService(
            context, action.hashCode(),
            Intent(context, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
