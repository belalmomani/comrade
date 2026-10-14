package com.cuteghost.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs

class GhostOverlayService : Service() {

    companion object {
        @Volatile
        var isRunning = false
            private set

        private const val TAG = "GhostOverlay"
        private const val CHANNEL_ID = "ghost_overlay"
        private const val NOTIFICATION_ID = 1
        private const val PREFS = "ghost_prefs"
        private const val KEY_X = "pos_x"
        private const val KEY_Y = "pos_y"
        private const val GHOST_WIDTH_DP = 100
        private const val GHOST_HEIGHT_DP = 128
        private const val MARGIN_DP = 12
        private const val TAP_MAX_MS = 450L

        fun start(context: Context) {
            val intent = Intent(context, GhostOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GhostOverlayService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var ghostView: GhostView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var voice: VoiceCompanion? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ghostView == null && !showGhost()) {
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        savePosition()
        voice?.release()
        voice = null
        removeGhost()
        isRunning = false
        super.onDestroy()
    }

    // ---------- Foreground notification ----------

    private fun enterForeground(): Boolean {
        return try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIFICATION_ID, notification, type)
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_stat_ghost)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    // ---------- Floating window ----------

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showGhost(): Boolean {
        val wm = windowManager ?: return false

        val params = WindowManager.LayoutParams(
            dp(GHOST_WIDTH_DP),
            dp(GHOST_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        restorePosition(params)

        val view = GhostView(this)
        attachTouchHandling(view, params)

        return try {
            wm.addView(view, params)
            ghostView = view
            layoutParams = params
            voice = VoiceCompanion(applicationContext, object : VoiceCompanion.Ui {
                override fun onState(state: GhostView.VoiceState) {
                    ghostView?.setVoiceState(state)
                }

                override fun onLevel(level: Float) {
                    ghostView?.setVoiceLevel(level)
                }

                override fun onSad() {
                    ghostView?.showSad()
                }
            })
            true
        } catch (e: RuntimeException) {
            // BadTokenException / SecurityException when permission is missing or revoked
            Log.e(TAG, "Could not add overlay view", e)
            false
        }
    }

    private fun removeGhost() {
        val view = ghostView ?: return
        try {
            windowManager?.removeView(view)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay view was already removed", e)
        }
        ghostView = null
        layoutParams = null
    }

    private fun attachTouchHandling(view: GhostView, params: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var lastRawX = 0f
        var lastRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    view.onTouchStart()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        view.onDragStart()
                    }
                    if (dragging) {
                        val dm = resources.displayMetrics
                        params.x = (startX + dx).toInt()
                            .coerceIn(0, maxOf(0, dm.widthPixels - params.width))
                        params.y = (startY + dy).toInt()
                            .coerceIn(0, maxOf(0, dm.heightPixels - params.height))
                        updateGhostLayout(v, params)
                        view.onDragMove(event.rawX - lastRawX, event.rawY - lastRawY)
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        view.onDragEnd()
                        savePosition()
                    } else if (event.eventTime - event.downTime <= TAP_MAX_MS) {
                        // Only a short press without movement counts as a tap
                        voice?.onTap()
                        v.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        view.onDragEnd()
                        savePosition()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateGhostLayout(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not update overlay layout", e)
        }
    }

    // ---------- Saved position ----------

    private fun restorePosition(params: WindowManager.LayoutParams) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dm = resources.displayMetrics
        val maxX = maxOf(0, dm.widthPixels - params.width)
        val maxY = maxOf(0, dm.heightPixels - params.height)
        if (prefs.contains(KEY_X) && prefs.contains(KEY_Y)) {
            params.x = prefs.getInt(KEY_X, 0).coerceIn(0, maxX)
            params.y = prefs.getInt(KEY_Y, 0).coerceIn(0, maxY)
        } else {
            params.x = (maxX - dp(MARGIN_DP)).coerceAtLeast(0)
            params.y = (dm.heightPixels * 0.4f).toInt().coerceIn(0, maxY)
        }
    }

    private fun savePosition() {
        val params = layoutParams ?: return
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_X, params.x)
            .putInt(KEY_Y, params.y)
            .apply()
    }
}
