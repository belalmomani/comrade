package com.cuteghost.app

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.util.Log
import android.view.Gravity
import android.view.WindowManager

/** The room with the cloud beds, shown at night. It never takes touches. */
class RoomWindow(
    private val context: Context,
    private val wm: WindowManager
) {
    companion object {
        const val WIDTH_DP = 270
        const val HEIGHT_DP = 170
        const val SLOTS = 3
    }

    private val density = context.resources.displayMetrics.density
    val widthPx = (WIDTH_DP * density).toInt()
    val heightPx = (HEIGHT_DP * density).toInt()
    private var x = 0
    private var y = 0
    private var view: RoomView? = null

    /** Where the bed of slot [i] is: its horizontal centre and the height of the bed's top. */
    fun slotCenter(i: Int): Point {
        val s = widthPx / WIDTH_DP.toFloat()
        return Point(x + ((58 + 70 * i) * s).toInt(), y + (118 * s).toInt())
    }

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
        val v = RoomView(context)
        return try {
            wm.addView(v, p)
            v.alpha = 0f
            v.animate().alpha(1f).setDuration(1_200L).start()
            view = v
            true
        } catch (e: RuntimeException) {
            Log.e("RoomWindow", "Could not add the room", e)
            false
        }
    }

    /** Fades the room away and removes it. */
    fun fadeOutAndRemove() {
        val v = view ?: return
        view = null
        v.animate().alpha(0f).setDuration(1_500L).withEndAction { removeQuietly(v) }.start()
    }

    fun remove() {
        val v = view ?: return
        view = null
        v.animate().cancel()
        removeQuietly(v)
    }

    private fun removeQuietly(v: RoomView) {
        try {
            wm.removeViewImmediate(v)
        } catch (e: IllegalArgumentException) {
            // already removed
        }
    }
}
