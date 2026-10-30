package com.cuteghost.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager

/** Shows one pretend-mischief effect over the whole screen (it cannot be touched), then fades it away. */
class MischiefWindow(
    private val context: Context,
    private val wm: WindowManager
) {
    private val handler = Handler(Looper.getMainLooper())
    private var view: MischiefView? = null
    private var onDone: (() -> Unit)? = null
    private val finish = Runnable { fadeOut() }

    fun show(effect: MischiefView.Effect, x: Float, y: Float, durationMs: Long, done: () -> Unit): Boolean {
        val dm = context.resources.displayMetrics
        val p = WindowManager.LayoutParams(
            dm.widthPixels,
            dm.heightPixels,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        val v = MischiefView(context)
        return try {
            wm.addView(v, p)
            v.start(effect, x, y, durationMs)
            view = v
            onDone = done
            handler.postDelayed(finish, durationMs)
            true
        } catch (e: RuntimeException) {
            Log.e("MischiefWindow", "Could not show the mischief", e)
            false
        }
    }

    private fun fadeOut() {
        val v = view ?: return
        v.animate().alpha(0f).setDuration(700L).withEndAction { removeNow() }.start()
    }

    fun remove() {
        handler.removeCallbacks(finish)
        removeNow()
    }

    private fun removeNow() {
        val v = view ?: return
        view = null
        v.animate().cancel()
        v.stop()
        try {
            wm.removeViewImmediate(v)
        } catch (e: IllegalArgumentException) {
            // already removed
        }
        onDone?.invoke()
        onDone = null
    }
}
