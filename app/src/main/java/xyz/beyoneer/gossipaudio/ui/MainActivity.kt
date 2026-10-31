package xyz.beyoneer.gossipaudio.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "GossipAudio"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        setContentView(tv)
    }
}
