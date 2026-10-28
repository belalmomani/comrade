package com.cuteghost.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.hypot
import kotlin.random.Random

/**
 * A baby that disappeared from the screen peeks in from the top edge or from a corner, watches
 * for a few seconds and runs away. A tap makes it jump inside, except for a shy baby, which
 * hides when it is discovered. A curious baby comes closer while it watches.
 */
class PeekWindow(
    private val context: Context,
    private val wm: WindowManager,
    private val colorIndex: Int,
    scale: Float,
    private val personality: Personality?,
    private val host: Host
) {
    interface Host {
        /** The peek is over. [jumpedInside] is true when the baby was tapped and jumps in. */
        fun onPeekEnded(jumpedInside: Boolean, centerX: Int, centerY: Int)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private val boxW = Family.sizePx(GhostWindow.BASE_WIDTH_DP, scale, density)
    private val boxH = Family.sizePx(GhostWindow.BASE_HEIGHT_DP, scale, density)

    // A square big enough to hold the ghost turned to any angle
    private val side = hypot(boxW.toDouble(), boxH.toDouble()).toInt() + 4

    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var ghost: GhostView? = null
    private var animator: ValueAnimator? = null
    private var finished = false

    private var curX = 0f
    private var curY = 0f
    private var hiddenX = 0f
    private var hiddenY = 0f
    private var peekX = 0f
    private var peekY = 0f
    private var deepX = 0f
    private var deepY = 0f
    private var lookDir = 1f

    fun start(): Boolean {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val hs = dm.heightPixels.toFloat()
        val h = boxH.toFloat()
        val a = 0.22f * h

        // Which edge: the top edge (head hanging down) or one of the corners
        val r = Random.nextFloat()
        val rotation: Float
        val outX: Float
        val outY: Float
        when {
            r < 0.30f -> {
                rotation = 180f
                peekX = w * (0.2f + 0.6f * Random.nextFloat())
                peekY = 0.20f * h
                outX = 0f
                outY = -1f
            }
            r < 0.50f -> { // top left corner
                rotation = 135f
                peekX = a
                peekY = a
                outX = -0.707f
                outY = -0.707f
            }
            r < 0.70f -> { // top right corner
                rotation = 225f
                peekX = w - a
                peekY = a
                outX = 0.707f
                outY = -0.707f
            }
            r < 0.85f -> { // bottom left corner
                rotation = 45f
                peekX = a
                peekY = hs - a
                outX = -0.707f
                outY = 0.707f
            }
            else -> { // bottom right corner
                rotation = 315f
                peekX = w - a
                peekY = hs - a
                outX = 0.707f
                outY = 0.707f
            }
        }
        hiddenX = peekX + outX * 0.8f * h
        hiddenY = peekY + outY * 0.8f * h
        deepX = peekX - outX * 0.18f * h
        deepY = peekY - outY * 0.18f * h
        lookDir = if (peekX < w / 2f) 1f else -1f

        val g = GhostView(context)
        g.setBodyColor(if (colorIndex >= 0) Family.color(colorIndex) else null)
        g.playful = true
        g.personality = personality
        g.rotation = rotation
        val container = FrameLayout(context)
        container.addView(g, FrameLayout.LayoutParams(boxW, boxH, Gravity.CENTER))
        g.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP && e.eventTime - e.downTime <= 450L) onTapped()
            true
        }

        val p = WindowManager.LayoutParams(
            side,
            side,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, // the window may stick out of the screen
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        params = p
        ghost = g
        curX = hiddenX
        curY = hiddenY
        p.x = (curX - side / 2f).toInt()
        p.y = (curY - side / 2f).toInt()

        return try {
            wm.addView(container, p)
            root = container
            g.showCurious(lookDir)
            glide(peekX, peekY, 700L) {
                // It watches for a few seconds, then runs away
                g.showCurious(lookDir)
                val watch = 3_000L + Random.nextLong(3_000L)
                if (personality == Personality.CURIOUS) {
                    // The curious one comes closer
                    handler.postDelayed({ if (!finished) glide(deepX, deepY, 600L) { } }, watch / 3)
                }
                handler.postDelayed({ runAway() }, watch)
            }
            true
        } catch (e: RuntimeException) {
            Log.e("PeekWindow", "Could not add the peeking ghost", e)
            false
        }
    }

    private fun setCenter(x: Float, y: Float) {
        val c = root ?: return
        val p = params ?: return
        curX = x
        curY = y
        p.x = (x - side / 2f).toInt()
        p.y = (y - side / 2f).toInt()
        try {
            wm.updateViewLayout(c, p)
        } catch (e: IllegalArgumentException) {
            Log.w("PeekWindow", "Could not move the peeking ghost", e)
        }
    }

    private fun glide(toX: Float, toY: Float, ms: Long, onEnd: () -> Unit) {
        animator?.cancel()
        val sx = curX
        val sy = curY
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = ms
        a.interpolator = AccelerateDecelerateInterpolator()
        a.addUpdateListener {
            val t = it.animatedFraction
            setCenter(sx + (toX - sx) * t, sy + (toY - sy) * t)
        }
        a.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) onEnd()
            }
        })
        animator = a
        a.start()
    }

    private fun runAway() {
        if (finished) return
        glide(hiddenX, hiddenY, 450L) { finish(false) }
    }

    private fun onTapped() {
        if (finished) return
        handler.removeCallbacksAndMessages(null)
        animator?.cancel()
        if (personality == Personality.SHY) {
            // The shy one hides as soon as it is discovered
            ghost?.showShy()
            glide(hiddenX, hiddenY, 350L) { finish(false) }
        } else {
            finish(true) // the others jump inside
        }
    }

    private fun finish(jumpedInside: Boolean) {
        if (finished) return
        finished = true
        val cx = curX.toInt()
        val cy = curY.toInt()
        removeViews()
        host.onPeekEnded(jumpedInside, cx, cy)
    }

    private fun removeViews() {
        handler.removeCallbacksAndMessages(null)
        animator?.cancel()
        val c = root ?: return
        try {
            wm.removeViewImmediate(c)
        } catch (e: IllegalArgumentException) {
            Log.w("PeekWindow", "Already removed", e)
        }
        root = null
        params = null
    }

    /** Stops the peek without telling anybody (the app is closing or it is bedtime). */
    fun cancel() {
        finished = true
        removeViews()
    }
}
