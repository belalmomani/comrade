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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cute 3D-style ghost drawn with Canvas on a fully transparent background.
 * Face (eyes, brows, mouth, tongue, blush) and body motion are drawn together and
 * every mood has its own matching motion. Logical drawing box: 100 x 128.
 */
class GhostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Conversation state driven by the voice companion. */
    enum class VoiceState { IDLE, LISTENING, THINKING, SPEAKING }

    /** Feeding game state driven by the service / feeding layer. */
    enum class FeedState { NONE, HUNGRY, EATING, THIRSTY, DRINKING }

    // Order must match PRESETS
    private enum class Mood {
        HAPPY, SURPRISED, SLEEPY, SAD, MISCHIEVOUS, ANGRY, RELAXED, SLEEPING,
        LISTENING, THINKING, TALKING, HUNGRY, EATING, THIRSTY, DRINKING, RELIEVED,
        CRYING, SICK, LAUGHING, SHY, CURIOUS
    }
    private enum class TapAnim {
        HOP, DOUBLE_HOP, WIGGLE_HOP, REBOUND, LEAN, SHAKE,
        SPIN, TRIPLE_BOUNCE, DANCE, PEEK, // baby play
        HIDE, LAUGH, HEAD_PAT
    }

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
        //  0 eyeRx   1 eyeRy   2 lidInL  3 lidOutL  4 lidInR  5 lidOutR
        //  6 mouthXL 7 mouthXR 8 mouthYL 9 mouthYR 10 ctrlTop 11 ctrlBottom
        // 12 tongue 13 blush  14 eyesClosed 15 lowerLid 16 lookX
        // 17 browAlpha 18 browInYL 19 browOutYL 20 browInYR 21 browOutYR 22 browCurve
        // 23 glintScale 24 lookY
        private const val N = 25

        private val PRESETS = arrayOf(
            // HAPPY: wide smile, smiling eyes, soft raised brows
            floatArrayOf(
                7.5f, 9f, 0f, 0f, 0f, 0f,
                41.5f, 58.5f, 63f, 63f, 65.5f, 76f,
                0f, 1f, 0f, 0.34f, 0f,
                0.55f, 34f, 34.5f, 34f, 34.5f, 2.2f,
                1.15f, 0f
            ),
            // SURPRISED: big eyes, high brows, small round mouth
            floatArrayOf(
                8.9f, 12f, 0f, 0f, 0f, 0f,
                46.5f, 53.5f, 67f, 67f, 62f, 74.5f,
                0f, 1f, 0f, 0f, 0f,
                0.85f, 29f, 29.5f, 29f, 29.5f, 2.5f,
                1.3f, 0f
            ),
            // SLEEPY: heavy lids (driven dynamically), low relaxed brows
            floatArrayOf(
                7.5f, 9f, 0.5f, 0.5f, 0.5f, 0.5f,
                46f, 54f, 66f, 66f, 66.5f, 69.5f,
                0f, 0.8f, 0f, 0f, 0f,
                0.35f, 37f, 37.8f, 37f, 37.8f, 0.8f,
                0.8f, 1f
            ),
            // SAD: big watery eyes, drooping outer lids, brows slanting up in the middle, frown
            floatArrayOf(
                8f, 10f, 0f, 0.55f, 0f, 0.55f,
                43.5f, 56.5f, 67f, 67f, 60.5f, 63.5f,
                0f, 0.6f, 0f, 0f, 0f,
                0.8f, 33.5f, 38f, 33.5f, 38f, 0.5f,
                1.45f, 1f
            ),
            // MISCHIEVOUS: half lids, one brow raised, lopsided grin with tongue, side glance
            floatArrayOf(
                7.5f, 9f, 0.3f, 0f, 0.45f, 0.15f,
                42f, 58f, 64.5f, 61.5f, 63.5f, 76f,
                1f, 1f, 0f, 0.15f, 2.8f,
                0.85f, 36f, 35f, 31.5f, 30.5f, 1.8f,
                1f, 0f
            ),
            // ANGRY (mild): narrow eyes, slanted lids and brows, small pout
            floatArrayOf(
                7.5f, 8f, 0.5f, 0f, 0.5f, 0f,
                44.5f, 55.5f, 67.5f, 67.5f, 63.8f, 66.4f,
                0f, 0.9f, 0f, 0.12f, 0f,
                0.9f, 36f, 32.5f, 36f, 32.5f, 0f,
                0.8f, 0f
            ),
            // RELAXED: the natural face, no brows, gentle smile
            floatArrayOf(
                7.5f, 8.6f, 0.12f, 0.12f, 0.12f, 0.12f,
                45f, 55f, 64.5f, 64.5f, 65.5f, 69.5f,
                0f, 0.9f, 0f, 0f, 0f,
                0f, 34.5f, 35f, 34.5f, 35f, 1.5f,
                1f, 0f
            ),
            // SLEEPING: eyes closed
            floatArrayOf(
                7.5f, 9f, 0f, 0f, 0f, 0f,
                46f, 54f, 66f, 66f, 66.5f, 69.5f,
                0f, 0.8f, 1f, 0f, 0f,
                0f, 37f, 37.8f, 37f, 37.8f, 0.8f,
                1f, 1f
            ),
            // LISTENING: attentive, bigger eyes, raised soft brows, gentle smile
            floatArrayOf(
                8.3f, 10.2f, 0f, 0f, 0f, 0f,
                45f, 55f, 64.5f, 64.5f, 66f, 71f,
                0f, 1f, 0f, 0.1f, 0f,
                0.6f, 32f, 32.8f, 32f, 32.8f, 2f,
                1.25f, -0.5f
            ),
            // THINKING: eyes up and aside, one brow raised, small "hmm" mouth
            floatArrayOf(
                7.5f, 9f, 0.1f, 0.1f, 0f, 0f,
                47f, 53f, 66.5f, 66f, 65.5f, 69f,
                0f, 0.9f, 0f, 0f, 2.4f,
                0.7f, 35.5f, 35.5f, 31.5f, 31f, 1.5f,
                1f, -2.2f
            ),
            // TALKING: happy face; the mouth is animated on top while speaking
            floatArrayOf(
                7.5f, 9f, 0f, 0f, 0f, 0f,
                44f, 56f, 64f, 64f, 65f, 72f,
                0f, 1f, 0f, 0.2f, 0f,
                0.35f, 34f, 34.5f, 34f, 34.5f, 2f,
                1.15f, 0f
            ),
            // HUNGRY: big pleading eyes looking at the tray, brows up in the middle, small open mouth
            floatArrayOf(
                8.3f, 10.4f, 0f, 0.25f, 0f, 0.25f,
                46.5f, 53.5f, 67f, 67f, 62.5f, 74f,
                0f, 0.8f, 0f, 0f, 0f,
                0.8f, 33f, 36.5f, 33f, 36.5f, 0.6f,
                1.5f, 1.5f
            ),
            // EATING: happy squinting eyes, soft brows; the mouth chews (animated on top)
            floatArrayOf(
                7.5f, 9f, 0f, 0f, 0f, 0f,
                45f, 55f, 64.5f, 64.5f, 65.5f, 70f,
                0f, 1f, 0f, 0.5f, 0f,
                0.45f, 34f, 34.5f, 34f, 34.5f, 2f,
                1.15f, 0f
            ),
            // THIRSTY: pleading eyes, small open mouth with the tongue showing a little
            floatArrayOf(
                8f, 10f, 0f, 0.2f, 0f, 0.2f,
                45.5f, 54.5f, 66.5f, 66.5f, 64f, 73f,
                0.55f, 0.7f, 0f, 0f, 0f,
                0.75f, 33.5f, 36f, 33.5f, 36f, 0.6f,
                1.4f, 1.5f
            ),
            // DRINKING: relaxed half-closed eyes, small mouth that gulps (animated on top)
            floatArrayOf(
                7.5f, 9f, 0.3f, 0.3f, 0.3f, 0.3f,
                46f, 54f, 65.5f, 65.5f, 63.5f, 71f,
                0f, 1f, 0f, 0.3f, 0f,
                0.35f, 34.5f, 35f, 34.5f, 35f, 1.5f,
                1f, 0f
            ),
            // RELIEVED: happy and relaxed after drinking, soft closed-ish eyes, wide smile
            floatArrayOf(
                7.5f, 9f, 0.35f, 0.35f, 0.35f, 0.35f,
                42f, 58f, 64f, 64f, 65.5f, 75.5f,
                0f, 1f, 0f, 0.4f, 0f,
                0.3f, 34.5f, 35f, 34.5f, 35f, 2f,
                0.9f, 0f
            ),
            // CRYING: squeezed wet eyes, wide wobbling mouth, sad brows
            floatArrayOf(
                7.5f, 8f, 0f, 0.5f, 0f, 0.5f,
                40f, 60f, 66f, 66f, 60.5f, 79f,
                0f, 0.9f, 0.55f, 0f, 0f,
                0.95f, 33f, 37.5f, 33f, 37.5f, 0.5f,
                1.3f, 0.5f
            ),
            // SICK: tired droopy eyes, pale cheeks, wavy frown
            floatArrayOf(
                7.5f, 8.4f, 0.4f, 0.4f, 0.4f, 0.4f,
                43.5f, 56.5f, 68f, 68f, 62.5f, 65.5f,
                0f, 0.3f, 0f, 0f, 0f,
                0.85f, 33f, 36.5f, 33f, 36.5f, 0.5f,
                0.7f, 1f
            ),
            // LAUGHING: happy closed eyes, wide laughing mouth (animated on top)
            floatArrayOf(
                7.5f, 9f, 0f, 0f, 0f, 0f,
                40f, 60f, 63f, 63f, 61f, 80f,
                0.4f, 1f, 0.35f, 0.6f, 0f,
                0.5f, 33.5f, 34f, 33.5f, 34f, 2.5f,
                1f, 0f
            ),
            // SHY: lowered eyes, rosy cheeks, tiny smile
            floatArrayOf(
                7.5f, 8.4f, 0.12f, 0.12f, 0.12f, 0.12f,
                46f, 54f, 65f, 65f, 65.5f, 69f,
                0f, 1f, 0f, 0.25f, 0f,
                0.5f, 33f, 35.5f, 33f, 35.5f, 1f,
                1f, 1.6f
            ),
            // CURIOUS: wide eyes, one brow high, small round mouth
            floatArrayOf(
                8.6f, 10.8f, 0f, 0f, 0f, 0f,
                47f, 53f, 67f, 67f, 62.5f, 71f,
                0f, 1f, 0f, 0f, 0f,
                0.8f, 35f, 35f, 30.5f, 30f, 1.5f,
                1.35f, -0.5f
            )
        )
    }

    private val happyAnims = listOf(TapAnim.HOP, TapAnim.DOUBLE_HOP, TapAnim.WIGGLE_HOP)
    private val playfulAnims = listOf(TapAnim.SPIN, TapAnim.TRIPLE_BOUNCE, TapAnim.PEEK, TapAnim.DANCE)

    // ----- Face state -----
    private val cur = FloatArray(N)
    private val tgt = FloatArray(N)
    private var mood = Mood.RELAXED
    private var overrideMood: Mood? = null
    private var overrideTimer = 0f

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

    // ----- Brow motion -----
    private var browKickPos = 0f
    private var browKickVel = 0f
    private var browTwitchT = -1f
    private var browTwitch = 0f

    // ----- Gaze -----
    private var glanceTimer = Random.nextFloat() * 8f + 8f
    private var glanceT = -1f
    private var glanceAutoDir = 1f
    private var glanceDir = 1f // direction of mischievous lean + glance
    private var mischT = 0f

    // ----- Body: float and mood-linked motion -----
    private var floatPhase = 0f
    private var floatY = 0f
    private var idleTilt = 0f
    private var breath = 1f
    private var sagAmt = 0f   // sad: body lowers slightly
    private var swayAmt = 0f  // sleepy: slow sway

    // ----- Neglect (tear, crying, sickness) and the baby play style -----
    /** Babies play differently from the big ghost: livelier float and other tap moves. */
    var playful = false
    private var neglect = 0
    private var cryPhase = 0f
    private var cryAmt = 0f
    private var sickAmt = 0f
    private var lastSickApplied = 0f
    private var spinX = 1f
    private var baseBodyColor: Int? = null

    // ----- Character, day and night, touch reactions -----
    /** Character of a baby ghost (null for the big ghost). */
    var personality: Personality? = null
    private var nightSleepy = false
    private var forcedSleep = false
    private var playedUntil = 0L
    private var curiousDir = 1f
    private var hideAmt = 0f
    private var heartsT = -1f
    private var laughPhase = 0f
    private var dragSpinPhase = 0f
    private var dragSpinX = 1f

    // ----- Outfit: hat, scarf, wings, glow, star trail -----
    private var outfitHat = 0
    private var outfitScarf = 0
    private var outfitWings = 0
    private var outfitGlow = 0
    private var outfitTrail = 0
    private val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val itemLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starX = FloatArray(10)
    private val starY = FloatArray(10)
    private val starAge = FloatArray(10) { -1f }
    private var starTimer = 0f
    private val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7FD1FF") }
    private val streamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7FD1FF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }

    // ----- Voice conversation -----
    private var voiceState = VoiceState.IDLE
    private var voiceLevelTarget = 0f
    private var voiceLevel = 0f
    private var voiceTilt = 0f
    private var listenDir = 1f
    private var talkOpen = 0f
    private var talkPhase = 0f

    // ----- Feeding game -----
    private var feedState = FeedState.NONE
    private var chewPhase = 0f
    private var gulpPhase = 0f

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

    private val browPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B3B4A")
        style = Paint.Style.STROKE
        strokeWidth = 1.9f
        strokeCap = Paint.Cap.ROUND
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

    /**
     * A quick tap. A tap makes the ghost jump (every baby in its own way); 3-4 quick taps make it
     * laugh or hide; 5 in a row make it cry. [head] is true when the head was touched.
     */
    fun react(head: Boolean = false) {
        // Taps do not interrupt feeding or watering, and a sleeping ghost is left alone
        if (feedState != FeedState.NONE || forcedSleep) {
            idle = 0f
            return
        }
        val now = SystemClock.uptimeMillis()
        tapStreak = if (now - lastTapAt < 1200L) tapStreak + 1 else 1
        lastTapAt = now
        idle = 0f
        playedUntil = now + 20_000L // it stays happy for a while after somebody played with it

        if (wokeByTouch) {
            wokeByTouch = false
            if (tapAnim == null) startTapAnim(TapAnim.REBOUND)
            setOverride(Mood.SURPRISED, 1.0f)
            return
        }

        // A new move never starts before the current one has finished
        if (tapAnim != null) return

        if (tapStreak >= 5) {
            // Too many taps in a row: it cries
            tapStreak = 0
            startTapAnim(TapAnim.SHAKE)
            setOverride(Mood.CRYING, 5f)
            return
        }
        if (head) {
            // Special move when the head is touched: it closes its eyes happily and little hearts rise
            startTapAnim(TapAnim.HEAD_PAT)
            heartsT = 0f
            setOverride(Mood.RELIEVED, 2.2f)
            return
        }
        if (tapStreak >= 3) {
            // Quick repeated taps: it laughs or hides
            val hide = when (personality) {
                Personality.SHY -> true
                Personality.GIGGLER -> false
                else -> Random.nextBoolean()
            }
            if (hide) {
                startTapAnim(TapAnim.HIDE)
                setOverride(Mood.SHY, 2.0f)
            } else {
                startTapAnim(TapAnim.LAUGH)
                setOverride(Mood.LAUGHING, 2.4f)
            }
            return
        }

        // A plain tap: it jumps, in the way that suits its character
        var anim = TapAnim.HOP
        var face = Mood.HAPPY
        when (personality) {
            Personality.JUMPER -> anim = TapAnim.DOUBLE_HOP
            Personality.GIGGLER -> face = Mood.LAUGHING
            Personality.SHY -> {
                anim = TapAnim.REBOUND
                face = Mood.SHY
            }
            Personality.DANCER -> anim = TapAnim.WIGGLE_HOP
            Personality.CURIOUS -> face = Mood.CURIOUS
            else -> anim = happyAnims.filter { it != lastTapAnim }.random()
        }
        lastTapAnim = anim
        startTapAnim(anim)
        setOverride(face, 1.6f)
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

    /** Conversation state from the voice companion (listening, thinking, speaking). */
    fun setVoiceState(state: VoiceState) {
        voiceState = state
        idle = 0f
        if (state != VoiceState.IDLE) overrideMood = null
    }

    /** Microphone loudness 0..1 while listening: the body pulses slightly. */
    fun setVoiceLevel(level: Float) {
        voiceLevelTarget = level.coerceIn(0f, 1f)
    }

    /** Brief sad face (used when something went wrong). */
    fun showSad() {
        setOverride(Mood.SAD, 2.2f)
    }

    /** Body tint for baby ghosts; null keeps the original white of the big ghost. */
    fun setBodyColor(color: Int?) {
        baseBodyColor = color
        rebuildBodyShader()
        invalidate()
    }

    /** Neglect level 0..3: 0 healthy, 1 a tear, 2 crying, 3 sick. */
    fun setNeglect(level: Int) {
        neglect = level.coerceIn(0, 3)
        if (neglect > 0) idle = 0f
    }

    /** Rebuilds the body gradient from the base colour, turning greener while the ghost is sick. */
    private fun rebuildBodyShader() {
        val color = baseBodyColor
        val stops = if (color == null) {
            intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#F3F4FA"),
                Color.parseColor("#D4D8E8")
            )
        } else {
            intArrayOf(
                mixColors(color, Color.WHITE, 0.65f),
                mixColors(color, Color.WHITE, 0.25f),
                mixColors(color, Color.BLACK, 0.12f)
            )
        }
        if (sickAmt > 0.01f) {
            val green = Color.parseColor("#9BCB6B")
            for (i in stops.indices) stops[i] = mixColors(stops[i], green, 0.55f * sickAmt)
        }
        bodyPaint.shader = RadialGradient(
            36f, 30f, 80f,
            stops,
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun mixColors(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) * (1f - t) + Color.red(b) * t).toInt()
        val g = (Color.green(a) * (1f - t) + Color.green(b) * t).toInt()
        val bl = (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
        return Color.rgb(r, g, bl)
    }

    /** In the evening the ghost looks sleepy. */
    fun setNightSleepy(on: Boolean) {
        nightSleepy = on
    }

    /** Puts the ghost to sleep in its bed (it cannot be woken until it is released). */
    fun setForcedSleep(on: Boolean) {
        forcedSleep = on
        if (on) {
            overrideMood = null
            tapAnim = null
            wokeByTouch = false
        }
        idle = 0f
    }

    /** Curious about something new that appeared in the direction [dir] (-1 left, 1 right). */
    fun showCurious(dir: Float) {
        if (feedState != FeedState.NONE || forcedSleep) return
        curiousDir = if (dir < 0f) -1f else 1f
        setOverride(Mood.CURIOUS, 5f)
    }

    /** Shy: it blushes and looks down (used when it is discovered). */
    fun showShy() {
        if (feedState != FeedState.NONE || forcedSleep) return
        setOverride(Mood.SHY, 2.0f)
    }

    /** A baby plays on its own with one of its play moves. */
    fun playRandom() {
        if (feedState != FeedState.NONE || dragging || forcedSleep || tapAnim != null) return
        val anim = playfulAnims.filter { it != lastTapAnim }.random()
        lastTapAnim = anim
        startTapAnim(anim)
        setOverride(Mood.HAPPY, 1.6f)
    }

    /** What the ghost wears: variants of hat, scarf, wings, glow and star trail (0 = nothing). */
    fun setOutfit(hat: Int, scarf: Int, wings: Int, glow: Int, trail: Int) {
        outfitHat = hat
        outfitScarf = scarf
        outfitWings = wings
        outfitTrail = trail
        if (glow != outfitGlow) {
            outfitGlow = glow
            if (glow > 0) {
                val color = when (glow) {
                    1 -> Color.parseColor("#FF7EB6")
                    2 -> Color.parseColor("#5EE0FF")
                    else -> Color.parseColor("#FFD54A")
                }
                glowPaint.shader = RadialGradient(
                    50f, 56f, 64f,
                    intArrayOf(
                        Color.argb(200, Color.red(color), Color.green(color), Color.blue(color)),
                        Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
                    ),
                    floatArrayOf(0.4f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        }
        invalidate()
    }

    /** Hungry / eating faces for the feeding game. */
    fun setFeedState(state: FeedState) {
        feedState = state
        idle = 0f
        if (state != FeedState.NONE) overrideMood = null
        // No other movement overlaps eating or drinking
        if (state == FeedState.EATING || state == FeedState.DRINKING) tapAnim = null
    }

    /** Happy hop and smile after a full meal. */
    fun showFed() {
        setOverride(Mood.HAPPY, 2.5f)
        if (tapAnim == null) startTapAnim(TapAnim.HOP)
    }

    /** A happy little hop (babies playing by themselves). */
    fun hopHappy() {
        if (feedState != FeedState.NONE || dragging) return
        setOverride(Mood.HAPPY, 1.2f)
        if (tapAnim == null) startTapAnim(TapAnim.HOP)
    }

    /** Happy, relaxed face after a drink. */
    fun showRelieved() {
        setOverride(Mood.RELIEVED, 2.6f)
    }

    /** Mouth centre in this view's pixel coordinates (used by the feeding game). */
    fun mouthPoint(out: FloatArray) {
        val s = min(width / LOGICAL_W, height / LOGICAL_H)
        val ox = (width - LOGICAL_W * s) / 2f
        val oy = (height - LOGICAL_H * s) / 2f
        out[0] = ox + (50f + tapX) * s
        out[1] = oy + (GHOST_OFFSET_Y + 66f + floatY + jumpY + sagAmt * 2.8f) * s
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

    private fun step(dt: Float) {
        time += dt
        idle += dt
        if (voiceState != VoiceState.IDLE || feedState != FeedState.NONE || neglect > 0) idle = 0f

        updateMood(dt)

        // Face blends: eyes faster, brows medium, mouth gradual
        val kEye = 1f - exp(-10f * dt)
        val kBrow = 1f - exp(-8f * dt)
        val kMouth = 1f - exp(-6.5f * dt)
        for (i in 0 until N) {
            val k = when (i) {
                in 6..12 -> kMouth
                in 17..22 -> kBrow
                else -> kEye
            }
            cur[i] += (tgt[i] - cur[i]) * k
        }

        updateBrows(dt)
        updateBlink(dt)

        // Voice-linked face/body: listening pulse + tilt, thinking sway, talking mouth
        val levelTarget = if (mood == Mood.LISTENING) voiceLevelTarget else 0f
        voiceLevel += (levelTarget - voiceLevel) * (1f - exp(-12f * dt))
        val voiceTiltTarget = when (mood) {
            Mood.LISTENING -> 5f * listenDir
            Mood.THINKING -> sin(time * TWO_PI / 2.2f) * 2.5f
            Mood.CURIOUS -> 7f * curiousDir
            else -> 0f
        }
        voiceTilt += (voiceTiltTarget - voiceTilt) * (1f - exp(-4f * dt))
        talkPhase += dt * TWO_PI * 3.6f * (0.8f + 0.4f * sin(time * 1.7f))
        chewPhase += dt * TWO_PI * 2.2f
        cryPhase += dt * TWO_PI * 3.2f
        laughPhase += dt * TWO_PI * 5f
        gulpPhase += dt * TWO_PI * 1.3f
        val talkTarget = when (mood) {
            Mood.TALKING ->
                (0.15f + 0.85f * abs(sin(talkPhase))) * (0.7f + 0.3f * sin(time * 2.3f + 1f))
            Mood.EATING -> 0.1f + 0.9f * abs(sin(chewPhase))
            Mood.CRYING -> 0.35f + 0.65f * abs(sin(cryPhase))
            Mood.LAUGHING -> 0.4f + 0.6f * abs(sin(laughPhase))
            Mood.DRINKING -> {
                val gulp = max(0f, sin(gulpPhase))
                0.2f + 0.8f * gulp * gulp
            }
            else -> 0f
        }
        talkOpen += (talkTarget - talkOpen) * (1f - exp(-18f * dt))

        val asleepTarget = when (mood) {
            Mood.SLEEPING -> 1f
            Mood.SLEEPY -> 0.35f
            else -> 0f
        }
        sleepAmt += (asleepTarget - sleepAmt) * (1f - exp(-1.5f * dt))

        // Mood-linked body motion fades in/out smoothly and is off while dragging
        val kBody = 1f - exp(-3f * dt)
        val sagTarget = if ((mood == Mood.SAD || mood == Mood.HUNGRY || mood == Mood.THIRSTY ||
                mood == Mood.CRYING || mood == Mood.SICK) && !dragging) 1f else 0f
        val swayTarget = if (mood == Mood.SLEEPY && !dragging) 1f else 0f
        sagAmt += (sagTarget - sagAmt) * kBody
        cryAmt += ((if (mood == Mood.CRYING && !dragging) 1f else 0f) - cryAmt) * kBody
        sickAmt += ((if (mood == Mood.SICK) 1f else 0f) - sickAmt) * kBody
        if (abs(sickAmt - lastSickApplied) > 0.02f) {
            lastSickApplied = sickAmt
            rebuildBodyShader()
        }
        swayAmt += (swayTarget - swayAmt) * kBody

        // Calm continuous float (slower and gentler when drowsy)
        val basePeriod = if (playful) 2.0f else 2.8f
        val baseAmp = if (playful) 4.2f else 3.2f
        val baseTilt = if (playful) 3.2f else 1.8f
        floatPhase += dt * TWO_PI / (basePeriod + 1.4f * sleepAmt)
        if (floatPhase > TWO_PI) floatPhase -= TWO_PI
        floatY = sin(floatPhase) * (baseAmp - 1.7f * sleepAmt)
        idleTilt = sin(floatPhase + 0.8f) * baseTilt * (1f - 0.6f * sleepAmt)
        breath = 1f + 0.018f * sleepAmt * sin(time * TWO_PI / 3.8f)

        updateTapAnim(dt)
        updateDragTilt(dt)
        updateExtras(dt)
    }

    private fun onMoodEntered(m: Mood) {
        when (m) {
            Mood.SURPRISED -> {
                browKickPos = -4.5f
                browKickVel = 0f
            }
            Mood.ANGRY -> browTwitchT = 0f
            Mood.LISTENING -> listenDir = if (Random.nextBoolean()) 1f else -1f
            Mood.CURIOUS -> tgt[16] = 2.2f * curiousDir
            Mood.MISCHIEVOUS -> {
                mischT = 0f
                if (glanceDir < 0f) mirrorFace()
            }
            else -> Unit
        }
    }

    private fun swap(a: Int, b: Int) {
        val t = tgt[a]
        tgt[a] = tgt[b]
        tgt[b] = t
    }

    /** Mirrors the asymmetric mischievous face so the smirk follows the lean direction. */
    private fun mirrorFace() {
        swap(2, 4)
        swap(3, 5)
        swap(8, 9)
        swap(18, 20)
        swap(19, 21)
    }

    private fun updateMood(dt: Float) {
        if (overrideMood != null) {
            overrideTimer -= dt
            if (overrideTimer <= 0f) {
                val ended = overrideMood
                overrideMood = null
                // After being annoyed the ghost sulks for a moment
                if (ended == Mood.ANGRY && !dragging) setOverride(Mood.SAD, 1.8f)
            }
        }

        val override = overrideMood
        val m = when {
            forcedSleep -> Mood.SLEEPING
            dragging -> Mood.LAUGHING
            voiceState == VoiceState.LISTENING -> Mood.LISTENING
            voiceState == VoiceState.THINKING -> Mood.THINKING
            voiceState == VoiceState.SPEAKING -> Mood.TALKING
            feedState == FeedState.EATING -> Mood.EATING
            feedState == FeedState.DRINKING -> Mood.DRINKING
            feedState == FeedState.HUNGRY -> Mood.HUNGRY
            feedState == FeedState.THIRSTY -> if (cryWhenThirsty) Mood.CRYING else Mood.THIRSTY
            neglect >= 3 -> Mood.SICK
            neglect == 2 -> Mood.CRYING
            neglect == 1 -> Mood.SAD
            override != null -> override
            idle >= SLEEP_AFTER -> Mood.SLEEPING
            idle >= SLEEPY_AFTER -> Mood.SLEEPY
            nightSleepy -> Mood.SLEEPY
            SystemClock.uptimeMillis() < playedUntil -> Mood.HAPPY
            else -> Mood.RELAXED
        }
        if (m != mood) {
            mood = m
            System.arraycopy(PRESETS[m.ordinal], 0, tgt, 0, N)
            glanceT = -1f
            onMoodEntered(m)
        }

        // Drowsiness: eyes close gradually before falling asleep
        if (mood == Mood.SLEEPY) {
            val p = if (nightSleepy && idle < SLEEPY_AFTER) 0.5f
            else ((idle - SLEEPY_AFTER) / (SLEEP_AFTER - SLEEPY_AFTER)).coerceIn(0f, 1f)
            val lid = 0.3f + 0.5f * p
            tgt[2] = lid
            tgt[3] = lid
            tgt[4] = lid
            tgt[5] = lid
        }

        // Mischief: look toward the lean, glance back briefly, then settle
        if (mood == Mood.MISCHIEVOUS) {
            mischT += dt
            val d = glanceDir
            tgt[16] = when {
                mischT < 1.0f -> 2.8f * d
                mischT < 1.5f -> -2.0f * d
                else -> 1.4f * d
            }
        }

        updateGlance(dt)
    }

    /** Rare side glance while relaxed. Never overlaps another expression or movement. */
    private fun updateGlance(dt: Float) {
        val calm = mood == Mood.RELAXED && overrideMood == null && !dragging && tapAnim == null
        if (glanceT >= 0f) {
            if (!calm) {
                glanceT = -1f
                tgt[16] = PRESETS[mood.ordinal][16]
                return
            }
            glanceT += dt
            val dur = 1.3f
            if (glanceT >= dur) {
                glanceT = -1f
                tgt[16] = 0f
            } else {
                val p = glanceT / dur
                val v = when {
                    p < 0.25f -> p / 0.25f
                    p > 0.75f -> (1f - p) / 0.25f
                    else -> 1f
                }
                tgt[16] = glanceAutoDir * 2.6f * v
            }
        } else if (calm) {
            glanceTimer -= dt
            if (glanceTimer <= 0f) {
                glanceTimer = Random.nextFloat() * 13f + 12f
                glanceT = 0f
                glanceAutoDir = if (Random.nextBoolean()) 1f else -1f
            }
        }
    }

    private fun updateBrows(dt: Float) {
        val sdt = min(dt, 0.032f)
        browKickVel += (-140f * browKickPos - 10f * browKickVel) * sdt
        browKickPos += browKickVel * sdt

        if (browTwitchT >= 0f) {
            browTwitchT += dt
            if (browTwitchT >= 0.8f) {
                browTwitchT = -1f
                browTwitch = 0f
            } else {
                browTwitch = sin(browTwitchT * TWO_PI * 3.5f) * 0.9f * (1f - browTwitchT / 0.8f)
            }
        }
    }

    private fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun updateBlink(dt: Float) {
        if (mood == Mood.SLEEPING) {
            blinkT = -1f
            blinkOpen = 1f
            return
        }
        if (blinkT >= 0f) {
            blinkT += dt / 0.2f
            if (blinkT >= 1f) {
                blinkT = -1f
                blinkOpen = 1f
                if (doubleBlinkPending) {
                    doubleBlinkPending = false
                    blinkWasSecond = true
                    nextBlink = 0.14f
                } else {
                    blinkWasSecond = false
                    nextBlink = Random.nextFloat() * 4.8f + 2.2f
                }
            } else {
                // quick smooth close, slightly slower open
                blinkOpen = if (blinkT < 0.4f) 1f - smooth(blinkT / 0.4f)
                else smooth((blinkT - 0.4f) / 0.6f)
            }
        } else {
            blinkOpen = 1f
            nextBlink -= dt
            if (nextBlink <= 0f) {
                blinkT = 0f
                doubleBlinkPending = !blinkWasSecond && Random.nextFloat() < 0.15f
            }
        }
    }

    private fun startTapAnim(type: TapAnim) {
        tapAnim = type
        tapT = 0f
        tapDur = when (type) {
            TapAnim.HOP -> 0.6f
            TapAnim.DOUBLE_HOP -> 1.0f
            TapAnim.WIGGLE_HOP -> 0.8f
            TapAnim.REBOUND -> 0.6f
            TapAnim.LEAN -> 0.95f
            TapAnim.SHAKE -> 0.6f
            TapAnim.SPIN -> 0.9f
            TapAnim.TRIPLE_BOUNCE -> 1.2f
            TapAnim.DANCE -> 1.2f
            TapAnim.PEEK -> 1.1f
            TapAnim.HIDE -> 1.6f
            TapAnim.LAUGH -> 1.4f
            TapAnim.HEAD_PAT -> 1.6f
        }
        tapJitter = 0.9f + Random.nextFloat() * 0.2f
        tapDir = if (Random.nextBoolean()) 1f else -1f
    }

    private fun updateTapAnim(dt: Float) {
        jumpY = 0f
        jumpSq = 0f
        tapRot = 0f
        tapX = 0f
        spinX = 1f
        hideAmt = 0f
        val type = tapAnim ?: return

        tapT += dt
        val u = tapT / tapDur
        if (u >= 1f) {
            tapAnim = null
            return
        }
        val j = tapJitter
        when (type) {
            TapAnim.HOP -> hop(u, 11f * j)
            TapAnim.DOUBLE_HOP ->
                if (u < 0.5f) hop(u * 2f, 8f * j) else hop((u - 0.5f) * 2f, 6f * j)
            TapAnim.WIGGLE_HOP -> {
                hop(u, 10f * j)
                tapRot = sin(u * PI_F * 5f) * 6f * (1f - u)
            }
            // Surprise: short rebound, then a smaller settle bounce
            TapAnim.REBOUND ->
                if (u < 0.4f) hop(u / 0.4f, 8f * j) else hop((u - 0.4f) / 0.6f, 3f * j)
            // Mischief: short sideways lean
            TapAnim.LEAN -> {
                val s = sin(PI_F * u)
                tapRot = tapDir * s * 8f
                tapX = tapDir * s * 3f
            }
            // Mild anger: very small shake
            TapAnim.SHAKE -> {
                val decay = 1f - u
                tapRot = sin(u * PI_F * 6f) * 3f * decay
                tapX = sin(u * PI_F * 6f) * 1.2f * decay
            }
            // Baby play: a little pirouette (the body turns around itself)
            TapAnim.SPIN -> {
                hop(u, 9f * j)
                spinX = cos(TWO_PI * smooth(u))
            }
            // Baby play: three quick bounces, each a bit smaller
            TapAnim.TRIPLE_BOUNCE -> {
                val seg = (u * 3f).toInt().coerceIn(0, 2)
                val v = u * 3f - seg
                hop(v, (9f - 2.5f * seg) * j)
            }
            // Baby play: a wiggly dance
            TapAnim.DANCE -> {
                val w = sin(u * TWO_PI * 3f)
                tapRot = w * 11f
                tapX = sin(u * TWO_PI * 3f + PI_F / 2f) * 3f
                jumpY = -abs(w) * 3.5f
            }
            // Baby play: ducks down, then pops up high
            TapAnim.PEEK -> {
                if (u < 0.35f) {
                    val e = smooth(u / 0.35f)
                    jumpY = 12f * e
                    jumpSq = 0.08f * e
                } else {
                    val v = (u - 0.35f) / 0.65f
                    hop(v, 16f * j)
                    jumpY += 12f * (1f - smooth(v * 2.5f))
                }
            }
            // Hiding: it shrinks down, stays hidden for a moment, then pops back
            TapAnim.HIDE -> {
                hideAmt = when {
                    u < 0.2f -> smooth(u / 0.2f)
                    u < 0.75f -> 1f
                    else -> 1f - smooth((u - 0.75f) / 0.25f)
                }
            }
            // Laughing: it shakes with laughter and bobs
            TapAnim.LAUGH -> {
                val w = sin(u * TWO_PI * 5f)
                tapRot = w * 5f
                jumpY = -abs(w) * 3f
            }
            // Head pat: a gentle press and a happy wiggle
            TapAnim.HEAD_PAT -> {
                tapRot = sin(u * TWO_PI * 2f) * 6f * (1f - u * 0.4f)
                jumpSq = 0.06f * sin(PI_F * min(1f, u * 3f))
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

        // While it is dragged the body spins around itself (faster when dragged faster) and
        // finishes the turn after it is released
        val rate = (1.2f + min(abs(dragVel) * 0.003f, 2.5f)) * TWO_PI
        if (dragging) {
            dragSpinPhase += rate * dt
        } else if (dragSpinPhase > 0f) {
            dragSpinPhase += rate * dt
            if (dragSpinPhase % TWO_PI < rate * dt) dragSpinPhase = 0f
        }
        dragSpinX = if (dragSpinPhase > 0f) cos(dragSpinPhase) else 1f
    }

    // =====================================================================
    // Drawing
    // =====================================================================

    /** Babies cry while they ask for their milk. */
    var cryWhenThirsty = false

    /** Set on the second twin: it draws exactly what [mirrorOf] does, turned around. */
    var mirrorOf: GhostView? = null

    override fun onDraw(canvas: Canvas) {
        val src = mirrorOf
        if (src != null) {
            canvas.save()
            canvas.scale(-1f, 1f, width / 2f, height / 2f)
            src.drawFrame(canvas, width, height)
            canvas.restore()
        } else {
            drawFrame(canvas, width, height)
        }
    }

    private fun drawFrame(canvas: Canvas, w: Int, h: Int) {
        val s = min(w / LOGICAL_W, h / LOGICAL_H)
        val chewing = mood == Mood.EATING || mood == Mood.DRINKING
        val sobbing = mood == Mood.CRYING
        val moodY = sagAmt * 2.8f + swayAmt * 1.2f + cryAmt * abs(sin(cryPhase * 0.5f)) * 1.6f -
            talkOpen * (if (chewing) -1.4f else if (sobbing) 0f else 0.9f)
        val moodTilt = sin(time * TWO_PI / 4.5f) * 3.2f * swayAmt + voiceTilt +
            sin(cryPhase) * 1.6f * cryAmt
        val squash = jumpSq + sagAmt * 0.03f + (if (chewing) talkOpen * 0.02f else 0f)

        canvas.save()
        canvas.translate((w - LOGICAL_W * s) / 2f, (h - LOGICAL_H * s) / 2f)
        canvas.scale(s, s)
        canvas.translate(tapX, GHOST_OFFSET_Y + floatY + jumpY + moodY)
        canvas.rotate(idleTilt + dragTilt + tapRot + moodTilt, 50f, 90f)
        val hideScale = 1f - 0.65f * hideAmt
        canvas.scale(
            (1f + squash + voiceLevel * 0.03f) * spinX * dragSpinX * hideScale,
            (1f - squash) * breath * hideScale,
            50f, 92f
        )
        drawGhost(canvas)
        canvas.restore()
    }

    private fun drawGhost(c: Canvas) {
        drawBehind(c)
        c.drawPath(body, bodyPaint)
        c.drawPath(body, shadePaint)
        c.drawCircle(30f, 28f, 15f, highlightPaint)

        // Eyes
        val rx = cur[0]
        val ry = cur[1]
        val closed = cur[14]
        val open = if (mood == Mood.SLEEPING) 1f else blinkOpen
        val ryEff = max(0.7f, ry * open * (1f - 0.93f * closed))
        val lookX = cur[16]
        val lookY = cur[24]
        drawEye(c, 36f + lookX, 50f + lookY, rx, ry, ryEff, cur[2], cur[3], true)
        drawEye(c, 64f + lookX, 50f + lookY, rx, ry, ryEff, cur[4], cur[5], false)

        drawBrows(c, lookX)

        // Blush (after the eyes so lids never cut it)
        blushPaint.alpha = (85f * cur[13]).toInt().coerceIn(0, 255)
        rect.set(17f, 56f, 31f, 64f)
        c.drawOval(rect, blushPaint)
        rect.set(69f, 56f, 83f, 64f)
        c.drawOval(rect, blushPaint)

        drawMouth(c)
        drawNeglectExtras(c)
        drawOutfitFront(c)
        drawHearts(c)

        if (sleepAmt > 0.5f) drawZzz(c)
    }

    // =====================================================================
    // Outfit (hat, scarf, wings, glow, star trail) and hearts
    // =====================================================================

    private fun updateExtras(dt: Float) {
        if (heartsT >= 0f) {
            heartsT += dt
            if (heartsT > 1.8f) heartsT = -1f
        }
        for (i in starAge.indices) {
            if (starAge[i] >= 0f) {
                starAge[i] += dt
                starY[i] += 14f * dt
                if (starAge[i] > 1.3f) starAge[i] = -1f
            }
        }
        if (outfitTrail > 0) {
            val moving = dragging || tapAnim != null || abs(dragVel) > 20f
            starTimer -= dt
            if (starTimer <= 0f) {
                starTimer = if (moving) 0.12f else 0.55f
                for (i in starAge.indices) {
                    if (starAge[i] < 0f) {
                        starAge[i] = 0f
                        starX[i] = 14f + Random.nextFloat() * 72f
                        starY[i] = 62f + Random.nextFloat() * 30f
                        break
                    }
                }
            }
        }
    }

    private fun drawStar(c: Canvas, x: Float, y: Float, r: Float, alpha: Int) {
        tmpPath.reset()
        tmpPath.moveTo(x, y - r)
        tmpPath.lineTo(x + r * 0.35f, y - r * 0.35f)
        tmpPath.lineTo(x + r, y)
        tmpPath.lineTo(x + r * 0.35f, y + r * 0.35f)
        tmpPath.lineTo(x, y + r)
        tmpPath.lineTo(x - r * 0.35f, y + r * 0.35f)
        tmpPath.lineTo(x - r, y)
        tmpPath.lineTo(x - r * 0.35f, y - r * 0.35f)
        tmpPath.close()
        itemPaint.color = Color.parseColor("#FFE27A")
        itemPaint.alpha = alpha.coerceIn(0, 255)
        c.drawPath(tmpPath, itemPaint)
    }

    /** Things behind the body: glowing aura, star trail and small wings. */
    private fun drawBehind(c: Canvas) {
        if (outfitGlow > 0) {
            glowPaint.alpha = (170f + 70f * sin(time * 2f)).toInt().coerceIn(0, 255)
            c.drawCircle(50f, 56f, 64f, glowPaint)
        }
        if (outfitTrail > 0) {
            for (i in starAge.indices) {
                val a = starAge[i]
                if (a < 0f) continue
                val alpha = (sin(PI_F * (a / 1.3f)) * 255f).toInt()
                drawStar(c, starX[i], starY[i], 3.6f - a * 1.2f, alpha)
            }
        }
        if (outfitWings > 0) {
            val flap = sin(time * 3.2f) * 0.12f
            for (side in intArrayOf(-1, 1)) {
                val saved = c.save()
                c.translate(50f + side * 35f, 58f)
                c.scale(side.toFloat(), 1f)
                c.rotate(-(22f + flap * 80f))
                itemPaint.color = Color.WHITE
                itemPaint.alpha = 255
                rect.set(0f, -9f, 17f, 5f)
                c.drawOval(rect, itemPaint)
                rect.set(1f, -2f, 14f, 10f)
                c.drawOval(rect, itemPaint)
                itemLine.color = Color.parseColor("#BFD3EA")
                itemLine.strokeWidth = 1.2f
                rect.set(0f, -9f, 17f, 5f)
                c.drawOval(rect, itemLine)
                rect.set(1f, -2f, 14f, 10f)
                c.drawOval(rect, itemLine)
                c.restoreToCount(saved)
            }
        }
    }

    /** Scarf and hat, drawn over the face. */
    private fun drawOutfitFront(c: Canvas) {
        if (outfitScarf > 0) {
            val main = if (outfitScarf == 1) Color.parseColor("#E63946") else Color.parseColor("#3D8BFD")
            val stripe = if (outfitScarf == 1) Color.parseColor("#FFB3B8") else Color.parseColor("#A9CEFF")
            tmpPath.reset()
            tmpPath.moveTo(15f, 76f)
            tmpPath.quadTo(50f, 90f, 85f, 76f)
            itemLine.color = main
            itemLine.strokeWidth = 8f
            c.drawPath(tmpPath, itemLine)
            itemLine.color = stripe
            itemLine.strokeWidth = 2f
            c.drawPath(tmpPath, itemLine)
            itemPaint.color = main
            itemPaint.alpha = 255
            rect.set(68f, 80f, 77f, 98f)
            c.drawRoundRect(rect, 3f, 3f, itemPaint)
            itemPaint.color = stripe
            rect.set(68f, 88f, 77f, 90f)
            c.drawRect(rect, itemPaint)
        }
        when (outfitHat) {
            1 -> { // party hat
                tmpPath.reset()
                tmpPath.moveTo(34f, 20f)
                tmpPath.lineTo(50f, -10f)
                tmpPath.lineTo(66f, 20f)
                tmpPath.close()
                itemPaint.color = Color.parseColor("#FF7AA8")
                itemPaint.alpha = 255
                c.drawPath(tmpPath, itemPaint)
                itemLine.color = Color.parseColor("#FFD54A")
                itemLine.strokeWidth = 3f
                c.drawLine(38.4f, 12f, 61.6f, 12f, itemLine)
                c.drawLine(43.2f, 3f, 56.8f, 3f, itemLine)
                itemPaint.color = Color.parseColor("#FFD54A")
                c.drawCircle(50f, -10f, 3.8f, itemPaint)
            }
            2 -> { // crown
                tmpPath.reset()
                tmpPath.moveTo(36f, 19f)
                tmpPath.lineTo(36f, 4f)
                tmpPath.lineTo(43f, 11f)
                tmpPath.lineTo(50f, 1f)
                tmpPath.lineTo(57f, 11f)
                tmpPath.lineTo(64f, 4f)
                tmpPath.lineTo(64f, 19f)
                tmpPath.close()
                itemPaint.color = Color.parseColor("#FFD54A")
                itemPaint.alpha = 255
                c.drawPath(tmpPath, itemPaint)
                itemLine.color = Color.parseColor("#E0A800")
                itemLine.strokeWidth = 1.6f
                c.drawPath(tmpPath, itemLine)
                itemPaint.color = Color.parseColor("#E63946")
                c.drawCircle(50f, 14f, 2.2f, itemPaint)
                itemPaint.color = Color.parseColor("#3D8BFD")
                c.drawCircle(41f, 15f, 1.6f, itemPaint)
                c.drawCircle(59f, 15f, 1.6f, itemPaint)
            }
            4, 5, 6 -> { // night cap (blue, pink, with a star)
                val capColor = when (outfitHat) {
                    4 -> Color.parseColor("#5B8DEF")
                    5 -> Color.parseColor("#FF8FB8")
                    else -> Color.parseColor("#7C5CFF")
                }
                tmpPath.reset()
                tmpPath.moveTo(35f, 20f)
                tmpPath.quadTo(40f, -6f, 64f, 2f)
                tmpPath.lineTo(65f, 20f)
                tmpPath.close()
                itemPaint.color = capColor
                itemPaint.alpha = 255
                c.drawPath(tmpPath, itemPaint)
                itemPaint.color = Color.WHITE
                rect.set(33f, 16f, 67f, 23f)
                c.drawRoundRect(rect, 3f, 3f, itemPaint)
                c.drawCircle(64f, 2f, 3.6f, itemPaint)
                if (outfitHat == 6) drawStar(c, 48f, 8f, 4f, 255)
            }
            3 -> { // wizard hat
                tmpPath.reset()
                tmpPath.moveTo(33f, 20f)
                tmpPath.lineTo(50f, -16f)
                tmpPath.lineTo(67f, 20f)
                tmpPath.close()
                itemPaint.color = Color.parseColor("#7C5CFF")
                itemPaint.alpha = 255
                c.drawPath(tmpPath, itemPaint)
                itemPaint.color = Color.parseColor("#5B3FD6")
                rect.set(26f, 15f, 74f, 25f)
                c.drawOval(rect, itemPaint)
                drawStar(c, 50f, 5f, 5f, 255)
            }
        }
    }

    /** Little hearts rising from the head after a head pat. */
    private fun drawHearts(c: Canvas) {
        if (heartsT < 0f) return
        for (i in 0 until 3) {
            val p = heartsT / 1.8f - i * 0.15f
            if (p <= 0f || p >= 1f) continue
            val x = 36f + i * 14f + sin(p * 9f + i) * 2.5f
            val y = 16f - p * 24f
            val sz = 3.4f
            tmpPath.reset()
            tmpPath.moveTo(x, y + sz)
            tmpPath.cubicTo(x - 1.4f * sz, y + 0.2f * sz, x - 1.1f * sz, y - sz, x, y - 0.4f * sz)
            tmpPath.cubicTo(x + 1.1f * sz, y - sz, x + 1.4f * sz, y + 0.2f * sz, x, y + sz)
            tmpPath.close()
            itemPaint.color = Color.parseColor("#FF6F9F")
            itemPaint.alpha = (sin(PI_F * p) * 255f).toInt().coerceIn(0, 255)
            c.drawPath(tmpPath, itemPaint)
        }
    }

    /** One tear, then tear streams while crying, and a sweat drop when sick. */
    private fun drawNeglectExtras(c: Canvas) {
        // Crying because of neglect or because of too many taps shows the same tears
        val level = if (neglect > 0) neglect else if (mood == Mood.CRYING) 2 else 0
        if (level <= 0 || mood == Mood.EATING || mood == Mood.DRINKING) return
        val lookX = cur[16]
        val lookY = cur[24]
        when (level) {
            1 -> {
                val cycle = 4.5f
                val n = (time / cycle).toInt()
                val p = (time % cycle) / 1.6f
                if (p < 1f) {
                    val x = if (n % 2 == 0) 33f + lookX else 67f + lookX
                    drawTear(c, x, 57f + lookY, p, false)
                }
            }
            2 -> {
                for (i in 0 until 2) {
                    val p = (time * 0.9f + i * 0.5f) % 1f
                    drawTear(c, 33f - i * 1.5f + lookX, 57f + lookY, p, true)
                    drawTear(c, 67f + i * 1.5f + lookX, 57f + lookY, p, true)
                }
            }
            else -> {
                val p = (time % 3f) / 2f
                if (p < 1f) drawDrop(c, 74f, 24f + p * 10f, ((1f - p) * 230f).toInt())
            }
        }
    }

    private fun drawTear(c: Canvas, x0: Float, y0: Float, p: Float, stream: Boolean) {
        val dir = if (x0 < 50f) -1f else 1f
        val x = x0 + dir * p * 2f
        val y = y0 + p * 22f
        val a = (sin(PI_F * p) * 230f).toInt().coerceIn(0, 255)
        if (stream) {
            streamPaint.alpha = a / 2
            c.drawLine(x0, y0, x, y, streamPaint)
        }
        drawDrop(c, x, y, a)
    }

    private fun drawDrop(c: Canvas, x: Float, y: Float, alpha: Int) {
        tearPaint.alpha = alpha.coerceIn(0, 255)
        tmpPath.reset()
        tmpPath.moveTo(x, y - 3.6f)
        tmpPath.quadTo(x + 2.6f, y + 0.4f, x, y + 2.6f)
        tmpPath.quadTo(x - 2.6f, y + 0.4f, x, y - 3.6f)
        tmpPath.close()
        c.drawPath(tmpPath, tearPaint)
    }

    private fun drawEye(
        c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, ryEff: Float,
        lidIn: Float, lidOut: Float, isLeft: Boolean
    ) {
        rect.set(cx - rx, cy - ryEff, cx + rx, cy + ryEff)
        c.drawOval(rect, darkPaint)

        if (ryEff > ry * 0.55f) {
            val gs = cur[23]
            c.drawCircle(cx + rx * 0.35f, cy - ryEff * 0.4f, rx * 0.38f * gs, glintPaint)
            c.drawCircle(cx - rx * 0.4f, cy + ryEff * 0.4f, rx * 0.16f * gs, glintPaint)
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

        // Lower lid pushed up by the cheeks: smiling eyes
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

    private fun drawBrows(c: Canvas, lookX: Float) {
        val a = cur[17]
        if (a < 0.03f) return
        browPaint.alpha = (255f * a).toInt().coerceIn(0, 255)
        val kick = browKickPos
        drawBrow(
            c,
            36f + lookX - 8.5f, cur[19] + kick,
            36f + lookX + 6f, cur[18] + kick + browTwitch
        )
        drawBrow(
            c,
            64f + lookX + 8.5f, cur[21] + kick,
            64f + lookX - 6f, cur[20] + kick + browTwitch
        )
    }

    private fun drawBrow(c: Canvas, ox: Float, oy: Float, ix: Float, iy: Float) {
        tmpPath.reset()
        tmpPath.moveTo(ox, oy)
        tmpPath.quadTo((ox + ix) / 2f, (oy + iy) / 2f - cur[22], ix, iy)
        c.drawPath(tmpPath, browPaint)
    }

    private fun drawMouth(c: Canvas) {
        val xl = cur[6]
        val xr = cur[7]
        val yl = cur[8]
        val yr = cur[9]
        var ct = cur[10]
        var cb = cur[11]
        if (talkOpen > 0.01f) {
            ct -= talkOpen * 2.2f
            cb += talkOpen * 7f
        }
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
