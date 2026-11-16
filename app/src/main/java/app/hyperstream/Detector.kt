package app.hyperstream

/** A playable stream found in a page's network requests, and when it was seen (ms). */
data class Stream(val url: String, val kind: String) {
    var at = 0L
}

/** Decides which of a page's requests is the video's master stream. */
object Detector {
    // Per-quality playlists of X and Reddit: we want the master playlist that lists them all.
    private val VARIANT = Regex("""/pl/(avc1|mp4a|hvc1|hev1)/|/HLS_[^/]*\.m3u8""")

    // Only addresses the page itself requested are offered: sites like Reddit sign them, so an
    // address worked out by hand is refused (HTTP 403).
    fun detect(url: String, now: Long = System.currentTimeMillis()): Stream? = find(url)?.also { it.at = now }

    private fun find(url: String): Stream? {
        if (!url.startsWith("http")) return null
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        if (path.endsWith(".m3u8")) return if (VARIANT.containsMatchIn(url)) null else Stream(url, "hls")
        if (path.endsWith(".mpd")) return Stream(url, "dash")
        if (path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm")) return Stream(url, "mp4")
        return null
    }

    /** The stream to offer after [found] shows up while [current] is on offer. */
    fun choose(current: Stream?, found: Stream): Stream {
        if (current == null) return found
        // Once a page has a streamed video (HLS/DASH), a plain MP4 is its own media file or, more
        // often, a video ad: keep offering the stream.
        if (found.kind == "mp4" && current.kind != "mp4") return current
        // A master playlist is followed within moments by the playlists of its qualities:
        // keep the master, which lists every quality. Later manifests are a new video.
        if (current.kind != "mp4" && found.at - current.at < 2000) return current
        return found
    }
}
