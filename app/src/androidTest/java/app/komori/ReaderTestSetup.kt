package app.komori

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Assert.assertTrue

/** Accept Android's first-use fullscreen hint through its own UI, without changing app permissions. */
internal fun UiDevice.confirmFullscreenTutorial() {
    if (executeShellCommand("settings get secure immersive_mode_confirmations").trim() == "confirmed") return
    val prompt = By.pkg(Pattern.compile("android|com\\.android\\.systemui"))
        .text(Pattern.compile("got it", Pattern.CASE_INSENSITIVE))
    val confirmation = wait(Until.findObject(prompt), 5000) ?: return
    confirmation.click()
    assertTrue("Android fullscreen tutorial did not close", wait(Until.gone(prompt), 5000))
}
