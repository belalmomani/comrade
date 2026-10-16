package com.cuteghost.app

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager

/** The babies' small house next to the big ghost. It never takes touches. */
class HouseWindow(
    private val context: Context,
    private val wm: WindowManager
) {
    companion object {
        const val SIZE_DP = 88
        fun size(density: Float): Int = (SIZE_DP * density).toInt()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    val widthPx = size(density)
    val heightPx = size(density)

    var x = 0
        private set
    var y = 0
        private set
    private var view: HouseView? = null
    private var params: WindowManager.LayoutParams? = null
    private val closeDoor = Runnable {
        view?.doorTarget = 0f
        view?.invalidate()
    }

    fun rect(): Rect = Rect(x, y, x + widthPx, y + heightPx)

    /** Bottom centre of the door: where babies come out and go in. */
    fun doorPoint(): Point = Point(x + widthPx / 2, y + (heightPx * 0.93f).toInt())

    fun show(px: Int, py: Int): Boolean {
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
        x = px
        y = py
        p.x = x
        p.y = y
        val v = HouseView(context)
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

    fun moveTo(px: Int, py: Int) {
        val v = view ?: return
        val p = params ?: return
        x = px
        y = py
        p.x = x
        p.y = y
        try {
            wm.updateViewLayout(v, p)
        } catch (e: IllegalArgumentException) {
            Log.w("HouseWindow", "Could not move the house", e)
        }
    }

    /** Opens the door for a while (a baby is going in or out). */
    fun openDoor(forMs: Long) {
        val v = view ?: return
        v.doorTarget = 1f
        v.invalidate()
        handler.removeCallbacks(closeDoor)
        handler.postDelayed(closeDoor, forMs)
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
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
