package xyz.beyoneer.gossipaudio.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import xyz.beyoneer.gossipaudio.GossipApp
import xyz.beyoneer.gossipaudio.R
import xyz.beyoneer.gossipaudio.data.Prefs
import xyz.beyoneer.gossipaudio.ui.screens.OnboardingScreen
import xyz.beyoneer.gossipaudio.ui.screens.SplashScreen
import xyz.beyoneer.gossipaudio.ui.theme.Palette
import xyz.beyoneer.gossipaudio.ui.theme.ThemeController
import xyz.beyoneer.gossipaudio.ui.theme.Ui

class MainActivity : Activity() {

    lateinit var palette: Palette
        private set
    lateinit var router: Router
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var app: GossipApp
        private set

    private var micCallback: ((Boolean) -> Unit)? = null
    private var notificationCallback: ((Boolean) -> Unit)? = null
    private var imageCallback: ((android.net.Uri?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (ThemeController.isDark(this)) R.style.Theme_GossipAudio_Dark else R.style.Theme_GossipAudio)
        super.onCreate(savedInstanceState)
        app = application as GossipApp
        prefs = app.prefs
        palette = ThemeController.palette(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        applySystemBars()
        router = Router(this)
        setContentView(router.container)
        if (savedInstanceState == null) {
            router.start(SplashScreen(this))
        }
    }

    private fun applySystemBars() {
        window.statusBarColor = palette.bg
        window.navigationBarColor = palette.bg
        var flags = window.decorView.systemUiVisibility
        flags = if (!palette.dark) {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        window.decorView.systemUiVisibility = flags
    }

    fun openOnboarding() {
        router.start(OnboardingScreen(this))
    }

    fun finishOnboarding() {
        prefs.onboardingDone = true
        router.start(SplashScreen(this))
    }

    fun toggleTheme() {
        ThemeController.toggle(this)
        recreateWithFade()
    }

    fun setThemeMode(mode: String) {
        prefs.themeMode = mode
        recreateWithFade()
    }

    fun recreateWithFade() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        }
        recreate()
    }

    fun requestMic(callback: (Boolean) -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            callback(true)
            return
        }
        micCallback = callback
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
    }

    fun requestNotifications(callback: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < 33) {
            callback?.invoke(true)
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            callback?.invoke(true)
            return
        }
        notificationCallback = callback
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
    }

    fun requestLegacyStorage(callback: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT >= 29) {
            callback?.invoke(true)
            return
        }
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            callback?.invoke(true)
            return
        }
        notificationCallback = callback
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            REQ_MIC -> {
                micCallback?.invoke(granted)
                micCallback = null
            }
            REQ_NOTIF, REQ_STORAGE -> {
                notificationCallback?.invoke(granted)
                notificationCallback = null
            }
        }
    }

    fun pickImage(callback: (android.net.Uri?) -> Unit) {
        imageCallback = callback
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, REQ_IMAGE)
        } catch (t: Throwable) {
            imageCallback?.invoke(null)
            imageCallback = null
            toast("No image picker available on this device.")
        }
    }

    fun copyUriToCache(uri: android.net.Uri, name: String = "cover-src.jpg"): java.io.File? {
        return try {
            val file = java.io.File(cacheDir, name)
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            if (file.exists() && file.length() > 0) file else null
        } catch (_: Throwable) {
            null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_IMAGE) {
            val uri = if (resultCode == RESULT_OK) data?.data else null
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Throwable) {
                }
            }
            imageCallback?.invoke(uri)
            imageCallback = null
        }
    }

    override fun onBackPressed() {
        if (Overlays.dismissTop(this)) return
        if (!router.back()) {
            super.onBackPressed()
        }
    }

    fun toast(message: String) = Overlays.toast(this, message)

    val contentRoot: android.view.ViewGroup
        get() = window.decorView.findViewById(android.R.id.content)

    companion object {
        private const val REQ_MIC = 501
        private const val REQ_NOTIF = 502
        private const val REQ_STORAGE = 503
        private const val REQ_IMAGE = 504
    }
}
