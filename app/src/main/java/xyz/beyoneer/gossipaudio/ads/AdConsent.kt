package xyz.beyoneer.gossipaudio.ads

import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import xyz.beyoneer.gossipaudio.BuildConfig
import xyz.beyoneer.gossipaudio.ui.MainActivity

/** UMP is checked on each activity launch; no ad SDK initialization precedes consent. */
class AdConsent(private val activity: MainActivity) {
    private val info by lazy { UserMessagingPlatform.getConsentInformation(activity) }
    private var requested = false
    private var initializing = false
    var ready = false
        private set
    var onChanged: (() -> Unit)? = null

    val privacyOptionsRequired: Boolean
        get() = BuildConfig.ADS_ENABLED && info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun start() {
        if (!BuildConfig.ADS_ENABLED || requested) return
        requested = true
        info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            if (activity.isFinishing || activity.isDestroyed) return@requestConsentInfoUpdate
            onChanged?.invoke()
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (error != null) Log.w("GossipAds", "Consent form: ${error.message}")
                afterConsent()
            }
        }, { error ->
            Log.w("GossipAds", "Consent update: ${error.message}")
            // UMP may retain usable consent from an earlier session. Unknown consent
            // never allows a request; a fresh install offline simply shows no ad.
            afterConsent()
        })
    }

    private fun afterConsent() {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!info.canRequestAds()) {
            ready = false
            onChanged?.invoke()
            return
        }
        if (ready || initializing) { onChanged?.invoke(); return }
        initializing = true
        MobileAds.initialize(activity.applicationContext) {
            activity.runOnUiThread {
                initializing = false
                if (!activity.isFinishing && !activity.isDestroyed) {
                    ready = info.canRequestAds()
                    onChanged?.invoke()
                }
            }
        }
    }

    fun showPrivacyOptions() {
        if (!privacyOptionsRequired) return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w("GossipAds", "Privacy options: ${error.message}")
            ready = false
            onChanged?.invoke() // discard any previously loaded ad before refreshing
            afterConsent()
        }
    }
}
