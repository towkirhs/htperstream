package app.hyperstream

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarksTest {
    private val a = Bookmark("Football", "Final highlights", "https://x.com/a/status/1")
    private val b = Bookmark("Funny", "Cat", "https://www.reddit.com/r/videos/comments/2")
    private val c = Bookmark("Football", "Semi-final", "https://site.example/watch?v=3&t=10")

    @Test fun roundTripsThroughJson() {
        assertEquals(listOf(a, b, c), Bookmarks.parse(Bookmarks.toJson(listOf(a, b, c))))
        assertEquals(emptyList<Bookmark>(), Bookmarks.parse(null))
        assertEquals(emptyList<Bookmark>(), Bookmarks.parse("not json"))
    }

    @Test fun groupsByFolderInFirstUseOrder() {
        assertEquals(listOf("Football", "Funny"), Bookmarks.folders(listOf(a, b, c)))
    }

    @Test fun savingTheSamePageAgainDoesNotDuplicateIt() {
        val again = a.copy(title = "Final highlights (new title)")
        assertEquals(listOf(b, c, again), Bookmarks.add(listOf(a, b, c), again))
        assertEquals(listOf(a, b, c, a.copy(folder = "Funny")), Bookmarks.add(listOf(a, b, c), a.copy(folder = "Funny")))
    }

    @Test fun homePageListsFoldersWithTheirVideos() {
        val html = Sites.homeHtml(emptyList(), listOf(a, b, c), "0.1.10")
        assert(">Football<" in html && ">Funny<" in html)
        assert("href=\"https://site.example/watch?v=3&amp;t=10\"" in html)
        assert("hyperstream://unsave?i=2" in html)
        assert(html.indexOf("Final highlights") < html.indexOf("Semi-final") && html.indexOf("Semi-final") < html.indexOf(">Funny<"))
    }
}
