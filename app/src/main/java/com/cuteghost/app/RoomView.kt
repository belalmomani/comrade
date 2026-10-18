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
import kotlin.math.min
import kotlin.math.sin

/**
 * The ghosts' private room on a transparent background: a cloud bed with soft pillows, a moon
 * lamp and a corner with toys. Unit box 270 x 170; the beds are at y = 118.
 */
class RoomView(context: Context) : View(context) {

    private var clock = 0f
    private var running = false
    private var lastTick = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, 40f,
            Color.parseColor("#99FFE680"), Color.parseColor("#00FFE680"),
            Shader.TileMode.CLAMP
        )
    }
    private val rect = RectF()
    private val moon = Path()

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

    init {
        // Crescent moon: a circle with a smaller circle cut out
        val outer = Path().apply { addCircle(14f, 82f, 11f, Path.Direction.CW) }
        val inner = Path().apply { addCircle(19f, 79f, 9.5f, Path.Direction.CW) }
        moon.set(outer)
        moon.op(inner, Path.Op.DIFFERENCE)
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
        val s = min(width / 270f, height / 170f)
        canvas.save()
        canvas.translate((width - 270f * s) / 2f, (height - 170f * s) / 2f)
        canvas.scale(s, s)

        // Moon lamp on a small cloud table (left)
        val pulse = 0.7f + 0.3f * sin(clock * 1.8f)
        canvas.save()
        canvas.translate(14f, 82f)
        glow.alpha = (255f * pulse).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, 40f, glow)
        canvas.restore()
        fill.style = Paint.Style.FILL
        fill.color = Color.parseColor("#FFE27A")
        canvas.drawPath(moon, fill)
        line.color = Color.parseColor("#E0A800")
        line.strokeWidth = 1.2f
        canvas.drawPath(moon, line)
        line.color = Color.parseColor("#B8A6F5")
        line.strokeWidth = 2.2f
        canvas.drawLine(14f, 93f, 14f, 150f, line)
        fill.color = Color.WHITE
        rect.set(2f, 146f, 30f, 162f)
        canvas.drawRoundRect(rect, 8f, 8f, fill)
        line.color = Color.parseColor("#D9D2FF")
        line.strokeWidth = 1.4f
        canvas.drawRoundRect(rect, 8f, 8f, line)

        // Pillows (one under each sleeper)
        val pillowColors = intArrayOf(
            Color.parseColor("#FFD6E8"), Color.parseColor("#D6E4FF"), Color.parseColor("#FFF0B3")
        )
        for (i in 0 until 3) {
            val cx = 58f + 70f * i
            fill.color = pillowColors[i]
            rect.set(cx - 24f, 104f, cx + 24f, 126f)
            canvas.drawRoundRect(rect, 10f, 10f, fill)
            line.color = Color.parseColor("#E5DDF7")
            line.strokeWidth = 1.2f
            canvas.drawRoundRect(rect, 10f, 10f, line)
        }

        // Cloud bed
        fill.color = Color.parseColor("#EEE9FF")
        rect.set(26f, 134f, 246f, 164f)
        canvas.drawRoundRect(rect, 16f, 16f, fill)
        fill.color = Color.WHITE
        for (cx in floatArrayOf(48f, 88f, 130f, 172f, 212f)) {
            canvas.drawCircle(cx, 128f, 17f, fill)
        }
        rect.set(30f, 126f, 242f, 156f)
        canvas.drawRoundRect(rect, 14f, 14f, fill)

        // Toy corner (right): blocks and a ball on a little rug
        fill.color = Color.parseColor("#E9E2FF")
        rect.set(238f, 158f, 268f, 166f)
        canvas.drawRoundRect(rect, 4f, 4f, fill)
        fill.color = Color.parseColor("#3D8BFD")
        rect.set(240f, 144f, 254f, 158f)
        canvas.drawRoundRect(rect, 2f, 2f, fill)
        fill.color = Color.parseColor("#E63946")
        rect.set(254f, 144f, 268f, 158f)
        canvas.drawRoundRect(rect, 2f, 2f, fill)
        fill.color = Color.parseColor("#FFD54A")
        rect.set(247f, 130f, 261f, 144f)
        canvas.drawRoundRect(rect, 2f, 2f, fill)
        fill.color = Color.parseColor("#62D6A9")
        canvas.drawCircle(232f, 151f, 7f, fill)
        line.color = Color.WHITE
        line.strokeWidth = 1.6f
        canvas.drawLine(226f, 149f, 238f, 153f, line)

        canvas.restore()
    }
}
