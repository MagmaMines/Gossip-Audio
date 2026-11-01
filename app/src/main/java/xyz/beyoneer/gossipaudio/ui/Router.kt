package xyz.beyoneer.gossipaudio.ui

import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import xyz.beyoneer.gossipaudio.ui.theme.Palette
import xyz.beyoneer.gossipaudio.ui.theme.Ui

/** A screen owns one root view and gets lifecycle callbacks from the router. */
abstract class Screen(protected val activity: MainActivity) {

    protected val palette: Palette get() = activity.palette

    val root: View by lazy { onCreateView() }

    protected abstract fun onCreateView(): View

    open fun onShown() {}

    open fun onHidden() {}

    open fun onDestroyed() {}

    /** Return true when the screen consumed the back press (e.g. switched tab). */
    open fun onBack(): Boolean = false
}

/**
 * Lightweight stack router with fade/pop/slide transitions. Deliberately not
 * using Fragments or Navigation — fewer moving parts, smoother frames.
 */
class Router(private val activity: MainActivity) {

    val container = FrameLayout(activity).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(activity.palette.bg)
    }

    private val stack = ArrayList<Screen>()

    fun start(screen: Screen) {
        stack.forEach { it.onHidden(); it.onDestroyed() }
        stack.clear()
        container.removeAllViews()
        stack.add(screen)
        val view = screen.root
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
        view.translationY = 0f
        container.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        screen.onShown()
        container.requestLayout()
        container.requestApplyInsets()
    }

    fun push(screen: Screen, animate: Boolean = true) {
        val previous = stack.lastOrNull()
        stack.add(screen)
        val view = screen.root
        view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        container.addView(view)
        if (animate && !activity.prefs.reduceMotion) {
            val rise = Ui.dpf(activity, 54f)
            view.alpha = 0f
            view.translationY = rise
            view.scaleX = 0.975f
            view.scaleY = 0.975f
            view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(380)
                .setInterpolator(PathInterpolator(0.16f, 0.9f, 0.12f, 1f))
                .start()
            previous?.root?.animate()?.alpha(0.25f)?.scaleX(0.985f)?.scaleY(0.985f)?.setDuration(300)?.start()
        } else {
            view.alpha = 1f
        }
        previous?.onHidden()
        screen.onShown()
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        val leaving = stack.removeAt(stack.size - 1)
        val below = stack.last()
        val view = leaving.root
        if (!activity.prefs.reduceMotion) {
            view.animate().alpha(0f).translationY(Ui.dpf(activity, 40f)).scaleX(0.98f).scaleY(0.98f)
                .setDuration(260)
                .withEndAction {
                    container.removeView(view)
                    leaving.onDestroyed()
                }
                .start()
            below.root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start()
        } else {
            container.removeView(view)
            leaving.onDestroyed()
            below.root.alpha = 1f
        }
        below.onShown()
        return true
    }

    fun popToRoot() {
        while (stack.size > 1) pop()
    }

    fun back(): Boolean {
        val current = stack.lastOrNull()
        if (current != null && current.onBack()) return true
        return if (stack.size > 1) pop() else false
    }

    fun current(): Screen? = stack.lastOrNull()
}
