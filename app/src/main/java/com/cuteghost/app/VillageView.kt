package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.sin

/** The village scene: sky, hills, a path and the houses of the grown ghosts, oldest first. */
class VillageView(context: Context, private val villagers: List<Villager>) : View(context) {

    companion object {
        const val SLOT_DP = 150
        const val START_DP = 20
        const val GROUND = 0.64f

        fun houseCenterX(index: Int, d: Float): Float = (START_DP + SLOT_DP * index + SLOT_DP / 2f) * d
        fun houseScale(d: Float): Float = 1.25f * d
    }

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2540")
        textSize = 14f * resources.displayMetrics.density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private var clock = 0f
    private var running = false
    private var lastTick = 0L
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

    private fun f(color: Int, alpha: Int = 255) {
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.color = color
        fill.alpha = alpha
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val ground = h * GROUND

        fill.style = Paint.Style.FILL
        fill.shader = LinearGradient(0f, 0f, 0f, ground, Color.parseColor("#8ECBFF"), Color.parseColor("#FFF4E0"), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null

        // The sun and drifting clouds
        f(Color.parseColor("#FFE27A"))
        canvas.drawCircle(w.coerceAtMost(900f * d) * 0.82f, h * 0.12f, 34f * d, fill)
        for (i in 0 until 5) {
            val x = ((clock * (7f + i * 2f) * d + i * w * 0.23f) % (w + 200f * d)) - 100f * d
            val y = h * (0.10f + 0.07f * (i % 3))
            f(Color.WHITE, 205)
            canvas.drawCircle(x - 22f * d, y + 4f * d, 15f * d, fill)
            canvas.drawCircle(x, y - 4f * d, 21f * d, fill)
            canvas.drawCircle(x + 24f * d, y + 4f * d, 16f * d, fill)
        }

        // Far hills, the green ground and a path
        f(Color.parseColor("#A9DFA0"))
        var hx = -40f * d
        while (hx < w + 120f * d) {
            rect.set(hx, ground - 70f * d, hx + 220f * d, ground + 60f * d)
            canvas.drawOval(rect, fill)
            hx += 170f * d
        }
        f(Color.parseColor("#7BCB78"))
        canvas.drawRect(0f, ground, w, h, fill)
        f(Color.parseColor("#E9D2A5"))
        rect.set(0f, ground + 18f * d, w, ground + 44f * d)
        canvas.drawRoundRect(rect, 13f * d, 13f * d, fill)
        var fx = 30f * d
        var k = 0
        while (fx < w) {
            f(if (k % 2 == 0) Color.parseColor("#FF8FB8") else Color.parseColor("#FFE27A"))
            canvas.drawCircle(fx, ground + 70f * d + (k % 3) * 22f * d, 3.2f * d, fill)
            fx += 53f * d
            k++
        }

        if (villagers.isEmpty()) {
            val m1 = "القرية فارغة بعد"
            val m2 = "عندما يكبر ابن ويصبح بحجم أمه، ينتقل ليعيش هنا في بيت خاص"
            text.textSize = 18f * d
            canvas.drawText(m1, minOf(w, resources.displayMetrics.widthPixels.toFloat()) / 2f, h * 0.30f, text)
            text.textSize = 13f * d
            canvas.drawText(m2, minOf(w, resources.displayMetrics.widthPixels.toFloat()) / 2f, h * 0.30f + 26f * d, text)
            text.textSize = 14f * d
            return
        }

        // The houses with a name plate under each
        val s = houseScale(d)
        for ((i, v) in villagers.withIndex()) {
            val cx = houseCenterX(i, d)
            canvas.save()
            canvas.translate(cx - 48f * s, ground - 88f * s + sin(clock * 1.2f + i) * 1.5f * d)
            canvas.scale(s, s)
            HouseArt.draw(canvas, v.houseId, 1 + (v.houseId + i) % 4, clock, if (sin(clock * 0.8f + i * 1.7f) > 0.7f) 1f else 0f)
            canvas.restore()

            f(Color.WHITE, 235)
            val half = 54f * d
            rect.set(cx - half, ground + 78f * d, cx + half, ground + 104f * d)
            canvas.drawRoundRect(rect, 12f * d, 12f * d, fill)
            canvas.drawText(v.label, cx, ground + 97f * d, text)
        }
    }
}
