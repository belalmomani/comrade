package com.cuteghost.app

import android.content.Context

/** A grown-up ghost that lives in its own house in the village. */
class Villager(val colorIndex: Int, val houseId: Int, val label: String, val isMother: Boolean)

/** The village: the houses of the ghosts that grew up (the oldest first). Saved for good. */
object Village {
    private const val PREFS = "ghost_home"
    private const val KEY = "village"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(context: Context): List<Villager> {
        val raw = prefs(context).getString(KEY, "").orEmpty()
        val result = ArrayList<Villager>()
        for (part in raw.split(';')) {
            val f = part.split('|')
            if (f.size < 4) continue
            val color = f[0].toIntOrNull() ?: continue
            val house = f[1].toIntOrNull() ?: continue
            result.add(Villager(color, house, f[2], f[3] == "1"))
        }
        return result
    }

    /** Gives the ghost its own house (the six designs repeat one after the other). */
    fun add(context: Context, colorIndex: Int, label: String, isMother: Boolean) {
        val current = list(context)
        val clean = label.replace(';', ' ').replace('|', ' ')
        val entry = "$colorIndex|${current.size % HouseDefs.all.size}|$clean|${if (isMother) 1 else 0}"
        val raw = prefs(context).getString(KEY, "").orEmpty()
        prefs(context).edit().putString(KEY, if (raw.isEmpty()) entry else "$raw;$entry").apply()
    }
}
