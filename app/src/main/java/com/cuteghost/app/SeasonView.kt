package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The season of the day on a transparent full-screen layer: snow (winter), petals (spring),
 * a shining sun (summer) or falling leaves (autumn). It never takes touches.
 * season: 0 winter, 1 spring, 2 summer, 3 autumn.
 */
class SeasonView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private var season = 0
    private var running = false
    private var lastTick = 0L
    private var clock = 0f

    private val maxParticles = 48
    private var count = 0
    private val px = FloatArray(maxParticles)
    private val py = FloatArray(maxParticles)
    private val speed = FloatArray(maxParticles)
    private val size = FloatArray(maxParticles)
    private val phase = FloatArray(maxParticles)
    private val sway = FloatArray(maxParticles)
    private val rot = FloatArray(maxParticles)
    private val rotSpeed = FloatArray(maxParticles)
    private val tone = IntArray(maxParticles)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val leaf = Path().apply {
        moveTo(0f, -1f)
        cubicTo(0.9f, -0.4f, 0.7f, 0.6f, 0f, 1f)
        cubicTo(-0.7f, 0.6f, -0.9f, -0.4f, 0f, -1f)
        close()
    }
    private val leafColors = intArrayOf(
        Color.parseColor("#E8742A"), Color.parseColor("#C9462C"),
        Color.parseColor("#E7B53B"), Color.parseColor("#9C6B30")
    )
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastTick) / 1000f).coerceIn(0f, 0.05f)
            lastTick = now
            step(dt)
            invalidate()
            postOnAnimation(this)
        }
    }

    fun start(newSeason: Int) {
        season = newSeason
        count = when (season) {
            0 -> 45
            1 -> 26
            2 -> 14
            else -> 24
        }
        for (i in 0 until count) resetParticle(i, true)
        running = true
        lastTick = SystemClock.uptimeMillis()
        removeCallbacks(ticker)
        postOnAnimation(ticker)
    }

    fun stop() {
        running = false
        removeCallbacks(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = 60f * d + 34f * d
        val cy = 90f * d
        glow.shader = RadialGradient(
            cx, cy, 130f * d,
            Color.parseColor("#99FFE680"), Color.parseColor("#00FFE680"),
            Shader.TileMode.CLAMP
        )
    }

    private fun resetParticle(i: Int, anywhere: Boolean) {
        px[i] = Random.nextFloat()
        py[i] = if (anywhere) Random.nextFloat() else -0.05f
        phase[i] = Random.nextFloat() * 6.2831855f
        rot[i] = Random.nextFloat() * 360f
        tone[i] = Random.nextInt(4)
        when (season) {
            0 -> { // snow
                speed[i] = 0.05f + Random.nextFloat() * 0.07f
                size[i] = 2.5f + Random.nextFloat() * 3.5f
                sway[i] = 8f + Random.nextFloat() * 14f
                rotSpeed[i] = 0f
            }
            1 -> { // petals
                speed[i] = 0.04f + Random.nextFloat() * 0.05f
                size[i] = 5f + Random.nextFloat() * 3f
                sway[i] = 16f + Random.nextFloat() * 20f
                rotSpeed[i] = 40f + Random.nextFloat() * 60f
            }
            2 -> { // sparkles in the summer air (they rise slowly and twinkle)
                speed[i] = 0f
                size[i] = 2f + Random.nextFloat() * 2f
                sway[i] = 0f
                rotSpeed[i] = 0f
                py[i] = Random.nextFloat() * 0.6f
            }
            else -> { // leaves
                speed[i] = 0.05f + Random.nextFloat() * 0.06f
                size[i] = 7f + Random.nextFloat() * 5f
                sway[i] = 18f + Random.nextFloat() * 24f
                rotSpeed[i] = 50f + Random.nextFloat() * 90f
            }
        }
    }

    private fun step(dt: Float) {
        clock += dt
        if (season == 2) return
        for (i in 0 until count) {
            py[i] += speed[i] * dt
            rot[i] += rotSpeed[i] * dt
            if (py[i] > 1.06f) resetParticle(i, false)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (season == 2) {
            drawSun(canvas, w)
            return
        }
        for (i in 0 until count) {
            val x = px[i] * w + sin(phase[i] + clock * 1.2f) * sway[i] * d
            val y = py[i] * h
            val r = size[i] * d
            when (season) {
                0 -> {
                    fill.style = Paint.Style.FILL
                    fill.color = Color.WHITE
                    fill.alpha = 235
                    canvas.drawCircle(x, y, r, fill)
                    line.color = Color.parseColor("#9CC9F0")
                    line.alpha = 200
                    line.strokeWidth = 1f * d
                    canvas.drawCircle(x, y, r, line)
                }
                1 -> {
                    val s = canvas.save()
                    canvas.translate(x, y)
                    canvas.rotate(rot[i])
                    fill.style = Paint.Style.FILL
                    fill.color = Color.parseColor("#FFB6D0")
                    fill.alpha = 240
                    rect.set(-r * 0.6f, -r, r * 0.6f, r)
                    canvas.drawOval(rect, fill)
                    line.color = Color.parseColor("#FF8FB8")
                    line.alpha = 220
                    line.strokeWidth = 1f * d
                    canvas.drawOval(rect, line)
                    canvas.restoreToCount(s)
                }
                else -> {
                    val s = canvas.save()
                    canvas.translate(x, y)
                    canvas.rotate(rot[i])
                    canvas.scale(r * 0.7f, r)
                    fill.style = Paint.Style.FILL
                    fill.color = leafColors[tone[i]]
                    fill.alpha = 245
                    canvas.drawPath(leaf, fill)
                    canvas.restoreToCount(s)
                }
            }
        }
    }

    private fun drawSun(canvas: Canvas, w: Float) {
        val cx = 60f * d + 34f * d
        val cy = 90f * d
        canvas.drawCircle(cx, cy, 130f * d, glow)

        line.color = Color.parseColor("#FFC400")
        line.alpha = 255
        line.strokeWidth = 3.5f * d
        val spin = clock * 12f
        for (i in 0 until 12) {
            val a = (spin + i * 30f) * (PI.toFloat() / 180f)
            canvas.drawLine(
                cx + cos(a) * 42f * d, cy + sin(a) * 42f * d,
                cx + cos(a) * 58f * d, cy + sin(a) * 58f * d, line
            )
        }
        fill.style = Paint.Style.FILL
        fill.color = Color.parseColor("#FFD93B")
        fill.alpha = 255
        canvas.drawCircle(cx, cy, 34f * d, fill)
        line.color = Color.parseColor("#FFB300")
        line.strokeWidth = 2f * d
        canvas.drawCircle(cx, cy, 34f * d, line)

        // Twinkling sparkles
        for (i in 0 until count) {
            val a = ((sin(phase[i] + clock * 2.2f) + 1f) / 2f)
            fill.color = Color.parseColor("#FFF3B0")
            fill.alpha = (255f * a).toInt().coerceIn(0, 255)
            canvas.drawCircle(px[i] * w, py[i] * height, size[i] * d, fill)
        }
    }
}
