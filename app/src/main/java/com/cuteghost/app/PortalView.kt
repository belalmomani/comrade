package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * A swirling magic portal (a glowing hole) on a transparent background. Its centre is at 118/160
 * of the height. It pops open, can be made bigger by touches and sparkles all the time.
 */
class PortalView(context: Context) : View(context) {

    private var open = 0f
    private var boost = 1f
    private var clock = 0f
    private var running = false
    private var lastTick = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    // Shaders on a unit circle (the canvas is scaled to the portal's size)
    private val haloShader = RadialGradient(
        0f, 0f, 1.35f,
        intArrayOf(Color.parseColor("#667C5CFF"), Color.parseColor("#007C5CFF")),
        floatArrayOf(0.74f, 1f), Shader.TileMode.CLAMP
    )
    private val coreShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(
            Color.parseColor("#12083F"), Color.parseColor("#3A1F9E"),
            Color.parseColor("#7C5CFF"), Color.parseColor("#C9B8FF")
        ),
        floatArrayOf(0f, 0.45f, 0.85f, 1f), Shader.TileMode.CLAMP
    )

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

    fun setOpenAmount(value: Float) {
        open = value
        invalidate()
    }

    fun setBoost(value: Float) {
        boost = value
        invalidate()
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
        val d = resources.displayMetrics.density
        val k = open * boost
        if (k <= 0.01f) return
        val cx = width / 2f
        val cy = height * (118f / 160f)
        val rx = 50f * d * k
        val ry = rx * 0.34f

        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(rx, ry)
        fill.style = Paint.Style.FILL
        fill.shader = haloShader
        canvas.drawCircle(0f, 0f, 1.35f, fill)
        fill.shader = coreShader
        canvas.drawCircle(0f, 0f, 1f, fill)
        fill.shader = null

        // Spiral arms turning slowly
        line.color = Color.parseColor("#D9CCFF")
        line.alpha = 220
        line.strokeWidth = 0.07f
        for (i in 0 until 3) {
            canvas.save()
            canvas.rotate(clock * 120f + i * 120f)
            rect.set(-0.8f, -0.8f, 0.8f, 0.8f)
            canvas.drawArc(rect, 0f, 100f, false, line)
            rect.set(-0.5f, -0.5f, 0.5f, 0.5f)
            canvas.drawArc(rect, 40f, 90f, false, line)
            canvas.restore()
        }
        line.color = Color.WHITE
        line.alpha = 230
        line.strokeWidth = 0.05f
        canvas.drawCircle(0f, 0f, 1f, line)
        canvas.restore()

        // Sparkles around the portal
        fill.style = Paint.Style.FILL
        fill.color = Color.parseColor("#FFF3B0")
        for (i in 0 until 6) {
            val a = clock * 1.5f + i * 1.047f
            val sx = cx + cos(a) * rx * 1.15f
            val sy = cy + sin(a) * ry * 1.15f - 4f * d
            fill.alpha = (150f + 105f * sin(clock * 3f + i)).toInt().coerceIn(0, 255)
            canvas.drawCircle(sx, sy, 2.2f * d * k, fill)
        }
    }
}
