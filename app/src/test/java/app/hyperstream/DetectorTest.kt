package app.hyperstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorTest {
    private val xMaster = "https://video.twimg.com/ext_tw_video/1/pu/pl/abc.m3u8?tag=12&v=1"
    private val redditHls = "https://v.redd.it/abc123/HLSPlaylist.m3u8?a=1&f=sd"
    private val redditDash = Stream("https://v.redd.it/abc123/DASHPlaylist.mpd", "dash", derived = true)

    @Test fun findsMasterPlaylists() {
        assertEquals(Stream(xMaster, "hls"), Detector.detect(xMaster))
        assertEquals(Stream(redditHls, "hls"), Detector.detect(redditHls))
        assertEquals(Stream("https://a.com/v/manifest.mpd", "dash"), Detector.detect("https://a.com/v/manifest.mpd"))
        assertEquals(Stream("https://a.com/clip.MP4?x=1", "mp4"), Detector.detect("https://a.com/clip.MP4?x=1"))
    }

    @Test fun skipsPerQualityPlaylistsAndOtherFiles() {
        assertNull(Detector.detect("https://video.twimg.com/ext_tw_video/1/pu/pl/avc1/720x1280/abc.m3u8"))
        assertNull(Detector.detect("https://video.twimg.com/ext_tw_video/1/pu/pl/mp4a/128000/abc.m3u8"))
        assertNull(Detector.detect("https://v.redd.it/abc123/HLS_720.m3u8"))
        assertNull(Detector.detect("https://a.com/page.html"))
        assertNull(Detector.detect("blob:https://a.com/1234"))
    }

    @Test fun redditFilesLeadToTheManifest() {
        assertEquals(redditDash, Detector.detect("https://v.redd.it/abc123/DASH_480.mp4"))
        assertEquals(redditDash, Detector.detect("https://v.redd.it/abc123/HLS_720.ts"))
    }

    @Test fun choosesBetweenDetections() {
        val hls = Stream(redditHls, "hls")
        assertEquals(hls, Detector.choose(null, hls))
        assertEquals(hls, Detector.choose(hls, redditDash))                       // same video: keep the page's own manifest
        val other = Stream("https://v.redd.it/zzz999/DASHPlaylist.mpd", "dash", derived = true)
        assertEquals(other, Detector.choose(hls, other))                          // a different video replaces it
        val x = Stream(xMaster, "hls")
        val init = Stream("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/0/0/720x1280/i.mp4", "mp4")
        assertEquals(x, Detector.choose(x, init))                                 // a stream's own media file is ignored
        val elsewhere = Stream("https://cdn.b.com/clip.mp4", "mp4")
        assertEquals(elsewhere, Detector.choose(x, elsewhere))
    }
}
