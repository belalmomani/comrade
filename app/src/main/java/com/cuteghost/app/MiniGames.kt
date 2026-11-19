package com.cuteghost.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Small games for the little ones, with no words: popping bubbles, matching shapes, sorting colours. */
object MiniGames {

    /** Each finished round gives a star, up to this many stars a day. */
    const val DAILY_STAR_CAP = 6

    const val GAME_BUBBLES = 0
    const val GAME_SHAPES = 1
    const val GAME_COLORS = 2
    const val GAME_COUNTING = 3
    const val GAME_DOCTOR = 4

    private const val PREFS = "ghost_goals"
    private const val KEY_DAY = "mg_day"
    private const val KEY_COUNT = "mg_count"

    /** A round was finished. Returns true when it gave a star (the day's limit stops the stars, not the fun). */
    fun reward(context: Context): Boolean {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = DailyGoals.dayNumber()
        val n = if (p.getInt(KEY_DAY, -1) == today) p.getInt(KEY_COUNT, 0) else 0
        if (n >= DAILY_STAR_CAP) return false
        p.edit().putInt(KEY_DAY, today).putInt(KEY_COUNT, n + 1).apply()
        Wardrobe.addStars(context, 1)
        return true
    }
}

// ============================================================ drawing helpers

private val PALETTE = intArrayOf(
    Color.parseColor("#FF6B6B"), Color.parseColor("#4DA3FF"),
    Color.parseColor("#FFC93C"), Color.parseColor("#4CC38A")
)
private val helperPath = Path()
private val helperRect = RectF()

private fun withAlpha(color: Int, alpha: Int): Int =
    Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

private fun starPath(cx: Float, cy: Float, r: Float): Path {
    helperPath.reset()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val a = (-90f + i * 36f) * (PI.toFloat() / 180f)
        val x = cx + cos(a) * rad
        val y = cy + sin(a) * rad
        if (i == 0) helperPath.moveTo(x, y) else helperPath.lineTo(x, y)
    }
    helperPath.close()
    return helperPath
}

/** Shape 0 circle, 1 square, 2 triangle, 3 star. A hole is faint with an outline; otherwise it is solid. */
private fun drawShape(c: Canvas, p: Paint, type: Int, cx: Float, cy: Float, r: Float, color: Int, hole: Boolean, d: Float) {
    val steps = if (hole) 2 else 1
    for (pass in 0 until steps) {
        val stroke = hole && pass == 1
        if (stroke) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f * d
            p.strokeJoin = Paint.Join.ROUND
            p.color = color
        } else {
            p.style = Paint.Style.FILL
            p.color = if (hole) withAlpha(color, 70) else color
        }
        when (type) {
            0 -> c.drawCircle(cx, cy, r, p)
            1 -> {
                helperRect.set(cx - r * 0.88f, cy - r * 0.88f, cx + r * 0.88f, cy + r * 0.88f)
                c.drawRoundRect(helperRect, r * 0.18f, r * 0.18f, p)
            }
            2 -> {
                helperPath.reset()
                helperPath.moveTo(cx, cy - r)
                helperPath.lineTo(cx + r * 1.0f, cy + r * 0.8f)
                helperPath.lineTo(cx - r * 1.0f, cy + r * 0.8f)
                helperPath.close()
                c.drawPath(helperPath, p)
            }
            else -> c.drawPath(starPath(cx, cy, r * 1.05f), p)
        }
    }
    p.style = Paint.Style.FILL
    if (!hole) {
        p.color = Color.argb(110, 255, 255, 255)
        c.drawCircle(cx - r * 0.35f, cy - r * 0.35f, r * 0.14f, p)
    }
}

