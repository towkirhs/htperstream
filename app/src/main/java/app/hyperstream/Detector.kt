package app.hyperstream

/** A playable stream found in a page's network requests. */
data class Stream(val url: String, val kind: String, val derived: Boolean = false)

/** Decides which of a page's requests is the video's master stream. */
object Detector {
    // Per-quality playlists of X and Reddit: we want the master playlist that lists them all.
    private val VARIANT = Regex("""/pl/(avc1|mp4a|hvc1|hev1)/|/HLS_[^/]*\.m3u8""")
    private val REDDIT = Regex("""^https://v\.redd\.it/([^/?]+)/""")

    fun detect(url: String): Stream? {
        if (!url.startsWith("http")) return null
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        if (path.endsWith(".m3u8")) return if (VARIANT.containsMatchIn(url)) null else Stream(url, "hls")
        if (path.endsWith(".mpd")) return Stream(url, "dash")
        // Any other file of a Reddit video: go to that video's manifest, which lists every quality.
        REDDIT.find(url)?.let { return Stream("https://v.redd.it/${it.groupValues[1]}/DASHPlaylist.mpd", "dash", derived = true) }
        if (path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm")) return Stream(url, "mp4")
        return null
    }

    private fun host(url: String) = url.substringAfter("://").substringBefore('/')
    private fun redditId(url: String) = REDDIT.find(url)?.groupValues?.get(1)

    /** The stream to offer after [found] shows up while [current] is on offer. */
    fun choose(current: Stream?, found: Stream): Stream {
        if (current == null) return found
        // A manifest the page itself requested beats one we worked out for the same video.
        if (found.derived && redditId(current.url) == redditId(found.url)) return current
        // Media files of a stream we already have (e.g. its init segment) are not a new video.
        if (found.kind == "mp4" && current.kind != "mp4" && host(current.url) == host(found.url)) return current
        return found
    }
}
