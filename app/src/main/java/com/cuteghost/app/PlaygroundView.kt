package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Where the toys of the cloud playground are, as fractions of the screen (shared with the ghosts). */
object PlayGeo {
    fun swingPivotX(w: Float) = 0.24f * w
    fun swingPivotY(h: Float) = 0.30f * h
    fun swingLen(h: Float) = 0.17f * h
    fun swingAngle(t: Float) = sin(t * 2.2f) * 0.55f
    fun slideTopX(w: Float) = 0.60f * w
    fun slideTopY(h: Float) = 0.40f * h
    fun slideBottomX(w: Float) = 0.88f * w
    fun slideBottomY(h: Float) = 0.62f * h
    fun whirlX(w: Float) = 0.5f * w
    fun whirlY(h: Float) = 0.80f * h
    fun whirlR(w: Float) = 0.17f * w
    fun balloonX(w: Float) = 0.80f * w
    fun balloonY(h: Float, t: Float) = 0.19f * h + sin(t * 1.2f) * 0.01f * h
}

/**
 * The cloud playground: sky, drifting clouds, a swing, a slide, a star whirl and the little
 * balloon of the daily balloon game. The ghosts are views on top; [time] comes from the activity.
 */
class PlaygroundView(context: Context) : View(context) {

    var time = 0f
    var onBalloonTapped: (() -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()

    private fun f(color: Int, alpha: Int = 255) {
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.color = color
        fill.alpha = alpha
    }

    private fun ln(color: Int, w: Float) {
        line.color = color
        line.strokeWidth = w
        line.alpha = 255
    }

    private fun cloud(c: Canvas, cx: Float, cy: Float, s: Float, alpha: Int) {
        f(Color.WHITE, alpha)
        c.drawCircle(cx - 22f * s, cy + 4f * s, 14f * s, fill)
        c.drawCircle(cx, cy - 4f * s, 20f * s, fill)
        c.drawCircle(cx + 24f * s, cy + 4f * s, 15f * s, fill)
        rect.set(cx - 36f * s, cy + 2f * s, cx + 40f * s, cy + 18f * s)
        c.drawRoundRect(rect, 9f * s, 9f * s, fill)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val bx = PlayGeo.balloonX(width.toFloat())
            val by = PlayGeo.balloonY(height.toFloat(), time)
            if (hypot(e.x - bx, e.y - by) < 0.13f * width) {
                onBalloonTapped?.invoke()
                return true
            }
        }
        return e.actionMasked == MotionEvent.ACTION_DOWN
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // Sky
        fill.style = Paint.Style.FILL
        fill.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#9FD3FF"), Color.parseColor("#F6EEFF"), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        // Twinkling stars and drifting clouds
        for (i in 0 until 10) {
            f(Color.parseColor("#FFF3B0"), (120f + 110f * sin(time * 2f + i)).toInt().coerceIn(0, 255))
            canvas.drawCircle((i * 97f % 100f) / 100f * w, (6f + (i * 31f) % 24f) / 100f * h, 2f * d, fill)
        }
        for (i in 0 until 4) {
            val x = ((time * (6f + i * 2f) * d + i * w * 0.4f) % (w + 160f * d)) - 80f * d
            cloud(canvas, x, h * (0.10f + 0.12f * i), d * (0.8f + 0.12f * i), 190)
        }
        // The big cloud ground
        f(Color.parseColor("#EDE6FF"))
        canvas.drawRect(0f, h * 0.86f, w, h, fill)
        for (i in 0 until 6) {
            f(Color.WHITE)
            canvas.drawCircle(w * (0.08f + 0.18f * i), h * 0.87f, 0.09f * w, fill)
        }
        f(Color.WHITE)
        canvas.drawRect(0f, h * 0.89f, w, h, fill)

        drawSwing(canvas, w, h)
        drawSlide(canvas, w, h)
        drawWhirl(canvas, w, h)
        drawBalloon(canvas, w, h)
    }

    private fun drawSwing(c: Canvas, w: Float, h: Float) {
        val px = PlayGeo.swingPivotX(w)
        val py = PlayGeo.swingPivotY(h)
        val len = PlayGeo.swingLen(h)
        cloud(c, px, py + len + 0.19f * h, d * 1.5f, 235)
        ln(Color.parseColor("#7C5CFF"), 5f * d)
        c.drawLine(px - 0.16f * w, py + len + 0.16f * h, px - 0.05f * w, py - 8f * d, line)
        c.drawLine(px + 0.16f * w, py + len + 0.16f * h, px + 0.05f * w, py - 8f * d, line)
        c.drawLine(px - 0.07f * w, py - 8f * d, px + 0.07f * w, py - 8f * d, line)
        val a = PlayGeo.swingAngle(time)
        val sx = px + len * sin(a)
        val sy = py + len * cos(a)
        ln(Color.parseColor("#B9A8F5"), 2f * d)
        c.drawLine(px - 0.04f * w, py - 8f * d, sx - 0.04f * w, sy, line)
        c.drawLine(px + 0.04f * w, py - 8f * d, sx + 0.04f * w, sy, line)
        f(Color.parseColor("#FF8FB8"))
        rect.set(sx - 0.06f * w, sy, sx + 0.06f * w, sy + 8f * d)
        c.drawRoundRect(rect, 4f * d, 4f * d, fill)
    }

