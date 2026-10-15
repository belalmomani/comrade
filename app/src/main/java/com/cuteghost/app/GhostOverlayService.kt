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
import java.util.Calendar

/**
 * Coordinates the ghost family: the big ghost, any number of babies (at most three on screen at a
 * time) and the egg. Each visible ghost lives in its own [GhostWindow]; this service decides who
 * may act so that movements never overlap, and it handles neglect, loss and the encouraging texts.
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

        // Saved state (the first four keys are the original big-ghost keys)
        private const val KEY_X = "pos_x"
        private const val KEY_Y = "pos_y"
        private const val KEY_NEXT_HUNGER = "next_hunger_at"
        private const val KEY_NEXT_THIRST = "next_thirst_at"
        private const val KEY_MOTHER_COLOR = "mother_color"
        private const val KEY_BABY_COUNT = "baby_count"
        private const val KEY_BABY_TOTAL = "baby_total"
        private const val KEY_FED_FLAG = "fed_since_egg"
        private const val KEY_WATER_FLAG = "watered_since_egg"
        private const val KEY_EGG_ACTIVE = "egg_active"
        private const val KEY_EGG_START = "egg_start"
        private const val KEY_EGG_DUR = "egg_duration"
        private const val KEY_EGG_X = "egg_x"
        private const val KEY_EGG_Y = "egg_y"
        private const val KEY_ALIVE = "last_alive_at"

        // Encouraging texts
        private const val KEY_LAST_PHRASE = "last_phrase_at"
        private const val KEY_PHRASE_CYCLE = "phrase_cycle"
        private const val KEY_PHRASE_INDEX = "phrase_index"
        private const val KEY_MORNING_DAY = "greet_morning_day"
        private const val KEY_EVENING_DAY = "greet_evening_day"
        private const val KEY_NIGHT_DAY = "greet_night_day"

        private const val MARGIN_DP = 12
        private const val LOOP_MS = 30_000L
        private const val FIRST_LOOP_DELAY_MS = 20_000L
        private const val SESSION_COOLDOWN_MS = 15_000L
        private const val CELEBRATION_LOCK_MS = 2_600L
        private const val EGG_INCUBATION_MS = 3L * 60L * 1000L
        private const val EGG_TEST_MS = 20_000L
        private const val BABY_FIRST_WATER_MS = 2L * 60L * 1000L
        private const val BABY_FIRST_FOOD_MS = 4L * 60L * 1000L
        private const val ROTATE_MS = 30L * 60L * 1000L
        private const val PHRASE_INTERVAL_MS = 60L * 60L * 1000L
        private const val UPDATE_FIRST_DELAY_MS = 2L * 60L * 1000L
        private const val UPDATE_CHECK_MS = 12L * 60L * 60L * 1000L

        private const val ACTION_FEED_NOW = "com.companion.theghost.FEED_NOW"
        private const val ACTION_EGG_NOW = "com.companion.theghost.EGG_NOW"
        private const val ACTION_NEGLECT_NOW = "com.companion.theghost.NEGLECT_NOW"
        private const val EXTRA_WATER = "water"

        fun start(context: Context) {
            startService(context, Intent(context, GhostOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GhostOverlayService::class.java))
        }

        /** Asks the big ghost to be hungry or thirsty right now (used for testing). */
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

        /** Moves the big ghost to the next neglect stage: tear, crying, sick, gone (testing). */
        fun neglectNow(context: Context) {
            startService(
                context,
                Intent(context, GhostOverlayService::class.java).setAction(ACTION_NEGLECT_NOW)
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
    private var notice: NoticeWindow? = null
    private var voice: VoiceCompanion? = null
    private val dying = HashSet<GhostWindow>()

    private var destroyed = false
    private var activeSession: GhostWindow? = null
    private var reactLockUntil = 0L
    private var lastSessionEndWall = 0L
    private var lastRotationAt = 0L
    private var fedFlag = false
    private var waterFlag = false
    private var babyTotal = 0

    private val loop = object : Runnable {
        override fun run() {
            checkDeaths()
            updateNeglectVisuals()
            checkNeeds()
            maybeLayEgg()
            rebalanceVisibility()
            maybeSpeakPhrase()
            prefs().edit().putLong(KEY_ALIVE, System.currentTimeMillis()).apply()
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
        } else if (action == ACTION_NEGLECT_NOW) {
            testNeglectStep()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        persist()
        voice?.release()
        voice = null
        notice?.dismissNow()
        notice = null
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

    private fun allGhosts(): List<GhostWindow> {
        val list = ArrayList<GhostWindow>()
        mother?.let { list.add(it) }
        list.addAll(babies)
        return list
    }

    private fun shownGhosts(): List<GhostWindow> = allGhosts().filter { it.isShown }

    private fun showAll(): Boolean {
        val wm = windowManager ?: return false
        val p = prefs()
        notice = NoticeWindow(this, wm)

        val m = GhostWindow(this, wm, "mother", 1f, p.getInt(KEY_MOTHER_COLOR, -1), this)
        m.nextFoodAt = p.getLong(KEY_NEXT_HUNGER, 0L)
        m.nextWaterAt = p.getLong(KEY_NEXT_THIRST, 0L)

        // All babies are remembered; only a few of them are shown at once
        val count = p.getInt(KEY_BABY_COUNT, 0).coerceAtLeast(0)
        for (n in 1..count) {
            val b = GhostWindow(this, wm, "baby_$n", Family.BABY_SCALE, p.getInt("b${n}_c", n - 1), this)
            b.nextFoodAt = p.getLong("b${n}_food", 0L)
            b.nextWaterAt = p.getLong("b${n}_water", 0L)
            if (p.contains("b${n}_x") && p.contains("b${n}_y")) {
                b.setSavedPosition(p.getInt("b${n}_x", 0), p.getInt("b${n}_y", 0))
            }
            babies.add(b)
        }
        babyTotal = maxOf(p.getInt(KEY_BABY_TOTAL, 0), count)
        applyOfflinePause(m)

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

        fedFlag = p.getBoolean(KEY_FED_FLAG, false)
        waterFlag = p.getBoolean(KEY_WATER_FLAG, false)

        if (p.getBoolean(KEY_EGG_ACTIVE, false)) {
            val e = EggWindow(
                this, wm, babyTotal,
                p.getLong(KEY_EGG_START, System.currentTimeMillis()),
                p.getLong(KEY_EGG_DUR, EGG_INCUBATION_MS),
                p.getInt(KEY_EGG_X, 0), p.getInt(KEY_EGG_Y, 0),
                false, this
            )
            if (e.show()) egg = e
        }

        lastRotationAt = System.currentTimeMillis()
        rebalanceVisibility()
        updateNeglectVisuals()

        handler.removeCallbacks(loop)
        handler.postDelayed(loop, FIRST_LOOP_DELAY_MS)
        handler.removeCallbacks(updateLoop)
        handler.postDelayed(updateLoop, UPDATE_FIRST_DELAY_MS)
        return true
    }

    /**
     * Time while the ghosts were hidden does not count toward hunger, thirst or the 24-hour limit,
     * so nothing gets sick or lost just because the app was closed.
     */
    private fun applyOfflinePause(m: GhostWindow) {
        if (!Features.PAUSE_WHILE_HIDDEN) return
        val last = prefs().getLong(KEY_ALIVE, 0L)
        if (last == 0L) return
        val offline = System.currentTimeMillis() - last
        if (offline <= 0L) return
        for (g in listOf(m) + babies) {
            if (g.nextFoodAt != 0L) g.nextFoodAt += offline
            if (g.nextWaterAt != 0L) g.nextWaterAt += offline
        }
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
            e.putInt(KEY_MOTHER_COLOR, it.colorIndex)
        }
        e.putInt(KEY_BABY_COUNT, babies.size)
        e.putInt(KEY_BABY_TOTAL, babyTotal)
        for ((i, b) in babies.withIndex()) {
            val n = i + 1
            e.putInt("b${n}_c", b.colorIndex)
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
        e.putLong(KEY_ALIVE, System.currentTimeMillis())
        e.apply()
    }

    // ---------- GhostWindow.Host ----------

    override fun canDrag(who: GhostWindow): Boolean = activeSession == null

    override fun onTap(who: GhostWindow) {
        if (activeSession != null || who in dying) return
        val companion = voice
        if (Features.VOICE_ENABLED && companion != null && who === mother) {
            companion.onTap()
            return
        }
        // A hungry, thirsty, crying or sick ghost opens what it needs when it is tapped
        val pending = who.pendingNeed(System.currentTimeMillis())
        if (pending != null) {
            beginSession(who, pending.first)
            return
        }
        // Otherwise it plays; only one ghost plays at a time
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
        for (w in shownGhosts()) w.setTouchable(true)
        lastSessionEndWall = System.currentTimeMillis()
        reactLockUntil = SystemClock.uptimeMillis() + CELEBRATION_LOCK_MS

        // Only the big ghost's completed meals and drinks count toward laying an egg
        if (who === mother && completed) {
            if (mode == FeedView.Mode.FOOD) fedFlag = true else waterFlag = true
        }
        persist()
        updateNeglectVisuals()
        handler.postDelayed({ if (!destroyed) maybeLayEgg() }, CELEBRATION_LOCK_MS + 400L)
    }

    // ---------- Sessions (one at a time across the whole family) ----------

    private fun beginSession(w: GhostWindow, mode: FeedView.Mode): Boolean {
        if (activeSession != null || w.userDragging || w in dying || !w.isShown) return false
        if (!w.startSession(mode)) return false
        activeSession = w
        // Everyone else ignores touches until this session is over
        for (o in shownGhosts()) if (o !== w) o.setTouchable(false)
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
        for (w in shownGhosts()) {
            if (w.userDragging || w in dying) continue
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

    // ---------- Neglect: tear, crying, sickness, and finally loss ----------

    private fun updateNeglectVisuals() {
        val now = System.currentTimeMillis()
        for (w in shownGhosts()) {
            if (w in dying) continue
            w.ghost.setNeglect(minOf(3, w.neglectLevel(now)))
        }
    }

    private fun checkDeaths() {
        val now = System.currentTimeMillis()
        for (w in allGhosts()) {
            if (w in dying || w === activeSession) continue
            if (w.neglectLevel(now) >= 4) kill(w)
        }
    }

    private fun ghostTitle(w: GhostWindow): String {
        if (w === mother) return getString(R.string.ghost_big)
        val names = resources.getStringArray(R.array.ghost_color_names)
        return if (w.colorIndex in names.indices) names[w.colorIndex]
        else getString(R.string.ghost_baby_number, w.colorIndex + 1)
    }

    /** After 24 hours without care the ghost fades away for good; a baby takes over if there is one. */
    private fun kill(w: GhostWindow) {
        if (!dying.add(w)) return
        val title = ghostTitle(w)
        val text: String = if (w === mother) {
            val heir = babies.firstOrNull { it !in dying }
            if (heir != null) {
                getString(R.string.notice_death_heir, title, ghostTitle(heir))
            } else {
                getString(R.string.notice_death_new, title)
            }
        } else {
            getString(R.string.notice_death, title)
        }
        notice?.show(text, 12_000L)

        if (w.isShown) {
            w.fadeOutAndRemove(2_500L) { finalizeDeath(w) }
        } else {
            finalizeDeath(w)
        }
    }

    private fun finalizeDeath(w: GhostWindow) {
        if (destroyed) return
        dying.remove(w)
        if (w === mother) {
            replaceMother(w)
        } else {
            babies.remove(w)
        }
        persist()
        rebalanceVisibility()
        updateNeglectVisuals()
    }

    /** The first baby grows up and takes the big ghost's place; without babies a new ghost appears. */
    private fun replaceMother(old: GhostWindow) {
        val wm = windowManager ?: return
        val now = System.currentTimeMillis()
        val heir = babies.firstOrNull { it !in dying }
        val next: GhostWindow
        if (heir != null) {
            babies.remove(heir)
            heir.remove()
            next = GhostWindow(this, wm, "mother", 1f, heir.colorIndex, this)
        } else {
            next = GhostWindow(this, wm, "mother", 1f, -1, this)
        }
        // A fresh start in the new role
        next.nextFoodAt = now + GhostWindow.HUNGER_INTERVAL_MS
        next.nextWaterAt = now + GhostWindow.THIRST_INTERVAL_MS
        mother = next
        if (next.show(old.restX, old.restY)) {
            next.ghost.showFed()
            reactLockUntil = SystemClock.uptimeMillis() + CELEBRATION_LOCK_MS
        } else {
            Log.e(TAG, "Could not show the new big ghost")
            stopSelf()
        }
    }

    /** Test button: moves the big ghost one neglect stage further (tear, crying, sick, gone). */
    private fun testNeglectStep() {
        val m = mother ?: return
        if (m in dying) return
        val now = System.currentTimeMillis()
        val elapsed = when (m.neglectLevel(now)) {
            0 -> GhostWindow.NEGLECT_GRACE_MS + 60_000L
            1 -> GhostWindow.NEGLECT_GRACE_MS + GhostWindow.CRY_AFTER_MS + 60_000L
            2 -> GhostWindow.NEGLECT_GRACE_MS + GhostWindow.SICK_AFTER_MS + 60_000L
            else -> GhostWindow.DEATH_AFTER_MS + 60_000L
        }
        m.nextFoodAt = now - elapsed
        m.nextWaterAt = now - elapsed
        m.askAfter = now + 60_000L // a minute to look at it before it asks again
        persist()
        updateNeglectVisuals()
        checkDeaths()
    }

    // ---------- Which babies are on screen (at most three) ----------

    private fun urgency(w: GhostWindow, now: Long): Int = when {
        w.neglectLevel(now) >= 1 -> 2
        w.pendingNeed(now) != null -> 1
        else -> 0
    }

    private fun rebalanceVisibility() {
        val now = System.currentTimeMillis()
        val max = Family.MAX_VISIBLE_BABIES
        var changed = false

        fun calmVictim(maxUrgency: Int): GhostWindow? =
            babies.filter {
                it.isShown && it !in dying && it !== activeSession && !it.userDragging &&
                    urgency(it, now) < maxUrgency
            }.minByOrNull { it.shownSince }

        // Too many on screen (a new baby was born): hide the one that has been there longest
        while (babies.count { it.isShown && it !in dying } > max) {
            val victim = calmVictim(3) ?: break
            hideBaby(victim)
            changed = true
        }

        // Fill free slots: urgent babies first, then the ones that were hidden the longest
        val hidden = babies.filter { !it.isShown && it !in dying }
            .sortedWith(compareByDescending<GhostWindow> { urgency(it, now) }.thenBy { it.lastShownAt })
        var visible = babies.count { it.isShown && it !in dying }
        for (h in hidden) {
            if (visible >= max) break
            if (showBaby(h)) {
                visible++
                changed = true
            }
        }

        // A hidden baby that needs care replaces a calm one that is on screen
        for (h in hidden) {
            if (h.isShown) continue
            val u = urgency(h, now)
            if (u == 0) continue
            val victim = calmVictim(u) ?: continue
            hideBaby(victim)
            if (showBaby(h)) changed = true
        }

        // Every baby gets screen time: rotate one in every half hour
        val stillHidden = babies.filter { !it.isShown && it !in dying }
        if (stillHidden.isNotEmpty() && now - lastRotationAt > ROTATE_MS) {
            val incoming = stillHidden.minByOrNull { it.lastShownAt }
            val victim = calmVictim(1)
            if (incoming != null && victim != null) {
                hideBaby(victim)
                if (showBaby(incoming)) changed = true
                lastRotationAt = now
            }
        }
        if (changed) persist()
    }

    private fun showBaby(b: GhostWindow): Boolean {
        val m = mother ?: return false
        val spot = if (b.hasPosition && isSpotFree(b.restX, b.restY, b.widthPx, b.heightPx)) {
            Point(b.restX, b.restY)
        } else {
            findFreeSpot(b.widthPx, b.heightPx, m.rect())
        }
        if (!b.show(spot.x, spot.y)) return false
        if (activeSession != null) b.setTouchable(false)
        b.ghost.setNeglect(minOf(3, b.neglectLevel(System.currentTimeMillis())))
        return true
    }

    private fun hideBaby(b: GhostWindow) {
        b.remove()
    }

    // ---------- Egg and babies ----------

    private fun maybeLayEgg() {
        if (egg != null || activeSession != null) return
        if (!fedFlag || !waterFlag) return
        layEgg(EGG_INCUBATION_MS)
    }

    private fun layEgg(durationMs: Long) {
        if (egg != null || activeSession != null) return
        val m = mother ?: return
        if (m.userDragging || m in dying) return
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density

        val spot = findFreeSpot(EggWindow.eggWidth(density), EggWindow.eggHeight(density), m.rect())
        val e = EggWindow(
            this, wm, babyTotal, System.currentTimeMillis(), durationMs,
            spot.x, spot.y, true, this
        )
        if (!e.show()) return
        egg = e
        fedFlag = false
        waterFlag = false
        m.ghost.showFed() // the big ghost is happy after laying
        reactLockUntil = SystemClock.uptimeMillis() + CELEBRATION_LOCK_MS
        persist()
    }

    /** The egg finished hatching: the baby takes its place with its own colour. */
    override fun onHatched(egg: EggWindow) {
        val wm = windowManager
        if (wm != null) {
            val b = GhostWindow(this, wm, "baby_${babies.size + 1}", Family.BABY_SCALE, babyTotal, this)
            babyTotal++
            val now = System.currentTimeMillis()
            b.nextWaterAt = now + BABY_FIRST_WATER_MS
            b.nextFoodAt = now + BABY_FIRST_FOOD_MS
            val density = resources.displayMetrics.density
            val bx = egg.x + (egg.widthPx - b.widthPx) / 2
            val by = egg.y + egg.heightPx - b.heightPx - (EggWindow.BABY_BOTTOM_MARGIN_DP * density).toInt()
            babies.add(b)
            if (b.show(bx, by)) {
                if (activeSession != null) b.setTouchable(false)
                b.ghost.showFed() // newborn hop and smile
            }
        }
        egg.remove()
        this.egg = null
        reactLockUntil = SystemClock.uptimeMillis() + CELEBRATION_LOCK_MS
        rebalanceVisibility()
        persist()
    }

    // ---------- Free spots on the screen ----------

    private fun occupiedRects(): List<Rect> {
        val list = ArrayList<Rect>()
        for (w in shownGhosts()) list.add(w.rect())
        egg?.let { list.add(it.rect()) }
        return list
    }

    private fun isSpotFree(x: Int, y: Int, w: Int, h: Int, occupied: List<Rect> = occupiedRects()): Boolean {
        val dm = resources.displayMetrics
        val gap = (6 * dm.density).toInt()
        if (x < 0 || y < 0 || x > maxOf(0, dm.widthPixels - w) || y > maxOf(0, dm.heightPixels - h)) return false
        val r = Rect(x - gap, y - gap, x + w + gap, y + h + gap)
        for (o in occupied) if (Rect.intersects(o, r)) return false
        return true
    }

    /** Looks for a free spot near [near] that does not overlap any ghost or the egg. */
    private fun findFreeSpot(w: Int, h: Int, near: Rect): Point {
        val dm = resources.displayMetrics
        val gap = (6 * dm.density).toInt()
        val maxX = maxOf(0, dm.widthPixels - w)
        val maxY = maxOf(0, dm.heightPixels - h)
        val occupied = occupiedRects()

        val candidates = listOf(
            Point(near.right + gap, near.bottom - h),
            Point(near.left - gap - w, near.bottom - h),
            Point(near.right + gap, near.top),
            Point(near.left - gap - w, near.top),
            Point(near.centerX() - w / 2, near.bottom + gap),
            Point(near.centerX() - w / 2, near.top - h - gap)
        )
        for (c in candidates) if (isSpotFree(c.x, c.y, w, h, occupied)) return c

        // Otherwise the closest free spot on a grid over the screen
        val step = (24 * dm.density).toInt().coerceAtLeast(1)
        var best: Point? = null
        var bestDist = Long.MAX_VALUE
        var yy = 0
        while (yy <= maxY) {
            var xx = 0
            while (xx <= maxX) {
                if (isSpotFree(xx, yy, w, h, occupied)) {
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

    // ---------- Encouraging texts with the children's names ----------

    /** About once an hour: a greeting in the morning, then play and encouragement texts. */
    private fun maybeSpeakPhrase() {
        val n = notice ?: return
        if (activeSession != null || n.isShowing) return
        val now = System.currentTimeMillis()
        val p = prefs()
        if (now - p.getLong(KEY_LAST_PHRASE, 0L) < PHRASE_INTERVAL_MS) return
        // Not while a ghost is crying or sick
        for (w in allGhosts()) if (w.neglectLevel(now) >= 2) return

        val names = ChildNames.list(this)
        val index = p.getInt(KEY_PHRASE_INDEX, 0)
        val name: String? = if (names.isEmpty()) null else names[index % names.size]
        val greetings = resources.getStringArray(
            if (name != null) R.array.greetings_named else R.array.greetings_plain
        )
        val phrases = resources.getStringArray(
            if (name != null) R.array.phrases_named else R.array.phrases_plain
        )

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dayKey = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        val edit = p.edit()
        var text: String
        if (hour in 5..11 && p.getInt(KEY_MORNING_DAY, 0) != dayKey) {
            text = greetings[0]
            edit.putInt(KEY_MORNING_DAY, dayKey)
            edit.putInt(KEY_PHRASE_CYCLE, 0) // the next text is "let's play"
        } else if (hour in 17..20 && p.getInt(KEY_EVENING_DAY, 0) != dayKey) {
            text = greetings[1]
            edit.putInt(KEY_EVENING_DAY, dayKey)
        } else if ((hour >= 21 || hour < 5) && p.getInt(KEY_NIGHT_DAY, 0) != dayKey) {
            text = greetings[2]
            edit.putInt(KEY_NIGHT_DAY, dayKey)
        } else {
            val cycle = p.getInt(KEY_PHRASE_CYCLE, 0)
            text = phrases[cycle % phrases.size]
            edit.putInt(KEY_PHRASE_CYCLE, cycle + 1)
        }
        if (name != null) text = String.format(text, name)
        edit.putLong(KEY_LAST_PHRASE, now)
        edit.putInt(KEY_PHRASE_INDEX, index + 1)
        edit.apply()
        n.show(text, 7_000L)
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
