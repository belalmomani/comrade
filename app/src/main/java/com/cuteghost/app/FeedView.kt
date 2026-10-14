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
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Feeding game layer drawn above the ghost: speech bubble, a small tray of cartoon foods,
 * dragging, and the "eating" logic. It only consumes touches that start on a food item,
 * so taps on the ghost still reach the ghost.
 */
class FeedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onFeedingEnded(fed: Boolean)
    }

    var listener: Listener? = null
    var ghost: GhostView? = null

    private enum class Kind { APPLE, BANANA, COOKIE }

    private class Food(val kind: Kind) {
        var x = 0f
        var y = 0f
        var homeX = 0f
        var homeY = 0f
        var eaten = 0f
        var appear = 0f
        var alpha = 1f
        var gone = false
        var returning = false
    }

    private val d = resources.displayMetrics.density
    private val foods = ArrayList<Food>()
    private var active = false
    private var fed = false
    private var fedFood: Food? = null
    private var dragFood: Food? = null
    private var dragDx = 0f
    private var dragDy = 0f
    private var eatingNow = false
    private var bubbleText = ""
    private var finishTimer = -1f
    private var lastTime = 0L
    private var clock = 0f

    private var winW = 0f
    private var winH = 0f
    private var bubbleH = 0f
    private var trayH = 0f
    private val mouth = FloatArray(2)

    // ----- Paints -----
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2540")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val layerRect = RectF(-34f, -34f, 34f, 34f)

    private val appleShader = RadialGradient(
        -7f, -2f, 32f,
        intArrayOf(Color.parseColor("#FF7B7B"), Color.parseColor("#E63946"), Color.parseColor("#B3212E")),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    private val cookieShader = RadialGradient(
        -6f, -6f, 30f,
        intArrayOf(Color.parseColor("#F5CB94"), Color.parseColor("#DDA25F"), Color.parseColor("#C58546")),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    private val chips = floatArrayOf(-8f, -8f, 7f, -10f, 10f, 4f, -10f, 6f, 0f, -1f, 3f, 12f)

    // =====================================================================
    // Session control (called by the service)
    // =====================================================================

    fun begin(w: Int, h: Int, bubbleHeight: Int, trayHeight: Int) {
        winW = w.toFloat()
        winH = h.toFloat()
        bubbleH = bubbleHeight.toFloat()
        trayH = trayHeight.toFloat()

        foods.clear()
        for ((i, kind) in Kind.values().withIndex()) {
            val f = Food(kind)
            f.homeX = winW * (2 * i + 1) / 6f
            f.homeY = winH - trayH / 2f
            f.x = f.homeX
            f.y = f.homeY
            f.appear = -0.15f * i // staggered pop-in
            foods.add(f)
        }
        active = true
        fed = false
        fedFood = null
        dragFood = null
        eatingNow = false
        finishTimer = -1f
        bubbleText = context.getString(R.string.feed_ask)
        lastTime = SystemClock.uptimeMillis()
        invalidate()
    }

    fun end() {
        active = false
        foods.clear()
        dragFood = null
        fedFood = null
        eatingNow = false
        finishTimer = -1f
        invalidate()
    }

    // =====================================================================
    // Touch: only foods are draggable; everything else passes through
    // =====================================================================

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!active || fed) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val f = hit(e.x, e.y) ?: return false
                dragFood = f
                f.returning = false
                dragDx = f.x - e.x
                dragDy = f.y - e.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val f = dragFood ?: return false
                f.x = (e.x + dragDx).coerceIn(0f, winW)
                f.y = (e.y + dragDy).coerceIn(0f, winH)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val f = dragFood ?: return false
                dragFood = null
                f.returning = true // moved away from the mouth: it goes back and is not eaten
                if (eatingNow) {
                    eatingNow = false
                    ghost?.setFeedState(GhostView.FeedState.HUNGRY)
                }
                invalidate()
                return true
            }
        }
        return false
    }

    private fun hit(x: Float, y: Float): Food? {
        for (i in foods.indices.reversed()) {
            val f = foods[i]
            if (f.gone || f.appear < 0.6f) continue
            if (hypot(f.x - x, f.y - y) <= 32f * d) return f
        }
        return null
    }

    // =====================================================================
    // Simulation
    // =====================================================================

    private fun step(dt: Float) {
        clock += dt

        for (f in foods) {
            if (f.appear < 1f) f.appear = min(1f, f.appear + dt / 0.35f)
            if (f.returning && f !== dragFood) {
                val k = 1f - exp(-14f * dt)
                f.x += (f.homeX - f.x) * k
                f.y += (f.homeY - f.y) * k
                if (abs(f.homeX - f.x) < 0.5f && abs(f.homeY - f.y) < 0.5f) {
                    f.x = f.homeX
                    f.y = f.homeY
                    f.returning = false
                }
            }
            if (fed && f !== fedFood) f.alpha = max(0f, f.alpha - dt / 0.6f)
        }

        val g = ghost
        val drag = dragFood
        var near = false
        if (drag != null && g != null && !fed) {
            g.mouthPoint(mouth)
            val mx = g.left + mouth[0]
            val my = g.top + mouth[1]
            val limit = (if (eatingNow) 36f else 28f) * d
            near = hypot(drag.x - mx, drag.y - my) <= limit
        }
        if (near != eatingNow) {
            eatingNow = near
            g?.setFeedState(if (near) GhostView.FeedState.EATING else GhostView.FeedState.HUNGRY)
        }

        // Eating only progresses while the food is held at the mouth
        if (eatingNow && drag != null) {
            drag.eaten += dt / 2.4f
            if (drag.eaten >= 1f) {
                drag.eaten = 1f
                drag.gone = true
                fedFood = drag
                dragFood = null
                eatingNow = false
                fed = true
                finishTimer = 0f
                bubbleText = context.getString(R.string.feed_thanks)
                g?.setFeedState(GhostView.FeedState.NONE)
                g?.showFed()
            }
        }

        if (finishTimer >= 0f) {
            finishTimer += dt
            if (finishTimer >= 2.6f) {
                finishTimer = -1f
                active = false
                post { listener?.onFeedingEnded(true) }
            }
        }
    }

    // =====================================================================
    // Drawing
    // =====================================================================

    override fun onDraw(canvas: Canvas) {
        if (!active && foods.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastTime) / 1000f).coerceIn(0f, 0.05f)
        lastTime = now
        if (active) step(dt)

        drawBubble(canvas)
        drawTray(canvas)
        for (f in foods) if (f !== dragFood) drawFoodItem(canvas, f)
        dragFood?.let { drawFoodItem(canvas, it) }

        if (active) postInvalidateOnAnimation()
    }

    private fun drawBubble(c: Canvas) {
        val cx = winW / 2f
        val bw = min(winW - 8f * d, 220f * d)
        val left = cx - bw / 2f
        rect.set(left, 4f * d, left + bw, bubbleH - 4f * d)

        fillPaint.shader = null
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.parseColor("#F2FFFFFF")
        c.drawRoundRect(rect, 14f * d, 14f * d, fillPaint)
        linePaint.color = Color.parseColor("#D8D2F0")
        linePaint.strokeWidth = 1.5f * d
        c.drawRoundRect(rect, 14f * d, 14f * d, linePaint)

        // Tail pointing at the ghost's head
        val g = ghost
        val gx = (g?.let { it.left + it.width / 2f } ?: cx).coerceIn(left + 18f * d, left + bw - 18f * d)
        path.reset()
        path.moveTo(gx - 7f * d, rect.bottom - 1f)
        path.lineTo(gx + 7f * d, rect.bottom - 1f)
        path.lineTo(gx, rect.bottom + 9f * d)
        path.close()
        c.drawPath(path, fillPaint)

        textPaint.textSize = 15f * d
        while (textPaint.measureText(bubbleText) > bw - 20f * d && textPaint.textSize > 9f * d) {
            textPaint.textSize = textPaint.textSize - 0.5f * d
        }
        val y = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        c.drawText(bubbleText, cx, y, textPaint)
    }

    private fun drawTray(c: Canvas) {
        val fade = if (finishTimer >= 0f) max(0f, 1f - finishTimer / 0.6f) else 1f
        if (fade <= 0f) return
        rect.set(6f * d, winH - trayH + 4f * d, winW - 6f * d, winH - 4f * d)
        panelPaint.style = Paint.Style.FILL
        panelPaint.color = Color.parseColor("#B3FFFFFF")
        panelPaint.alpha = (179f * fade).toInt()
        c.drawRoundRect(rect, 18f * d, 18f * d, panelPaint)
        panelPaint.style = Paint.Style.STROKE
        panelPaint.strokeWidth = 1.5f * d
        panelPaint.color = Color.parseColor("#D8D2F0")
        panelPaint.alpha = (255f * fade).toInt()
        c.drawRoundRect(rect, 18f * d, 18f * d, panelPaint)
    }

    private fun drawFoodItem(c: Canvas, f: Food) {
        if (f.gone || f.appear <= 0f) return
        val bob = if (f !== dragFood && !f.returning && !fed) sin(clock * 3f + f.homeX * 0.01f) * 2f * d else 0f
        val e = f.eaten
        val lift = if (f === dragFood) 1.12f else 1f
        val sc = f.appear * (1f - 0.88f * e) * lift * (54f * d / 56f)
        val a = f.alpha * (1f - 0.4f * e)

        val saved = c.save()
        c.translate(f.x, f.y + bob)
        c.scale(sc, sc)
        if (a < 0.99f) c.saveLayerAlpha(layerRect, (a * 255f).toInt().coerceIn(0, 255))
        when (f.kind) {
            Kind.APPLE -> drawApple(c)
            Kind.BANANA -> drawBanana(c)
            Kind.COOKIE -> drawCookie(c)
        }
        c.restoreToCount(saved)
    }

    private fun drawApple(c: Canvas) {
        fillPaint.style = Paint.Style.FILL
        fillPaint.shader = appleShader
        c.drawCircle(0f, 4f, 21f, fillPaint)
        fillPaint.shader = null

        linePaint.style = Paint.Style.STROKE
        linePaint.color = Color.parseColor("#6D4C41")
        linePaint.strokeWidth = 2.6f
        c.drawLine(0f, -14f, 2f, -23f, linePaint)

        val s = c.save()
        c.rotate(-30f, 4f, -20f)
        rect.set(4f, -27f, 17f, -19f)
        fillPaint.color = Color.parseColor("#4CAF50")
        c.drawOval(rect, fillPaint)
        c.restoreToCount(s)

        rect.set(-15f, -6f, -8f, 5f)
        fillPaint.color = Color.parseColor("#78FFFFFF")
        c.drawOval(rect, fillPaint)
    }

    private fun drawBanana(c: Canvas) {
        val s = c.save()
        c.translate(0f, -6f)
        rect.set(-26f, -30f, 26f, 20f)
        linePaint.style = Paint.Style.STROKE
        linePaint.color = Color.parseColor("#C99A00")
        linePaint.strokeWidth = 17f
        c.drawArc(rect, 25f, 130f, false, linePaint)
        linePaint.color = Color.parseColor("#FFE066")
        linePaint.strokeWidth = 12.5f
        c.drawArc(rect, 25f, 130f, false, linePaint)
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.parseColor("#6D4C41")
        c.drawCircle(23.6f, 5.6f, 2.4f, fillPaint)
        c.drawCircle(-23.6f, 5.6f, 2.4f, fillPaint)
        c.restoreToCount(s)
    }

    private fun drawCookie(c: Canvas) {
        fillPaint.style = Paint.Style.FILL
        fillPaint.shader = cookieShader
        c.drawCircle(0f, 0f, 21f, fillPaint)
        fillPaint.shader = null

        linePaint.style = Paint.Style.STROKE
        linePaint.color = Color.parseColor("#B97A3E")
        linePaint.strokeWidth = 2.2f
        c.drawCircle(0f, 0f, 20f, linePaint)

        fillPaint.color = Color.parseColor("#5B3A22")
        var i = 0
        while (i < chips.size) {
            val x = chips[i]
            val y = chips[i + 1]
            rect.set(x - 3.2f, y - 2.4f, x + 3.2f, y + 2.4f)
            c.drawOval(rect, fillPaint)
            i += 2
        }
    }
}
