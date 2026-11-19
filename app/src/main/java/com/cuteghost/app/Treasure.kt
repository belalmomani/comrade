package com.cuteghost.app

import android.content.Context
import kotlin.random.Random

/**
 * The day's hidden treasure in the village: one a day, in a different place each day. It glitters now
 * and then behind a bush; the child finds it by touching it. It gives stars, village coins and a brick
 * of building material, all of which go into upgrades.
 */
object Treasure {

    const val REWARD_STARS = 2
    const val REWARD_COINS = 12
    const val REWARD_MATERIALS = 1

    /** The three kinds of treasure: a chest, a blue gem and a golden star. */
    const val KINDS = 3

    private const val PREFS = "ghost_goals"
    private const val KEY_FOUND_DAY = "treasure_found_day"
    private const val KEY_TOTAL = "treasure_total"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun foundToday(context: Context): Boolean =
        prefs(context).getInt(KEY_FOUND_DAY, -1) == DailyGoals.dayNumber()

    /** How many treasures were found in all. */
    fun total(context: Context): Int = prefs(context).getInt(KEY_TOTAL, 0)

    /** Today's place along the village, between [minDp] and [maxDp] (the same place all day long). */
    fun spotDp(minDp: Float, maxDp: Float): Float {
        val r = Random(DailyGoals.dayNumber() * 7919L + 17L)
        return minDp + r.nextFloat() * (maxDp - minDp)
    }

    fun kind(): Int = Random(DailyGoals.dayNumber() * 104729L + 5L).nextInt(KINDS)

    /** The treasure was found: marks it, and gives the reward. Returns the wardrobe items it unlocked. */
    fun find(context: Context): List<Wardrobe.Item> {
        if (foundToday(context)) return emptyList()
        val p = prefs(context)
        p.edit()
            .putInt(KEY_FOUND_DAY, DailyGoals.dayNumber())
            .putInt(KEY_TOTAL, p.getInt(KEY_TOTAL, 0) + 1)
            .apply()
        Village.addCoins(context, REWARD_COINS)
        HomeStore.addMaterials(context, REWARD_MATERIALS)
        return Wardrobe.addStars(context, REWARD_STARS)
    }
}
