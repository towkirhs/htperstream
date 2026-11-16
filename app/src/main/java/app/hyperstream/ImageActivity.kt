package app.hyperstream

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/** Full-screen view of one image (photo, GIF, WebP and other formats) at its best quality, with zoom. */
class ImageActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        setContentView(web)
        web.setBackgroundColor(0xFF000000.toInt())
        web.settings.apply {
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = intent.getStringExtra("ua") ?: userAgentString
        }
        // The page's address as base, so the image server sees the same referrer as on the page.
        web.loadDataWithBaseURL(intent.getStringExtra("referer"), Images.viewerHtml(intent.getStringExtra("url")!!), "text/html", "utf-8", null)
    }
}
