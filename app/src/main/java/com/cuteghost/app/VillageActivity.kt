package com.cuteghost.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * The village: a wide landscape (scroll sideways) with the ghosts' restaurant at the start and the
 * house of every ghost that grew up, the oldest first. A chef ghost cooks, village ghosts and tourist
 * creatures come to buy food, and the money pays for upgrading the village houses (touch a house) and
 * the restaurant itself (touch it): dining tables, then a waiter, then a fruit and food stand.
 */
class VillageActivity : Activity() {

    private companion object {
        val FOOD_PRICE = intArrayOf(2, 2, 3, 4, 5, 5, 6, 8)

        // The waiter
        const val W_IDLE = 0
        const val W_TO_COUNTER = 1
        const val W_PICKUP = 2
        const val W_CARRY = 3
        const val W_SERVE = 4
        const val W_BACK = 5

        // The farmer
        const val F_IDLE = 0
        const val F_WALK = 1
        const val F_WORK = 2
        const val F_BACK = 3
    }

    private lateinit var root: FrameLayout
    private lateinit var view: VillageView
    private lateinit var coinView: CoinCounterView
    private var villagers: List<Villager> = emptyList()
    private val ghosts = ArrayList<GhostView>()
    private var chef: GhostView? = null
    private var waiter: GhostView? = null
    private var rLevel = 1

    private var running = false
    private var lastTick = 0L
    private var spawnIn = 2.5f
    private var chefAt = 0f
    private var t = 0f

    private var wState = W_IDLE
    private var wTarget: Customer? = null
    private var wX = 0f
    private var wTimer = 0f
    private var wPhase = 0f

    private var farmer: GhostView? = null
    private var scrollView: HorizontalScrollView? = null
    private var farmSlot = 3
    private val farmQueue = ArrayList<Int>()
    private var fState = F_IDLE
    private var fTarget = -1
    private var fAction = 0
    private var fX = 0f
    private var fTimer = 0f
    private var fPhase = 0f
    private var plotRefresh = 0f

    private val green = Color.parseColor("#2EAD4B")
    private val red = Color.parseColor("#E5484D")

