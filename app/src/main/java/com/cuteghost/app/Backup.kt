package com.cuteghost.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves the child's progress (names, stars, clothes, house, village, farm, goals, stickers) into one small
 * text file, and puts it back on this or another phone. Play time, the update notice and the ad
 * counters are deliberately left out.
 */
object Backup {

    private const val APP = "cute-ghost-backup"
    private const val FORMAT = 1
    private val FILES = arrayOf("ghost_prefs", "ghost_home", "ghost_farm", "ghost_goals")
    private val SKIPPED_KEYS = setOf("update_notified_code")

    class BadBackupException(message: String) : Exception(message)

    private fun prefs(context: Context, name: String) =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** The whole backup as text. */
    fun export(context: Context): String {
        val files = JSONObject()
        for (name in FILES) {
            val entries = JSONObject()
            for ((key, value) in prefs(context, name).all) {
                if (name == "ghost_prefs" && key in SKIPPED_KEYS) continue
                val item = JSONObject()
                when (value) {
                    is Boolean -> item.put("t", "b").put("v", value)
                    is Int -> item.put("t", "i").put("v", value)
                    is Long -> item.put("t", "l").put("v", value)
                    is Float -> item.put("t", "f").put("v", value.toDouble())
                    is String -> item.put("t", "s").put("v", value)
                    is Set<*> -> {
                        val list = JSONArray()
                        for (s in value) list.put(s.toString())
                        item.put("t", "ss").put("v", list)
                    }
                    else -> continue
                }
                entries.put(key, item)
            }
            files.put(name, entries)
        }
        return JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("files", files)
            .toString()
    }

    /** Replaces the saved progress with the backup. Returns how many values were restored. */
    fun restore(context: Context, text: String): Int {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BadBackupException("الملف ليس نسخة احتياطية")
        }
        if (root.optString("app") != APP) throw BadBackupException("الملف ليس نسخة احتياطية من الشبح اللطيف")
        if (root.optInt("format", 0) != FORMAT) throw BadBackupException("نسخة من إصدار غير مدعوم")
        val files = root.optJSONObject("files") ?: throw BadBackupException("النسخة فارغة")

        var restored = 0
        for (name in FILES) {
            val entries = files.optJSONObject(name) ?: continue
            val p = prefs(context, name)
            val edit = p.edit()
            for (key in p.all.keys) {
                if (name == "ghost_prefs" && key in SKIPPED_KEYS) continue
                edit.remove(key)
            }
            val keys = entries.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (name == "ghost_prefs" && key in SKIPPED_KEYS) continue
                val item = entries.optJSONObject(key) ?: continue
                when (item.optString("t")) {
                    "b" -> edit.putBoolean(key, item.getBoolean("v"))
                    "i" -> edit.putInt(key, item.getInt("v"))
                    "l" -> edit.putLong(key, item.getLong("v"))
                    "f" -> edit.putFloat(key, item.getDouble("v").toFloat())
                    "s" -> edit.putString(key, item.getString("v"))
                    "ss" -> {
                        val list = item.getJSONArray("v")
                        val set = HashSet<String>()
                        for (i in 0 until list.length()) set.add(list.getString(i))
                        edit.putStringSet(key, set)
                    }
                    else -> continue
                }
                restored++
            }
            edit.commit()
        }
        return restored
    }
}
