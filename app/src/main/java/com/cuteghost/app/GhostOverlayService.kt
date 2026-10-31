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
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Coordinates the ghost family: the big ghost next to the babies' house, any number of babies
 * (they live in the house and only one is outside at a time), the egg and the season of the day.
 * Each visible ghost lives in its own [GhostWindow]; this service decides who may act so that
 * movements never overlap, and it handles neglect, loss and the encouraging texts.
 */
class GhostOverlayService : Service(), GhostWindow.Host, EggWindow.Host,
    PeekWindow.Host, PortalWindow.Host, PickupWindow.Listener {

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
        private const val KEY_LAST_EGG_DAY = "last_egg_day"
        private const val KEY_EGG_MYSTERY = "egg_mystery"
        private const val KEY_NEXT_BATH = "next_bath"
        private const val KEY_SEASON_DAY = "season_day"
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
        private const val PLAY_LOOP_MS = 4_000L
        private const val FIRST_LOOP_DELAY_MS = 20_000L
        private const val SESSION_COOLDOWN_MS = 15_000L
        private const val CELEBRATION_LOCK_MS = 2_600L
        private const val EGG_INCUBATION_MS = 60L * 60L * 1000L // hatching takes one hour
        private const val EGG_TEST_MS = 20_000L
        private const val BABY_FIRST_WATER_MS = 2L * 60L * 1000L
        private const val BABY_FIRST_FOOD_MS = 4L * 60L * 1000L
        private const val ROTATE_MS = 10L * 60L * 1000L          // babies take turns outside
        private const val SWAP_OVERLAP_MS = 3_500L               // they meet at the door and play
        private const val SWAP_COOLDOWN_MS = 60_000L
        private const val PHRASE_INTERVAL_MS = 60L * 60L * 1000L
        private const val SEASON_DURATION_MS = 15L * 60L * 1000L // snow etc. disappears after 15 minutes
        private const val SEASON_TEST_MS = 40_000L
        private const val SEASON_START_DELAY_MS = 90_000L
        private const val UPDATE_FIRST_DELAY_MS = 2L * 60L * 1000L
        private const val UPDATE_CHECK_MS = 12L * 60L * 60L * 1000L

        private const val ACTION_FEED_NOW = "com.companion.theghost.FEED_NOW"
        private const val ACTION_EGG_NOW = "com.companion.theghost.EGG_NOW"
        private const val ACTION_BATH_NOW = "com.companion.theghost.BATH_NOW"
        private const val ACTION_BRUSH_NOW = "com.companion.theghost.BRUSH_NOW"
        private const val ACTION_MISCHIEF_NOW = "com.companion.theghost.MISCHIEF_NOW"
        private const val ACTION_GROW_NOW = "com.companion.theghost.GROW_NOW"
        private const val ACTION_NEGLECT_NOW = "com.companion.theghost.NEGLECT_NOW"
        private const val ACTION_SEASON_NOW = "com.companion.theghost.SEASON_NOW"
        private const val ACTION_OUTFIT = "com.companion.theghost.OUTFIT"
        private const val PROXIMITY_MS = 400L
        private const val NEAR_DOOR_DP = 80
        private const val PEEK_COOLDOWN_MS = 4L * 60L * 1000L
        private const val PORTAL_COOLDOWN_MS = 8L * 60L * 1000L
        private const val VISIT_MS = 60_000L
        private const val ACTION_PEEK_NOW = "com.companion.theghost.PEEK_NOW"
        private const val ACTION_PORTAL_NOW = "com.companion.theghost.PORTAL_NOW"
        private const val HOUSE_DRIFT_MIN_MS = 25_000L
        private const val HOUSE_DRIFT_MAX_MS = 45_000L
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

        /** The first baby grows to the big ghost's size and moves to the village (testing). */
        fun growNow(context: Context) {
            startService(
                context,
                Intent(context, GhostOverlayService::class.java).setAction(ACTION_GROW_NOW)
            )
        }

        /** The big ghost asks for a bath right now (testing). */
        fun bathNow(context: Context) {
            startService(context, Intent(context, GhostOverlayService::class.java).setAction(ACTION_BATH_NOW))
        }

        /** The big ghost asks to brush its teeth right now (testing). */
        fun brushNow(context: Context) {
            startService(context, Intent(context, GhostOverlayService::class.java).setAction(ACTION_BRUSH_NOW))
        }

        /** A ghost plays a pretend prank right now (testing). */
        fun mischiefNow(context: Context) {
            startService(
                context,
                Intent(context, GhostOverlayService::class.java).setAction(ACTION_MISCHIEF_NOW)
            )
        }

        /** Moves the big ghost to the next neglect stage: tear, crying, sick, gone (testing). */
        fun neglectNow(context: Context) {
            startService(
                context,
                Intent(context, GhostOverlayService::class.java).setAction(ACTION_NEGLECT_NOW)
            )
        }

        /** Shows the next season for 40 seconds (testing). */
        fun seasonNow(context: Context) {
            startService(
                context,
                Intent(context, GhostOverlayService::class.java).setAction(ACTION_SEASON_NOW)
            )
        }

        /** A baby peeks in from the edge of the screen right now (testing). */
        fun peekNow(context: Context) {
            startService(
                context,
                Intent(context, GhostOverlayService::class.java).setAction(ACTION_PEEK_NOW)
            )
        }

        /** Opens the magic portal right now (testing). */
        fun portalNow(context: Context) {
            startService(
                context,
                Intent(context, GhostOverlayService::class.java).setAction(ACTION_PORTAL_NOW)
            )
        }

        /** Puts on the outfit that was chosen in the wardrobe. */
        fun outfitNow(context: Context) {
            startService(
                context,
                Intent(context, GhostOverlayService::class.java).setAction(ACTION_OUTFIT)
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
    private val babies = ArrayList<GhostWindow>()     // all babies; most of them are inside the house
    private val outside = ArrayList<GhostWindow>()    // babies outside the house (one, two while swapping)
    private val entering = HashSet<GhostWindow>()     // babies on their way back into the house
    private val dying = HashSet<GhostWindow>()
    private var house: HouseWindow? = null
    private var egg: EggWindow? = null
    private var notice: NoticeWindow? = null
    private var seasonWindow: SeasonWindow? = null
    private var voice: VoiceCompanion? = null

    private var destroyed = false
    private var serviceStartedAt = 0L
    private var activeSession: GhostWindow? = null
    private var reactLockUntil = 0L
    private var lastSessionEndWall = 0L
    private var lastSwapAt = 0L
    private var lastRotationAt = 0L
    private var fedFlag = false
    private var waterFlag = false
    private var babyTotal = 0
    private var testSeason = 0
    private var room: RoomWindow? = null
    private var bedtimeActive = false
    private val savedPos = HashMap<GhostWindow, Point>()
    private var lastLoopWall = 0L
    private var nextHouseDriftAt = 0L

    // Surprises: a baby peeking from the edge, the magic portal and its visitor
    private var peek: PeekWindow? = null
    private var peeker: GhostWindow? = null
    private var lastPeekAt = 0L
    private var portal: PortalWindow? = null
    private var lastPortalAt = 0L
    private var visitor: GhostWindow? = null
    private var visitorLeaving = false
    private var visitorLeaveAt = 0L
    private val pickups = ArrayList<PickupWindow>()

    private val loop = object : Runnable {
        override fun run() {
            val nowWall = System.currentTimeMillis()
            val delta = nowWall - lastLoopWall
            lastLoopWall = nowWall
            // While the ghosts sleep, hunger, thirst and the 24-hour limit stand still
            if (bedtimeActive && delta > 0L) pauseTimers(delta)
            checkBedtime()
            updateDayMood()
            checkDeaths()
            updateNeglectVisuals()
            checkNeeds()
            maybeLayEgg()
            manageHouse()
            maybeDriftHouse()
            maybePeek()
            maybeMischief()
            maybeOpenPortal()
            checkVisitor()
            maybeSpeakPhrase()
            maybeStartSeason()
            applyOutfitToAll()
            prefs().edit().putLong(KEY_ALIVE, nowWall).apply()
            handler.postDelayed(this, LOOP_MS)
        }
    }

    /** Babies outside the house jump around the big ghost and play (each in its own way). */
    private val playLoop = object : Runnable {
        override fun run() {
            if (!bedtimeActive) {
                for (b in outside.toList()) {
                    if (!b.isShown || b in entering) continue
                    if (b.personality == Personality.SHY && Random.nextFloat() < 0.5f) continue
                    val inPlace = if (b.personality == Personality.JUMPER) 0.15f else 0.3f
                    if (Random.nextFloat() < inPlace) playInPlace(b) else playHop(b)
                }
                // The stranger from the portal plays with the family too
                val v = visitor
                if (v != null && v.isShown && !visitorLeaving) {
                    if (Random.nextFloat() < 0.3f) playInPlace(v) else playHop(v)
                }
            }
            handler.postDelayed(this, PLAY_LOOP_MS)
        }
    }

    /** The door of the house opens by itself when a ghost comes near it. */
    private val proximityLoop = object : Runnable {
        override fun run() {
            val h = house
            if (h != null) {
                val door = h.doorPoint()
                val limit = dp(NEAR_DOOR_DP).toDouble()
                var near = false
                for (g in shownGhosts()) {
                    val r = g.rect()
                    val dist = hypot((r.centerX() - door.x).toDouble(), (r.centerY() - door.y).toDouble())
                    if (dist < limit) {
                        near = true
                        break
                    }
                }
                h.setNear(near)
            }
            handler.postDelayed(this, PROXIMITY_MS)
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
            layEgg(EGG_TEST_MS, true)
        } else if (action == ACTION_GROW_NOW) {
            growNow()
        } else if (action == ACTION_BATH_NOW) {
            mother?.let { it.ghost.dirty = true; beginSession(it, FeedView.Mode.BATH) }
        } else if (action == ACTION_BRUSH_NOW) {
            mother?.let { beginSession(it, FeedView.Mode.BRUSH) }
        } else if (action == ACTION_MISCHIEF_NOW) {
            startMischief(true)
        } else if (action == ACTION_NEGLECT_NOW) {
            testNeglectStep()
        } else if (action == ACTION_SEASON_NOW) {
            testSeason = (testSeason + 1) % 4
            startSeason(testSeason, SEASON_TEST_MS)
        } else if (action == ACTION_OUTFIT) {
            applyOutfitToAll()
        } else if (action == ACTION_PEEK_NOW) {
            peekNow()
        } else if (action == ACTION_PORTAL_NOW) {
            if (portal == null) openPortal()
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
        cancelSurprises()
        room?.remove()
        room = null
        seasonWindow?.remove()
        seasonWindow = null
        egg?.remove()
        egg = null
        mischiefWindow?.remove()
        mischiefWindow = null
        house?.remove()
        house = null
        for (b in babies) b.remove()
        babies.clear()
        outside.clear()
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

    private fun dayNumber(): Int {
        val now = System.currentTimeMillis()
        return ((now + TimeZone.getDefault().getOffset(now)) / 86_400_000L).toInt()
    }

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
        serviceStartedAt = System.currentTimeMillis()
        notice = NoticeWindow(this, wm)

        val m = GhostWindow(this, wm, "mother", 1f, p.getInt(KEY_MOTHER_COLOR, -1), this)
        m.nextFoodAt = p.getLong(KEY_NEXT_HUNGER, 0L)
        m.nextWaterAt = p.getLong(KEY_NEXT_THIRST, 0L)
        m.nextBathAt = p.getLong(KEY_NEXT_BATH, 0L)

        // All babies are remembered; they live inside the house
        val count = p.getInt(KEY_BABY_COUNT, 0).coerceAtLeast(0)
        for (n in 1..count) {
            val meals = p.getInt("b${n}_meals", 0)
            val b = GhostWindow(
                this, wm, "baby_$n", Family.babyScale(meals), p.getInt("b${n}_c", n - 1), this
            )
            b.meals = meals
            b.twin = p.getBoolean("b${n}_twin", false)
            b.nextFoodAt = p.getLong("b${n}_food", 0L)
            b.nextWaterAt = p.getLong("b${n}_water", 0L)
            b.nextBathAt = p.getLong("b${n}_bath", 0L)
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
            e.mystery = p.getBoolean(KEY_EGG_MYSTERY, false)
            if (e.show()) egg = e
        }

        // The house stands next to the big ghost as soon as there is a baby or an egg
        if (babies.isNotEmpty() || egg != null) showHouse()

        lastRotationAt = System.currentTimeMillis()
        lastLoopWall = lastRotationAt
        nextHouseDriftAt = lastRotationAt + 20_000L
        updateNeglectVisuals()
        updateDayMood()

        handler.removeCallbacks(loop)
        handler.postDelayed(loop, FIRST_LOOP_DELAY_MS)
        handler.removeCallbacks(playLoop)
        handler.postDelayed(playLoop, PLAY_LOOP_MS)
        handler.removeCallbacks(proximityLoop)
        handler.postDelayed(proximityLoop, PROXIMITY_MS)
        handler.removeCallbacks(updateLoop)
        handler.postDelayed(updateLoop, UPDATE_FIRST_DELAY_MS)
        // Let the first baby out of the house shortly after the start
        handler.postDelayed({ if (!destroyed) manageHouse() }, 1_500L)
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
            if (g.nextBathAt != 0L) g.nextBathAt += offline
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
            // leave room on the right for the house
            Point(
                (maxX - dp(MARGIN_DP) - HouseWindow.size(dm.density)).coerceAtLeast(0),
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
            e.putLong(KEY_NEXT_BATH, it.nextBathAt)
            e.putInt(KEY_MOTHER_COLOR, it.colorIndex)
        }
        e.putInt(KEY_BABY_COUNT, babies.size)
        e.putInt(KEY_BABY_TOTAL, babyTotal)
        for ((i, b) in babies.withIndex()) {
            val n = i + 1
            e.putInt("b${n}_c", b.colorIndex)
            e.putInt("b${n}_meals", b.meals)
            e.putLong("b${n}_food", b.nextFoodAt)
            e.putLong("b${n}_water", b.nextWaterAt)
            e.putLong("b${n}_bath", b.nextBathAt)
            e.putBoolean("b${n}_twin", b.twin)
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
            e.putBoolean(KEY_EGG_MYSTERY, eg.mystery)
        }
        e.putLong(KEY_ALIVE, System.currentTimeMillis())
        e.apply()
    }

    // ---------- GhostWindow.Host ----------

    override fun canDrag(who: GhostWindow): Boolean = activeSession == null && !bedtimeActive

    override fun onTap(who: GhostWindow, head: Boolean) {
        if (activeSession != null || who in dying || who in entering || who.isMoving) return
        if (bedtimeActive || who.sleeping) return // a sleeping ghost is not disturbed
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
        // Otherwise it reacts to the touch (jump, laugh, hide, cry...); only one ghost at a time
        val now = SystemClock.uptimeMillis()
        if (now < reactLockUntil) return
        reactLockUntil = now + 1300L
        who.ghost.react(head)
    }

    override fun onMoved(who: GhostWindow) {
        persist()
    }

    override fun onSessionEnded(who: GhostWindow, mode: FeedView.Mode, completed: Boolean) {
        activeSession = null
        for (w in shownGhosts()) w.setTouchable(true)
        lastSessionEndWall = System.currentTimeMillis()
        reactLockUntil = SystemClock.uptimeMillis() + CELEBRATION_LOCK_MS

        if (completed) {
            if (who === mother) {
                // Only the big ghost's completed meals and drinks count toward laying an egg
                if (mode == FeedView.Mode.FOOD) fedFlag = true else if (mode == FeedView.Mode.WATER) waterFlag = true
            } else if (mode == FeedView.Mode.FOOD || mode == FeedView.Mode.WATER) {
                // Every meal and every bottle of milk makes a baby grow by 1%
                who.meals++
                who.applyScale(Family.babyScale(who.meals))
                if (Family.babyScale(who.meals) >= Family.MAX_BABY_SCALE) {
                    // As big as the big ghost: it moves to its own house in the village
                    handler.postDelayed({ if (!destroyed) graduate(who) }, CELEBRATION_LOCK_MS + 800L)
                }
            }
        }
        if (completed && (mode == FeedView.Mode.FOOD || mode == FeedView.Mode.WATER)) {
            // Care earns a star and a building material for the houses
            HomeStore.addMaterials(this, 1)
            announceGifts(
                Wardrobe.recordCare(this, mode == FeedView.Mode.FOOD, dayNumber()) +
                    Wardrobe.addStars(this, 1)
            )
        } else if (completed && mode == FeedView.Mode.BATH) {
            HomeStore.addMaterials(this, 1)
            announceGifts(Wardrobe.addStars(this, 1))
        }
        // After a meal the ghost brushes its teeth: the child rubs the brush at its mouth
        if (completed && mode == FeedView.Mode.FOOD) {
            handler.postDelayed({ if (!destroyed) beginSession(who, FeedView.Mode.BRUSH) }, CELEBRATION_LOCK_MS + 500L)
        }
        persist()
        updateNeglectVisuals()
        handler.postDelayed({ if (!destroyed) maybeLayEgg() }, CELEBRATION_LOCK_MS + 400L)
    }

    // ---------- Sessions (one at a time across the whole family) ----------

    private fun anyoneMoving(): Boolean = shownGhosts().any { it.isMoving }

    private fun beginSession(w: GhostWindow, mode: FeedView.Mode): Boolean {
        if (activeSession != null || w.userDragging || w in dying || w in entering || !w.isShown) return false
        if (bedtimeActive || w.sleeping) return false
        if (anyoneMoving()) return false
        if (!w.startSession(mode)) return false
        activeSession = w
        // Everyone else ignores touches until this session is over
        for (o in shownGhosts()) if (o !== w) o.setTouchable(false)
        return true
    }

    private fun checkNeeds() {
        if (activeSession != null || anyoneMoving() || bedtimeActive) return
        if (SystemClock.uptimeMillis() < reactLockUntil) return
        val now = System.currentTimeMillis()
        if (now - lastSessionEndWall < SESSION_COOLDOWN_MS) return

        var best: GhostWindow? = null
        var bestMode = FeedView.Mode.FOOD
        var bestTime = Long.MAX_VALUE
        for (w in shownGhosts()) {
            if (w.userDragging || w in dying || w in entering || w.sleeping) continue
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
            w.ghost.dirty = w.nextBathAt != 0L && now >= w.nextBathAt
        }
    }

    /**
     * A baby reached the size of the big ghost: it moves to its own house in the village and lives
     * there alone, and the big ghost goes to the village too. The next baby (or a new ghost) takes
     * her place on the screen.
     */
    private fun graduate(b: GhostWindow) {
        if (destroyed || b !in babies || b in dying) return
        val m = mother ?: return
        if (m in dying) return
        if (activeSession != null || bedtimeActive || b.isMoving || m.userDragging) {
            handler.postDelayed({ graduate(b) }, 30_000L)
            return
        }
        Village.add(this, b.colorIndex, ghostTitle(b), false)
        Village.add(this, m.colorIndex, getString(R.string.ghost_big), true)
        notice?.show(
            "كبر ${ghostTitle(b)} وصار بحجم أمه! ذهب ليعيش وحده في بيته الخاص في القرية، وذهبت الأم لتعيش هناك أيضًا.",
            14_000L
        )
        dying.add(b)
        dying.add(m)
        b.fadeOutAndRemove(1_800L) {
            if (!destroyed) {
                dying.remove(b)
                babies.remove(b)
                outside.remove(b)
                entering.remove(b)
                m.fadeOutAndRemove(2_200L) {
                    if (!destroyed) {
                        dying.remove(m)
                        replaceMother(m)
                        persist()
                        updateNeglectVisuals()
                    }
                }
            }
        }
    }

    /** Test button: the first baby grows to the big ghost's size now and moves to the village. */
    private fun growNow() {
        val b = babies.firstOrNull { it !in dying }
        if (b == null) {
            notice?.show("لا يوجد ابن بعد. جرّب البيضة أولًا.", 5_000L)
            return
        }
        b.meals = ((Family.MAX_BABY_SCALE - Family.NEWBORN_SCALE) / Family.GROWTH_PER_MEAL).toInt() + 1
        b.applyScale(Family.babyScale(b.meals))
        graduate(b)
    }

    // ---------- Naughty ghosts: pretend pranks (never real damage) ----------

    private var mischiefWindow: MischiefWindow? = null
    private var lastMischiefAt = 0L

    private fun mischiefEnabled(): Boolean =
        getSharedPreferences("ghost_prefs", MODE_PRIVATE).getBoolean("mischief_enabled", true)

    /** Once in a while (at most twice a day, hours apart) a ghost plays a harmless pretend prank. */
    private fun maybeMischief() {
        if (mischiefWindow != null || bedtimeActive || activeSession != null) return
        if (peek != null || portal != null || peeker != null) return
        if (!mischiefEnabled()) return
        val now = System.currentTimeMillis()
        if (now - serviceStartedAt < 180_000L || now - lastMischiefAt < 150L * 60L * 1000L) return
        val p = getSharedPreferences("ghost_prefs", MODE_PRIVATE)
        val today = dayNumber()
        val count = if (p.getInt("mischief_day", -1) == today) p.getInt("mischief_count", 0) else 0
        if (count >= 2) return
        if (Random.nextFloat() > 0.02f) return
        startMischief(false)
    }

    private fun startMischief(test: Boolean) {
        if (mischiefWindow != null || activeSession != null || bedtimeActive) return
        val wm = windowManager ?: return
        val now = System.currentTimeMillis()
        // A baby that plays outside is the naughty one; without one, the big ghost
        val g = pickPeeker(now) ?: mother?.takeIf {
            it.isShown && !it.userDragging && it !in dying && it.neglectLevel(now) == 0 && it.pendingNeed(now) == null
        }
        if (g == null) {
            if (test) notice?.show(getString(R.string.notice_no_baby), 5_000L)
            return
        }
        val r = g.rect()
        val cx = r.centerX().toFloat()
        val cy = r.centerY().toFloat()
        val title = ghostTitle(g)
        val gv = g.ghost
        lastMischiefAt = now
        val p = getSharedPreferences("ghost_prefs", MODE_PRIVATE)
        val today = dayNumber()
        val count = if (p.getInt("mischief_day", -1) == today) p.getInt("mischief_count", 0) else 0
        if (!test) p.edit().putInt("mischief_day", today).putInt("mischief_count", count + 1).apply()

        fun at(ms: Long, block: () -> Unit) {
            handler.postDelayed({ if (!destroyed) block() }, ms)
        }
        fun effect(kind: MischiefView.Effect, ms: Long, duration: Long) {
            at(ms) {
                val mw = MischiefWindow(this, wm)
                if (mw.show(kind, cx, cy, duration) { mischiefWindow = null }) mischiefWindow = mw
            }
        }
        when (Random.nextInt(4)) {
            0 -> { // the "broken screen" joke
                gv.funnyMove(0)
                notice?.show("$title يشاغب... أوبس!", 3_000L)
                effect(MischiefView.Effect.CRACK, 1_400L, 7_000L)
                at(2_000L) { gv.funnyMove(4) }
                at(3_800L) { gv.funnyMove(1) }
                at(6_300L) { notice?.show("مزحة! الهاتف سليم تمامًا", 3_500L) }
            }
            1 -> { // throwing colours
                gv.funnyMove(2)
                notice?.show("$title يرمي الألوان!", 3_000L)
                effect(MischiefView.Effect.SPLAT, 1_000L, 7_000L)
                at(2_500L) { gv.funnyMove(1) }
                at(4_500L) { gv.funnyMove(4) }
            }
            2 -> { // soap bubbles
                gv.funnyMove(3)
                notice?.show("$title ينفخ فقاعات الصابون!", 3_000L)
                effect(MischiefView.Effect.BUBBLES, 800L, 8_000L)
                at(3_000L) { gv.funnyMove(1) }
                at(5_500L) { gv.funnyMove(4) }
            }
            else -> { // a silly dance
                notice?.show("$title يرقص رقصة مضحكة!", 3_500L)
                for (i in 0 until 6) at(i * 1_400L) { gv.funnyMove(i) }
            }
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
        entering.remove(w)
        outside.remove(w)
        if (w === mother) {
            replaceMother(w)
        } else {
            babies.remove(w)
        }
        persist()
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
            outside.remove(heir)
            entering.remove(heir)
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

    // ---------- The babies' house ----------

    private fun showHouse() {
        if (house != null) return
        val wm = windowManager ?: return
        val h = HouseWindow(this, wm, { openHouseScreen() }, { x, y -> onHouseMoved(x, y) })
        // Where the child put the house last time, or next to the big ghost
        val sx = prefs().getInt("house_x", -1)
        val sy = prefs().getInt("house_y", -1)
        val dm = resources.displayMetrics
        if (sx >= 0 && sy >= 0 && sx + h.widthPx <= dm.widthPixels && sy + h.heightPx <= dm.heightPixels) {
            if (h.show(sx, sy)) house = h
            return
        }
        val spot = houseSpot(h.widthPx, h.heightPx) ?: return
        if (h.show(spot.x, spot.y)) house = h
    }

    /** The house stands on the side of the big ghost where there is room. */
    private fun houseSpot(w: Int, h: Int): Point? {
        val m = mother ?: return null
        val dm = resources.displayMetrics
        val gap = (6 * dm.density).toInt()
        val r = m.rect()
        var x = r.right + gap
        if (x + w > dm.widthPixels) x = r.left - gap - w
        x = x.coerceIn(0, maxOf(0, dm.widthPixels - w))
        val y = (r.bottom - h).coerceIn(0, maxOf(0, dm.heightPixels - h))
        return Point(x, y)
    }

    private fun score(w: GhostWindow, now: Long): Int =
        w.neglectLevel(now) * 10 + (if (w.pendingNeed(now) != null) 1 else 0)

    /**
     * Only one baby is outside at a time. They take turns: the next one comes out while the one
     * outside goes back in, and a baby that needs food or water comes out for it.
     */
    private fun manageHouse() {
        if (babies.isEmpty() || activeSession != null || bedtimeActive || peeker != null) return
        if (house == null) showHouse()
        if (house == null) return
        val now = System.currentTimeMillis()
        val inside = babies.filter { !it.isShown && it !in dying }

        // Nobody outside: let one out (the neediest first)
        if (outside.isEmpty()) {
            val first = inside.maxByOrNull { score(it, now) } ?: return
            exitHouse(first)
            return
        }
        if (outside.size > 1 || outside.any { it in entering }) return // a swap is in progress
        if (inside.isEmpty()) return
        if (now - lastSwapAt < SWAP_COOLDOWN_MS) return
        val current = outside.first()

        // Someone inside needs care more than the baby outside
        val needy = inside.maxByOrNull { score(it, now) }
        if (needy != null && score(needy, now) > score(current, now)) {
            swapIn(needy)
            return
        }
        // Otherwise take turns
        if (now - lastRotationAt > ROTATE_MS) {
            lastRotationAt = now
            // the baby that has been inside the longest comes out
            val next = inside.minByOrNull { it.lastShownAt } ?: return
            swapIn(next)
        }
    }

    private fun swapIn(next: GhostWindow) {
        val leaving = outside.filter { it !in entering && it.isShown }
        lastSwapAt = System.currentTimeMillis()
        exitHouse(next)
        // The baby that was outside goes home a few seconds later, so they meet and play at the door
        for (c in leaving) {
            entering.add(c)
            handler.postDelayed({ if (!destroyed) enterHouse(c) }, SWAP_OVERLAP_MS)
        }
    }

    private fun exitHouse(b: GhostWindow) {
        if (b.isShown || b in dying) return
        val h = house ?: return
        val door = h.doorPoint()
        h.openDoor(1_500L)
        if (b.show(door.x - b.widthPx / 2, door.y - b.heightPx)) {
            outside.add(b)
            b.fadeIn(350L)
            b.ghost.hopHappy()
            if (activeSession != null) b.setTouchable(false)
            b.ghost.setNeglect(minOf(3, b.neglectLevel(System.currentTimeMillis())))
            b.ghost.setNightSleepy(isSleepyHour(hourNow()))
            handler.postDelayed({ if (!destroyed) playHop(b) }, 900L)
        }
    }

    private fun enterHouse(c: GhostWindow) {
        if (destroyed) return
        if (!c.isShown) {
            entering.remove(c)
            outside.remove(c)
            return
        }
        // Wait while it is being cared for, dragged, hopping or asleep
        if (c === activeSession || c.userDragging || c in dying || c.isMoving || c.sleeping) {
            handler.postDelayed({ enterHouse(c) }, 4_000L)
            return
        }
        val h = house
        if (h == null) {
            c.remove()
            entering.remove(c)
            outside.remove(c)
            return
        }
        entering.add(c)
        val d = resources.displayMetrics.density
        val door = h.doorPoint()
        val tx = door.x - c.widthPx / 2
        val ty = door.y - c.heightPx
        val dist = hypot((tx - c.restX).toDouble(), (ty - c.restY).toDouble()).toFloat()
        if (dist > 150f * d) {
            // The house floats on its own and may be far away: hop towards the door in steps
            val step = 120f * d
            val nx = (c.restX + (tx - c.restX) / dist * step).toInt()
            val ny = (c.restY + (ty - c.restY) / dist * step).toInt()
            c.hopTo(nx, ny)
            handler.postDelayed({ enterHouse(c) }, 800L)
            return
        }
        h.openDoor(1_700L)
        c.hopTo(tx, ty)
        handler.postDelayed({
            if (destroyed) return@postDelayed
            if (c.isShown && c !== activeSession) {
                c.fadeOutAndRemove(300L) {
                    entering.remove(c)
                    outside.remove(c)
                    persist()
                }
            } else {
                entering.remove(c)
                outside.remove(c)
            }
        }, 850L)
    }

    /** A baby outside jumps to another spot around the big ghost. */
    private fun playHop(b: GhostWindow) {
        val m = mother ?: return
        if (bedtimeActive || activeSession != null || b.userDragging || b.isMoving || !b.isShown) return
        if (b in entering || b in dying || b.sleeping) return
        if (SystemClock.uptimeMillis() < reactLockUntil) return
        if (b.neglectLevel(System.currentTimeMillis()) >= 1) return // a sad or sick baby does not play
        val d = resources.displayMetrics.density
        val mr = m.rect()
        val cx = mr.centerX().toFloat()
        val cy = mr.centerY().toFloat()
        val bx = b.restX + b.widthPx / 2f
        val by = b.restY + b.heightPx / 2f
        val dx = cx - bx
        val dy = cy - by
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist > 230f * d) {
            // Far from the big ghost (the house floats on its own): hop towards her in steps
            val step = 130f * d
            b.hopTo((bx + dx / dist * step - b.widthPx / 2f).toInt(), (by + dy / dist * step - b.heightPx / 2f).toInt())
            return
        }
        val occupied = occupiedRects(b)
        for (attempt in 0 until 8) {
            val angle = Random.nextFloat() * 6.2831855f
            val radius = (80f + Random.nextFloat() * 70f) * d
            val x = (cx + cos(angle) * radius - b.widthPx / 2f).toInt()
            val y = (cy + sin(angle) * radius * 0.6f - b.heightPx / 2f).toInt()
            if (isSpotFree(x, y, b.widthPx, b.heightPx, occupied)) {
                b.hopTo(x, y)
                return
            }
        }
        b.ghost.hopHappy() // no free spot nearby: hop on the spot
    }

    /** A baby plays on the spot with its own moves (spin, bounces, dance, peek). */
    private fun playInPlace(b: GhostWindow) {
        if (activeSession != null || b.userDragging || b.isMoving || !b.isShown) return
        val now = SystemClock.uptimeMillis()
        if (now < reactLockUntil) return
        if (b.neglectLevel(System.currentTimeMillis()) >= 1) return
        reactLockUntil = now + 1300L
        b.ghost.playRandom()
    }

    // ---------- Egg and babies ----------

    /** The big ghost lays one egg per day, after she was fed and watered. */
    private fun maybeLayEgg() {
        if (egg != null || activeSession != null || bedtimeActive) return
        if (!fedFlag || !waterFlag) return
        if (prefs().getInt(KEY_LAST_EGG_DAY, -1) == dayNumber()) return
        layEgg(EGG_INCUBATION_MS, false)
    }

    private fun layEgg(durationMs: Long, test: Boolean) {
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
        // One egg in four is a mystery egg with twins inside (the test egg always is)
        e.mystery = test || kotlin.random.Random.nextInt(4) == 0
        if (!e.show()) return
        egg = e
        if (!test) {
            fedFlag = false
            waterFlag = false
            prefs().edit().putInt(KEY_LAST_EGG_DAY, dayNumber()).apply()
        }
        m.ghost.showFed() // the big ghost is happy after laying
        showCuriousAll(spot.x + e.widthPx / 2) // everybody looks at the new egg
        reactLockUntil = SystemClock.uptimeMillis() + CELEBRATION_LOCK_MS
        showHouse()
        persist()
    }

    /** The egg finished hatching: the newborn takes its place with its own colour. */
    override fun onHatched(egg: EggWindow) {
        val wm = windowManager
        if (wm != null) {
            val b = GhostWindow(
                this, wm, "baby_${babies.size + 1}", Family.babyScale(0), babyTotal, this
            )
            b.twin = egg.mystery
            babyTotal++
            val now = System.currentTimeMillis()
            b.nextWaterAt = now + BABY_FIRST_WATER_MS
            b.nextFoodAt = now + BABY_FIRST_FOOD_MS
            val density = resources.displayMetrics.density
            val bx = egg.x + (egg.widthPx - b.boxWidthPx) / 2
            val by = egg.y + egg.heightPx - b.heightPx - (EggWindow.BABY_BOTTOM_MARGIN_DP * density).toInt()
            babies.add(b)
            showHouse()
            if (b.show(bx, by)) {
                // The babies that were outside go home after a moment; the newborn stays out to play
                val leaving = outside.filter { it !in entering && it.isShown }
                outside.add(b)
                if (activeSession != null) b.setTouchable(false)
                b.ghost.showFed() // newborn hop and smile
                lastSwapAt = now
                for (c in leaving) {
                    entering.add(c)
                    handler.postDelayed({ if (!destroyed) enterHouse(c) }, SWAP_OVERLAP_MS)
                }
                b.ghost.setNightSleepy(isSleepyHour(hourNow()))
                // Every baby has its own character: tell which one this baby has
                val traits = resources.getStringArray(R.array.personality_names)
                val trait = traits[Family.personalityFor(b.colorIndex).ordinal]
                var text = getString(R.string.notice_hatched, ghostTitle(b), trait)
                if (b.twin) text = "فقس توأمان! يتصرفان معًا كأنهما واحد.\n" + text
                val gifts = Wardrobe.recordHatched(this)
                HomeStore.addMaterials(this, 3)
                if (gifts.isNotEmpty()) text += "\n" + giftText(gifts)
                notice?.show(text, 9_000L)
                showCuriousAll(bx + b.widthPx / 2)
            }
        }
        egg.remove()
        this.egg = null
        reactLockUntil = SystemClock.uptimeMillis() + CELEBRATION_LOCK_MS
        persist()
    }

    // ---------- Free spots on the screen ----------

    private fun occupiedRects(exclude: GhostWindow? = null, includeHouse: Boolean = true): List<Rect> {
        val list = ArrayList<Rect>()
        for (w in shownGhosts()) if (w !== exclude) list.add(w.rect())
        egg?.let { list.add(it.rect()) }
        visitor?.let { if (it.isShown) list.add(it.rect()) }
        portal?.let { list.add(it.rect()) }
        if (includeHouse) house?.let { list.add(it.rect()) }
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

    /** Looks for a free spot near [near] that does not overlap any ghost, the house or the egg. */
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

    // ---------- Seasons: one season a day, it disappears after 15 minutes ----------

    private fun maybeStartSeason() {
        if (seasonWindow != null || bedtimeActive) return
        if (System.currentTimeMillis() - serviceStartedAt < SEASON_START_DELAY_MS) return
        val day = dayNumber()
        if (prefs().getInt(KEY_SEASON_DAY, -1) == day) return
        prefs().edit().putInt(KEY_SEASON_DAY, day).apply()
        startSeason(day % 4, SEASON_DURATION_MS) // winter, spring, summer, autumn, one per day
    }

    private fun startSeason(season: Int, durationMs: Long) {
        val wm = windowManager ?: return
        seasonWindow?.remove()
        val sw = SeasonWindow(this, wm)
        val ok = sw.show(season, durationMs) { seasonWindow = null }
        if (ok) {
            seasonWindow = sw
            val messages = resources.getStringArray(R.array.season_messages)
            if (season in messages.indices) notice?.show(messages[season], 6_000L)
            showCuriousAll(if (Random.nextBoolean()) 0 else resources.displayMetrics.widthPixels)
        }
    }

    // ---------- Encouraging texts with the children's names ----------

    /** About once an hour: a greeting in the morning, then play and encouragement texts. */
    private fun maybeSpeakPhrase() {
        val n = notice ?: return
        if (activeSession != null || n.isShowing || bedtimeActive) return
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

    // ---------- Day and night: sleepy evening, bedtime in the ghosts' room ----------

    private fun hourNow(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    private fun isBedtimeHour(h: Int): Boolean =
        h >= Features.BEDTIME_START_HOUR && h < Features.BEDTIME_END_HOUR

    private fun isSleepyHour(h: Int): Boolean = h >= Features.SLEEPY_EVENING_HOUR || isBedtimeHour(h)

    /** In the evening and at night the ghosts look sleepy. */
    private fun updateDayMood() {
        val sleepy = isSleepyHour(hourNow())
        for (w in shownGhosts()) w.ghost.setNightSleepy(sleepy)
    }

    /** By the phone's clock: at bedtime the ghosts go to their beds, in the morning they wake up. */
    private fun checkBedtime() {
        val h = hourNow()
        if (isBedtimeHour(h)) {
            if (!bedtimeActive && activeSession == null) enterBedtime()
        } else if (bedtimeActive) {
            exitBedtime()
        }
    }

    private fun enterBedtime() {
        val wm = windowManager ?: return
        val dm = resources.displayMetrics
        val r = RoomWindow(this, wm)
        val rx = (dm.widthPixels - r.widthPx) / 2
        val ry = (dm.heightPixels - r.heightPx - dp(80)).coerceAtLeast(0)
        if (!r.show(rx, ry)) return
        room = r
        bedtimeActive = true
        lastLoopWall = System.currentTimeMillis()
        cancelSurprises()

        // The house floats out of the way, to the top right
        house?.let { it.glideTo(dm.widthPixels - it.widthPx - dp(12), dp(90), 3_000L) }

        val sleepers = shownGhosts()
            .filter { it !in dying && it !in entering }
            .sortedByDescending { it.widthPx }
            .take(RoomWindow.SLOTS)
        savedPos.clear()
        for ((i, g) in sleepers.withIndex()) {
            savedPos[g] = Point(g.restX, g.restY)
            g.bringToFront() // drawn above the room
            val slot = r.slotCenter(i)
            g.goToBed(slot.x - g.widthPx / 2, slot.y - (g.heightPx * 0.93f).toInt())
        }
        notice?.show(getString(R.string.notice_bedtime), 6_000L)
    }

    private fun exitBedtime() {
        bedtimeActive = false
        for ((g, pos) in savedPos) {
            if (g.isShown) g.wakeUp(pos.x, pos.y)
        }
        savedPos.clear()
        room?.fadeOutAndRemove()
        room = null
        nextHouseDriftAt = System.currentTimeMillis() + 10_000L
        reactLockUntil = SystemClock.uptimeMillis() + CELEBRATION_LOCK_MS
        notice?.show(getString(R.string.notice_wake), 6_000L)
    }

    /** Hunger, thirst and the neglect clock stand still while the ghosts sleep. */
    private fun pauseTimers(deltaMs: Long) {
        for (g in allGhosts()) {
            if (g.nextFoodAt != 0L) g.nextFoodAt += deltaMs
            if (g.nextWaterAt != 0L) g.nextWaterAt += deltaMs
            if (g.nextBathAt != 0L) g.nextBathAt += deltaMs
        }
    }

    /** The child moved the house: it stays there (also after a restart) and does not float away for a while. */
    private fun onHouseMoved(x: Int, y: Int) {
        prefs().edit().putInt("house_x", x).putInt("house_y", y).apply()
        nextHouseDriftAt = System.currentTimeMillis() + 30L * 60L * 1000L
    }

    // ---------- The floating house moves on its own ----------

    private fun maybeDriftHouse() {
        val h = house ?: return
        if (bedtimeActive || activeSession != null || h.isGliding || h.isDragging) return
        val now = System.currentTimeMillis()
        if (now < nextHouseDriftAt) return
        nextHouseDriftAt = now + HOUSE_DRIFT_MIN_MS +
            Random.nextLong(HOUSE_DRIFT_MAX_MS - HOUSE_DRIFT_MIN_MS)
        val dm = resources.displayMetrics
        val margin = dp(8)
        val top = dp(60)
        val bottom = dp(100)
        val occupied = occupiedRects(null, false)
        for (attempt in 0 until 10) {
            val x = margin + Random.nextInt(maxOf(1, dm.widthPixels - h.widthPx - 2 * margin))
            val y = top + Random.nextInt(maxOf(1, dm.heightPixels - h.heightPx - top - bottom))
            if (isSpotFree(x, y, h.widthPx, h.heightPx, occupied)) {
                h.glideTo(x, y, 7_000L)
                return
            }
        }
    }

    // ---------- Outfits, gifts and curiosity ----------

    private fun applyOutfitToAll() {
        for (w in shownGhosts()) w.applyOutfit()
        house?.applyLook()
    }

    private fun giftText(items: List<Wardrobe.Item>): String =
        getString(R.string.notice_gift, items.joinToString("، ") { getString(it.nameRes) })

    private fun announceGifts(items: List<Wardrobe.Item>) {
        if (items.isEmpty()) return
        notice?.show(giftText(items), 7_000L)
    }

    /** Something new appeared: everybody looks at it with curiosity. */
    private fun showCuriousAll(targetX: Int) {
        for (g in shownGhosts()) {
            if (g.sleeping || g in dying) continue
            val dir = if (targetX < g.rect().centerX()) -1f else 1f
            g.ghost.showCurious(dir)
        }
    }

    // ---------- Surprises: a ghost peeking from the edge, the magic portal ----------

    private fun pickPeeker(now: Long): GhostWindow? = outside.filter {
        it.isShown && it !in entering && it !in dying && !it.isMoving && !it.userDragging &&
            !it.sleeping && it.neglectLevel(now) == 0 && it.pendingNeed(now) == null
    }.randomOrNull()

    /** Sometimes a baby disappears and peeks in from the screen's edge to see what the others do. */
    private fun maybePeek() {
        if (peek != null || portal != null || peeker != null || bedtimeActive || activeSession != null) return
        val now = System.currentTimeMillis()
        if (now - serviceStartedAt < 120_000L || now - lastPeekAt < PEEK_COOLDOWN_MS) return
        if (Random.nextFloat() > 0.15f) return // random: not every time
        val b = pickPeeker(now) ?: return
        startPeek(b)
    }

    private fun peekNow() {
        if (peek != null || peeker != null || bedtimeActive || activeSession != null) return
        val b = pickPeeker(System.currentTimeMillis())
        if (b == null) {
            notice?.show(getString(R.string.notice_no_baby), 5_000L)
            return
        }
        startPeek(b)
    }

    private fun startPeek(b: GhostWindow) {
        val wm = windowManager ?: return
        val pw = PeekWindow(this, wm, b.colorIndex, b.scale, b.personality, this)
        lastPeekAt = System.currentTimeMillis()
        val x = b.restX
        val y = b.restY
        // The ghost disappears from the screen...
        b.remove()
        outside.remove(b)
        entering.remove(b)
        peeker = b
        if (pw.start()) {
            peek = pw
        } else {
            peeker = null
            returnOutside(b, x, y)
        }
    }

    override fun onPeekEnded(jumpedInside: Boolean, centerX: Int, centerY: Int) {
        val b = peeker
        peek = null
        peeker = null
        lastPeekAt = System.currentTimeMillis()
        if (b == null || destroyed) return
        // Tapped: it jumps inside. Otherwise it ran away and is back in the house.
        if (jumpedInside) returnOutside(b, centerX - b.widthPx / 2, centerY - b.heightPx / 2)
    }

    private fun returnOutside(b: GhostWindow, x: Int, y: Int) {
        if (destroyed || b in dying) return
        if (b.show(x, y)) {
            outside.add(b)
            b.fadeIn(300L)
            b.ghost.hopHappy()
            b.ghost.setNightSleepy(isSleepyHour(hourNow()))
            b.ghost.setNeglect(minOf(3, b.neglectLevel(System.currentTimeMillis())))
            if (activeSession != null) b.setTouchable(false)
        }
    }

    private fun maybeOpenPortal() {
        if (portal != null || peek != null || bedtimeActive || activeSession != null) return
        val now = System.currentTimeMillis()
        if (now - serviceStartedAt < 180_000L || now - lastPortalAt < PORTAL_COOLDOWN_MS) return
        if (Random.nextFloat() > 0.08f) return
        openPortal()
    }

    /** A magic portal opens: a head comes out, then a star or a gift, or a stranger who visits. */
    private fun openPortal() {
        val wm = windowManager ?: return
        if (portal != null || bedtimeActive) return
        val dm = resources.displayMetrics
        val w = PortalWindow.widthPx(dm.density)
        val h = PortalWindow.heightPx(dm.density)
        val occupied = occupiedRects()
        var spot: Point? = null
        for (attempt in 0 until 12) {
            val x = Random.nextInt(maxOf(1, dm.widthPixels - w))
            val y = dp(80) + Random.nextInt(maxOf(1, dm.heightPixels - h - dp(80) - dp(100)))
            if (isSpotFree(x, y, w, h, occupied)) {
                spot = Point(x, y)
                break
            }
        }
        val where = spot ?: findFreeSpot(w, h, mother?.rect() ?: Rect(0, 0, 0, 0))
        val roll = Random.nextFloat()
        val event = when {
            roll < 0.35f -> PortalWindow.Event.STAR
            roll < 0.60f -> PortalWindow.Event.GIFT
            roll < 0.85f -> PortalWindow.Event.VISITOR
            else -> PortalWindow.Event.EMPTY
        }
        val pw = PortalWindow(this, wm, this)
        if (pw.show(where.x, where.y, event)) {
            portal = pw
            lastPortalAt = System.currentTimeMillis()
            showCuriousAll(where.x + w / 2)
        }
    }

    override fun onToss(isGift: Boolean, screenX: Int, screenY: Int) {
        val wm = windowManager ?: return
        val d = resources.displayMetrics.density
        val angle = Random.nextFloat() * 6.2831855f
        val radius = (90f + Random.nextFloat() * 60f) * d
        val tx = (screenX + cos(angle) * radius).toInt()
        val ty = (screenY + sin(angle) * radius * 0.6f).toInt()
        val pk = PickupWindow(this, wm, isGift, this)
        if (pk.show(screenX, screenY, tx, ty)) pickups.add(pk)
    }

    override fun onVisitorOut(colorIndex: Int, screenX: Int, screenY: Int) {
        val wm = windowManager ?: return
        if (visitor != null) return
        val v = GhostWindow(this, wm, "visitor", 0.7f, colorIndex, this)
        if (!v.show(screenX - v.widthPx / 2, screenY - v.heightPx)) return
        v.ghost.setOutfit(1 + Random.nextInt(3), 0, 0, 0, 0)
        v.fadeIn(300L)
        v.ghost.hopHappy()
        visitor = v
        visitorLeaving = false
        visitorLeaveAt = System.currentTimeMillis() + VISIT_MS
        showCuriousAll(screenX)
        notice?.show(getString(R.string.notice_visitor), 6_000L)
    }

    override fun onCloseRequested(): Boolean {
        if (visitor != null) {
            visitorGoHome() // the visitor goes home first, then the portal closes
            return false
        }
        return true
    }

    override fun onClosed() {
        portal = null
        lastPortalAt = System.currentTimeMillis()
    }

    private fun checkVisitor() {
        if (visitor == null) return
        if (System.currentTimeMillis() >= visitorLeaveAt) visitorGoHome()
    }

    private fun visitorGoHome() {
        if (visitor == null || visitorLeaving) return
        visitorLeaving = true
        visitorStep()
    }

    /** The stranger hops back to the portal (in steps if it is far) and goes in. */
    private fun visitorStep() {
        if (destroyed) return
        val v = visitor ?: return
        val p = portal
        if (p == null || !v.isShown) {
            v.remove()
            visitor = null
            return
        }
        if (activeSession != null || v.userDragging || v.isMoving) {
            handler.postDelayed({ visitorStep() }, 1_500L)
            return
        }
        val d = resources.displayMetrics.density
        val r = p.rect()
        val tx = r.centerX() - v.widthPx / 2
        val ty = p.mouthY() - v.heightPx
        val dist = hypot((tx - v.restX).toDouble(), (ty - v.restY).toDouble()).toFloat()
        if (dist > 150f * d) {
            val step = 130f * d
            v.hopTo(
                (v.restX + (tx - v.restX) / dist * step).toInt(),
                (v.restY + (ty - v.restY) / dist * step).toInt()
            )
            handler.postDelayed({ visitorStep() }, 800L)
            return
        }
        v.hopTo(tx, ty)
        handler.postDelayed({
            if (destroyed) return@postDelayed
            v.fadeOutAndRemove(300L) {
                if (visitor === v) visitor = null
                portal?.visitorFinished()
            }
        }, 850L)
    }

    /** The child touched a star or a gift: it becomes a collected star or a new wardrobe item. */
    override fun onCollected(w: PickupWindow, isGift: Boolean) {
        pickups.remove(w)
        val text: String
        if (isGift) {
            val item = Wardrobe.giftRandom(this)
            if (item != null) {
                text = giftText(listOf(item))
            } else {
                // everything is unlocked already: stars instead
                val extra = Wardrobe.addStars(this, 3)
                text = getString(R.string.notice_gift_duplicate) +
                    (if (extra.isNotEmpty()) "\n" + giftText(extra) else "")
            }
        } else {
            val unlocked = Wardrobe.addStars(this, 1)
            text = getString(R.string.notice_star, Wardrobe.stars(this)) +
                (if (unlocked.isNotEmpty()) "\n" + giftText(unlocked) else "")
        }
        notice?.show(text, 6_000L)
    }

    override fun onExpired(w: PickupWindow) {
        pickups.remove(w)
    }

    /** At bedtime and when the app closes, all surprises disappear. */
    private fun cancelSurprises() {
        peek?.cancel()
        peek = null
        peeker = null // it is hidden already: it simply stays in the house
        portal?.closeNow()
        portal = null
        visitor?.remove()
        visitor = null
        for (p in pickups.toList()) p.removeNow()
        pickups.clear()
    }

    /** Touching the floating house opens the house screen (the inside with the sleeping ghosts). */
    private fun openHouseScreen() {
        if (activeSession != null) return
        try {
            startActivity(
                Intent(this, HouseActivity::class.java)
                    .putExtra(HouseActivity.EXTRA_INTERIOR, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not open the house screen", e)
        }
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
