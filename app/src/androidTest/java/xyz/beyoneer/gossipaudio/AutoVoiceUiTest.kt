package xyz.beyoneer.gossipaudio

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.screens.ShellScreen
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton

@RunWith(AndroidJUnit4::class)
class AutoVoiceUiTest {
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()

    @Test fun autoVoiceAndLicenseControlsAreVisible() {
        val app = ApplicationProvider.getApplicationContext<GossipApp>()
        app.prefs.autoVoice = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val shell = ShellScreen(activity)
                activity.router.start(shell)
                val studio = descendants(shell.studio.root)
                studio.filterIsInstance<EditText>().first { it.contentDescription == "Your gossip text" }.setText("नमस्ते आपका स्वागत है")
                studio.filterIsInstance<BrandButton>().first { it.text() == "Auto voice: off" }.performClick()
                assertTrue(app.prefs.autoVoice)
                assertTrue(descendants(shell.studio.root).filterIsInstance<TextView>().any {
                    it.text.toString().startsWith("Detected: Hindi") || it.text.toString().startsWith("Hindi · download needed")
                })
                shell.selectTab(1, animate = false)
                assertTrue(descendants(shell.voices.root).any { it.contentDescription == "Hindi license information" })
                assertTrue(descendants(shell.voices.root).any { it.contentDescription == "LJ Speech license information" })
            }
        }
    }
}
