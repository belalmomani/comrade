package com.cuteghost.app

import android.content.Context

/** Names of the children who play with the ghost, used in the encouraging phrases. */
object ChildNames {
    private const val PREFS = "ghost_prefs"
    private const val KEY = "child_names"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun raw(context: Context): String = prefs(context).getString(KEY, "").orEmpty()

    fun list(context: Context): List<String> =
        raw(context).split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }.take(6)

    fun save(context: Context, text: String) {
        prefs(context).edit().putString(KEY, text.trim()).apply()
    }
}
