package com.cuteghost.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager

/** Shows one short text message at the top of the screen. It never takes touches. */
class NoticeWindow(
    private val context: Context,
    private val wm: WindowManager
) {
    private val handler = Handler(Looper.getMainLooper())
    private var view: NoticeView? = null
    private val hideRunnable = Runnable { dismiss() }

    val isShowing: Boolean get() = view != null

    fun show(text: String, durationMs: Long) {
        dismissNow()
        val dm = context.resources.displayMetrics
        val d = dm.density
        val width = minOf(dm.widthPixels - (32f * d).toInt(), (320f * d).toInt())
        val nv = NoticeView(context)
        nv.setText(text, width)

        val p = WindowManager.LayoutParams(
            width,
            nv.contentHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        p.y = (dm.heightPixels * 0.10f).toInt()

        try {
            wm.addView(nv, p)
            view = nv
            nv.alpha = 0f
            nv.animate().alpha(1f).setDuration(300L).start()
            handler.postDelayed(hideRunnable, durationMs)
        } catch (e: RuntimeException) {
            Log.e("NoticeWindow", "Could not show the notice", e)
        }
    }

    /** Fades the message out and removes it. */
    fun dismiss() {
        handler.removeCallbacks(hideRunnable)
        val v = view ?: return
        view = null
        v.animate().alpha(0f).setDuration(400L).withEndAction { removeQuietly(v) }.start()
    }

    fun dismissNow() {
        handler.removeCallbacks(hideRunnable)
        val v = view ?: return
        view = null
        v.animate().cancel()
        removeQuietly(v)
    }

    private fun removeQuietly(v: NoticeView) {
        try {
            wm.removeViewImmediate(v)
        } catch (e: IllegalArgumentException) {
            // already removed
        }
    }
}
