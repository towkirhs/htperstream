package app.hyperstream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockTest {
    private val list = """
        # comment
        127.0.0.1 localhost
        0.0.0.0 0.0.0.0
        0.0.0.0 doubleclick.net
        0.0.0.0 ads.Example.com  # trailing comment
    """.trimIndent()

    @Test fun blocksListedHostsAndTheirSubdomains() {
        AdBlock.load(list.byteInputStream())
        assertTrue(AdBlock.isBlocked("doubleclick.net"))
        assertTrue(AdBlock.isBlocked("stats.g.doubleclick.net"))
        assertTrue(AdBlock.isBlocked("ads.example.com"))
        assertTrue(AdBlock.isBlocked("x.ADS.example.com"))
    }

    @Test fun letsEverythingElseThrough() {
        AdBlock.load(list.byteInputStream())
        assertFalse(AdBlock.isBlocked("example.com"))
        assertFalse(AdBlock.isBlocked("www.reddit.com"))
        assertFalse(AdBlock.isBlocked("localhost"))
        assertFalse(AdBlock.isBlocked("notdoubleclick.net"))
        assertFalse(AdBlock.isBlocked(null))
    }
}
