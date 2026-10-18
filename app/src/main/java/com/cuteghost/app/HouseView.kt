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
 * A small house floating above the clouds: purple roof, glowing windows, and a door that opens
 * by itself when a ghost comes near. Transparent background, gently bobbing.
 */
class HouseView(context: Context) : View(context) {

    /** 0 = door closed, 1 = fully open. */
    var doorTarget = 0f
    private var door = 0f
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
            0f, 0f, 14f,
            Color.parseColor("#B3FFE27A"), Color.parseColor("#00FFE27A"),
            Shader.TileMode.CLAMP
        )
    }
    private val rect = RectF()
    private val path = Path()

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastTick) / 1000f).coerceIn(0f, 0.05f)
            lastTick = now
            clock += dt
            door += (doorTarget - door) * min(1f, dt * 9f)
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

    private fun cloudPuff(c: Canvas, cx: Float, cy: Float, r: Float) {
        c.drawCircle(cx, cy, r, fill)
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width / 96f, height / 104f)
        canvas.save()
        canvas.translate((width - 96f * s) / 2f, (height - 104f * s) / 2f)
        canvas.scale(s, s)

        // ----- Cloud base (bobs a little against the house) -----
        val cloudDy = sin(clock * 1.6f + 1f) * 1.2f
        canvas.save()
        canvas.translate(0f, cloudDy)
        fill.style = Paint.Style.FILL
        fill.color = Color.parseColor("#EEE9FF")
        rect.set(12f, 90f, 84f, 101f)
        canvas.drawRoundRect(rect, 6f, 6f, fill)
        fill.color = Color.WHITE
        cloudPuff(canvas, 22f, 90f, 11f)
        cloudPuff(canvas, 38f, 86f, 14f)
        cloudPuff(canvas, 56f, 88f, 13f)
        cloudPuff(canvas, 73f, 91f, 10f)
        rect.set(14f, 88f, 82f, 98f)
        canvas.drawRoundRect(rect, 5f, 5f, fill)
        canvas.restore()

        // ----- House (bobs) -----
        val dy = sin(clock * 1.6f) * 2.5f
        canvas.save()
        canvas.translate(0f, dy)

        // Chimney
        fill.color = Color.parseColor("#5B3FD6")
        rect.set(58f, 26f, 66f, 44f)
        canvas.drawRect(rect, fill)

        // Walls
        fill.color = Color.parseColor("#F6EEFF")
        rect.set(22f, 50f, 74f, 88f)
        canvas.drawRoundRect(rect, 4f, 4f, fill)
        line.color = Color.parseColor("#CBB8FF")
        line.strokeWidth = 1.8f
        canvas.drawRoundRect(rect, 4f, 4f, line)

        // Purple roof with a little star
        path.reset()
        path.moveTo(16f, 52f)
        path.lineTo(48f, 22f)
        path.lineTo(80f, 52f)
        path.close()
        fill.color = Color.parseColor("#7C5CFF")
        canvas.drawPath(path, fill)
        line.color = Color.parseColor("#5B3FD6")
        line.strokeWidth = 2f
        canvas.drawPath(path, line)
        fill.color = Color.parseColor("#FFE27A")
        canvas.drawCircle(48f, 38f, 3.2f, fill)

        // Glowing windows
        val pulse = 0.75f + 0.25f * sin(clock * 2.4f)
        for (cx in floatArrayOf(31f, 65f)) {
            canvas.save()
            canvas.translate(cx, 66f)
            glow.alpha = (255f * pulse).toInt().coerceIn(0, 255)
            canvas.drawCircle(0f, 0f, 14f, glow)
            canvas.restore()
            fill.color = Color.parseColor("#FFE27A")
            rect.set(cx - 5f, 60f, cx + 5f, 72f)
            canvas.drawRoundRect(rect, 2.5f, 2.5f, fill)
            line.color = Color.parseColor("#E0A800")
            line.strokeWidth = 1.2f
            canvas.drawRoundRect(rect, 2.5f, 2.5f, line)
            canvas.drawLine(cx, 60f, cx, 72f, line)
            canvas.drawLine(cx - 5f, 66f, cx + 5f, 66f, line)
        }

        // Door: warm light inside, the leaf swings open when a ghost is near
        path.reset()
        path.moveTo(40f, 88f)
        path.lineTo(40f, 72f)
        path.quadTo(40f, 64f, 48f, 64f)
        path.quadTo(56f, 64f, 56f, 72f)
        path.lineTo(56f, 88f)
        path.close()
        fill.color = Color.parseColor("#FFD98A")
        canvas.drawPath(path, fill)
        val leafW = 16f * (1f - 0.8f * door)
        fill.color = Color.parseColor("#A98BFF")
        rect.set(40f, 66f, 40f + leafW, 88f)
        canvas.drawRoundRect(rect, 3f, 3f, fill)
        line.color = Color.parseColor("#7C5CFF")
        line.strokeWidth = 1.2f
        canvas.drawRoundRect(rect, 3f, 3f, line)
        fill.color = Color.parseColor("#FFE27A")
        canvas.drawCircle(40f + leafW - 3f, 78f, 1.4f, fill)

        canvas.restore()
        canvas.restore()
    }
}
