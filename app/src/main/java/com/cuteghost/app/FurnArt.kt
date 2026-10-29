package com.cuteghost.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Drawing of every piece of furniture. Each slot has its own local size ([sizeOf]); [draw] paints
 * the item in that box and [drawFit] scales it into any box (shop cards).
 */
object FurnArt {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val path2 = Path()
    private val rect = RectF()

    fun sizeOf(slot: FSlot): FloatArray = when (slot) {
        FSlot.BED -> floatArrayOf(34f, 22f)
        FSlot.COVER -> floatArrayOf(34f, 17f)
        FSlot.PILLOW -> floatArrayOf(14f, 8.5f)
        FSlot.RUG -> floatArrayOf(64f, 20f)
        FSlot.SOFA -> floatArrayOf(36f, 20f)
        FSlot.TABLE -> floatArrayOf(16f, 12f)
        FSlot.LAMP -> floatArrayOf(12f, 16f)
        FSlot.PICTURE -> floatArrayOf(16f, 13f)
        FSlot.CLOCK -> floatArrayOf(12f, 12f)
        FSlot.PLANT -> floatArrayOf(12f, 18f)
        FSlot.SHELF -> floatArrayOf(30f, 14f)
        FSlot.CLOSET -> floatArrayOf(18f, 30f)
        FSlot.FLOOR -> floatArrayOf(100f, 46f)
        FSlot.WALL -> floatArrayOf(100f, 104f)
        FSlot.WINDOW -> floatArrayOf(40f, 34f)
        FSlot.CURTAIN -> floatArrayOf(8f, 38f)
        FSlot.MAGIC -> floatArrayOf(100f, 104f)
        FSlot.GHOST_HAT -> floatArrayOf(18f, 16f)
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

    private fun ln(color: Int, w: Float) {
        line.color = color
        line.strokeWidth = w
        line.alpha = 255
    }

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        rect.set(l, t, r, b)
        f(color)
        c.drawRoundRect(rect, rad, rad, fill)
    }

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        rect.set(l, t, r, b)
        f(color)
        c.drawOval(rect, fill)
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int = 255) {
        path.reset()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val a = (-90f + i * 36f) * (PI.toFloat() / 180f)
            if (i == 0) path.moveTo(cx + cos(a) * rr, cy + sin(a) * rr) else path.lineTo(cx + cos(a) * rr, cy + sin(a) * rr)
        }
        path.close()
        f(color, alpha)
        c.drawPath(path, fill)
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        path.reset()
        path.moveTo(cx, cy + s)
        path.cubicTo(cx - 1.5f * s, cy + 0.1f * s, cx - 1.1f * s, cy - s, cx, cy - 0.4f * s)
        path.cubicTo(cx + 1.1f * s, cy - s, cx + 1.5f * s, cy + 0.1f * s, cx, cy + s)
        path.close()
        f(color)
        c.drawPath(path, fill)
    }

    /** Draws [item] in the local box of its slot. [t] is the time in seconds (for animations). */
    fun draw(c: Canvas, item: Furn, t: Float) {
        when (item.slot) {
            FSlot.BED -> bed(c, item)
            FSlot.COVER -> cover(c, item)
            FSlot.PILLOW -> pillow(c, item)
            FSlot.RUG -> rug(c, item)
            FSlot.SOFA -> sofa(c, item)
            FSlot.TABLE -> table(c, item)
            FSlot.LAMP -> lamp(c, item)
            FSlot.PICTURE -> picture(c, item)
            FSlot.CLOCK -> clock(c, item, t)
            FSlot.PLANT -> plant(c, item)
            FSlot.SHELF -> shelf(c, item)
            FSlot.CLOSET -> closet(c, item)
            FSlot.FLOOR -> floor(c, item)
            FSlot.WALL -> wall(c, item)
            FSlot.WINDOW -> windowThumb(c, item, t)
            FSlot.CURTAIN -> curtain(c, item)
            FSlot.MAGIC -> magic(c, item, t)
            FSlot.GHOST_HAT -> cap(c, item)
        }
    }

    /** Draws [item] scaled and centred inside a box of [bw] x [bh]. */
    fun drawFit(c: Canvas, item: Furn, bw: Float, bh: Float, t: Float) {
        val sz = sizeOf(item.slot)
        val s = min(bw / sz[0], bh / sz[1])
        c.save()
        c.translate((bw - sz[0] * s) / 2f, (bh - sz[1] * s) / 2f)
        c.scale(s, s)
        draw(c, item, t)
        c.restore()
    }

    // ---------------- bedroom ----------------
    private fun bed(c: Canvas, i: Furn) {
        rr(c, 0f, 6f, 34f, 22f, if (i.style == 1) 2f else 4f, i.c1)
        rr(c, -1f, 0f, 3f, 22f, 2f, i.c2)
        when (i.style) {
            0 -> { f(Color.WHITE); c.drawCircle(8f, 6f, 5f, fill); c.drawCircle(17f, 4f, 6f, fill); c.drawCircle(26f, 6f, 5f, fill) }
            2 -> star(c, 1f, 6f, 3.2f, i.c2)
            3 -> {
                f(i.c2)
                c.drawCircle(1f, 7f, 3.4f, fill)
                f(i.c1)
                c.drawCircle(2.4f, 6f, 2.8f, fill)
            }
            else -> Unit
        }
        rr(c, 2f, 5f, 32f, 19f, 3f, Color.WHITE)
        rr(c, 3f, 20f, 6f, 22f, 1f, mix(i.c1, Color.BLACK, 0.2f))
        rr(c, 28f, 20f, 31f, 22f, 1f, mix(i.c1, Color.BLACK, 0.2f))
    }

    private fun cover(c: Canvas, i: Furn) {
        c.save()
        path.reset()
        rect.set(0f, 0f, 34f, 17f)
        path.addRoundRect(rect, 4f, 4f, Path.Direction.CW)
        c.clipPath(path)
        if (i.style == 2) {
            val colors = intArrayOf(Color.parseColor("#FF8FA3"), Color.parseColor("#FFC38A"), Color.parseColor("#FFE27A"), Color.parseColor("#8FE0A8"), Color.parseColor("#8FC7FF"))
            for (k in colors.indices) {
                rr(c, 0f, k * 3.5f, 34f, k * 3.5f + 3.6f, 0f, colors[k])
            }
        } else {
            rr(c, 0f, 0f, 34f, 17f, 4f, i.c1)
            rr(c, 0f, 0f, 34f, 4f, 2f, mix(i.c1, Color.WHITE, 0.45f))
            for (k in 0 until 4) {
                if (i.style == 1) star(c, 5f + k * 8f, 9f + (k % 2) * 4f, 2.2f, i.c2)
                else { f(Color.WHITE, 150); c.drawCircle(5f + k * 8f, 9f + (k % 2) * 4f, 1.3f, fill) }
            }
        }
        c.restore()
    }

    private fun pillow(c: Canvas, i: Furn) {
        when (i.style) {
            1 -> heart(c, 7f, 4.6f, 4.2f, i.c1)
            2 -> star(c, 7f, 4.4f, 4.6f, i.c1)
            else -> { rr(c, 0f, 0f, 14f, 8.5f, 3.5f, i.c1); rr(c, 1.5f, 1f, 8f, 3f, 1.5f, mix(i.c1, Color.WHITE, 0.5f)) }
        }
    }

    // ---------------- living room ----------------
    private fun rug(c: Canvas, i: Furn) {
        when (i.style) {
            0 -> { oval(c, 0f, 0f, 64f, 20f, i.c1); ln(i.c2, 1f); rect.set(8f, 4f, 56f, 16f); c.drawOval(rect, line) }
            1 -> {
                rr(c, 0f, 0f, 64f, 20f, 5f, i.c1)
                for (k in 0 until 5) rr(c, 4f + k * 12f, 0f, 8f + k * 12f, 20f, 0f, mix(i.c1, Color.WHITE, 0.45f))
            }
            else -> {
                val colors = intArrayOf(Color.parseColor("#FF8FA3"), Color.parseColor("#FFE27A"), Color.parseColor("#8FE0A8"), Color.parseColor("#8FC7FF"))
                for (k in colors.indices) oval(c, k * 7f, k * 2.2f, 64f - k * 7f, 20f - k * 2.2f, colors[k])
            }
        }
    }

    private fun sofa(c: Canvas, i: Furn) {
        rr(c, 2f, 0f, 34f, 12f, 5f, mix(i.c1, Color.BLACK, 0.1f))
        rr(c, 0f, 9f, 36f, 18f, 4f, i.c1)
        rr(c, 3f, 5f, 18f, 14f, 3f, mix(i.c1, Color.WHITE, 0.25f))
        rr(c, 18f, 5f, 33f, 14f, 3f, mix(i.c1, Color.WHITE, 0.25f))
        rr(c, 7f, 6f, 13f, 12f, 2f, i.c2)
        rr(c, 3f, 17f, 7f, 20f, 1f, mix(i.c1, Color.BLACK, 0.35f))
        rr(c, 29f, 17f, 33f, 20f, 1f, mix(i.c1, Color.BLACK, 0.35f))
    }

    private fun table(c: Canvas, i: Furn) {
        rr(c, 7f, 4f, 9f, 12f, 0.5f, mix(i.c1, Color.BLACK, 0.3f))
        oval(c, 0f, 0f, 16f, 6f, i.c1)
        if (i.style == 1) star(c, 8f, 3f, 2.4f, i.c2)
    }

    // ---------------- decor ----------------
    private fun lamp(c: Canvas, i: Furn) {
        ln(Color.parseColor("#B8A6F5"), 1f)
        c.drawLine(6f, 14f, 6f, 8f, line)
        when (i.style) {
            0 -> {
                path.reset(); path.addCircle(6f, 6f, 5f, Path.Direction.CW)
                path2.reset(); path2.addCircle(8.4f, 4.8f, 4.3f, Path.Direction.CW)
                path.op(path2, Path.Op.DIFFERENCE)
                f(i.c2); c.drawPath(path, fill)
            }
            1 -> star(c, 6f, 6f, 6f, i.c1)
            else -> {
                rect.set(0f, 0f, 12f, 10f); f(i.c2); c.drawArc(rect, 180f, 180f, true, fill)
                f(Color.WHITE); c.drawCircle(4f, 3.5f, 1.2f, fill); c.drawCircle(8f, 2.5f, 1f, fill)
                rr(c, 4.5f, 5f, 7.5f, 9f, 1f, Color.parseColor("#FFF1D6"))
            }
        }
    }

    private fun picture(c: Canvas, i: Furn) {
        rr(c, 0f, 0f, 16f, 13f, 1.5f, Color.parseColor("#C9A57A"))
        rr(c, 1.5f, 1.5f, 14.5f, 11.5f, 1f, i.c1)
        if (i.style == 1) star(c, 8f, 6.5f, 3.6f, i.c2)
        else { oval(c, 4f, 5f, 12f, 9f, i.c2); f(i.c2); c.drawCircle(7f, 5f, 2.4f, fill) }
    }

    private fun clock(c: Canvas, i: Furn, t: Float) {
        f(i.c1); c.drawCircle(6f, 6f, 6f, fill)
        ln(i.c2, 1.2f); c.drawCircle(6f, 6f, 5.4f, line)
        ln(i.c2, 1f)
        c.drawLine(6f, 6f, 6f, 2.6f, line)
        val a = t * 1.5f
        c.drawLine(6f, 6f, 6f + sin(a) * 4f, 6f - cos(a) * 4f, line)
    }

    private fun plant(c: Canvas, i: Furn) {
        f(i.c1)
        c.drawCircle(3.5f, 7f, 3.6f, fill); c.drawCircle(8.5f, 7f, 3.6f, fill); c.drawCircle(6f, 4f, 3.8f, fill)
        if (i.style == 1) { f(i.c2); c.drawCircle(6f, 1.8f, 2.2f, fill); f(Color.parseColor("#FFE27A")); c.drawCircle(6f, 1.8f, 0.9f, fill) }
        path.reset(); path.moveTo(2f, 10f); path.lineTo(10f, 10f); path.lineTo(8.6f, 18f); path.lineTo(3.4f, 18f); path.close()
        f(Color.parseColor("#C97B4A")); c.drawPath(path, fill)
    }

    private fun shelf(c: Canvas, i: Furn) {
        rr(c, 0f, 9f, 30f, 12f, 1f, i.c1)
        rr(c, 3f, 11f, 5f, 14f, 0.5f, mix(i.c1, Color.BLACK, 0.3f))
        rr(c, 25f, 11f, 27f, 14f, 0.5f, mix(i.c1, Color.BLACK, 0.3f))
        rr(c, 4f, 2f, 8f, 9f, 0.5f, Color.parseColor("#7C5CFF"))
        rr(c, 8.5f, 4f, 12f, 9f, 0.5f, Color.parseColor("#FFC93C"))
        f(i.c2); c.drawCircle(22f, 6f, 3f, fill)
        rr(c, 20.4f, 8f, 23.6f, 9f, 0.4f, Color.parseColor("#C97B4A"))
    }

    private fun closet(c: Canvas, i: Furn) {
        rr(c, 0f, 0f, 18f, 30f, 2f, i.c1)
        ln(i.c2, 0.9f); c.drawLine(9f, 1f, 9f, 29f, line)
        rect.set(1.5f, 1.5f, 16.5f, 28.5f); c.drawRoundRect(rect, 1.5f, 1.5f, line)
        f(Color.parseColor("#FFE27A")); c.drawCircle(7.3f, 15f, 0.9f, fill); c.drawCircle(10.7f, 15f, 0.9f, fill)
        star(c, 4.5f, 6f, 1.8f, Color.parseColor("#FFE27A")); star(c, 13.5f, 6f, 1.8f, Color.parseColor("#FFE27A"))
    }

    // ---------------- floor, wall, windows ----------------
    private fun floor(c: Canvas, i: Furn) {
        f(i.c1); rect.set(0f, 0f, 100f, 46f); c.drawRect(rect, fill)
        when (i.style) {
            1 -> {
                var y = 0f; var row = 0
                while (y < 46f) {
                    var x = if (row % 2 == 0) 0f else 10f
                    while (x < 100f) { rr(c, x, y, x + 10f, y + 10f, 0f, i.c2); x += 20f }
                    y += 10f; row++
                }
            }
            2 -> { f(i.c2, 200); for (k in 0 until 6) { c.drawCircle(8f + k * 18f, 12f + (k % 2) * 16f, 7f, fill); c.drawCircle(17f + k * 18f, 14f + (k % 2) * 16f, 5f, fill) } }
            else -> { ln(i.c2, 0.6f); c.drawLine(0f, 14f, 100f, 14f, line); c.drawLine(0f, 28f, 100f, 28f, line); var x = 8f; while (x < 100f) { c.drawLine(x, 0f, x + 6f, 14f, line); c.drawLine(x + 14f, 14f, x + 8f, 28f, line); x += 20f } }
        }
    }

    private fun wall(c: Canvas, i: Furn) {
        f(i.c1); rect.set(0f, 0f, 100f, 104f); c.drawRect(rect, fill)
        if (i.style == 1) {
            for (k in 0 until 16) star(c, 6f + (k * 29f) % 90f, 8f + (k * 17f) % 90f, 1.6f, i.c2, 200)
        } else {
            f(Color.WHITE, 40)
            var x = 0f
            while (x < 100f) { rect.set(x, 0f, x + 10f, 104f); c.drawRect(rect, fill); x += 20f }
        }
    }

    /** The window frame (the sky is painted by the room itself). */
    fun windowFrame(c: Canvas, i: Furn?, accent: Int, t: Float) {
        val style = i?.style ?: 0
        val color = i?.c1 ?: accent
        rect.set(30f, 10f, 70f, 44f)
        if (style == 1) {
            val colors = intArrayOf(Color.parseColor("#FF8FA3"), Color.parseColor("#FFE27A"), Color.parseColor("#8FE0A8"), Color.parseColor("#8FC7FF"))
            for (k in colors.indices) {
                ln(colors[k], 1.2f)
                rect.set(30f - k * 0.9f, 10f - k * 0.9f, 70f + k * 0.9f, 44f + k * 0.9f)
                c.drawRoundRect(rect, 16f, 16f, line)
            }
            for (k in 0 until 4) star(c, 50f + cos(t * 1.5f + k * 1.6f) * 24f, 27f + sin(t * 1.5f + k * 1.6f) * 20f, 1.4f, Color.parseColor("#FFF3B0"))
        } else {
            ln(color, 2f)
            rect.set(30f, 10f, 70f, 44f)
            c.drawRoundRect(rect, 16f, 16f, line)
        }
        ln(color, 1.2f)
        c.drawLine(50f, 10f, 50f, 44f, line)
        c.drawLine(30f, 27f, 70f, 27f, line)
    }

    private fun windowThumb(c: Canvas, i: Furn, t: Float) {
        c.save()
        rect.set(30f, 10f, 70f, 44f)
        path.reset(); path.addRoundRect(rect, 16f, 16f, Path.Direction.CW)
        c.clipPath(path)
        fill.style = Paint.Style.FILL
        fill.shader = LinearGradient(0f, 10f, 0f, 44f, Color.parseColor("#BFD9FF"), Color.parseColor("#F4EDFF"), Shader.TileMode.CLAMP)
        fill.alpha = 255
        c.drawRect(rect, fill)
        fill.shader = null
        c.restore()
        c.save()
        c.translate(-30f, -10f)
        windowFrame(c, i, i.c1, t)
        c.restore()
    }

    private fun curtain(c: Canvas, i: Furn) {
        rr(c, 0f, 0f, 8f, 38f, 3f, i.c1)
        rr(c, 0f, 17f, 8f, 20f, 1f, mix(i.c1, Color.BLACK, 0.2f))
        if (i.style == 1) { star(c, 4f, 8f, 1.8f, i.c2); star(c, 4f, 28f, 1.8f, i.c2) }
    }

    // ---------------- magic decorations (animated, drawn over the room) ----------------
    private fun magic(c: Canvas, i: Furn, t: Float) {
        when (i.style) {
            0 -> for (k in 0 until 8) {
                val y = 104f - ((t * 6f + k * 13f) % 104f)
                star(c, 8f + (k * 37f) % 86f + sin(t + k) * 2f, y, 2f, i.c1, (150f + 100f * sin(t * 3f + k)).toInt().coerceIn(0, 255))
            }
            1 -> for (k in 0 until 4) {
                val cx = if (k % 2 == 0) 6f else 94f
                val cy = if (k < 2) 56f else 100f
                path.reset(); path.moveTo(cx, cy - 8f); path.lineTo(cx + 3.5f, cy); path.lineTo(cx, cy + 5f); path.lineTo(cx - 3.5f, cy); path.close()
                f(i.c1, (170f + 70f * sin(t * 2f + k)).toInt().coerceIn(0, 255)); c.drawPath(path, fill)
                f(Color.WHITE, 160); c.drawCircle(cx - 1f, cy - 3f, 0.9f, fill)
            }
            2 -> for (k in 0 until 3) {
                val x = ((t * 4f + k * 38f) % 130f) - 15f
                val y = 52f + k * 9f
                oval(c, x - 7f, y - 3f, x + 7f, y + 3f, i.c1); f(i.c1); c.drawCircle(x - 2f, y - 3f, 3f, fill)
            }
            else -> for (k in 0 until 10) {
                val x = 50f + sin(t * 0.7f + k * 1.3f) * 42f
                val y = 60f + cos(t * 0.9f + k * 2.1f) * 30f
                f(i.c2, (80f + 120f * sin(t * 4f + k)).toInt().coerceIn(0, 255)); c.drawCircle(x, y, 2.6f, fill)
                f(i.c1); c.drawCircle(x, y, 1.1f, fill)
            }
        }
    }

    // ---------------- ghost accessory (shop picture) ----------------
    private fun cap(c: Canvas, i: Furn) {
        path.reset(); path.moveTo(2f, 14f); path.quadTo(6f, 0f, 15f, 4f); path.lineTo(16f, 14f); path.close()
        f(i.c1); c.drawPath(path, fill)
        rr(c, 0f, 11f, 18f, 15f, 2f, Color.WHITE)
        f(Color.WHITE); c.drawCircle(15f, 4f, 2.4f, fill)
        if (i.style == 2) star(c, 8f, 8f, 2.6f, i.c2)
    }
}
