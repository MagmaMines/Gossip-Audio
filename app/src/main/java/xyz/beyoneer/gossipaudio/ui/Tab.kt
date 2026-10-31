package xyz.beyoneer.gossipaudio.ui

import android.view.View

/** One page inside the shell (Studio / Voices / Library / Settings). */
interface Tab {
    val root: View
    fun onShow() {}
    fun onHide() {}
}
