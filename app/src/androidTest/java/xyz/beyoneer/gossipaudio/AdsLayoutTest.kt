package xyz.beyoneer.gossipaudio

import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.screens.ShellScreen

@RunWith(AndroidJUnit4::class)
class AdsLayoutTest {
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()

    @Test fun disabledTestAdsLeaveNoBlankSpaceAndSurviveNavigation() {
        assertFalse(BuildConfig.ADS_ENABLED)
        assertEquals("ca-app-pub-3940256099942544/6300978111", BuildConfig.ADMOB_BANNER_ID)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val shell = ShellScreen(activity)
                activity.router.start(shell)
                val slot = descendants(shell.root).first { it.contentDescription == "Advertisement" }
                assertEquals(View.GONE, slot.visibility)
                shell.selectTab(3, animate = false)
                assertEquals(View.GONE, slot.visibility)
                shell.selectTab(0, animate = false)
                assertEquals(View.GONE, slot.visibility)
                assertTrue(!activity.adConsent.ready)
            }
        }
    }
}
