package xyz.beyoneer.gossipaudio.ads

import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import xyz.beyoneer.gossipaudio.BuildConfig
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.theme.Ui

/** Single anchored banner, separated from navigation/actions. Hidden while typing. */
class BannerSlot(private val activity: MainActivity) {
    val view = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = View.GONE
        setPadding(0, Ui.dp(activity, 8f), 0, Ui.dp(activity, 10f))
        contentDescription = "Advertisement"
    }
    private val holder = FrameLayout(activity)
    private var ad: AdView? = null
    private var loaded = false
    private var active = false
    private var eligible = false
    private var disposed = false
    private var creating = false
    private val focusListener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, _ -> updateVisibility() }
    private val retry = Runnable { if (!disposed && eligible && active && ad == null) create() }

    init {
        view.addView(Ui.text(activity, "ADVERTISEMENT", 9f, activity.palette.textDim, gravity = Gravity.CENTER))
        view.addView(holder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        activity.window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
    }

    fun update(enabled: Boolean, visibleScreen: Boolean) {
        eligible = enabled
        active = visibleScreen
        if (!eligible) clearAd()
        if (eligible && active && ad == null) create()
        if (active) ad?.resume() else ad?.pause()
        updateVisibility()
    }

    private fun create() {
        if (creating || disposed || !eligible || !active) return
        creating = true
        holder.post {
            creating = false
            if (disposed || !eligible || !active || ad != null) return@post
            val widthPixels = holder.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
            val widthDp = (widthPixels / activity.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            val banner = AdView(activity)
            banner.adUnitId = BuildConfig.ADMOB_BANNER_ID
            @Suppress("DEPRECATION")
            banner.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
            ad = banner
            banner.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    if (disposed || ad !== banner || !eligible) return
                    loaded = true
                    updateVisibility()
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.i("GossipAds", "Banner not available: ${error.message}")
                    if (ad !== banner) return
                    clearAd()
                    if (!disposed && eligible && active) view.postDelayed(retry, 60_000)
                }
            }
            holder.addView(banner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            banner.loadAd(AdRequest.Builder().build())
        }
    }

    private fun updateVisibility() {
        val typing = activity.currentFocus is EditText
        view.visibility = if (eligible && active && loaded && !typing) View.VISIBLE else View.GONE
    }

    private fun clearAd() {
        view.removeCallbacks(retry)
        loaded = false
        ad?.let { holder.removeView(it); it.destroy() }
        ad = null
        updateVisibility()
    }

    fun destroy() {
        disposed = true
        clearAd()
        val observer = activity.window.decorView.viewTreeObserver
        if (observer.isAlive) observer.removeOnGlobalFocusChangeListener(focusListener)
    }
}
