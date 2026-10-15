package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Overlay window holding the egg and, hidden behind its shell, the baby that will hatch.
 * It is not touchable, so touches go to whatever is underneath.
 */
class EggWindow(
    private val context: Context,
    private val wm: WindowManager,
    val colorIndex: Int,
    val startMs: Long,
    val durationMs: Long,
    val x: Int,
    val y: Int,
    private val animateIn: Boolean,
    private val host: Host
) : EggView.Listener {

    interface Host {
        fun onHatched(egg: EggWindow)
    }

    companion object {
        const val WIDTH_DP = 92
        const val HEIGHT_DP = 114
        const val BABY_BOTTOM_MARGIN_DP = 8
        private const val TAG = "EggWindow"

        fun eggWidth(density: Float): Int = (WIDTH_DP * density).toInt()
        fun eggHeight(density: Float): Int = (HEIGHT_DP * density).toInt()
    }

    private val density = context.resources.displayMetrics.density
    val widthPx = eggWidth(density)
    val heightPx = eggHeight(density)

    private var root: FrameLayout? = null
    private var eggView: EggView? = null

    fun rect(): Rect = Rect(x, y, x + widthPx, y + heightPx)

    fun show(): Boolean {
        val p = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = x
        p.y = y

        val babyView = GhostView(context)
        babyView.setBodyColor(Family.color(colorIndex))
        babyView.visibility = View.INVISIBLE

        val egg = EggView(context)
        egg.tint = Family.color(colorIndex)
        egg.baby = babyView
        egg.listener = this

        val container = object : FrameLayout(context) {
            // While hatching the baby is clipped to the egg shape so it only shows inside the shell
            override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
                val clip = if (child === babyView) egg.babyClip() else null
                if (clip == null) return super.drawChild(canvas, child, drawingTime)
                val saved = canvas.save()
                canvas.clipPath(clip)
                val result = super.drawChild(canvas, child, drawingTime)
                canvas.restoreToCount(saved)
                return result
            }
        }

        val babyW = Family.sizePx(GhostWindow.BASE_WIDTH_DP, Family.BABY_SCALE, density)
        val babyH = Family.sizePx(GhostWindow.BASE_HEIGHT_DP, Family.BABY_SCALE, density)
        val babyParams = FrameLayout.LayoutParams(
            babyW, babyH, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        babyParams.bottomMargin = (BABY_BOTTOM_MARGIN_DP * density).toInt()
        container.addView(babyView, babyParams)
        container.addView(
            egg,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        return try {
            wm.addView(container, p)
            root = container
            eggView = egg
            egg.start(startMs, durationMs, animateIn)
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not add egg window", e)
            false
        }
    }

    override fun onHatched() {
        host.onHatched(this)
    }

    fun remove() {
        eggView?.stop()
        val r = root ?: return
        try {
            wm.removeViewImmediate(r)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Egg window already removed", e)
        }
        root = null
        eggView = null
    }
}