    private val d: Float get() = resources.displayMetrics.density

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastTick) / 1000f).coerceIn(0f, 0.05f)
            lastTick = now
            update(dt)
            root.postOnAnimation(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        setContentView(root)
        // The restaurant kept selling a little while nobody was looking (more with every level)
        val last = Village.lastVisit(this)
        if (last > 0L) {
            val level = Village.restaurantLevel(this)
            val minutes = (System.currentTimeMillis() - last) / 60_000L
            val income = minOf(40L + 20L * (level - 1), minutes / 10L * level).toInt()
            if (income > 0) {
                Village.addCoins(this, income)
                Toast.makeText(this, "باع المطعم وأنت غائب: +$income", Toast.LENGTH_LONG).show()
            }
        }
        Village.markVisit(this)
        build()
    }

    override fun onResume() {
        super.onResume()
        running = true
        lastTick = SystemClock.uptimeMillis()
        root.postOnAnimation(ticker)
    }

    override fun onPause() {
        super.onPause()
        running = false
        root.removeCallbacks(ticker)
        Village.markVisit(this)
    }

    private fun dp(v: Int): Int = (v * d).toInt()

    private fun build() {
        root.removeAllViews()
        ghosts.clear()
        chef = null
        waiter = null
        wState = W_IDLE
        wTarget = null
        villagers = Village.list(this)
        rLevel = Village.restaurantLevel(this)
        val dm = resources.displayMetrics
        val match = FrameLayout.LayoutParams.MATCH_PARENT
        farmSlot = maxOf(3, villagers.size + 2)
        val worldW = maxOf(dm.widthPixels, dp(VillageView.START_DP * 2 + VillageView.SLOT_DP * (farmSlot + 2)))

        view = VillageView(this, villagers)
        view.restaurantLevel = rLevel
        view.farmSlot = farmSlot
        view.onTap = { x, y -> handleTap(x, y) }
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        scrollView = scroll
        val world = FrameLayout(this)
        world.addView(view, FrameLayout.LayoutParams(worldW, match))
        scroll.addView(world, FrameLayout.LayoutParams(worldW, match))
        root.addView(scroll, FrameLayout.LayoutParams(match, match))
        // The camera: far layers of the picture move slower than near ones while scrolling
        scroll.viewTreeObserver.addOnScrollChangedListener { view.camX = scroll.scrollX.toFloat() }
        root.addView(CinemaOverlayView(this), FrameLayout.LayoutParams(match, match))

        // Every grown-up ghost stands in front of its house; the cook stands behind the counter
        val gw = dp(62)
        val gh = (gw * 1.28f).toInt()
        for ((idx, v) in villagers.withIndex()) {
            val ghost = GhostView(this)
            ghost.setBodyColor(if (v.colorIndex >= 0) Family.color(v.colorIndex) else null)
            ghost.playful = true
            ghost.setOnClickListener {
                val cu = view.customers.firstOrNull { it.villager == idx }
                if (cu != null && cu.state == Customer.READY) serve(cu) else ghost.showFed()
            }
            world.addView(ghost, FrameLayout.LayoutParams(gw, gh, Gravity.TOP or Gravity.LEFT))
            ghosts.add(ghost)
        }
        val cook = GhostView(this)
        cook.setOutfit(7, 0, 0, 0, 0)
        cook.playful = true
        cook.setOnClickListener { cook.funnyMove(2) }
        world.addView(cook, FrameLayout.LayoutParams(gw, gh, Gravity.TOP or Gravity.LEFT))
        chef = cook
        if (rLevel >= 3) {
            // The waiter wears a red bow (the red scarf) and carries the dishes to the tables
            val w = GhostView(this)
            w.setBodyColor(Family.color(1))
            w.setOutfit(0, 1, 0, 0, 0)
            w.playful = true
            w.setOnClickListener { w.funnyMove(1) }
            world.addView(w, FrameLayout.LayoutParams(gw, gh, Gravity.TOP or Gravity.LEFT))
            waiter = w
            wX = waiterHome()
        }

        // The farmer, with a straw hat, works on the village farm
        val farmerGhost = GhostView(this)
        farmerGhost.setBodyColor(Family.color(2))
        farmerGhost.setOutfit(8, 0, 0, 0, 0)
        farmerGhost.playful = true
        farmerGhost.setOnClickListener { farmerGhost.funnyMove(1) }
        world.addView(farmerGhost, FrameLayout.LayoutParams(gw, gh, Gravity.TOP or Gravity.LEFT))
        farmer = farmerGhost
        farmQueue.clear()
        fState = F_IDLE
        fAction = 0
        fX = VillageView.farmerHomeX(farmSlot, d)

        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(12), dp(28), dp(12), dp(8))
        val back = IconButtonView(this, IconButtonView.BACK, Color.parseColor("#7C5CFF"))
        back.setOnClickListener { finish() }
        bar.addView(back, LinearLayout.LayoutParams(dp(56), dp(56)))
        // Two picture buttons that scroll the village to the restaurant or to the farm
        val toRest = IconButtonView(this, IconButtonView.VIL_REST, Color.parseColor("#E5469A"))
        toRest.setOnClickListener { scrollView?.smoothScrollTo(0, 0) }
        bar.addView(toRest, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(6) })
        val toFarm = IconButtonView(this, IconButtonView.VIL_FARM, Color.parseColor("#3DAE5C"))
        toFarm.setOnClickListener {
            scrollView?.smoothScrollTo(maxOf(0, ((VillageView.START_DP + VillageView.SLOT_DP * farmSlot - 40) * d).toInt()), 0)
        }
        bar.addView(toFarm, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(6) })
        val title = TextView(this)
        title.text = "القرية"
        title.textSize = 13f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER
        title.background = GradientDrawable().apply { setColor(Color.parseColor("#997C5CFF")); cornerRadius = dp(22).toFloat() }
        title.setPadding(dp(14), dp(8), dp(14), dp(8))
        val tlp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tlp.setMargins(dp(10), 0, dp(10), 0)
        bar.addView(title, tlp)
        coinView = CoinCounterView(this)
        coinView.value = Village.coins(this)
        bar.addView(coinView, LinearLayout.LayoutParams(dp(112), dp(44)))
        root.addView(bar, FrameLayout.LayoutParams(match, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
    }

    // ---------------------------------------------------------------- the restaurant

    private fun spawnX(): Float = resources.displayMetrics.widthPixels + 50f * d

    private fun baseX(index: Int): Float =
        VillageView.houseCenterX(index, d) + 44f * VillageView.houseScale(d) * 0.55f

    private fun waiterHome(): Float = VillageView.restaurantX(d) + 52f * d
    private fun counterSpot(): Float = VillageView.restaurantX(d) + 40f * d

    private fun freeSeat(): Int = (0 until 4).firstOrNull { s -> view.customers.none { it.seat == s } } ?: -1

    /**
     * Puts a ghost where the perspective camera shows it: [x] and [feetY] are world coordinates, [anchorY] is
     * the depth it stands at. Far ghosts are smaller and everything converges toward the vanishing point.
     */
    private fun place(g: GhostView, x: Float, feetY: Float, anchorY: Float, gw: Float, gh: Float, bob: Float) {
        val s = view.scaleAt(anchorY)
        g.pivotX = gw / 2f
        g.pivotY = gh
        g.scaleX = s
        g.scaleY = s
        g.translationX = view.projX(x, anchorY) - gw / 2f
        g.translationY = anchorY + (feetY - anchorY) * s - gh - bob * s
    }

    private fun spawnCustomer() {
        // Only the people at the counter use the queue places; the others sit at the tables
        val used = view.customers
            .filter {
                it.state == Customer.WALK_IN || it.state == Customer.ORDER || it.state == Customer.WAIT ||
                    (it.state == Customer.READY && (rLevel < 3 || it.seat < 0))
            }
            .map { it.slot }.toSet()
        val slot = (0..2).firstOrNull { it !in used } ?: return
        val cu: Customer
        if (villagers.isEmpty() || Random.nextFloat() < 0.55f) {
            cu = Customer(Random.nextInt(5), -1)
            cu.x = spawnX()
            cu.homeX = cu.x
        } else {
            val free = villagers.indices.filter { idx -> view.customers.none { it.villager == idx } }
            if (free.isEmpty()) return
            val idx = free[Random.nextInt(free.size)]
            cu = Customer(-1, idx)
            cu.x = baseX(idx)
            cu.homeX = cu.x
        }
        // The fruit and food stand adds new, dearer dishes to the menu
        cu.food = Random.nextInt(if (rLevel >= 4) 8 else 4)
        cu.slot = slot
        view.customers.add(cu)
    }

    private fun moveToward(x: Float, target: Float, speed: Float, dt: Float): Float {
        val step = speed * d * dt
        return x + (target - x).coerceIn(-step, step)
    }

    private fun pay(cu: Customer, ground: Float) {
        var price = FOOD_PRICE[cu.food % 8] * (if (cu.tourist >= 0) 2 else 1) // tourists pay double
        if (cu.servedByWaiter) price = (price * 3 + 1) / 2 // served at the table: a bigger tip
        else if (cu.seat >= 0 && rLevel == 2) price += 1 // sat at a table
        if (cu.patience >= 14f) price = maxOf(1, price / 2) // late and angry: half price
        Village.addCoins(this, price)
        Sounds.play(this, Snd.CHING)
        coinView.value = Village.coins(this)
        view.fx.add(CoinFx(cu.x, ground + 56f * d - 60f * d, price))
    }

    private fun afterOrder(cu: Customer) {
        cu.seat = if (rLevel >= 2) freeSeat() else -1
        chef?.funnyMove(1)
        if (cu.seat >= 0 && rLevel >= 3) {
            // With a waiter the customer goes straight to a table and the dish is brought to him
            cu.state = Customer.WALK_TABLE
        } else {
            cu.state = Customer.WAIT
            cu.timer = 1.8f
        }
    }

    private fun update(dt: Float) {
        val h = view.height.toFloat()
        if (h == 0f) return
        val ground = h * VillageView.GROUND
        t += dt

        spawnIn -= dt
        val maxCustomers = 3 + (if (rLevel >= 2) 2 else 0)
        if (spawnIn <= 0f && view.customers.size < maxCustomers) {
            spawnCustomer()
            spawnIn = (6f + Random.nextFloat() * 5f) / (1f + 0.15f * (rLevel - 1))
        }
        val iter = view.customers.iterator()
        while (iter.hasNext()) {
            val cu = iter.next()
            cu.phase += dt
            val speed = if (cu.tourist >= 0) 62f else 74f
            when (cu.state) {
                Customer.WALK_IN -> {
                    val tx = VillageView.queueX(cu.slot, d)
                    cu.x = moveToward(cu.x, tx, speed, dt)
                    if (abs(cu.x - tx) < 2f) {
                        cu.state = Customer.ORDER
                        cu.timer = 1.4f
                    }
                }
                Customer.ORDER -> {
                    cu.timer -= dt
                    if (cu.timer <= 0f) afterOrder(cu)
                }
                Customer.WAIT -> {
                    // The cook is cooking; then the dish is ready and waits for the child's touch
                    cu.timer -= dt
                    if (cu.timer <= 0f) {
                        cu.state = Customer.READY
                        cu.patience = 0f
                    }
                }
                Customer.READY -> {
                    cu.patience += dt
                    if (cu.patience >= 14f && !cu.angryShown) {
                        cu.angryShown = true
                        Sounds.play(this, Snd.GRUMBLE)
                        villagerGhost(cu)?.funnyMove(0)
                    }
                    if (cu.patience >= 26f) {
                        // Too late: the customer leaves angry without paying
                        cu.state = Customer.LEAVE
                        cu.seat = -1
                    }
                }
                Customer.WALK_TABLE -> {
                    val tx = VillageView.seatX(cu.seat, d)
                    cu.x = moveToward(cu.x, tx, speed, dt)
                    if (abs(cu.x - tx) < 2f) {
                        if (rLevel >= 3) {
                            // The cook cooked meanwhile: the dish waits for the child's touch
                            cu.state = Customer.READY
                            cu.patience = 0f
                        } else {
                            cu.state = Customer.EAT
                            cu.timer = 3f
                        }
                    }
                }
                Customer.WAIT_SERVE -> {
                    // The waiter brings the dish; if he is too busy for too long the customer gets it anyway
                    if (!cu.assigned) cu.timer -= dt
                    if (cu.timer <= 0f) {
                        cu.state = Customer.EAT
                        cu.timer = 3f
                    }
                }
                Customer.EAT -> {
                    cu.timer -= dt
                    if (cu.timer <= 0f) {
                        pay(cu, ground)
                        cu.state = Customer.PAY
                        cu.timer = 0.9f
                    }
                }
                Customer.PAY -> {
                    cu.timer -= dt
                    if (cu.timer <= 0f) {
                        cu.state = Customer.LEAVE
                        cu.seat = -1
                    }
                }
                else -> {
                    val tx = if (cu.tourist >= 0) spawnX() else cu.homeX
                    cu.x = moveToward(cu.x, tx, speed, dt)
                    if (abs(cu.x - tx) < 2f) iter.remove()
                }
            }
        }
        val fi = view.fx.iterator()
        while (fi.hasNext()) {
            val coin = fi.next()
            coin.age += dt
            if (coin.age > 1.2f) fi.remove()
        }
        updateWaiter(dt)
        updateFarm(dt, ground)
        // The cook stirs the pot from time to time while somebody waits
        if (t >= chefAt && view.customers.any { it.state == Customer.WAIT || it.state == Customer.ORDER || it.state == Customer.WAIT_SERVE }) {
            chefAt = t + 3.5f
            chef?.funnyMove(0)
        }

        // Positions of the ghosts: villagers go to the counter, the tables and back when they are customers
        val gw = dp(62).toFloat()
        val gh = gw * 1.28f
        for ((i, g) in ghosts.withIndex()) {
            val cu = view.customers.firstOrNull { it.villager == i }
            val x = cu?.x ?: baseX(i)
            val walking = cu != null && (cu.state == Customer.WALK_IN || cu.state == Customer.LEAVE || cu.state == Customer.WALK_TABLE)
            val bob = if (walking) abs(sin(cu!!.phase * 9f)) * 3f * d else 0f
            place(g, x, ground + 50f * d, ground + 50f * d, gw, gh, bob)
        }
        chef?.let {
            place(it, VillageView.restaurantX(d) - 16f * d, ground - 32f * d, ground, gw, gh, 0f)
        }
        waiter?.let {
            val moving = wState == W_TO_COUNTER || wState == W_CARRY || wState == W_BACK
            val bob = if (moving) abs(sin(wPhase * 9f)) * 3f * d else 0f
            place(it, wX, ground + 56f * d, ground, gw, gh, bob)
        }
        view.waiterX = wX
    }

    /** The waiter takes the dish from the counter to the table of a customer who is waiting for it. */
    private fun updateWaiter(dt: Float) {
        if (waiter == null) return
        wPhase += dt
        when (wState) {
            W_IDLE -> {
                val next = view.customers.firstOrNull { it.state == Customer.WAIT_SERVE && !it.assigned }
                if (next != null) {
                    next.assigned = true
                    wTarget = next
                    wState = W_TO_COUNTER
                } else {
                    wX = moveToward(wX, waiterHome(), 60f, dt)
                }
            }
            W_TO_COUNTER -> {
                wX = moveToward(wX, counterSpot(), 70f, dt)
                if (abs(wX - counterSpot()) < 2f) {
                    wState = W_PICKUP
                    wTimer = 1.2f
                    chef?.funnyMove(1)
                }
            }
            W_PICKUP -> {
                wTimer -= dt
                if (wTimer <= 0f) {
                    view.waiterCarry = wTarget?.food ?: 0
                    wState = W_CARRY
                }
            }
            W_CARRY -> {
                val c = wTarget
                if (c == null) {
                    view.waiterCarry = -1
                    wState = W_BACK
                } else {
                    val tx = VillageView.tableX(c.seat.coerceAtLeast(0) / 2, d)
                    wX = moveToward(wX, tx, 70f, dt)
                    if (abs(wX - tx) < 2f) {
                        wState = W_SERVE
                        wTimer = 0.6f
                    }
                }
            }
            W_SERVE -> {
                wTimer -= dt
                if (wTimer <= 0f) {
                    view.waiterCarry = -1
                    wTarget?.let {
                        it.servedByWaiter = true
                        it.state = Customer.EAT
                        it.timer = 3f
                    }
                    wTarget = null
                    waiter?.funnyMove(1)
                    wState = W_BACK
                }
            }
            else -> {
                wX = moveToward(wX, waiterHome(), 70f, dt)
                if (abs(wX - waiterHome()) < 2f) wState = W_IDLE
            }
        }
    }

    // ---------------------------------------------------------------- the farm

    /** What the farmer would do at a plot now: 1 plant, 2 water, 3 harvest, 0 nothing (it is growing happily). */
    private fun farmActionFor(i: Int): Int {
        val st = Farm.state(this, i)
        return when {
            !st.planted -> 1
            st.ripe -> 3
            st.hydrated <= 0.5f -> 2
            else -> 0
        }
    }

    private fun farmTap(i: Int) {
        if (farmActionFor(i) == 0) {
            farmer?.funnyMove(1)
            return
        }
        if (farmQueue.size < 3 && !farmQueue.contains(i) && !(fState != F_IDLE && fTarget == i)) farmQueue.add(i)
    }

    private fun addFarmCoins(amount: Int, x: Float, ground: Float) {
        Village.addCoins(this, amount)
        coinView.value = Village.coins(this)
        view.fx.add(CoinFx(x, ground + 24f * d, amount))
    }

    private fun finishFarmWork(ground: Float) {
        val i = fTarget
        if (i < 0) return
        val cx = VillageView.plotCenterX(farmSlot, i, d)
        when (fAction) {
            1 -> {
                Farm.plant(this, i)
                Sounds.play(this, Snd.POP, true)
            }
            2 -> if (Farm.water(this, i)) {
                addFarmCoins(1, cx, ground)
                Sounds.play(this, Snd.SPLASH, true)
            }
            else -> {
                val crop = Farm.harvest(this, i)
                if (crop != null) {
                    addFarmCoins(crop.coins, cx, ground)
                    Wardrobe.addStars(this, crop.stars)
                    Sounds.play(this, Snd.SPARKLE, true)
                    farmer?.showFed()
                    Toast.makeText(this, "حصاد ${crop.name}! +${crop.coins} عملة و+${crop.stars} نجمة", Toast.LENGTH_SHORT).show()
                }
            }
        }
        view.plots[i] = Farm.state(this, i)
    }

    private fun updateFarm(dt: Float, ground: Float) {
        plotRefresh -= dt
        if (plotRefresh <= 0f) {
            plotRefresh = 1f
            for (i in 0 until Farm.PLOTS) view.plots[i] = Farm.state(this, i)
        }
        val fm = farmer ?: return
        fPhase += dt
        val home = VillageView.farmerHomeX(farmSlot, d)
        when (fState) {
            F_IDLE -> {
                if (farmQueue.isNotEmpty()) {
                    val i = farmQueue.removeAt(0)
                    val a = farmActionFor(i)
                    if (a != 0) {
                        fTarget = i
                        fAction = a
                        fState = F_WALK
                    }
                } else {
                    fX = moveToward(fX, home, 70f, dt)
                }
            }
            F_WALK -> {
                val tx = VillageView.plotCenterX(farmSlot, fTarget, d)
                fX = moveToward(fX, tx, 85f, dt)
                if (abs(fX - tx) < 2f) {
                    fState = F_WORK
                    fTimer = if (fAction == 2) 1.7f else 1.3f
                    view.farmerAction = fAction
                    view.farmerTarget = fTarget
                    view.farmerT = 0f
                    fm.funnyMove(1)
                }
            }
            F_WORK -> {
                fTimer -= dt
                view.farmerT += dt
                if (fTimer <= 0f) {
                    finishFarmWork(ground)
                    view.farmerAction = 0
                    fState = F_BACK
                }
            }
            else -> {
                fX = moveToward(fX, home, 85f, dt)
                if (abs(fX - home) < 2f) fState = F_IDLE
            }
        }
        view.farmerX = fX
        val gw = dp(62).toFloat()
        val gh = gw * 1.28f
        val moving = fState == F_WALK || fState == F_BACK
        val bob = if (moving) abs(sin(fPhase * 9f)) * 3f * d else 0f
        place(fm, fX, ground + 100f * d, ground + 50f * d, gw, gh, bob)
    }

    // ---------------------------------------------------------------- serving, touching a house or the restaurant

    /** The child touched a customer whose dish is ready: the food is given (or the waiter takes it) and he pays. */
    private fun serve(cu: Customer) {
        if (cu.state != Customer.READY) return
        val ground = view.height * VillageView.GROUND
        villagerGhost(cu)?.showFed()
        chef?.funnyMove(1)
        when {
            rLevel >= 3 && cu.seat >= 0 -> {
                cu.state = Customer.WAIT_SERVE
                cu.assigned = false
                cu.timer = 25f
            }
            rLevel == 2 && cu.seat >= 0 -> cu.state = Customer.WALK_TABLE
            else -> {
                pay(cu, ground)
                cu.state = Customer.PAY
                cu.timer = 0.9f
            }
        }
    }

    private fun villagerGhost(cu: Customer): GhostView? = if (cu.villager >= 0) ghosts.getOrNull(cu.villager) else null

    private fun handleTap(rawX: Float, rawY: Float) {
        val ground = view.height * VillageView.GROUND
        // The picture is in perspective: a touch is turned back into the world's own coordinates
        val depth = view.scaleAt(maxOf(rawY, ground))
        val x = view.vpx() + (rawX - view.vpx()) / depth
        val y = ground + (rawY - ground) / depth
        // The patrol car and the officers answer a touch (siren, whistle, a wave)
        if (view.tapPolice(x, y)) return
        // A customer waiting for his dish: touching him gives it to him
        for (cu in view.customers) {
            if (cu.state == Customer.READY && abs(x - cu.x) < 40f * d && y in (ground + 56f * d - 118f * d)..(ground + 56f * d + 8f * d)) {
                serve(cu)
                return
            }
        }
        // A plot: the farmer comes to plant, water or harvest
        for (i in 0 until Farm.PLOTS) {
            val pcx = VillageView.plotCenterX(farmSlot, i, d)
            if (abs(x - pcx) < 34f * d && y in (ground + 4f * d)..(ground + 92f * d)) {
                farmTap(i)
                return
            }
        }
        if (abs(x - VillageView.restaurantX(d)) < 70f * d && y in (ground - 140f * d)..(ground + 10f * d)) {
            if (rLevel < Village.REST_MAX) showRestaurantUpgrade() else chef?.funnyMove(2)
            return
        }
        for (i in villagers.indices) {
            val cx = VillageView.houseCenterX(i, d)
            if (abs(x - cx) < 64f * d && y in (ground - 120f * d)..(ground + 10f * d)) {
                showUpgrade(i)
                return
            }
        }
    }

    private fun artColumn(houseId: Int, level: Int): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.addView(HouseArtView(this, houseId, level), LinearLayout.LayoutParams(dp(125), dp(135)))
        val tv = TextView(this)
        tv.text = "$level"
        tv.textSize = 18f
        tv.gravity = Gravity.CENTER
        tv.setTextColor(Color.parseColor("#2B2540"))
        col.addView(tv)
        return col
    }

    private fun icon(kind: Int, color: Int, sizeDp: Int, onClick: () -> Unit): IconButtonView {
        val v = IconButtonView(this, kind, color)
        v.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        v.layoutParams = lp
        return v
    }

    /** The restaurant's next step: a big picture of it, what it gives, the coins needed and a green tick. */
    private fun showRestaurantUpgrade() {
        val next = rLevel + 1
        val kind = when (next) {
            2 -> IconButtonView.REST_TABLE
            3 -> IconButtonView.REST_WAITER
            else -> IconButtonView.REST_FRUIT
        }
        val color = when (next) {
            2 -> Color.parseColor("#E5469A")
            3 -> Color.parseColor("#7C5CFF")
            else -> Color.parseColor("#2EAD4B")
        }
        val text = when (next) {
            2 -> "طاولات للزبائن: يجلسون ويأكلون ويدفعون بقشيشًا"
            3 -> "عامل طاولة يحمل الصينية ويقدّم الطعام: أرباح أعلى"
            else -> "بسطة فواكه وأطعمة وأصناف جديدة أغلى (بطيخ، برتقال، ساندويتش، بيتزا)"
        }
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.setPadding(dp(16), dp(12), dp(16), dp(10))
        val big = IconButtonView(this, kind, color)
        content.addView(big, LinearLayout.LayoutParams(dp(110), dp(110)))
        val label = TextView(this)
        label.text = text
        label.textSize = 14f
        label.gravity = Gravity.CENTER
        label.setTextColor(Color.parseColor("#2B2540"))
        content.addView(label)
        val cost = Village.restaurantCost(rLevel)
        val have = Village.coins(this)
        content.addView(MeterView(this, 2, have, cost), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        val dialog = AlertDialog.Builder(this).setView(content).create()
        val buttons = LinearLayout(this)
        buttons.gravity = Gravity.CENTER
        val check = icon(IconButtonView.CHECK, green, 74) {
            if (Village.spendCoins(this, cost)) {
                Village.setRestaurantLevel(this, next)
                dialog.dismiss()
                Toast.makeText(this, "ارتقى المطعم!", Toast.LENGTH_SHORT).show()
                build()
            } else {
                Toast.makeText(this, "ينقصك ${cost - Village.coins(this)} عملة", Toast.LENGTH_LONG).show()
            }
        }
        check.dimmed = have < cost
        buttons.addView(check)
        buttons.addView(icon(IconButtonView.CLOSE, red, 74) { dialog.dismiss() })
        content.addView(buttons)
        dialog.show()
    }

    /** The restaurant's coins pay for the upgrade of a village house: before and after pictures and a green tick. */
    private fun showUpgrade(index: Int) {
        val v = villagers[index]
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.setPadding(dp(16), dp(10), dp(16), dp(10))
        val previews = LinearLayout(this)
        previews.gravity = Gravity.CENTER
        previews.addView(artColumn(v.houseId, v.level))
        if (v.level < Village.MAX_LEVEL) {
            val arrow = TextView(this)
            arrow.text = "  ←  "
            arrow.textSize = 26f
            arrow.setTextColor(Color.parseColor("#7C5CFF"))
            previews.addView(arrow)
            previews.addView(artColumn(v.houseId, v.level + 1))
        }
        content.addView(previews)
        val dialog = AlertDialog.Builder(this).setView(content).create()
        val buttons = LinearLayout(this)
        buttons.gravity = Gravity.CENTER
        if (v.level >= Village.MAX_LEVEL) {
            val done = TextView(this)
            done.text = "هذا البيت في أعلى مستوى!"
            done.textSize = 17f
            done.gravity = Gravity.CENTER
            content.addView(done)
            buttons.addView(icon(IconButtonView.CLOSE, red, 74) { dialog.dismiss() })
            content.addView(buttons)
            dialog.show()
            return
        }
        val cost = Village.upgradeCost(v.level)
        val have = Village.coins(this)
        content.addView(MeterView(this, 2, have, cost), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        val check = icon(IconButtonView.CHECK, green, 74) {
            if (Village.spendCoins(this, cost)) {
                Village.setLevel(this, index, v.level + 1)
                dialog.dismiss()
                Toast.makeText(this, "ارتقى البيت إلى المستوى ${v.level + 1}!", Toast.LENGTH_SHORT).show()
                build()
            } else {
                Toast.makeText(this, "ينقصك ${cost - Village.coins(this)} عملة من المطعم", Toast.LENGTH_LONG).show()
            }
        }
        check.dimmed = have < cost
        buttons.addView(check)
        buttons.addView(icon(IconButtonView.CLOSE, red, 74) { dialog.dismiss() })
        content.addView(buttons)
        dialog.show()
    }
}
