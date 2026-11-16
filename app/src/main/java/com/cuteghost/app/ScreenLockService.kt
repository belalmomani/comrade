package com.cuteghost.app

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.lang.ref.WeakReference

/**
 * The guard of play time. It does not draw anything the child sees. It:
 *  - brings the child's home screen back if the child manages to leave the app (home button, recents...);
 *  - while the app is not pinned, covers the status bar and the navigation bar with invisible strips
 *    that swallow touches, so the notification shade and the system buttons cannot be used;
 *  - lets go of everything (strips, screen pinning) when the time is over or a parent ends it.
 * Incoming calls are left alone: while the phone rings or is in a call nothing is blocked or relaunched.
 */
class ScreenLockService : Service() {

    companion object {
        private const val TAG = "ScreenLockService"
        private const val CHANNEL_ID = "ghost_play_time"
        private const val NOTIFICATION_ID = 3
        private const val GONE_LIMIT_MS = 1_500L
        const val ACTION_LOCK = "com.companion.theghost.PLAY_TIME_START"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var topStrip: View? = null
    private var bottomStrip: View? = null

    private var startedCount = 0
    private var goneSince = 0L
    private var topActivity: WeakReference<Activity>? = null
    private var callbacksSet = false

    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {
            startedCount++
        }
        override fun onActivityResumed(activity: Activity) {
            topActivity = WeakReference(activity)
        }
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {
            if (startedCount > 0) startedCount--
        }
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!ScreenLock.isActive(this@ScreenLockService)) {
                finishLock()
                return
            }
            guard()
            handler.postDelayed(this, 700L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ScreenLock.isActive(this)) {
            finishLock()
            return START_NOT_STICKY
        }
        if (!callbacksSet) {
            application.registerActivityLifecycleCallbacks(callbacks)
            callbacksSet = true
            goneSince = System.currentTimeMillis()
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeStrips()
        unpin()
        if (callbacksSet) {
            application.unregisterActivityLifecycleCallbacks(callbacks)
            callbacksSet = false
        }
        super.onDestroy()
    }

    private fun finishLock() {
        handler.removeCallbacksAndMessages(null)
        ScreenLock.stop(this)
        stopSelf()
    }

    // ---------- Guarding ----------

    private fun guard() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val busy = audio.mode != AudioManager.MODE_NORMAL
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pinned = am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        if (busy || pinned) {
            removeStrips()
        } else if (Settings.canDrawOverlays(this)) {
            ensureStrips()
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (startedCount > 0 || busy || !pm.isInteractive) {
            goneSince = 0L
            return
        }
        val now = System.currentTimeMillis()
        if (goneSince == 0L) {
            goneSince = now
        } else if (now - goneSince > GONE_LIMIT_MS) {
            relaunch()
            goneSince = now
        }
    }

    private fun relaunch() {
        try {
            startActivity(
                Intent(this, ChildHomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not bring the child's home screen back", e)
        }
    }

    /** Lets the child's task go when play time ends (any of our screens may be the one in front). */
    private fun unpin() {
        val a = topActivity?.get() ?: return
        try {
            a.stopLockTask()
        } catch (e: RuntimeException) {
            // it was not pinned
        }
    }

    private fun systemBarHeight(name: String, fallbackDp: Int): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        val size = if (id > 0) resources.getDimensionPixelSize(id) else 0
        return if (size > 0) size else (fallbackDp * resources.displayMetrics.density).toInt()
    }

    private fun ensureStrips() {
        if (topStrip != null && bottomStrip != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        if (topStrip == null) topStrip = addStrip(wm, Gravity.TOP, systemBarHeight("status_bar_height", 26))
        if (bottomStrip == null) bottomStrip = addStrip(wm, Gravity.BOTTOM, systemBarHeight("navigation_bar_height", 48))
    }

    @Suppress("DEPRECATION")
    private fun addStrip(wm: WindowManager, gravity: Int, heightPx: Int): View? {
        val v = View(this)
        v.setOnTouchListener { _, _ -> true }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        )
        p.gravity = gravity
        return try {
            wm.addView(v, p)
            v
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not add a guard strip", e)
            null
        }
    }

    private fun removeStrips() {
        val wm = windowManager
        val t = topStrip
        val b = bottomStrip
        topStrip = null
        bottomStrip = null
        if (wm == null) return
        if (t != null) {
            try {
                wm.removeView(t)
            } catch (e: RuntimeException) {
                // already gone
            }
        }
        if (b != null) {
            try {
                wm.removeView(b)
            } catch (e: RuntimeException) {
                // already gone
            }
        }
    }

    // ---------- Foreground notification ----------

    private fun enterForeground(): Boolean {
        return try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not start foreground service", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "وقت اللعب", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_stat_ghost)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("وقت اللعب مفعّل")
            .setOngoing(true)
            .build()
    }
}
