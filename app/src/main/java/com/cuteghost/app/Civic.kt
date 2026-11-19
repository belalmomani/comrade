package com.cuteghost.app

import android.content.Context
import kotlin.random.Random

/**
 * The village's public buildings, built one by one with village coins. Each one has something to do:
 *  - the park: the little ghosts play there, once a day it gives a star;
 *  - the toy shop: once a day a new toy for the shelf, until the whole collection is there;
 *  - the school: it opens the small games;
 *  - the library: a counting picture book, the child counts the things on every page;
 *  - the hospital: the child cures a sick little ghost with a thermometer, medicine and a heart.
 */
object Civic {

    const val PARK = 0
    const val TOYS = 1
    const val SCHOOL = 2
    const val LIBRARY = 3
    const val HOSPITAL = 4
    const val COUNT = 5
    const val TOY_KINDS = 6

    private const val PREFS = "ghost_home"
    private const val KEY_BUILT = "civic_built"
    private const val KEY_PARK_DAY = "civic_park_day"
    private const val KEY_TOY_DAY = "civic_toy_day"
    private const val KEY_TOYS = "civic_toys"

    const val TOY_COINS = 5

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cost(id: Int): Int = when (id) {
        PARK -> 80
        TOYS -> 140
        SCHOOL -> 200
        LIBRARY -> 260
        else -> 320
    }

    fun built(context: Context, id: Int): Boolean =
        (prefs(context).getInt(KEY_BUILT, 0) and (1 shl id)) != 0

    /** Builds it if there are enough coins. Returns false when there are not. */
    fun build(context: Context, id: Int): Boolean {
        if (built(context, id)) return true
        if (!Village.spendCoins(context, cost(id))) return false
        val p = prefs(context)
        p.edit().putInt(KEY_BUILT, p.getInt(KEY_BUILT, 0) or (1 shl id)).apply()
        return true
    }

    /** The toys on the shop's shelf, one bit each. */
    fun toys(context: Context): Int = prefs(context).getInt(KEY_TOYS, 0)

    fun toyCount(context: Context): Int = Integer.bitCount(toys(context))

    /** A play in the park. Gives a star the first time each day. Returns true when it did. */
    fun playInPark(context: Context): Boolean {
        val p = prefs(context)
        val today = DailyGoals.dayNumber()
        if (p.getInt(KEY_PARK_DAY, -1) == today) return false
        p.edit().putInt(KEY_PARK_DAY, today).apply()
        Wardrobe.addStars(context, 1)
        return true
    }

    /**
     * The toy of the day. Returns the new toy (0 up to 5), or -1 when today's toy was already taken,
     * or -2 when the whole collection is on the shelf.
     */
    fun takeToy(context: Context): Int {
        val p = prefs(context)
        val today = DailyGoals.dayNumber()
        val have = p.getInt(KEY_TOYS, 0)
        if (Integer.bitCount(have) >= TOY_KINDS) return -2
        if (p.getInt(KEY_TOY_DAY, -1) == today) return -1
        val missing = ArrayList<Int>()
        for (i in 0 until TOY_KINDS) if ((have and (1 shl i)) == 0) missing.add(i)
        val pick = missing[Random.nextInt(missing.size)]
        p.edit().putInt(KEY_TOYS, have or (1 shl pick)).putInt(KEY_TOY_DAY, today).apply()
        Village.addCoins(context, TOY_COINS)
        return pick
    }
}
