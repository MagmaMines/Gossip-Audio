package xyz.beyoneer.gossipaudio.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import xyz.beyoneer.gossipaudio.GossipApp
import xyz.beyoneer.gossipaudio.R
import xyz.beyoneer.gossipaudio.data.Prefs
import xyz.beyoneer.gossipaudio.ui.screens.OnboardingScreen
import xyz.beyoneer.gossipaudio.ui.screens.SplashScreen
import xyz.beyoneer.gossipaudio.ui.theme.Haptics
import xyz.beyoneer.gossipaudio.ui.theme.Motion
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
    private var storageCallback: ((Boolean) -> Unit)? = null
    private var imageCallback: ((android.net.Uri?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (ThemeController.isDark(this)) R.style.Theme_GossipAudio_Dark else R.style.Theme_GossipAudio)
        super.onCreate(savedInstanceState)
        app = application as GossipApp
        prefs = app.prefs
        palette = ThemeController.palette(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        applySystemBars()
        Haptics.enabled = prefs.haptics
        Ui.textScale = if (prefs.bigText) 1.15f else 1f
        Motion.reduce = prefs.reduceMotion
        router = Router(this)
        setContentView(router.container)
        applyWindowFit()
        // Also covers process-death restore: without this the container would stay empty.
        if (router.current() == null) {
            router.start(SplashScreen(this))
        }
        registerBackHandler()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * One entry point for everything that can start us: a share sheet, "process text"
     * from another app, or one of the launcher shortcuts.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val shared = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!shared.isNullOrBlank()) {
            app.pendingText = shared.trim().take(2000)
            app.pendingTab = 0
        }
        when (intent.action) {
            ACTION_NEW_GOSSIP -> app.pendingTab = 0
            ACTION_OPEN_LIBRARY -> app.pendingTab = 2
            ACTION_PLAY_LATEST -> app.pendingTab = if (app.store.all().isEmpty()) 0 else 2
        }
        dispatchPending()
    }

    /** Applies the pending tab/text to whatever screen happens to be showing. */
    fun dispatchPending() {
        val tab = app.pendingTab
        val shell = router.current() as? xyz.beyoneer.gossipaudio.ui.screens.ShellScreen
        if (tab != null && shell != null) {
            shell.selectTab(tab, animate = false)
            app.pendingTab = null
        }
    }

    /**
     * Android 13+ routes the back gesture/button through OnBackInvokedDispatcher; without this
     * callback the system would finish the activity instead of popping the in-app stack.
     */
    private fun registerBackHandler() {
        if (Build.VERSION.SDK_INT < 33) return
        try {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleBack() }
        } catch (_: Throwable) {
            // Older ROMs that report 33 without the dispatcher: onBackPressed still works.
        }
    }

    private fun handleBack() {
        if (Overlays.dismissTop(this)) return
        if (router.back()) return
        finish()
    }

    private fun applySystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 35 forces edge-to-edge, so the bar colours are ignored there; the icon
            // colour is still ours to set, and it has to follow the theme.
            window.insetsController?.let { controller ->
                val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                if (palette.dark) {
                    controller.setSystemBarsAppearance(0, mask)
                } else {
                    controller.setSystemBarsAppearance(mask, mask)
                }
            }
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = palette.bg
                window.navigationBarColor = palette.bg
            }
        } else {
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = palette.bg
                window.navigationBarColor = palette.bg
                var flags = window.decorView.systemUiVisibility
                flags = if (!palette.dark) {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and
                        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
                window.decorView.systemUiVisibility = flags
            }
        }
    }

    /**
     * Android 15 draws the app under the system bars whether it likes it or not. Pad the
     * root container by exactly the bar heights so the header is not under the clock and
     * the navigation dock is not under the gesture handle.
     */
    private fun applyWindowFit() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val container = router.container
        window.setDecorFitsSystemWindows(false)
        container.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsets.Type.ime())
            val pad = WindowFit.padding(
                statusTop = bars.top,
                navBottom = bars.bottom,
                imeBottom = ime.bottom
            )
            view.setPadding(0, pad.top, 0, pad.bottom)
            insets
        }
        container.requestApplyInsets()
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
        storageCallback = callback
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
            REQ_NOTIF -> {
                notificationCallback?.invoke(granted)
                notificationCallback = null
            }
            REQ_STORAGE -> {
                storageCallback?.invoke(granted)
                storageCallback = null
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
        handleBack()
    }

    /** True when the user permanently blocked a permission; useful for a "fix it in Settings" hint. */
    fun isPermissionPermanentlyDenied(permission: String): Boolean =
        checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED &&
            !shouldShowRequestPermissionRationale(permission)

    /** Deep-link to this app's notification settings so a blocked toggle is never a dead end. */
    fun openNotificationSettings() {
        try {
            startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (_: Throwable) {
            toast("Open Settings \u2192 Apps \u2192 GossipAudio \u2192 Notifications")
        }
    }

    fun toast(message: String) = Overlays.toast(this, message)

    val contentRoot: android.view.ViewGroup
        get() = window.decorView.findViewById(android.R.id.content)

    companion object {
        const val ACTION_NEW_GOSSIP = "xyz.beyoneer.gossipaudio.NEW_GOSSIP"
        const val ACTION_OPEN_LIBRARY = "xyz.beyoneer.gossipaudio.OPEN_LIBRARY"
        const val ACTION_PLAY_LATEST = "xyz.beyoneer.gossipaudio.PLAY_LATEST"
        private const val REQ_MIC = 501
        private const val REQ_NOTIF = 502
        private const val REQ_STORAGE = 503
        private const val REQ_IMAGE = 504
    }
}
