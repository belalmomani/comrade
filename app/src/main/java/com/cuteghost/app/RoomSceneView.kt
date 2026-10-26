package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.min
import kotlin.math.sin

/**
 * The inside of a house in layers (unit box 100 x 150): the back layer is the room (wall,
 * window with moving sky, floor, rug, beds), then the sleeping ghosts are views placed between
 * the layers, and the front layer (bed covers, lamp glow, fairy lights) is drawn over them.
 */
class RoomSceneView(
    context: Context,
    private val houseId: Int,
    private val level: Int,
    private val front: Boolean
) : View(context) {

    companion object {
        /** Level 1-2: one bed, level 3: two beds, level 4-5: three beds. */
        fun bedCount(level: Int): Int = when {
            level >= 4 -> 3
            level == 3 -> 2
            else -> 1
        }

        fun bedXs(n: Int): FloatArray = when (n) {
            1 -> floatArrayOf(50f)
            2 -> floatArrayOf(31f, 69f)
            else -> floatArrayOf(19f, 50f, 81f)
        }

        fun bedWidth(n: Int): Float = when (n) {
            1 -> 34f
            2 -> 32f
            else -> 28f
        }

        /** left, top, width, height (scene units) of the box of the ghost sleeping in bed [i]. */
        fun ghostBox(i: Int, n: Int): FloatArray {
            val w = bedWidth(n) * 1.05f
            val h = w * 1.28f
            return floatArrayOf(bedXs(n)[i] - w / 2f, 104f - h, w, h)
        }

        const val SCENE_W = 100f
        const val SCENE_H = 150f
    }

    private val theme = HouseDefs.get(houseId)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, 22f,
            Color.parseColor("#AAFFE680"), Color.parseColor("#00FFE680"),
            Shader.TileMode.CLAMP
        )
    }
    private val skyShader = LinearGradient(0f, 10f, 0f, 44f, theme.sky1, theme.sky2, Shader.TileMode.CLAMP)
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

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1f - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1f - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
    )

    private fun f(color: Int, alpha: Int = 255) {
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.color = color
        fill.alpha = alpha
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        rect.set(l, t, r, b)
        f(color)
        c.drawRoundRect(rect, rad, rad, fill)
    }

    override fun onDraw(canvas: Canvas) {
        val s = width / SCENE_W
        val oy = (height - SCENE_H * s) / 2f
        canvas.save()
        canvas.translate(0f, oy)
        canvas.scale(s, s)
        if (front) drawFront(canvas) else drawBack(canvas)
        canvas.restore()
    }

    // ================= back layer: the room =================
    private fun drawBack(c: Canvas) {
        val night = houseId == 4

        // Wall with soft stripes
        f(theme.wall)
        rect.set(0f, 0f, 100f, 104f)
        c.drawRect(rect, fill)
        f(Color.WHITE, 40)
        var x = 0f
        while (x < 100f) {
            rect.set(x, 0f, x + 10f, 104f)
            c.drawRect(rect, fill)
            x += 20f
        }
        if (night) {
            for (i in 0 until 14) {
                f(Color.parseColor("#FFE27A"), (120f + 100f * sin(clock * 2f + i)).toInt().coerceIn(0, 255))
                c.drawCircle(4f + (i * 29f) % 92f, 4f + (i * 17f) % 8f + (i % 3) * 14f, 0.8f, fill)
            }
        }

        // Window with a moving sky
        rect.set(30f, 10f, 70f, 44f)
        c.save()
        path.reset()
        path.addRoundRect(rect, 16f, 16f, Path.Direction.CW)
        c.clipPath(path)
        fill.style = Paint.Style.FILL
        fill.shader = skyShader
        fill.alpha = 255
        c.drawRect(rect, fill)
        fill.shader = null
        for (i in 0 until 3) {
            val cx = 24f + (clock * 4f + i * 22f) % 60f
            val cy = 20f + i * 8f
            f(Color.WHITE, 225)
            rect.set(cx - 8f, cy - 3f, cx + 8f, cy + 3f)
            c.drawOval(rect, fill)
            c.drawCircle(cx - 2f, cy - 3f, 3.4f, fill)
        }
        if (night) {
            for (i in 0 until 9) {
                f(Color.parseColor("#FFF3B0"), (150f + 100f * sin(clock * 2.4f + i)).toInt().coerceIn(0, 255))
                c.drawCircle(32f + (i * 13f) % 36f, 12f + (i * 7f) % 24f, 0.8f, fill)
            }
        }
        if (houseId == 5) {
            for (i in 0 until 12) {
                f(Color.WHITE)
                c.drawCircle(32f + (i * 37f) % 36f + sin(clock + i) * 1.4f, 10f + (clock * 6f + i * 7f) % 34f, 0.9f, fill)
            }
        }
        if (level >= 4) {
            // the balcony garden seen through the window
            f(mix(theme.accent, Color.WHITE, 0.55f))
            rect.set(30f, 38f, 70f, 44f)
            c.drawRect(rect, fill)
            for (px in floatArrayOf(38f, 50f, 62f)) {
                f(Color.parseColor("#C97B4A"))
                rect.set(px - 2.5f, 34f, px + 2.5f, 38f)
                c.drawRect(rect, fill)
                f(Color.parseColor("#5FC577"))
                c.drawCircle(px - 1.5f, 32f, 2.4f, fill)
                c.drawCircle(px + 1.5f, 31f, 2.6f, fill)
            }
        }
        c.restore()
        rect.set(30f, 10f, 70f, 44f)
        line.color = theme.accent
        line.strokeWidth = 2f
        c.drawRoundRect(rect, 16f, 16f, line)
        line.strokeWidth = 1.2f
        c.drawLine(50f, 10f, 50f, 44f, line)
        c.drawLine(30f, 27f, 70f, 27f, line)
        rr(c, 28f, 44f, 72f, 47f, 1.5f, mix(theme.accent, Color.WHITE, 0.4f))

        // Curtains
        val curtain = mix(theme.accent, Color.WHITE, 0.55f)
        rr(c, 22f, 8f, 30f, 46f, 3f, curtain)
        rr(c, 70f, 8f, 78f, 46f, 3f, curtain)
        rr(c, 22f, 24f, 30f, 27f, 1f, mix(curtain, Color.BLACK, 0.2f))
        rr(c, 70f, 24f, 78f, 27f, 1f, mix(curtain, Color.BLACK, 0.2f))

        // Picture on the wall
        rr(c, 10f, 54f, 26f, 67f, 1.5f, Color.parseColor("#C9A57A"))
        rr(c, 11.5f, 55.5f, 24.5f, 65.5f, 1f, Color.parseColor("#BFD9FF"))
        f(Color.WHITE)
        rect.set(14f, 58f, 22f, 62f)
        c.drawOval(rect, fill)
        c.drawCircle(17f, 58f, 2.4f, fill)

        // Moon lamp on the wall (its glow is in the front layer)
        f(Color.parseColor("#FFE27A"))
        c.drawCircle(90f, 60f, 4.2f, fill)
        f(theme.wall)
        c.drawCircle(92.2f, 58.8f, 3.6f, fill)

        // Floor, planks and baseboard
        f(theme.floor)
        rect.set(0f, 104f, 100f, 150f)
        c.drawRect(rect, fill)
        line.color = mix(theme.floor, Color.BLACK, 0.12f)
        line.strokeWidth = 0.6f
        c.drawLine(0f, 118f, 100f, 118f, line)
        c.drawLine(0f, 132f, 100f, 132f, line)
        var px = 8f
        while (px < 100f) {
            c.drawLine(px, 104f, px + 6f, 118f, line)
            c.drawLine(px + 14f, 118f, px + 8f, 132f, line)
            px += 20f
        }
        f(mix(theme.floor, Color.BLACK, 0.2f))
        rect.set(0f, 102f, 100f, 105f)
        c.drawRect(rect, fill)
        if (level >= 2) {
            line.color = theme.accent
            line.strokeWidth = 0.9f
            c.drawLine(0f, 101f, 100f, 101f, line)
        }

        // Round rug
        f(mix(theme.accent, Color.WHITE, 0.6f))
        rect.set(18f, 124f, 82f, 144f)
        c.drawOval(rect, fill)
        line.color = mix(theme.accent, Color.WHITE, 0.3f)
        line.strokeWidth = 1f
        rect.set(26f, 128f, 74f, 140f)
        c.drawOval(rect, line)

        // Beds (frame, mattress and pillow); the covers are in the front layer
        val n = bedCount(level)
        val xs = bedXs(n)
        val w = bedWidth(n)
        val pillows = intArrayOf(Color.parseColor("#FFD6E8"), Color.parseColor("#D6E4FF"), Color.parseColor("#FFF0B3"))
        for (i in 0 until n) {
            val cx = xs[i]
            rr(c, cx - w / 2f, 86f, cx + w / 2f, 108f, 4f, Color.parseColor("#D8CCFF"))
            rr(c, cx - w / 2f - 1f, 80f, cx - w / 2f + 3f, 108f, 2f, Color.parseColor("#B9A8F5"))
            rr(c, cx - w / 2f + 1.5f, 89f, cx + w / 2f - 1.5f, 99f, 3f, Color.WHITE)
            rr(c, cx - w * 0.40f, 83.5f, cx - w * 0.02f, 92f, 3.5f, pillows[i % 3])
        }
    }

    // ================= front layer: over the sleeping ghosts =================
    private fun drawFront(c: Canvas) {
        val n = bedCount(level)
        val xs = bedXs(n)
        val w = bedWidth(n)
        val covers = intArrayOf(Color.parseColor("#FFB6D0"), Color.parseColor("#9CC7FF"), Color.parseColor("#FFE08A"))
        for (i in 0 until n) {
            val cx = xs[i]
            val cover = covers[i % 3]
            rr(c, cx - w / 2f - 0.5f, 92f, cx + w / 2f + 0.5f, 109f, 4f, cover)
            rr(c, cx - w / 2f - 0.5f, 92f, cx + w / 2f + 0.5f, 96f, 2f, mix(cover, Color.WHITE, 0.45f))
            for (k in 0 until 4) {
                f(Color.WHITE, 150)
                c.drawCircle(cx - w * 0.36f + k * w * 0.24f, 101f + (k % 2) * 3f, 1.3f, fill)
            }
        }

        // Warm light of the moon lamp
        val pulse = 0.7f + 0.3f * sin(clock * 1.8f)
        c.save()
        c.translate(90f, 60f)
        glow.alpha = (255f * pulse).toInt().coerceIn(0, 255)
        c.drawCircle(0f, 0f, 22f, glow)
        c.restore()

        if (level >= 5) {
            // fairy lights over the top of the wall
            val colors = intArrayOf(Color.parseColor("#FFE27A"), Color.parseColor("#FF8FB8"), Color.parseColor("#8FD0FF"))
            line.color = Color.parseColor("#B8A6F5")
            line.strokeWidth = 0.6f
            for (i in 0 until 13) {
                val t = i / 12f
                val x = 4f + 92f * t
                val y = 5f + 5f * sin(Math.PI.toFloat() * t)
                f(colors[i % 3], (160f + 95f * sin(clock * 3f + i)).toInt().coerceIn(0, 255))
                c.drawCircle(x, y, 1.6f, fill)
            }
        }
    }
}
