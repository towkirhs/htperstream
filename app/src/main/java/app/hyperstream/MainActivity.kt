package app.hyperstream

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import java.io.ByteArrayInputStream
import kotlin.concurrent.thread
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/** A small browser that watches each page's requests for a video stream and offers to play it. */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var playButton: Button
    private lateinit var progress: ProgressBar
    private var stream: Stream? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        thread { assets.open("hosts.txt").use(AdBlock::load) }
        web = findViewById(R.id.web)
        urlBar = findViewById(R.id.url)
        playButton = findViewById(R.id.play)
        progress = findViewById(R.id.progress)

        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Without the WebView marker, sites serve the same pages they give Chrome.
            userAgentString = userAgentString.replace("; wv", "")
        }
        web.webViewClient = object : WebViewClient() {
            // Called on a background thread for every request the page makes.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                // Ad and tracker requests get an empty answer instead of reaching the server.
                if (!request.isForMainFrame && AdBlock.isBlocked(request.url.host)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                Detector.detect(request.url.toString())?.let { found -> runOnUiThread { offer(Detector.choose(stream, found)) } }
                return null
            }

            // Links that try to open another app (intent://, reddit://) are ignored,
            // and so are redirects to ad servers (the pop-up ads of streaming sites).
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                (request.url.scheme != "http" && request.url.scheme != "https") ||
                    (!request.hasGesture() && AdBlock.isBlocked(request.url.host))

            // Fires for normal navigation and for in-page navigation on sites like Reddit and X.
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                urlBar.setText(if (url.startsWith("http")) url else "")
                offer(null)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, percent: Int) {
                progress.progress = percent
                progress.visibility = if (percent < 100) View.VISIBLE else View.INVISIBLE
            }
        }

        urlBar.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_GO) return@setOnEditorActionListener false
            val text = urlBar.text.toString().trim()
            web.loadUrl(
                when {
                    text.startsWith("http://") || text.startsWith("https://") -> text
                    text.contains('.') && !text.contains(' ') -> "https://$text"
                    else -> "https://duckduckgo.com/?q=" + Uri.encode(text)
                }
            )
            urlBar.clearFocus()
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(urlBar.windowToken, 0)
            true
        }

        playButton.setOnClickListener {
            val s = stream ?: return@setOnClickListener
            web.evaluateJavascript("document.querySelectorAll('video').forEach(v => v.pause())", null)
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra("url", s.url)
                    .putExtra("kind", s.kind)
                    .putExtra("referer", web.url)
                    .putExtra("cookie", CookieManager.getInstance().getCookie(s.url))
                    .putExtra("ua", web.settings.userAgentString)
            )
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        open(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    /** Load the link this app was opened with (shared text or a web link), or the start page. */
    private fun open(intent: Intent) {
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        val link = text?.let { Regex("""https?://\S+""").find(it)?.value }
        if (link != null) web.loadUrl(link)
        else if (web.url == null) web.loadDataWithBaseURL(null, HOME, "text/html", "utf-8", null)
    }

    private fun offer(s: Stream?) {
        stream = s
        playButton.visibility = if (s == null) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private companion object {
        const val HOME = """
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
              body { margin: 0; padding: 48px 24px; background: #121419; color: #f2f3f5; font: 16px/1.5 sans-serif; text-align: center; }
              h1 { margin: 0 0 8px; font-size: 28px; color: #ffc43d; }
              p { margin: 0 0 32px; color: #9aa1ad; }
              a { display: block; margin: 0 auto 12px; max-width: 320px; padding: 16px; border-radius: 12px;
                  background: #1b1e25; border: 1px solid #343946; color: #f2f3f5; text-decoration: none; font-weight: 600; }
            </style></head><body>
            <h1>HyperStream</h1>
            <p>Open a site and start a video. When a stream is found, a Play button appears at the bottom.</p>
            <a href="https://www.reddit.com">Reddit</a>
            <a href="https://x.com">X</a>
            </body></html>"""
    }
}
