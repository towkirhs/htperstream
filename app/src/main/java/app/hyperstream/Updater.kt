package app.hyperstream

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** "Check for updates": installs the newest APK from this app's GitHub releases. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/towkirhs/htperstream/releases/latest"

    /** True if release tag [latest] ("v0.1.9") is a newer version than [current] ("0.1.8"). */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun version(activity: Activity): String = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0"

    fun check(activity: Activity) {
        toast(activity, "Checking for updates…")
        thread {
            try {
                val release = JSONObject(URL(LATEST).readText())
                val tag = release.getString("tag_name")
                val assets = release.getJSONArray("assets")
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }?.getString("browser_download_url")
                activity.runOnUiThread {
                    if (apk == null || !isNewer(tag, version(activity))) {
                        toast(activity, "HyperStream is up to date (v${version(activity)})")
                    } else {
                        AlertDialog.Builder(activity)
                            .setTitle("Update to $tag?")
                            .setMessage("You have v${version(activity)}. The update downloads and then Android asks you to install it.")
                            .setPositiveButton("Update") { _, _ -> download(activity, apk) }
                            .setNegativeButton("Not now", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread { toast(activity, "Couldn't check for updates. Check your internet connection.") }
            }
        }
    }

    private fun download(activity: Activity, url: String) {
        // Android only lets an app install updates after the user allows it, once, in Settings.
        if (!activity.packageManager.canRequestPackageInstalls()) {
            toast(activity, "Allow HyperStream to install apps, then tap Check for updates again")
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)))
            return
        }
        toast(activity, "Downloading update…")
        thread {
            try {
                val file = File(activity.cacheDir, "updates/HyperStream.apk").apply { parentFile!!.mkdirs() }
                val connection = URL(url).openConnection() as HttpURLConnection // follows GitHub's redirect
                connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                val uri = FileProvider.getUriForFile(activity, activity.packageName + ".files", file)
                activity.runOnUiThread {
                    activity.startActivity(
                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }
            } catch (e: Exception) {
                activity.runOnUiThread { toast(activity, "The download failed. Try again.") }
            }
        }
    }

    private fun toast(activity: Activity, text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
}
