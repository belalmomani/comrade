package com.cuteghost.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drawing of the six houses (a 96 x 104 unit box, the door's bottom centre is at 48, 86) and of
 * what every upgrade level adds: level 2 a flag and flowers, level 3 an extra wing, level 4 a
 * balcony with a small garden, level 5 fairy lights and sparkles.
 */
object HouseArt {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, 14f,
            Color.parseColor("#B3FFE27A"), Color.parseColor("#00FFE27A"),
            Shader.TileMode.CLAMP
        )
    }
    private val path = Path()
    private val rect = RectF()

    private fun col(s: String): Int = Color.parseColor(s)

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1f - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1f - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
    )

    private fun f(color: Int) {
        fill.style = Paint.Style.FILL
        fill.shader = null
        fill.color = color
        fill.alpha = 255
    }

    private fun ln(color: Int, width: Float) {
        line.color = color
        line.strokeWidth = width
        line.alpha = 255
    }

    private fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int, stroke: Int, sw: Float) {
        rect.set(l, t, r, b)
        f(color)
        c.drawRoundRect(rect, rad, rad, fill)
        ln(stroke, sw)
        c.drawRoundRect(rect, rad, rad, line)
    }

    private fun halo(c: Canvas, cx: Float, cy: Float, pulse: Float) {
        c.save()
        c.translate(cx, cy)
        glow.alpha = (255f * pulse).toInt().coerceIn(0, 255)
        c.drawCircle(0f, 0f, 14f, glow)
        c.restore()
    }

    /** A glowing window with a cross. */
    private fun window(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, pulse: Float) {
        halo(c, cx, cy, pulse)
        box(c, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, 2.5f, col("#FFE27A"), col("#E0A800"), 1.1f)
        c.drawLine(cx, cy - h / 2, cx, cy + h / 2, line)
        c.drawLine(cx - w / 2, cy, cx + w / 2, cy, line)
    }

    /** An arched door: warm light inside and a leaf that swings open ([open] 0..1). */
    private fun door(c: Canvas, cx: Float, top: Float, w: Float, bottom: Float, open: Float, leaf: Int) {
        val l = cx - w / 2
        val r = cx + w / 2
        val a = w / 2
        path.reset()
        path.moveTo(l, bottom)
        path.lineTo(l, top + a)
        path.quadTo(l, top, cx, top)
        path.quadTo(r, top, r, top + a)
        path.lineTo(r, bottom)
        path.close()
        f(col("#FFD98A"))
        c.drawPath(path, fill)
        val leafW = w * (1f - 0.8f * open)
        box(c, l, top + 2f, l + leafW, bottom, 3f, leaf, mix(leaf, Color.BLACK, 0.25f), 1.1f)
        f(col("#FFE27A"))
        c.drawCircle(l + leafW - 2.6f, (top + bottom) / 2f + 3f, 1.3f, fill)
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        path.reset()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val a = (-90f + i * 36f) * (PI.toFloat() / 180f)
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        f(color)
        c.drawPath(path, fill)
    }

    fun draw(c: Canvas, id: Int, level: Int, clock: Float, door: Float) {
        val pulse = 0.75f + 0.25f * sin(clock * 2.4f)
        when (id) {
            0 -> cloudHouse(c, clock, door, pulse)
            1 -> mushroomHouse(c, door, pulse)
            2 -> crystalPalace(c, door, pulse)
            3 -> treeHouse(c, door, pulse)
            4 -> starPalace(c, door, pulse)
            else -> snowHouse(c, clock, door, pulse)
        }
        extras(c, id, level, clock, pulse)
    }

    // ---------- 0: the floating house on the clouds ----------
    private fun cloudHouse(c: Canvas, clock: Float, open: Float, pulse: Float) {
        c.save()
        c.translate(0f, sin(clock * 1.6f + 1f) * 1.2f)
        f(col("#EEE9FF"))
        rect.set(12f, 90f, 84f, 101f)
        c.drawRoundRect(rect, 6f, 6f, fill)
        f(Color.WHITE)
        c.drawCircle(22f, 90f, 11f, fill)
        c.drawCircle(38f, 86f, 14f, fill)
        c.drawCircle(56f, 88f, 13f, fill)
        c.drawCircle(73f, 91f, 10f, fill)
        rect.set(14f, 88f, 82f, 98f)
        c.drawRoundRect(rect, 5f, 5f, fill)
        c.restore()

        f(col("#5B3FD6"))
        rect.set(58f, 26f, 66f, 44f)
        c.drawRect(rect, fill)
        box(c, 22f, 50f, 74f, 88f, 4f, col("#F6EEFF"), col("#CBB8FF"), 1.8f)
        path.reset()
        path.moveTo(16f, 52f)
        path.lineTo(48f, 22f)
        path.lineTo(80f, 52f)
        path.close()
        f(col("#7C5CFF"))
        c.drawPath(path, fill)
        ln(col("#5B3FD6"), 2f)
        c.drawPath(path, line)
        f(col("#FFE27A"))
        c.drawCircle(48f, 38f, 3.2f, fill)
        window(c, 31f, 66f, 10f, 12f, pulse)
        window(c, 65f, 66f, 10f, 12f, pulse)
        door(c, 48f, 66f, 14f, 88f, open, col("#A98BFF"))
    }

    // ---------- 1: the magic mushroom house ----------
    private fun mushroomHouse(c: Canvas, open: Float, pulse: Float) {
        f(col("#7BD88F"))
        rect.set(6f, 88f, 90f, 104f)
        c.drawOval(rect, fill)
        f(col("#5FC577"))
        rect.set(20f, 94f, 76f, 104f)
        c.drawOval(rect, fill)
        f(col("#FF8FB8"))
        c.drawCircle(14f, 93f, 2.2f, fill)
        f(col("#FFE27A"))
        c.drawCircle(82f, 94f, 2.2f, fill)

        box(c, 30f, 40f, 66f, 92f, 10f, col("#FFF1D6"), col("#E3C9A0"), 1.6f)
        rect.set(8f, 10f, 88f, 74f)
        f(col("#E5484D"))
        c.drawArc(rect, 180f, 180f, true, fill)
        ln(col("#B8323A"), 2f)
        c.drawArc(rect, 180f, 180f, true, line)
        box(c, 10f, 38f, 86f, 46f, 4f, col("#FFE3E8"), col("#E5B3BC"), 1.2f)
        f(Color.WHITE)
        c.drawCircle(28f, 28f, 6f, fill)
        c.drawCircle(52f, 20f, 5f, fill)
        c.drawCircle(70f, 30f, 6f, fill)
        c.drawCircle(44f, 34f, 3.5f, fill)
        window(c, 39f, 62f, 8f, 10f, pulse)
        window(c, 57f, 62f, 8f, 10f, pulse)
        door(c, 48f, 70f, 13f, 90f, open, col("#E5484D"))
    }

    // ---------- 2: the crystal palace ----------
    private fun crystalPalace(c: Canvas, open: Float, pulse: Float) {
        f(col("#BEE9FF"))
        for (x in floatArrayOf(10f, 26f, 48f, 70f, 86f)) {
            path.reset()
            path.moveTo(x - 7f, 102f)
            path.lineTo(x, 90f)
            path.lineTo(x + 7f, 102f)
            path.close()
            c.drawPath(path, fill)
        }
        for (side in intArrayOf(0, 1)) {
            val l = if (side == 0) 10f else 64f
            path.reset()
            path.moveTo(l, 88f)
            path.lineTo(l, 46f)
            path.lineTo(l + 11f, 32f)
            path.lineTo(l + 22f, 46f)
            path.lineTo(l + 22f, 88f)
            path.close()
            f(col("#CBEBFF"))
            c.drawPath(path, fill)
            ln(col("#7FC8F0"), 1.6f)
            c.drawPath(path, line)
        }
        path.reset()
        path.moveTo(48f, 2f)
        path.lineTo(66f, 28f)
        path.lineTo(64f, 88f)
        path.lineTo(32f, 88f)
        path.lineTo(30f, 28f)
        path.close()
        f(col("#BEE9FF"))
        c.drawPath(path, fill)
        path.reset()
        path.moveTo(48f, 2f)
        path.lineTo(30f, 28f)
        path.lineTo(32f, 88f)
        path.lineTo(48f, 88f)
        path.close()
        f(col("#DDF3FF"))
        c.drawPath(path, fill)
        ln(col("#7FC8F0"), 1.8f)
        c.drawLine(48f, 2f, 66f, 28f, line)
        c.drawLine(66f, 28f, 64f, 88f, line)
        c.drawLine(30f, 28f, 32f, 88f, line)
        window(c, 48f, 34f, 7f, 10f, pulse)
        window(c, 48f, 54f, 7f, 10f, pulse)
        window(c, 21f, 62f, 6f, 8f, pulse)
        window(c, 75f, 62f, 6f, 8f, pulse)
        door(c, 48f, 68f, 14f, 88f, open, col("#7FC8F0"))
    }

    // ---------- 3: the tree house ----------
    private fun treeHouse(c: Canvas, open: Float, pulse: Float) {
        f(col("#6FCF7A"))
        c.drawCircle(28f, 36f, 22f, fill)
        c.drawCircle(68f, 36f, 22f, fill)
        c.drawCircle(48f, 22f, 22f, fill)
        ln(col("#4FAE5A"), 1.6f)
        c.drawCircle(28f, 36f, 22f, line)
        c.drawCircle(68f, 36f, 22f, line)
        c.drawCircle(48f, 22f, 22f, line)
        f(col("#6B4428"))
        rect.set(26f, 88f, 70f, 102f)
        c.drawOval(rect, fill)
        box(c, 36f, 52f, 60f, 92f, 6f, col("#8B5E3C"), col("#6B4428"), 1.6f)
        box(c, 30f, 38f, 66f, 64f, 3f, col("#D9A66B"), col("#A8743F"), 1.6f)
        ln(col("#B88548"), 1f)
        c.drawLine(30f, 46f, 66f, 46f, line)
        c.drawLine(30f, 54f, 66f, 54f, line)
        path.reset()
        path.moveTo(26f, 40f)
        path.lineTo(48f, 24f)
        path.lineTo(70f, 40f)
        path.close()
        f(col("#5FB760"))
        c.drawPath(path, fill)
        ln(col("#3E8E48"), 2f)
        c.drawPath(path, line)
        window(c, 39f, 52f, 8f, 9f, pulse)
        window(c, 57f, 52f, 8f, 9f, pulse)
        ln(col("#8B5E3C"), 1.6f)
        c.drawLine(74f, 62f, 74f, 94f, line)
        c.drawLine(82f, 62f, 82f, 94f, line)
        var y = 68f
        while (y < 94f) {
            c.drawLine(74f, y, 82f, y, line)
            y += 7f
        }
        door(c, 48f, 70f, 12f, 90f, open, col("#6B4428"))
    }

    // ---------- 4: the star palace ----------
    private fun starPalace(c: Canvas, open: Float, pulse: Float) {
        f(col("#4B3A99"))
        c.drawCircle(20f, 92f, 10f, fill)
        c.drawCircle(38f, 90f, 13f, fill)
        c.drawCircle(58f, 91f, 12f, fill)
        c.drawCircle(76f, 93f, 9f, fill)
        rect.set(14f, 90f, 82f, 102f)
        c.drawRoundRect(rect, 5f, 5f, fill)
        for (side in intArrayOf(0, 1)) {
            val l = if (side == 0) 8f else 72f
            box(c, l, 46f, l + 16f, 90f, 2f, col("#5B3FD6"), col("#FFC93C"), 1.6f)
            path.reset()
            path.moveTo(l - 2f, 48f)
            path.lineTo(l + 8f, 26f)
            path.lineTo(l + 18f, 48f)
            path.close()
            f(col("#FFC93C"))
            c.drawPath(path, fill)
        }
        box(c, 22f, 54f, 74f, 90f, 3f, col("#5B3FD6"), col("#FFC93C"), 2f)
        rect.set(28f, 28f, 68f, 76f)
        f(col("#FFC93C"))
        c.drawArc(rect, 180f, 180f, true, fill)
        ln(col("#E0A800"), 1.8f)
        c.drawArc(rect, 180f, 180f, true, line)
        star(c, 48f, 20f, 7f, col("#FFE27A"))
        window(c, 36f, 68f, 8f, 10f, pulse)
        window(c, 60f, 68f, 8f, 10f, pulse)
        window(c, 16f, 62f, 6f, 8f, pulse)
        window(c, 80f, 62f, 6f, 8f, pulse)
        door(c, 48f, 68f, 14f, 88f, open, col("#FFC93C"))
    }

    // ---------- 5: the snow house (igloo) ----------
    private fun snowHouse(c: Canvas, clock: Float, open: Float, pulse: Float) {
        f(Color.WHITE)
        rect.set(4f, 84f, 92f, 104f)
        c.drawOval(rect, fill)
        ln(col("#D3E6F7"), 1.5f)
        c.drawOval(rect, line)

        rect.set(12f, 30f, 84f, 150f)
        f(col("#F4FAFF"))
        c.drawArc(rect, 180f, 180f, true, fill)
        ln(col("#BFD9F0"), 2f)
        c.drawArc(rect, 180f, 180f, true, line)
        c.save()
        path.reset()
        path.addArc(rect, 180f, 180f)
        path.close()
        c.clipPath(path)
        ln(col("#C9DEF0"), 1.2f)
        var row = 0
        var y = 44f
        while (y < 90f) {
            c.drawLine(8f, y, 88f, y, line)
            var x = if (row % 2 == 0) 20f else 28f
            while (x < 88f) {
                c.drawLine(x, y, x, y + 14f, line)
                x += 16f
            }
            row++
            y += 14f
        }
        c.restore()

        box(c, 34f, 64f, 62f, 92f, 10f, col("#EAF4FF"), col("#BFD9F0"), 1.6f)
        window(c, 70f, 52f, 7f, 7f, pulse)
        door(c, 48f, 70f, 14f, 90f, open, col("#9FD4F5"))

        f(Color.WHITE)
        for (i in 0 until 8) {
            val x = 8f + (i * 11f) % 80f + sin(clock + i) * 2f
            val yy = (clock * 8f + i * 13f) % 100f
            c.drawCircle(x, yy, 1.1f, fill)
        }
    }

    // ---------- what the upgrade levels add ----------
    private fun extras(c: Canvas, id: Int, level: Int, clock: Float, pulse: Float) {
        val accent = HouseDefs.get(id).accent
        if (level >= 2) {
            // a flag on top and flowers at the foot of the house
            val ax = floatArrayOf(48f, 48f, 22f, 48f, 16f, 48f)[id]
            val ay = floatArrayOf(22f, 22f, 34f, 24f, 26f, 34f)[id]
            ln(col("#7A5C3E"), 1.4f)
            c.drawLine(ax, ay, ax, ay - 12f, line)
            path.reset()
            path.moveTo(ax, ay - 12f)
            path.lineTo(ax + 9f, ay - 9f)
            path.lineTo(ax, ay - 6f)
            path.close()
            f(accent)
            c.drawPath(path, fill)
            f(col("#FF8FB8"))
            c.drawCircle(18f, 96f, 2.2f, fill)
            c.drawCircle(76f, 97f, 2.2f, fill)
            f(col("#FFE27A"))
            c.drawCircle(24f, 97f, 2f, fill)
            c.drawCircle(81f, 96f, 2f, fill)
        }
        if (level >= 3) {
            // an extra wing
            box(c, 76f, 66f, 94f, 88f, 3f, mix(accent, Color.WHITE, 0.75f), accent, 1.4f)
            path.reset()
            path.moveTo(74f, 68f)
            path.lineTo(85f, 56f)
            path.lineTo(96f, 68f)
            path.close()
            f(accent)
            c.drawPath(path, fill)
            window(c, 85f, 78f, 6f, 7f, pulse)
        }
        if (level >= 4) {
            // a balcony with a small garden
            box(c, 1f, 70f, 26f, 76f, 2f, mix(accent, Color.WHITE, 0.6f), accent, 1.2f)
            ln(accent, 1.2f)
            for (x in floatArrayOf(3f, 10f, 17f, 24f)) c.drawLine(x, 64f, x, 70f, line)
            c.drawLine(1f, 64f, 26f, 64f, line)
            for (x in floatArrayOf(8f, 19f)) {
                f(col("#C97B4A"))
                rect.set(x - 3.5f, 64f, x + 3.5f, 70f)
                c.drawRect(rect, fill)
                f(col("#5FC577"))
                c.drawCircle(x - 2f, 61f, 3f, fill)
                c.drawCircle(x + 2f, 60f, 3.2f, fill)
                f(col("#FF8FB8"))
                c.drawCircle(x, 58f, 1.8f, fill)
            }
        }
        if (level >= 5) {
            // fairy lights and sparkles
            val colors = intArrayOf(col("#FFE27A"), col("#FF8FB8"), col("#8FD0FF"))
            for (i in 0 until 9) {
                val t = i / 8f
                val x = 10f + 76f * t
                val y = 50f - 40f * sin(PI.toFloat() * t)
                f(colors[i % 3])
                fill.alpha = (150f + 105f * sin(clock * 3f + i)).toInt().coerceIn(0, 255)
                c.drawCircle(x, y, 1.9f, fill)
            }
            f(col("#FFF3B0"))
            for (i in 0 until 3) {
                val a = clock * 1.2f + i * 2.1f
                fill.alpha = (150f + 105f * sin(clock * 4f + i)).toInt().coerceIn(0, 255)
                c.drawCircle(48f + cos(a) * 46f, 50f + sin(a) * 26f, 1.6f, fill)
            }
        }
    }
}
