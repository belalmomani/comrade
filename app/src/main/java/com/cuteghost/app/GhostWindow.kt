package com.cuteghost.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
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
import android.view.animation.LinearInterpolator
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
    initialScale: Float,
    /** Index into the family colours, or -1 for the original white ghost. */
    val colorIndex: Int,
    private val host: Host
) : FeedView.Listener {

    interface Host {
        fun canDrag(who: GhostWindow): Boolean
        fun onTap(who: GhostWindow, head: Boolean)
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

        /** Babies drink their milk every two hours. */
        const val MILK_INTERVAL_MS = 2L * 60L * 60L * 1000L
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

    /** 1.0 for the big ghost; babies start small and grow a little with every meal. */
    var scale: Float = initialScale
        private set

    /** Meals eaten so far (every meal makes a baby grow by 1%). */
    var meals = 0

    val widthPx: Int get() = Family.sizePx(BASE_WIDTH_DP, scale, density)

    /** True for twins: one baby (one set of needs, one colour) shown as two bodies that act as one. */
    var twin = false

    /** Width of the whole window: wider for twins. */
    val boxWidthPx: Int get() = if (twin) (widthPx * 1.8f).toInt() else widthPx
    private var twinView: GhostView? = null
    val heightPx: Int get() = Family.sizePx(BASE_HEIGHT_DP, scale, density)

    private val drinkIntervalMs: Long get() = if (id == "mother") THIRST_INTERVAL_MS else MILK_INTERVAL_MS

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

    /** Character of a baby (null for the big ghost). */
    val personality: Personality?
        get() = if (id != "mother" && colorIndex >= 0) Family.personalityFor(colorIndex) else null

    /** True while the ghost is asleep in its bed (or on its way there). */
    var sleeping = false
        private set

    private fun applyOutfitTo(g: GhostView) {
        g.setOutfit(
            Wardrobe.equipped(context, Wardrobe.Slot.HAT),
            Wardrobe.equipped(context, Wardrobe.Slot.SCARF),
            Wardrobe.equipped(context, Wardrobe.Slot.WINGS),
            Wardrobe.equipped(context, Wardrobe.Slot.GLOW),
            Wardrobe.equipped(context, Wardrobe.Slot.TRAIL)
        )
    }

    /** Puts on whatever is currently chosen in the wardrobe. */
    fun applyOutfit() {
        if (isShown) applyOutfitTo(ghost)
    }

    /** The head is the upper middle part of the ghost's box. */
    private fun isHead(x: Float, y: Float, w: Int, h: Int): Boolean {
        if (w <= 0 || h <= 0) return false
        val fx = x / w
        val fy = y / h
        return fx in 0.25f..0.75f && fy in 0.28f..0.58f
    }

    /** Re-adds the window so it is drawn above the other overlay windows. */
    fun bringToFront() {
        val root = rootView ?: return
        val p = params ?: return
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
        }
    }

    /** Hops to its bed and falls asleep there. */
    fun goToBed(x: Int, y: Int) {
        if (!isShown) return
        sleeping = true
        ghost.setForcedSleep(false)
        ghost.hopHappy()
        moveTo(x, y, 1_400L) {
            if (sleeping && isShown) ghost.setForcedSleep(true)
        }
    }

    /** Wakes up and goes back to where it was. */
    fun wakeUp(x: Int, y: Int) {
        sleeping = false
        if (!isShown) return
        ghost.setForcedSleep(false)
        moveTo(x, y, 1_200L)
    }

    private val feedTimeout = Runnable { endSession(false) }

    private fun dp(v: Int): Int = (v * density).toInt()

    fun rect(): Rect = Rect(restX, restY, restX + boxWidthPx, restY + heightPx)

    fun setSavedPosition(x: Int, y: Int) {
        restX = x
        restY = y
        hasPosition = true
    }

    // =====================================================================
    // Growth and movement of babies
    // =====================================================================

    private var moveAnimator: ValueAnimator? = null

    /** True while the window is travelling (hopping) to another place. */
    var isMoving = false
        private set

    /** A baby grows a little: resize keeping the bottom centre of the ghost where it is. */
    fun applyScale(newScale: Float) {
        val oldW = boxWidthPx
        val oldH = heightPx
        scale = newScale
        feed?.mouthZoneScale = maxOf(0.8f, scale)
        val dm = context.resources.displayMetrics
        val newX = (restX + (oldW - boxWidthPx) / 2).coerceIn(0, maxOf(0, dm.widthPixels - boxWidthPx))
        val newY = (restY + (oldH - heightPx)).coerceIn(0, maxOf(0, dm.heightPixels - heightPx))
        restX = newX
        restY = newY
        val p = params
        if (p != null && !feeding) {
            p.x = restX
            p.y = restY
            p.width = boxWidthPx
            p.height = heightPx
            updateWindow()
        }
    }

    /** Moves the window smoothly to ([x], [y]). */
    fun moveTo(x: Int, y: Int, durationMs: Long, onDone: (() -> Unit)? = null) {
        val p = params ?: return
        if (feeding) return
        moveAnimator?.cancel()
        val dm = context.resources.displayMetrics
        val tx = x.coerceIn(0, maxOf(0, dm.widthPixels - boxWidthPx))
        val ty = y.coerceIn(0, maxOf(0, dm.heightPixels - heightPx))
        val sx = p.x
        val sy = p.y
        isMoving = true
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = durationMs
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            val t = it.animatedFraction
            p.x = (sx + (tx - sx) * t).toInt()
            p.y = (sy + (ty - sy) * t).toInt()
            restX = p.x
            restY = p.y
            updateWindow()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                isMoving = false
                if (!cancelled) onDone?.invoke()
            }
        })
        moveAnimator = animator
        animator.start()
    }

    /** A happy hop to another spot (babies jump around the big ghost). */
    fun hopTo(x: Int, y: Int) {
        if (!isShown) return
        ghost.hopHappy()
        moveTo(x, y, 650L)
    }

    fun fadeIn(durationMs: Long) {
        val root = rootView ?: return
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(durationMs).start()
    }

    // =====================================================================
    // Window
    // =====================================================================

    fun show(x: Int, y: Int): Boolean {
        val dm = context.resources.displayMetrics
        val p = WindowManager.LayoutParams(
            boxWidthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        restX = x.coerceIn(0, maxOf(0, dm.widthPixels - boxWidthPx))
        restY = y.coerceIn(0, maxOf(0, dm.heightPixels - heightPx))
        hasPosition = true
        p.x = restX
        p.y = restY

        val g = GhostView(context)
        g.setBodyColor(if (colorIndex >= 0) Family.color(colorIndex) else null)
        g.cryWhenThirsty = id != "mother" // babies cry while they ask for milk
        // Babies play differently from the big ghost
        g.playful = scale < 1f
        // Every baby has its own character; the big ghost has none
        if (id != "mother" && colorIndex >= 0) g.personality = Family.personalityFor(colorIndex)
        applyOutfitTo(g)
        val f = FeedView(context)
        f.ghost = g
        f.listener = this
        f.mouthZoneScale = maxOf(0.8f, scale)
        // The water cup has the baby's colour (the big ghost keeps the blue one)
        if (colorIndex >= 0) f.cupColor = Family.color(colorIndex)
        f.milk = id != "mother" // babies drink milk from a bottle
        f.visibility = View.GONE

        val root = FrameLayout(context)
        if (twin) {
            // The second twin only draws what the first one does, so they move and act as one
            val t = GhostView(context)
            t.mirrorOf = g
            twinView = t
            root.addView(g, FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.START))
            root.addView(t, FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.END))
        } else {
            root.addView(
                g,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        root.addView(
            f,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        ghost = g
        feed = f
        attachTouchHandling(g, p)
        twinView?.setOnTouchListener { _, e -> g.dispatchTouchEvent(e) }

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
        moveAnimator?.cancel()
        isMoving = false
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
        twinView = null
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
                            moveAnimator?.cancel()
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
                        host.onTap(this, isHead(event.x, event.y, v.width, v.height))
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
        if (nextWaterAt == 0L) nextWaterAt = now + drinkIntervalMs
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
        twinView?.let { t ->
            val tl = FrameLayout.LayoutParams(widthPx, heightPx, Gravity.TOP or Gravity.START)
            tl.leftMargin = (lp.leftMargin + (widthPx * 0.8f).toInt()).coerceAtMost(maxOf(0, winW - widthPx))
            tl.topMargin = bubbleH
            t.layoutParams = tl
        }

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
            p.width = boxWidthPx
            p.height = heightPx
            if (twin) {
                ghost.layoutParams = FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.START)
                twinView?.layoutParams = FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.TOP or Gravity.END)
            } else {
                ghost.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            updateWindow()
        }

        val mode = currentMode ?: FeedView.Mode.FOOD
        val now = System.currentTimeMillis()
        if (completed) {
            // This need is satisfied. Anything else that is still due stays due (neglect goes on).
            if (mode == FeedView.Mode.WATER) {
                nextWaterAt = now + drinkIntervalMs
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
