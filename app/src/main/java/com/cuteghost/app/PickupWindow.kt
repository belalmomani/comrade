package com.cuteghost.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A star or a gift thrown from the portal. The child collects it by touching it. */
class PickupWindow(
    private val context: Context,
    private val wm: WindowManager,
    val isGift: Boolean,
    private val listener: Listener
) {
    interface Listener {
        fun onCollected(w: PickupWindow, isGift: Boolean)
        fun onExpired(w: PickupWindow)
    }

    companion object {
        const val SIZE_DP = 56
    }

    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private val size = (SIZE_DP * density).toInt()
    private var view: PickupView? = null
    private var params: WindowManager.LayoutParams? = null
    private var flight: ValueAnimator? = null
    private var done = false

    val isShown: Boolean get() = view != null

    /** Throws the item from ([fromX], [fromY]) so that it lands at ([toX], [toY]). */
    fun show(fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        val dm = context.resources.displayMetrics
        val v = PickupView(context, isGift)
        val p = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = fromX - size / 2
        p.y = fromY - size / 2
        v.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP) collect()
            true
        }
        return try {
            wm.addView(v, p)
            view = v
            params = p
            val tx = (toX - size / 2).coerceIn(0, maxOf(0, dm.widthPixels - size))
            val ty = (toY - size / 2).coerceIn(0, maxOf(0, dm.heightPixels - size))
            val sx = p.x
            val sy = p.y
            val arc = 80f * density
            val a = ValueAnimator.ofFloat(0f, 1f)
            a.duration = 700L
            a.addUpdateListener {
                val t = it.animatedFraction
                p.x = (sx + (tx - sx) * t).toInt()
                p.y = (sy + (ty - sy) * t - sin(PI.toFloat() * t) * arc).toInt()
                update()
            }
            flight = a
            a.start()
            handler.postDelayed({ expire() }, 45_000L)
            true
        } catch (e: RuntimeException) {
            Log.e("PickupWindow", "Could not show the gift", e)
            false
        }
    }

    private fun update() {
        val v = view ?: return
        val p = params ?: return
        try {
            wm.updateViewLayout(v, p)
        } catch (e: IllegalArgumentException) {
            // removed
        }
    }

    private fun collect() {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        flight?.cancel()
        val v = view
        v?.animate()?.alpha(0f)?.scaleX(1.8f)?.scaleY(1.8f)?.setDuration(350L)
            ?.withEndAction { removeNow() }?.start()
        listener.onCollected(this, isGift)
    }

    private fun expire() {
        if (done) return
        done = true
        flight?.cancel()
        val v = view
        v?.animate()?.alpha(0f)?.setDuration(500L)?.withEndAction { removeNow() }?.start()
        listener.onExpired(this)
    }

    fun removeNow() {
        handler.removeCallbacksAndMessages(null)
        flight?.cancel()
        val v = view ?: return
        v.animate().cancel()
        view = null
        params = null
        try {
            wm.removeViewImmediate(v)
        } catch (e: IllegalArgumentException) {
            // already removed
        }
    }
}

/** Draws a bobbing, twinkling star or gift box. */
class PickupView(context: Context, private val gift: Boolean) : View(context) {
    private var clock = 0f
    private var running = false
    private var lastTick = 0L
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val rect = RectF()
    private val boxColor = intArrayOf(
        Color.parseColor("#E63946"), Color.parseColor("#3D8BFD"),
        Color.parseColor("#62D6A9"), Color.parseColor("#A98BFF")
    )[Random.nextInt(4)]

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            clock += ((now - lastTick) / 1000f).coerceIn(0f, 0.05f)
            lastTick = now
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastTick = SystemClock.uptimeMillis()
        postOnAnimation(ticker)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height) / 56f
        canvas.save()
        canvas.scale(s, s)
        canvas.translate(28f, 28f + sin(clock * 3f) * 2.5f)
        val pulse = 1f + 0.06f * sin(clock * 5f)
        canvas.scale(pulse, pulse)

        if (gift) drawGift(canvas) else drawStar(canvas)

        // Twinkles
        fill.style = Paint.Style.FILL
        fill.color = Color.parseColor("#FFF3B0")
        for (i in 0 until 3) {
            val a = clock * 2f + i * 2.1f
            fill.alpha = (130f + 125f * sin(clock * 4f + i)).toInt().coerceIn(0, 255)
            canvas.drawCircle(cos(a) * 22f, sin(a) * 22f, 2f, fill)
        }
        canvas.restore()
    }

    private fun drawStar(c: Canvas) {
        path.reset()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) 20f else 9f
            val a = (-90f + i * 36f) * (PI.toFloat() / 180f)
            val px = cos(a) * r
            val py = sin(a) * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill.style = Paint.Style.FILL
        fill.color = Color.parseColor("#FFD93B")
        fill.alpha = 255
        c.drawPath(path, fill)
        line.color = Color.parseColor("#FFB300")
        line.strokeWidth = 2f
        c.drawPath(path, line)
    }

    private fun drawGift(c: Canvas) {
        fill.style = Paint.Style.FILL
        fill.color = boxColor
        fill.alpha = 255
        rect.set(-15f, -6f, 15f, 16f)
        c.drawRoundRect(rect, 3f, 3f, fill)
        fill.color = Color.WHITE
        rect.set(-2.5f, -6f, 2.5f, 16f)
        c.drawRect(rect, fill)
        rect.set(-17f, -12f, 17f, -5f)
        fill.color = boxColor
        c.drawRoundRect(rect, 3f, 3f, fill)
        fill.color = Color.WHITE
        rect.set(-2.5f, -12f, 2.5f, -5f)
        c.drawRect(rect, fill)
        // bow
        line.color = Color.WHITE
        line.strokeWidth = 3f
        rect.set(-9f, -19f, 0f, -11f)
        c.drawOval(rect, line)
        rect.set(0f, -19f, 9f, -11f)
        c.drawOval(rect, line)
    }
}
