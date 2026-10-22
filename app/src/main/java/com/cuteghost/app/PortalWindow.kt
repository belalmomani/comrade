package com.cuteghost.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import kotlin.random.Random

/**
 * A magic portal that opens somewhere on the screen. A stranger's head comes out of it and then
 * it throws a star or a gift, or the whole stranger steps out and visits the family for a minute.
 * Touching the portal makes it a little bigger; after three touches the next one closes it.
 */
class PortalWindow(
    private val context: Context,
    private val wm: WindowManager,
    private val host: Host
) {
    enum class Event { STAR, GIFT, VISITOR, EMPTY }

    interface Host {
        /** The head tossed a star or a gift; [screenX]/[screenY] is where it leaves the portal. */
        fun onToss(isGift: Boolean, screenX: Int, screenY: Int)

        /** The stranger steps out of the portal (its feet are at [screenX], [screenY]). */
        fun onVisitorOut(colorIndex: Int, screenX: Int, screenY: Int)

        /** Returns true when the portal may close now; false when a visitor must go home first. */
        fun onCloseRequested(): Boolean

        fun onClosed()
    }

    companion object {
        const val WIDTH_DP = 150
        const val HEIGHT_DP = 160
        private const val MOUTH_DP = 118
        private const val GHOST_SCALE = 0.6f
        fun widthPx(density: Float): Int = (WIDTH_DP * density).toInt()
        fun heightPx(density: Float): Int = (HEIGHT_DP * density).toInt()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    val widthPx = widthPx(density)
    val heightPx = heightPx(density)
    private val ghostW = Family.sizePx(GhostWindow.BASE_WIDTH_DP, GHOST_SCALE, density)
    private val ghostH = Family.sizePx(GhostWindow.BASE_HEIGHT_DP, GHOST_SCALE, density)

    private var x = 0
    private var y = 0
    private var root: FrameLayout? = null
    private var portalView: PortalView? = null
    private var ghost: GhostView? = null
    private var event = Event.EMPTY
    private var visitorColor = 100
    private var taps = 0
    private var closing = false
    private var boostAnimator: ValueAnimator? = null

    fun rect(): Rect = Rect(x, y, x + widthPx, y + heightPx)

    /** Screen height of the centre of the portal (where the ghosts' feet are). */
    fun mouthY(): Int = y + (MOUTH_DP * density).toInt()

    fun show(px: Int, py: Int, newEvent: Event): Boolean {
        event = newEvent
        x = px
        y = py
        val mouth = MOUTH_DP * density

        val pv = PortalView(context)
        val g = GhostView(context)
        visitorColor = 100 + Random.nextInt(60)
        g.setBodyColor(Family.color(visitorColor))
        g.playful = true
        g.personality = Family.personalityFor(visitorColor)
        g.setOutfit(Random.nextInt(4), 0, 0, 0, 0)

        // The stranger is only visible above the portal's centre line: it comes out of the hole
        val container = object : FrameLayout(context) {
            override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
                if (child !== g) return super.drawChild(canvas, child, drawingTime)
                val saved = canvas.save()
                canvas.clipRect(0f, 0f, width.toFloat(), mouth)
                val result = super.drawChild(canvas, child, drawingTime)
                canvas.restoreToCount(saved)
                return result
            }
        }
        container.addView(pv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val lp = FrameLayout.LayoutParams(ghostW, ghostH, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = (mouth - ghostH).toInt()
        container.addView(g, lp)
        g.translationY = ghostH.toFloat() // hidden below the centre line

        container.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP && e.eventTime - e.downTime <= 450L) onPortalTapped()
            true
        }

        val p = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = x
        p.y = y

        return try {
            wm.addView(container, p)
            root = container
            portalView = pv
            ghost = g
            animateOpen(0f, 1f, 500L, true) { }
            handler.postDelayed({ peekOut() }, 900L)
            handler.postDelayed({ requestClose() }, 75_000L)
            true
        } catch (e: RuntimeException) {
            Log.e("PortalWindow", "Could not open the portal", e)
            false
        }
    }

    private fun animateOpen(from: Float, to: Float, ms: Long, overshoot: Boolean, onEnd: () -> Unit) {
        val pv = portalView ?: return
        val a = ValueAnimator.ofFloat(from, to)
        a.duration = ms
        if (overshoot) a.interpolator = OvershootInterpolator(1.6f)
        a.addUpdateListener { pv.setOpenAmount(it.animatedValue as Float) }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onEnd()
            }
        })
        a.start()
    }

    private fun slideGhost(translation: Float, ms: Long, onEnd: () -> Unit) {
        val g = ghost ?: return
        g.animate().translationY(translation).setDuration(ms).withEndAction { onEnd() }.start()
    }

    /** The stranger's head comes out of the portal. */
    private fun peekOut() {
        if (closing) return
        slideGhost(0.30f * ghostH, 600L) {
            handler.postDelayed({ doEvent() }, 900L)
        }
    }

    private fun doEvent() {
        val g = ghost ?: return
        if (closing) return
        val outX = x + widthPx / 2
        when (event) {
            Event.STAR, Event.GIFT -> {
                g.hopHappy()
                host.onToss(event == Event.GIFT, outX, mouthY() - (ghostH * 0.7f).toInt())
                handler.postDelayed({
                    slideGhost(ghostH.toFloat(), 450L) {
                        handler.postDelayed({ requestClose() }, 1_500L)
                    }
                }, 700L)
            }
            Event.VISITOR -> {
                // The whole stranger steps out: it becomes a real visitor ghost
                slideGhost(-0.02f * ghostH, 600L) {
                    host.onVisitorOut(visitorColor, outX, mouthY())
                    ghost?.visibility = View.INVISIBLE
                }
            }
            Event.EMPTY -> {
                g.hopHappy()
                handler.postDelayed({
                    slideGhost(ghostH.toFloat(), 450L) {
                        handler.postDelayed({ requestClose() }, 800L)
                    }
                }, 900L)
            }
        }
    }

    /** Touching the portal makes it a bit bigger; the fourth touch closes it. */
    private fun onPortalTapped() {
        if (closing) return
        taps++
        if (taps <= 3) {
            val pv = portalView ?: return
            boostAnimator?.cancel()
            val a = ValueAnimator.ofFloat(1f + 0.12f * (taps - 1), 1f + 0.12f * taps)
            a.duration = 250L
            a.addUpdateListener { pv.setBoost(it.animatedValue as Float) }
            boostAnimator = a
            a.start()
            ghost?.hopHappy()
        } else {
            requestClose()
        }
    }

    private fun requestClose() {
        if (closing) return
        if (host.onCloseRequested()) close()
    }

    /** The visitor went home through the portal: it can close now. */
    fun visitorFinished() {
        close()
    }

    private fun close() {
        if (closing) return
        closing = true
        handler.removeCallbacksAndMessages(null)
        val g = ghost
        g?.animate()?.cancel()
        animateOpen(1f, 0f, 450L, false) {
            removeViews()
            host.onClosed()
        }
    }

    private fun removeViews() {
        handler.removeCallbacksAndMessages(null)
        boostAnimator?.cancel()
        val c = root ?: return
        try {
            wm.removeViewImmediate(c)
        } catch (e: IllegalArgumentException) {
            Log.w("PortalWindow", "Already removed", e)
        }
        root = null
        portalView = null
        ghost = null
    }

    /** Removes the portal right away without telling anybody. */
    fun closeNow() {
        closing = true
        removeViews()
    }
}
