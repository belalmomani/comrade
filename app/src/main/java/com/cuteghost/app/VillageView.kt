package com.cuteghost.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/** A customer of the restaurant: a village ghost or a tourist creature (cat, frog, bunny, penguin, bear). */
class Customer(val tourist: Int, val villager: Int) {
    var x = 0f
    var homeX = 0f
    var state = WALK_IN
    var timer = 0f
    var food = 0
    var slot = 0
    var phase = 0f
    var seat = -1
    var assigned = false
    var servedByWaiter = false

    companion object {
        const val WALK_IN = 0
        const val ORDER = 1
        const val WAIT = 2
        const val PAY = 3
        const val LEAVE = 4
        const val WALK_TABLE = 5
        const val EAT = 6
        const val WAIT_SERVE = 7
    }
}

/** A coin that jumps up when a customer pays. */
class CoinFx(val x: Float, val y: Float, val amount: Int) {
    var age = 0f
}

/**
 * The village scene: sky, hills, the ghosts' restaurant at the start, then the houses of the grown
 * ghosts (oldest first), and the customers (tourists are drawn here; village ghosts are views).
 */
class VillageView(context: Context, private val villagers: List<Villager>) : View(context) {

    companion object {
        const val SLOT_DP = 150
        const val START_DP = 20
        const val GROUND = 0.64f

        /** The restaurant takes the first slot; the villagers' houses follow. */
        fun restaurantX(d: Float): Float = (START_DP + SLOT_DP / 2f) * d
        fun houseCenterX(index: Int, d: Float): Float = (START_DP + SLOT_DP * (index + 2) + SLOT_DP / 2f) * d
        fun houseScale(d: Float): Float = 1.25f * d
        fun queueX(slot: Int, d: Float): Float = restaurantX(d) + (82f + slot * 48f) * d

        /** The two dining tables in front of the restaurant and the four seats beside them. */
        fun tableX(i: Int, d: Float): Float = restaurantX(d) + (if (i == 0) -50f else 50f) * d
        fun seatX(seat: Int, d: Float): Float = tableX(seat / 2, d) + (if (seat % 2 == 0) -40f else 40f) * d

        /** The fruit and food stand uses the slot between the restaurant and the first house. */
        fun standX(d: Float): Float = (START_DP + SLOT_DP * 1 + SLOT_DP / 2f) * d
    }

    val customers = ArrayList<Customer>()
    val fx = ArrayList<CoinFx>()
    var onTap: ((Float, Float) -> Unit)? = null

    /** 1 counter only, 2 dining tables, 3 a waiter, 4 the fruit and food stand and a bigger menu. */
    var restaurantLevel = 1

