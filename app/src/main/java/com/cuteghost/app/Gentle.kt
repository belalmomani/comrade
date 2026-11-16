package com.cuteghost.app

import android.content.Context

/**
 * Gentle mode (on by default, for very small children): a ghost that is not cared for only gets a tear
 * and then cries until someone looks after it. It never gets sick and never disappears.
 */
object Gentle {
    private const val KEY = "gentle_mode"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("ghost_prefs", Context.MODE_PRIVATE)

    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY, true)

    fun set(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY, on).apply()
    }
}
