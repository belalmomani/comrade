package com.cuteghost.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager

/** Shows the season of the day over the whole screen for a while, then fades it away. */
class SeasonWindow(
    private val context: Context,
    private val wm: WindowManager
) {
    private val handler = Handler(Looper.getMainLooper())
    private var view: SeasonView? = null
    private var onDone: (() -> Unit)? = null
    private val finish = Runnable { fadeOut() }

    fun show(season: Int, durationMs: Long, done: () -> Unit): Boolean {
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
        val v = SeasonView(context)
        v.start(season)
        return try {
            wm.addView(v, p)
            view = v
            onDone = done
            v.alpha = 0f
            v.animate().alpha(1f).setDuration(1500L).start()
            handler.postDelayed(finish, durationMs)
            true
        } catch (e: RuntimeException) {
            Log.e("SeasonWindow", "Could not show the season", e)
            v.stop()
            false
        }
    }

    private fun fadeOut() {
        val v = view ?: return
        v.animate().alpha(0f).setDuration(2000L).withEndAction { removeNow() }.start()
    }

    /** Removes the season right away. */
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
