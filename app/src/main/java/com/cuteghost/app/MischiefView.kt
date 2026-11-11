package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pretend mischief drawn over the screen for a few seconds: cute pastel "cracks", colour splats
 * or soap bubbles. They are only pictures on a transparent layer that cannot be touched, they
 * never harm the phone, and they disappear on their own (a "just a joke" label is shown).
 */
class MischiefView(context: Context) : View(context) {

    enum class Effect { CRACK, SPLAT, BUBBLES }

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()
    private val path = Path()

    private var effect = Effect.CRACK
    private var cx = 0f
    private var cy = 0f
    private var startMs = 0L
    private var durationMs = 7000L
    private var running = false
    private var rays = ArrayList<FloatArray>()
    private var blobs = ArrayList<FloatArray>()   // x, y, radius, colour index, delay
    private var bubbles = ArrayList<FloatArray>() // x0, y0, radius, speed, sway, phase, life, delay, colour index

    private val pastel = intArrayOf(
        Color.parseColor("#FF8FB8"), Color.parseColor("#8FD0FF"), Color.parseColor("#FFE27A"),
        Color.parseColor("#A8E6A1"), Color.parseColor("#C3A6FF")
    )

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            postOnAnimation(this)
        }
    }

    fun start(newEffect: Effect, x: Float, y: Float, duration: Long) {
        effect = newEffect
        cx = x
        cy = y
        durationMs = duration
        startMs = SystemClock.uptimeMillis()
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        rays.clear()
        blobs.clear()
        bubbles.clear()
        when (newEffect) {
            Effect.CRACK -> for (k in 0 until 10) {
                var ang = k * (2f * PI.toFloat() / 10f) + (Random.nextFloat() - 0.5f) * 0.36f
                var px = cx
                var py = cy
                val pts = ArrayList<Float>()
                pts.add(px)
                pts.add(py)
                val segs = 4 + Random.nextInt(3)
                for (s in 0 until segs) {
                    val len = (24f + Random.nextFloat() * 38f) * d
                    ang += (Random.nextFloat() - 0.5f) * 0.7f
                    px += cos(ang) * len
                    py += sin(ang) * len
                    pts.add(px)
                    pts.add(py)
                }
                rays.add(pts.toFloatArray())
            }
            Effect.SPLAT -> for (k in 0 until 7) {
                blobs.add(
                    floatArrayOf(
                        w * (0.1f + Random.nextFloat() * 0.8f), h * (0.1f + Random.nextFloat() * 0.5f),
                        (30f + Random.nextFloat() * 30f) * d, Random.nextInt(pastel.size).toFloat(), k * 0.35f
                    )
                )
            }
            Effect.BUBBLES -> for (k in 0 until 16) {
                bubbles.add(
                    floatArrayOf(
                        cx + (Random.nextFloat() - 0.5f) * 60f * d, cy,
                        (10f + Random.nextFloat() * 16f) * d, (55f + Random.nextFloat() * 55f) * d,
                        (8f + Random.nextFloat() * 14f) * d, Random.nextFloat() * 6f,
                        2.2f + Random.nextFloat() * 1.8f, Random.nextFloat() * 3.5f, Random.nextInt(pastel.size).toFloat()
                    )
                )
            }
        }
        running = true
        postOnAnimation(ticker)
    }

    fun stop() {
        running = false
        removeCallbacks(ticker)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun label(c: Canvas, msg: String, t: Float) {
        // "Just a joke": shown in the second half so nobody is worried
        if (t < 0.55f) return
        val w = width.toFloat()
        val a = ((t - 0.55f) / 0.1f).coerceIn(0f, 1f)
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.color = Color.parseColor("#4FAE5A")
        fill.alpha = (255f * a).toInt()
        rect.set(w / 2f - 140f * d, 96f * d, w / 2f + 140f * d, 140f * d)
        c.drawRoundRect(rect, 22f * d, 22f * d, fill)
        text.color = Color.WHITE
        text.alpha = (255f * a).toInt()
        text.textSize = 17f * d
        c.drawText(msg, w / 2f, 125f * d, text)
    }

    override fun onDraw(canvas: Canvas) {
        val sec = (SystemClock.uptimeMillis() - startMs) / 1000f
        val t = (sec * 1000f / durationMs).coerceIn(0f, 1f)
        when (effect) {
            Effect.CRACK -> drawCrack(canvas, sec, t)
            Effect.SPLAT -> drawSplat(canvas, sec, t)
            Effect.BUBBLES -> drawBubbles(canvas, sec)
        }
    }

    private fun drawCrack(c: Canvas, sec: Float, t: Float) {
        val p = min(1f, sec / 0.5f)
        for (ray in rays) {
            val n = ray.size / 2 - 1
            for (pass in 0 until 2) {
                line.color = if (pass == 0) Color.parseColor("#66A8D8FF") else Color.WHITE
                line.strokeWidth = (if (pass == 0) 7f else 3f) * d
                for (i in 0 until n) {
                    val f = (p * n - i).coerceIn(0f, 1f)
                    if (f <= 0f) break
                    val x1 = ray[i * 2]
                    val y1 = ray[i * 2 + 1]
                    val x2 = ray[i * 2 + 2]
                    val y2 = ray[i * 2 + 3]
                    c.drawLine(x1, y1, x1 + (x2 - x1) * f, y1 + (y2 - y1) * f, line)
                }
            }
            if (p >= 1f) {
                fill.style = Paint.Style.FILL
                fill.color = Color.parseColor("#FFF3B0")
                fill.alpha = (150f + 100f * sin(sec * 6f + ray[0])).toInt().coerceIn(0, 255)
                c.drawCircle(ray[ray.size - 2], ray[ray.size - 1], 3.5f * d, fill)
            }
        }
        // The comic "crack!" burst
        if (t < 0.55f) {
            val bounce = if (sec < 0.4f) 0.6f + 0.6f * sin(sec / 0.4f * (PI.toFloat() / 2f)) else 1f
            val outer = 58f * d * bounce
            val inner = 42f * d * bounce
            path.reset()
            for (i in 0 until 24) {
                val r = if (i % 2 == 0) outer else inner
                val a = i * (2f * PI.toFloat() / 24f)
                if (i == 0) path.moveTo(cx + cos(a) * r, cy + sin(a) * r) else path.lineTo(cx + cos(a) * r, cy + sin(a) * r)
            }
            path.close()
            fill.style = Paint.Style.FILL
            fill.color = Color.parseColor("#FFE27A")
            fill.alpha = 255
            c.drawPath(path, fill)
            line.color = Color.parseColor("#7C5CFF")
            line.strokeWidth = 3f * d
            c.drawPath(path, line)
            text.color = Color.parseColor("#5B3FD6")
            text.alpha = 255
            text.textSize = 21f * d * bounce
            c.drawText("كراك!", cx, cy + 8f * d, text)
        }
        label(c, "مزحة! ما انكسر شيء", t)
    }

    private fun drawSplat(c: Canvas, sec: Float, t: Float) {
        for (b in blobs) {
            val age = sec - b[4]
            if (age <= 0f) continue
            val s = (age / 0.3f).coerceIn(0f, 1f)
            val color = pastel[b[3].toInt()]
            val r = b[2] * s
            fill.style = Paint.Style.FILL
            fill.color = color
            fill.alpha = 205
            c.drawCircle(b[0], b[1], r, fill)
            for (k in 0 until 6) {
                val a = k * (2f * PI.toFloat() / 6f) + b[4]
                c.drawCircle(b[0] + cos(a) * r * 0.85f, b[1] + sin(a) * r * 0.85f, r * 0.34f, fill)
            }
            // a drip that slowly slides down
            val drip = min(60f * d, maxOf(0f, (age - 0.3f) * 40f * d))
            if (drip > 0f) {
                line.color = color
                line.alpha = 205
                line.strokeWidth = r * 0.3f
                c.drawLine(b[0], b[1] + r * 0.6f, b[0], b[1] + r * 0.6f + drip, line)
                c.drawCircle(b[0], b[1] + r * 0.6f + drip, r * 0.18f, fill)
            }
        }
        label(c, "مزحة! ألوان وهمية وتذوب", t)
    }

    private fun drawBubbles(c: Canvas, sec: Float) {
        for (b in bubbles) {
            val age = sec - b[7]
            if (age <= 0f) continue
            val color = pastel[b[8].toInt()]
            if (age < b[6]) {
                val x = b[0] + sin(age * 2f + b[5]) * b[4]
                val y = b[1] - b[3] * age
                val a = if (age > b[6] * 0.8f) ((b[6] - age) / (b[6] * 0.2f)).coerceIn(0f, 1f) else 1f
                fill.style = Paint.Style.FILL
                fill.color = Color.WHITE
                fill.alpha = (55f * a).toInt()
                c.drawCircle(x, y, b[2], fill)
                line.color = color
                line.alpha = (220f * a).toInt()
                line.strokeWidth = 2f * d
                c.drawCircle(x, y, b[2], line)
                fill.color = Color.WHITE
                fill.alpha = (200f * a).toInt()
                c.drawCircle(x - b[2] * 0.35f, y - b[2] * 0.35f, b[2] * 0.17f, fill)
            } else if (age < b[6] + 0.25f) {
                // a little pop
                val k = (age - b[6]) / 0.25f
                val x = b[0] + sin(b[6] * 2f + b[5]) * b[4]
                val y = b[1] - b[3] * b[6]
                fill.style = Paint.Style.FILL
                fill.color = color
                fill.alpha = (200f * (1f - k)).toInt()
                for (j in 0 until 6) {
                    val a = j * (2f * PI.toFloat() / 6f)
                    c.drawCircle(x + cos(a) * b[2] * (1f + k), y + sin(a) * b[2] * (1f + k), 2.4f * d, fill)
                }
            }
        }
    }
}
