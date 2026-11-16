package app.hyperstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImagesTest {
    @Test fun findsRedditAndXOriginals() {
        assertEquals("https://i.redd.it/abc123.jpeg", Images.original("https://preview.redd.it/abc123.jpeg?width=640&crop=smart&auto=webp&s=f00"))
        assertEquals("https://i.redd.it/funny.gif", Images.original("https://preview.redd.it/funny.gif?width=320&format=png8&s=1"))
        assertEquals("https://pbs.twimg.com/media/GxYz?format=png&name=orig", Images.original("https://pbs.twimg.com/media/GxYz?format=png&name=small"))
        assertEquals("https://pbs.twimg.com/media/GxYz.jpg?format=jpg&name=orig", Images.original("https://pbs.twimg.com/media/GxYz.jpg"))
    }

    @Test fun leavesOtherImagesAlone() {
        assertNull(Images.original("https://example.com/cat.webp"))
        assertNull(Images.original("https://external-preview.redd.it/x.jpg?s=1"))
        assertNull(Images.original("https://pbs.twimg.com/profile_images/1/a.jpg"))
        assertNull(Images.original("https://pbs.twimg.com/media/GxYz?format=jpg&name=orig"))
    }

    @Test fun viewerFallsBackToTheAddressShown() {
        val html = Images.viewerHtml("https://preview.redd.it/a.png?s=1&w=2")
        assert("src=\"https://i.redd.it/a.png\"" in html)
        assert("data-fallback=\"https://preview.redd.it/a.png?s=1&amp;w=2\"" in html)
    }
}
