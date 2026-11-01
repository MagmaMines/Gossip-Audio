package xyz.beyoneer.gossipaudio

import android.view.ViewGroup
import android.view.View
import android.widget.ScrollView
import xyz.beyoneer.gossipaudio.ui.screens.OnboardingScreen
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.screens.ShellScreen

@RunWith(AndroidJUnit4::class)
class StartupLayoutTest {
    @Test fun onboardingIsScrollableOnShortScreens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = OnboardingScreen(activity).root as ViewGroup
                val density = activity.resources.displayMetrics.density
                val w = (320 * density).toInt()
                val h = (360 * density).toInt()
                root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, w, h)
                val scroll = root.getChildAt(1) as ScrollView
                assertTrue("First-run controls must scroll on a short screen", scroll.getChildAt(0).height > scroll.height)
            }
        }
    }

    @Test fun coldLaunchAndThemeRecreationFillSameWindow() {
        val app = ApplicationProvider.getApplicationContext<GossipApp>()
        app.prefs.onboardingDone = true
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(4000) // allow the branded splash to navigate and layout
            var width = 0
            var height = 0
            scenario.onActivity { activity ->
                assertTrue(activity.router.current() is ShellScreen)
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val root = activity.router.container
                assertTrue(root.width > 0 && root.height > 0)
                assertEquals(content.width, root.width)
                assertEquals(content.height, root.height)
                width = root.width; height = root.height
                activity.toggleTheme()
            }
            Thread.sleep(4000)
            scenario.onActivity { activity ->
                assertTrue(activity.router.current() is ShellScreen)
                assertEquals(width, activity.router.container.width)
                assertEquals(height, activity.router.container.height)
                assertEquals(1f, activity.router.current()!!.root.scaleX, 0.001f)
                assertEquals(1f, activity.router.current()!!.root.scaleY, 0.001f)
            }
        }
    }
}
