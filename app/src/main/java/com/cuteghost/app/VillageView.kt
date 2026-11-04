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
import kotlin.random.Random

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
    var patience = 0f
    var angryShown = false

    companion object {
        const val WALK_IN = 0
        const val ORDER = 1
        const val WAIT = 2
        const val PAY = 3
        const val LEAVE = 4
        const val WALK_TABLE = 5
        const val EAT = 6
        const val WAIT_SERVE = 7

        /** The dish is ready and waits for the child to touch the customer. */
        const val READY = 8
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

        /** The farm has four plots; this is the middle of plot [i] when the farm starts at slot [slot]. */
        fun plotCenterX(slot: Int, i: Int, d: Float): Float = (START_DP + SLOT_DP * slot + 38f + 70f * i) * d
        fun farmerHomeX(slot: Int, d: Float): Float = (START_DP + SLOT_DP * slot + 14f) * d
    }

    val customers = ArrayList<Customer>()
    val fx = ArrayList<CoinFx>()
    var onTap: ((Float, Float) -> Unit)? = null

    /** 1 counter only, 2 dining tables, 3 a waiter, 4 the fruit and food stand and a bigger menu. */
    var restaurantLevel = 1

    /** The waiter's place and the dish on his tray (-1 = nothing). */
    var waiterX = 0f
    var waiterCarry = -1

    /** How far the village was scrolled: far layers move slower than near ones (depth). */
    var camX = 0f

    /** The farm comes after the houses (never before the third slot). */
    var farmSlot = 3
    val plots: Array<PlotState?> = arrayOfNulls(Farm.PLOTS)

    /** What the farmer is doing now (0 nothing, 1 planting, 2 watering, 3 harvesting) and where. */
    var farmerX = 0f
    var farmerAction = 0
    var farmerTarget = -1
    var farmerT = 0f

    private class Vehicle(val lane: Int, val type: Int, val color: Int, val length: Float) {
        var x = 0f
        var speed = 0f
        var maxSpeed = 0f
        var wheel = 0f
    }

    private class Plane(val dir: Float, val y: Float, val speed: Float) {
        var x = 0f
        var trailAcc = 0f
        val trail = ArrayList<FloatArray>()
    }

    private class Flock(val dir: Float, val y: Float, val speed: Float, val count: Int) {
        var x = 0f
    }

    private class Walker(val cu: Customer) {
        var x = 0f
        var y = 0f
        var state = 0
        var timer = 0f
        var dirX = 1f
    }

    private val vehicles = ArrayList<Vehicle>()
    private val planes = ArrayList<Plane>()
    private val flocks = ArrayList<Flock>()
    private val walkers = ArrayList<Walker>()
    private var lifeStarted = false
    private var carTimer = 1.5f
    private var planeTimer = 2.5f
    private var flockTimer = 1.0f
    private var lightT = 0f
    private var lastWalkerCycle = -1

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
            val dt = ((now - lastTick) / 1000f).coerceIn(0f, 0.05f)
            clock += dt
            lastTick = now
            stepLife(dt)
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

        drawBackdrop(canvas, w, h, ground)

        drawRestaurant(canvas, restaurantX(d), ground)
        drawStand(canvas, ground)
        drawTables(canvas, ground)
        drawFarm(canvas, ground)

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
            // A soft shadow under the house
            f(Color.BLACK, 60)
            canvas.drawOval(RectF(cx - 58f * d, ground - 8f * d, cx + 74f * d, ground + 14f * d), fill)
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
            f(Color.BLACK, 55)
            canvas.drawOval(RectF(c.x - 20f * d, ground + 52f * d, c.x + 20f * d, ground + 61f * d), fill)
            if (c.tourist >= 0) drawTourist(canvas, c, ground + 56f * d)
            if (c.state == Customer.ORDER || c.state == Customer.WAIT || c.state == Customer.WAIT_SERVE || c.state == Customer.READY) {
                drawOrder(canvas, c, ground + 56f * d - 92f * d)
            }
            if (c.state == Customer.READY && c.patience >= 14f) drawAnger(canvas, c, ground + 56f * d)
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
        drawLife(canvas, w, h, ground)
        drawForeground(canvas, w, h)
    }


    // ---------------------------------------------------------------- life in the village

    private fun crossX(): Float = 200f * d

    /** The traffic light: 0 green (10 s), 1 yellow (1.5 s), 2 red (6 s). */
    private fun lightState(): Int {
        val t = lightT % 17.5f
        return if (t < 10f) 0 else if (t < 11.5f) 1 else 2
    }

    private fun spawnVehicle(atX: Float?, w: Float) {
        val lane = Random.nextInt(2)
        val type = intArrayOf(0, 0, 1, 2, 3)[Random.nextInt(5)]
        val len = floatArrayOf(74f, 78f, 128f, 100f)[type] * d
        val x = atX ?: if (lane == 0) w + 120f * d else -120f * d
        if (vehicles.any { it.lane == lane && abs(it.x - x) < 170f * d }) return
        val colors = intArrayOf(
            Color.parseColor("#FF6B6B"), Color.parseColor("#4FC3F7"), Color.parseColor("#A29BFE"),
            Color.parseColor("#55D6A5"), Color.parseColor("#FF9F43"), Color.parseColor("#F78FB3")
        )
        val color = if (type == 1) Color.parseColor("#FFD93B") else colors[Random.nextInt(colors.size)]
        val v = Vehicle(lane, type, color, len)
        v.x = x
        v.maxSpeed = (if (type == 2) 85f else 105f + Random.nextFloat() * 45f) * d
        v.speed = v.maxSpeed
        vehicles.add(v)
    }

    /** Cars, buses, taxis and trucks drive in two lanes and stop at the red light; planes and birds cross the sky. */
    private fun stepLife(dt: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val ground = h * GROUND
        val vw = minOf(w, resources.displayMetrics.widthPixels.toFloat())
        lightT += dt
        val light = lightState()

        if (!lifeStarted) {
            lifeStarted = true
            for (i in 0 until 6) spawnVehicle(Random.nextFloat() * w, w)
        }
        carTimer -= dt
        if (carTimer <= 0f) {
            spawnVehicle(null, w)
            carTimer = 2.2f + Random.nextFloat() * 3.5f
        }
        for (v in vehicles) {
            val dir = if (v.lane == 0) -1f else 1f
            var target = v.maxSpeed
            if (light != 0) {
                // Red or yellow: stop before the zebra crossing
                val stopX = crossX() + (if (dir > 0f) -56f * d else 56f * d)
                val front = v.x + dir * v.length / 2f
                val toStop = (stopX - front) * dir
                if (toStop > -2f * d && toStop < 100f * d) {
                    target = if (toStop < 6f * d) 0f else minOf(target, toStop * 1.2f)
                }
            }
            // Keep a distance from the vehicle in front
            var ahead: Vehicle? = null
            var best = Float.MAX_VALUE
            for (o in vehicles) {
                if (o === v || o.lane != v.lane) continue
                val gap = (o.x - v.x) * dir
                if (gap > 0f && gap < best) {
                    best = gap
                    ahead = o
                }
            }
            if (ahead != null) {
                val gap = best - (v.length + ahead.length) / 2f
                if (gap < 22f * d) target = 0f else if (gap < 60f * d) target = minOf(target, ahead.speed)
            }
            v.speed += (target - v.speed) * minOf(1f, dt * 3f)
            v.x += dir * v.speed * dt
            v.wheel += v.speed * dt / (9f * d)
        }
        vehicles.removeAll { it.x < -220f * d || it.x > w + 220f * d }

        // Two little ones cross the street at the zebra crossing while the cars wait at the red light
        val cycle = (lightT / 17.5f).toInt()
        if (light == 2 && lightT % 17.5f > 13.1f && cycle != lastWalkerCycle) {
            lastWalkerCycle = cycle
            for (i in 0 until 1 + Random.nextInt(2)) {
                val wk = Walker(Customer(Random.nextInt(5), -1))
                wk.x = crossX() - 6f * d + i * 22f * d
                wk.y = ground + 108f * d
                walkers.add(wk)
            }
        }
        val wi = walkers.iterator()
        while (wi.hasNext()) {
            val wk = wi.next()
            wk.cu.phase += dt
            if (wk.state == 0) {
                wk.y += 26f * d * dt
                if (wk.y >= ground + 176f * d) {
                    wk.state = 1
                    wk.dirX = if (Random.nextBoolean()) 1f else -1f
                }
            } else {
                wk.timer += dt
                wk.x += wk.dirX * 30f * d * dt
                if (wk.timer > 4f) wi.remove()
            }
        }

        // Airplanes with white trails
        planeTimer -= dt
        if (planeTimer <= 0f && planes.size < 2) {
            val dir = if (Random.nextBoolean()) 1f else -1f
            val p = Plane(dir, h * (0.07f + Random.nextFloat() * 0.2f), (70f + Random.nextFloat() * 50f) * d)
            p.x = if (dir > 0f) -100f * d else vw + 100f * d
            planes.add(p)
            planeTimer = 16f + Random.nextFloat() * 16f
        }
        val pi = planes.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.x += p.dir * p.speed * dt
            p.trailAcc += dt
            val onScreen = p.x > -100f * d && p.x < vw + 100f * d
            if (p.trailAcc >= 0.06f && onScreen) {
                p.trailAcc = 0f
                p.trail.add(floatArrayOf(p.x - p.dir * 30f * d, p.y + 2f * d, 0f))
            }
            for (tp in p.trail) tp[2] += dt
            p.trail.removeAll { it[2] > 3f }
            if (!onScreen && p.trail.isEmpty() && (p.x < -140f * d || p.x > vw + 140f * d)) pi.remove()
        }

        // Flocks of birds
        flockTimer -= dt
        if (flockTimer <= 0f && flocks.size < 3) {
            val dir = if (Random.nextBoolean()) 1f else -1f
            val fl = Flock(dir, h * (0.12f + Random.nextFloat() * 0.3f), (45f + Random.nextFloat() * 40f) * d, 3 + Random.nextInt(5))
            fl.x = if (dir > 0f) -120f * d else vw + 120f * d
            flocks.add(fl)
            flockTimer = 6f + Random.nextFloat() * 9f
        }
        val fi = flocks.iterator()
        while (fi.hasNext()) {
            val fl = fi.next()
            fl.x += fl.dir * fl.speed * dt
            if (fl.x < -200f * d || fl.x > vw + 200f * d) fi.remove()
        }
    }

    private fun drawBird(c: Canvas, x: Float, y: Float, flap: Float, s: Float) {
        line.color = Color.parseColor("#3B3B5C")
        line.strokeWidth = 2f * s
        c.drawLine(x - 9f * s, y - flap * 6f * s, x, y, line)
        c.drawLine(x, y, x + 9f * s, y - flap * 6f * s, line)
        f(Color.parseColor("#3B3B5C"))
        c.drawCircle(x, y, 2.4f * s, fill)
    }

    private fun drawPlane(c: Canvas, p: Plane) {
        c.save()
        c.translate(p.x, p.y)
        if (p.dir < 0f) c.scale(-1f, 1f)
        val s = d
        // tail fin, far wing, fuselage, windows, red stripe, near wing and engine
        path.reset(); path.moveTo(-34f * s, -2f * s); path.lineTo(-42f * s, -17f * s); path.lineTo(-26f * s, -2f * s); path.close()
        f(Color.parseColor("#E5484D")); c.drawPath(path, fill)
        path.reset(); path.moveTo(-4f * s, -2f * s); path.lineTo(-20f * s, -15f * s); path.lineTo(-10f * s, -15f * s); path.lineTo(10f * s, -2f * s); path.close()
        f(Color.parseColor("#B8C4DA")); c.drawPath(path, fill)
        f(Color.WHITE)
        c.drawOval(RectF(-38f * s, -7f * s, 38f * s, 7f * s), fill)
        f(Color.parseColor("#7FB7E6"))
        for (k in 0 until 7) c.drawCircle(-22f * s + k * 8f * s, -1.5f * s, 1.7f * s, fill)
        line.color = Color.parseColor("#E5484D")
        line.strokeWidth = 1.6f * s
        c.drawLine(-36f * s, 3f * s, 36f * s, 3f * s, line)
        path.reset(); path.moveTo(-2f * s, 2f * s); path.lineTo(-22f * s, 17f * s); path.lineTo(-12f * s, 17f * s); path.lineTo(14f * s, 2f * s); path.close()
        f(Color.parseColor("#DDE6F5")); c.drawPath(path, fill)
        f(Color.parseColor("#9AA8C4"))
        c.drawOval(RectF(-16f * s, 8f * s, -6f * s, 14f * s), fill)
        c.restore()
    }

    /** Planes with their white trails and flocks of flapping birds, in front of the far mountains. */
    private fun drawSkyLife(c: Canvas) {
        c.save()
        c.translate(camX * 0.9f, 0f)
        for (p in planes) {
            for (tp in p.trail) {
                val a = (1f - tp[2] / 3f).coerceIn(0f, 1f)
                f(Color.WHITE, (150f * a).toInt())
                c.drawCircle(tp[0], tp[1], (2.5f + tp[2] * 2f) * d, fill)
            }
            drawPlane(c, p)
        }
        for (fl in flocks) {
            for (i in 0 until fl.count) {
                val k = (i + 1) / 2 * (if (i % 2 == 0) 1 else -1)
                val bx = fl.x - fl.dir * abs(k) * 16f * d
                val by = fl.y + k * 9f * d + sin(clock * 2f + i) * 2f * d
                drawBird(c, bx, by, sin(clock * 12f + i * 0.7f), d)
            }
        }
        c.restore()
    }

    private fun wheel(c: Canvas, x: Float, y: Float, r: Float, rot: Float) {
        f(Color.parseColor("#2B2540"))
        c.drawCircle(x, y, r, fill)
        f(Color.parseColor("#C9D1D9"))
        c.drawCircle(x, y, r * 0.5f, fill)
        line.color = Color.parseColor("#2B2540")
        line.strokeWidth = r * 0.22f
        c.drawLine(x + kotlin.math.cos(rot) * r * 0.45f, y + sin(rot) * r * 0.45f, x - kotlin.math.cos(rot) * r * 0.45f, y - sin(rot) * r * 0.45f, line)
    }

    /** A hatchback, a taxi, a bus or a truck (drawn facing right, turned around for the other lane). */
    private fun drawVehicle(c: Canvas, v: Vehicle, laneY: Float) {
        val s = (if (v.lane == 0) 0.88f else 1f) * d
        c.save()
        c.translate(v.x, laneY)
        if (v.lane == 0) c.scale(-1f, 1f)
        val half = v.length / d / 2f
        f(Color.BLACK, 70)
        c.drawOval(RectF(-half * s, 10f * s, half * s, 17f * s), fill)
        val glass = Color.parseColor("#CFEFFF")
        when (v.type) {
            2 -> { // bus
                rr(c, -64f * s, -18f * s, 64f * s, 12f * s, 8f * s, v.color)
                rr(c, -64f * s, -18f * s, 64f * s, -13f * s, 4f * s, Color.WHITE)
                for (k in 0 until 6) rr(c, -54f * s + k * 17f * s, -10f * s, -42f * s + k * 17f * s, -1f * s, 2f * s, glass)
                rr(c, 46f * s, -12f * s, 60f * s, 6f * s, 3f * s, glass)
                wheel(c, -40f * s, 12f * s, 8f * s, v.wheel)
                wheel(c, 40f * s, 12f * s, 8f * s, v.wheel)
            }
            3 -> { // truck
                rr(c, -50f * s, -20f * s, 12f * s, 12f * s, 4f * s, Color.WHITE)
                rr(c, -50f * s, -20f * s, 12f * s, -14f * s, 3f * s, v.color)
                rr(c, 14f * s, -9f * s, 50f * s, 12f * s, 5f * s, v.color)
                rr(c, 30f * s, -6f * s, 46f * s, 1f * s, 2f * s, glass)
                wheel(c, -30f * s, 12f * s, 8f * s, v.wheel)
                wheel(c, -8f * s, 12f * s, 8f * s, v.wheel)
                wheel(c, 36f * s, 12f * s, 8f * s, v.wheel)
            }
            else -> { // hatchback or taxi
                rr(c, -37f * s, -4f * s, 37f * s, 12f * s, 6f * s, v.color)
                path.reset()
                path.moveTo(-22f * s, -4f * s); path.lineTo(-14f * s, -18f * s); path.lineTo(14f * s, -18f * s); path.lineTo(24f * s, -4f * s); path.close()
                f(v.color); c.drawPath(path, fill)
                path.reset()
                path.moveTo(-18f * s, -5f * s); path.lineTo(-12f * s, -16f * s); path.lineTo(12f * s, -16f * s); path.lineTo(20f * s, -5f * s); path.close()
                f(glass); c.drawPath(path, fill)
                line.color = v.color; line.strokeWidth = 1.5f * s
                c.drawLine(0f, -16f * s, 0f, -5f * s, line)
                if (v.type == 1) {
                    rr(c, -6f * s, -23f * s, 6f * s, -18f * s, 2f * s, Color.WHITE)
                    f(Color.parseColor("#2B2540"))
                    for (k in 0 until 6) c.drawRect(-30f * s + k * 11f * s, 4f * s, -26f * s + k * 11f * s, 8f * s, fill)
                }
                wheel(c, -22f * s, 12f * s, 8f * s, v.wheel)
                wheel(c, 22f * s, 12f * s, 8f * s, v.wheel)
            }
        }
        // Headlight in front, tail light behind
        f(Color.parseColor("#FFE9A8"))
        c.drawRoundRect(RectF((half - 4f) * s, -2f * s, (half + 1f) * s, 4f * s), 2f * s, 2f * s, fill)
        f(Color.parseColor("#E5484D"))
        c.drawRoundRect(RectF((-half - 1f) * s, -2f * s, (-half + 3f) * s, 4f * s), 2f * s, 2f * s, fill)
        c.restore()
    }

    private fun drawLamp(c: Canvas, x: Float, baseY: Float, index: Int) {
        rr(c, x - 2f * d, baseY - 54f * d, x + 2f * d, baseY, 1.5f * d, Color.parseColor("#4A4A68"))
        rr(c, x - 9f * d, baseY - 57f * d, x + 9f * d, baseY - 53f * d, 2f * d, Color.parseColor("#4A4A68"))
        fill.style = Paint.Style.FILL
        fill.shader = android.graphics.RadialGradient(
            x, baseY - 60f * d, 28f * d,
            intArrayOf(Color.parseColor("#77FFE9A8"), Color.parseColor("#00FFE9A8")), null, Shader.TileMode.CLAMP
        )
        c.drawCircle(x, baseY - 60f * d, 28f * d, fill)
        fill.shader = null
        f(Color.parseColor("#FFE9A8"))
        c.drawCircle(x, baseY - 60f * d, 6f * d, fill)
        if (index % 3 == 0) {
            // a small bird rests on the lamp and now and then lifts its head
            val bx = x + 1f * d
            val by = baseY - 71f * d + (if (sin(clock * 2.5f + index) > 0.9f) -2f * d else 0f)
            val color = if (index % 2 == 0) Color.parseColor("#FF8FB8") else Color.parseColor("#4FC3F7")
            f(color)
            c.drawOval(RectF(bx - 6f * d, by - 4f * d, bx + 6f * d, by + 5f * d), fill)
            c.drawCircle(bx + 6f * d, by - 4f * d, 3.6f * d, fill)
            path.reset(); path.moveTo(bx + 9f * d, by - 5f * d); path.lineTo(bx + 13f * d, by - 3.5f * d); path.lineTo(bx + 9f * d, by - 2.5f * d); path.close()
            f(Color.parseColor("#FF9F1C")); c.drawPath(path, fill)
            f(Color.parseColor("#2B2540")); c.drawCircle(bx + 7f * d, by - 5f * d, 1f * d, fill)
            path.reset(); path.moveTo(bx - 6f * d, by); path.lineTo(bx - 13f * d, by + 3f * d); path.lineTo(bx - 6f * d, by + 3f * d); path.close()
            f(color); c.drawPath(path, fill)
        }
    }

    private fun drawTrafficLight(c: Canvas, x: Float, baseY: Float) {
        val state = lightState()
        rr(c, x - 1.5f * d, baseY - 44f * d, x + 1.5f * d, baseY, 1f * d, Color.parseColor("#4A4A68"))
        rr(c, x - 6f * d, baseY - 64f * d, x + 6f * d, baseY - 38f * d, 3f * d, Color.parseColor("#2B2B3C"))
        val colors = intArrayOf(Color.parseColor("#FF4D4D"), Color.parseColor("#FFD93B"), Color.parseColor("#3DDC84"))
        val lit = intArrayOf(2, 1, 0) // red, yellow, green
        for (i in 0 until 3) {
            f(colors[i], if (state == lit[i]) 255 else 55)
            c.drawCircle(x, baseY - 57f * d + i * 8f * d, 3.2f * d, fill)
        }
    }

    /** Street lamps with resting birds, the traffic lights, the vehicles and the people crossing the street. */
    private fun drawLife(c: Canvas, w: Float, h: Float, ground: Float) {
        var k = 1
        while ((START_DP + SLOT_DP * k) * d < w) {
            drawLamp(c, (START_DP + SLOT_DP * k) * d, ground + 114f * d, k)
            k++
        }
        drawTrafficLight(c, crossX() - 40f * d, ground + 114f * d)
        drawTrafficLight(c, crossX() + 40f * d, ground + 114f * d)
        for (lane in 0..1) {
            for (v in vehicles) if (v.lane == lane) drawVehicle(c, v, ground + (if (lane == 0) 134f else 158f) * d)
        }
        for (wk in walkers) {
            c.save()
            c.translate(wk.x, wk.y)
            c.scale(0.55f, 0.55f)
            wk.cu.x = 0f
            drawTourist(c, wk.cu, 0f)
            c.restore()
        }
    }


    // ---------------------------------------------------------------- the farm

    /** A small picture of a crop: carrot, tomato, strawberry or sunflower. */
    private fun drawCropIcon(c: Canvas, crop: Int, cx: Float, cy: Float, s: Float) {
        when (crop % 4) {
            0 -> {
                path.reset(); path.moveTo(cx - 6f * s, cy - 6f * s); path.lineTo(cx + 6f * s, cy - 6f * s); path.lineTo(cx, cy + 12f * s); path.close()
                f(Color.parseColor("#FF8A2B")); c.drawPath(path, fill)
                f(Color.parseColor("#4CAF50"))
                c.drawCircle(cx - 3f * s, cy - 9f * s, 3.2f * s, fill)
                c.drawCircle(cx + 3f * s, cy - 10f * s, 3.2f * s, fill)
            }
            1 -> {
                f(Color.parseColor("#E63946")); c.drawCircle(cx, cy, 8f * s, fill)
                f(Color.parseColor("#4CAF50")); c.drawCircle(cx, cy - 8f * s, 3f * s, fill)
            }
            2 -> {
                path.reset(); path.moveTo(cx - 8f * s, cy - 5f * s); path.lineTo(cx + 8f * s, cy - 5f * s); path.lineTo(cx, cy + 10f * s); path.close()
                f(Color.parseColor("#E63946")); c.drawPath(path, fill)
                f(Color.parseColor("#4CAF50")); c.drawCircle(cx, cy - 6f * s, 4f * s, fill)
                f(Color.parseColor("#FFF3B0"))
                c.drawCircle(cx - 3f * s, cy, 1f * s, fill)
                c.drawCircle(cx + 3f * s, cy + 1f * s, 1f * s, fill)
                c.drawCircle(cx, cy + 5f * s, 1f * s, fill)
            }
            else -> {
                f(Color.parseColor("#FFD93B"))
                for (k in 0 until 8) {
                    val a = k * 0.785f
                    c.drawCircle(cx + kotlin.math.cos(a) * 7f * s, cy + sin(a) * 7f * s, 3.6f * s, fill)
                }
                f(Color.parseColor("#8B5E3C")); c.drawCircle(cx, cy, 5f * s, fill)
            }
        }
    }

    private fun drawPlant(c: Canvas, i: Int, st: PlotState, cx: Float, baseY: Float) {
        val p = st.progress.coerceIn(0f, 1f)
        val thirsty = st.thirsty
        f(Color.parseColor("#7A5230"))
        c.drawOval(RectF(cx - 12f * d, baseY - 4f * d, cx + 12f * d, baseY + 5f * d), fill)
        if (p < 0.05f && !st.ripe) {
            f(Color.parseColor("#C9A27A"))
            c.drawCircle(cx, baseY - 3f * d, 2.6f * d, fill)
            return
        }
        val sway = if (thirsty) 0f else sin(clock * 1.5f + i) * 2f * d
        val droop = if (thirsty) 7f * d else 0f
        val height = (8f + 42f * p) * d
        val green = if (thirsty) Color.parseColor("#A8B58A") else Color.parseColor("#4CAF50")
        val topX = cx + sway + droop
        val topY = baseY - height + droop * 0.6f
        line.color = green
        line.strokeWidth = 3f * d
        c.drawLine(cx, baseY, topX, topY, line)
        val leaves = 1 + (p * 3f).toInt()
        for (k in 0 until leaves) {
            val t = 0.35f + 0.2f * k
            val lx = cx + (topX - cx) * t
            val ly = baseY - height * t + droop * 0.3f
            f(green)
            c.drawOval(RectF(lx - 11f * d, ly - 3f * d, lx - 1f * d, ly + 4f * d), fill)
            c.drawOval(RectF(lx + 1f * d, ly - 7f * d, lx + 11f * d, ly), fill)
        }
        if (st.ripe) {
            if (i % 4 == 0) drawCropIcon(c, 0, cx, baseY - 6f * d, 1.5f * d) else drawCropIcon(c, i, topX, topY - 3f * d, 1.6f * d)
            line.color = Color.parseColor("#2EAD4B")
            line.strokeWidth = 3f * d
            line.alpha = (150f + 100f * sin(clock * 6f)).toInt().coerceIn(0, 255)
            c.drawCircle(cx, baseY - 24f * d, 27f * d, line)
            line.alpha = 255
        } else if (thirsty) {
            // a blue drop: "I am thirsty"
            f(Color.WHITE, 235)
            val by = topY - 30f * d
            c.drawRoundRect(RectF(cx - 14f * d, by - 13f * d, cx + 14f * d, by + 13f * d), 10f * d, 10f * d, fill)
            f(Color.parseColor("#3DA5F5"), (170f + 85f * sin(clock * 5f)).toInt().coerceIn(0, 255))
            path.reset(); path.moveTo(cx, by - 9f * d); path.lineTo(cx - 6f * d, by + 1f * d); path.lineTo(cx + 6f * d, by + 1f * d); path.close()
            c.drawPath(path, fill)
            c.drawCircle(cx, by + 3f * d, 6f * d, fill)
        }
    }

    /** The village farm: fence, sign, four plots with growing crops, and a scarecrow. */
    private fun drawFarm(c: Canvas, ground: Float) {
        val x0 = (START_DP + SLOT_DP * farmSlot) * d
        // Fence at the back
        for (k in 0..7) {
            val px = x0 + 6f * d + k * 40f * d
            rr(c, px - 2f * d, ground + 6f * d, px + 2f * d, ground + 32f * d, 1.5f * d, Color.parseColor("#B98A5E"))
        }
        rr(c, x0 + 4f * d, ground + 12f * d, x0 + 292f * d, ground + 17f * d, 2f * d, Color.parseColor("#C99A6B"))
        rr(c, x0 + 4f * d, ground + 22f * d, x0 + 292f * d, ground + 27f * d, 2f * d, Color.parseColor("#C99A6B"))
        // The sign
        rr(c, x0 + 2f * d, ground - 30f * d, x0 + 6f * d, ground + 8f * d, 1.5f * d, Color.parseColor("#8B5E3C"))
        rr(c, x0 - 10f * d, ground - 46f * d, x0 + 56f * d, ground - 24f * d, 7f * d, Color.parseColor("#6BA84F"))
        val old = text.color
        text.color = Color.WHITE
        text.textSize = 12.5f * d
        c.drawText("المزرعة", x0 + 23f * d, ground - 30f * d, text)
        text.color = old
        text.textSize = 14f * d
        // The plots
        for (i in 0 until Farm.PLOTS) {
            val cx = plotCenterX(farmSlot, i, d)
            rr(c, cx - 27f * d, ground + 40f * d, cx + 27f * d, ground + 88f * d, 9f * d, Color.parseColor("#8B5E3C"))
            line.color = Color.parseColor("#6E4728")
            line.strokeWidth = 2f * d
            for (k in 0 until 3) c.drawLine(cx - 22f * d, ground + 52f * d + k * 13f * d, cx + 22f * d, ground + 52f * d + k * 13f * d, line)
            val st = plots[i]
            if (st == null || !st.planted) {
                // "plant here": a bubble with the picture of the crop
                f(Color.WHITE, 235)
                val pulse = 1f + 0.06f * sin(clock * 4f + i)
                c.drawRoundRect(RectF(cx - 17f * d * pulse, ground + 8f * d, cx + 17f * d * pulse, ground + 42f * d), 12f * d, 12f * d, fill)
                drawCropIcon(c, i, cx, ground + 26f * d, 1.3f * d)
            } else {
                drawPlant(c, i, st, cx, ground + 70f * d)
            }
        }
        // The scarecrow
        val sx = x0 + 292f * d
        line.color = Color.parseColor("#8B5E3C")
        line.strokeWidth = 4f * d
        c.drawLine(sx, ground + 4f * d, sx, ground + 74f * d, line)
        c.drawLine(sx - 20f * d, ground + 30f * d, sx + 20f * d, ground + 30f * d + sin(clock * 1.2f) * 2f * d, line)
        rr(c, sx - 11f * d, ground + 28f * d, sx + 11f * d, ground + 54f * d, 4f * d, Color.parseColor("#4F8FE0"))
        f(Color.parseColor("#F2D2A9")); c.drawCircle(sx, ground + 20f * d, 9f * d, fill)
        f(Color.parseColor("#E9C46A")); c.drawOval(RectF(sx - 15f * d, ground + 11f * d, sx + 15f * d, ground + 18f * d), fill)
        c.drawRoundRect(RectF(sx - 8f * d, ground + 2f * d, sx + 8f * d, ground + 14f * d), 4f * d, 4f * d, fill)
        // The farmer at work: seeds, the watering can or the basket
        if (farmerAction != 0 && farmerTarget >= 0) {
            val cx = plotCenterX(farmSlot, farmerTarget, d)
            when (farmerAction) {
                1 -> for (k in 0 until 6) {
                    f(Color.parseColor("#7A5230"))
                    c.drawCircle(cx + (k - 3) * 5f * d, ground + 20f * d + ((farmerT * 60f * d + k * 13f * d) % (40f * d)), 2.2f * d, fill)
                }
                2 -> {
                    c.save()
                    c.translate(cx - 26f * d, ground + 4f * d)
                    c.rotate(-22f)
                    rr(c, -14f * d, -8f * d, 10f * d, 8f * d, 3f * d, Color.parseColor("#6C8EAD"))
                    line.color = Color.parseColor("#6C8EAD")
                    line.strokeWidth = 3f * d
                    c.drawLine(10f * d, -2f * d, 20f * d, 4f * d, line)
                    c.restore()
                    for (k in 0 until 9) {
                        f(Color.parseColor("#66BFFF"))
                        c.drawCircle(cx - 8f * d + (k * 7 % 20) * d, ground + 14f * d + ((farmerT * 70f * d + k * 11f * d) % (48f * d)), 2.4f * d, fill)
                    }
                }
                else -> {
                    rr(c, cx - 13f * d, ground + 18f * d, cx + 13f * d, ground + 36f * d, 4f * d, Color.parseColor("#C97B4A"))
                    line.color = Color.parseColor("#A8622F")
                    line.strokeWidth = 1.6f * d
                    c.drawLine(cx - 13f * d, ground + 26f * d, cx + 13f * d, ground + 26f * d, line)
                    f(Color.parseColor("#FFD93B"), (140f + 100f * sin(farmerT * 10f)).toInt().coerceIn(0, 255))
                    c.drawCircle(cx, ground + 14f * d, 6f * d, fill)
                }
            }
        }
    }

    /** Draws a layer that moves slower than the village ([factor] < 1: far away) or faster (> 1: near). */
    private fun layer(c: Canvas, factor: Float, body: (Float) -> Unit) {
        c.save()
        c.translate(camX * (1f - factor), 0f)
        body(camX * factor)
        c.restore()
    }

    /** Sky, a low sun with rays, clouds, mountains and hills at different depths, haze, ground and road. */
    private fun drawBackdrop(c: Canvas, w: Float, h: Float, ground: Float) {
        val vw = minOf(w, resources.displayMetrics.widthPixels.toFloat())
        // The sky and the sun stay with the camera
        c.save()
        c.translate(camX, 0f)
        fill.style = Paint.Style.FILL
        fill.alpha = 255
        fill.shader = LinearGradient(
            0f, 0f, 0f, ground,
            intArrayOf(Color.parseColor("#5FA8F0"), Color.parseColor("#A9D4FF"), Color.parseColor("#FFE9C7"), Color.parseColor("#FFD9A8")),
            floatArrayOf(0f, 0.45f, 0.8f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, vw, ground + 6f * d, fill)
        val sx = vw * 0.78f
        val sy = h * 0.2f
        fill.shader = android.graphics.RadialGradient(
            sx, sy, 210f * d,
            intArrayOf(Color.parseColor("#CCFFF3C4"), Color.parseColor("#55FFE9A8"), Color.parseColor("#00FFE9A8")),
            floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(sx, sy, 210f * d, fill)
        fill.shader = null
        for (i in 0 until 7) {
            val a = 0.35f + i * 0.3f + sin(clock * 0.3f) * 0.04f
            val len = 520f * d
            path.reset()
            path.moveTo(sx, sy)
            path.lineTo(sx + kotlin.math.cos(a - 0.05f) * len, sy + sin(a - 0.05f) * len)
            path.lineTo(sx + kotlin.math.cos(a + 0.05f) * len, sy + sin(a + 0.05f) * len)
            path.close()
            f(Color.WHITE, 22)
            c.drawPath(path, fill)
        }
        f(Color.parseColor("#FFF8DC"))
        c.drawCircle(sx, sy, 30f * d, fill)
        c.restore()

        layer(c, 0.2f) { start ->
            for (i in 0 until 6) {
                val x = start + ((clock * (5f + i * 1.5f) * d + i * vw * 0.37f) % (vw + 260f * d)) - 130f * d
                val y = h * (0.09f + 0.06f * (i % 3))
                val cs = (0.8f + 0.15f * (i % 3)) * d
                f(Color.WHITE, 215)
                c.drawCircle(x - 22f * cs, y + 4f * cs, 14f * cs, fill)
                c.drawCircle(x, y - 4f * cs, 20f * cs, fill)
                c.drawCircle(x + 24f * cs, y + 4f * cs, 15f * cs, fill)
                f(Color.parseColor("#FFE9D2"), 160)
                c.drawOval(RectF(x - 34f * cs, y + 6f * cs, x + 38f * cs, y + 18f * cs), fill)
            }
        }
        drawSkyLife(c)
        layer(c, 0.15f) { start ->
            val step = 190f * d
            var i = (start / step).toInt() - 2
            while (i * step < start + vw + step * 2) {
                val bx = i * step
                val ph = (70f + (((i * 37) % 5 + 5) % 5) * 24f) * d
                val base = ground - 8f * d
                path.reset()
                path.moveTo(bx - step * 0.8f, base)
                path.lineTo(bx, base - ph)
                path.lineTo(bx + step * 0.8f, base)
                path.close()
                f(Color.parseColor("#9DB8E8"))
                c.drawPath(path, fill)
                path.reset()
                path.moveTo(bx - step * 0.8f, base)
                path.lineTo(bx, base - ph)
                path.lineTo(bx, base)
                path.close()
                f(Color.parseColor("#B2C9F2"))
                c.drawPath(path, fill)
                path.reset()
                path.moveTo(bx, base - ph)
                path.lineTo(bx - step * 0.14f, base - ph * 0.78f)
                path.lineTo(bx + step * 0.14f, base - ph * 0.78f)
                path.close()
                f(Color.WHITE, 230)
                c.drawPath(path, fill)
                i++
            }
        }
        layer(c, 0.35f) { start ->
            val step = 170f * d
            var i = (start / step).toInt() - 2
            while (i * step < start + vw + step * 2) {
                val bx = i * step
                f(Color.parseColor("#A5D8A0"))
                c.drawOval(RectF(bx - 150f * d, ground - 80f * d, bx + 150f * d, ground + 60f * d), fill)
                for (k in 0 until 4) {
                    val tx = bx + (k - 1.5f) * 38f * d + (((i * 13 + k * 7) % 9 + 9) % 9) * 3f * d
                    val ty = ground - 30f * d - (((i + k) % 3 + 3) % 3) * 8f * d
                    path.reset()
                    path.moveTo(tx - 11f * d, ty)
                    path.lineTo(tx, ty - 34f * d)
                    path.lineTo(tx + 11f * d, ty)
                    path.close()
                    f(Color.parseColor("#6DB57B"))
                    c.drawPath(path, fill)
                }
                i++
            }
        }
        layer(c, 0.6f) { start ->
            val step = 220f * d
            var i = (start / step).toInt() - 2
            while (i * step < start + vw + step * 2) {
                val bx = i * step
                f(Color.parseColor("#8DD08A"))
                c.drawOval(RectF(bx - 200f * d, ground - 52f * d, bx + 200f * d, ground + 70f * d), fill)
                i++
            }
        }
        // Warm haze near the horizon (things far away look paler)
        c.save()
        c.translate(camX, 0f)
        fill.shader = LinearGradient(
            0f, ground - 100f * d, 0f, ground + 14f * d,
            Color.parseColor("#00FFE3B8"), Color.parseColor("#AAFFE3B8"), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, ground - 100f * d, vw, ground + 14f * d, fill)
        fill.shader = null
        c.restore()

        // The ground gets darker toward the viewer, with a road
        fill.shader = LinearGradient(0f, ground, 0f, h, Color.parseColor("#93DB8F"), Color.parseColor("#4A9A58"), Shader.TileMode.CLAMP)
        c.drawRect(0f, ground, w, h, fill)
        fill.shader = LinearGradient(
            0f, ground + 16f * d, 0f, ground + 48f * d,
            Color.parseColor("#F5E6C0"), Color.parseColor("#D9BE8A"), Shader.TileMode.CLAMP
        )
        rect.set(0f, ground + 16f * d, w, ground + 48f * d)
        c.drawRoundRect(rect, 14f * d, 14f * d, fill)
        fill.shader = null
        var sr = 8f * d
        while (sr < w) {
            f(Color.WHITE, 150)
            rect.set(sr, ground + 31f * d, sr + 22f * d, ground + 33.5f * d)
            c.drawRoundRect(rect, 1.5f * d, 1.5f * d, fill)
            sr += 52f * d
        }
        var fx0 = 30f * d
        var k = 0
        while (fx0 < w) {
            f(if (k % 2 == 0) Color.parseColor("#FF8FB8") else Color.parseColor("#FFE27A"))
            c.drawCircle(fx0, ground + 70f * d + (k % 3) * 22f * d, 3.2f * d, fill)
            fx0 += 53f * d
            k++
        }

        // The pavement, the street with its two lanes, and a zebra crossing
        f(Color.parseColor("#D8D3E3"))
        c.drawRect(0f, ground + 104f * d, w, ground + 120f * d, fill)
        f(Color.parseColor("#C6C0D6"))
        var tileX = 0f
        while (tileX < w) {
            c.drawRect(tileX, ground + 104f * d, tileX + 1.5f * d, ground + 118f * d, fill)
            tileX += 26f * d
        }
        f(Color.parseColor("#B2ACC4"))
        c.drawRect(0f, ground + 118f * d, w, ground + 122f * d, fill)
        fill.shader = LinearGradient(0f, ground + 122f * d, 0f, ground + 172f * d, Color.parseColor("#6E7388"), Color.parseColor("#4E5268"), Shader.TileMode.CLAMP)
        c.drawRect(0f, ground + 122f * d, w, ground + 172f * d, fill)
        fill.shader = null
        f(Color.parseColor("#FFD93B"))
        var dashX = 10f * d
        while (dashX < w) {
            c.drawRect(dashX, ground + 146f * d, dashX + 24f * d, ground + 148.5f * d, fill)
            dashX += 52f * d
        }
        f(Color.parseColor("#B2ACC4"))
        c.drawRect(0f, ground + 172f * d, w, ground + 178f * d, fill)
        f(Color.WHITE, 235)
        for (z in 0 until 7) {
            val zx = crossX() - 30f * d + z * 9.5f * d
            c.drawRect(zx, ground + 124f * d, zx + 5.5f * d, ground + 170f * d, fill)
        }
    }

    /** Bushes in front of everything (they move faster than the village), floating light and butterflies. */
    private fun drawForeground(c: Canvas, w: Float, h: Float) {
        val vw = minOf(w, resources.displayMetrics.widthPixels.toFloat())
        layer(c, 1.35f) { start ->
            val step = 118f * d
            var i = (start / step).toInt() - 2
            while (i * step < start + vw + step * 2) {
                val bx = i * step + ((((i * 29) % 7) + 7) % 7) * 6f * d
                val by = h + 8f * d
                f(Color.parseColor("#3F8F4D"))
                c.drawCircle(bx, by - 8f * d, 30f * d, fill)
                c.drawCircle(bx - 28f * d, by, 24f * d, fill)
                c.drawCircle(bx + 30f * d, by - 2f * d, 26f * d, fill)
                f(Color.parseColor("#58A863"))
                c.drawCircle(bx - 6f * d, by - 22f * d, 12f * d, fill)
                f(if (i % 2 == 0) Color.parseColor("#FF8FB8") else Color.parseColor("#FFE27A"))
                c.drawCircle(bx + 10f * d, by - 26f * d, 3.4f * d, fill)
                c.drawCircle(bx - 20f * d, by - 14f * d, 3f * d, fill)
                i++
            }
        }
        layer(c, 0.8f) { start ->
            for (i in 0 until 16) {
                val x = start + ((i * 97f * d + clock * (8f + i % 5) * d) % vw)
                val y = h * (0.25f + 0.5f * ((i * 37) % 100) / 100f) + sin(clock + i) * 10f * d
                f(Color.parseColor("#FFF3B0"), (120f + 100f * sin(clock * 2f + i)).toInt().coerceIn(0, 255))
                c.drawCircle(x, y, (2f + (i % 3)) * d, fill)
            }
            for (i in 0 until 2) {
                val x = start + ((clock * 30f * d + i * 400f * d) % (vw + 80f * d)) - 40f * d
                val y = h * 0.5f + sin(clock * 2f + i) * 40f * d
                val flap = abs(sin(clock * 14f + i))
                f(if (i == 0) Color.parseColor("#FF8FB8") else Color.parseColor("#FFD93B"))
                c.drawOval(RectF(x - 9f * d * flap, y - 6f * d, x, y + 6f * d), fill)
                c.drawOval(RectF(x, y - 6f * d, x + 9f * d * flap, y + 6f * d), fill)
            }
        }
    }

    /** An impatient customer: red steam over the head, an angry mark and (on tourists) angry eyebrows. */
    private fun drawAnger(c: Canvas, cu: Customer, footY: Float) {
        for (i in 0 until 3) {
            val age = (clock * 1.1f + i * 0.33f) % 1f
            f(Color.parseColor("#FF6B6B"), ((1f - age) * 190f).toInt())
            c.drawCircle(cu.x + (i - 1) * 12f * d + sin(clock * 5f + i) * 3f * d, footY - 64f * d - age * 26f * d, (4f + age * 5f) * d, fill)
        }
        line.color = Color.parseColor("#E5484D")
        line.strokeWidth = 3f * d
        val ax = cu.x + 22f * d
        val ay = footY - 78f * d
        c.drawLine(ax - 6f * d, ay - 6f * d, ax - 2f * d, ay - 2f * d, line)
        c.drawLine(ax + 6f * d, ay - 6f * d, ax + 2f * d, ay - 2f * d, line)
        c.drawLine(ax - 6f * d, ay + 6f * d, ax - 2f * d, ay + 2f * d, line)
        c.drawLine(ax + 6f * d, ay + 6f * d, ax + 2f * d, ay + 2f * d, line)
        if (cu.tourist >= 0) {
            line.strokeWidth = 2.4f * d
            c.drawLine(cu.x - 9f * d, footY - 61f * d, cu.x - 2f * d, footY - 57f * d, line)
            c.drawLine(cu.x + 9f * d, footY - 61f * d, cu.x + 2f * d, footY - 57f * d, line)
        }
    }

    private fun drawRestaurant(c: Canvas, rx: Float, ground: Float) {
        val bw = 70f * d
        f(Color.BLACK, 60)
        c.drawOval(RectF(rx - bw - 6f * d, ground - 9f * d, rx + bw + 22f * d, ground + 14f * d), fill)
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
        val angry = cu.state == Customer.READY && cu.patience >= 14f
        if (cu.state == Customer.READY && !angry) {
            // "give me my food": a green ring pulses around the order
            line.color = Color.parseColor("#2EAD4B")
            line.strokeWidth = 3f * d
            line.alpha = (150f + 100f * sin(clock * 6f)).toInt().coerceIn(0, 255)
            rect.set(cu.x - 28f * d, y - 26f * d, cu.x + 28f * d, y + 26f * d)
            c.drawRoundRect(rect, 16f * d, 16f * d, line)
            line.alpha = 255
        }
        f(if (angry) Color.parseColor("#FFD0D0") else Color.WHITE, 245)
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

/** A film-like grade over the whole village: warm light from the sun side, cool shadow and a vignette. */
class CinemaOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.shader = LinearGradient(w, 0f, 0f, h, Color.argb(51, 255, 180, 90), Color.argb(40, 91, 63, 214), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = android.graphics.RadialGradient(
            w / 2f, h / 2f, maxOf(w, h) * 0.75f,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(150, 20, 16, 48)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}
