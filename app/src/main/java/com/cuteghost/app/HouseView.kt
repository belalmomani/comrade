package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import kotlin.math.min
import kotlin.math.sin

/** The floating house on the screen: the chosen design and level, gently bobbing. */
class HouseView(context: Context) : View(context) {

    /** 0 = door closed, 1 = fully open. */
    var doorTarget = 0f
    private var door = 0f
    private var clock = 0f
    private var running = false
    private var lastTick = 0L
    private var houseId = 0
    private var level = 1

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastTick) / 1000f).coerceIn(0f, 0.05f)
            lastTick = now
            clock += dt
            door += (doorTarget - door) * min(1f, dt * 9f)
            invalidate()
            postOnAnimation(this)
        }
    }

    fun setLook(id: Int, newLevel: Int) {
        houseId = id
        level = newLevel
        invalidate()
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
        canvas.translate(0f, sin(clock * 1.6f) * 2.5f)
        HouseArt.draw(canvas, houseId, level, clock, door)
        canvas.restore()
    }
}
