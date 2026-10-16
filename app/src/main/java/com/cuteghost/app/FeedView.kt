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
 * Feeding / watering layer drawn above the ghost. Everything is drawn on a fully transparent
 * background (no panel): a text line, then cartoon foods or a water cup that the child drags
 * to the ghost's mouth. It only consumes touches that start on an item, so taps still reach
 * the ghost.
 */
class FeedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { FOOD, WATER }

    interface Listener {
        fun onFeedingEnded(fed: Boolean)
    }

    var listener: Listener? = null
    var ghost: GhostView? = null

    /** Catch-zone scale around the mouth (babies are smaller but stay easy to feed). */
    var mouthZoneScale = 1f

    /** Colour of the water cup (a baby's cup has the baby's colour). */
    var cupColor: Int = Color.parseColor("#4FC3F7")

    private enum class Kind { APPLE, BANANA, COOKIE, CUP }

    private class Food(val kind: Kind) {
        var x = 0f
        var y = 0f
        var homeX = 0f
        var homeY = 0f
        var eaten = 0f   // eaten / drunk fraction 0..1
        var appear = 0f
        var alpha = 1f
        var gone = false
        var returning = false
        var tilt = 0f    // cup tilt in degrees
    }

    private val d = resources.displayMetrics.density
    private val foods = ArrayList<Food>()
    private var mode = Mode.FOOD
    private var active = false
    private var fed = false
    private var fedFood: Food? = null
    private var dragFood: Food? = null
    private var dragDx = 0f
    private var dragDy = 0f
    private var eatingNow = false
    private var cupDir = 1f
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
    // White outline keeps the text readable on any background without a box behind it
    private val textOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 5f * resources.displayMetrics.density
    }
    private val rect = RectF()
    private val path = Path()
    private val layerRect = RectF(-40f, -40f, 40f, 40f)

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

    fun begin(w: Int, h: Int, bubbleHeight: Int, trayHeight: Int, newMode: Mode) {
        winW = w.toFloat()
        winH = h.toFloat()
        bubbleH = bubbleHeight.toFloat()
        trayH = trayHeight.toFloat()
        mode = newMode

        foods.clear()
        val kinds = if (mode == Mode.FOOD) listOf(Kind.APPLE, Kind.BANANA, Kind.COOKIE) else listOf(Kind.CUP)
        for ((i, kind) in kinds.withIndex()) {
            val f = Food(kind)
            f.homeX = winW * (2 * i + 1) / (2f * kinds.size)
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
        bubbleText = context.getString(if (mode == Mode.FOOD) R.string.feed_ask else R.string.drink_ask)
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
    // Touch: only items are draggable; everything else passes through
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
                f.returning = true // moved away from the mouth: it goes back and nothing happens
                if (eatingNow) {
                    eatingNow = false
                    ghost?.setFeedState(waitingState())
                }
                invalidate()
                return true
            }
        }
        return false
    }

    private fun waitingState() =
        if (mode == Mode.FOOD) GhostView.FeedState.HUNGRY else GhostView.FeedState.THIRSTY

    private fun activeState() =
        if (mode == Mode.FOOD) GhostView.FeedState.EATING else GhostView.FeedState.DRINKING

    /** Generous touch target so small fingers can grab items easily. */
    private fun hit(x: Float, y: Float): Food? {
        for (i in foods.indices.reversed()) {
            val f = foods[i]
            if (f.gone || f.appear < 0.6f) continue
            if (hypot(f.x - x, f.y - y) <= 40f * d) return f
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
            // Start a little before touching the mouth, stop a bit further away (no flicker)
            val limit = (if (eatingNow) 42f else 32f) * d * mouthZoneScale
            near = hypot(drag.x - mx, drag.y - my) <= limit
            if (near) cupDir = if (drag.x < mx) 1f else -1f
        }
        if (near != eatingNow) {
            eatingNow = near
            g?.setFeedState(if (near) activeState() else waitingState())
        }

        // The cup tilts toward the mouth only while it is at the mouth, then returns upright
        for (f in foods) {
            if (f.kind == Kind.CUP) {
                val target = if (f === dragFood && eatingNow) cupDir * 38f else 0f
                f.tilt += (target - f.tilt) * (1f - exp(-10f * dt))
            }
        }

        // Eating / drinking only progresses while the item is held at the mouth
        if (eatingNow && drag != null) {
            drag.eaten += dt / (if (drag.kind == Kind.CUP) 2.6f else 2.4f)
            if (drag.eaten >= 1f) {
                drag.eaten = 1f
                fedFood = drag
                dragFood = null
                eatingNow = false
                fed = true
                finishTimer = 0f
                if (drag.kind == Kind.CUP) {
                    drag.returning = true // empty cup glides back upright
                    bubbleText = context.getString(R.string.drink_thanks)
                    g?.setFeedState(GhostView.FeedState.NONE)
                    g?.showRelieved()
                } else {
                    drag.gone = true
                    bubbleText = context.getString(R.string.feed_thanks)
                    g?.setFeedState(GhostView.FeedState.NONE)
                    g?.showFed()
                }
            }
        }

        if (finishTimer >= 0f) {
            finishTimer += dt
            val cup = fedFood
            if (cup != null && cup.kind == Kind.CUP) {
                cup.alpha = (1f - (finishTimer - 2.0f) / 0.6f).coerceIn(0f, 1f)
            }
            if (finishTimer >= 2.6f) {
                finishTimer = -1f
                active = false
                post { listener?.onFeedingEnded(true) }
            }
        }
    }

    // =====================================================================
    // Drawing (transparent background: no panel, no bubble box)
    // =====================================================================

    override fun onDraw(canvas: Canvas) {
        if (!active && foods.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastTime) / 1000f).coerceIn(0f, 0.05f)
        lastTime = now
        if (active) step(dt)

        drawText(canvas)
        for (f in foods) if (f !== dragFood) drawItem(canvas, f)
        dragFood?.let { drawItem(canvas, it) }

        if (active) postInvalidateOnAnimation()
    }

    private fun drawText(c: Canvas) {
        var size = 17f * d
        textPaint.textSize = size
        textOutlinePaint.textSize = size
        val maxW = winW - 16f * d
        while (textPaint.measureText(bubbleText) > maxW && size > 9f * d) {
            size -= 0.5f * d
            textPaint.textSize = size
            textOutlinePaint.textSize = size
        }
        val cx = winW / 2f
        val cy = bubbleH + 10f * d // just above the ghost's head
        val y = cy - (textPaint.ascent() + textPaint.descent()) / 2f
        c.drawText(bubbleText, cx, y, textOutlinePaint)
        c.drawText(bubbleText, cx, y, textPaint)
    }

    private fun drawItem(c: Canvas, f: Food) {
        if (f.gone || f.appear <= 0f) return
        val isCup = f.kind == Kind.CUP
        val bob = if (f !== dragFood && !f.returning && !fed) sin(clock * 3f + f.homeX * 0.01f) * 2f * d else 0f
        val lift = if (f === dragFood) 1.1f else 1f
        val shrink = if (isCup) 1f else 1f - 0.88f * f.eaten
        val sc = f.appear * shrink * lift * (60f * d / 56f)
        val a = f.alpha * (if (isCup) 1f else 1f - 0.4f * f.eaten)

        val saved = c.save()
        c.translate(f.x, f.y + bob)
        if (isCup) c.rotate(f.tilt)
        c.scale(sc, sc)
        if (a < 0.99f) c.saveLayerAlpha(layerRect, (a * 255f).toInt().coerceIn(0, 255))
        when (f.kind) {
            Kind.APPLE -> drawApple(c)
            Kind.BANANA -> drawBanana(c)
            Kind.COOKIE -> drawCookie(c)
            Kind.CUP -> drawCup(c, 1f - f.eaten)
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

    private fun mixC(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) * (1f - t) + Color.red(b) * t).toInt()
        val g = (Color.green(a) * (1f - t) + Color.green(b) * t).toInt()
        val bl = (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
        return Color.rgb(r, g, bl)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /** Cartoon glass of water; [level] 1 = full, 0 = empty. */
    private fun drawCup(c: Canvas, level: Float) {
        fillPaint.style = Paint.Style.FILL
        fillPaint.shader = null

        // Glass body
        path.reset()
        path.moveTo(-17f, -22f)
        path.lineTo(17f, -22f)
        path.lineTo(13f, 22f)
        path.quadTo(0f, 28f, -13f, 22f)
        path.close()
        fillPaint.color = withAlpha(mixC(cupColor, Color.WHITE, 0.8f), 0x66)
        c.drawPath(path, fillPaint)

        // Water
        if (level > 0.02f) {
            val ys = 22f - 36f * level
            val hw = 17f - 4f * ((ys + 22f) / 44f)
            path.reset()
            path.moveTo(-hw, ys)
            path.lineTo(hw, ys)
            path.lineTo(13f, 22f)
            path.quadTo(0f, 28f, -13f, 22f)
            path.close()
            fillPaint.color = withAlpha(cupColor, 0xE6)
            c.drawPath(path, fillPaint)
            rect.set(-hw, ys - 2.2f, hw, ys + 2.2f)
            fillPaint.color = mixC(cupColor, Color.WHITE, 0.45f)
            c.drawOval(rect, fillPaint)
        }

        // Outline and rim
        linePaint.style = Paint.Style.STROKE
        linePaint.color = mixC(cupColor, Color.BLACK, 0.35f)
        linePaint.strokeWidth = 2.4f
        path.reset()
        path.moveTo(-17f, -22f)
        path.lineTo(-13f, 22f)
        path.quadTo(0f, 28f, 13f, 22f)
        path.lineTo(17f, -22f)
        c.drawPath(path, linePaint)
        rect.set(-17f, -24.5f, 17f, -19.5f)
        linePaint.strokeWidth = 2f
        c.drawOval(rect, linePaint)

        // Shine
        rect.set(-13f, -16f, -9.5f, 10f)
        fillPaint.color = Color.parseColor("#99FFFFFF")
        c.drawRoundRect(rect, 2f, 2f, fillPaint)
    }
}
