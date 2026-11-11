package com.cuteghost.app

import android.content.Context

/** A grown-up ghost that lives in its own house in the village. */
class Villager(val colorIndex: Int, val houseId: Int, val label: String, val isMother: Boolean, val level: Int)

/** The village: the houses of the ghosts that grew up (the oldest first) and the restaurant's money. */
object Village {
    private const val PREFS = "ghost_home"
    private const val KEY = "village"
    private const val KEY_LEVELS = "village_levels"
    private const val KEY_COINS = "village_coins"
    private const val KEY_LAST_VISIT = "village_last_visit"
    const val MAX_LEVEL = 5

    private const val KEY_REST = "restaurant_level"

    /** 1 counter only, 2 dining tables, 3 a waiter, 4 a fruit and food stand with a bigger menu. */
    const val REST_MAX = 4

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun levels(context: Context): List<Int> =
        prefs(context).getString(KEY_LEVELS, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    fun list(context: Context): List<Villager> {
        val raw = prefs(context).getString(KEY, "").orEmpty()
        val lv = levels(context)
        val result = ArrayList<Villager>()
        for (part in raw.split(';')) {
            val f = part.split('|')
            if (f.size < 4) continue
            val color = f[0].toIntOrNull() ?: continue
            val house = f[1].toIntOrNull() ?: continue
            val level = lv.getOrElse(result.size) { 1 }.coerceIn(1, MAX_LEVEL)
            result.add(Villager(color, house, f[2], f[3] == "1", level))
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

    /** Raises the level of the village house number [index] (the restaurant's money pays for it). */
    fun setLevel(context: Context, index: Int, level: Int) {
        val count = list(context).size
        val lv = ArrayList<Int>()
        val old = levels(context)
        for (i in 0 until count) lv.add(old.getOrElse(i) { 1 })
        if (index in 0 until count) lv[index] = level.coerceIn(1, MAX_LEVEL)
        prefs(context).edit().putString(KEY_LEVELS, lv.joinToString(",")).apply()
    }

    /** Upgrading a village house from [level] costs this many coins. */
    fun upgradeCost(level: Int): Int = 15 * level

    fun restaurantLevel(context: Context): Int = prefs(context).getInt(KEY_REST, 1).coerceIn(1, REST_MAX)

    fun setRestaurantLevel(context: Context, level: Int) {
        prefs(context).edit().putInt(KEY_REST, level.coerceIn(1, REST_MAX)).apply()
    }

    /** What it costs to move the restaurant from [level] to the next one (high on purpose). */
    fun restaurantCost(level: Int): Int = when (level) {
        1 -> 150
        2 -> 400
        else -> 800
    }

    fun coins(context: Context): Int = prefs(context).getInt(KEY_COINS, 0)

    fun addCoins(context: Context, amount: Int) {
        prefs(context).edit().putInt(KEY_COINS, coins(context) + amount).apply()
    }

    fun spendCoins(context: Context, amount: Int): Boolean {
        val have = coins(context)
        if (have < amount) return false
        prefs(context).edit().putInt(KEY_COINS, have - amount).apply()
        return true
    }

    /** The restaurant keeps selling a little while nobody is looking; this tells when the village was last open. */
    fun lastVisit(context: Context): Long = prefs(context).getLong(KEY_LAST_VISIT, 0L)

    fun markVisit(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_VISIT, System.currentTimeMillis()).apply()
    }
}
