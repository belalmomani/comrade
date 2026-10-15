package com.cuteghost.app

import android.graphics.Color

/** Shared settings for the ghost family (mother + babies). */
object Family {
    const val BABY_SCALE = 0.62f

    /** The number of babies is unlimited, but only this many are shown on screen at once. */
    const val MAX_VISIBLE_BABIES = 3

    /** Distinct body colours for the first babies (the original mother stays white). */
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
