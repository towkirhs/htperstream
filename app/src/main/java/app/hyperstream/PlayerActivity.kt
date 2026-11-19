@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.hyperstream

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.offline.DashDownloader
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.ProgressiveDownloader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** One on-disk video cache shared by playback and preloading (up to 2 GB, oldest removed first). */
object VideoCache {
    private var cache: SimpleCache? = null

    @Synchronized
    fun get(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.cacheDir, "video"),
        LeastRecentlyUsedCacheEvictor(2L * 1024 * 1024 * 1024),
        StandaloneDatabaseProvider(context.applicationContext),
    ).also { cache = it }
}

/** Plays a stream at its highest quality while preloading the rest of it to disk in parallel. */
class PlayerActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var quality: TextView
    private lateinit var loaded: TextView
    private val executor = Executors.newFixedThreadPool(6)

    @Volatile private var downloader: Downloader? = null
    @Volatile private var closed = false
    private var saver: Saver? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.player)
        quality = findViewById(R.id.quality)
        loaded = findViewById(R.id.loaded)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val url = intent.getStringExtra("url")!!
        val kind = intent.getStringExtra("kind")
        val headers = buildMap {
            intent.getStringExtra("referer")?.let { put("Referer", it) }
            intent.getStringExtra("cookie")?.let { put("Cookie", it) }
        }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(intent.getStringExtra("ua") ?: "Mozilla/5.0")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        val cached = CacheDataSource.Factory().setCache(VideoCache.get(this)).setUpstreamDataSourceFactory(http)
        val item = MediaItem.Builder().setUri(url).setMimeType(
            when (kind) {
                "hls" -> MimeTypes.APPLICATION_M3U8
                "dash" -> MimeTypes.APPLICATION_MPD
                else -> null
            }
        ).build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cached))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        // The point of the app: always the best version the site offers, not what the connection "deserves".
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setForceHighestSupportedBitrate(true).build()
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.height > 0) quality.text = "${minOf(size.width, size.height)}p"
            }

            override fun onPlayerError(error: PlaybackException) {
                // For refused requests, say which server answered what, so the cause can be tracked down.
                val http = error.cause as? HttpDataSource.InvalidResponseCodeException
                val detail = if (http != null) "HTTP ${http.responseCode} from ${http.dataSpec.uri.host}" else error.errorCodeName
                // Go back to the page, which plays the video its own way.
                setResult(RESULT_FIRST_USER, android.content.Intent().putExtra("error", detail))
                finish()
            }
        })
        playerView.player = player
        player.setMediaItem(item)
        player.prepare()
        player.play()

        // Single tap shows or hides the controls, double tap on a side skips 10 seconds.
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (playerView.isControllerFullyVisible) playerView.hideController() else playerView.showController()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (e.x < playerView.width / 2) player.seekBack() else player.seekForward()
                return true
            }
        })
        playerView.setOnTouchListener { _, event -> gestures.onTouchEvent(event) }
        quality.setOnClickListener { chooseQuality() }

        val title = intent.getStringExtra("title").orEmpty()
        val page = intent.getStringExtra("referer")
        findViewById<ImageButton>(R.id.save).apply {
            visibility = if (page?.startsWith("http") == true) View.VISIBLE else View.GONE
            setOnClickListener { Bookmarks.pick(this@PlayerActivity, title, page!!) }
        }
        findViewById<ImageButton>(R.id.download).setOnClickListener { download(item, title, cached) }

        preload(item, kind, cached)
    }

    /** Saves the whole video, at its highest quality, as an MP4 in Downloads/HyperStream. */
    private fun download(item: MediaItem, title: String, cached: CacheDataSource.Factory) {
        when {
            !Saver.supported -> return toast("Downloading needs Android 10 or newer")
            player.isCurrentMediaItemLive -> return toast("Live streams can't be downloaded")
            saver != null -> return toast("Already downloading: ${loaded.text}")
        }
        toast("Downloading in the best quality. Keep the player open until it finishes.")
        saver = Saver(this, DefaultMediaSourceFactory(cached),
            onProgress = { percent -> loaded.visibility = View.VISIBLE; loaded.text = "Downloading $percent%" },
            onDone = { name ->
                saver = null
                loaded.text = if (name != null) "Saved" else "Download failed"
                toast(if (name != null) "Saved to Downloads/HyperStream/$name" else "The download failed. This site may not allow it.")
            },
        ).also { it.start(item, title.ifBlank { "HyperStream video" }) }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    /** Lists the video qualities of the stream; picking one pins it, "Best available" unpins. */
    private fun chooseQuality() {
        val choices = mutableListOf<Pair<Int, TrackSelectionOverride>>()
        for (group in player.currentTracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                choices += minOf(format.width, format.height) to TrackSelectionOverride(group.mediaTrackGroup, i)
            }
        }
        choices.sortByDescending { it.first }
        val labels = listOf("Best available") + choices.map { "${it.first}p" }
        AlertDialog.Builder(this).setTitle("Quality").setItems(labels.toTypedArray()) { _, which ->
            val params = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            if (which > 0) params.addOverride(choices[which - 1].second)
            player.trackSelectionParameters = params.build()
        }.show()
    }

    /**
     * Downloads the whole video into the cache with several connections at once, so playback and
     * seeking read from disk. Only the highest quality is fetched. If anything here fails the
     * video still plays; it just loads the ordinary way.
     */
    private fun preload(item: MediaItem, kind: String?, cached: CacheDataSource.Factory) {
        if (kind != "hls" && kind != "dash") {
            download(ProgressiveDownloader(item, cached, executor))
            return
        }
        val helper = DownloadHelper.forMediaItem(this, item, DefaultRenderersFactory(this), cached)
        helper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper) {
                val best = DownloadHelper.getDefaultTrackSelectorParameters(this@PlayerActivity)
                    .buildUpon().setForceHighestSupportedBitrate(true).build()
                for (period in 0 until helper.periodCount) {
                    helper.clearTrackSelections(period)
                    helper.addTrackSelection(period, best)
                }
                val chosen = helper.getDownloadRequest(null).toMediaItem()
                helper.release()
                download(if (kind == "hls") HlsDownloader(chosen, cached, executor) else DashDownloader(chosen, cached, executor))
            }

            override fun onPrepareError(helper: DownloadHelper, e: IOException) = helper.release()
        })
    }

    private fun download(d: Downloader) {
        if (closed) return
        downloader = d
        thread {
            try {
                d.download { _, _, percent ->
                    runOnUiThread {
                        loaded.visibility = View.VISIBLE
                        if (saver == null) loaded.text = if (percent >= 100f) "Fully loaded" else "${percent.toInt()}% loaded"
                    }
                }
            } catch (e: Exception) {
                // Cancelled on exit, or the site refused: playback carries on without preloading.
            }
        }
    }

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        saver?.cancel()
        closed = true
        downloader?.cancel()
        executor.shutdownNow()
        player.release()
        super.onDestroy()
    }
}
