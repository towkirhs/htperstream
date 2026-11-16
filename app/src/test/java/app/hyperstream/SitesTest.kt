package app.hyperstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SitesTest {
    @Test fun originOfKeepsSchemeAndHostOnly() {
        assertEquals("https://www.reddit.com", Sites.originOf("https://www.Reddit.com/r/videos/comments/1?x=1#y"))
        assertEquals("http://example.org", Sites.originOf("http://example.org"))
        assertNull(Sites.originOf("hyperstream://home/"))
        assertNull(Sites.originOf("not a url"))
        assertNull(Sites.originOf(null))
    }

    @Test fun labelIsTheBareSiteName() {
        assertEquals("reddit.com", Sites.label("https://www.reddit.com"))
        assertEquals("x.com", Sites.label("https://x.com"))
        assertEquals("example.org", Sites.label("https://m.example.org"))
    }
}
