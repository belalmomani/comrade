package com.cuteghost.app

import android.graphics.Color

/** Character of a baby: it decides how the baby reacts when it is touched. */
enum class Personality { JUMPER, GIGGLER, SHY, DANCER, CURIOUS }

/** Shared settings for the ghost family (the big ghost and the babies). */
object Family {
    /** Every hatched baby gets a different character (they repeat after five). */
    fun personalityFor(colorIndex: Int): Personality =
        Personality.values()[((colorIndex % 5) + 5) % 5]

    /** Size of a newborn baby compared with the big ghost (1.0). */
    const val NEWBORN_SCALE = 0.40f

    /** Every meal a baby eats makes it grow by this much of the big ghost's size (1%). */
    const val GROWTH_PER_MEAL = 0.01f

    /** Babies never grow bigger than this. */
    const val MAX_BABY_SCALE = 0.85f

    fun babyScale(meals: Int): Float =
        minOf(MAX_BABY_SCALE, NEWBORN_SCALE + GROWTH_PER_MEAL * meals.coerceAtLeast(0))

    /** Distinct body colours for the first babies (the original big ghost stays white). */
    val COLORS = intArrayOf(
        0xFF5CD6A9.toInt(), // mint
        0xFF62B6F5.toInt(), // sky blue
        0xFFFF9B6A.toInt(), // peach
        0xFFA98BFF.toInt(), // lavender
        0xFFFFD54A.toInt(), // sunny yellow
        0xFFFF8FB8.toInt(), // pink
        0xFFA8DB4B.toInt()  // lime
    )

    /** Every baby gets its own colour; after the palette ends new hues are generated. */
    fun color(index: Int): Int {
        if (index in COLORS.indices) return COLORS[index]
        val hue = (kotlin.math.abs(index) * 137.5f) % 360f
        return Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.95f))
    }

    fun sizePx(baseDp: Int, scale: Float, density: Float): Int = (baseDp * scale * density).toInt()
}
