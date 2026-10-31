package xyz.beyoneer.gossipaudio

import android.app.Application
import android.os.Build
import xyz.beyoneer.gossipaudio.audio.PlaybackHub
import xyz.beyoneer.gossipaudio.data.ClipStore
import xyz.beyoneer.gossipaudio.data.Prefs
import xyz.beyoneer.gossipaudio.notify.Notifier
import xyz.beyoneer.gossipaudio.notify.ReminderScheduler
import xyz.beyoneer.gossipaudio.core.TextKit

class GossipApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var store: ClipStore
        private set

    /** Text handed to us by another app ("Share to GossipAudio"), consumed by the composer. */
    var pendingText: String? = null

    /** Tab to open after a launcher shortcut or a share intent. */
    var pendingTab: Int? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        store = ClipStore(this)
        Notifier.createChannels(this)
        PlaybackHub.init(this)
        PlaybackHub.setEffects(prefs.effectsEnabled)
        if (prefs.dailyReminder) {
            ReminderScheduler.schedule(this, prefs.reminderHour, prefs.reminderMinute)
        }
        Thread {
            try {
                store.sweepOrphans()
            } catch (_: Throwable) {
            }
        }.start()
    }

    fun notifierNudge(): String {
        val pool = TextKit.gossipSeeds
        return pool[(Math.random() * pool.size).toInt().coerceIn(0, pool.size - 1)]
    }

    companion object {
        lateinit var instance: GossipApp
            private set

        fun safeApplication(): GossipApp? = if (::instance.isInitialized) instance else null
    }
}
