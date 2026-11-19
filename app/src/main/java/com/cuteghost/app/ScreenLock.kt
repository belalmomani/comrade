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
    private const val KEY_TOTAL = "total"
    private const val KEY_SCHED_ON = "sched_on"
    private const val KEY_SCHED_START = "sched_start"
    private const val KEY_SCHED_LEN = "sched_len"
    private const val KEY_SCHED_DAY = "sched_day"

    /** How many minutes after the chosen time play time may still start (the phone may have been off). */
    private const val SCHED_GRACE_MIN = 10

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun until(context: Context): Long = prefs(context).getLong(KEY_UNTIL, 0L)

    fun isActive(context: Context): Boolean = until(context) > System.currentTimeMillis()

    fun minutesLeft(context: Context): Int {
        val left = until(context) - System.currentTimeMillis()
        return if (left <= 0L) 0 else ((left + 59_999L) / 60_000L).toInt()
    }

    // ---------- The daily schedule (it starts by itself while the ghost is on the screen) ----------

    fun scheduleEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_SCHED_ON, false)

    /** The start, as minutes after midnight (default 4:00 pm). */
    fun scheduleStart(context: Context): Int = prefs(context).getInt(KEY_SCHED_START, 16 * 60)

    fun scheduleLength(context: Context): Int = prefs(context).getInt(KEY_SCHED_LEN, 30)

    fun setSchedule(context: Context, enabled: Boolean, startMinutes: Int, lengthMinutes: Int) {
        prefs(context).edit()
            .putBoolean(KEY_SCHED_ON, enabled)
            .putInt(KEY_SCHED_START, startMinutes.coerceIn(0, 24 * 60 - 1))
            .putInt(KEY_SCHED_LEN, lengthMinutes.coerceIn(1, MAX_MINUTES))
            .apply()
    }

    /** True when it is time for today's scheduled play time and it has not started yet today. */
    fun scheduleDue(context: Context): Boolean {
        if (!scheduleEnabled(context) || isActive(context)) return false
        val p = prefs(context)
        val today = DailyGoals.dayNumber()
        if (p.getInt(KEY_SCHED_DAY, -1) == today) return false
        val cal = java.util.Calendar.getInstance()
        val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val start = scheduleStart(context)
        return now >= start && now < start + SCHED_GRACE_MIN
    }

    fun markScheduleStarted(context: Context) {
        prefs(context).edit().putInt(KEY_SCHED_DAY, DailyGoals.dayNumber()).apply()
    }

    /** 1 at the start of play time, down to 0 when it ends (1 when there is no play time). */
    fun fractionLeft(context: Context): Float {
        val total = prefs(context).getLong(KEY_TOTAL, 0L)
        if (total <= 0L) return 1f
        val left = until(context) - System.currentTimeMillis()
        return (left.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /** Starts play time and brings the child's home screen to the front. */
    fun start(context: Context, minutes: Int) {
        val m = minutes.coerceIn(1, MAX_MINUTES)
        prefs(context).edit()
            .putLong(KEY_UNTIL, System.currentTimeMillis() + m * 60_000L)
            .putLong(KEY_TOTAL, m * 60_000L)
            .apply()
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
        prefs(context).edit().putLong(KEY_UNTIL, 0L).putLong(KEY_TOTAL, 0L).apply()
        context.stopService(Intent(context, ScreenLockService::class.java))
    }
}
