package com.cuteghost.app

import android.content.Context

/** Parent-controlled settings. The API key lives only in the app's private storage. */
object ChatSettings {
    private const val PREFS = "ghost_prefs"
    private const val KEY_API = "api_key"
    private const val KEY_MODEL = "model"
    const val DEFAULT_MODEL = "claude-haiku-5-5"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun apiKey(context: Context): String =
        prefs(context).getString(KEY_API, "")?.trim().orEmpty()

    fun hasKey(context: Context): Boolean = apiKey(context).isNotEmpty()

    fun model(context: Context): String {
        val saved = prefs(context).getString(KEY_MODEL, null)?.trim()
        return if (saved.isNullOrEmpty()) DEFAULT_MODEL else saved
    }

    /** A blank [key] keeps the existing key. */
    fun save(context: Context, key: String, model: String) {
        val editor = prefs(context).edit()
        if (key.isNotBlank()) editor.putString(KEY_API, key.trim())
        editor.putString(KEY_MODEL, model.trim())
        editor.apply()
    }

    fun clearKey(context: Context) {
        prefs(context).edit().remove(KEY_API).apply()
    }
}
