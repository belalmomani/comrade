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

/** A shop card: a picture of the item, its name and its state (placed, owned, free, price). */
class FurnCardView(
    context: Context,
    private val item: Furn,
    var owned: Boolean,
    var placed: Boolean,
    var chosen: Boolean
) : View(context) {

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
    }
    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2540")
        textSize = 12f * resources.displayMetrics.density
        isFakeBoldText = true
    }
    private val chipPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * resources.displayMetrics.density
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

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(2f * d, 2f * d, w - 2f * d, h - 2f * d)
        fill.style = Paint.Style.FILL
        fill.color = Color.parseColor("#F3EEFF")
        canvas.drawRoundRect(rect, 14f * d, 14f * d, fill)
        stroke.color = if (chosen) Color.parseColor("#FFC93C") else Color.parseColor("#D9CCFF")
        canvas.drawRoundRect(rect, 14f * d, 14f * d, stroke)

        // Dark backgrounds for pale items (walls, curtains) and magic items
        fill.color = Color.parseColor("#E6DDFB")
        rect.set(8f * d, 8f * d, w - 8f * d, h * 0.52f)
        canvas.drawRoundRect(rect, 10f * d, 10f * d, fill)
        canvas.save()
        canvas.translate(10f * d, 10f * d)
        FurnArt.drawFit(canvas, item, w - 20f * d, h * 0.52f - 12f * d, clock)
        canvas.restore()

        val layout = StaticLayout.Builder.obtain(item.name, 0, item.name.length, namePaint, (w - 12f * d).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).build()
        canvas.save()
        canvas.translate(6f * d, h * 0.55f)
        layout.draw(canvas)
        canvas.restore()

        val chip = when {
            placed -> "موضوع"
            owned && item.source == FSource.FREE -> "مجاني"
            owned -> "مملوك"
            item.source == FSource.STARS -> "${item.stars} نجمة"
            else -> "إعلان • ${item.stars}"
        }
        fill.color = when {
            placed -> Color.parseColor("#E0A800")
            owned -> Color.parseColor("#4FAE5A")
            item.source == FSource.AD -> Color.parseColor("#FF8FB8")
            else -> Color.parseColor("#7C5CFF")
        }
        rect.set(w / 2f - 38f * d, h - 28f * d, w / 2f + 38f * d, h - 8f * d)
        canvas.drawRoundRect(rect, 10f * d, 10f * d, fill)
        canvas.drawText(chip, w / 2f, h - 14f * d, chipPaint)
    }
}
