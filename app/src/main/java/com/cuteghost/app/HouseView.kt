package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/** A small cartoon house on a transparent background. The door opens when a baby goes in or out. */
class HouseView(context: Context) : View(context) {

    /** 0 = door closed, 1 = fully open. */
    var doorTarget = 0f
    private var door = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        door += (doorTarget - door) * 0.2f
        if (abs(doorTarget - door) > 0.01f) postInvalidateOnAnimation() else door = doorTarget

        val s = min(width, height) / 88f
        canvas.save()
        canvas.translate((width - 88f * s) / 2f, (height - 88f * s) / 2f)
        canvas.scale(s, s)

        // Chimney
        fill.style = Paint.Style.FILL
        fill.color = Color.parseColor("#B5543B")
        rect.set(62f, 14f, 72f, 34f)
        canvas.drawRect(rect, fill)

        // Walls
        fill.color = Color.parseColor("#FFE9C7")
        rect.set(12f, 42f, 76f, 82f)
        canvas.drawRoundRect(rect, 4f, 4f, fill)
        line.color = Color.parseColor("#C9A872")
        line.strokeWidth = 2f
        canvas.drawRoundRect(rect, 4f, 4f, line)

        // Roof
        path.reset()
        path.moveTo(4f, 44f)
        path.lineTo(44f, 8f)
        path.lineTo(84f, 44f)
        path.close()
        fill.color = Color.parseColor("#E0674B")
        canvas.drawPath(path, fill)
        line.color = Color.parseColor("#A8442F")
        line.strokeWidth = 2.2f
        canvas.drawPath(path, line)

        // Windows
        for (cx in floatArrayOf(24f, 64f)) {
            fill.color = Color.parseColor("#BFE3FF")
            canvas.drawCircle(cx, 60f, 5.5f, fill)
            line.color = Color.parseColor("#7FA6C4")
            line.strokeWidth = 1.4f
            canvas.drawCircle(cx, 60f, 5.5f, line)
            canvas.drawLine(cx - 5.5f, 60f, cx + 5.5f, 60f, line)
            canvas.drawLine(cx, 54.5f, cx, 65.5f, line)
        }

        // Door (dark inside, the leaf swings open)
        path.reset()
        path.moveTo(36f, 82f)
        path.lineTo(36f, 64f)
        path.quadTo(36f, 56f, 44f, 56f)
        path.quadTo(52f, 56f, 52f, 64f)
        path.lineTo(52f, 82f)
        path.close()
        fill.color = Color.parseColor("#3E2A1A")
        canvas.drawPath(path, fill)
        val leafW = 16f * (1f - 0.75f * door)
        fill.color = Color.parseColor("#8B5E3C")
        rect.set(36f, 58f, 36f + leafW, 82f)
        canvas.drawRoundRect(rect, 3f, 3f, fill)
        fill.color = Color.parseColor("#FFD54A")
        canvas.drawCircle(36f + leafW - 3f, 71f, 1.5f, fill)

        canvas.restore()
    }
}
