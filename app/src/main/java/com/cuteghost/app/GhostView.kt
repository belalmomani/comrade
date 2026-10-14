package com.cuteghost.app

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
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cute 3D-style ghost drawn with Canvas on a fully transparent background.
 * Expressions are part of the drawing itself (eyes, lids, mouth, tongue, blush)
 * and blend smoothly between moods. Logical drawing box: 100 x 128.
 */
class GhostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Order must match PRESETS
    private enum class Mood { HAPPY, SURPRISED, SLEEPY, SAD, MISCHIEVOUS, ANGRY, RELAXED, SLEEPING }
    private enum class TapAnim { HOP, HIGH_JUMP, DOUBLE_HOP, WIGGLE_HOP, LEAP, STARTLE, SHAKE }

    companion object {
        const val LOGICAL_W = 100f
        const val LOGICAL_H = 128f
        private const val GHOST_OFFSET_Y = 28f
        private const val SLEEPY_AFTER = 30f
        private const val SLEEP_AFTER = 40f
        private const val TWO_PI = 6.2831855f
        private const val PI_F = 3.1415927f
        private const val MAX_DRAG_TILT = 14f

        // Face parameter layout:
        // 0 eyeRx, 1 eyeRy, 2 lidInL, 3 lidOutL, 4 lidInR, 5 lidOutR,
        // 6 mouthXL, 7 mouthXR, 8 mouthYL, 9 mouthYR, 10 ctrlTop, 11 ctrlBottom,
        // 12 tongue, 13 blush, 14 eyesClosed, 15 lowerLid (smiling eyes), 16 lookX
        private const val N = 17

        private val PRESETS = arrayOf(
            // HAPPY: clear smile, smiling (lifted lower lid) eyes
            floatArrayOf(7.5f, 9f, 0f, 0f, 0f, 0f, 43f, 57f, 63.5f, 63.5f, 66f, 74f, 0f, 1f, 0f, 0.28f, 0f),
            // SURPRISED: wide eyes, slightly open mouth
            floatArrayOf(8.7f, 11.5f, 0f, 0f, 0f, 0f, 46.5f, 53.5f, 67f, 67f, 62f, 74.5f, 0f, 1f, 0f, 0f, 0f),
            // SLEEPY (lids are driven dynamically)
            floatArrayOf(7.5f, 9f, 0.5f, 0.5f, 0.5f, 0.5f, 46f, 54f, 66f, 66f, 66.5f, 69.5f, 0f, 0.8f, 0f, 0f, 0f),
            // SAD: drooping outer lids, small frown
            floatArrayOf(7.5f, 9.5f, 0f, 0.55f, 0f, 0.55f, 43.5f, 56.5f, 67f, 67f, 60.5f, 63.5f, 0f, 0.6f, 0f, 0f, 0f),
            // MISCHIEVOUS: side glance, lopsided grin with tongue
            floatArrayOf(7.5f, 9f, 0.3f, 0f, 0.45f, 0.15f, 42f, 58f, 64.5f, 61.5f, 63.5f, 76f, 1f, 1f, 0f, 0.15f, 2.8f),
            // ANGRY (mild): narrowed eyes, small pout
            floatArrayOf(7.5f, 8f, 0.5f, 0f, 0.5f, 0f, 44.5f, 55.5f, 67.5f, 67.5f, 63.8f, 66.4f, 0f, 0.9f, 0f, 0.12f, 0f),
            // RELAXED: calm face, gentle smile (the natural expression)
            floatArrayOf(7.5f, 8.6f, 0.12f, 0.12f, 0.12f, 0.12f, 45f, 55f, 64.5f, 64.5f, 65.5f, 69.5f, 0f, 0.9f, 0f, 0f, 0f),
            // SLEEPING: eyes closed
            floatArrayOf(7.5f, 9f, 0f, 0f, 0f, 0f, 46f, 54f, 66f, 66f, 66.5f, 69.5f, 0f, 0.8f, 1f, 0f, 0f)
        )
    }

    private val happyAnims = listOf(
        TapAnim.HOP, TapAnim.HIGH_JUMP, TapAnim.DOUBLE_HOP, TapAnim.WIGGLE_HOP
    )

    // ----- Face state -----
    private val cur = FloatArray(N)
    private val tgt = FloatArray(N)
    private var mood = Mood.RELAXED
    private var overrideMood: Mood? = null
    private var overrideTimer = 0f
    private var momentTimer = Random.nextFloat() * 6f + 8f

    // ----- Time / idle -----
    private var time = 0f
    private var idle = 0f
    private var sleepAmt = 0f
    private var lastFrame = 0L

    // ----- Blink -----
    private var blinkT = -1f
    private var blinkOpen = 1f
    private var nextBlink = 3f
    private var doubleBlinkPending = false
    private var blinkWasSecond = false

    // ----- Float -----
    private var floatPhase = 0f
    private var floatY = 0f
    private var idleTilt = 0f
    private var breath = 1f

    // ----- Tap animation (only one at a time) -----
    private var tapAnim: TapAnim? = null
    private var lastTapAnim: TapAnim? = null
    private var lastReaction: Mood? = null
    private var tapT = 0f
    private var tapDur = 0.6f
    private var tapJitter = 1f
    private var tapDir = 1f
    private var jumpY = 0f
    private var jumpSq = 0f
    private var tapRot = 0f
    private var tapX = 0f
    private var tapStreak = 0
    private var lastTapAt = 0L
    private var wokeByTouch = false

    // ----- Drag -----
    private var dragging = false
    private var pendingDx = 0f
    private var dragVel = 0f
    private var dragTilt = 0f
    private var tiltVel = 0f

    init {
        System.arraycopy(PRESETS[Mood.RELAXED.ordinal], 0, cur, 0, N)
        System.arraycopy(PRESETS[Mood.RELAXED.ordinal], 0, tgt, 0, N)
    }

    private val frameAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastFrame) / 1000f).coerceIn(0f, 0.05f)
            lastFrame = now
            step(dt)
            invalidate()
        }
    }

    // ----- Shapes and paints (logical coordinates) -----

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
        color = Color.parseColor("#FF8FAB")
    }

    private val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF7A9A")
    }

    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8F86D0")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val rect = RectF()
    private val tmpPath = Path()

    // =====================================================================
    // Public API (main thread)
    // =====================================================================

    /** Finger touched the ghost (before we know whether it is a tap or a drag). */
    fun onTouchStart() {
        wokeByTouch = idle >= SLEEPY_AFTER
        idle = 0f
    }

    /** A quick tap: picks a reaction (happy / surprised / mischievous) with a short move. */
    fun react() {
        val now = SystemClock.uptimeMillis()
        tapStreak = if (now - lastTapAt < 1200L) tapStreak + 1 else 1
        lastTapAt = now
        idle = 0f

        if (wokeByTouch) {
            wokeByTouch = false
            startTapAnim(TapAnim.STARTLE)
            setOverride(Mood.SURPRISED, 0.9f)
            return
        }

        // Never pile animations up: ignore taps while the current move is still early
        if (tapAnim != null && tapT / tapDur < 0.7f) return

        val (reaction, anim) = pickReaction()
        lastReaction = reaction
        lastTapAnim = anim
        startTapAnim(anim)
        setOverride(
            reaction,
            when (reaction) {
                Mood.SURPRISED -> 1.2f
                Mood.MISCHIEVOUS -> 2.0f
                Mood.ANGRY -> 1.6f
                else -> 1.5f
            }
        )
    }

    fun onDragStart() {
        dragging = true
        wokeByTouch = false
        idle = 0f
        tapAnim = null
        pendingDx = 0f
        dragVel = 0f
    }

    /** Pixel movement since the previous call. */
    fun onDragMove(dxPx: Float, @Suppress("UNUSED_PARAMETER") dyPx: Float) {
        pendingDx += dxPx / resources.displayMetrics.density
        idle = 0f
    }

    fun onDragEnd() {
        dragging = false
        idle = 0f
        setOverride(Mood.HAPPY, 1.0f)
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = SystemClock.uptimeMillis()
        if (!frameAnimator.isStarted) frameAnimator.start()
    }

    override fun onDetachedFromWindow() {
        frameAnimator.cancel()
        super.onDetachedFromWindow()
    }

    // =====================================================================
    // Simulation
    // =====================================================================

    private fun setOverride(m: Mood, seconds: Float) {
        overrideMood = m
        overrideTimer = seconds
    }

    private fun pickReaction(): Pair<Mood, TapAnim> {
        // Rapid tapping annoys the ghost (mild, cute anger)
        if (tapStreak >= 5) return Pair(Mood.ANGRY, TapAnim.SHAKE)

        val options = listOf(Mood.HAPPY, Mood.SURPRISED, Mood.MISCHIEVOUS)
            .filter { it != lastReaction }
        val reaction = options.random()
        val anim = when (reaction) {
            Mood.SURPRISED -> TapAnim.STARTLE
            Mood.MISCHIEVOUS -> TapAnim.LEAP
            else -> happyAnims.filter { it != lastTapAnim }.random()
        }
        return Pair(reaction, anim)
    }

    private fun step(dt: Float) {
        time += dt
        idle += dt

        updateMood(dt)

        val k = 1f - exp(-12f * dt)
        for (i in 0 until N) cur[i] += (tgt[i] - cur[i]) * k

        updateBlink(dt)

        val asleepTarget = when (mood) {
            Mood.SLEEPING -> 1f
            Mood.SLEEPY -> 0.35f
            else -> 0f
        }
        sleepAmt += (asleepTarget - sleepAmt) * (1f - exp(-1.5f * dt))

        // Continuous float: calmer and slower when drowsy
        floatPhase += dt * TWO_PI / (2.8f + 1.4f * sleepAmt)
        if (floatPhase > TWO_PI) floatPhase -= TWO_PI
        floatY = sin(floatPhase) * (3.2f - 1.7f * sleepAmt)
        idleTilt = sin(floatPhase + 0.8f) * 1.8f * (1f - 0.6f * sleepAmt)
        breath = 1f + 0.018f * sleepAmt * sin(time * TWO_PI / 3.8f)

        updateTapAnim(dt)
        updateDragTilt(dt)
    }

    private fun updateMood(dt: Float) {
        if (overrideMood != null) {
            overrideTimer -= dt
            if (overrideTimer <= 0f) overrideMood = null
        }

        val override = overrideMood
        val m = when {
            dragging -> Mood.SURPRISED
            override != null -> override
            idle >= SLEEP_AFTER -> Mood.SLEEPING
            idle >= SLEEPY_AFTER -> Mood.SLEEPY
            else -> Mood.RELAXED
        }
        if (m != mood) {
            mood = m
            System.arraycopy(PRESETS[m.ordinal], 0, tgt, 0, N)
        }

        // Drowsiness: eyes close gradually before falling asleep
        if (mood == Mood.SLEEPY) {
            val p = ((idle - SLEEPY_AFTER) / (SLEEP_AFTER - SLEEPY_AFTER)).coerceIn(0f, 1f)
            val lid = 0.3f + 0.5f * p
            tgt[2] = lid
            tgt[3] = lid
            tgt[4] = lid
            tgt[5] = lid
        }

        // Occasional spontaneous expression while awake
        if (!dragging && overrideMood == null && idle < SLEEPY_AFTER) {
            momentTimer -= dt
            if (momentTimer <= 0f) {
                momentTimer = Random.nextFloat() * 9f + 9f
                when (Random.nextInt(3)) {
                    0 -> setOverride(Mood.SAD, 2.8f)
                    1 -> setOverride(Mood.MISCHIEVOUS, 2.2f)
                    else -> setOverride(Mood.HAPPY, 2.2f)
                }
            }
        }
    }

    private fun updateBlink(dt: Float) {
        if (mood == Mood.SLEEPING) {
            blinkT = -1f
            blinkOpen = 1f
            return
        }
        if (blinkT >= 0f) {
            blinkT += dt / 0.17f
            if (blinkT >= 1f) {
                blinkT = -1f
                blinkOpen = 1f
                if (doubleBlinkPending) {
                    doubleBlinkPending = false
                    blinkWasSecond = true
                    nextBlink = 0.12f
                } else {
                    blinkWasSecond = false
                    nextBlink = Random.nextFloat() * 4f + 2.5f
                }
            } else {
                blinkOpen = abs(1f - 2f * blinkT)
            }
        } else {
            blinkOpen = 1f
            nextBlink -= dt
            if (nextBlink <= 0f) {
                blinkT = 0f
                doubleBlinkPending = !blinkWasSecond && Random.nextFloat() < 0.2f
            }
        }
    }

    private fun startTapAnim(type: TapAnim) {
        tapAnim = type
        tapT = 0f
        tapDur = when (type) {
            TapAnim.HOP -> 0.62f
            TapAnim.HIGH_JUMP -> 0.85f
            TapAnim.DOUBLE_HOP -> 1.05f
            TapAnim.WIGGLE_HOP -> 0.85f
            TapAnim.LEAP -> 0.8f
            TapAnim.STARTLE -> 0.5f
            TapAnim.SHAKE -> 0.55f
        }
        tapJitter = 0.85f + Random.nextFloat() * 0.3f
        tapDir = if (Random.nextBoolean()) 1f else -1f
    }

    private fun updateTapAnim(dt: Float) {
        jumpY = 0f
        jumpSq = 0f
        tapRot = 0f
        tapX = 0f
        val type = tapAnim ?: return

        tapT += dt
        val u = tapT / tapDur
        if (u >= 1f) {
            tapAnim = null
            return
        }
        val j = tapJitter
        when (type) {
            TapAnim.HOP -> hop(u, 13f * j)
            TapAnim.HIGH_JUMP -> hop(u, 24f * j)
            TapAnim.DOUBLE_HOP ->
                if (u < 0.5f) hop(u * 2f, 11f * j) else hop((u - 0.5f) * 2f, 8f * j)
            TapAnim.WIGGLE_HOP -> {
                hop(u, 13f * j)
                tapRot = sin(u * PI_F * 5f) * 9f * (1f - u)
            }
            TapAnim.LEAP -> {
                hop(u, 19f * j)
                tapRot = tapDir * sin(PI_F * u) * 9f
                tapX = tapDir * sin(PI_F * u) * 6f
            }
            TapAnim.STARTLE -> hop(u, 10f)
            TapAnim.SHAKE -> {
                val decay = 1f - u
                tapRot = sin(u * PI_F * 6f) * 8f * decay
                tapX = sin(u * PI_F * 6f) * 2f * decay
            }
        }
    }

    /** One hop: crouch, stretch in the air, squash on landing. */
    private fun hop(u: Float, h: Float) {
        val x = u.coerceIn(0f, 1f)
        when {
            x < 0.12f -> jumpSq = 0.12f * (x / 0.12f)
            x > 0.88f -> jumpSq = 0.14f * sin(PI_F * (x - 0.88f) / 0.12f)
            else -> {
                val v = (x - 0.12f) / 0.76f
                jumpY = -h * 4f * v * (1f - v)
                jumpSq = -0.07f * sin(PI_F * v)
            }
        }
    }

    private fun updateDragTilt(dt: Float) {
        if (dragging && dt > 0.0001f) {
            val v = pendingDx / dt
            dragVel += (v - dragVel) * 0.3f
        } else if (!dragging) {
            dragVel = 0f
        }
        pendingDx = 0f

        val target = if (dragging) (dragVel * 0.012f).coerceIn(-MAX_DRAG_TILT, MAX_DRAG_TILT) else 0f
        val sdt = min(dt, 0.032f)
        tiltVel += (-120f * (dragTilt - target) - 9f * tiltVel) * sdt
        dragTilt += tiltVel * sdt
    }

    // =====================================================================
    // Drawing
    // =====================================================================

    override fun onDraw(canvas: Canvas) {
        val s = min(width / LOGICAL_W, height / LOGICAL_H)
        canvas.save()
        canvas.translate((width - LOGICAL_W * s) / 2f, (height - LOGICAL_H * s) / 2f)
        canvas.scale(s, s)
        canvas.translate(tapX, GHOST_OFFSET_Y + floatY + jumpY)
        canvas.rotate(idleTilt + dragTilt + tapRot, 50f, 90f)
        canvas.scale(1f + jumpSq, (1f - jumpSq) * breath, 50f, 92f)
        drawGhost(canvas)
        canvas.restore()
    }

    private fun drawGhost(c: Canvas) {
        c.drawPath(body, bodyPaint)
        c.drawPath(body, shadePaint)
        c.drawCircle(30f, 28f, 15f, highlightPaint)

        // Eyes
        val rx = cur[0]
        val ry = cur[1]
        val closed = cur[14]
        val open = if (mood == Mood.SLEEPING) 1f else blinkOpen
        val ryEff = max(0.7f, ry * open * (1f - 0.93f * closed))
        val look = cur[16]
        drawEye(c, 36f + look, 50f, rx, ry, ryEff, cur[2], cur[3], true)
        drawEye(c, 64f + look, 50f, rx, ry, ryEff, cur[4], cur[5], false)

        // Blush (drawn after the eyes so lids never cut it)
        blushPaint.alpha = (85f * cur[13]).toInt().coerceIn(0, 255)
        rect.set(17f, 56f, 31f, 64f)
        c.drawOval(rect, blushPaint)
        rect.set(69f, 56f, 83f, 64f)
        c.drawOval(rect, blushPaint)

        drawMouth(c)

        if (sleepAmt > 0.5f) drawZzz(c)
    }

    private fun drawEye(
        c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, ryEff: Float,
        lidIn: Float, lidOut: Float, isLeft: Boolean
    ) {
        rect.set(cx - rx, cy - ryEff, cx + rx, cy + ryEff)
        c.drawOval(rect, darkPaint)

        if (ryEff > ry * 0.55f) {
            c.drawCircle(cx + rx * 0.35f, cy - ryEff * 0.4f, rx * 0.38f, glintPaint)
            c.drawCircle(cx - rx * 0.4f, cy + ryEff * 0.4f, rx * 0.16f, glintPaint)
        }

        // Upper eyelid: body-colored polygon over the top of the eye
        if (lidIn > 0.01f || lidOut > 0.01f) {
            val top = cy - ryEff
            val h = 2f * ryEff
            val outerX = if (isLeft) cx - rx - 1.5f else cx + rx + 1.5f
            val innerX = if (isLeft) cx + rx + 1.5f else cx - rx - 1.5f
            tmpPath.reset()
            tmpPath.moveTo(outerX, top + lidOut * h)
            tmpPath.lineTo(innerX, top + lidIn * h)
            tmpPath.lineTo(innerX, top - 3f)
            tmpPath.lineTo(outerX, top - 3f)
            tmpPath.close()
            c.drawPath(tmpPath, bodyPaint)
        }

        // Lower lid pushed up by the cheeks: gives smiling eyes
        val lb = cur[15]
        if (lb > 0.01f) {
            val bottom = cy + ryEff
            val edge = bottom - lb * 2f * ryEff
            tmpPath.reset()
            tmpPath.moveTo(cx - rx - 2f, bottom + 2f)
            tmpPath.lineTo(cx - rx - 2f, edge)
            tmpPath.quadTo(cx, edge - 14f * lb, cx + rx + 2f, edge)
            tmpPath.lineTo(cx + rx + 2f, bottom + 2f)
            tmpPath.close()
            c.drawPath(tmpPath, bodyPaint)
        }
    }

    private fun drawMouth(c: Canvas) {
        val xl = cur[6]
        val xr = cur[7]
        val yl = cur[8]
        val yr = cur[9]
        val ct = cur[10]
        val cb = cur[11]
        val cx = (xl + xr) / 2f

        tmpPath.reset()
        tmpPath.moveTo(xl, yl)
        tmpPath.quadTo(cx, ct, xr, yr)
        tmpPath.quadTo(cx, cb, xl, yl)
        tmpPath.close()
        c.drawPath(tmpPath, darkPaint)

        val tongue = cur[12]
        if (tongue > 0.02f) {
            val bottomY = 0.25f * yl + 0.5f * cb + 0.25f * yr
            tonguePaint.alpha = (255f * tongue).toInt().coerceIn(0, 255)
            rect.set(
                cx + 1.5f - 3.2f * tongue, bottomY - 2.0f - 2.3f * tongue,
                cx + 1.5f + 3.2f * tongue, bottomY - 2.0f + 2.3f * tongue
            )
            c.drawOval(rect, tonguePaint)
        }
    }

    private fun drawZzz(c: Canvas) {
        val amount = ((sleepAmt - 0.5f) * 2f).coerceIn(0f, 1f)
        for (i in 0 until 3) {
            val p = (time * 0.3f + i / 3f) % 1f
            val size = 3.5f + p * 3f
            val x = 70f + p * 14f
            val y = 14f - p * 24f
            zPaint.alpha = (sin(PI_F * p) * amount * 190f).toInt().coerceIn(0, 255)
            tmpPath.reset()
            tmpPath.moveTo(x, y)
            tmpPath.lineTo(x + size, y)
            tmpPath.lineTo(x, y + size)
            tmpPath.lineTo(x + size, y + size)
            c.drawPath(tmpPath, zPaint)
        }
    }
}
