package com.cuteghost.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * Cute 3D-style ghost drawn with Canvas on a transparent background.
 * Drawing happens in a 100x100 logical box that is scaled to the view size.
 */
class GhostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private enum class Expression { HAPPY, SURPRISED, WINK }

    private var expression = Expression.HAPPY
    private var tapCount = 0
    private var phase = 0f
    private var squash = 0f

    private val resetExpression = Runnable {
        expression = Expression.HAPPY
        invalidate()
    }

    private val floatAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = (it.animatedValue as Float) * 2f * PI.toFloat()
            invalidate()
        }
    }

    private val bounceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 550L
        addUpdateListener {
            val t = it.animatedValue as Float
            squash = sin(t * 3f * PI.toFloat()) * (1f - t) * 0.14f
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                squash = 0f
                invalidate()
            }
        })
    }

    // ----- Shapes and paints (logical 100x100 coordinates) -----

    private val body = Path().apply {
        moveTo(12f, 86f)
        cubicTo(7f, 58f, 12f, 12f, 50f, 10f)
        cubicTo(88f, 12f, 93f, 58f, 88f, 86f)
        var x0 = 88f
        repeat(4) {
            val x1 = x0 - 19f
            quadTo((x0 + x1) / 2f, 99f, x1, 86f)
            x0 = x1
        }
        close()
    }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            36f, 30f, 80f,
            intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#F3F4FA"),
                Color.parseColor("#D4D8E8")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 55f, 0f, 95f,
            Color.parseColor("#00A9AEC8"),
            Color.parseColor("#66A9AEC8"),
            Shader.TileMode.CLAMP
        )
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            30f, 28f, 15f,
            Color.parseColor("#E6FFFFFF"),
            Color.parseColor("#00FFFFFF"),
            Shader.TileMode.CLAMP
        )
    }

    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#17171F")
    }

    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FF8FAB")
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#17171F")
        style = Paint.Style.STROKE
        strokeWidth = 2.8f
        strokeCap = Paint.Cap.ROUND
    }

    private val rect = RectF()
    private val tmpPath = Path()

    // ----- Public API -----

    /** Called when the user taps the ghost: bounce + temporary expression change. */
    fun react() {
        tapCount++
        expression = if (tapCount % 2 == 1) Expression.SURPRISED else Expression.WINK
        bounceAnimator.cancel()
        bounceAnimator.start()
        removeCallbacks(resetExpression)
        postDelayed(resetExpression, 1600L)
        invalidate()
    }

    // ----- Lifecycle -----

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!floatAnimator.isStarted) floatAnimator.start()
    }

    override fun onDetachedFromWindow() {
        floatAnimator.cancel()
        bounceAnimator.cancel()
        removeCallbacks(resetExpression)
        super.onDetachedFromWindow()
    }

    // ----- Drawing -----

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) / 100f
        canvas.save()
        canvas.translate((width - 100f * s) / 2f, (height - 100f * s) / 2f)
        canvas.scale(s, s)
        canvas.translate(0f, sin(phase) * 3.2f)
        canvas.rotate(sin(phase) * 1.8f, 50f, 90f)
        canvas.scale(1f + squash, 1f - squash, 50f, 92f)
        drawGhost(canvas)
        canvas.restore()
    }

    private fun drawGhost(c: Canvas) {
        c.drawPath(body, bodyPaint)
        c.drawPath(body, shadePaint)
        c.drawCircle(30f, 28f, 15f, highlightPaint)

        // Blush
        rect.set(17f, 56f, 31f, 64f)
        c.drawOval(rect, blushPaint)
        rect.set(69f, 56f, 83f, 64f)
        c.drawOval(rect, blushPaint)

        // Eyes
        when (expression) {
            Expression.HAPPY -> {
                drawEye(c, 36f, 50f, 7.5f, 9f)
                drawEye(c, 64f, 50f, 7.5f, 9f)
            }
            Expression.SURPRISED -> {
                drawEye(c, 36f, 50f, 8.5f, 11f)
                drawEye(c, 64f, 50f, 8.5f, 11f)
            }
            Expression.WINK -> {
                drawEye(c, 36f, 50f, 7.5f, 9f)
                tmpPath.reset()
                tmpPath.moveTo(57f, 52f)
                tmpPath.quadTo(64f, 44f, 71f, 52f)
                c.drawPath(tmpPath, strokePaint)
            }
        }

        // Mouth
        if (expression == Expression.SURPRISED) {
            rect.set(46.5f, 63.5f, 53.5f, 73f)
            c.drawOval(rect, darkPaint)
        } else {
            tmpPath.reset()
            tmpPath.moveTo(43f, 63.5f)
            tmpPath.quadTo(50f, 66f, 57f, 63.5f)
            tmpPath.quadTo(50f, 74f, 43f, 63.5f)
            tmpPath.close()
            c.drawPath(tmpPath, darkPaint)
        }
    }

    private fun drawEye(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(rect, darkPaint)
        c.drawCircle(cx + rx * 0.35f, cy - ry * 0.4f, rx * 0.38f, glintPaint)
        c.drawCircle(cx - rx * 0.4f, cy + ry * 0.4f, rx * 0.16f, glintPaint)
    }
}
