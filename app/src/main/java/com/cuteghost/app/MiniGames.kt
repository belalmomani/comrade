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
    const val GAME_CLASS = 5
    const val GAME_GHOST_BUBBLES = 6
    const val GAME_BUTTERFLY = 7
    const val GAME_BALL = 8
    const val GAME_WHICH = 9
    const val GAME_TWINS = 10

    private const val PREFS = "ghost_goals"
    private const val KEY_DAY = "mg_day"
    private const val KEY_COUNT = "mg_count"
    private const val KEY_SHAPES_STAGE = "shapes_stage"

    /** The shape game has six stages, from one big shape up to four. */
    const val STAGES = 6

    /** The stage the child reached in the shape game (kept between visits). */
    fun shapesStage(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SHAPES_STAGE, 0).coerceIn(0, STAGES - 1)

    /** The colour game has five stages (one bin and one ball, up to three bins with two balls each). */
    const val COLOR_STAGES = 5

    /** The bubble game has three stages: a few big slow bubbles, then more and smaller. */
    const val BUBBLE_STAGES = 3

    fun colorsStage(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("colors_stage", 0).coerceIn(0, COLOR_STAGES - 1)

    fun setColorsStage(context: Context, stage: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("colors_stage", stage.coerceIn(0, COLOR_STAGES - 1)).apply()
    }

    fun bubblesStage(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("bubbles_stage", 0).coerceIn(0, BUBBLE_STAGES - 1)

    fun setBubblesStage(context: Context, stage: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("bubbles_stage", stage.coerceIn(0, BUBBLE_STAGES - 1)).apply()
    }

    /** The butterfly game has two stages (two butterflies, then three); the twins game has four. */
    const val BUTTERFLY_STAGES = 2
    const val TWINS_STAGES = 4

    private fun stageOf(context: Context, key: String, max: Int): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key, 0).coerceIn(0, max - 1)

    private fun setStageOf(context: Context, key: String, value: Int, max: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(key, value.coerceIn(0, max - 1)).apply()
    }

    fun butterflyStage(context: Context): Int = stageOf(context, "butterfly_stage", BUTTERFLY_STAGES)
    fun setButterflyStage(context: Context, stage: Int) = setStageOf(context, "butterfly_stage", stage, BUTTERFLY_STAGES)
    fun twinsStage(context: Context): Int = stageOf(context, "twins_stage", TWINS_STAGES)
    fun setTwinsStage(context: Context, stage: Int) = setStageOf(context, "twins_stage", stage, TWINS_STAGES)

    /** At most this many golden-bubble stars a day. */
    const val DAILY_GOLD_CAP = 5

    /** A golden bubble was popped: one star, which counts like any star for the village and wardrobe purchases. */
    fun goldenStar(context: Context): Boolean {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = DailyGoals.dayNumber()
        val n = if (p.getInt("gold_day", -1) == today) p.getInt("gold_count", 0) else 0
        if (n >= DAILY_GOLD_CAP) return false
        p.edit().putInt("gold_day", today).putInt("gold_count", n + 1).apply()
        Wardrobe.addStars(context, 1)
        return true
    }

    /** The classroom has three stages: two pupils, then three, then four. */
    const val CLASS_STAGES = 3

    fun classStage(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("class_stage", 0).coerceIn(0, CLASS_STAGES - 1)

    fun setClassStage(context: Context, stage: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("class_stage", stage.coerceIn(0, CLASS_STAGES - 1)).apply()
    }

    fun setShapesStage(context: Context, stage: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SHAPES_STAGE, stage.coerceIn(0, STAGES - 1)).apply()
    }

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
/** How many shapes each stage of the shape game has; the last two stages have grey holes (no colour hint). */
private val STAGE_COUNTS = intArrayOf(1, 2, 3, 4, 3, 4)
private val HOLE_GREY = Color.parseColor("#9A93B8")

/** The colour game: how many bins, and how many balls for each bin, at every stage. */
private val COLOR_BINS = intArrayOf(1, 2, 3, 2, 3)
private val COLOR_PER = intArrayOf(1, 1, 1, 2, 2)

/** The bubble game: bubbles to pop, bubbles on screen at once, their size and their speed, at every stage. */
private val BUBBLE_GOALS = intArrayOf(4, 6, 8)
private val BUBBLE_MAX = intArrayOf(4, 5, 7)
private val BUBBLE_SIZE = floatArrayOf(1.3f, 1.15f, 1f)
private val BUBBLE_SPEED = floatArrayOf(0.75f, 0.9f, 1f)
/** The hospital's patients: the colour of the sick ghost and the order of the three cares (0 thermometer, 1 medicine, 2 heart). */
private val PATIENT_COLORS = intArrayOf(Color.parseColor("#D3EBCB"), Color.parseColor("#CFE3F7"), Color.parseColor("#F7D9D9"))
private val PATIENT_ORDERS = arrayOf(intArrayOf(0, 1, 2), intArrayOf(1, 0, 2), intArrayOf(0, 2, 1))
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

/** The things in the counting book: 0 apple, 1 duck, 2 flower, 3 balloon. A different one each day. */
private fun drawBookItem(c: Canvas, p: Paint, kind: Int, cx: Float, cy: Float, r: Float) {
    p.style = Paint.Style.FILL
    when (kind) {
        0 -> {
            p.color = Color.parseColor("#E5484D")
            c.drawCircle(cx - r * 0.25f, cy + r * 0.1f, r * 0.7f, p)
            c.drawCircle(cx + r * 0.25f, cy + r * 0.1f, r * 0.7f, p)
            p.color = Color.parseColor("#7A4B2A")
            helperRect.set(cx - r * 0.06f, cy - r * 0.95f, cx + r * 0.06f, cy - r * 0.45f)
            c.drawRoundRect(helperRect, r * 0.06f, r * 0.06f, p)
            p.color = Color.parseColor("#4CC38A")
            helperRect.set(cx + r * 0.06f, cy - r * 1.0f, cx + r * 0.6f, cy - r * 0.55f)
            c.drawOval(helperRect, p)
            p.color = Color.argb(120, 255, 255, 255)
            c.drawCircle(cx - r * 0.5f, cy - r * 0.1f, r * 0.12f, p)
        }
        1 -> {
            p.color = Color.parseColor("#FFD43B")
            c.drawCircle(cx - r * 0.1f, cy + r * 0.25f, r * 0.8f, p)
            c.drawCircle(cx + r * 0.45f, cy - r * 0.4f, r * 0.45f, p)
            p.color = Color.parseColor("#FF8A2B")
            helperPath.reset()
            helperPath.moveTo(cx + r * 0.85f, cy - r * 0.5f)
            helperPath.lineTo(cx + r * 1.2f, cy - r * 0.38f)
            helperPath.lineTo(cx + r * 0.85f, cy - r * 0.26f)
            helperPath.close()
            c.drawPath(helperPath, p)
            p.color = Color.parseColor("#2B2540")
            c.drawCircle(cx + r * 0.58f, cy - r * 0.5f, r * 0.07f, p)
        }
        2 -> {
            p.color = Color.parseColor("#FF8FB8")
            for (k in 0 until 5) {
                val a = k * 1.2566f
                c.drawCircle(cx + cos(a) * r * 0.5f, cy + sin(a) * r * 0.5f, r * 0.38f, p)
            }
            p.color = Color.parseColor("#FFD43B")
            c.drawCircle(cx, cy, r * 0.3f, p)
        }
        else -> {
            p.color = Color.parseColor("#4DA3FF")
            helperRect.set(cx - r * 0.62f, cy - r * 0.95f, cx + r * 0.62f, cy + r * 0.55f)
            c.drawOval(helperRect, p)
            helperPath.reset()
            helperPath.moveTo(cx, cy + r * 0.5f)
            helperPath.lineTo(cx - r * 0.12f, cy + r * 0.72f)
            helperPath.lineTo(cx + r * 0.12f, cy + r * 0.72f)
            helperPath.close()
            c.drawPath(helperPath, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.05f
            p.color = Color.parseColor("#8A93A0")
            c.drawLine(cx, cy + r * 0.72f, cx + r * 0.1f, cy + r * 1.05f, p)
            p.style = Paint.Style.FILL
            p.color = Color.argb(120, 255, 255, 255)
            c.drawCircle(cx - r * 0.25f, cy - r * 0.45f, r * 0.14f, p)
        }
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
    var onStar: (() -> Unit)? = null

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
    private var stage = MiniGames.bubblesStage(context)
    private val goal: Int get() = BUBBLE_GOALS[stage]
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
            if (celebrate <= 0f) {
                popped = 0
                stage = MiniGames.bubblesStage(context)
            }
        } else {
            spawnT -= dt
            if (spawnT <= 0f && bubbles.size < BUBBLE_MAX[stage]) {
                spawnT = 0.8f
                val r = (34f + Random.nextFloat() * 14f) * d * BUBBLE_SIZE[stage]
                val color = PALETTE[Random.nextInt(PALETTE.size)]
                bubbles.add(
                    Bubble(
                        r + Random.nextFloat() * (w - 2f * r), h + r, r,
                        (40f + Random.nextFloat() * 30f) * d * BUBBLE_SPEED[stage], color, Random.nextFloat() * 6.28f
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
            MiniGames.setBubblesStage(context, (stage + 1) % MiniGames.BUBBLE_STAGES)
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
    private val tx = FloatArray(4)
    private val ty = FloatArray(4)
    private val ttype = IntArray(4)
    private val placedIn = IntArray(4)
    private var count = 3
    private var stage = if (colorsMode) MiniGames.colorsStage(context) else MiniGames.shapesStage(context)
    private var perBin = 1
    private var grayHoles = false
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
        count = if (colorsMode) COLOR_BINS[stage] else STAGE_COUNTS[stage]
        grayHoles = !colorsMode && stage >= 4
        r = when (count) {
            1 -> 66f * d
            2 -> min(w * 0.2f, 62f * d)
            3 -> min(w / 3f * 0.42f, 62f * d)
            else -> min(w * 0.17f, 52f * d)
        }
        ir = if (colorsMode) r * 0.5f else r * 0.8f
        val all = mutableListOf(0, 1, 2, 3)
        all.shuffle()
        for (i in 0 until count) {
            ttype[i] = all[i]
            if (count == 4) {
                tx[i] = w * (if (i % 2 == 0) 0.3f else 0.7f)
                ty[i] = h * (if (i < 2) 0.19f else 0.37f)
            } else {
                tx[i] = w * (2 * i + 1) / (2f * count)
                ty[i] = h * 0.27f
            }
            placedIn[i] = 0
        }
        val per = if (colorsMode) COLOR_PER[stage] else 1
        perBin = per
        val list = ArrayList<Int>()
        for (i in 0 until count) for (k in 0 until per) list.add(ttype[i])
        list.shuffle()
        val rows = if (list.size > 3) 2 else 1
        val cols = (list.size + rows - 1) / rows
        items.clear()
        for ((n, type) in list.withIndex()) {
            val row = n / cols
            val col = n % cols
            val hx = w * (2 * col + 1) / (2f * cols)
            val hy = if (rows == 1) h * 0.68f else h * ((if (colorsMode) 0.56f else 0.6f) + 0.15f * row)
            items.add(Item(type, hx, hy, hx, hy))
        }
        drag = null
        celebrate = 0f
    }

    override fun step(dt: Float) {
        if (celebrate > 0f) {
            celebrate -= dt
            if (celebrate <= 0f) {
                stage = if (colorsMode) MiniGames.colorsStage(context) else MiniGames.shapesStage(context)
                newRound()
            }
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
        for (i in 0 until count) {
            val dx = item.x - tx[i]
            val dy = item.y - ty[i]
            if (sqrt(dx * dx + dy * dy) < r * 1.1f) {
                if (ttype[i] == item.type) {
                    item.placed = true
                    val idx = placedIn[i]
                    placedIn[i] = idx + 1
                    if (colorsMode) {
                        item.x = tx[i] + (idx - (perBin - 1) / 2f) * ir * 0.9f
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
                        // the next stage is saved now, and starts when the celebration is over
                        if (colorsMode) {
                            MiniGames.setColorsStage(context, (stage + 1) % MiniGames.COLOR_STAGES)
                        } else {
                            MiniGames.setShapesStage(context, (stage + 1) % MiniGames.STAGES)
                        }
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

        // the stages: a dot for each, gold for the ones reached
        run {
            val total = if (colorsMode) MiniGames.COLOR_STAGES else MiniGames.STAGES
            val filled = if (celebrate > 0f) stage + 1 else stage
            val gap = 22f * d
            val x0 = w / 2f - (total - 1) * gap / 2f
            for (i in 0 until total) {
                paint.style = Paint.Style.FILL
                paint.color = if (i < filled) Color.parseColor("#FFC93C") else Color.parseColor("#D8D2F0")
                canvas.drawCircle(x0 + i * gap, 52f * d, 6.5f * d, paint)
            }
        }
        for (i in 0 until count) {
            if (colorsMode) {
                drawBin(canvas, i)
            } else {
                val filled = placedIn[i] > 0
                val color = if (filled || !grayHoles) PALETTE[ttype[i]] else HOLE_GREY
                drawShape(canvas, paint, ttype[i], tx[i], ty[i], r * 0.85f, color, !filled, d)
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
    private val bookKind = DailyGoals.dayNumber() % 4
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
        for (i in 0 until k) {
            val bob = if (counted[i]) 0f else sin(clock * 2.4f + i) * 3f * d
            val pop = if (counted[i]) 1.15f else 1f
            drawBookItem(canvas, paint, bookKind, itemX(i, k), itemY(i, k) + bob, rad * pop)
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
    private val order = intArrayOf(0, 1, 2)
    private val used = BooleanArray(3)
    private var patient = 0
    private var anim = 0f
    private var shake = 0f
    private var celebrate = 0f
    private var bg: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg = sky(h)
    }

    /** The care that is needed now (-1 once all three are done). */
    private fun current(): Int = if (step < 3) order[step] else -1

    private fun applyPatient() {
        val o = PATIENT_ORDERS[patient % PATIENT_ORDERS.size]
        for (i in 0 until 3) {
            order[i] = o[i]
            used[i] = false
        }
    }

    private fun toolX(i: Int): Float = width * (2 * i + 1) / 6f
    private fun toolY(): Float = height * 0.80f
    private fun toolR(): Float = min(width / 6f * 0.8f, 46f * d)

    override fun step(dt: Float) {
        if (shake > 0f) shake = max(0f, shake - dt)
        if (anim > 0f) {
            anim -= dt
            if (anim <= 0f) {
                used[order[step]] = true
                step++
                if (step >= 3) {
                    celebrate = 2.4f
                    onRound?.invoke()
                }
            }
        }
        if (celebrate > 0f) {
            celebrate -= dt
            if (celebrate <= 0f) {
                step = 0
                patient++
                applyPatient()
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN || anim > 0f || celebrate > 0f) return true
        for (i in 0 until 3) {
            val dx = toolX(i) - e.x
            val dy = toolY() - e.y
            if (sqrt(dx * dx + dy * dy) < toolR() * 1.2f) {
                if (i == current()) {
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

        val current = current()
        // the ghost: pale and sad while it is sick (a different colour for each patient), white and smiling once cured
        val cured = celebrate > 0f
        val gx = w / 2f
        val gy = h * 0.34f
        drawCuteGhost(
            canvas, paint, gx, gy, 66f * d, clock, if (cured) 1f else 0f,
            if (cured) Color.parseColor("#F7F4FF") else PATIENT_COLORS[patient % PATIENT_COLORS.size], !cured
        )
        if (cured) drawCelebration(canvas, paint, gx, gy, clock, d)

        // the tool that is being used right now, next to the ghost
        if (anim > 0f && current >= 0) {
            val k = 1f - anim
            drawTool(canvas, current, gx + 78f * d + sin(clock * 12f) * 3f * d, gy - 10f * d - k * 14f * d, 56f * d)
        }

        // the three tools: the one that comes next breathes gently
        for (i in 0 until 3) {
            val x = toolX(i) + (if (shake > 0f && i != current) sin(shake * 60f) * 6f * d else 0f)
            val pulse = if (i == current && celebrate <= 0f) 1f + 0.07f * sin(clock * 4f) else 1f
            val r = toolR() * pulse
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(34, 0, 0, 0)
            canvas.drawCircle(x + 3f * d, toolY() + 5f * d, r, paint)
            paint.color = if (used[i]) Color.parseColor("#D9F5E3") else Color.WHITE
            canvas.drawCircle(x, toolY(), r, paint)
            if (i == current && celebrate <= 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f * d
                paint.color = Color.parseColor("#FFC93C")
                canvas.drawCircle(x, toolY(), r, paint)
            }
            drawTool(canvas, i, x, toolY(), r * 1.1f)
        }
    }
}

// ============================================================ game 6: the classroom (school)

/**
 * The teacher draws a shape on the blackboard. Each pupil holds a card with a shape; the child touches the
 * pupil who holds the same shape. Five right answers finish the lesson. The class grows from two to four pupils.
 */
private class ClassroomView(context: Context) : GameView(context) {

    private class Pupil(val shape: Int, val body: Int) {
        var cheer = 0f
        var shake = 0f
    }

    private val pupils = ArrayList<Pupil>()
    private val bodies = intArrayOf(
        Color.parseColor("#FFD6E8"), Color.parseColor("#D6ECFF"), Color.parseColor("#DFF5D0"), Color.parseColor("#FFF0C2")
    )
    private var target = 0
    private var lastTarget = -1
    private var correct = 0
    private val goal = 5
    private var stage = MiniGames.classStage(context)
    private var celebrate = 0f
    private var nextIn = 0f
    private var teacherJoy = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && pupils.isEmpty()) deal()
    }

    /** New cards for the pupils and a new shape on the board. */
    private fun deal() {
        val n = 2 + stage
        val all = mutableListOf(0, 1, 2, 3)
        all.shuffle()
        pupils.clear()
        for (i in 0 until n) pupils.add(Pupil(all[i], bodies[i]))
        var t = all[Random.nextInt(n)]
        if (t == lastTarget) t = all[(all.indexOf(t) + 1) % n]
        target = t
        lastTarget = t
    }

    private fun pupilX(i: Int): Float = width * (2 * i + 1) / (2f * pupils.size)
    private fun pupilR(): Float = min(width / (pupils.size * 2.7f), 40f * d)
    private fun pupilY(): Float = height * 0.62f

    override fun step(dt: Float) {
        if (pupils.isEmpty()) return
        if (teacherJoy > 0f) teacherJoy = max(0f, teacherJoy - dt)
        for (p in pupils) {
            if (p.cheer > 0f) p.cheer = max(0f, p.cheer - dt)
            if (p.shake > 0f) p.shake = max(0f, p.shake - dt)
        }
        if (nextIn > 0f) {
            nextIn -= dt
            if (nextIn <= 0f) deal()
        }
        if (celebrate > 0f) {
            celebrate -= dt
            if (celebrate <= 0f) {
                correct = 0
                stage = MiniGames.classStage(context)
                deal()
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN || nextIn > 0f || celebrate > 0f || pupils.isEmpty()) return true
        for (i in pupils.indices) {
            val dx = pupilX(i) - e.x
            val dy = pupilY() - e.y
            if (sqrt(dx * dx + dy * dy) < pupilR() * 1.5f) {
                answer(i)
                break
            }
        }
        return true
    }

    private fun answer(i: Int) {
        val p = pupils[i]
        if (p.shape == target) {
            p.cheer = 1.2f
            teacherJoy = 1.2f
            correct++
            Sounds.play(context, Snd.POP)
            if (correct >= goal) {
                celebrate = 2.6f
                onRound?.invoke()
                MiniGames.setClassStage(context, (stage + 1) % MiniGames.CLASS_STAGES)
            } else {
                nextIn = 1.3f
            }
        } else {
            p.shake = 0.5f
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (pupils.isEmpty()) return
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FFF3D6")
        canvas.drawRect(0f, 0f, w, h * 0.74f, paint)
        paint.color = Color.parseColor("#D9A86C")
        canvas.drawRect(0f, h * 0.74f, w, h, paint)
        paint.color = Color.parseColor("#C48F55")
        canvas.drawRect(0f, h * 0.74f, w, h * 0.74f + 5f * d, paint)

        // the blackboard, with the shape the teacher drew
        rrect(canvas, w * 0.28f, h * 0.05f, w * 0.94f, h * 0.29f, 10f * d, Color.parseColor("#9A6B3F"))
        rrect(canvas, w * 0.28f + 6f * d, h * 0.05f + 6f * d, w * 0.94f - 6f * d, h * 0.29f - 6f * d, 6f * d, Color.parseColor("#2F6B55"))
        rrect(canvas, w * 0.3f, h * 0.29f - 2f * d, w * 0.92f, h * 0.29f + 4f * d, 2f * d, Color.parseColor("#B5803F"))
        val bx = w * 0.61f
        val by = h * 0.17f
        drawShape(canvas, paint, target, bx, by, min(h * 0.07f, 44f * d), PALETTE[target], false, d)

        // the lesson's progress: a dot for each right answer
        val gap = 24f * d
        for (i in 0 until goal) {
            paint.style = Paint.Style.FILL
            paint.color = if (i < correct) Color.parseColor("#FFC93C") else Color.parseColor("#D9CBA8")
            canvas.drawCircle(w / 2f + (i - (goal - 1) / 2f) * gap, h * 0.345f, 8f * d, paint)
        }

        // the teacher, beside the board, pointing at it
        val tr = 32f * d
        val tx = w * 0.13f
        val tBase = h * 0.19f
        val joy = if (teacherJoy > 0f || celebrate > 0f) 1f else 0f
        drawCuteGhost(canvas, paint, tx, tBase, tr, clock, joy)
        val tcy = tBase + sin(clock * 2.2f) * tr * 0.05f - joy * abs(sin(clock * 7f)) * tr * 0.5f
        val dark = Color.parseColor("#2B2540")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tr * 0.06f
        paint.color = dark
        canvas.drawCircle(tx - tr * 0.35f, tcy - tr * 0.05f, tr * 0.2f, paint)
        canvas.drawCircle(tx + tr * 0.35f, tcy - tr * 0.05f, tr * 0.2f, paint)
        canvas.drawLine(tx - tr * 0.15f, tcy - tr * 0.05f, tx + tr * 0.15f, tcy - tr * 0.05f, paint)
        paint.color = Color.parseColor("#8A5A2B")
        paint.strokeWidth = 3f * d
        canvas.drawLine(tx + tr * 0.9f, tcy + tr * 0.3f, tx + tr * 2.2f + sin(clock * 5f) * 3f * d, tcy - tr * 0.6f, paint)
        paint.style = Paint.Style.FILL
        helperPath.reset()
        helperPath.moveTo(tx - tr * 0.95f, tcy - tr * 0.9f)
        helperPath.lineTo(tx, tcy - tr * 1.2f)
        helperPath.lineTo(tx + tr * 0.95f, tcy - tr * 0.9f)
        helperPath.lineTo(tx, tcy - tr * 0.65f)
        helperPath.close()
        paint.color = dark
        canvas.drawPath(helperPath, paint)
        paint.color = Color.parseColor("#FFC93C")
        canvas.drawCircle(tx + tr * 0.95f, tcy - tr * 0.55f, tr * 0.07f, paint)

        // the pupils, each at a desk holding a card with a shape
        val r = pupilR()
        for ((i, p) in pupils.withIndex()) {
            val x = pupilX(i) + sin(p.shake * 50f) * 5f * d * (if (p.shake > 0f) 1f else 0f)
            val y = pupilY()
            val happy = p.cheer > 0f || celebrate > 0f
            drawCuteGhost(canvas, paint, x, y, r, clock + i * 1.3f, if (happy) 1f else 0f, p.body, p.shake > 0f)
            rrect(canvas, x - r * 0.5f, y + r * 0.5f, x + r * 0.5f, y + r * 1.05f, r * 0.08f, Color.WHITE)
            drawShape(canvas, paint, p.shape, x, y + r * 0.78f, r * 0.2f, PALETTE[p.shape], false, d)
            rrect(canvas, x - r * 1.05f, y + r * 1.12f, x + r * 1.05f, y + r * 1.85f, r * 0.12f, Color.parseColor("#B5703A"))
            rrect(canvas, x - r * 1.05f, y + r * 1.12f, x + r * 1.05f, y + r * 1.25f, r * 0.08f, Color.parseColor("#D88C4A"))
        }
        if (celebrate > 0f) drawCelebration(canvas, paint, w / 2f, h * 0.45f, clock, d)
    }

    private fun rrect(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        helperRect.set(l, t, r, b)
        c.drawRoundRect(helperRect, rad, rad, paint)
    }
}

// ============================================================ more drawings

private val ballClip = Path()

private fun drawButterfly(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int, flap: Float) {
    val w = abs(sin(flap)) * s * 0.9f + s * 0.12f
    p.style = Paint.Style.FILL
    p.color = color
    helperRect.set(x - w, y - s * 0.8f, x - 0.05f * s, y + s * 0.1f)
    c.drawOval(helperRect, p)
    helperRect.set(x + 0.05f * s, y - s * 0.8f, x + w, y + s * 0.1f)
    c.drawOval(helperRect, p)
    helperRect.set(x - w * 0.8f, y - s * 0.05f, x - 0.05f * s, y + s * 0.7f)
    c.drawOval(helperRect, p)
    helperRect.set(x + 0.05f * s, y - s * 0.05f, x + w * 0.8f, y + s * 0.7f)
    c.drawOval(helperRect, p)
    p.color = Color.argb(150, 255, 255, 255)
    c.drawCircle(x - w * 0.5f, y - s * 0.35f, s * 0.12f, p)
    c.drawCircle(x + w * 0.5f, y - s * 0.35f, s * 0.12f, p)
    p.color = Color.parseColor("#2B2540")
    helperRect.set(x - s * 0.06f, y - s * 0.55f, x + s * 0.06f, y + s * 0.55f)
    c.drawRoundRect(helperRect, s * 0.06f, s * 0.06f, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = s * 0.04f
    p.strokeCap = Paint.Cap.ROUND
    c.drawLine(x, y - s * 0.55f, x - s * 0.2f, y - s * 0.82f, p)
    c.drawLine(x, y - s * 0.55f, x + s * 0.2f, y - s * 0.82f, p)
    p.style = Paint.Style.FILL
}

/** Four kinds of ball: 0 spotted, 1 beach ball, 2 football, 3 striped. */
private fun drawBall(c: Canvas, p: Paint, type: Int, cx: Float, cy: Float, r: Float, colorIndex: Int, angle: Float) {
    p.style = Paint.Style.FILL
    when (type) {
        0 -> {
            p.color = PALETTE[colorIndex % 4]
            c.drawCircle(cx, cy, r, p)
            p.color = Color.WHITE
            for (k in 0 until 4) {
                val a = (angle + k * 90f + 45f) * (PI.toFloat() / 180f)
                c.drawCircle(cx + cos(a) * r * 0.55f, cy + sin(a) * r * 0.55f, r * 0.16f, p)
            }
        }
        1 -> {
            helperRect.set(cx - r, cy - r, cx + r, cy + r)
            for (k in 0 until 4) {
                p.color = PALETTE[(k + colorIndex) % 4]
                c.drawArc(helperRect, angle + k * 90f, 90f, true, p)
            }
            p.color = Color.WHITE
            c.drawCircle(cx, cy, r * 0.16f, p)
        }
        2 -> {
            p.color = Color.WHITE
            c.drawCircle(cx, cy, r, p)
            p.color = Color.parseColor("#2B2540")
            helperPath.reset()
            for (k in 0 until 5) {
                val a = (angle - 90f + k * 72f) * (PI.toFloat() / 180f)
                val x = cx + cos(a) * r * 0.38f
                val y = cy + sin(a) * r * 0.38f
                if (k == 0) helperPath.moveTo(x, y) else helperPath.lineTo(x, y)
            }
            helperPath.close()
            c.drawPath(helperPath, p)
            for (k in 0 until 5) {
                val a = (angle - 90f + k * 72f) * (PI.toFloat() / 180f)
                c.drawCircle(cx + cos(a) * r * 0.82f, cy + sin(a) * r * 0.82f, r * 0.1f, p)
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.06f
            p.color = Color.parseColor("#B8B0D8")
            c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.FILL
        }
        else -> {
            p.color = PALETTE[colorIndex % 4]
            c.drawCircle(cx, cy, r, p)
            ballClip.reset()
            ballClip.addCircle(cx, cy, r, Path.Direction.CW)
            c.save()
            c.clipPath(ballClip)
            c.rotate(angle, cx, cy)
            p.color = Color.WHITE
            helperRect.set(cx - r, cy - r * 0.45f, cx + r, cy - r * 0.15f)
            c.drawRect(helperRect, p)
            helperRect.set(cx - r, cy + r * 0.15f, cx + r, cy + r * 0.45f)
            c.drawRect(helperRect, p)
            c.restore()
        }
    }
    p.color = Color.argb(120, 255, 255, 255)
    c.drawCircle(cx - r * 0.4f, cy - r * 0.4f, r * 0.14f, p)
}

/** A picture for the twins game: 0 to 3 are the book things, 4 to 7 are the shapes. */
private fun drawPicture(c: Canvas, p: Paint, kind: Int, x: Float, y: Float, r: Float, d: Float) {
    if (kind < 4) drawBookItem(c, p, kind, x, y, r) else drawShape(c, p, kind - 4, x, y, r, PALETTE[kind - 4], false, d)
}

// ============================================================ game 7: the ghost's bubbles

/** The ghost blows bubbles into the air and the child pops them. Now and then a golden one with a star comes. */
private class GhostBubblesView(context: Context) : GameView(context) {

    private class GBubble(
        var x: Float, var y: Float, val r: Float, val vx: Float, val vy: Float,
        val color: Int, val golden: Boolean, val phase: Float
    )

    private class GBurst(val x: Float, val y: Float, val color: Int) {
        var t = 0f
    }

    private val bubbles = ArrayList<GBubble>()
    private val bursts = ArrayList<GBurst>()
    private var spawnT = 0.6f
    private var blow = 0f
    private var popped = 0
    private val goal = 8
    private var celebrate = 0f
    private var sinceGolden = 0
    private var bg: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg = sky(h)
    }

    private fun gx(): Float = width / 2f
    private fun gy(): Float = height * 0.80f
    private fun gr(): Float = 38f * d
    private fun shownX(b: GBubble): Float = b.x + sin(clock * 1.6f + b.phase) * 8f * d

    override fun step(dt: Float) {
        val w = width.toFloat()
        if (w <= 0f) return
        if (blow > 0f) blow = max(0f, blow - dt)
        if (celebrate > 0f) {
            celebrate -= dt
            if (celebrate <= 0f) popped = 0
        } else {
            spawnT -= dt
            if (spawnT <= 0f && bubbles.size < 6) {
                spawnT = 1.0f + Random.nextFloat() * 0.5f
                blow = 0.45f
                sinceGolden++
                val golden = sinceGolden >= 5 && Random.nextFloat() < 0.5f
                if (golden) sinceGolden = 0
                val radius = if (golden) 38f * d else (30f + Random.nextFloat() * 14f) * d
                val color = if (golden) Color.parseColor("#FFC93C") else PALETTE[Random.nextInt(PALETTE.size)]
                bubbles.add(
                    GBubble(
                        gx() + (Random.nextFloat() - 0.5f) * 20f * d, gy() - gr() * 0.5f, radius,
                        (Random.nextFloat() - 0.5f) * 70f * d, -(55f + Random.nextFloat() * 35f) * d,
                        color, golden, Random.nextFloat() * 6.28f
                    )
                )
            }
        }
        for (b in bubbles) {
            b.x += b.vx * dt
            b.y += b.vy * dt
        }
        bubbles.removeAll { it.y < -it.r || it.x < -it.r || it.x > w + it.r }
        for (u in bursts) u.t += dt
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
        var best: GBubble? = null
        var bestDist = Float.MAX_VALUE
        for (b in bubbles) {
            val dx = shownX(b) - x
            val dy = b.y - y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < b.r * 1.3f && dist < bestDist) {
                best = b
                bestDist = dist
            }
        }
        val hit = best ?: return
        bubbles.remove(hit)
        bursts.add(GBurst(shownX(hit), hit.y, hit.color))
        Sounds.play(context, Snd.POP)
        if (hit.golden) {
            Sounds.play(context, Snd.SPARKLE, true)
            onStar?.invoke()
        }
        popped++
        if (popped >= goal) {
            for (o in bubbles) bursts.add(GBurst(shownX(o), o.y, o.color))
            bubbles.clear()
            celebrate = 2.2f
            onRound?.invoke()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        paintBackground(canvas, bg)
        val gap = 26f * d
        val x0 = w / 2f - (goal - 1) * gap / 2f
        for (i in 0 until goal) {
            paint.style = Paint.Style.FILL
            paint.color = if (i < popped) Color.parseColor("#FFC93C") else Color.parseColor("#D8D2F0")
            canvas.drawCircle(x0 + i * gap, 70f * d, 9f * d, paint)
        }
        for (b in bubbles) {
            val x = shownX(b)
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(b.color, if (b.golden) 150 else 110)
            canvas.drawCircle(x, b.y, b.r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * d
            paint.color = withAlpha(b.color, 235)
            canvas.drawCircle(x, b.y, b.r, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(190, 255, 255, 255)
            canvas.drawCircle(x - b.r * 0.35f, b.y - b.r * 0.35f, b.r * 0.18f, paint)
            if (b.golden) drawShape(canvas, paint, 3, x, b.y, b.r * 0.5f, Color.parseColor("#FF8A2B"), false, d)
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
        drawCuteGhost(canvas, paint, gx(), gy(), gr(), clock, if (celebrate > 0f) 1f else 0f)
        if (blow > 0f) {
            // the mouth is a round "o" while the ghost blows
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#F7F4FF")
            canvas.drawCircle(gx(), gy() + gr() * 0.27f, gr() * 0.3f, paint)
            paint.color = Color.parseColor("#2B2540")
            canvas.drawCircle(gx(), gy() + gr() * 0.3f, gr() * 0.13f, paint)
        }
        if (celebrate > 0f) drawCelebration(canvas, paint, gx(), gy(), clock, d)
    }
}

// ============================================================ game 8: catch the butterfly

/** Butterflies fly around the ghost. The child touches one: the ghost jumps for joy, holds it a moment, then lets it go. */
private class ButterflyView(context: Context) : GameView(context) {

    private class Fly(val radius: Float, val speed: Float, var angle: Float, val color: Int, val phase: Float, val ky: Float) {
        var held = 0f
        var rel = 1f
    }

    private val flies = ArrayList<Fly>()
    private var stage = MiniGames.butterflyStage(context)
    private var caught = 0
    private val goal = 5
    private var celebrate = 0f
    private var joy = 0f
    private var bg: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg = sky(h)
        if (flies.isEmpty() && w > 0) setup()
    }

    private fun setup() {
        flies.clear()
        val n = 2 + stage
        for (i in 0 until n) {
            val dir = if (i % 2 == 0) 1f else -1f
            flies.add(
                Fly(
                    min((90f + 34f * i) * d, width * 0.42f), (0.5f + 0.18f * stage + 0.08f * i) * dir,
                    i * 2.1f, PALETTE[i % 4], i * 1.7f, 0.7f + 0.1f * i
                )
            )
        }
    }

    private fun cx(): Float = width / 2f
    private fun cy(): Float = height * 0.46f
    private fun gr(): Float = 36f * d

    private fun handX(): Float = cx() + gr() * 1.05f
    private fun handY(): Float = cy() - gr() * 0.2f

    private fun posX(f: Fly): Float {
        val ox = cx() + cos(f.angle) * f.radius
        if (f.held > 0f) return handX() + sin(clock * 12f + f.phase) * 4f * d
        val k = f.rel.coerceIn(0f, 1f)
        return handX() + (ox - handX()) * k
    }

    private fun posY(f: Fly): Float {
        val oy = cy() + sin(f.angle * 1.3f + f.phase) * f.radius * f.ky + sin(clock * 3f + f.phase) * 8f * d
        if (f.held > 0f) return handY() + sin(clock * 9f + f.phase) * 3f * d
        val k = f.rel.coerceIn(0f, 1f)
        return handY() + (oy - handY()) * k
    }

    override fun step(dt: Float) {
        if (flies.isEmpty()) return
        if (joy > 0f) joy = max(0f, joy - dt)
        for (f in flies) {
            if (f.held > 0f) {
                f.held -= dt
                if (f.held <= 0f) f.rel = 0f
            } else {
                f.angle += f.speed * dt
                if (f.rel < 1f) f.rel = min(1f, f.rel + dt / 0.9f)
            }
        }
        if (celebrate > 0f) {
            celebrate -= dt
            if (celebrate <= 0f) {
                caught = 0
                stage = MiniGames.butterflyStage(context)
                setup()
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN || celebrate > 0f) return true
        for (f in flies) {
            if (f.held > 0f || f.rel < 1f) continue
            val dx = posX(f) - e.x
            val dy = posY(f) - e.y
            if (sqrt(dx * dx + dy * dy) < 48f * d) {
                f.held = 1.0f
                joy = 1.2f
                caught++
                Sounds.play(context, Snd.POP)
                if (caught >= goal) {
                    celebrate = 2.4f
                    onRound?.invoke()
                    MiniGames.setButterflyStage(context, (stage + 1) % MiniGames.BUTTERFLY_STAGES)
                }
                break
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        paintBackground(canvas, bg)
        val gap = 26f * d
        val x0 = w / 2f - (goal - 1) * gap / 2f
        for (i in 0 until goal) {
            paint.style = Paint.Style.FILL
            paint.color = if (i < caught) Color.parseColor("#FFC93C") else Color.parseColor("#D8D2F0")
            canvas.drawCircle(x0 + i * gap, 70f * d, 9f * d, paint)
        }
        drawCuteGhost(canvas, paint, cx(), cy(), gr(), clock, if (joy > 0f || celebrate > 0f) 1f else 0f)
        for (f in flies) {
            drawButterfly(canvas, paint, posX(f), posY(f), 24f * d, f.color, clock * 14f + f.phase)
        }
        if (celebrate > 0f) drawCelebration(canvas, paint, cx(), cy(), clock, d)
    }
}

// ============================================================ game 9: pass the ball

/** The ghost rolls a ball to the child, who drags it back up to the ghost. The ball changes every round. */
private class BallView(context: Context) : GameView(context) {

    private var phase = 0 // 0 rolling to the child, 1 waiting, 2 dragged, 3 flying back, 4 the ghost has it
    private var t = 0f
    private var bx = 0f
    private var by = 0f
    private var offX = 0f
    private var offY = 0f
    private var angle = 0f
    private var type = 0
    private var colorIndex = 0
    private var returns = 0
    private val goal = 5
    private var celebrate = 0f
    private var joy = 0f
    private var bg: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg = sky(h)
        if (w > 0 && h > 0 && t == 0f && phase == 0) startRoll()
    }

    private fun gx(): Float = width / 2f
    private fun gy(): Float = height * 0.20f
    private fun gr(): Float = 38f * d
    private fun ballR(): Float = 40f * d
    private fun restX(): Float = width / 2f
    private fun restY(): Float = height * 0.70f

    private fun startRoll() {
        phase = 0
        t = 0.0001f
        bx = gx()
        by = gy() + gr()
    }

    private fun nextBall() {
        type = (type + 1) % 4
        colorIndex = Random.nextInt(4)
    }

    override fun step(dt: Float) {
        if (width <= 0) return
        if (joy > 0f) joy = max(0f, joy - dt)
        when (phase) {
            0 -> {
                t += dt / 1.8f
                val k = 1f - (1f - min(t, 1f)) * (1f - min(t, 1f))
                bx = gx()
                by = gy() + gr() + (restY() - gy() - gr()) * k
                angle = k * 720f
                if (t >= 1f) {
                    phase = 1
                    t = 0f
                }
            }
            1 -> {
                bx += (restX() - bx) * min(1f, dt * 8f)
                by += (restY() - by) * min(1f, dt * 8f)
            }
            3 -> {
                t += dt / 0.35f
                val k = min(t, 1f)
                bx += (gx() - bx) * min(1f, dt * 10f)
                by += (gy() + gr() * 0.4f - by) * min(1f, dt * 10f)
                angle += 900f * dt
                if (k >= 1f) {
                    phase = 4
                    t = 0f
                    joy = 1.1f
                    returns++
                    Sounds.play(context, Snd.POP)
                    if (returns >= goal) {
                        celebrate = 2.4f
                        onRound?.invoke()
                    }
                }
            }
            4 -> {
                t += dt
                if (celebrate > 0f) {
                    celebrate -= dt
                    if (celebrate <= 0f) {
                        returns = 0
                        nextBall()
                        startRoll()
                    }
                } else if (t > 1.0f) {
                    nextBall()
                    startRoll()
                }
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (phase == 1) {
                    val dx = bx - e.x
                    val dy = by - e.y
                    if (sqrt(dx * dx + dy * dy) < ballR() * 1.7f) {
                        phase = 2
                        offX = dx
                        offY = dy
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (phase == 2) {
                    bx = e.x + offX
                    by = e.y + offY
                    angle += 6f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (phase == 2) {
                    val dx = bx - gx()
                    val dy = by - gy()
                    if (sqrt(dx * dx + dy * dy) < gr() + ballR() * 1.6f || by < height * 0.36f) {
                        phase = 3
                        t = 0f
                    } else {
                        phase = 1
                    }
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        paintBackground(canvas, bg)
        val gap = 26f * d
        val x0 = w / 2f - (goal - 1) * gap / 2f
        for (i in 0 until goal) {
            paint.style = Paint.Style.FILL
            paint.color = if (i < returns) Color.parseColor("#FFC93C") else Color.parseColor("#D8D2F0")
            canvas.drawCircle(x0 + i * gap, 70f * d + gr() * 2.6f, 9f * d, paint)
        }
        drawCuteGhost(canvas, paint, gx(), gy(), gr(), clock, if (joy > 0f || celebrate > 0f) 1f else 0f)

        // little arrows that rise toward the ghost while the ball waits for the child
        if (phase == 1) {
            for (k in 0 until 3) {
                val ph = (clock * 1.2f + k * 0.33f) % 1f
                val y = restY() - ballR() * 1.5f - ph * 90f * d
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f * d
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = withAlpha(Color.parseColor("#7C5CFF"), ((1f - ph) * 200f).toInt())
                canvas.drawLine(restX() - 14f * d, y + 8f * d, restX(), y - 4f * d, paint)
                canvas.drawLine(restX() + 14f * d, y + 8f * d, restX(), y - 4f * d, paint)
            }
        }
        val pulse = if (phase == 1) 1f + 0.05f * sin(clock * 4f) else if (phase == 2) 1.12f else 1f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(34, 0, 0, 0)
        helperRect.set(bx - ballR() * 0.9f, by + ballR() * 0.85f, bx + ballR() * 0.9f, by + ballR() * 1.1f)
        canvas.drawOval(helperRect, paint)
        drawBall(canvas, paint, type, bx, by, ballR() * pulse, colorIndex, angle)
        if (celebrate > 0f) drawCelebration(canvas, paint, gx(), gy(), clock, d)
    }
}

// ============================================================ game 10: where is the right shape?

/** The ghost thinks of a shape (the first one is a circle); the child picks it among three. */
private class WhichShapeView(context: Context) : GameView(context) {

    private val options = IntArray(3)
    private val shake = FloatArray(3)
    private var target = 0
    private var correct = 0
    private val goal = 5
    private var nextIn = 0f
    private var joy = 0f
    private var sadT = 0f
    private var celebrate = 0f
    private var first = true
    private var bg: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg = sky(h)
        if (w > 0 && first) {
            first = false
            deal(0)
        }
    }

    /** A new question; [wanted] is the shape asked for, or -1 for a random one different from the last. */
    private fun deal(wanted: Int) {
        val all = mutableListOf(0, 1, 2, 3)
        all.shuffle()
        var t = if (wanted >= 0) wanted else all[0]
        if (wanted < 0 && t == target) t = all[1]
        target = t
        val others = ArrayList<Int>()
        for (s in 0 until 4) if (s != t) others.add(s)
        others.shuffle()
        val list = mutableListOf(t, others[0], others[1])
        list.shuffle()
        for (i in 0 until 3) {
            options[i] = list[i]
            shake[i] = 0f
        }
    }

    private fun cardSize(): Float = min(width * 0.27f, 104f * d)
    private fun cardX(i: Int): Float = width * (2 * i + 1) / 6f
    private fun cardY(): Float = height * 0.64f

    override fun step(dt: Float) {
        if (joy > 0f) joy = max(0f, joy - dt)
        if (sadT > 0f) sadT = max(0f, sadT - dt)
        for (i in 0 until 3) if (shake[i] > 0f) shake[i] = max(0f, shake[i] - dt)
        if (nextIn > 0f) {
            nextIn -= dt
            if (nextIn <= 0f) deal(-1)
        }
        if (celebrate > 0f) {
            celebrate -= dt
            if (celebrate <= 0f) {
                correct = 0
                deal(0)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN || nextIn > 0f || celebrate > 0f) return true
        val half = cardSize() / 2f
        for (i in 0 until 3) {
            if (abs(e.x - cardX(i)) < half && abs(e.y - cardY()) < half) {
                if (options[i] == target) {
                    joy = 1.3f
                    correct++
                    Sounds.play(context, Snd.POP)
                    if (correct >= goal) {
                        celebrate = 2.4f
                        onRound?.invoke()
                    } else {
                        nextIn = 1.4f
                    }
                } else {
                    shake[i] = 0.5f
                    sadT = 0.6f
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
        val gap = 24f * d
        for (i in 0 until goal) {
            paint.style = Paint.Style.FILL
            paint.color = if (i < correct) Color.parseColor("#FFC93C") else Color.parseColor("#D8D2F0")
            canvas.drawCircle(w / 2f + (i - (goal - 1) / 2f) * gap, 64f * d, 8f * d, paint)
        }

        // the ghost and its thought: the shape it wants
        val happy = joy > 0f || celebrate > 0f
        drawCuteGhost(canvas, paint, w * 0.27f, h * 0.27f, 40f * d, clock, if (happy) 1f else 0f, Color.parseColor("#F7F4FF"), sadT > 0f)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(w * 0.46f, h * 0.30f, 6f * d, paint)
        canvas.drawCircle(w * 0.52f, h * 0.27f, 9f * d, paint)
        canvas.drawCircle(w * 0.70f, h * 0.21f, 58f * d, paint)
        drawShape(canvas, paint, target, w * 0.70f, h * 0.21f, 32f * d, PALETTE[target], false, d)

        // three cards to choose from
        for (i in 0 until 3) {
            val x = cardX(i) + sin(shake[i] * 50f) * 6f * d * (if (shake[i] > 0f) 1f else 0f)
            val s = cardSize()
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(34, 0, 0, 0)
            helperRect.set(x - s / 2f + 3f * d, cardY() - s / 2f + 6f * d, x + s / 2f + 3f * d, cardY() + s / 2f + 6f * d)
            canvas.drawRoundRect(helperRect, 22f * d, 22f * d, paint)
            paint.color = Color.WHITE
            helperRect.set(x - s / 2f, cardY() - s / 2f, x + s / 2f, cardY() + s / 2f)
            canvas.drawRoundRect(helperRect, 22f * d, 22f * d, paint)
            drawShape(canvas, paint, options[i], x, cardY(), s * 0.32f, PALETTE[options[i]], false, d)
        }
        if (celebrate > 0f) drawCelebration(canvas, paint, w / 2f, h * 0.45f, clock, d)
    }
}

// ============================================================ game 11: find the twin

/**
 * A few pictures lie on the table and the child looks for the identical ones: touch one, then its twin.
 * It begins with one pair among three pictures and grows to three pairs.
 */
private class TwinsView(context: Context) : GameView(context) {

    private class Card(val kind: Int) {
        var matched = false
        var sel = false
        var shake = 0f
    }

    private val cards = ArrayList<Card>()
    private var stage = MiniGames.twinsStage(context)
    private var first = -1
    private var second = -1
    private var checkT = 0f
    private var pairsLeft = 0
    private var celebrate = 0f
    private var bg: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bg = sky(h)
        if (cards.isEmpty() && w > 0) deal()
    }

    private fun deal() {
        val pairs = intArrayOf(1, 2, 2, 3)[stage]
        val odds = intArrayOf(1, 0, 1, 0)[stage]
        val kinds = mutableListOf(0, 1, 2, 3, 4, 5, 6, 7)
        kinds.shuffle()
        cards.clear()
        var n = 0
        for (i in 0 until pairs) {
            cards.add(Card(kinds[n]))
            cards.add(Card(kinds[n]))
            n++
        }
        for (i in 0 until odds) {
            cards.add(Card(kinds[n]))
            n++
        }
        cards.shuffle()
        pairsLeft = pairs
        first = -1
        second = -1
        checkT = 0f
    }

    private fun cols(): Int = if (cards.size <= 3) cards.size else 3
    private fun rowsCount(): Int = (cards.size + cols() - 1) / cols()
    private fun cardSize(): Float = min(min(width / (cols() * 1.3f), 104f * d), height * 0.5f / (rowsCount() * 1.3f))

    private fun cardX(i: Int): Float {
        val row = i / cols()
        val rowItems = if (row < rowsCount() - 1) cols() else cards.size - cols() * (rowsCount() - 1)
        return width * (2 * (i % cols()) + 1) / (2f * rowItems)
    }

    private fun cardY(i: Int): Float = height * 0.20f + cardSize() * 0.65f + (i / cols()) * cardSize() * 1.3f

    override fun step(dt: Float) {
        for (c in cards) if (c.shake > 0f) c.shake = max(0f, c.shake - dt)
        if (checkT > 0f) {
            checkT -= dt
            if (checkT <= 0f) {
                if (first >= 0) cards[first].sel = false
                if (second >= 0) cards[second].sel = false
                first = -1
                second = -1
            }
        }
        if (celebrate > 0f) {
            celebrate -= dt
            if (celebrate <= 0f) {
                stage = MiniGames.twinsStage(context)
                deal()
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN || checkT > 0f || celebrate > 0f) return true
        val half = cardSize() / 2f
        for (i in cards.indices) {
            val c = cards[i]
            if (c.matched || c.sel) continue
            if (abs(e.x - cardX(i)) < half && abs(e.y - cardY(i)) < half) {
                if (first < 0) {
                    first = i
                    c.sel = true
                } else {
                    val f = cards[first]
                    if (f.kind == c.kind) {
                        f.matched = true
                        c.matched = true
                        f.sel = false
                        first = -1
                        pairsLeft--
                        Sounds.play(context, Snd.POP)
                        if (pairsLeft <= 0) {
                            celebrate = 2.4f
                            onRound?.invoke()
                            MiniGames.setTwinsStage(context, (stage + 1) % MiniGames.TWINS_STAGES)
                        }
                    } else {
                        c.sel = true
                        second = i
                        f.shake = 0.5f
                        c.shake = 0.5f
                        checkT = 0.7f
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
        val gap = 22f * d
        for (i in 0 until MiniGames.TWINS_STAGES) {
            paint.style = Paint.Style.FILL
            val filled = if (celebrate > 0f) stage + 1 else stage
            paint.color = if (i < filled) Color.parseColor("#FFC93C") else Color.parseColor("#D8D2F0")
            canvas.drawCircle(w / 2f + (i - (MiniGames.TWINS_STAGES - 1) / 2f) * gap, 52f * d, 6.5f * d, paint)
        }
        val s = cardSize()
        for (i in cards.indices) {
            val c = cards[i]
            val x = cardX(i) + sin(c.shake * 50f) * 6f * d * (if (c.shake > 0f) 1f else 0f)
            val y = cardY(i) - (if (c.sel) 8f * d else 0f)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(34, 0, 0, 0)
            helperRect.set(x - s / 2f + 3f * d, y - s / 2f + 6f * d, x + s / 2f + 3f * d, y + s / 2f + 6f * d)
            canvas.drawRoundRect(helperRect, 20f * d, 20f * d, paint)
            paint.color = if (c.matched) Color.parseColor("#E3F8EA") else Color.WHITE
            helperRect.set(x - s / 2f, y - s / 2f, x + s / 2f, y + s / 2f)
            canvas.drawRoundRect(helperRect, 20f * d, 20f * d, paint)
            if (c.sel || c.matched) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f * d
                paint.color = if (c.matched) Color.parseColor("#4CC38A") else Color.parseColor("#FFC93C")
                canvas.drawRoundRect(helperRect, 20f * d, 20f * d, paint)
                paint.style = Paint.Style.FILL
            }
            val bob = if (c.matched) sin(clock * 4f + i) * 3f * d else 0f
            drawPicture(canvas, paint, c.kind, x, y + bob, s * 0.3f, d)
        }
        val happy = celebrate > 0f
        drawCuteGhost(canvas, paint, w / 2f, h - 78f * d, 32f * d, clock, if (happy) 1f else 0f)
        if (happy) drawCelebration(canvas, paint, w / 2f, h - 78f * d, clock, d)
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
            MiniGames.GAME_GHOST_BUBBLES -> {
                drawCuteGhost(canvas, paint, cx - 0.1f * s, cy + 0.12f * s + bob, 0.17f * s, clock, 0f)
                paint.style = Paint.Style.FILL
                paint.color = withAlpha(Color.parseColor("#FFC93C"), 150)
                canvas.drawCircle(cx + 0.22f * s, cy - 0.16f * s - bob, 0.15f * s, paint)
                drawShape(canvas, paint, 3, cx + 0.22f * s, cy - 0.16f * s - bob, 0.08f * s, Color.parseColor("#FF8A2B"), false, d)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * d
                paint.color = withAlpha(PALETTE[1], 235)
                canvas.drawCircle(cx - 0.18f * s, cy - 0.22f * s + bob, 0.1f * s, paint)
                paint.style = Paint.Style.FILL
            }
            MiniGames.GAME_BUTTERFLY -> {
                drawButterfly(canvas, paint, cx, cy + bob, 0.26f * s, PALETTE[0], clock * 6f)
                drawButterfly(canvas, paint, cx - 0.28f * s, cy - 0.2f * s - bob, 0.14f * s, PALETTE[1], clock * 7f + 1f)
            }
            MiniGames.GAME_BALL -> drawBall(canvas, paint, 1, cx, cy + bob, 0.22f * s, 0, clock * 60f)
            MiniGames.GAME_WHICH -> {
                drawShape(canvas, paint, 0, cx - 0.26f * s, cy + bob, 0.13f * s, PALETTE[0], false, d)
                drawShape(canvas, paint, 1, cx, cy - bob, 0.12f * s, PALETTE[1], false, d)
                drawShape(canvas, paint, 2, cx + 0.26f * s, cy + bob, 0.13f * s, PALETTE[2], false, d)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f * d
                paint.color = Color.parseColor("#FFC93C")
                canvas.drawCircle(cx - 0.26f * s, cy + bob, 0.19f * s, paint)
                paint.style = Paint.Style.FILL
            }
            MiniGames.GAME_TWINS -> {
                drawBookItem(canvas, paint, 0, cx - 0.27f * s, cy + bob, 0.1f * s)
                drawBookItem(canvas, paint, 1, cx, cy - bob, 0.1f * s)
                drawBookItem(canvas, paint, 0, cx + 0.27f * s, cy + bob, 0.1f * s)
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

/** The small games as big picture cards, two in a row. */
class MiniGamesActivity : Activity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.background = gameBackground()

        val games = intArrayOf(
            MiniGames.GAME_BUBBLES, MiniGames.GAME_GHOST_BUBBLES,
            MiniGames.GAME_SHAPES, MiniGames.GAME_WHICH,
            MiniGames.GAME_COLORS, MiniGames.GAME_TWINS,
            MiniGames.GAME_BUTTERFLY, MiniGames.GAME_BALL
        )
        val dm = resources.displayMetrics
        val rows = (games.size + 1) / 2
        val side = max(dp(80), min(min((dm.widthPixels - dp(48)) / 2, (dm.heightPixels - dp(150)) / rows - dp(12)), dp(150)))

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER
        column.setPadding(0, dp(80), 0, dp(16))
        for (r in 0 until rows) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER
            for (c in 0 until 2) {
                val index = r * 2 + c
                if (index >= games.size) continue
                val game = games[index]
                val tile = GameTileView(this, game)
                tile.setOnClickListener {
                    startActivity(Intent(this, MiniGameActivity::class.java).putExtra(MiniGameActivity.EXTRA_GAME, game))
                }
                val lp = LinearLayout.LayoutParams(side, side)
                lp.setMargins(dp(6), dp(6), dp(6), dp(6))
                line.addView(tile, lp)
            }
            column.addView(
                line,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            )
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
            MiniGames.GAME_CLASS -> ClassroomView(this)
            MiniGames.GAME_GHOST_BUBBLES -> GhostBubblesView(this)
            MiniGames.GAME_BUTTERFLY -> ButterflyView(this)
            MiniGames.GAME_BALL -> BallView(this)
            MiniGames.GAME_WHICH -> WhichShapeView(this)
            MiniGames.GAME_TWINS -> TwinsView(this)
            else -> BubblesView(this)
        }
        view.onRound = { MiniGames.reward(this) }
        view.onStar = { MiniGames.goldenStar(this) }

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
