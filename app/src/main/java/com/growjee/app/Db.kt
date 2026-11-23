package com.growjee.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Local copy of the student's data. Same JSON shape as the desktop app: { master: [], records: { daily: [], ... } }. */
class Db private constructor(private val ctx: Context) {
    private val file get() = File(ctx.filesDir, "progress.json")
    private val dirtyFile get() = File(ctx.filesDir, "progress.dirty")
    var root: JSONObject = load()

    /** True when local changes have not reached the cloud yet (so a cloud load must not overwrite them). */
    var dirty: Boolean
        get() = dirtyFile.exists()
        set(v) { if (v) dirtyFile.createNewFile() else dirtyFile.delete() }

    private fun load(): JSONObject = try {
        if (file.exists()) JSONObject(file.readText()) else fresh()
    } catch (e: Exception) {
        fresh()
    }

    private fun fresh(): JSONObject {
        val master = JSONArray()
        try {
            val chapters = JSONArray(ctx.assets.open("chapters.json").bufferedReader().use { it.readText() })
            for (i in 0 until chapters.length()) {
                val c = chapters.getJSONObject(i)
                master.put(JSONObject()
                    .put("id", "c$i")
                    .put("subject", c.str("subject"))
                    .put("chapter", c.str("chapter"))
                    .put("source", c.str("source"))
                    .put("status", "Not Started")
                    .put("confidence", "")
                    .put("mockMarks", "")
                    .put("mockDate", "")
                    .put("notes", ""))
            }
        } catch (_: Exception) { }
        val records = JSONObject()
        for (k in listOf("daily", "questions", "mocks", "mistakes", "revisions", "targets", "chapterMarks")) records.put(k, JSONArray())
        return JSONObject().put("master", master).put("records", records)
    }

    fun save() { file.writeText(root.toString(2)) }

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context): Db = inst ?: synchronized(this) { inst ?: Db(ctx.applicationContext).also { inst = it } }
    }

    fun master(): JSONArray = root.optJSONArray("master") ?: JSONArray().also { root.put("master", it) }

    fun records(key: String): JSONArray {
        val r = root.optJSONObject("records") ?: JSONObject().also { root.put("records", it) }
        return r.optJSONArray(key) ?: JSONArray().also { r.put(key, it) }
    }
}

fun JSONArray.indexOfId(id: String): Int {
    for (i in 0 until length()) if (optJSONObject(i)?.str("id") == id) return i
    return -1
}
