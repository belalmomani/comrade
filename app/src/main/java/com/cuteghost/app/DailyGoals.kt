package com.cuteghost.app

import android.content.Context
import java.util.TimeZone

/**
 * Daily goals for the little ones: five simple things to do every day (feed, give water, bath, brush
 * teeth, look after the farm). Each one is shown as a picture. When all five are done the child gets
 * a sticker for the day and a small reward, once per day. Nothing is ever taken away: a missed day
 * only means no sticker that day.
 */
object DailyGoals {

    enum class Goal { FOOD, WATER, BATH, BRUSH, FARM }

    /** What a finished day gives. */
    const val REWARD_STARS = 3
    const val REWARD_MATERIALS = 2

    /** The result of finishing the last goal of the day. */
    class Completion(val unlocked: List<Wardrobe.Item>, val streak: Int)

    private const val PREFS = "ghost_goals"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DAY = "day"
    private const val KEY_MASK = "mask"
    private const val KEY_STICKERS = "stickers"
    private const val KEY_STREAK = "streak"
    private const val KEY_LAST_COMPLETE = "last_complete"

    private val ALL_MASK = (1 shl Goal.values().size) - 1

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun dayNumber(): Int {
        val now = System.currentTimeMillis()
        return ((now + TimeZone.getDefault().getOffset(now)) / 86_400_000L).toInt()
    }

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    private fun todayMask(context: Context): Int {
        val p = prefs(context)
        return if (p.getInt(KEY_DAY, -1) == dayNumber()) p.getInt(KEY_MASK, 0) else 0
    }

    fun isDone(context: Context, goal: Goal): Boolean =
        (todayMask(context) and (1 shl goal.ordinal)) != 0

    fun doneCount(context: Context): Int {
        val mask = todayMask(context)
        var n = 0
        for (g in Goal.values()) if ((mask and (1 shl g.ordinal)) != 0) n++
        return n
    }

    fun total(): Int = Goal.values().size

    /** Stickers earned so far (one per fully finished day). */
    fun stickers(context: Context): Int = prefs(context).getInt(KEY_STICKERS, 0)

    /** Finished days in a row, counting today if it is finished. Zero when yesterday was missed. */
    fun streak(context: Context): Int {
        val p = prefs(context)
        val last = p.getInt(KEY_LAST_COMPLETE, -1000)
        val today = dayNumber()
        return if (last == today || last == today - 1) p.getInt(KEY_STREAK, 0) else 0
    }

    /**
     * Marks [goal] as done for today. Returns a [Completion] only when this was the last missing goal
     * of the day (the sticker and the reward are given then, once); otherwise null.
     */
    fun record(context: Context, goal: Goal): Completion? {
        if (!enabled(context)) return null
        val p = prefs(context)
        val today = dayNumber()
        val old = if (p.getInt(KEY_DAY, -1) == today) p.getInt(KEY_MASK, 0) else 0
        val bit = 1 shl goal.ordinal
        if ((old and bit) != 0) return null
        val mask = old or bit
        if (mask != ALL_MASK) {
            p.edit().putInt(KEY_DAY, today).putInt(KEY_MASK, mask).apply()
            return null
        }

        // The last goal of the day: one sticker, the streak and the reward
        val last = p.getInt(KEY_LAST_COMPLETE, -1000)
        val streak = if (last == today - 1) p.getInt(KEY_STREAK, 0) + 1 else 1
        p.edit()
            .putInt(KEY_DAY, today)
            .putInt(KEY_MASK, mask)
            .putInt(KEY_STICKERS, p.getInt(KEY_STICKERS, 0) + 1)
            .putInt(KEY_STREAK, streak)
            .putInt(KEY_LAST_COMPLETE, today)
            .apply()
        HomeStore.addMaterials(context, REWARD_MATERIALS)
        val unlocked = Wardrobe.addStars(context, REWARD_STARS)
        return Completion(unlocked, streak)
    }
}
