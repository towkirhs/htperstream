package app.hyperstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectorTest {
    private val xMaster = "https://video.twimg.com/ext_tw_video/1/pu/pl/abc.m3u8?tag=12&v=1"
    private val redditHls = "https://v.redd.it/abc123/HLSPlaylist.m3u8?a=1&f=sd"

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

    @Test fun redditFilesAreOfferedAsRequested() {
        // Signed addresses must be used exactly as the page requested them.
        val packaged = "https://packaged-media.redd.it/abc123/pb/m2-res_720p.mp4?m=DASHPlaylist.mpd&v=1&e=1&s=sig"
        assertEquals(Stream(packaged, "mp4"), Detector.detect(packaged))
        assertNull(Detector.detect("https://v.redd.it/abc123/HLS_720.ts"))
    }

    @Test fun choosesBetweenDetections() {
        val hls = Stream(redditHls, "hls")
        assertEquals(hls, Detector.choose(null, hls))
        val other = Stream("https://v.redd.it/zzz999/HLSPlaylist.m3u8?a=2", "hls")
        assertEquals(other, Detector.choose(hls, other))                          // a different video replaces it
        val packaged = Stream("https://packaged-media.redd.it/abc123/pb/m2-res_720p.mp4?s=sig", "mp4")
        assertEquals(hls, Detector.choose(packaged, hls))                         // a full stream beats a single file
        val x = Stream(xMaster, "hls")
        val init = Stream("https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/0/0/720x1280/i.mp4", "mp4")
        assertEquals(x, Detector.choose(x, init))                                 // a stream's own media file is ignored
        val ad = Stream("https://cdn.ads-example.com/creative.mp4", "mp4")
        assertEquals(x, Detector.choose(x, ad))                                   // nor is a video ad elsewhere
        val first = Stream("https://cdn.b.com/one.mp4", "mp4")
        val second = Stream("https://cdn.b.com/two.mp4", "mp4")
        assertEquals(second, Detector.choose(first, second))                      // plain videos: the newest wins
    }
}
