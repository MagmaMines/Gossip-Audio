package xyz.beyoneer.gossipaudio.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import xyz.beyoneer.gossipaudio.R
import xyz.beyoneer.gossipaudio.audio.PlaybackHub
import xyz.beyoneer.gossipaudio.core.TextKit
import xyz.beyoneer.gossipaudio.ui.MainActivity

object Notifier {

    const val CHANNEL_PLAYBACK = "gossip_playback"
    const val CHANNEL_EXPORT = "gossip_exports"
    const val CHANNEL_REMINDER = "gossip_reminders"

    const val ID_PLAYBACK = 9001
    const val ID_EXPORT = 9002
    const val ID_REMINDER = 9003

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val playback = NotificationChannel(
            CHANNEL_PLAYBACK,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Now playing controls for your gossip"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val exports = NotificationChannel(
            CHANNEL_EXPORT,
            "Exports & downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "When your audio file is ready to download" }
        val reminders = NotificationChannel(
            CHANNEL_REMINDER,
            "Daily gossip",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "A gentle nudge to spill some tea" }
        nm.createNotificationChannel(playback)
        nm.createNotificationChannel(exports)
        nm.createNotificationChannel(reminders)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun playback(context: Context, state: PlaybackHub.State, sessionToken: android.media.session.MediaSession.Token?): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_PLAYBACK)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (state.title.isBlank()) "GossipAudio" else state.title)
            .setContentText(if (state.playing) "Playing gossip\u2026" else "Paused \u00B7 tap to continue")
            .setContentIntent(contentIntent(context))
            .setOngoing(state.playing)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)

        val toggleIcon = if (state.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val toggleLabel = if (state.playing) "Pause" else "Play"
        val toggleIntent = PendingIntent.getService(
            context, 11,
            Intent(context, xyz.beyoneer.gossipaudio.playback.PlaybackService::class.java)
                .setAction(xyz.beyoneer.gossipaudio.playback.PlaybackService.ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            context, 12,
            Intent(context, xyz.beyoneer.gossipaudio.playback.PlaybackService::class.java)
                .setAction(xyz.beyoneer.gossipaudio.playback.PlaybackService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(Notification.Action.Builder(toggleIcon, toggleLabel, toggleIntent).build())
        builder.addAction(
            Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent).build()
        )
        if (sessionToken != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.style = Notification.MediaStyle()
                .setMediaSession(sessionToken)
                .setShowActionsInCompactView(0)
        }
        return builder.build()
    }

    fun exported(context: Context, fileName: String, uri: String?) {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_EXPORT)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Your gossip file is ready \uD83C\uDFA7")
            .setContentText("$fileName saved to Downloads/GossipAudio")
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
        if (uri != null) {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val sharePi = PendingIntent.getActivity(
                context, 13, Intent.createChooser(share, "Share gossip"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(Notification.Action.Builder(null, "Share", sharePi).build())
        }
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID_EXPORT, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun reminder(context: Context, text: String) {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_REMINDER)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Gossip break \uD83D\uDC40")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text + "\n\nTap to open Gossip Studio."))
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID_REMINDER, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelAll(context: Context) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
        } catch (_: Throwable) {
        }
    }

    fun randomNudge(): String = TextKit.gossipSeeds.random()
}
