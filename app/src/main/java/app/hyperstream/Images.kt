package app.hyperstream

/** Picks the best-quality address for an image the user long-pressed. */
object Images {
    /**
     * Reddit and X show resized copies; their originals live at a predictable address.
     * Returns the original's address, or null if there is nothing better than [url].
     */
    fun original(url: String): String? {
        val host = url.substringAfter("://").substringBefore('/')
        val path = url.substringAfter(host).substringBefore('?')
        return when {
            host == "preview.redd.it" -> "https://i.redd.it$path"
            host == "pbs.twimg.com" && path.startsWith("/media/") -> {
                val format = Regex("""[?&]format=(\w+)""").find(url)?.groupValues?.get(1) ?: "jpg"
                "https://pbs.twimg.com$path?format=$format&name=orig"
            }
            else -> null
        }?.takeIf { it != url }
    }

    /** A page showing the image fitted to the screen; zoom with two fingers. Falls back to [url]. */
    fun viewerHtml(url: String): String {
        val best = original(url) ?: url
        val esc = { s: String -> s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;") }
        return """<html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=10">
            <style>html, body { margin: 0; height: 100%; background: #000; }
            body { display: flex; align-items: center; justify-content: center; }
            img { max-width: 100%; max-height: 100%; }</style></head>
            <body><img src="${esc(best)}" data-fallback="${esc(url)}"
            onerror="if (this.dataset.fallback) { this.src = this.dataset.fallback; this.dataset.fallback = ''; }"></body></html>"""
    }
}
