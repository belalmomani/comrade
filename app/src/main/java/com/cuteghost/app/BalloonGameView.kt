package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The ghosts' balloon game (once a day): the child steers the balloon with a finger and collects
 * the falling stars for 45 seconds. Nothing can be lost: it is only a happy flight.
 */
class BalloonGameView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        /** The basket's position, so the ghost can ride in it. */
        fun onFrame(basketX: Float, basketY: Float)
        fun onFinished(collected: Int)
    }

    companion object {
        const val GAME_SECONDS = 45f
    }

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2540")
        textSize = 20f * resources.displayMetrics.density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val path = Path()

    private var bx = 0f
    private var by = 0f
    private var tx = 0f
    private var ty = 0f
    private var ready = false
    private var timeLeft = GAME_SECONDS
    private var collected = 0
    private var finished = false
    private var clock = 0f
    private var spawn = 0f
    private var running = false
    private var lastTick = 0L

    private val starX = FloatArray(10)
    private val starY = FloatArray(10)
    private val starV = FloatArray(10)
    private val starAlive = BooleanArray(10)
    private val spX = FloatArray(40)
    private val spY = FloatArray(40)
    private val spVx = FloatArray(40)
    private val spVy = FloatArray(40)
    private val spLife = FloatArray(40)

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            step(((now - lastTick) / 1000f).coerceIn(0f, 0.05f))
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

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
            // The balloon floats toward the finger, a little above it so the finger does not hide it
            tx = e.x.coerceIn(40f * d, width - 40f * d)
            ty = (e.y - 90f * d).coerceIn(150f * d, height - 60f * d)
        }
        return true
    }

    private fun step(dt: Float) {
        if (width == 0 || height == 0 || finished) return
        if (!ready) {
            ready = true
            bx = width / 2f
            by = height * 0.62f
            tx = bx
            ty = by
        }
        clock += dt
        timeLeft -= dt
        val k = min(1f, dt * 3.5f)
        bx += (tx - bx) * k
        by += (ty - by) * k + sin(clock * 2f) * 0.25f

        spawn -= dt
        if (spawn <= 0f) {
            spawn = 0.8f
            for (i in starAlive.indices) {
                if (!starAlive[i]) {
                    starAlive[i] = true
                    starX[i] = 30f * d + Random.nextFloat() * (width - 60f * d)
                    starY[i] = -20f * d
                    starV[i] = (45f + Random.nextFloat() * 35f) * d
                    break
                }
            }
        }
        for (i in starAlive.indices) {
            if (!starAlive[i]) continue
            starY[i] += starV[i] * dt
            starX[i] += sin(clock * 2f + i) * 12f * d * dt
            if (hypot(starX[i] - bx, starY[i] - (by - 70f * d)) < 52f * d) {
                starAlive[i] = false
                collected++
                burst(starX[i], starY[i])
            } else if (starY[i] > height + 30f * d) {
                starAlive[i] = false
            }
        }
        for (i in spLife.indices) {
            if (spLife[i] > 0f) {
                spLife[i] -= dt
                spX[i] += spVx[i] * dt
                spY[i] += spVy[i] * dt
            }
        }
        listener.onFrame(bx, by)
        if (timeLeft <= 0f) {
            finished = true
            listener.onFinished(collected)
        }
    }

    private fun burst(x: Float, y: Float) {
        var made = 0
        for (i in spLife.indices) {
            if (spLife[i] <= 0f) {
                val a = Random.nextFloat() * 2f * PI.toFloat()
                spX[i] = x
                spY[i] = y
                spVx[i] = cos(a) * 90f * d
                spVy[i] = sin(a) * 90f * d
                spLife[i] = 0.6f
                made++
                if (made >= 8) break
            }
        }
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        path.reset()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val a = (-90f + i * 36f) * (PI.toFloat() / 180f)
            if (i == 0) path.moveTo(cx + cos(a) * rr, cy + sin(a) * rr) else path.lineTo(cx + cos(a) * rr, cy + sin(a) * rr)
        }
        path.close()
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.color = color
        fill.alpha = alpha
        c.drawPath(path, fill)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        fill.style = Paint.Style.FILL
        fill.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#7EC4FF"), Color.parseColor("#FDEFFF"), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        for (i in 0 until 5) {
            val x = ((clock * (8f + i * 3f) * d + i * w * 0.35f) % (w + 200f * d)) - 100f * d
            val y = h * (0.15f + 0.17f * i)
            fill.color = Color.WHITE
            fill.alpha = 200
            canvas.drawCircle(x - 22f * d, y + 4f * d, 16f * d, fill)
            canvas.drawCircle(x, y - 4f * d, 22f * d, fill)
            canvas.drawCircle(x + 26f * d, y + 4f * d, 17f * d, fill)
        }
        for (i in starAlive.indices) {
            if (starAlive[i]) star(canvas, starX[i], starY[i], 15f * d, Color.parseColor("#FFD93B"), 255)
        }
        for (i in spLife.indices) {
            if (spLife[i] > 0f) star(canvas, spX[i], spY[i], 5f * d, Color.parseColor("#FFF3B0"), (spLife[i] / 0.6f * 255f).toInt().coerceIn(0, 255))
        }
        if (ready) drawBalloon(canvas)

        // Time bar and the stars collected
        fill.color = Color.parseColor("#33000000")
        rect.set(16f * d, 78f * d, w - 16f * d, 90f * d)
        canvas.drawRoundRect(rect, 6f * d, 6f * d, fill)
        fill.color = Color.parseColor("#FFC93C")
        rect.set(16f * d, 78f * d, 16f * d + (w - 32f * d) * (timeLeft / GAME_SECONDS).coerceIn(0f, 1f), 90f * d)
        canvas.drawRoundRect(rect, 6f * d, 6f * d, fill)
        canvas.drawText("النجوم: $collected", w / 2f, 118f * d, text)
    }

    private fun drawBalloon(c: Canvas) {
        val r = 58f * d
        val ey = by - 130f * d
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.color = Color.parseColor("#FF6B8A")
        c.drawCircle(bx, ey, r, fill)
        fill.color = Color.parseColor("#FFE27A")
        rect.set(bx - r * 0.38f, ey - r, bx + r * 0.38f, ey + r)
        c.drawOval(rect, fill)
        fill.color = Color.parseColor("#7C5CFF")
        rect.set(bx - r * 0.12f, ey - r, bx + r * 0.12f, ey + r)
        c.drawOval(rect, fill)
        line.color = Color.parseColor("#B8324E")
        line.strokeWidth = 3f * d
        c.drawCircle(bx, ey, r, line)
        line.color = Color.parseColor("#8B5E3C")
        line.strokeWidth = 2f * d
        c.drawLine(bx - r * 0.5f, ey + r * 0.85f, bx - 22f * d, by - 6f * d, line)
        c.drawLine(bx + r * 0.5f, ey + r * 0.85f, bx + 22f * d, by - 6f * d, line)
        fill.color = Color.parseColor("#C97B4A")
        rect.set(bx - 28f * d, by - 6f * d, bx + 28f * d, by + 22f * d)
        c.drawRoundRect(rect, 6f * d, 6f * d, fill)
    }
}
