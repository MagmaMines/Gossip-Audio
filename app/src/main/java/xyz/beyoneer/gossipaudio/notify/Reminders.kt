package xyz.beyoneer.gossipaudio.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Daily "go gossip" nudge — inexact, battery friendly, no special permissions. */
object ReminderScheduler {

    private const val REQUEST = 4401

    fun schedule(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                next.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pi
            )
        } catch (_: Throwable) {
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.cancel(pi)
        } catch (_: Throwable) {
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? xyz.beyoneer.gossipaudio.GossipApp ?: return
        if (!app.prefs.dailyReminder || !app.prefs.notifications) return
        Notifier.reminder(context, app.notifierNudge())
    }

    companion object {
        const val ACTION_REMIND = "xyz.beyoneer.gossipaudio.REMIND"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? xyz.beyoneer.gossipaudio.GossipApp ?: return
        if (app.prefs.dailyReminder) {
            ReminderScheduler.schedule(context, app.prefs.reminderHour, app.prefs.reminderMinute)
        }
    }
}