/** The ghost friend: it floats, and jumps for joy when [joy] is 1. */
private fun drawCuteGhost(
    c: Canvas, p: Paint, cx: Float, baseY: Float, r: Float, t: Float, joy: Float,
    body: Int = Color.parseColor("#F7F4FF"), sad: Boolean = false
) {
    val cy = baseY + sin(t * 2.2f) * r * 0.05f - joy * abs(sin(t * 7f)) * r * 0.5f
    val bottom = cy + r * 1.1f
    p.style = Paint.Style.FILL
    p.color = Color.argb(34, 0, 0, 0)
    helperRect.set(cx - r * 0.9f, baseY + r * 1.1f, cx + r * 0.9f, baseY + r * 1.35f)
    c.drawOval(helperRect, p)

    helperPath.reset()
    helperPath.moveTo(cx - r, cy)
    helperRect.set(cx - r, cy - r, cx + r, cy + r)
    helperPath.arcTo(helperRect, 180f, 180f)
    helperPath.lineTo(cx + r, bottom)
    for (i in 0 until 3) {
        val x1 = cx + r - (2f * i + 1f) * r / 3f
        val x2 = cx + r - (2f * i + 2f) * r / 3f
        val wave = if (i % 2 == 0) r * 0.28f else -r * 0.28f
        helperPath.quadTo(x1, bottom + wave, x2, bottom)
    }
    helperPath.close()
    p.color = body
    c.drawPath(helperPath, p)

    p.color = Color.parseColor("#2B2540")
    c.drawCircle(cx - r * 0.35f, cy - r * 0.05f, r * 0.1f, p)
    c.drawCircle(cx + r * 0.35f, cy - r * 0.05f, r * 0.1f, p)
    p.color = Color.parseColor("#FFB6D5")
    c.drawCircle(cx - r * 0.58f, cy + r * 0.28f, r * 0.12f, p)
    c.drawCircle(cx + r * 0.58f, cy + r * 0.28f, r * 0.12f, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = r * 0.07f
    p.strokeCap = Paint.Cap.ROUND
    p.color = Color.parseColor("#2B2540")
    if (sad) {
        helperRect.set(cx - r * 0.22f, cy + r * 0.3f, cx + r * 0.22f, cy + r * 0.6f)
        c.drawArc(helperRect, 200f, 140f, false, p)
    } else {
        helperRect.set(cx - r * 0.25f, cy + r * 0.05f, cx + r * 0.25f, cy + r * 0.45f)
        c.drawArc(helperRect, 20f, 140f, false, p)
    }
    p.style = Paint.Style.FILL
}

/** Gold stars circling around the ghost while a round is celebrated. */
private fun drawCelebration(c: Canvas, p: Paint, cx: Float, cy: Float, t: Float, d: Float) {
    for (k in 0 until 8) {
        val a = t * 2f + k * 0.785f
        val x = cx + cos(a) * 92f * d
        val y = cy + sin(a) * 58f * d
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#FFC93C")
        c.drawPath(starPath(x, y, (9f + 4f * sin(t * 6f + k)) * d), p)
    }
}

// ============================================================ game base

private abstract class GameView(context: Context) : View(context) {

    protected val d = resources.displayMetrics.density
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected var clock = 0f
    var onRound: (() -> Unit)? = null

    private var last = 0L
    private var running = false
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - last) / 1000f).coerceIn(0f, 0.05f)
            last = now
            clock += dt
            step(dt)
            invalidate()
            postOnAnimation(this)
        }
    }

    protected abstract fun step(dt: Float)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        last = SystemClock.uptimeMillis()
        postOnAnimation(ticker)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    protected fun paintBackground(canvas: Canvas, shader: Shader?) {
        paint.style = Paint.Style.FILL
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    protected fun sky(h: Int): Shader =
        LinearGradient(0f, 0f, 0f, h.toFloat(), Color.parseColor("#BDE6FF"), Color.parseColor("#F5F3FF"), Shader.TileMode.CLAMP)
}

// ============================================================ game 1: bubbles

private class BubblesView(context: Context) : GameView(context) {

    private class Bubble(var x: Float, var y: Float, val r: Float, val speed: Float, val color: Int, val phase: Float)
    private class Burst(val x: Float, val y: Float, val color: Int) {
        var t = 0f
    }

