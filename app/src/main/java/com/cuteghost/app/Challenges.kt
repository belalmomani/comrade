package com.cuteghost.app

import android.content.Context

/**
 * Optional daily challenges. A challenge is a suggestion and never a duty: ignoring it costs nothing. A daily
 * challenge can be claimed once a day. Its star comes out of the same daily limit as the stars of the games,
 * so nothing here can be farmed, and playing again after claiming is always allowed (it just gives no more).
 */
object Challenges {

    private const val PREFS = "ghost_goals"
    private const val KEY_CLAIMS = "claims"

    /** What a finished challenge gave. [claimed] is false when it had already been claimed today. */
    class Result(val claimed: Boolean, val stars: Int, val newSticker: Boolean)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dailyKey(id: String): String = id + "@" + DailyGoals.dayNumber()

    fun claimedToday(context: Context, id: String): Boolean =
        prefs(context).getStringSet(KEY_CLAIMS, emptySet())?.contains(dailyKey(id)) == true

    /** Remembers a claim, forgetting the ones of earlier days so the saved set stays small. */
    private fun remember(context: Context, key: String) {
        val p = prefs(context)
        val old = p.getStringSet(KEY_CLAIMS, emptySet()) ?: emptySet()
        val suffix = "@" + DailyGoals.dayNumber()
        val kept = HashSet<String>()
        for (k in old) if (!k.contains('@') || k.endsWith(suffix)) kept.add(k)
        kept.add(key)
        p.edit().putStringSet(KEY_CLAIMS, kept).apply()
    }

    /**
     * Finishes the daily challenge [id]: [stars] stars (inside the daily limit) and, if given, a sticker.
     * When the daily star limit has been reached nothing is claimed, so it can be claimed again tomorrow.
     */
    fun completeDaily(context: Context, id: String, stars: Int = 1, sticker: String? = null): Result {
        if (claimedToday(context, id)) return Result(false, 0, false)
        var given = 0
        for (i in 0 until stars) if (MiniGames.grantStar(context)) given++
        if (stars > 0 && given == 0) return Result(false, 0, false)
        remember(context, dailyKey(id))
        val isNew = sticker != null && Album.unlock(context, sticker)
        return Result(true, given, isNew)
    }
}
