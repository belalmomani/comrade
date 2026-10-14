package com.cuteghost.app

/**
 * Single place for the AI service settings.
 * Keys are NOT stored in the code or repository: they are injected at build time from the
 * GitHub secret GHOST_AI_KEYS (one key, or several separated by commas).
 */
object AiConfig {
    const val MODEL = "claude-haiku-5-5"

    fun keys(): List<String> =
        BuildConfig.AI_KEYS.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun hasKeys(): Boolean = keys().isNotEmpty()
}
