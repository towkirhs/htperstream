package app.hyperstream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun comparesVersionsNumerically() {
        assertTrue(Updater.isNewer("v0.1.9", "0.1.8"))
        assertTrue(Updater.isNewer("v0.1.10", "0.1.9"))
        assertTrue(Updater.isNewer("v0.2.0", "0.1.99"))
        assertFalse(Updater.isNewer("v0.1.8", "0.1.8"))
        assertFalse(Updater.isNewer("v0.1.7", "0.1.8"))
    }
}
