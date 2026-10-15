package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cartoon egg on a transparent background. It cracks slowly and gradually while it incubates,
 * then the top shell lifts off and the baby (a tinted [GhostView] behind this view) rises out.
 * Incubation progress is wall-clock based, so it continues even if the app was closed.
 */
class EggView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onHatched()
    }

    var listener: Listener? = null
    var baby: GhostView? = null
    var tint: Int = Color.parseColor("#7C5CFF")

    private val d = resources.displayMetrics.density

    private var startMs = 0L
    private var durationMs = 180_000L
    private var running = false
    private var hatching = false
    private var hatchT = 0f
    private var babyHappyShown = false
    private var appear = 1f
    private var progress = 0f
    private var clock = 0f
    private var lastTick = 0L
    private var wobbleTimer = 5f
    private var wobbleT = -1f

    // ----- Egg geometry in a 70 x 90 unit box -----
    private val eggPath = Path().apply {
        moveTo(35f, 2f)
        cubicTo(55f, 2f, 68f, 40f, 68f, 58f)
        cubicTo(68f, 78f, 54f, 88f, 35f, 88f)
        cubicTo(16f, 88f, 2f, 78f, 2f, 58f)
        cubicTo(2f, 40f, 15f, 2f, 35f, 2f)
        close()
    }
    private val zigX = floatArrayOf(0f, 9f, 17f, 26f, 35f, 44f, 53f, 61f, 70f)
    private val zigY = floatArrayOf(52f, 44f, 56f, 46f, 57f, 46f, 56f, 45f, 52f)
    private val topRegion = Path()
    private val bottomRegion = Path()
    private val zigPath = Path()

    // Crack segments x1,y1,x2,y2 (revealed in order as the egg incubates)
    private val cracks = floatArrayOf(
        35f, 10f, 31f, 18f,
        31f, 18f, 37f, 25f,
        37f, 25f, 32f, 33f,
        32f, 33f, 36f, 41f,
        31f, 18f, 24f, 22f,
        24f, 22f, 20f, 30f,
        37f, 25f, 45f, 29f,
        45f, 29f, 49f, 37f,
        32f, 33f, 26f, 38f,
        36f, 41f, 31f, 45f,
        31f, 45f, 35f, 49f,
        36f, 41f, 43f, 46f
    )
    private val spots = floatArrayOf(22f, 26f, 4f, 48f, 34f, 3.2f, 40f, 64f, 4.5f, 20f, 60f, 3.4f, 52f, 70f, 3f)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val eggShader = RadialGradient(
        26f, 30f, 62f,
        intArrayOf(Color.parseColor("#FFFDF6"), Color.parseColor("#F8EAD2"), Color.parseColor("#E4CCA0")),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
    )
    private val rect = RectF()
    private val clipPath = Path()
    private val clipMatrix = Matrix()

    init {
        topRegion.moveTo(-10f, -10f)
        topRegion.lineTo(80f, -10f)
        topRegion.lineTo(80f, zigY[8])
        bottomRegion.moveTo(-10f, 100f)
        bottomRegion.lineTo(80f, 100f)
        bottomRegion.lineTo(80f, zigY[8])
        for (i in 8 downTo 0) {
            topRegion.lineTo(zigX[i], zigY[i])
            bottomRegion.lineTo(zigX[i], zigY[i])
        }
        topRegion.lineTo(-10f, zigY[0])
        topRegion.close()
        bottomRegion.lineTo(-10f, zigY[0])
        bottomRegion.close()

        zigPath.moveTo(zigX[0], zigY[0])
        for (i in 1..8) zigPath.lineTo(zigX[i], zigY[i])
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastTick) / 1000f).coerceIn(0f, 0.05f)
            lastTick = now
            step(dt)
            invalidate()
            postOnAnimation(this)
        }
    }

    // =====================================================================
    // Control
    // =====================================================================

    fun start(startMillis: Long, durationMillis: Long, animateIn: Boolean) {
        startMs = startMillis
        durationMs = max(1000L, durationMillis)
        appear = if (animateIn) 0f else 1f
        hatching = false
        hatchT = 0f
        babyHappyShown = false
        baby?.visibility = View.INVISIBLE
        running = true
        lastTick = SystemClock.uptimeMillis()
        removeCallbacks(ticker)
        postOnAnimation(ticker)
    }

    fun stop() {
        running = false
        removeCallbacks(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    /** Egg-shaped clip so the baby is only visible inside the shell while hatching. */
    fun babyClip(): Path? {
        if (!hatching || hatchT >= 0.78f) return null
        val s = eggScale()
        clipMatrix.reset()
        clipMatrix.postTranslate(-35f, -90f)
        clipMatrix.postScale(s, s)
        clipMatrix.postTranslate(width / 2f, height - 3f * d)
        clipPath.reset()
        eggPath.transform(clipMatrix, clipPath)
        return clipPath
    }

    // =====================================================================
    // Simulation
    // =====================================================================

    private fun ease(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun step(dt: Float) {
        clock += dt
        if (appear < 1f) appear = min(1f, appear + dt / 0.5f)

        if (!hatching) {
            progress = ((System.currentTimeMillis() - startMs).toFloat() / durationMs).coerceIn(0f, 1f)

            // Slow wobble bursts that become more frequent as the egg gets closer to hatching
            if (wobbleT < 0f) {
                wobbleTimer -= dt
                if (wobbleTimer <= 0f) {
                    wobbleT = 0f
                    wobbleTimer = (14f - 10f * progress) * (0.7f + 0.6f * Random.nextFloat())
                }
            } else {
                wobbleT += dt / 0.9f
                if (wobbleT >= 1f) wobbleT = -1f
            }

            if (progress >= 1f) {
                hatching = true
                hatchT = 0f
                baby?.visibility = View.VISIBLE
                baby?.translationY = HIDE_OFFSET_DP * d
            }
        } else {
            hatchT = min(1f, hatchT + dt / 8f)
            val e = ease((hatchT - 0.1f) / 0.65f)
            baby?.translationY = (1f - e) * HIDE_OFFSET_DP * d
            if (hatchT >= 0.8f && !babyHappyShown) {
                babyHappyShown = true
                baby?.showFed()
            }
            if (hatchT >= 1f) {
                running = false
                post { listener?.onHatched() }
            }
        }
    }

    // =====================================================================
    // Drawing
    // =====================================================================

    private fun eggScale(): Float = min(width / 70f, height / 90f) * 0.95f

    override fun onDraw(canvas: Canvas) {
        val a = appear
        val popScale = if (a >= 1f) 1f else {
            val t = a - 1f
            1f + 2.70158f * t * t * t + 1.70158f * t * t
        }
        val s = eggScale() * max(0.01f, popScale)

        var angle = 0f
        if (!hatching && wobbleT >= 0f) {
            angle = sin(wobbleT * 6.2831855f * 2f) * (2.5f + 5f * progress) * (1f - wobbleT)
        }

        canvas.save()
        canvas.translate(width / 2f, height - 3f * d)
        canvas.scale(s, s)
        canvas.translate(-35f, -90f)
        canvas.rotate(angle, 35f, 88f)
        if (!hatching) drawWhole(canvas) else drawHatching(canvas)
        canvas.restore()
    }

    private fun drawSurface(c: Canvas, alpha: Float) {
        fillPaint.style = Paint.Style.FILL
        fillPaint.shader = eggShader
        fillPaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        c.drawPath(eggPath, fillPaint)
        fillPaint.shader = null

        fillPaint.color = tint
        fillPaint.alpha = (200f * alpha).toInt().coerceIn(0, 255)
        var i = 0
        while (i < spots.size) {
            val r = spots[i + 2]
            rect.set(spots[i] - r, spots[i + 1] - r * 0.8f, spots[i] + r, spots[i + 1] + r * 0.8f)
            c.drawOval(rect, fillPaint)
            i += 3
        }

        linePaint.color = Color.parseColor("#B8935A")
        linePaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        linePaint.strokeWidth = 1.6f
        c.drawPath(eggPath, linePaint)
    }

    private fun drawWhole(c: Canvas) {
        drawSurface(c, 1f)

        // Cracks appear gradually: segment by segment, the last one growing smoothly
        val total = cracks.size / 4
        val reveal = progress * total
        linePaint.color = Color.parseColor("#7A5A30")
        linePaint.alpha = 255
        linePaint.strokeWidth = 1.7f
        for (n in 0 until total) {
            val f = (reveal - n).coerceIn(0f, 1f)
            if (f <= 0f) break
            val x1 = cracks[n * 4]
            val y1 = cracks[n * 4 + 1]
            val x2 = cracks[n * 4 + 2]
            val y2 = cracks[n * 4 + 3]
            c.drawLine(x1, y1, x1 + (x2 - x1) * f, y1 + (y2 - y1) * f, linePaint)
        }
    }

    private fun drawHatching(c: Canvas) {
        val t = hatchT

        // Bottom shell stays, then fades away at the end revealing the whole baby
        val bottomAlpha = 1f - ((t - 0.78f) / 0.22f).coerceIn(0f, 1f)
        if (bottomAlpha > 0f) {
            val s = c.save()
            c.clipPath(bottomRegion)
            drawSurface(c, bottomAlpha)
            c.restoreToCount(s)
            drawEdge(c, bottomAlpha)
        }

        // Top shell lifts, tilts and fades
        val lift = ease(t / 0.7f)
        val topAlpha = 1f - ((t - 0.3f) / 0.5f).coerceIn(0f, 1f)
        if (topAlpha > 0f) {
            val s = c.save()
            c.translate(0f, -lift * 58f)
            c.rotate(lift * 28f, 52f, 46f)
            c.clipPath(topRegion)
            drawSurface(c, topAlpha)
            c.restoreToCount(s)
            val s2 = c.save()
            c.translate(0f, -lift * 58f)
            c.rotate(lift * 28f, 52f, 46f)
            drawEdge(c, topAlpha)
            c.restoreToCount(s2)
        }
    }

    private fun drawEdge(c: Canvas, alpha: Float) {
        linePaint.color = Color.parseColor("#7A5A30")
        linePaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
        linePaint.strokeWidth = 1.7f
        c.drawPath(zigPath, linePaint)
    }

    companion object {
        /** Baby starts this far below its final position, hidden behind the shell. */
        const val HIDE_OFFSET_DP = 32f
    }
}
