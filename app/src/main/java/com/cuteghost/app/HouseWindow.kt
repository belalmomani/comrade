package com.cuteghost.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator

/** The floating house. It moves by itself and never takes touches. */
class HouseWindow(
    private val context: Context,
    private val wm: WindowManager,
    private val onTap: () -> Unit = {},
    /** Called with the new place after the house was dragged. */
    private val onMoved: (Int, Int) -> Unit = { _, _ -> }
) {
    companion object {
        const val WIDTH_DP = 96
        const val HEIGHT_DP = 104
        fun size(density: Float): Int = (WIDTH_DP * density).toInt()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    val widthPx = (WIDTH_DP * density).toInt()
    val heightPx = (HEIGHT_DP * density).toInt()

    var x = 0
        private set
    var y = 0
        private set
    var isGliding = false
        private set

    /** True while a finger is moving the house. */
    var isDragging = false
        private set
    private var view: HouseView? = null
    private var params: WindowManager.LayoutParams? = null
    private var glideAnimator: ValueAnimator? = null
    private var near = false
    private var forcedOpen = false
    private val releaseForced = Runnable {
        forcedOpen = false
        updateDoor()
    }

    fun rect(): Rect = Rect(x, y, x + widthPx, y + heightPx)

    /** Bottom centre of the door: where babies come out and go in. */
    fun doorPoint(): Point = Point(x + widthPx / 2, y + (heightPx * 0.83f).toInt())

    fun show(px: Int, py: Int): Boolean {
        val p = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        x = px
        y = py
        p.x = x
        p.y = y
        val v = HouseView(context)
        v.setLook(HomeStore.selected(context), HomeStore.level(context, HomeStore.selected(context)))
        // Touching the house opens it: the inside with the sleeping ghosts
        // A short touch opens it; dragging with a finger moves it to another place
        val dm = context.resources.displayMetrics
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = x
                    startY = y
                    isDragging = false
                    glideAnimator?.cancel()
                    isGliding = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (!isDragging && hypot(dx, dy) > slop) isDragging = true
                    if (isDragging) {
                        x = (startX + dx).toInt().coerceIn(0, maxOf(0, dm.widthPixels - widthPx))
                        y = (startY + dy).toInt().coerceIn(0, maxOf(0, dm.heightPixels - heightPx))
                        p.x = x
                        p.y = y
                        updateWindow()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                        onMoved(x, y)
                    } else if (e.eventTime - e.downTime <= 450L) {
                        openDoor(1_200L)
                        onTap()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                        onMoved(x, y)
                    }
                }
            }
            true
        }
        return try {
            wm.addView(v, p)
            view = v
            params = p
            true
        } catch (e: RuntimeException) {
            Log.e("HouseWindow", "Could not add the house", e)
            false
        }
    }

    /** Takes the design and level that were chosen in the house screen. */
    fun applyLook() {
        val id = HomeStore.selected(context)
        view?.setLook(id, HomeStore.level(context, id))
    }

    private fun updateWindow() {
        val v = view ?: return
        val p = params ?: return
        try {
            wm.updateViewLayout(v, p)
        } catch (e: IllegalArgumentException) {
            Log.w("HouseWindow", "Could not move the house", e)
        }
    }

    /** The house floats slowly to another place on the screen, on its own. */
    fun glideTo(px: Int, py: Int, durationMs: Long) {
        val p = params ?: return
        glideAnimator?.cancel()
        val sx = p.x
        val sy = p.y
        isGliding = true
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = durationMs
        a.interpolator = AccelerateDecelerateInterpolator()
        a.addUpdateListener {
            val t = it.animatedFraction
            x = (sx + (px - sx) * t).toInt()
            y = (sy + (py - sy) * t).toInt()
            p.x = x
            p.y = y
            updateWindow()
        }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isGliding = false
            }
        })
        glideAnimator = a
        a.start()
    }

    private fun updateDoor() {
        val v = view ?: return
        v.doorTarget = if (near || forcedOpen) 1f else 0f
    }

    /** Opens the door by itself when a ghost is near it. */
    fun setNear(isNear: Boolean) {
        if (near == isNear) return
        near = isNear
        updateDoor()
    }

    /** Keeps the door open for a while (a baby is going in or out). */
    fun openDoor(forMs: Long) {
        forcedOpen = true
        updateDoor()
        handler.removeCallbacks(releaseForced)
        handler.postDelayed(releaseForced, forMs)
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        glideAnimator?.cancel()
        isGliding = false
        val v = view ?: return
        try {
            wm.removeViewImmediate(v)
        } catch (e: IllegalArgumentException) {
            Log.w("HouseWindow", "House already removed", e)
        }
        view = null
        params = null
    }
}
