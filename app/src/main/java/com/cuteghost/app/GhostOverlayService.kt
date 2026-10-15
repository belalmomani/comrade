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
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.WindowManager

/**
 * Coordinates the ghost family: the mother, her babies and the egg. Each ghost lives in its own
 * [GhostWindow]; this service decides who may act so that movements never overlap.
 */
class GhostOverlayService : Service(), GhostWindow.Host, EggWindow.Host {

    companion object {
        @Volatile
        var isRunning = false
            private set

        private const val TAG = "GhostOverlay"
        private const val CHANNEL_ID = "ghost_overlay"
        private const val NOTIFICATION_ID = 1
        private const val PREFS = "ghost_prefs"

        // Saved state (the first four keys are the original mother keys)
        private const val KEY_X = "pos_x"
        private const val KEY_Y = "pos_y"
        private const val KEY_NEXT_HUNGER = "next_hunger_at"
        private const val KEY_NEXT_THIRST = "next_thirst_at"
        private const val KEY_BABY_COUNT = "baby_count"
        private const val KEY_FED_FLAG = "fed_since_egg"
        private const val KEY_WATER_FLAG = "watered_since_egg"
        private const val KEY_EGG_ACTIVE = "egg_active"
        private const val KEY_EGG_START = "egg_start"
        private const val KEY_EGG_DUR = "egg_duration"
        private const val KEY_EGG_X = "egg_x"
        private const val KEY_EGG_Y = "egg_y"

        private const val MARGIN_DP = 12
        private const val LOOP_MS = 30_000L
        private const val FIRST_LOOP_DELAY_MS = 20_000L
        private const val SESSION_COOLDOWN_MS = 15_000L
        private const val CELEBRATION_LOCK_MS = 2_600L
        private const val EGG_INCUBATION_MS = 3L * 60L * 1000L
        private const val EGG_TEST_MS = 20_000L
        private const val BABY_FIRST_WATER_MS = 2L * 60L * 1000L
        private const val BABY_FIRST_FOOD_MS = 4L * 60L * 1000L
        private const val UPDATE_FIRST_DELAY_MS = 2L * 60L * 1000L
        private const val UPDATE_CHECK_MS = 12L * 60L * 60L * 1000L

        private const val ACTION_FEED_NOW = "com.companion.theghost.FEED_NOW"
        private const val ACTION_EGG_NOW = "com.companion.theghost.EGG_NOW"
        private const val EXTRA_WATER = "water"

        fun start(context: Context) {
            startService(context, Intent(context, GhostOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GhostOverlayService::class.java))
        }

        /** Asks the mother to be hungry or thirsty right now (used for testing). */
        fun needNow(context: Context, water: Boolean) {
            startService(
                context,
                Intent(context, GhostOverlayService::class.java)
                    .setAction(ACTION_FEED_NOW)
                    .putExtra(EXTRA_WATER, water)
            )
        }

        /** Lays an egg right now that hatches after 20 seconds (used for testing). */
        fun eggNow(context: Context) {
            startService(
                context,
                Intent(context, GhostOverlayService::class.java).setAction(ACTION_EGG_NOW)
            )
        }

        private fun startService(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var mother: GhostWindow? = null
    private val babies = ArrayList<GhostWindow>()
    private var egg: EggWindow? = null
    private var voice: VoiceCompanion? = null

    private var activeSession: GhostWindow? = null
    private var reactLockUntil = 0L
    private var lastSessionEndWall = 0L
    private var fedFlag = false
    private var waterFlag = false

    private val loop = object : Runnable {
        override fun run() {
            checkNeeds()
            maybeLayEgg()
            handler.postDelayed(this, LOOP_MS)
        }
    }

    private val updateLoop = object : Runnable {
        override fun run() {
            checkForUpdate()
            handler.postDelayed(this, UPDATE_CHECK_MS)
        }
    }

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
        if (mother == null && !showAll()) {
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true

        val action = intent?.action
        if (action == ACTION_FEED_NOW) {
            val water = intent?.getBooleanExtra(EXTRA_WATER, false) == true
            val m = mother
            if (m != null) {
                beginSession(m, if (water) FeedView.Mode.WATER else FeedView.Mode.FOOD)
            }
        } else if (action == ACTION_EGG_NOW) {
            layEgg(EGG_TEST_MS)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        persist()
        voice?.release()
        voice = null
        egg?.remove()
        egg = null
        for (b in babies) b.remove()
        babies.clear()
        mother?.remove()
        mother = null
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

    // ---------- Building and restoring the family ----------

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun allWindows(): List<GhostWindow> {
        val list = ArrayList<GhostWindow>()
        mother?.let { list.add(it) }
        list.addAll(babies)
        return list
    }

    private fun showAll(): Boolean {
        val wm = windowManager ?: return false
        val p = prefs()

        val m = GhostWindow(this, wm, "mother", 1f, null, this)
        m.nextFoodAt = p.getLong(KEY_NEXT_HUNGER, 0L)
        m.nextWaterAt = p.getLong(KEY_NEXT_THIRST, 0L)
        val mp = savedMotherPosition(m)
        if (!m.show(mp.x, mp.y)) return false
        mother = m

        if (Features.VOICE_ENABLED) {
            voice = VoiceCompanion(applicationContext, object : VoiceCompanion.Ui {
                override fun onState(state: GhostView.VoiceState) {
                    mother?.ghost?.setVoiceState(state)
                }

                override fun onLevel(level: Float) {
                    mother?.ghost?.setVoiceLevel(level)
                }

                override fun onSad() {
                    mother?.ghost?.showSad()
                }
            })
        }

        // Babies keep their colour, position and needs between sessions
        val count = p.getInt(KEY_BABY_COUNT, 0).coerceIn(0, Family.MAX_BABIES)
        for (n in 1..count) {
            val b = GhostWindow(this, wm, "baby_$n", Family.BABY_SCALE, Family.color(n - 1), this)
            b.nextFoodAt = p.getLong("b${n}_food", 0L)
            b.nextWaterAt = p.getLong("b${n}_water", 0L)
            val spot = if (p.contains("b${n}_x") && p.contains("b${n}_y")) {
                Point(p.getInt("b${n}_x", 0), p.getInt("b${n}_y", 0))
            } else {
                findFreeSpot(b.widthPx, b.heightPx, m.rect())
            }
            if (b.show(spot.x, spot.y)) babies.add(b)
        }

        fedFlag = p.getBoolean(KEY_FED_FLAG, false)
        waterFlag = p.getBoolean(KEY_WATER_FLAG, false)

        if (p.getBoolean(KEY_EGG_ACTIVE, false)) {
            val e = EggWindow(
                this, wm, babies.size,
                p.getLong(KEY_EGG_START, System.currentTimeMillis()),
                p.getLong(KEY_EGG_DUR, EGG_INCUBATION_MS),
                p.getInt(KEY_EGG_X, 0), p.getInt(KEY_EGG_Y, 0),
                false, this
            )
            if (e.show()) egg = e
        }

        handler.removeCallbacks(loop)
        handler.postDelayed(loop, FIRST_LOOP_DELAY_MS)
        handler.removeCallbacks(updateLoop)
        handler.postDelayed(updateLoop, UPDATE_FIRST_DELAY_MS)
        return true
    }

    private fun savedMotherPosition(m: GhostWindow): Point {
        val p = prefs()
        val dm = resources.displayMetrics
        val maxX = maxOf(0, dm.widthPixels - m.widthPx)
        val maxY = maxOf(0, dm.heightPixels - m.heightPx)
        return if (p.contains(KEY_X) && p.contains(KEY_Y)) {
            Point(p.getInt(KEY_X, 0).coerceIn(0, maxX), p.getInt(KEY_Y, 0).coerceIn(0, maxY))
        } else {
            Point(
                (maxX - dp(MARGIN_DP)).coerceAtLeast(0),
                (dm.heightPixels * 0.4f).toInt().coerceIn(0, maxY)
            )
        }
    }

    private fun persist() {
        val e = prefs().edit()
        mother?.let {
            e.putInt(KEY_X, it.restX)
            e.putInt(KEY_Y, it.restY)
            e.putLong(KEY_NEXT_HUNGER, it.nextFoodAt)
            e.putLong(KEY_NEXT_THIRST, it.nextWaterAt)
        }
        e.putInt(KEY_BABY_COUNT, babies.size)
        for ((i, b) in babies.withIndex()) {
            val n = i + 1
            e.putInt("b${n}_x", b.restX)
            e.putInt("b${n}_y", b.restY)
            e.putLong("b${n}_food", b.nextFoodAt)
            e.putLong("b${n}_water", b.nextWaterAt)
        }
        e.putBoolean(KEY_FED_FLAG, fedFlag)
        e.putBoolean(KEY_WATER_FLAG, waterFlag)
        val eg = egg
        e.putBoolean(KEY_EGG_ACTIVE, eg != null)
        if (eg != null) {
            e.putLong(KEY_EGG_START, eg.startMs)
            e.putLong(KEY_EGG_DUR, eg.durationMs)
            e.putInt(KEY_EGG_X, eg.x)
            e.putInt(KEY_EGG_Y, eg.y)
        }
        e.apply()
    }

    // ---------- GhostWindow.Host ----------

    override fun canDrag(who: GhostWindow): Boolean = activeSession == null

    override fun onTap(who: GhostWindow) {
        if (activeSession != null) return
        val companion = voice
        if (Features.VOICE_ENABLED && companion != null && who === mother) {
            companion.onTap()
            return
        }
        // Only one ghost reacts at a time
        val now = SystemClock.uptimeMillis()
        if (now < reactLockUntil) return
        reactLockUntil = now + 1300L
        who.ghost.react()
    }

    override fun onMoved(who: GhostWindow) {
        persist()
    }

    override fun onSessionEnded(who: GhostWindow, mode: FeedView.Mode, completed: Boolean) {
        activeSession = null
        for (w in allWindows()) w.setTouchable(true)
        lastSessionEndWall = System.currentTimeMillis()
        reactLockUntil = SystemClock.uptimeMillis() + CELEBRATION_LOCK_MS

        // Only the mother's completed meals and drinks count toward laying an egg
        if (who === mother && completed) {
            if (mode == FeedView.Mode.FOOD) fedFlag = true else waterFlag = true
        }
        persist()
        handler.postDelayed({ maybeLayEgg() }, CELEBRATION_LOCK_MS + 400L)
    }

    // ---------- Sessions (one at a time across the whole family) ----------

    private fun beginSession(w: GhostWindow, mode: FeedView.Mode): Boolean {
        if (activeSession != null || w.userDragging) return false
        if (!w.startSession(mode)) return false
        activeSession = w
        // Everyone else ignores touches until this session is over
        for (o in allWindows()) if (o !== w) o.setTouchable(false)
        return true
    }

    private fun checkNeeds() {
        if (activeSession != null) return
        if (SystemClock.uptimeMillis() < reactLockUntil) return
        val now = System.currentTimeMillis()
        if (now - lastSessionEndWall < SESSION_COOLDOWN_MS) return

        var best: GhostWindow? = null
        var bestMode = FeedView.Mode.FOOD
        var bestTime = Long.MAX_VALUE
        for (w in allWindows()) {
            if (w.userDragging) continue
            val due = w.dueNeed(now) ?: continue
            if (due.second < bestTime) {
                best = w
                bestMode = due.first
                bestTime = due.second
            }
        }
        val target = best ?: return
        beginSession(target, bestMode)
    }

    // ---------- Egg and babies ----------

    private fun maybeLayEgg() {
        if (egg != null || activeSession != null) return
        if (!fedFlag || !waterFlag) return
        layEgg(EGG_INCUBATION_MS)
    }

    private fun layEgg(durationMs: Long) {
        if (egg != null || activeSession != null) return
        if (babies.size >= Family.MAX_BABIES) return
        val m = mother ?: return
        if (m.userDragging) return
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density

        val spot = findFreeSpot(EggWindow.eggWidth(density), EggWindow.eggHeight(density), m.rect())
        val e = EggWindow(
            this, wm, babies.size, System.currentTimeMillis(), durationMs,
            spot.x, spot.y, true, this
        )
        if (!e.show()) return
        egg = e
        fedFlag = false
        waterFlag = false
        m.ghost.showFed() // the mother is happy after laying
        reactLockUntil = SystemClock.uptimeMillis() + CELEBRATION_LOCK_MS
        persist()
    }

    /** The egg finished hatching: the baby takes its place with its own colour. */
    override fun onHatched(egg: EggWindow) {
        val wm = windowManager
        if (wm != null && babies.size < Family.MAX_BABIES) {
            val n = babies.size + 1
            val b = GhostWindow(this, wm, "baby_$n", Family.BABY_SCALE, Family.color(n - 1), this)
            val now = System.currentTimeMillis()
            b.nextWaterAt = now + BABY_FIRST_WATER_MS
            b.nextFoodAt = now + BABY_FIRST_FOOD_MS
            val density = resources.displayMetrics.density
            val bx = egg.x + (egg.widthPx - b.widthPx) / 2
            val by = egg.y + egg.heightPx - b.heightPx - (EggWindow.BABY_BOTTOM_MARGIN_DP * density).toInt()
            if (b.show(bx, by)) {
                babies.add(b)
                if (activeSession != null) b.setTouchable(false)
                b.ghost.showFed() // newborn hop and smile
            }
        }
        egg.remove()
        this.egg = null
        reactLockUntil = SystemClock.uptimeMillis() + CELEBRATION_LOCK_MS
        persist()
    }

    /** Looks for a free spot near [near] that does not overlap any ghost or the egg. */
    private fun findFreeSpot(w: Int, h: Int, near: Rect): Point {
        val dm = resources.displayMetrics
        val gap = (6 * dm.density).toInt()
        val maxX = maxOf(0, dm.widthPixels - w)
        val maxY = maxOf(0, dm.heightPixels - h)

        val occupied = ArrayList<Rect>()
        for (win in allWindows()) occupied.add(win.rect())
        egg?.let { occupied.add(it.rect()) }

        fun isFree(x: Int, y: Int): Boolean {
            if (x < 0 || y < 0 || x > maxX || y > maxY) return false
            val r = Rect(x - gap, y - gap, x + w + gap, y + h + gap)
            for (o in occupied) if (Rect.intersects(o, r)) return false
            return true
        }

        val candidates = listOf(
            Point(near.right + gap, near.bottom - h),
            Point(near.left - gap - w, near.bottom - h),
            Point(near.right + gap, near.top),
            Point(near.left - gap - w, near.top),
            Point(near.centerX() - w / 2, near.bottom + gap),
            Point(near.centerX() - w / 2, near.top - h - gap)
        )
        for (c in candidates) if (isFree(c.x, c.y)) return c

        // Otherwise the closest free spot on a grid over the screen
        val step = (24 * dm.density).toInt().coerceAtLeast(1)
        var best: Point? = null
        var bestDist = Long.MAX_VALUE
        var yy = 0
        while (yy <= maxY) {
            var xx = 0
            while (xx <= maxX) {
                if (isFree(xx, yy)) {
                    val dx = (xx - near.left).toLong()
                    val dy = (yy - near.top).toLong()
                    val dist = dx * dx + dy * dy
                    if (dist < bestDist) {
                        bestDist = dist
                        best = Point(xx, yy)
                    }
                }
                xx += step
            }
            yy += step
        }
        return best ?: Point(
            (near.right + gap).coerceIn(0, maxX),
            (near.bottom - h).coerceIn(0, maxY)
        )
    }

    // ---------- App updates ----------

    private fun checkForUpdate() {
        Updater.checkInBackground { release ->
            if (release != null && Updater.isNewer(this, release)) {
                Updater.notifyUpdate(this, release)
            }
        }
    }
}