    /** The waiter's place and the dish on his tray (-1 = nothing). */
    var waiterX = 0f
    var waiterCarry = -1

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2540")
        textSize = 14f * resources.displayMetrics.density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val path = Path()
    private var clock = 0f
    private var running = false
    private var lastTick = 0L
    private var downX = 0f
    private var downY = 0f
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

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(e.x - downX) < 16f * d && abs(e.y - downY) < 16f * d) onTap?.invoke(e.x, e.y)
                return true
            }
        }
        return super.onTouchEvent(e)
    }

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

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val ground = h * GROUND

        fill.style = Paint.Style.FILL
        fill.shader = LinearGradient(0f, 0f, 0f, ground, Color.parseColor("#8ECBFF"), Color.parseColor("#FFF4E0"), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, fill)
        fill.shader = null

        f(Color.parseColor("#FFE27A"))
        canvas.drawCircle(w.coerceAtMost(900f * d) * 0.82f, h * 0.12f, 34f * d, fill)
        for (i in 0 until 5) {
            val x = ((clock * (7f + i * 2f) * d + i * w * 0.23f) % (w + 200f * d)) - 100f * d
            val y = h * (0.10f + 0.07f * (i % 3))
            f(Color.WHITE, 205)
            canvas.drawCircle(x - 22f * d, y + 4f * d, 15f * d, fill)
            canvas.drawCircle(x, y - 4f * d, 21f * d, fill)
            canvas.drawCircle(x + 24f * d, y + 4f * d, 16f * d, fill)
        }

        f(Color.parseColor("#A9DFA0"))
        var hx = -40f * d
        while (hx < w + 120f * d) {
            rect.set(hx, ground - 70f * d, hx + 220f * d, ground + 60f * d)
            canvas.drawOval(rect, fill)
            hx += 170f * d
        }
        f(Color.parseColor("#7BCB78"))
        canvas.drawRect(0f, ground, w, h, fill)
        f(Color.parseColor("#E9D2A5"))
        rect.set(0f, ground + 18f * d, w, ground + 44f * d)
        canvas.drawRoundRect(rect, 13f * d, 13f * d, fill)
        var fx0 = 30f * d
        var k = 0
        while (fx0 < w) {
            f(if (k % 2 == 0) Color.parseColor("#FF8FB8") else Color.parseColor("#FFE27A"))
            canvas.drawCircle(fx0, ground + 70f * d + (k % 3) * 22f * d, 3.2f * d, fill)
            fx0 += 53f * d
            k++
        }

        drawRestaurant(canvas, restaurantX(d), ground)
        drawStand(canvas, ground)
        drawTables(canvas, ground)

        if (villagers.isEmpty()) {
            val cx = minOf(w, resources.displayMetrics.widthPixels.toFloat()) * 0.64f
            text.textSize = 15f * d
            canvas.drawText("عندما يكبر ابن ويصبح بحجم أمه", cx, h * 0.28f, text)
            canvas.drawText("ينتقل ليعيش هنا في بيت خاص", cx, h * 0.28f + 22f * d, text)
            text.textSize = 14f * d
        }

        val s = houseScale(d)
        for ((i, v) in villagers.withIndex()) {
            val cx = houseCenterX(i, d)
            canvas.save()
            canvas.translate(cx - 48f * s, ground - 88f * s + sin(clock * 1.2f + i) * 1.5f * d)
            canvas.scale(s, s)
            HouseArt.draw(canvas, v.houseId, v.level, clock, if (sin(clock * 0.8f + i * 1.7f) > 0.7f) 1f else 0f)
            canvas.restore()

            f(Color.WHITE, 235)
            val half = 54f * d
            rect.set(cx - half, ground + 78f * d, cx + half, ground + 104f * d)
            canvas.drawRoundRect(rect, 12f * d, 12f * d, fill)
            canvas.drawText(v.label, cx, ground + 97f * d, text)
        }

        // Customers: tourist creatures are drawn here, and every customer shows what it wants
        for (c in customers) {
            if (c.tourist >= 0) drawTourist(canvas, c, ground + 56f * d)
            if (c.state == Customer.ORDER || c.state == Customer.WAIT || c.state == Customer.WAIT_SERVE) {
                drawOrder(canvas, c, ground + 56f * d - 92f * d)
            }
            if (c.state == Customer.EAT && c.seat >= 0) {
                // The dish on the table while the customer eats
                f(Color.WHITE)
                canvas.drawOval(RectF(tableX(c.seat / 2, d) - 12f * d, ground + 30f * d, tableX(c.seat / 2, d) + 12f * d, ground + 42f * d), fill)
                drawFoodIcon(canvas, c.food, tableX(c.seat / 2, d), ground + 34f * d, 1.0f * d)
            }
        }
        if (waiterCarry >= 0) {
            // The waiter's tray
            rr(canvas, waiterX + 8f * d, ground + 56f * d - 40f * d, waiterX + 40f * d, ground + 56f * d - 34f * d, 3f * d, Color.parseColor("#C9D1D9"))
            drawFoodIcon(canvas, waiterCarry, waiterX + 24f * d, ground + 56f * d - 47f * d, 1.0f * d)
        }
        for (coin in fx) drawCoinFx(canvas, coin)
    }

    private fun drawRestaurant(c: Canvas, rx: Float, ground: Float) {
        val bw = 70f * d
        rr(c, rx - bw, ground - 112f * d, rx + bw, ground, 10f * d, Color.parseColor("#FFE3C2"))
        line.color = Color.parseColor("#E0A458")
        line.strokeWidth = 2f * d
        rect.set(rx - bw, ground - 112f * d, rx + bw, ground)
        c.drawRoundRect(rect, 10f * d, 10f * d, line)
        // Striped awning with scallops
        val stripe = bw * 2f / 7f
        for (i in 0 until 7) {
            val l = rx - bw + i * stripe
            f(if (i % 2 == 0) Color.parseColor("#E5484D") else Color.WHITE)
            c.drawRect(l, ground - 112f * d, l + stripe, ground - 88f * d, fill)
            c.drawCircle(l + stripe / 2f, ground - 88f * d, stripe / 2f, fill)
        }
        // The sign
        rr(c, rx - 48f * d, ground - 138f * d, rx + 48f * d, ground - 114f * d, 8f * d, Color.parseColor("#8B5E3C"))
        val old = text.color
        text.color = Color.WHITE
        text.textSize = 12.5f * d
        c.drawText("مطعم الأشباح", rx, ground - 121f * d, text)
        text.color = old
        text.textSize = 14f * d
        // The serving window with a warm light, the counter and a steaming pot
        rr(c, rx - 50f * d, ground - 78f * d, rx + 50f * d, ground - 40f * d, 6f * d, Color.parseColor("#FFD98A"))
        rr(c, rx - 56f * d, ground - 40f * d, rx + 56f * d, ground - 22f * d, 6f * d, Color.parseColor("#C97B4A"))
        rr(c, rx - 56f * d, ground - 40f * d, rx + 56f * d, ground - 35f * d, 4f * d, Color.parseColor("#E0A458"))
        val px = rx + 32f * d
        rr(c, px - 13f * d, ground - 58f * d, px + 13f * d, ground - 40f * d, 5f * d, Color.parseColor("#5C5675"))
        for (i in 0 until 3) {
            val age = (clock * 0.7f + i * 0.33f) % 1f
            f(Color.WHITE, ((1f - age) * 200f).toInt())
            c.drawCircle(px + sin(clock * 2f + i) * 5f * d, ground - 62f * d - age * 30f * d, (5f + age * 5f) * d, fill)
        }
    }

    /** Two round dining tables with red cloths and chairs (restaurant level 2 and up). */
    private fun drawTables(c: Canvas, ground: Float) {
        if (restaurantLevel < 2) return
        for (i in 0 until 2) {
            val tx = tableX(i, d)
            val ty = ground + 44f * d
            rr(c, tx - 48f * d, ty - 8f * d, tx - 38f * d, ty + 22f * d, 3f * d, Color.parseColor("#C97B4A"))
            rr(c, tx + 38f * d, ty - 8f * d, tx + 48f * d, ty + 22f * d, 3f * d, Color.parseColor("#C97B4A"))
            rr(c, tx - 3f * d, ty + 4f * d, tx + 3f * d, ty + 24f * d, 1.5f * d, Color.parseColor("#8B5E3C"))
            f(Color.parseColor("#FFF1D6"))
            c.drawOval(RectF(tx - 32f * d, ty - 12f * d, tx + 32f * d, ty + 8f * d), fill)
            line.color = Color.parseColor("#E5484D")
            line.strokeWidth = 2.5f * d
            c.drawOval(RectF(tx - 26f * d, ty - 9f * d, tx + 26f * d, ty + 5f * d), line)
        }
    }

    /** The fruit and food stand (level 4), or a signpost for the empty plot before that. */
    private fun drawStand(c: Canvas, ground: Float) {
        val sx = standX(d)
        if (restaurantLevel < 4) {
            rr(c, sx - 3f * d, ground - 40f * d, sx + 3f * d, ground, 1.5f * d, Color.parseColor("#8B5E3C"))
            rr(c, sx - 36f * d, ground - 60f * d, sx + 36f * d, ground - 34f * d, 6f * d, Color.parseColor("#B98A5E"))
            val old = text.color
            text.color = Color.WHITE
            text.textSize = 12f * d
            c.drawText("بسطة قريبًا", sx, ground - 42f * d, text)
            text.color = old
            text.textSize = 14f * d
            return
        }
        rr(c, sx - 52f * d, ground - 36f * d, sx + 52f * d, ground - 8f * d, 6f * d, Color.parseColor("#C97B4A"))
        rr(c, sx - 52f * d, ground - 36f * d, sx + 52f * d, ground - 30f * d, 4f * d, Color.parseColor("#E0A458"))
        rr(c, sx - 52f * d, ground - 96f * d, sx - 47f * d, ground - 8f * d, 2f * d, Color.parseColor("#8B5E3C"))
        rr(c, sx + 47f * d, ground - 96f * d, sx + 52f * d, ground - 8f * d, 2f * d, Color.parseColor("#8B5E3C"))
        val stripe = 104f * d / 5f
        for (i in 0 until 5) {
            val l = sx - 52f * d + i * stripe
            f(if (i % 2 == 0) Color.parseColor("#2EAD4B") else Color.WHITE)
            c.drawRect(l, ground - 96f * d, l + stripe, ground - 74f * d, fill)
            c.drawCircle(l + stripe / 2f, ground - 74f * d, stripe / 2f, fill)
        }
        // Fruit on the counter
        val colors = intArrayOf(Color.parseColor("#E63946"), Color.parseColor("#FF9F1C"), Color.parseColor("#FFD93B"), Color.parseColor("#4CAF50"), Color.parseColor("#E63946"))
        for (i in 0 until 5) {
            f(colors[i])
            c.drawCircle(sx - 40f * d + i * 20f * d, ground - 42f * d, 8f * d, fill)
        }
        rr(c, sx - 38f * d, ground - 120f * d, sx + 38f * d, ground - 98f * d, 7f * d, Color.parseColor("#8B5E3C"))
        val old = text.color
        text.color = Color.WHITE
        text.textSize = 12f * d
        c.drawText("فواكه وأطعمة", sx, ground - 104f * d, text)
        text.color = old
        text.textSize = 14f * d
    }

    private fun drawFoodIcon(c: Canvas, food: Int, cx: Float, cy: Float, s: Float) {
        when (food % 8) {
            0 -> { // apple
                f(Color.parseColor("#E63946"))
                c.drawCircle(cx, cy + 1f * s, 9f * s, fill)
                f(Color.parseColor("#4CAF50"))
                c.drawCircle(cx + 4f * s, cy - 9f * s, 3f * s, fill)
            }
            1 -> { // banana
                line.color = Color.parseColor("#FFD93B")
                line.strokeWidth = 6f * s
                rect.set(cx - 10f * s, cy - 11f * s, cx + 10f * s, cy + 9f * s)
                c.drawArc(rect, 30f, 120f, false, line)
            }
            2 -> { // cookie
                f(Color.parseColor("#DDA25F"))
                c.drawCircle(cx, cy, 9f * s, fill)
                f(Color.parseColor("#5B3A22"))
                c.drawCircle(cx - 3f * s, cy - 3f * s, 1.6f * s, fill)
                c.drawCircle(cx + 3f * s, cy + 1f * s, 1.6f * s, fill)
                c.drawCircle(cx - 1f * s, cy + 4f * s, 1.6f * s, fill)
            }
            4 -> { // watermelon slice
                f(Color.parseColor("#4CAF50"))
                rect.set(cx - 11f * s, cy - 9f * s, cx + 11f * s, cy + 13f * s)
                c.drawArc(rect, 0f, 180f, true, fill)
                f(Color.parseColor("#E63946"))
                rect.set(cx - 8.5f * s, cy - 9f * s, cx + 8.5f * s, cy + 9f * s)
                c.drawArc(rect, 0f, 180f, true, fill)
                f(Color.parseColor("#2B2540"))
                c.drawCircle(cx - 3f * s, cy + 1f * s, 1.1f * s, fill)
                c.drawCircle(cx + 3f * s, cy + 3f * s, 1.1f * s, fill)
            }
            5 -> { // orange
                f(Color.parseColor("#FF9F1C"))
                c.drawCircle(cx, cy + 1f * s, 9f * s, fill)
                f(Color.parseColor("#4CAF50"))
                c.drawCircle(cx + 4f * s, cy - 9f * s, 3f * s, fill)
            }
            6 -> { // sandwich
                rr(c, cx - 10f * s, cy - 9f * s, cx + 10f * s, cy - 2f * s, 4f * s, Color.parseColor("#E0A458"))
                rr(c, cx - 11f * s, cy - 2f * s, cx + 11f * s, cy + 1f * s, 1.5f * s, Color.parseColor("#6BCB77"))
                rr(c, cx - 10f * s, cy + 1f * s, cx + 10f * s, cy + 5f * s, 1.5f * s, Color.parseColor("#C97B4A"))
                rr(c, cx - 10f * s, cy + 5f * s, cx + 10f * s, cy + 10f * s, 3f * s, Color.parseColor("#E0A458"))
            }
            7 -> { // pizza slice
                path.reset()
                path.moveTo(cx - 10f * s, cy - 8f * s)
                path.lineTo(cx + 10f * s, cy - 8f * s)
                path.lineTo(cx, cy + 12f * s)
                path.close()
                f(Color.parseColor("#FFC857"))
                c.drawPath(path, fill)
                rr(c, cx - 11f * s, cy - 12f * s, cx + 11f * s, cy - 7f * s, 2.5f * s, Color.parseColor("#E0A458"))
                f(Color.parseColor("#E63946"))
                c.drawCircle(cx - 3f * s, cy - 2f * s, 2.2f * s, fill)
                c.drawCircle(cx + 3f * s, cy + 2f * s, 2.2f * s, fill)
            }
            else -> { // ice cream
                path.reset()
                path.moveTo(cx - 6f * s, cy + 1f * s)
                path.lineTo(cx + 6f * s, cy + 1f * s)
                path.lineTo(cx, cy + 12f * s)
                path.close()
                f(Color.parseColor("#E0A458"))
                c.drawPath(path, fill)
                f(Color.parseColor("#FFB6D0"))
                c.drawCircle(cx, cy - 3f * s, 7f * s, fill)
                f(Color.parseColor("#FFF1D6"))
                c.drawCircle(cx, cy - 9f * s, 5.5f * s, fill)
            }
        }
    }

    private fun drawOrder(c: Canvas, cu: Customer, y: Float) {
        f(Color.WHITE, 245)
        rect.set(cu.x - 24f * d, y - 22f * d, cu.x + 24f * d, y + 22f * d)
        c.drawRoundRect(rect, 14f * d, 14f * d, fill)
        path.reset()
        path.moveTo(cu.x - 6f * d, y + 21f * d)
        path.lineTo(cu.x + 6f * d, y + 21f * d)
        path.lineTo(cu.x, y + 30f * d)
        path.close()
        c.drawPath(path, fill)
        drawFoodIcon(c, cu.food, cu.x, y, 1.5f * d)
    }

    private fun drawCoinFx(c: Canvas, coin: CoinFx) {
        val k = (coin.age / 1.2f).coerceIn(0f, 1f)
        val y = coin.y - 70f * d * k
        f(Color.parseColor("#FFC93C"), ((1f - k) * 255f).toInt())
        c.drawCircle(coin.x, y, 11f * d, fill)
        f(Color.parseColor("#E0A800"), ((1f - k) * 255f).toInt())
        c.drawCircle(coin.x, y, 7f * d, fill)
        val old = text.color
        text.color = Color.parseColor("#2B7A3D")
        text.alpha = ((1f - k) * 255f).toInt()
        text.textSize = 15f * d
        c.drawText("+${coin.amount}", coin.x + 26f * d, y + 5f * d, text)
        text.color = old
        text.alpha = 255
        text.textSize = 14f * d
    }

    /** The five tourist creatures: cat, frog, bunny, penguin and bear, each with a small red backpack. */
    private fun drawTourist(c: Canvas, cu: Customer, footY: Float) {
        val moving = cu.state == Customer.WALK_IN || cu.state == Customer.LEAVE
        val bob = if (moving) abs(sin(cu.phase * 9f)) * 3f * d else 0f
        val by = footY - bob
        val x = cu.x
        val body = intArrayOf(
            Color.parseColor("#F4A261"), Color.parseColor("#6BCB77"), Color.parseColor("#FFFFFF"),
            Color.parseColor("#2B2540"), Color.parseColor("#A9744F")
        )[cu.tourist % 5]
        // Legs, backpack, body and head
        f(Color.parseColor("#7A5C3E"))
        c.drawOval(RectF(x - 12f * d, by - 6f * d, x - 2f * d, by + 2f * d), fill)
        c.drawOval(RectF(x + 2f * d, by - 6f * d, x + 12f * d, by + 2f * d), fill)
        f(Color.parseColor("#E5484D"))
        c.drawRoundRect(RectF(x - 22f * d, by - 34f * d, x - 10f * d, by - 14f * d), 4f * d, 4f * d, fill)
        f(body)
        c.drawOval(RectF(x - 16f * d, by - 38f * d, x + 16f * d, by - 4f * d), fill)
        c.drawCircle(x, by - 50f * d, 15f * d, fill)
        line.color = Color.parseColor("#33000000")
        line.strokeWidth = 1.5f * d
        c.drawCircle(x, by - 50f * d, 15f * d, line)
        when (cu.tourist % 5) {
            0 -> { // cat: pointed ears and a tail
                f(body)
                path.reset(); path.moveTo(x - 14f * d, by - 56f * d); path.lineTo(x - 9f * d, by - 72f * d); path.lineTo(x - 3f * d, by - 62f * d); path.close(); c.drawPath(path, fill)
                path.reset(); path.moveTo(x + 14f * d, by - 56f * d); path.lineTo(x + 9f * d, by - 72f * d); path.lineTo(x + 3f * d, by - 62f * d); path.close(); c.drawPath(path, fill)
                line.color = body
                line.strokeWidth = 4f * d
                rect.set(x + 8f * d, by - 28f * d, x + 28f * d, by - 4f * d)
                c.drawArc(rect, 270f, 160f, false, line)
            }
            1 -> { // frog: eyes on top of the head and a wide mouth
                f(Color.WHITE)
                c.drawCircle(x - 8f * d, by - 63f * d, 6f * d, fill)
                c.drawCircle(x + 8f * d, by - 63f * d, 6f * d, fill)
                f(Color.parseColor("#2B2540"))
                c.drawCircle(x - 8f * d, by - 63f * d, 2.6f * d, fill)
                c.drawCircle(x + 8f * d, by - 63f * d, 2.6f * d, fill)
            }
            2 -> { // bunny: long ears with pink insides
                for (side in intArrayOf(-1, 1)) {
                    f(body)
                    c.drawOval(RectF(x + side * 7f * d - 5f * d, by - 84f * d, x + side * 7f * d + 5f * d, by - 56f * d), fill)
                    f(Color.parseColor("#FFB6D0"))
                    c.drawOval(RectF(x + side * 7f * d - 2.6f * d, by - 80f * d, x + side * 7f * d + 2.6f * d, by - 60f * d), fill)
                }
            }
            3 -> { // penguin: white belly and an orange beak
                f(Color.WHITE)
                c.drawOval(RectF(x - 10f * d, by - 34f * d, x + 10f * d, by - 8f * d), fill)
                f(Color.parseColor("#FF9F1C"))
                path.reset(); path.moveTo(x - 4f * d, by - 49f * d); path.lineTo(x + 4f * d, by - 49f * d); path.lineTo(x, by - 43f * d); path.close(); c.drawPath(path, fill)
            }
            else -> { // bear: round ears and a lighter muzzle
                f(body)
                c.drawCircle(x - 12f * d, by - 63f * d, 6f * d, fill)
                c.drawCircle(x + 12f * d, by - 63f * d, 6f * d, fill)
                f(Color.parseColor("#E3C29F"))
                c.drawOval(RectF(x - 7f * d, by - 49f * d, x + 7f * d, by - 40f * d), fill)
            }
        }
        // Eyes (not for the frog, whose eyes are on top) and a small smile
        if (cu.tourist % 5 != 1) {
            f(Color.parseColor("#2B2540"))
            c.drawCircle(x - 5f * d, by - 53f * d, 2f * d, fill)
            c.drawCircle(x + 5f * d, by - 53f * d, 2f * d, fill)
        }
        line.color = Color.parseColor("#2B2540")
        line.strokeWidth = 1.6f * d
        rect.set(x - 4f * d, by - 49f * d, x + 4f * d, by - 43f * d)
        c.drawArc(rect, 20f, 140f, false, line)
    }
}
