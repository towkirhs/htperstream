package app.hyperstream

import java.io.InputStream

/**
 * Blocks requests to known ad and tracker servers, using the StevenBlack hosts list
 * (downloaded into the app's assets at build time).
 */
object AdBlock {
    @Volatile private var hosts: Set<String> = emptySet()

    /** Reads a hosts file: lines like "0.0.0.0 ads.example.com". */
    fun load(input: InputStream) {
        val set = HashSet<String>(100_000)
        input.bufferedReader().forEachLine { line ->
            if (!line.startsWith("0.0.0.0 ")) return@forEachLine
            val host = line.substring(8).substringBefore('#').trim().lowercase()
            if (host.isNotEmpty() && host != "0.0.0.0") set += host
        }
        hosts = set
    }

    /** True if [host] or any domain it belongs to is listed (ads.x.com is blocked when x.com is). */
    fun isBlocked(host: String?): Boolean {
        var h = host?.lowercase() ?: return false
        while (true) {
            if (h in hosts) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }
}