    private fun drawSlide(c: Canvas, w: Float, h: Float) {
        val tx = PlayGeo.slideTopX(w)
        val ty = PlayGeo.slideTopY(h)
        val bx = PlayGeo.slideBottomX(w)
        val by = PlayGeo.slideBottomY(h)
        cloud(c, (tx + bx) / 2f, by + 0.14f * h, d * 1.6f, 235)
        // ladder and tower
        ln(Color.parseColor("#E0A800"), 4f * d)
        c.drawLine(tx - 0.07f * w, ty + 0.02f * h, tx - 0.07f * w, by + 0.04f * h, line)
        c.drawLine(tx, ty + 0.02f * h, tx, by + 0.04f * h, line)
        var y = ty + 0.06f * h
        while (y < by) {
            c.drawLine(tx - 0.07f * w, y, tx, y, line)
            y += 0.035f * h
        }
        f(Color.parseColor("#FFC93C"))
        rect.set(tx - 0.09f * w, ty, tx + 0.02f * w, ty + 6f * d)
        c.drawRoundRect(rect, 3f * d, 3f * d, fill)
        // ramp
        path.reset()
        path.moveTo(tx, ty + 2f * d)
        path.lineTo(bx, by)
        path.lineTo(bx, by + 8f * d)
        path.lineTo(tx, ty + 10f * d)
        path.close()
        f(Color.parseColor("#FF8FB8"))
        c.drawPath(path, fill)
        ln(Color.parseColor("#E5469A"), 2f * d)
        c.drawPath(path, line)
    }

    private fun drawWhirl(c: Canvas, w: Float, h: Float) {
        val cx = PlayGeo.whirlX(w)
        val cy = PlayGeo.whirlY(h)
        val r = PlayGeo.whirlR(w)
        cloud(c, cx, cy + 0.05f * h, d * 1.8f, 235)
        f(Color.parseColor("#5B3FD6"))
        rect.set(cx - r, cy - 0.045f * h, cx + r, cy + 0.045f * h)
        c.drawOval(rect, fill)
        ln(Color.parseColor("#FFC93C"), 2.5f * d)
        c.drawOval(rect, line)
        // stars turning around the platform
        for (i in 0 until 6) {
            val a = time * 1.3f + i * (2f * PI.toFloat() / 6f)
            drawStar(c, cx + cos(a) * r * 0.85f, cy + sin(a) * 0.035f * h, 7f * d, Color.parseColor("#FFE27A"))
        }
        ln(Color.parseColor("#FFC93C"), 3f * d)
        c.drawLine(cx, cy, cx, cy - 0.12f * h, line)
        drawStar(c, cx, cy - 0.13f * h, 11f * d, Color.parseColor("#FFC93C"))
    }

    private fun drawStar(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        path.reset()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val a = (-90f + i * 36f) * (PI.toFloat() / 180f)
            if (i == 0) path.moveTo(cx + cos(a) * rr, cy + sin(a) * rr) else path.lineTo(cx + cos(a) * rr, cy + sin(a) * rr)
        }
        path.close()
        f(color)
        c.drawPath(path, fill)
    }

    /** The small balloon that starts the daily balloon game when touched. */
    private fun drawBalloon(c: Canvas, w: Float, h: Float) {
        val bx = PlayGeo.balloonX(w)
        val by = PlayGeo.balloonY(h, time)
        val r = 0.075f * w
        f(Color.parseColor("#FF6B8A"))
        c.drawCircle(bx, by - r * 0.4f, r, fill)
        f(Color.parseColor("#FFE27A"))
        rect.set(bx - r * 0.35f, by - r * 1.4f, bx + r * 0.35f, by + r * 0.6f)
        c.drawOval(rect, fill)
        ln(Color.parseColor("#B8324E"), 2f * d)
        c.drawCircle(bx, by - r * 0.4f, r, line)
        ln(Color.parseColor("#8B5E3C"), 1.5f * d)
        c.drawLine(bx - r * 0.4f, by + r * 0.5f, bx - r * 0.25f, by + r * 1.1f, line)
        c.drawLine(bx + r * 0.4f, by + r * 0.5f, bx + r * 0.25f, by + r * 1.1f, line)
        f(Color.parseColor("#C97B4A"))
        rect.set(bx - r * 0.3f, by + r * 1.1f, bx + r * 0.3f, by + r * 1.5f)
        c.drawRoundRect(rect, 3f * d, 3f * d, fill)
    }
}
