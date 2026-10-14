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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.abs

class GhostOverlayService : Service(), FeedView.Listener {

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
        private const val KEY_NEXT_HUNGER = "next_hunger_at"
        private const val GHOST_WIDTH_DP = 100
        private const val GHOST_HEIGHT_DP = 128
        private const val MARGIN_DP = 12
        private const val TAP_MAX_MS = 450L

        // Feeding
        private const val HUNGER_INTERVAL_MS = 60L * 60L * 1000L
        private const val HUNGER_CHECK_MS = 30_000L
        private const val FIRST_CHECK_DELAY_MS = 20_000L
        private const val FEED_TIMEOUT_MS = 3L * 60L * 1000L
        private const val FEED_WINDOW_WIDTH_DP = 240
        private const val BUBBLE_HEIGHT_DP = 44
        private const val TRAY_HEIGHT_DP = 90
        private const val ACTION_FEED_NOW = "com.companion.theghost.FEED_NOW"

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

        /** Asks the running ghost to be hungry right now (used for testing). */
        fun feedNow(context: Context) {
            val intent = Intent(context, GhostOverlayService::class.java).setAction(ACTION_FEED_NOW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var ghostView: GhostView? = null
    private var feedView: FeedView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var voice: VoiceCompanion? = null

    private var feeding = false
    private var userDragging = false
    private var origX = 0
    private var origY = 0

    private val hungerCheck = object : Runnable {
        override fun run() {
            checkHunger()
            handler.postDelayed(this, HUNGER_CHECK_MS)
        }
    }

    private val feedTimeout = Runnable { endFeeding() }

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
        if (rootView == null && !showGhost()) {
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        if (intent?.action == ACTION_FEED_NOW) startFeeding()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (feeding) endFeeding()
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
                if (Features.VOICE_ENABLED &&
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
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

        val ghost = GhostView(this)
        val feed = FeedView(this).apply {
            this.ghost = ghost
            listener = this@GhostOverlayService
            visibility = View.GONE
        }
        val root = FrameLayout(this).apply {
            addView(
                ghost,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                feed,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        attachTouchHandling(ghost, params)

        return try {
            wm.addView(root, params)
            rootView = root
            ghostView = ghost
            feedView = feed
            layoutParams = params
            if (Features.VOICE_ENABLED) {
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
            }
            handler.removeCallbacks(hungerCheck)
            handler.postDelayed(hungerCheck, FIRST_CHECK_DELAY_MS)
            true
        } catch (e: RuntimeException) {
            // BadTokenException / SecurityException when permission is missing or revoked
            Log.e(TAG, "Could not add overlay view", e)
            false
        }
    }

    private fun removeGhost() {
        val root = rootView ?: return
        try {
            windowManager?.removeView(root)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay view was already removed", e)
        }
        rootView = null
        ghostView = null
        feedView = null
        layoutParams = null
    }

    private fun updateWindow() {
        val root = rootView ?: return
        val params = layoutParams ?: return
        try {
            windowManager?.updateViewLayout(root, params)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not update overlay layout", e)
        }
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
                    // The ghost cannot be moved while the feeding game is open
                    if (!feeding) {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                            dragging = true
                            userDragging = true
                            view.onDragStart()
                        }
                        if (dragging) {
                            val dm = resources.displayMetrics
                            params.x = (startX + dx).toInt()
                                .coerceIn(0, maxOf(0, dm.widthPixels - params.width))
                            params.y = (startY + dy).toInt()
                                .coerceIn(0, maxOf(0, dm.heightPixels - params.height))
                            updateWindow()
                            view.onDragMove(event.rawX - lastRawX, event.rawY - lastRawY)
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        dragging = false
                        userDragging = false
                        view.onDragEnd()
                        savePosition()
                    } else if (event.eventTime - event.downTime <= TAP_MAX_MS) {
                        // Only a short press without movement counts as a tap
                        val companion = voice
                        if (Features.VOICE_ENABLED && companion != null) companion.onTap() else view.react()
                        v.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        userDragging = false
                        view.onDragEnd()
                        savePosition()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ---------- Feeding ----------

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun checkHunger() {
        if (feeding || userDragging || rootView == null) return
        val now = System.currentTimeMillis()
        val next = prefs().getLong(KEY_NEXT_HUNGER, 0L)
        if (next == 0L) {
            prefs().edit().putLong(KEY_NEXT_HUNGER, now + HUNGER_INTERVAL_MS).apply()
            return
        }
        if (now >= next) startFeeding()
    }

    /** Expands the window around the ghost (bubble above, food tray below). */
    private fun startFeeding() {
        val params = layoutParams ?: return
        val ghost = ghostView ?: return
        val feed = feedView ?: return
        if (feeding || userDragging) return

        val dm = resources.displayMetrics
        val ghostW = dp(GHOST_WIDTH_DP)
        val ghostH = dp(GHOST_HEIGHT_DP)
        val winW = dp(FEED_WINDOW_WIDTH_DP)
        val bubbleH = dp(BUBBLE_HEIGHT_DP)
        val trayH = dp(TRAY_HEIGHT_DP)
        val winH = bubbleH + ghostH + trayH

        origX = params.x
        origY = params.y
        val wx = (origX - (winW - ghostW) / 2).coerceIn(0, maxOf(0, dm.widthPixels - winW))
        val wy = (origY - bubbleH).coerceIn(0, maxOf(0, dm.heightPixels - winH))

        params.x = wx
        params.y = wy
        params.width = winW
        params.height = winH
        ghost.layoutParams = FrameLayout.LayoutParams(ghostW, ghostH, Gravity.TOP or Gravity.START).apply {
            leftMargin = (origX - wx).coerceIn(0, maxOf(0, winW - ghostW))
            topMargin = bubbleH
        }
        updateWindow()

        feeding = true
        feed.visibility = View.VISIBLE
        feed.begin(winW, winH, bubbleH, trayH)
        ghost.setFeedState(GhostView.FeedState.HUNGRY)
        handler.removeCallbacks(feedTimeout)
        handler.postDelayed(feedTimeout, FEED_TIMEOUT_MS)
    }

    override fun onFeedingEnded(fed: Boolean) {
        endFeeding()
    }

    private fun endFeeding() {
        if (!feeding) return
        feeding = false
        handler.removeCallbacks(feedTimeout)

        feedView?.let {
            it.end()
            it.visibility = View.GONE
        }
        ghostView?.setFeedState(GhostView.FeedState.NONE)

        val params = layoutParams
        if (params != null) {
            params.x = origX
            params.y = origY
            params.width = dp(GHOST_WIDTH_DP)
            params.height = dp(GHOST_HEIGHT_DP)
            ghostView?.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            updateWindow()
        }
        prefs().edit()
            .putLong(KEY_NEXT_HUNGER, System.currentTimeMillis() + HUNGER_INTERVAL_MS)
            .apply()
    }

    // ---------- Saved position ----------

    private fun restorePosition(params: WindowManager.LayoutParams) {
        val prefs = prefs()
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
        if (feeding) return
        val params = layoutParams ?: return
        prefs().edit()
            .putInt(KEY_X, params.x)
            .putInt(KEY_Y, params.y)
            .apply()
    }
}
