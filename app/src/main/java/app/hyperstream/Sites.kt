package app.hyperstream

import java.net.URI

/** The sites on the home page: the user adds and removes them with the star next to the address bar. */
object Sites {
    val DEFAULT = listOf("https://www.reddit.com", "https://x.com")

    /** "https://www.reddit.com/r/videos/..." -> "https://www.reddit.com"; null for anything that isn't a web page. */
    fun originOf(url: String?): String? {
        val uri = runCatching { URI(url ?: return null) }.getOrNull() ?: return null
        if (uri.scheme != "http" && uri.scheme != "https" || uri.host.isNullOrEmpty()) return null
        return "${uri.scheme}://${uri.host.lowercase()}"
    }

    /** Name shown on the home page: "https://www.reddit.com" -> "reddit.com". */
    fun label(origin: String) = origin.substringAfter("://").removePrefix("www.").removePrefix("m.")

    fun homeHtml(sites: List<String>, version: String): String {
        val rows = sites.mapIndexed { i, s ->
            val u = java.net.URLEncoder.encode(s, "UTF-8")
            """<div class="row" id="s$i"><a class="site" href="$s">${label(s)}</a><a class="x" href="hyperstream://remove?i=$i&amp;u=$u" aria-label="Remove">✕</a></div>"""
        }.joinToString("\n")
        return """
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              body { margin: 0; padding: 40px 16px; background: #121419; color: #f2f3f5; font: 16px/1.5 sans-serif; }
              h1 { margin: 0 0 4px; font-size: 28px; color: #ffc43d; text-align: center; }
              p { margin: 0 auto 28px; max-width: 360px; color: #9aa1ad; text-align: center; font-size: 14px; }
              .row { display: flex; align-items: center; max-width: 400px; margin: 0 auto 10px; border-radius: 12px;
                     background: #1b1e25; border: 1px solid #343946; }
              .site { flex: 1; padding: 16px; color: #f2f3f5; text-decoration: none; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
              .update { display: block; width: fit-content; margin: 28px auto 0; padding: 12px 20px; border-radius: 12px;
                        border: 1px solid #343946; color: #ffc43d; text-decoration: none; font-weight: 600; font-size: 14px; }
              .x { width: 48px; height: 48px; line-height: 48px; text-align: center; color: #9aa1ad; text-decoration: none; flex: none; }
            </style></head><body>
            <h1>HyperStream</h1>
            <p>Open a site and start a video; a Play button appears when a stream is found.
            Tap ☆ next to the address bar to add the site you're on.</p>
            $rows
            <a class="update" href="hyperstream://update">Check for updates · v$version</a>
            </body></html>"""
    }
}
