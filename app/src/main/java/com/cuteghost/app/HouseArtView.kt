package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import kotlin.math.min

/** A small animated picture of one house at one level (used to compare levels before upgrading). */
class HouseArtView(context: Context, private val houseId: Int, private val level: Int) : View(context) {
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
        val s = min(width / 96f, height / 104f)
        canvas.save()
        canvas.translate((width - 96f * s) / 2f, (height - 104f * s) / 2f)
        canvas.scale(s, s)
        HouseArt.draw(canvas, houseId, level, clock, 0f)
        canvas.restore()
    }
}
