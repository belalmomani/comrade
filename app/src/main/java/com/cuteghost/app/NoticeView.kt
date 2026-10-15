package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View

/** Wrapped text on a transparent background, with a white outline so it stays readable. */
class NoticeView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val fill = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2540")
        textSize = 18f * d
        isFakeBoldText = true
    }
    private val outline = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f * d
        isFakeBoldText = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 5f * d
    }
    private var fillLayout: StaticLayout? = null
    private var outlineLayout: StaticLayout? = null

    fun setText(text: String, widthPx: Int) {
        val w = widthPx - (16f * d).toInt()
        fillLayout = StaticLayout.Builder.obtain(text, 0, text.length, fill, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        outlineLayout = StaticLayout.Builder.obtain(text, 0, text.length, outline, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
    }

    fun contentHeight(): Int = (fillLayout?.height ?: 0) + (16f * d).toInt()

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(8f * d, 8f * d)
        outlineLayout?.draw(canvas)
        fillLayout?.draw(canvas)
        canvas.restore()
    }
}
