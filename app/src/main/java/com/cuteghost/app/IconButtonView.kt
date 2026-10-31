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

/**
 * A big round picture button for children who cannot read yet (a three-year-old): the picture says
 * what it does, it gently bobs to invite a touch, and a green dot says "you can do it now".
 */
class IconButtonView(context: Context, private val kind: Int, private val color: Int) : View(context) {

    companion object {
        const val SHOP = 0
        const val UPGRADE = 1
        const val HOUSES = 2
        const val VILLAGE = 3
        const val PLAY = 4
        const val CHECK = 5
        const val CLOSE = 6
        const val BACK = 7
        const val CAT_BED = 8
        const val CAT_LIVING = 9
        const val CAT_DECOR = 10
        const val CAT_SURFACES = 11
        const val CAT_WINDOWS = 12
        const val CAT_MAGIC = 13
        const val CAT_GHOST = 14

        fun forCategory(cat: FCat): Int = CAT_BED + cat.ordinal
    }

    /** Grey look: not possible right now. */
    var dimmed = false
        set(value) { field = value; invalidate() }

    /** The chosen one (a golden ring). */
    var chosen = false
        set(value) { field = value; invalidate() }

    /** A green dot: "you can do this now". */
    var badge = false
        set(value) { field = value; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()
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

    private fun f(c: Int) {
        fill.style = Paint.Style.FILL
        fill.color = c
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        rect.set(l, t, r, b)
        f(color)
        c.drawRoundRect(rect, rad, rad, fill)
    }

    private fun tri(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        f(color)
        c.drawPath(path, fill)
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        path.reset()
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) r else r * 0.45f
            val a = (-90f + i * 36f) * (PI.toFloat() / 180f)
            if (i == 0) path.moveTo(cx + cos(a) * rad, cy + sin(a) * rad) else path.lineTo(cx + cos(a) * rad, cy + sin(a) * rad)
        }
        path.close()
        f(color)
        c.drawPath(path, fill)
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        val pulse = 1f + 0.035f * sin(clock * 3f + kind)
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(pulse, pulse)
        f(Color.parseColor("#22000000"))
        canvas.drawCircle(0f, s * 0.04f, s * 0.47f, fill)
        f(if (dimmed) Color.parseColor("#B8B2C8") else color)
        canvas.drawCircle(0f, 0f, s * 0.46f, fill)
        if (chosen) {
            line.color = Color.parseColor("#FFC93C")
            line.strokeWidth = s * 0.07f
            canvas.drawCircle(0f, 0f, s * 0.46f, line)
        }
        val u = s * 0.64f / 100f
        canvas.save()
        canvas.scale(u, u)
        canvas.translate(-50f, -50f)
        drawIcon(canvas)
        canvas.restore()
        canvas.restore()
        if (badge) {
            f(Color.parseColor("#2EAD4B"))
            canvas.drawCircle(width * 0.82f, height * 0.18f, s * 0.15f, fill)
            line.color = Color.WHITE
            line.strokeWidth = s * 0.05f
            val bx = width * 0.82f
            val by = height * 0.18f
            canvas.drawLine(bx, by + s * 0.06f, bx, by - s * 0.07f, line)
            canvas.drawLine(bx - s * 0.05f, by - s * 0.02f, bx, by - s * 0.07f, line)
            canvas.drawLine(bx + s * 0.05f, by - s * 0.02f, bx, by - s * 0.07f, line)
        }
    }

    /** The picture, drawn in a 100 x 100 box. */
    private fun drawIcon(c: Canvas) {
        val white = Color.WHITE
        when (kind) {
            SHOP, CAT_BED -> {
                rr(c, 12f, 56f, 88f, 78f, 5f, white)
                rr(c, 8f, 38f, 22f, 78f, 4f, Color.parseColor("#FFE0F0"))
                rr(c, 24f, 46f, 46f, 58f, 5f, Color.parseColor("#FF8FB8"))
                rr(c, 42f, 58f, 88f, 78f, 5f, Color.parseColor("#FFD54A"))
                star(c, 74f, 28f, 13f, Color.parseColor("#FFE27A"))
            }
            UPGRADE -> {
                tri(c, 30f, 8f, 70f, 8f, 50f, -8f, white)
                tri(c, 50f, 4f, 76f, 34f, 24f, 34f, Color.parseColor("#FFE27A"))
                rr(c, 40f, 30f, 60f, 50f, 2f, Color.parseColor("#FFE27A"))
                tri(c, 20f, 66f, 50f, 46f, 80f, 66f, white)
                rr(c, 28f, 64f, 72f, 90f, 3f, white)
                rr(c, 44f, 72f, 56f, 90f, 5f, Color.parseColor("#7C5CFF"))
            }
            HOUSES -> {
                for (i in 0 until 3) {
                    val x = 6f + i * 30f
                    val tint = intArrayOf(Color.parseColor("#FFD6E8"), Color.parseColor("#D6F5E8"), Color.parseColor("#FFF0B3"))[i]
                    tri(c, x - 2f, 52f, x + 14f, 32f, x + 30f, 52f, Color.parseColor("#7C5CFF"))
                    rr(c, x + 2f, 50f, x + 26f, 80f, 2f, tint)
                    rr(c, x + 10f, 62f, x + 18f, 80f, 3f, Color.parseColor("#B39BFF"))
                }
            }
            VILLAGE -> {
                rr(c, 14f, 56f, 22f, 86f, 2f, Color.parseColor("#8B5E3C"))
                f(Color.parseColor("#5FC577"))
                c.drawCircle(18f, 42f, 18f, fill)
                tri(c, 36f, 62f, 54f, 44f, 72f, 62f, Color.parseColor("#FF8FB8"))
                rr(c, 40f, 60f, 68f, 86f, 2f, white)
                tri(c, 62f, 70f, 76f, 54f, 92f, 70f, Color.parseColor("#8FD0FF"))
                rr(c, 66f, 68f, 88f, 86f, 2f, white)
            }
            PLAY -> {
                f(Color.parseColor("#FF6B8A"))
                c.drawCircle(50f, 38f, 28f, fill)
                rr(c, 38f, 12f, 62f, 66f, 12f, Color.parseColor("#FFE27A"))
                line.color = Color.parseColor("#8B5E3C")
                line.strokeWidth = 3f
                c.drawLine(32f, 62f, 40f, 78f, line)
                c.drawLine(68f, 62f, 60f, 78f, line)
                rr(c, 38f, 78f, 62f, 92f, 4f, Color.parseColor("#C97B4A"))
            }
            CHECK -> {
                line.color = white
                line.strokeWidth = 13f
                path.reset()
                path.moveTo(24f, 52f)
                path.lineTo(43f, 71f)
                path.lineTo(78f, 30f)
                c.drawPath(path, line)
            }
            CLOSE -> {
                line.color = white
                line.strokeWidth = 13f
                c.drawLine(28f, 28f, 72f, 72f, line)
                c.drawLine(72f, 28f, 28f, 72f, line)
            }
            BACK -> {
                line.color = white
                line.strokeWidth = 12f
                path.reset()
                path.moveTo(60f, 24f)
                path.lineTo(34f, 50f)
                path.lineTo(60f, 76f)
                c.drawPath(path, line)
                c.drawLine(34f, 50f, 78f, 50f, line)
            }
            CAT_LIVING -> {
                rr(c, 20f, 32f, 80f, 56f, 8f, Color.parseColor("#FFE0F0"))
                rr(c, 10f, 46f, 90f, 72f, 8f, white)
                rr(c, 6f, 44f, 22f, 76f, 6f, Color.parseColor("#FFD6E8"))
                rr(c, 78f, 44f, 94f, 76f, 6f, Color.parseColor("#FFD6E8"))
                rr(c, 16f, 76f, 24f, 86f, 2f, Color.parseColor("#8B5E3C"))
                rr(c, 76f, 76f, 84f, 86f, 2f, Color.parseColor("#8B5E3C"))
            }
            CAT_DECOR -> {
                f(Color.parseColor("#FFF3B0"))
                c.drawCircle(50f, 40f, 30f, fill)
                tri(c, 28f, 54f, 72f, 54f, 62f, 24f, Color.parseColor("#FFC93C"))
                tri(c, 28f, 54f, 72f, 54f, 38f, 24f, Color.parseColor("#FFD54A"))
                rr(c, 46f, 54f, 54f, 80f, 2f, white)
                rr(c, 32f, 80f, 68f, 90f, 4f, white)
            }
            CAT_SURFACES -> {
                rr(c, 10f, 14f, 90f, 54f, 4f, Color.parseColor("#F1E9FF"))
                rr(c, 10f, 54f, 90f, 88f, 4f, Color.parseColor("#D9B88F"))
                line.color = Color.parseColor("#B98A5E")
                line.strokeWidth = 2f
                c.drawLine(10f, 68f, 90f, 68f, line)
                c.drawLine(10f, 78f, 90f, 78f, line)
                star(c, 30f, 34f, 9f, Color.parseColor("#FFC93C"))
            }
            CAT_WINDOWS -> {
                rr(c, 24f, 18f, 76f, 84f, 22f, white)
                rr(c, 29f, 23f, 71f, 79f, 18f, Color.parseColor("#9FD3FF"))
                rr(c, 14f, 14f, 30f, 86f, 6f, Color.parseColor("#FF8FB8"))
                rr(c, 70f, 14f, 86f, 86f, 6f, Color.parseColor("#FF8FB8"))
                star(c, 50f, 44f, 10f, Color.parseColor("#FFF3B0"))
            }
            CAT_MAGIC -> {
                star(c, 50f, 48f, 34f, Color.parseColor("#FFE27A"))
                star(c, 20f, 22f, 11f, white)
                star(c, 82f, 24f, 9f, white)
                star(c, 80f, 78f, 11f, white)
            }
            CAT_GHOST -> {
                f(white)
                c.drawCircle(50f, 50f, 28f, fill)
                rr(c, 22f, 50f, 78f, 82f, 4f, white)
                tri(c, 28f, 30f, 50f, 2f, 72f, 30f, Color.parseColor("#5B8DEF"))
                f(Color.parseColor("#2B2540"))
                c.drawCircle(40f, 54f, 4f, fill)
                c.drawCircle(60f, 54f, 4f, fill)
            }
        }
    }
}

