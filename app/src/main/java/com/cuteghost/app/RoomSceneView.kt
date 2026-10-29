package com.cuteghost.app

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
import android.view.View
import kotlin.math.sin

/**
 * The inside of a house in two layers (unit box 100 x 150). The back layer is the room with its
 * furniture, the sleeping ghosts are views between the layers, and the front layer (bed covers,
 * magic decorations, lamp glow, lights) is drawn over them. What is placed comes from [state].
 */
class RoomSceneView(
    context: Context,
    private val houseId: Int,
    private val level: Int,
    private val front: Boolean,
    private val state: SceneState
) : View(context) {

    companion object {
        /** Level 1-2: one bed, level 3: two beds, level 4-5: three beds. */
        fun bedCount(level: Int): Int = when {
            level >= 4 -> 3
            level == 3 -> 2
            else -> 1
        }

        fun bedXs(n: Int): FloatArray = when (n) {
            1 -> floatArrayOf(50f)
            2 -> floatArrayOf(31f, 69f)
            else -> floatArrayOf(19f, 50f, 81f)
        }

        fun bedWidth(n: Int): Float = when (n) {
            1 -> 34f
            2 -> 32f
            else -> 28f
        }

        /** left, top, width, height (scene units) of the box of the ghost sleeping in bed [i]. */
        fun ghostBox(i: Int, n: Int): FloatArray {
            val w = bedWidth(n) * 1.05f
            val h = w * 1.28f
            return floatArrayOf(bedXs(n)[i] - w / 2f, 104f - h, w, h)
        }

        const val SCENE_W = 100f
        const val SCENE_H = 150f
    }

    private val theme = HouseDefs.get(houseId)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val skyShader = LinearGradient(0f, 10f, 0f, 44f, theme.sky1, theme.sky2, Shader.TileMode.CLAMP)
    private val rect = RectF()
    private val path = Path()
    private var clock = 0f
    private var running = false
    private var lastTick = 0L
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            clock += ((now - lastTick) / 1000f).coerceIn(0f, 0.05f)
            lastTick = now
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

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1f - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1f - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
    )

    private fun f(color: Int, alpha: Int = 255) {
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.color = color
        fill.alpha = alpha
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        rect.set(l, t, r, b)
        f(color)
        c.drawRoundRect(rect, rad, rad, fill)
    }

    /** Draws an item scaled into the box x, y, w, h of the room. */
    private fun put(c: Canvas, item: Furn?, x: Float, y: Float, w: Float, h: Float) {
        if (item == null) return
        val sz = FurnArt.sizeOf(item.slot)
        c.save()
        c.translate(x, y)
        c.scale(w / sz[0], h / sz[1])
        FurnArt.draw(c, item, clock)
        c.restore()
    }

    override fun onDraw(canvas: Canvas) {
        val s = width / SCENE_W
        val oy = (height - SCENE_H * s) / 2f
        canvas.save()
        canvas.translate(0f, oy)
        canvas.scale(s, s)
        if (front) drawFront(canvas) else drawBack(canvas)
        canvas.restore()
    }

    // ================= back layer: the room and its furniture =================
    private fun drawBack(c: Canvas) {
        val night = houseId == 4

        // Wall
        val wallItem = state.itemFor(FSlot.WALL)
        if (wallItem != null) {
            put(c, wallItem, 0f, 0f, 100f, 104f)
        } else {
            f(theme.wall)
            rect.set(0f, 0f, 100f, 104f)
            c.drawRect(rect, fill)
            f(Color.WHITE, 40)
            var x = 0f
            while (x < 100f) {
                rect.set(x, 0f, x + 10f, 104f)
                c.drawRect(rect, fill)
                x += 20f
            }
        }
        if (night && wallItem == null) {
            for (i in 0 until 14) {
                f(Color.parseColor("#FFE27A"), (120f + 100f * sin(clock * 2f + i)).toInt().coerceIn(0, 255))
                c.drawCircle(4f + (i * 29f) % 92f, 4f + (i * 17f) % 8f + (i % 3) * 14f, 0.8f, fill)
            }
        }

        // Window with a moving sky
        rect.set(30f, 10f, 70f, 44f)
        c.save()
        path.reset()
        path.addRoundRect(rect, 16f, 16f, Path.Direction.CW)
        c.clipPath(path)
        fill.style = Paint.Style.FILL
        fill.shader = skyShader
        fill.alpha = 255
        c.drawRect(rect, fill)
        fill.shader = null
        for (i in 0 until 3) {
            val cx = 24f + (clock * 4f + i * 22f) % 60f
            val cy = 20f + i * 8f
            f(Color.WHITE, 225)
            rect.set(cx - 8f, cy - 3f, cx + 8f, cy + 3f)
            c.drawOval(rect, fill)
            c.drawCircle(cx - 2f, cy - 3f, 3.4f, fill)
        }
        if (night) {
            for (i in 0 until 9) {
                f(Color.parseColor("#FFF3B0"), (150f + 100f * sin(clock * 2.4f + i)).toInt().coerceIn(0, 255))
                c.drawCircle(32f + (i * 13f) % 36f, 12f + (i * 7f) % 24f, 0.8f, fill)
            }
        }
        if (houseId == 5) {
            for (i in 0 until 12) {
                f(Color.WHITE)
                c.drawCircle(32f + (i * 37f) % 36f + sin(clock + i) * 1.4f, 10f + (clock * 6f + i * 7f) % 34f, 0.9f, fill)
            }
        }
        if (level >= 4) {
            f(mix(theme.accent, Color.WHITE, 0.55f))
            rect.set(30f, 38f, 70f, 44f)
            c.drawRect(rect, fill)
            for (px in floatArrayOf(38f, 50f, 62f)) {
                f(Color.parseColor("#C97B4A"))
                rect.set(px - 2.5f, 34f, px + 2.5f, 38f)
                c.drawRect(rect, fill)
                f(Color.parseColor("#5FC577"))
                c.drawCircle(px - 1.5f, 32f, 2.4f, fill)
                c.drawCircle(px + 1.5f, 31f, 2.6f, fill)
            }
        }
        c.restore()
        FurnArt.windowFrame(c, state.itemFor(FSlot.WINDOW), theme.accent, clock)
        rr(c, 28f, 44f, 72f, 47f, 1.5f, mix(theme.accent, Color.WHITE, 0.4f))

        // Curtains (the right one is the left one turned around)
        val curtain = state.itemFor(FSlot.CURTAIN)
        put(c, curtain, 22f, 8f, 8f, 38f)
        if (curtain != null) {
            c.save()
            c.translate(78f, 8f)
            c.scale(-1f, 1f)
            put(c, curtain, 0f, 0f, 8f, 38f)
            c.restore()
        }

        // Things on the wall
        put(c, state.itemFor(FSlot.CLOSET), 2f, 10f, 18f, 30f)
        put(c, state.itemFor(FSlot.CLOCK), 84f, 12f, 12f, 12f)
        put(c, state.itemFor(FSlot.SHELF), 54f, 46f, 30f, 14f)
        put(c, state.itemFor(FSlot.PICTURE), 10f, 54f, 16f, 13f)
        put(c, state.itemFor(FSlot.LAMP), 84f, 52f, 12f, 16f)

        // Floor
        val floorItem = state.itemFor(FSlot.FLOOR)
        if (floorItem != null) {
            put(c, floorItem, 0f, 104f, 100f, 46f)
        } else {
            f(theme.floor)
            rect.set(0f, 104f, 100f, 150f)
            c.drawRect(rect, fill)
            line.color = mix(theme.floor, Color.BLACK, 0.12f)
            line.strokeWidth = 0.6f
            c.drawLine(0f, 118f, 100f, 118f, line)
            c.drawLine(0f, 132f, 100f, 132f, line)
        }
        f(mix(theme.floor, Color.BLACK, 0.2f))
        rect.set(0f, 102f, 100f, 105f)
        c.drawRect(rect, fill)
        if (level >= 2) {
            line.color = theme.accent
            line.strokeWidth = 0.9f
            c.drawLine(0f, 101f, 100f, 101f, line)
        }

        // Living room
        put(c, state.itemFor(FSlot.RUG), 18f, 124f, 64f, 20f)
        put(c, state.itemFor(FSlot.SOFA), 3f, 112f, 36f, 20f)
        put(c, state.itemFor(FSlot.TABLE), 62f, 122f, 16f, 12f)
        put(c, state.itemFor(FSlot.PLANT), 86f, 112f, 12f, 18f)

        // Beds: frame and pillow (the cover is in the front layer)
        val n = bedCount(level)
        val xs = bedXs(n)
        val w = bedWidth(n)
        val bed = state.itemFor(FSlot.BED)
        val pillow = state.itemFor(FSlot.PILLOW)
        for (i in 0 until n) {
            val cx = xs[i]
            put(c, bed, cx - w / 2f, 86f, w, 22f)
            put(c, pillow, cx - w * 0.40f, 83.5f, w * 0.38f, 8.5f)
        }
    }

    // ================= front layer: over the sleeping ghosts =================
    private fun drawFront(c: Canvas) {
        val n = bedCount(level)
        val xs = bedXs(n)
        val w = bedWidth(n)
        val cover = state.itemFor(FSlot.COVER)
        for (i in 0 until n) put(c, cover, xs[i] - w / 2f - 0.5f, 92f, w + 1f, 17f)

        // Magic decorations float over everything
        put(c, state.itemFor(FSlot.MAGIC), 0f, 0f, 100f, 104f)

        // Warm light of the lamp
        val lamp = state.itemFor(FSlot.LAMP)
        if (lamp != null) {
            val pulse = 0.7f + 0.3f * sin(clock * 1.8f)
            c.save()
            c.translate(90f, 60f)
            glow.shader = RadialGradient(
                0f, 0f, 22f,
                Color.argb(170, Color.red(lamp.c1), Color.green(lamp.c1), Color.blue(lamp.c1)),
                Color.argb(0, Color.red(lamp.c1), Color.green(lamp.c1), Color.blue(lamp.c1)),
                Shader.TileMode.CLAMP
            )
            glow.alpha = (255f * pulse).toInt().coerceIn(0, 255)
            c.drawCircle(0f, 0f, 22f, glow)
            c.restore()
        }

        if (level >= 5) {
            val colors = intArrayOf(Color.parseColor("#FFE27A"), Color.parseColor("#FF8FB8"), Color.parseColor("#8FD0FF"))
            for (i in 0 until 13) {
                val t = i / 12f
                f(colors[i % 3], (160f + 95f * sin(clock * 3f + i)).toInt().coerceIn(0, 255))
                c.drawCircle(4f + 92f * t, 5f + 5f * sin(Math.PI.toFloat() * t), 1.6f, fill)
            }
        }

        // A burst of sparkles when something new was put in the room
        val left = state.celebrateUntil - SystemClock.uptimeMillis()
        if (left > 0L) {
            val p = 1f - left / 1600f
            for (i in 0 until 16) {
                val a = i * 0.3927f
                val r = 8f + 46f * p
                f(Color.parseColor("#FFE27A"), ((1f - p) * 255f).toInt().coerceIn(0, 255))
                c.drawCircle(50f + kotlin.math.cos(a) * r, 80f + sin(a) * r * 0.8f, 2.2f * (1f - p * 0.5f), fill)
            }
        }
    }
}
