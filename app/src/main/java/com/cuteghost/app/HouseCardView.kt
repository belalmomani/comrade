package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import kotlin.math.min

/** A card showing one house (animated), its name and whether it is open, chosen or locked. */
class HouseCardView(
    context: Context,
    private val def: HouseDef,
    private val unlocked: Boolean,
    private val selected: Boolean,
    private val level: Int
) : View(context) {

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
    }
    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2540")
        textSize = 14f * resources.displayMetrics.density
        isFakeBoldText = true
    }
    private val chipPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * resources.displayMetrics.density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
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

    private fun tint(color: Int, t: Float): Int = Color.rgb(
        (Color.red(color) * (1f - t) + 255f * t).toInt(),
        (Color.green(color) * (1f - t) + 255f * t).toInt(),
        (Color.blue(color) * (1f - t) + 255f * t).toInt()
    )

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(2f * d, 2f * d, w - 2f * d, h - 2f * d)
        fill.style = Paint.Style.FILL
        fill.color = tint(def.accent, 0.86f)
        canvas.drawRoundRect(rect, 18f * d, 18f * d, fill)
        stroke.color = if (selected) Color.parseColor("#FFC93C") else tint(def.accent, 0.35f)
        canvas.drawRoundRect(rect, 18f * d, 18f * d, stroke)

        // The house
        val artH = h * 0.62f
        val s = min(w * 0.9f / 96f, artH / 104f)
        canvas.save()
        canvas.translate((w - 96f * s) / 2f, 8f * d)
        canvas.scale(s, s)
        HouseArt.draw(canvas, def.id, level, clock, 0f)
        canvas.restore()

        if (!unlocked) {
            fill.color = Color.parseColor("#66000000")
            rect.set(4f * d, 4f * d, w - 4f * d, h * 0.66f)
            canvas.drawRoundRect(rect, 16f * d, 16f * d, fill)
            // a padlock
            val cx = w / 2f
            val cy = h * 0.30f
            fill.color = Color.parseColor("#FFC93C")
            rect.set(cx - 13f * d, cy - 2f * d, cx + 13f * d, cy + 18f * d)
            canvas.drawRoundRect(rect, 5f * d, 5f * d, fill)
            stroke.color = Color.parseColor("#FFC93C")
            rect.set(cx - 8f * d, cy - 16f * d, cx + 8f * d, cy + 4f * d)
            canvas.drawArc(rect, 180f, 180f, false, stroke)
            fill.color = Color.parseColor("#7A5A00")
            canvas.drawCircle(cx, cy + 8f * d, 2.5f * d, fill)
        }

        // Name (two lines at most)
        val layout = StaticLayout.Builder.obtain(def.name, 0, def.name.length, namePaint, (w - 16f * d).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).build()
        canvas.save()
        canvas.translate(8f * d, h * 0.66f)
        layout.draw(canvas)
        canvas.restore()

        // Status chip
        val chip = when {
            selected -> "المنزل الحالي"
            unlocked -> "مفتوح"
            else -> "${def.starCost} نجمة"
        }
        fill.color = when {
            selected -> Color.parseColor("#E0A800")
            unlocked -> Color.parseColor("#4FAE5A")
            else -> Color.parseColor("#7C5CFF")
        }
        rect.set(w / 2f - 46f * d, h - 30f * d, w / 2f + 46f * d, h - 8f * d)
        canvas.drawRoundRect(rect, 11f * d, 11f * d, fill)
        canvas.drawText(chip, w / 2f, h - 14f * d, chipPaint)
    }
}