/** A picture meter: star or building material, a bar filled by what you have and "have / need". */
class MeterView(context: Context, private val icon: Int, private val have: Int, private val need: Int) : View(context) {
    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2540")
        textSize = 15f * resources.displayMetrics.density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        val ok = have >= need
        // The picture: a star, or two bricks for building materials
        if (icon == 0) {
            path.reset()
            for (i in 0 until 10) {
                val rad = if (i % 2 == 0) h * 0.46f else h * 0.2f
                val a = (-90f + i * 36f) * (PI.toFloat() / 180f)
                val x = h * 0.5f + cos(a) * rad
                val y = h * 0.52f + sin(a) * rad
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            fill.color = Color.parseColor("#FFC93C")
            canvas.drawPath(path, fill)
        } else {
            fill.color = Color.parseColor("#C97B4A")
            rect.set(h * 0.08f, h * 0.2f, h * 0.55f, h * 0.5f)
            canvas.drawRoundRect(rect, 4f * d, 4f * d, fill)
            rect.set(h * 0.45f, h * 0.5f, h * 0.92f, h * 0.8f)
            canvas.drawRoundRect(rect, 4f * d, 4f * d, fill)
            rect.set(h * 0.08f, h * 0.5f, h * 0.4f, h * 0.8f)
            canvas.drawRoundRect(rect, 4f * d, 4f * d, fill)
        }
        // The bar
        val left = h * 1.1f
        val right = w - 70f * d
        fill.color = Color.parseColor("#E1DAF2")
        rect.set(left, h * 0.3f, right, h * 0.7f)
        canvas.drawRoundRect(rect, h * 0.2f, h * 0.2f, fill)
        val p = if (need <= 0) 1f else (have.toFloat() / need).coerceIn(0f, 1f)
        fill.color = if (ok) Color.parseColor("#2EAD4B") else Color.parseColor("#FFB020")
        rect.set(left, h * 0.3f, left + (right - left) * p, h * 0.7f)
        canvas.drawRoundRect(rect, h * 0.2f, h * 0.2f, fill)
        canvas.drawText("$have/$need", w - 34f * d, h * 0.68f, text)
    }
}
