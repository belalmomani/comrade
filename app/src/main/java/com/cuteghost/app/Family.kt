package com.cuteghost.app

/** Shared settings for the ghost family (mother + babies). */
object Family {
    const val BABY_SCALE = 0.62f
    const val MAX_BABIES = 5

    /** One distinct body colour per baby (the mother stays white). */
    val COLORS = intArrayOf(
        0xFF5CD6A9.toInt(), // mint
        0xFF62B6F5.toInt(), // sky blue
        0xFFFF9B6A.toInt(), // peach
        0xFFA98BFF.toInt(), // lavender
        0xFFFFD54A.toInt(), // sunny yellow
        0xFFFF8FB8.toInt(), // pink
        0xFFA8DB4B.toInt()  // lime
    )

    fun color(index: Int): Int = COLORS[((index % COLORS.size) + COLORS.size) % COLORS.size]

    fun sizePx(baseDp: Int, scale: Float, density: Float): Int = (baseDp * scale * density).toInt()
}
