package com.cuteghost.app

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * One ghost (the big one or a baby). It owns the ghost's data (colour, needs, position) and, while
 * it is shown, its own overlay window: drawing, dragging, taps and the feeding / watering
 * sessions. The big ghost and every baby use exactly the same care logic.
 *
 * A ghost can be hidden (window removed) without losing anything: its needs keep their timers.
 */
class GhostWindow(
    private val context: Context,
    private val wm: WindowManager,
    val id: String,
    private val scale: Float,
    /** Index into the family colours, or -1 for the original white ghost. */
    val colorIndex: Int,
    private val host: Host
) : FeedView.Listener {

    interface Host {
        fun canDrag(who: GhostWindow): Boolean
        fun onTap(who: GhostWindow)
        fun onMoved(who: GhostWindow)
        fun onSessionEnded(who: GhostWindow, mode: FeedView.Mode, completed: Boolean)
    }

    companion object {
        private const val TAG = "GhostWindow"
        const val BASE_WIDTH_DP = 100
        const val BASE_HEIGHT_DP = 128
        private const val TAP_MAX_MS = 450L
        private const val FEED_TIMEOUT_MS = 3L * 60L * 1000L
        private const val FEED_WINDOW_WIDTH_DP = 240
        private const val BUBBLE_HEIGHT_DP = 44
        private const val TRAY_HEIGHT_DP = 90

        // Needs: food every hour, water every half hour (same for everyone)
        const val HUNGER_INTERVAL_MS = 60L * 60L * 1000L
        const val THIRST_INTERVAL_MS = 30L * 60L * 1000L
        const val RETRY_MS = 5L * 60L * 1000L
        const val MIN_GAP_MS = 5L * 60L * 1000L

        // Neglect, counted from the moment a need became due and was not satisfied
        const val NEGLECT_GRACE_MS = 3L * 60L * 1000L          // then: a tear
        const val CRY_AFTER_MS = 30L * 60L * 1000L              // half an hour later: crying
        const val SICK_AFTER_MS = 60L * 60L * 1000L             // another half hour: sick
        const val DEATH_AFTER_MS = 24L * 60L * 60L * 1000L      // 24 hours: gone for good
    }

    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density

    val widthPx = Family.sizePx(BASE_WIDTH_DP, scale, density)
    val heightPx = Family.sizePx(BASE_HEIGHT_DP, scale, density)

    var nextFoodAt = 0L
    var nextWaterAt = 0L

    /** The ghost does not ask again before this time (it still can be tapped to be cared for). */
    var askAfter = 0L

    var feeding = false
        private set
    var userDragging = false
        private set
    private var currentMode: FeedView.Mode? = null

    var hasPosition = false
        private set

    /** Resting position of the ghost box (the window itself is larger while a session is open). */
    var restX = 0
        private set
    var restY = 0
        private set

    var shownSince = 0L
        private set
    var lastShownAt = 0L
        private set

    private var rootView: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var feed: FeedView? = null
    lateinit var ghost: GhostView
        private set

    val isShown: Boolean get() = rootView != null

    private val feedTimeout = Runnable { endSession(false) }

    private fun dp(v: Int): Int = (v * density).toInt()

    fun rect(): Rect = Rect(restX, restY, restX + widthPx, restY + heightPx)

    fun setSavedPosition(x: Int, y: Int) {
        restX = x
        restY = y
        hasPosition = true
    }

    // =====================================================================
    // Window
    // =====================================================================

    fun show(x: Int, y: Int): Boolean {
        val dm = context.resources.displayMetrics
        val p = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        restX = x.coerceIn(0, maxOf(0, dm.widthPixels - widthPx))
        restY = y.coerceIn(0, maxOf(0, dm.heightPixels - heightPx))
        hasPosition = true
        p.x = restX
        p.y = restY

        val g = GhostView(context)
        g.setBodyColor(if (colorIndex >= 0) Family.color(colorIndex) else null)
        // Babies play differently from the big ghost
        g.playful = scale < 1f
        val f = FeedView(context)
        f.ghost = g
        f.listener = this
        f.mouthZoneScale = maxOf(0.8f, scale)
        f.visibility = View.GONE

        val root = FrameLayout(context)
        root.addView(
            g,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        root.addView(
            f,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        ghost = g
        feed = f
        attachTouchHandling(g, p)

        return try {
            wm.addView(root, p)
            rootView = root
            params = p
            shownSince = System.currentTimeMillis()
            lastShownAt = shownSince
            true
        } catch (e: RuntimeException) {
            // BadTokenException / SecurityException when the permission is missing or revoked
            Log.e(TAG, "Could not add overlay view", e)
            false
        }
    }

    /** Removes the window. The ghost's data (needs, position, colour) stays. */
    fun remove() {
        handler.removeCallbacksAndMessages(null)
        feeding = false
        val root = rootView ?: return
        lastShownAt = System.currentTimeMillis()
        try {
            wm.removeViewImmediate(root)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Overlay view was already removed", e)
        }
        rootView = null
        params = null
        feed = null
    }

    /** Fades the ghost out and removes the window, then calls [onDone]. */
    fun fadeOutAndRemove(durationMs: Long, onDone: () -> Unit) {
        val root = rootView
        if (root == null) {
            onDone()
            return
        }
        setTouchable(false)
        root.animate().alpha(0f).setDuration(durationMs).withEndAction {
            remove()
            onDone()
        }.start()
    }

    private fun updateWindow() {
        val root = rootView ?: return
        val p = params ?: return
        try {
            wm.updateViewLayout(root, p)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not update overlay layout", e)
        }
    }

    /** While another ghost is in a session this window ignores touches (they pass through). */
    fun setTouchable(touchable: Boolean) {
        val p = params ?: return
        val flag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val newFlags = if (touchable) p.flags and flag.inv() else p.flags or flag
        if (newFlags != p.flags) {
            p.flags = newFlags
            updateWindow()
        }
    }

    private fun attachTouchHandling(view: GhostView, p: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
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
                    startX = p.x
                    startY = p.y
                    dragging = false
                    view.onTouchStart()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Ghosts cannot be moved while a feeding / watering session is open
                    if (!feeding && host.canDrag(this)) {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                            dragging = true
                            userDragging = true
                            view.onDragStart()
                        }
                        if (dragging) {
                            val dm = context.resources.displayMetrics
                            p.x = (startX + dx).toInt().coerceIn(0, maxOf(0, dm.widthPixels - p.width))
                            p.y = (startY + dy).toInt().coerceIn(0, maxOf(0, dm.heightPixels - p.height))
                            restX = p.x
                            restY = p.y
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
                        host.onMoved(this)
                    } else if (event.eventTime - event.downTime <= TAP_MAX_MS) {
                        // Only a short press without movement counts as a tap
                        host.onTap(this)
                        v.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        userDragging = false
                        view.onDragEnd()
                        host.onMoved(this)
                    }
                    true
                }
                else -> false
            }
        }
    }

    // =====================================================================
    // Needs, neglect and sessions
    // =====================================================================

    /**
     * 0 healthy, 1 a tear, 2 crying, 3 sick, 4 gone for good. Counted from the earliest need that
     * became due and was not satisfied; it ends only when everything due has been taken care of.
     */
    fun neglectLevel(now: Long): Int {
        if (nextFoodAt == 0L || nextWaterAt == 0L) return 0
        val elapsed = now - minOf(nextFoodAt, nextWaterAt)
        return when {
            elapsed >= DEATH_AFTER_MS -> 4
            elapsed >= NEGLECT_GRACE_MS + SICK_AFTER_MS -> 3
            elapsed >= NEGLECT_GRACE_MS + CRY_AFTER_MS -> 2
            elapsed >= NEGLECT_GRACE_MS -> 1
            else -> 0
        }
    }

    private fun dueNeedInternal(now: Long, ignoreAskAfter: Boolean): Pair<FeedView.Mode, Long>? {
        if (nextFoodAt == 0L) nextFoodAt = now + HUNGER_INTERVAL_MS
        if (nextWaterAt == 0L) nextWaterAt = now + THIRST_INTERVAL_MS
        if (!ignoreAskAfter && now < askAfter) return null
        val food = now >= nextFoodAt
        val water = now >= nextWaterAt
        return when {
            food && water ->
                if (nextFoodAt <= nextWaterAt) Pair(FeedView.Mode.FOOD, nextFoodAt)
                else Pair(FeedView.Mode.WATER, nextWaterAt)
            food -> Pair(FeedView.Mode.FOOD, nextFoodAt)
            water -> Pair(FeedView.Mode.WATER, nextWaterAt)
            else -> null
        }
    }

    /** The need to ask for now (respecting the retry pause), earliest first, or null. */
    fun dueNeed(now: Long): Pair<FeedView.Mode, Long>? = dueNeedInternal(now, false)

    /** Any need that is due, even during the retry pause (used when the child taps the ghost). */
    fun pendingNeed(now: Long): Pair<FeedView.Mode, Long>? = dueNeedInternal(now, true)

    /** Expands the window around the ghost (text above, tray below) and shows the need. */
    fun startSession(mode: FeedView.Mode): Boolean {
        val p = params ?: return false
        val root = rootView ?: return false
        val f = feed ?: return false
        if (feeding || userDragging) return false

        val dm = context.resources.displayMetrics
        val winW = dp(FEED_WINDOW_WIDTH_DP)
        val bubbleH = dp(BUBBLE_HEIGHT_DP)
        val trayH = dp(TRAY_HEIGHT_DP)
        val winH = bubbleH + heightPx + trayH

        val wx = (restX - (winW - widthPx) / 2).coerceIn(0, maxOf(0, dm.widthPixels - winW))
        val wy = (restY - bubbleH).coerceIn(0, maxOf(0, dm.heightPixels - winH))

        p.x = wx
        p.y = wy
        p.width = winW
        p.height = winH
        val lp = FrameLayout.LayoutParams(widthPx, heightPx, Gravity.TOP or Gravity.START)
        lp.leftMargin = (restX - wx).coerceIn(0, maxOf(0, winW - widthPx))
        lp.topMargin = bubbleH
        ghost.layoutParams = lp

        // Re-add the window so it is drawn and touched above the other ghosts
        try {
            wm.removeViewImmediate(root)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Window was not attached", e)
        }
        try {
            wm.addView(root, p)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not re-add window", e)
            rootView = null
            return false
        }

        feeding = true
        currentMode = mode
        f.visibility = View.VISIBLE
        f.begin(winW, winH, bubbleH, trayH, mode)
        ghost.setFeedState(
            if (mode == FeedView.Mode.FOOD) GhostView.FeedState.HUNGRY else GhostView.FeedState.THIRSTY
        )
        handler.removeCallbacks(feedTimeout)
        handler.postDelayed(feedTimeout, FEED_TIMEOUT_MS)
        return true
    }

    override fun onFeedingEnded(fed: Boolean) {
        endSession(fed)
    }

    private fun endSession(completed: Boolean) {
        if (!feeding) return
        feeding = false
        handler.removeCallbacks(feedTimeout)

        feed?.let {
            it.end()
            it.visibility = View.GONE
        }
        ghost.setFeedState(GhostView.FeedState.NONE)

        val p = params
        if (p != null) {
            p.x = restX
            p.y = restY
            p.width = widthPx
            p.height = heightPx
            ghost.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            updateWindow()
        }

        val mode = currentMode ?: FeedView.Mode.FOOD
        val now = System.currentTimeMillis()
        if (completed) {
            // This need is satisfied. Anything else that is still due stays due (neglect goes on).
            if (mode == FeedView.Mode.WATER) {
                nextWaterAt = now + THIRST_INTERVAL_MS
            } else {
                nextFoodAt = now + HUNGER_INTERVAL_MS
            }
            askAfter = now + MIN_GAP_MS
        } else {
            // Nobody came: the need stays due and the ghost asks again in a few minutes
            askAfter = now + RETRY_MS
        }
        currentMode = null
        host.onSessionEnded(this, mode, completed)
    }
}
