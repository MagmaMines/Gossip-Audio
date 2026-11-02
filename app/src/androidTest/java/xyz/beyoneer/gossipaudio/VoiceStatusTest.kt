package xyz.beyoneer.gossipaudio

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.beyoneer.gossipaudio.speech.VoiceDownloads
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.screens.ShellScreen
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton

@RunWith(AndroidJUnit4::class)
class VoiceStatusTest {
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun downloadedVoiceShowsInstalledAndCanBeSelectedAfterNavigation() {
        val app = ApplicationProvider.getApplicationContext<GossipApp>()
        // Use the real installer; a previous test may already have installed it.
        VoiceDownloads.download(app, "hi_super")
        assertTrue(VoiceDownloads.installed(app, "hi_super"))
        app.prefs.selectedVoiceId = "ljspeech"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val shell = ShellScreen(activity)
                activity.router.start(shell)
                shell.selectTab(1, animate = false)
                fun hindiCard(): ViewGroup = descendants(shell.voices.root).filterIsInstance<ViewGroup>()
                    .filter { group -> descendants(group).any { it is TextView && it.text == "Hindi" } &&
                        descendants(group).any { it is BrandButton && it.text() == "Use this voice" } }
                    .minByOrNull { descendants(it).size } ?: error("Installed Hindi voice still shows Download")
                val button = descendants(hindiCard()).filterIsInstance<BrandButton>().first { it.text() == "Use this voice" }
                button.performClick()
                assertEquals("hi_super", app.prefs.selectedVoiceId)
                assertTrue(descendants(shell.voices.root).filterIsInstance<BrandButton>().any { it.text() == "Selected" })
                shell.selectTab(0, animate = false)
                shell.selectTab(1, animate = false)
                assertTrue(VoiceDownloads.installed(activity, "hi_super"))
                assertTrue(descendants(shell.voices.root).filterIsInstance<BrandButton>().any { it.text() == "Selected" })
            }
        }
    }
    @Test fun downloadButtonUpdatesSharedCardsAfterLeavingAndReturning() {
        val app = ApplicationProvider.getApplicationContext<GossipApp>()
        VoiceDownloads.directory(app, "hi_super").deleteRecursively()
        assertTrue(!VoiceDownloads.installed(app, "hi_super"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var shell: ShellScreen
            scenario.onActivity { activity ->
                shell = ShellScreen(activity)
                activity.router.start(shell)
                shell.selectTab(1, animate = false)
                val hindi = descendants(shell.voices.root).filterIsInstance<ViewGroup>()
                    .filter { group -> descendants(group).any { it is TextView && it.text == "Hindi" } && descendants(group).any { it is BrandButton && it.text() == "Download" } }
                    .minByOrNull { descendants(it).size } ?: error("Hindi card not found")
                descendants(hindi).filterIsInstance<BrandButton>().first { it.text() == "Download" }.performClick()
            }
            onView(withText("Download")).inRoot(isDialog()).perform(click())
            scenario.onActivity { shell.selectTab(0, animate = false) }
            val deadline = System.currentTimeMillis() + 300_000
            while (!VoiceDownloads.installed(app, "hi_super") && System.currentTimeMillis() < deadline) Thread.sleep(500)
            assertTrue("Download did not complete", VoiceDownloads.installed(app, "hi_super"))
            scenario.onActivity {
                shell.selectTab(1, animate = false)
                val french = descendants(shell.voices.root).filterIsInstance<ViewGroup>()
                    .filter { group -> descendants(group).any { it is TextView && it.text == "French" } && descendants(group).any { it is BrandButton && it.text() == "Use this voice" } }
                assertTrue("Shared French voice did not show installed", french.isNotEmpty())
                assertTrue(VoiceDownloads.installed(app, "fr_super"))
            }
        }
    }

}
