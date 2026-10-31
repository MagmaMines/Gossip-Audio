package xyz.beyoneer.gossipaudio.ui.widgets

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import xyz.beyoneer.gossipaudio.ui.theme.Palette
import xyz.beyoneer.gossipaudio.ui.theme.Ui

/**
 * A horizontally scrolling strip that fades at its edges instead of guillotining
 * the last card.
 *
 * The old rows simply clipped at the screen edge, which reads as "unfinished" on
 * every phone: half a voice card, half a style chip. Here the content still
 * scrolls, but a gradient sits on top of the leading/trailing edge and only
 * appears while there is actually more to see — so the row looks deliberate and
 * the affordance is honest.
 */
class FadeRow(context: Context, private val palette: Palette) : FrameLayout(context) {

    val content: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private val scroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        overScrollMode = OVER_SCROLL_NEVER
    }
    private val rightFade = View(context)
    private val leftFade = View(context)

    init {
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        scroll.addView(
            content,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        leftFade.background = GradientDrawable(
            GradientDrawable.Orientation.RIGHT_LEFT,
            intArrayOf(Color.TRANSPARENT, palette.bg)
        )
        rightFade.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.TRANSPARENT, palette.bg)
        )
        listOf(leftFade, rightFade).forEach { fade ->
            fade.isClickable = false
            fade.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(fade, LayoutParams(Ui.dp(context, 30f), LayoutParams.MATCH_PARENT))
        }
        (leftFade.layoutParams as LayoutParams).gravity = Gravity.START
        (rightFade.layoutParams as LayoutParams).gravity = Gravity.END
        leftFade.alpha = 0f
        rightFade.alpha = 0f
        scroll.setOnScrollChangeListener { _, scrollX, _, _, _ -> updateFades(scrollX) }
        post { updateFades(scroll.scrollX) }
    }

    private fun updateFades(scrollX: Int) {
        val overflow = content.width - scroll.width
        if (overflow <= Ui.dp(context, 4f)) {
            leftFade.alpha = 0f
            rightFade.alpha = 0f
            return
        }
        val edge = Ui.dp(context, 10f)
        leftFade.alpha = if (scrollX > edge) 1f else 0f
        rightFade.alpha = if (scrollX < overflow - edge) 1f else 0f
    }
}
