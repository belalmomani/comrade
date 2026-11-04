package com.cuteghost.app

import android.content.Context

/** A crop: how long it takes to grow (only while it is watered), how often it is thirsty, and what it pays. */
class Crop(val name: String, val growMs: Long, val waterEveryMs: Long, val coins: Int, val stars: Int)

/** What one plot looks like right now. */
class PlotState(val planted: Boolean, val progress: Float, val hydrated: Float, val ripe: Boolean) {
    /** Dry: it stopped growing and needs water. */
    val thirsty: Boolean get() = planted && !ripe && hydrated <= 0f
}

/**
 * The village farm: four plots (carrot, tomato, strawberry, sunflower). A plant grows only while it is
 * watered, also while the game is closed, and it never spoils. Harvesting pays coins and stars.
 */
object Farm {
    const val PLOTS = 4

    val crops: List<Crop> = listOf(
        Crop("جزر", 90_000L, 45_000L, 4, 1),
        Crop("طماطم", 150_000L, 60_000L, 7, 1),
        Crop("فراولة", 210_000L, 75_000L, 10, 1),
        Crop("دوّار الشمس", 300_000L, 90_000L, 15, 2)
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("ghost_farm", Context.MODE_PRIVATE)

    /** Brings the plot up to date (growth counts only up to the moment the water ran out) and reads it. */
    fun state(context: Context, i: Int, now: Long = System.currentTimeMillis()): PlotState {
        val p = prefs(context)
        if (!p.getBoolean("p${i}_on", false)) return PlotState(false, 0f, 0f, false)
        val crop = crops[i]
        var grow = p.getLong("p${i}_grow", 0L)
        val tick = p.getLong("p${i}_tick", now)
        val until = p.getLong("p${i}_until", 0L)
        if (grow < crop.growMs) {
            val end = minOf(now, until)
            if (end > tick) grow = minOf(crop.growMs, grow + (end - tick))
        }
        p.edit().putLong("p${i}_grow", grow).putLong("p${i}_tick", now).apply()
        val hydrated = ((until - now).toFloat() / crop.waterEveryMs).coerceIn(0f, 1f)
        return PlotState(true, grow.toFloat() / crop.growMs, hydrated, grow >= crop.growMs)
    }

    /** Plants the plot's crop with a first drink. */
    fun plant(context: Context, i: Int) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putBoolean("p${i}_on", true)
            .putLong("p${i}_grow", 0L)
            .putLong("p${i}_tick", now)
            .putLong("p${i}_until", now + crops[i].waterEveryMs)
            .apply()
    }

    /** Waters a planted, unripe plot that is below half full of water. Returns false when it was not needed. */
    fun water(context: Context, i: Int): Boolean {
        val s = state(context, i)
        if (!s.planted || s.ripe || s.hydrated > 0.5f) return false
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong("p${i}_until", now + crops[i].waterEveryMs).putLong("p${i}_tick", now).apply()
        return true
    }

    /** Picks the ripe crop (the plot becomes empty). Returns null when it was not ripe. */
    fun harvest(context: Context, i: Int): Crop? {
        val s = state(context, i)
        if (!s.ripe) return null
        prefs(context).edit().putBoolean("p${i}_on", false).apply()
        return crops[i]
    }
}