    private val bubbles = ArrayList<Bubble>()
    private val bursts = ArrayList<Burst>()
    private var spawnT = 0f
    private var popped = 0
    private var celebrate = 0f
    private val goal = 8
    private var bg: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg = sky(h)
    }

    private fun sway(b: Bubble): Float = b.x + sin(clock * 1.5f + b.phase) * 10f * d

    override fun step(dt: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (celebrate > 0f) {
            celebrate -= dt
            if (celebrate <= 0f) popped = 0
        } else {
            spawnT -= dt
            if (spawnT <= 0f && bubbles.size < 7) {
                spawnT = 0.8f
                val r = (34f + Random.nextFloat() * 14f) * d
                val color = PALETTE[Random.nextInt(PALETTE.size)]
                bubbles.add(
                    Bubble(
                        r + Random.nextFloat() * (w - 2f * r), h + r, r,
                        (40f + Random.nextFloat() * 30f) * d, color, Random.nextFloat() * 6.28f
                    )
                )
            }
        }
        for (b in bubbles) b.y -= b.speed * dt
        bubbles.removeAll { it.y < -it.r }
        for (x in bursts) x.t += dt
        bursts.removeAll { it.t > 0.5f }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val a = e.actionMasked
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex
            popAt(e.getX(i), e.getY(i))
        }
        return true
    }

    private fun popAt(x: Float, y: Float) {
        if (celebrate > 0f) return
        var best: Bubble? = null
        var bestDist = Float.MAX_VALUE
        for (b in bubbles) {
            val dx = sway(b) - x
            val dy = b.y - y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < b.r * 1.3f && dist < bestDist) {
                best = b
                bestDist = dist
            }
        }
        val hit = best ?: return
        bubbles.remove(hit)
        bursts.add(Burst(sway(hit), hit.y, hit.color))
        Sounds.play(context, Snd.POP)
        popped++
        if (popped >= goal) {
            for (o in bubbles) bursts.add(Burst(sway(o), o.y, o.color))
            bubbles.clear()
            celebrate = 2.2f
            onRound?.invoke()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paintBackground(canvas, bg)

        // how many are popped: a row of dots that fill up
        val gap = 26f * d
        val x0 = w / 2f - (goal - 1) * gap / 2f
        for (i in 0 until goal) {
            paint.style = Paint.Style.FILL
            paint.color = if (i < popped) Color.parseColor("#FFC93C") else Color.parseColor("#D8D2F0")
            canvas.drawCircle(x0 + i * gap, 70f * d, 9f * d, paint)
        }

        for (b in bubbles) {
            val x = sway(b)
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(b.color, 110)
            canvas.drawCircle(x, b.y, b.r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * d
            paint.color = withAlpha(b.color, 235)
            canvas.drawCircle(x, b.y, b.r, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(190, 255, 255, 255)
            canvas.drawCircle(x - b.r * 0.35f, b.y - b.r * 0.35f, b.r * 0.18f, paint)
        }
        for (u in bursts) {
            val k = u.t / 0.5f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * d
            paint.color = withAlpha(u.color, ((1f - k) * 255f).toInt())
            canvas.drawCircle(u.x, u.y, 20f * d + k * 50f * d, paint)
            paint.style = Paint.Style.FILL
            for (n in 0 until 6) {
                val a = n * 1.047f
                canvas.drawCircle(u.x + cos(a) * (24f + 46f * k) * d, u.y + sin(a) * (24f + 46f * k) * d, 4f * d, paint)
            }
        }
        val gy = h - 90f * d
        drawCuteGhost(canvas, paint, w / 2f, gy, 34f * d, clock, if (celebrate > 0f) 1f else 0f)
        if (celebrate > 0f) drawCelebration(canvas, paint, w / 2f, gy, clock, d)
    }
}

// ============================================================ games 2 and 3: drag and match

/**
 * Shapes: drag each shape into its matching hole. Colours: drag each ball into the bin of its colour.
 * Three kinds are in play each round, picked at random.
 */
private class DragMatchView(context: Context, private val colorsMode: Boolean) : GameView(context) {

    private class Item(val type: Int, var x: Float, var y: Float, val hx: Float, val hy: Float) {
        var placed = false
        var shake = 0f
    }

    private val items = ArrayList<Item>()
    private val tx = FloatArray(3)
    private val ty = FloatArray(3)
    private val ttype = IntArray(3)
    private val placedIn = IntArray(3)
    private var drag: Item? = null
    private var offX = 0f
    private var offY = 0f
    private var celebrate = 0f
    private var bg: Shader? = null
    private var r = 0f
    private var ir = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg = sky(h)
        if (w > 0 && h > 0) newRound()
    }

    private fun newRound() {
        val w = width.toFloat()
        val h = height.toFloat()
        r = min(w / 3f * 0.42f, 62f * d)
        ir = if (colorsMode) r * 0.5f else r * 0.8f
        val all = mutableListOf(0, 1, 2, 3)
        all.shuffle()
        for (i in 0 until 3) {
            ttype[i] = all[i]
            tx[i] = w * (2 * i + 1) / 6f
            ty[i] = h * 0.27f
            placedIn[i] = 0
        }
        val per = if (colorsMode) 2 else 1
        val list = ArrayList<Int>()
        for (i in 0 until 3) for (k in 0 until per) list.add(ttype[i])
        list.shuffle()
        val rows = if (colorsMode) 2 else 1
        val cols = list.size / rows
        items.clear()
        for ((n, type) in list.withIndex()) {
            val row = n / cols
            val col = n % cols
            val hx = w * (2 * col + 1) / (2f * cols)
            val hy = if (colorsMode) h * (0.56f + 0.15f * row) else h * 0.68f
            items.add(Item(type, hx, hy, hx, hy))
        }
        drag = null
        celebrate = 0f
    }

    override fun step(dt: Float) {
        if (celebrate > 0f) {
            celebrate -= dt
            if (celebrate <= 0f) newRound()
            return
        }
        for (item in items) {
            if (item.placed || item === drag) continue
            val k = min(1f, dt * 10f)
            item.x += (item.hx - item.x) * k
            item.y += (item.hy - item.y) * k
            if (item.shake > 0f) item.shake = max(0f, item.shake - dt)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (celebrate > 0f) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                for (i in items.indices.reversed()) {
                    val item = items[i]
                    if (item.placed) continue
                    val dx = item.x - e.x
                    val dy = item.y - e.y
                    if (sqrt(dx * dx + dy * dy) <= ir * 1.5f) {
                        drag = item
                        offX = dx
                        offY = dy
                        break
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val item = drag
                if (item != null) {
                    item.x = e.x + offX
                    item.y = e.y + offY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val item = drag
                drag = null
                if (item != null) drop(item)
            }
        }
        return true
    }

    private fun drop(item: Item) {
        for (i in 0 until 3) {
            val dx = item.x - tx[i]
            val dy = item.y - ty[i]
            if (sqrt(dx * dx + dy * dy) < r * 1.1f) {
                if (ttype[i] == item.type) {
                    item.placed = true
                    val idx = placedIn[i]
                    placedIn[i] = idx + 1
                    if (colorsMode) {
                        item.x = tx[i] + (idx - 0.5f) * ir * 0.9f
                        item.y = ty[i] - r * 0.3f
                    } else {
                        item.x = tx[i]
                        item.y = ty[i]
                    }
                    Sounds.play(context, Snd.POP)
                    var all = true
                    for (o in items) if (!o.placed) all = false
                    if (all) {
                        celebrate = 2.4f
                        onRound?.invoke()
                    }
                    return
                }
                item.shake = 0.4f
                break
            }
        }
    }

    private fun drawBin(c: Canvas, i: Int) {
        val color = PALETTE[ttype[i]]
        val x = tx[i]
        val y = ty[i]
        helperPath.reset()
        helperPath.moveTo(x - r * 0.9f, y - r * 0.55f)
        helperPath.lineTo(x + r * 0.9f, y - r * 0.55f)
        helperPath.lineTo(x + r * 0.65f, y + r * 0.85f)
        helperPath.lineTo(x - r * 0.65f, y + r * 0.85f)
        helperPath.close()
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawPath(helperPath, paint)
        paint.color = Color.argb(120, 255, 255, 255)
        helperRect.set(x - r * 0.9f, y - r * 0.68f, x + r * 0.9f, y - r * 0.42f)
        c.drawOval(helperRect, paint)
    }

    private fun drawItem(c: Canvas, item: Item, scale: Float) {
        val color = PALETTE[item.type]
        val x = item.x + sin(item.shake * 40f) * 6f * d * item.shake
        if (colorsMode) {
            paint.style = Paint.Style.FILL
            paint.color = color
            c.drawCircle(x, item.y, ir * scale, paint)
            paint.color = Color.argb(120, 255, 255, 255)
            c.drawCircle(x - ir * 0.35f * scale, item.y - ir * 0.35f * scale, ir * 0.18f * scale, paint)
        } else {
            drawShape(c, paint, item.type, x, item.y, ir * scale, color, false, d)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paintBackground(canvas, bg)

        for (i in 0 until 3) {
            if (colorsMode) {
                drawBin(canvas, i)
            } else {
                drawShape(canvas, paint, ttype[i], tx[i], ty[i], r * 0.85f, PALETTE[ttype[i]], placedIn[i] == 0, d)
            }
        }
        for ((n, item) in items.withIndex()) {
            if (item === drag) continue
            val pulse = if (item.placed || celebrate > 0f) 1f else 1f + 0.04f * sin(clock * 3f + n)
            drawItem(canvas, item, pulse)
        }
        val dragged = drag
        if (dragged != null) drawItem(canvas, dragged, 1.15f)

        val gy = h - 62f * d
        drawCuteGhost(canvas, paint, w / 2f, gy, 26f * d, clock, if (celebrate > 0f) 1f else 0f)
        if (celebrate > 0f) drawCelebration(canvas, paint, w / 2f, h * 0.5f, clock, d)
    }
}

// ============================================================ game 4: the counting book (library)

/**
 * A picture book with five pages: one thing on the first page, two on the next, up to five.
 * The child touches every thing to count it; when all are counted the page turns.
 */
private class CountingView(context: Context) : GameView(context) {

    private val pages = 5
    private var page = 0
    private val counted = BooleanArray(5)
    private var turn = 0f
    private var celebrate = 0f
    private var bg: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg = sky(h)
    }

    private fun count(): Int = page + 1

    private fun pageLeft(): Float = width * 0.08f
    private fun pageRight(): Float = width * 0.92f
    private fun pageTop(): Float = height * 0.16f
    private fun pageBottom(): Float = height * 0.70f

    private fun firstRow(k: Int): Int = if (k <= 3) k else k / 2

    private fun colsOf(i: Int, k: Int): Int {
        val r0 = firstRow(k)
        return if (i < r0) r0 else k - r0
    }

    private fun itemX(i: Int, k: Int): Float {
        val r0 = firstRow(k)
        val col = if (i < r0) i else i - r0
        val cols = colsOf(i, k)
        return pageLeft() + (pageRight() - pageLeft()) * (2 * col + 1) / (2f * cols)
    }

    private fun itemY(i: Int, k: Int): Float {
        val mid = (pageTop() + pageBottom()) / 2f
        if (k <= 3) return mid
        val span = pageBottom() - pageTop()
        return if (i < firstRow(k)) pageTop() + span * 0.32f else pageTop() + span * 0.72f
    }

    private fun itemRadius(k: Int): Float {
        val cols = max(firstRow(k), k - firstRow(k))
        return min((pageRight() - pageLeft()) / (cols * 2.5f), 40f * d)
    }

    override fun step(dt: Float) {
        if (turn > 0f) {
            turn -= dt
            if (turn <= 0f) {
                page++
                for (i in counted.indices) counted[i] = false
            }
        }
        if (celebrate > 0f) {
            celebrate -= dt
            if (celebrate <= 0f) {
                page = 0
                for (i in counted.indices) counted[i] = false
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN || turn > 0f || celebrate > 0f) return true
        val k = count()
        val rad = itemRadius(k)
        for (i in 0 until k) {
            val dx = itemX(i, k) - e.x
            val dy = itemY(i, k) - e.y
            if (!counted[i] && sqrt(dx * dx + dy * dy) < rad * 1.4f) {
                counted[i] = true
                Sounds.play(context, Snd.POP)
                var all = true
                for (n in 0 until k) if (!counted[n]) all = false
                if (all) {
                    if (page == pages - 1) {
                        celebrate = 2.4f
                        onRound?.invoke()
                    } else {
                        turn = 0.9f
                    }
                }
                break
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paintBackground(canvas, bg)
        val k = count().coerceAtMost(pages)

        // the page of the book
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(40, 0, 0, 0)
        helperRect.set(pageLeft() + 4f * d, pageTop() + 6f * d, pageRight() + 4f * d, pageBottom() + 8f * d)
        canvas.drawRoundRect(helperRect, 22f * d, 22f * d, paint)
        paint.color = Color.parseColor("#FFFDF7")
        helperRect.set(pageLeft(), pageTop(), pageRight(), pageBottom())
        canvas.drawRoundRect(helperRect, 22f * d, 22f * d, paint)

        // one dot for each thing: gold when it has been counted
        val gap = 26f * d
        val x0 = w / 2f - (k - 1) * gap / 2f
        for (i in 0 until k) {
            paint.color = if (counted[i]) Color.parseColor("#FFC93C") else Color.parseColor("#DDD6F3")
            canvas.drawCircle(x0 + i * gap, pageTop() + 30f * d, 9f * d, paint)
        }

        val rad = itemRadius(k)
        val type = page % 4
        for (i in 0 until k) {
            val bob = if (counted[i]) 0f else sin(clock * 2.4f + i) * 3f * d
            val pop = if (counted[i]) 1.15f else 1f
            drawShape(canvas, paint, type, itemX(i, k), itemY(i, k) + bob, rad * pop, PALETTE[(page + 1) % 4], false, d)
            if (counted[i]) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f * d
                paint.color = Color.parseColor("#FFC93C")
                canvas.drawCircle(itemX(i, k), itemY(i, k), rad * 1.4f, paint)
                paint.style = Paint.Style.FILL
            }
        }

        val gy = h - 92f * d
        val happy = celebrate > 0f || turn > 0f
        drawCuteGhost(canvas, paint, w / 2f, gy, 30f * d, clock, if (happy) 1f else 0f)
        if (celebrate > 0f) drawCelebration(canvas, paint, w / 2f, gy, clock, d)
    }
}

// ============================================================ game 5: the doctor (hospital)

/** A sick little ghost: the child gives it the thermometer, the medicine and a heart, in that order. */
private class DoctorView(context: Context) : GameView(context) {

    private var step = 0
    private var anim = 0f
    private var shake = 0f
    private var celebrate = 0f
    private var bg: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg = sky(h)
    }

    private fun toolX(i: Int): Float = width * (2 * i + 1) / 6f
    private fun toolY(): Float = height * 0.80f
    private fun toolR(): Float = min(width / 6f * 0.8f, 46f * d)

    override fun step(dt: Float) {
        if (shake > 0f) shake = max(0f, shake - dt)
        if (anim > 0f) {
            anim -= dt
            if (anim <= 0f) {
                step++
                if (step >= 3) {
                    celebrate = 2.4f
                    onRound?.invoke()
                }
            }
        }
        if (celebrate > 0f) {
            celebrate -= dt
            if (celebrate <= 0f) step = 0
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN || anim > 0f || celebrate > 0f) return true
        for (i in 0 until 3) {
            val dx = toolX(i) - e.x
            val dy = toolY() - e.y
            if (sqrt(dx * dx + dy * dy) < toolR() * 1.2f) {
                if (i == step) {
                    anim = 1.0f
                    Sounds.play(context, Snd.POP)
                } else {
                    shake = 0.4f
                }
                break
            }
        }
        return true
    }

    private fun drawTool(c: Canvas, kind: Int, cx: Float, cy: Float, s: Float) {
        when (kind) {
            0 -> { // a thermometer
                helperRect.set(cx - s * 0.09f, cy - s * 0.5f, cx + s * 0.09f, cy + s * 0.2f)
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#F1F1F7")
                c.drawRoundRect(helperRect, s * 0.09f, s * 0.09f, paint)
                paint.color = Color.parseColor("#E5484D")
                c.drawCircle(cx, cy + s * 0.32f, s * 0.2f, paint)
                helperRect.set(cx - s * 0.04f, cy - s * 0.05f, cx + s * 0.04f, cy + s * 0.3f)
                c.drawRect(helperRect, paint)
            }
            1 -> { // a medicine bottle
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#4DA3FF")
                helperRect.set(cx - s * 0.25f, cy - s * 0.2f, cx + s * 0.25f, cy + s * 0.5f)
                c.drawRoundRect(helperRect, s * 0.08f, s * 0.08f, paint)
                paint.color = Color.parseColor("#B8C2CC")
                helperRect.set(cx - s * 0.14f, cy - s * 0.46f, cx + s * 0.14f, cy - s * 0.2f)
                c.drawRoundRect(helperRect, s * 0.05f, s * 0.05f, paint)
                paint.color = Color.WHITE
                helperRect.set(cx - s * 0.17f, cy + s * 0.02f, cx + s * 0.17f, cy + s * 0.3f)
                c.drawRoundRect(helperRect, s * 0.04f, s * 0.04f, paint)
            }
            else -> { // a heart
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#FF5C8A")
                c.drawCircle(cx - s * 0.2f, cy - s * 0.1f, s * 0.26f, paint)
                c.drawCircle(cx + s * 0.2f, cy - s * 0.1f, s * 0.26f, paint)
                helperPath.reset()
                helperPath.moveTo(cx - s * 0.44f, cy - s * 0.02f)
                helperPath.lineTo(cx + s * 0.44f, cy - s * 0.02f)
                helperPath.lineTo(cx, cy + s * 0.5f)
                helperPath.close()
                c.drawPath(helperPath, paint)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paintBackground(canvas, bg)

        // three dots: how many cares are done
        val gap = 26f * d
        for (i in 0 until 3) {
            paint.style = Paint.Style.FILL
            paint.color = if (i < step || celebrate > 0f) Color.parseColor("#FFC93C") else Color.parseColor("#DDD6F3")
            canvas.drawCircle(w / 2f + (i - 1) * gap, 70f * d, 9f * d, paint)
        }

        // the ghost: pale green and sad while it is sick, white and smiling once it is cured
        val cured = celebrate > 0f
        val gx = w / 2f
        val gy = h * 0.34f
        drawCuteGhost(
            canvas, paint, gx, gy, 66f * d, clock, if (cured) 1f else 0f,
            if (cured) Color.parseColor("#F7F4FF") else Color.parseColor("#D3EBCB"), !cured
        )
        if (cured) drawCelebration(canvas, paint, gx, gy, clock, d)

        // the tool that is being used right now, next to the ghost
        if (anim > 0f) {
            val k = 1f - anim
            drawTool(canvas, step, gx + 78f * d + sin(clock * 12f) * 3f * d, gy - 10f * d - k * 14f * d, 56f * d)
        }

        // the three tools: the one that comes next breathes gently
        for (i in 0 until 3) {
            val x = toolX(i) + (if (shake > 0f && i != step) sin(shake * 60f) * 6f * d else 0f)
            val pulse = if (i == step && celebrate <= 0f) 1f + 0.07f * sin(clock * 4f) else 1f
            val r = toolR() * pulse
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(34, 0, 0, 0)
            canvas.drawCircle(x + 3f * d, toolY() + 5f * d, r, paint)
            paint.color = if (i < step) Color.parseColor("#D9F5E3") else Color.WHITE
            canvas.drawCircle(x, toolY(), r, paint)
            if (i == step && celebrate <= 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f * d
                paint.color = Color.parseColor("#FFC93C")
                canvas.drawCircle(x, toolY(), r, paint)
            }
            drawTool(canvas, i, x, toolY(), r * 1.1f)
        }
    }
}

// ============================================================ screens

private class GameTileView(context: Context, private val game: Int) : View(context) {

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var clock = 0f
    private var running = false
    private var last = 0L
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            clock += ((now - last) / 1000f).coerceIn(0f, 0.05f)
            last = now
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        last = SystemClock.uptimeMillis()
        postOnAnimation(ticker)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = min(w, h)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(34, 0, 0, 0)
        rect.set(4f * d, 8f * d, w - 4f * d, h)
        canvas.drawRoundRect(rect, 28f * d, 28f * d, paint)
        paint.color = Color.WHITE
        rect.set(0f, 0f, w - 4f * d, h - 6f * d)
        canvas.drawRoundRect(rect, 28f * d, 28f * d, paint)

        val cx = (w - 4f * d) / 2f
        val cy = (h - 6f * d) / 2f
        val bob = sin(clock * 2.5f + game) * s * 0.02f
        when (game) {
            MiniGames.GAME_BUBBLES -> {
                val spots = arrayOf(
                    floatArrayOf(-0.22f, 0.12f, 0.2f), floatArrayOf(0.2f, -0.12f, 0.15f), floatArrayOf(0.22f, 0.24f, 0.1f)
                )
                for ((i, sp) in spots.withIndex()) {
                    val color = PALETTE[i]
                    val x = cx + sp[0] * s
                    val y = cy + sp[1] * s + bob * (i + 1)
                    paint.style = Paint.Style.FILL
                    paint.color = withAlpha(color, 110)
                    canvas.drawCircle(x, y, sp[2] * s, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f * d
                    paint.color = withAlpha(color, 235)
                    canvas.drawCircle(x, y, sp[2] * s, paint)
                }
            }
            MiniGames.GAME_SHAPES -> {
                drawShape(canvas, paint, 0, cx - 0.26f * s, cy + bob, 0.15f * s, PALETTE[0], false, d)
                drawShape(canvas, paint, 2, cx + 0.04f * s, cy - bob, 0.16f * s, PALETTE[2], false, d)
                drawShape(canvas, paint, 3, cx + 0.28f * s, cy + bob, 0.15f * s, PALETTE[3], false, d)
            }
            else -> {
                for (i in 0 until 3) {
                    val x = cx + (i - 1) * 0.3f * s
                    paint.style = Paint.Style.FILL
                    paint.color = PALETTE[i]
                    helperPath.reset()
                    helperPath.moveTo(x - 0.11f * s, cy + 0.02f * s)
                    helperPath.lineTo(x + 0.11f * s, cy + 0.02f * s)
                    helperPath.lineTo(x + 0.08f * s, cy + 0.24f * s)
                    helperPath.lineTo(x - 0.08f * s, cy + 0.24f * s)
                    helperPath.close()
                    canvas.drawPath(helperPath, paint)
                    canvas.drawCircle(x, cy - 0.1f * s + bob * (i + 1), 0.07f * s, paint)
                }
            }
        }
    }
}

private fun gameBackground(): GradientDrawable =
    GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor("#BDE6FF"), Color.parseColor("#F5F3FF"))
    )

/** The three small games as three big picture cards. */
class MiniGamesActivity : Activity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.background = gameBackground()

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER
        val dm = resources.displayMetrics
        val side = max(dp(90), min((dm.heightPixels - dp(200)) / 3, dp(150)))
        for (game in 0 until 3) {
            val tile = GameTileView(this, game)
            tile.setOnClickListener {
                startActivity(Intent(this, MiniGameActivity::class.java).putExtra(MiniGameActivity.EXTRA_GAME, game))
            }
            val lp = LinearLayout.LayoutParams(min(dm.widthPixels - dp(48), dp(280)), side)
            lp.setMargins(0, dp(8), 0, dp(8))
            column.addView(tile, lp)
        }
        root.addView(
            column,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val back = IconButtonView(this, IconButtonView.BACK, Color.parseColor("#9A8FC0"))
        back.setOnClickListener { finish() }
        val blp = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.START)
        blp.setMargins(dp(12), dp(36), dp(12), dp(12))
        root.addView(back, blp)
        setContentView(root)
    }
}

/** One of the small games, full screen. */
class MiniGameActivity : Activity() {

    companion object {
        const val EXTRA_GAME = "game"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val game = intent.getIntExtra(EXTRA_GAME, MiniGames.GAME_BUBBLES)
        val view: GameView = when (game) {
            MiniGames.GAME_SHAPES -> DragMatchView(this, false)
            MiniGames.GAME_COLORS -> DragMatchView(this, true)
            MiniGames.GAME_COUNTING -> CountingView(this)
            MiniGames.GAME_DOCTOR -> DoctorView(this)
            else -> BubblesView(this)
        }
        view.onRound = { MiniGames.reward(this) }

        val root = FrameLayout(this)
        root.addView(
            view,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        val back = IconButtonView(this, IconButtonView.BACK, Color.parseColor("#9A8FC0"))
        back.setOnClickListener { finish() }
        val blp = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.START)
        blp.setMargins(dp(12), dp(36), dp(12), dp(12))
        root.addView(back, blp)
        setContentView(root)
    }
}
