package app.hyperstream

import android.app.Activity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.json.JSONArray
import org.json.JSONObject

/** A saved video or page: kept in a folder, opened again by its page address. */
data class Bookmark(val folder: String, val title: String, val url: String)

/** Saved videos, grouped into folders the user names. Works the same for every site. */
object Bookmarks {
    fun parse(json: String?): List<Bookmark> {
        val array = runCatching { JSONArray(json ?: "[]") }.getOrDefault(JSONArray())
        return (0 until array.length()).map { array.getJSONObject(it) }
            .map { Bookmark(it.getString("folder"), it.getString("title"), it.getString("url")) }
    }

    fun toJson(list: List<Bookmark>): String = JSONArray(list.map {
        JSONObject().put("folder", it.folder).put("title", it.title).put("url", it.url)
    }).toString()

    /** Folders in the order they were first used. */
    fun folders(list: List<Bookmark>) = list.map { it.folder }.distinct()

    /** Adds [b] at the end, or moves it there if this page is already in that folder. */
    fun add(list: List<Bookmark>, b: Bookmark) = list.filterNot { it.folder == b.folder && it.url == b.url } + b

    private fun load(activity: Activity) = parse(activity.getSharedPreferences("settings", Activity.MODE_PRIVATE).getString("bookmarks", null))

    fun save(activity: Activity, list: List<Bookmark>) =
        activity.getSharedPreferences("settings", Activity.MODE_PRIVATE).edit().putString("bookmarks", toJson(list)).apply()

    fun all(activity: Activity) = load(activity)

    /** Asks which folder (or a new one) and saves the page there. */
    fun pick(activity: Activity, title: String, url: String) {
        val folders = folders(load(activity))
        if (folders.isEmpty()) return newFolder(activity, title, url)
        AlertDialog.Builder(activity)
            .setTitle("Save to folder")
            .setItems((folders + "＋ New folder…").toTypedArray()) { _, which ->
                if (which == folders.size) newFolder(activity, title, url) else store(activity, folders[which], title, url)
            }
            .show()
    }

    private fun newFolder(activity: Activity, title: String, url: String) {
        val input = EditText(activity).apply { hint = "Folder name"; setSingleLine() }
        val box = FrameLayout(activity).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(activity)
            .setTitle("New folder")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) store(activity, name, title, url)
            }
            .setNegativeButton("Cancel", null)
            .show()
        input.requestFocus()
    }

    private fun store(activity: Activity, folder: String, title: String, url: String) {
        save(activity, add(load(activity), Bookmark(folder, title.ifBlank { url }, url)))
        Toast.makeText(activity, "Saved to $folder", Toast.LENGTH_SHORT).show()
    }
}
