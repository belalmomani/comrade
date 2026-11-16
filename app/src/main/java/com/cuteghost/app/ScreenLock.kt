package com.cuteghost.app

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Play time: for a number of minutes chosen by a parent, the phone stays on the child's home screen and
 * its games (see [ChildHomeActivity]). Off unless a parent starts it. It ends by itself when the time is
 * over, or when a parent ends it from the parents' panel (behind the multiplication question).
 */
object ScreenLock {

    const val MAX_MINUTES = 180
    private const val PREFS = "ghost_lock"
    private const val KEY_UNTIL = "until"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun until(context: Context): Long = prefs(context).getLong(KEY_UNTIL, 0L)

    fun isActive(context: Context): Boolean = until(context) > System.currentTimeMillis()

    fun minutesLeft(context: Context): Int {
        val left = until(context) - System.currentTimeMillis()
        return if (left <= 0L) 0 else ((left + 59_999L) / 60_000L).toInt()
    }

    /** Starts play time and brings the child's home screen to the front. */
    fun start(context: Context, minutes: Int) {
        val m = minutes.coerceIn(1, MAX_MINUTES)
        prefs(context).edit().putLong(KEY_UNTIL, System.currentTimeMillis() + m * 60_000L).apply()
        val intent = Intent(context, ScreenLockService::class.java).setAction(ScreenLockService.ACTION_LOCK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.startActivity(
            Intent(context, ChildHomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    fun stop(context: Context) {
        prefs(context).edit().putLong(KEY_UNTIL, 0L).apply()
        context.stopService(Intent(context, ScreenLockService::class.java))
    }
}
