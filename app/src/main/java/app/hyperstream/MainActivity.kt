package app.hyperstream

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
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
import android.widget.ImageButton
import android.widget.Toast
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import android.widget.EditText
import android.widget.ProgressBar
import java.io.ByteArrayInputStream
import kotlin.concurrent.thread
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** A small browser that watches each page's requests for a video stream and offers to play it. */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var playButton: Button
    private lateinit var progress: ProgressBar
    private var stream: Stream? = null
    private lateinit var desktopButton: ImageButton
    private lateinit var starButton: ImageButton
    private lateinit var saveButton: ImageButton
    private lateinit var mobileUa: String
    private var mobileMetadata: UserAgentMetadata? = null
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    // When HyperStream's player cannot play a video, it closes and the page plays it instead.
    private val player = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_FIRST_USER) return@registerForActivityResult
        val why = result.data?.getStringExtra("error")
        Toast.makeText(this, "HyperStream couldn't play this one ($why), so it plays on the page", Toast.LENGTH_LONG).show()
        web.evaluateJavascript("document.querySelector('video')?.play()", null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        thread { assets.open("hosts.txt").use(AdBlock::load) }
        web = findViewById(R.id.web)
        urlBar = findViewById(R.id.url)
        playButton = findViewById(R.id.play)
        progress = findViewById(R.id.progress)
        desktopButton = findViewById(R.id.desktop)
        starButton = findViewById(R.id.star)
        saveButton = findViewById(R.id.save)
        saveButton.setOnClickListener {
            val url = web.url?.takeIf { it.startsWith("http") } ?: return@setOnClickListener
            Bookmarks.pick(this, web.title.orEmpty(), url)
        }
        findViewById<View>(R.id.home).setOnClickListener { showHome() }
        starButton.setOnClickListener {
            val origin = Sites.originOf(web.url) ?: return@setOnClickListener
            val list = sites()
            val added = origin !in list
            saveSites(if (added) list + origin else list - origin)
            updateStar()
            Toast.makeText(this, if (added) "Added to home" else "Removed from home", Toast.LENGTH_SHORT).show()
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Pages must ask before opening a new window, which lets onCreateWindow below refuse pop-ups.
            setSupportMultipleWindows(true)
        }
        // Without the WebView marker, sites serve the same pages they give Chrome.
        mobileUa = web.settings.userAgentString.replace("; wv", "")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            mobileMetadata = WebSettingsCompat.getUserAgentMetadata(web.settings)
        }
        applyDesktopMode()
        desktopButton.setOnClickListener {
            prefs.edit().putBoolean("desktop", !prefs.getBoolean("desktop", true)).apply()
            applyDesktopMode()
            val on = prefs.getBoolean("desktop", true)
            Toast.makeText(this, if (on) "Desktop site on" else "Desktop site off", Toast.LENGTH_SHORT).show()
            web.reload()
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
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                // The ✕ next to a site on the home page.
                if (url.scheme == "hyperstream" && url.host == "unsave" && view.url == HOME_URL) {
                    val i = url.getQueryParameter("i")?.toIntOrNull() ?: return true
                    val list = Bookmarks.all(this@MainActivity)
                    if (i in list.indices) Bookmarks.save(this@MainActivity, list.filterIndexed { j, _ -> j != i })
                    showHome()
                    return true
                }
                if (url.scheme == "hyperstream" && url.host == "update" && view.url == HOME_URL) {
                    Updater.check(this@MainActivity)
                    return true
                }
                if (url.scheme == "hyperstream" && url.host == "remove" && view.url == HOME_URL) {
                    saveSites(sites() - url.getQueryParameter("u").orEmpty())
                    view.evaluateJavascript("document.getElementById('s${url.getQueryParameter("i")?.toIntOrNull()}')?.remove()", null)
                    return true
                }
                return (url.scheme != "http" && url.scheme != "https") || (!request.hasGesture() && AdBlock.isBlocked(url.host))
            }

            // Fires for normal navigation and for in-page navigation on sites like Reddit and X.
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                urlBar.setText(if (url.startsWith("http")) url else "")
                offer(null)
                updateStar()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            // Pop-up blocker. A new window is only allowed for a link the user tapped (it opens here,
            // in the same view); windows opened by scripts are what tap-to-play pop-up ads use.
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                val hit = view.hitTestResult
                val link = hit.extra
                if (isUserGesture && hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE && link != null &&
                    !AdBlock.isBlocked(Uri.parse(link).host)
                ) view.loadUrl(link)
                return false
            }

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
            player.launch(
                Intent(this, PlayerActivity::class.java)
                    .putExtra("url", s.url)
                    .putExtra("kind", s.kind)
                    .putExtra("referer", web.url)
                    .putExtra("cookie", CookieManager.getInstance().getCookie(s.url))
                    .putExtra("ua", web.settings.userAgentString)
                    .putExtra("title", web.title)
            )
        }

        // Long-press a picture (photo, GIF, WebP...) to see it full screen at its best quality.
        web.setOnLongClickListener {
            val hit = web.hitTestResult
            val image = hit.extra
            if ((hit.type != WebView.HitTestResult.IMAGE_TYPE && hit.type != WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) ||
                image == null || !image.startsWith("http")
            ) return@setOnLongClickListener false
            startActivity(
                Intent(this, ImageActivity::class.java)
                    .putExtra("url", image)
                    .putExtra("referer", web.url)
                    .putExtra("ua", web.settings.userAgentString)
                    .putExtra("title", web.title)
            )
            true
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
        else if (web.url == null) showHome()
    }

    private fun sites(): List<String> =
        prefs.getString("sites", null)?.split('\n')?.filter { it.isNotEmpty() } ?: Sites.DEFAULT

    private fun saveSites(list: List<String>) = prefs.edit().putString("sites", list.joinToString("\n")).apply()

    private fun showHome() = web.loadDataWithBaseURL(HOME_URL, Sites.homeHtml(sites(), Bookmarks.all(this), Updater.version(this)), "text/html", "utf-8", HOME_URL)

    /** Filled star when the current site is on the home page; hidden on the home page itself. */
    private fun updateStar() {
        val origin = Sites.originOf(web.url)
        starButton.visibility = if (origin == null) View.INVISIBLE else View.VISIBLE
        saveButton.visibility = starButton.visibility
        val saved = origin in sites()
        starButton.setImageResource(if (saved) R.drawable.ic_star_filled else R.drawable.ic_star)
        starButton.contentDescription = if (saved) "Remove from home" else "Add to home"
    }

    /**
     * "Desktop site" (on by default): sites are told this is Chrome on Windows, so they serve their
     * desktop version, while the page still lays out to the phone's width wherever the site allows.
     */
    private fun applyDesktopMode() {
        val on = prefs.getBoolean("desktop", true)
        val chrome = Regex("Chrome/[\\d.]+").find(mobileUa)?.value ?: "Chrome/131.0.0.0"
        web.settings.userAgentString = if (on) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
        } else mobileUa
        // Newer sites also read the browser's "client hints", which must say the same thing.
        mobileMetadata?.let { mobile ->
            WebSettingsCompat.setUserAgentMetadata(
                web.settings,
                if (on) UserAgentMetadata.Builder(mobile).setMobile(false).setPlatform("Windows")
                    .setPlatformVersion("15.0.0").setModel("").build()
                else mobile,
            )
        }
        desktopButton.alpha = if (on) 1f else 0.45f
        desktopButton.contentDescription = if (on) "Desktop site on" else "Desktop site off"
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
        const val HOME_URL = "hyperstream://home/"
    }
}
