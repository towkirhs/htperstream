package app.hyperstream

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

/**
 * Saves a video (plain file or HLS/DASH stream) as one MP4 in Downloads/HyperStream, at the
 * highest quality the stream offers. Pieces already preloaded come from the cache. Streams are
 * copied without re-encoding when the phone's MP4 writer accepts their format.
 */
@OptIn(UnstableApi::class)
class Saver(
    private val context: Context,
    private val sources: MediaSource.Factory,
    private val onProgress: (Int) -> Unit,
    private val onDone: (String?) -> Unit, // file name, or null if it failed
) {
    private var transformer: Transformer? = null
    private val temp = File(context.cacheDir, "export.mp4")
    private val progress = ProgressHolder()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun start(item: MediaItem, title: String) {
        temp.delete()
        val t = Transformer.Builder(context)
            .setAssetLoaderFactory(ExoPlayerAssetLoader.Factory(context, DefaultDecoderFactory(context), Clock.DEFAULT, sources))
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    handler.removeCallbacksAndMessages(null)
                    onDone(runCatching { publish(title) }.getOrNull())
                    temp.delete()
                }

                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    handler.removeCallbacksAndMessages(null)
                    temp.delete()
                    onDone(null)
                }
            })
            .build()
        transformer = t
        t.start(item, temp.absolutePath)
        poll()
    }

    private fun poll() {
        val t = transformer ?: return
        if (t.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(progress.progress)
        handler.postDelayed(::poll, 500)
    }

    fun cancel() {
        handler.removeCallbacksAndMessages(null)
        transformer?.cancel()
        transformer = null
        temp.delete()
    }

    /** Copies the finished file into the phone's Downloads folder; returns its name. */
    private fun publish(title: String): String {
        val name = title.replace(Regex("""[\\/:*?"<>|\n\r]+"""), " ").trim().take(80).ifEmpty { "video" } + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HyperStream")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { out -> temp.inputStream().use { it.copyTo(out) } }
        return name
    }

    companion object {
        // Downloads/ without any storage permission needs Android 10 or newer.
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}
