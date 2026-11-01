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
 * creatures come to buy food, and the money pays for upgrading the village houses (touch a house).
 */
class VillageActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var view: VillageView
    private lateinit var coinView: CoinCounterView
    private var villagers: List<Villager> = emptyList()
    private val ghosts = ArrayList<GhostView>()
    private var chef: GhostView? = null

    private var running = false
    private var lastTick = 0L
    private var spawnIn = 2.5f
    private var chefAt = 0f
    private var t = 0f

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
        // The restaurant kept selling a little while nobody was looking
        val last = Village.lastVisit(this)
        if (last > 0L) {
            val income = minOf(40L, (System.currentTimeMillis() - last) / 600_000L).toInt()
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
        villagers = Village.list(this)
        val dm = resources.displayMetrics
        val match = FrameLayout.LayoutParams.MATCH_PARENT
        val worldW = maxOf(dm.widthPixels, dp(VillageView.START_DP * 2 + VillageView.SLOT_DP * (villagers.size + 1)))

        view = VillageView(this, villagers)
        view.onTap = { x, y -> handleTap(x, y) }
        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        val world = FrameLayout(this)
        world.addView(view, FrameLayout.LayoutParams(worldW, match))
        scroll.addView(world, FrameLayout.LayoutParams(worldW, match))
        root.addView(scroll, FrameLayout.LayoutParams(match, match))

        // Every grown-up ghost stands in front of its house; the cook stands behind the counter
        val gw = dp(62)
        val gh = (gw * 1.28f).toInt()
        for (v in villagers) {
            val ghost = GhostView(this)
            ghost.setBodyColor(if (v.colorIndex >= 0) Family.color(v.colorIndex) else null)
            ghost.playful = true
            ghost.setOnClickListener { ghost.showFed() }
            world.addView(ghost, FrameLayout.LayoutParams(gw, gh, Gravity.TOP or Gravity.LEFT))
            ghosts.add(ghost)
        }
        val cook = GhostView(this)
        cook.setOutfit(7, 0, 0, 0, 0)
        cook.playful = true
        cook.setOnClickListener { cook.funnyMove(2) }
        world.addView(cook, FrameLayout.LayoutParams(gw, gh, Gravity.TOP or Gravity.LEFT))
        chef = cook

        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(12), dp(28), dp(12), dp(8))
        val back = IconButtonView(this, IconButtonView.BACK, Color.parseColor("#7C5CFF"))
        back.setOnClickListener { finish() }
        bar.addView(back, LinearLayout.LayoutParams(dp(56), dp(56)))
        val title = TextView(this)
        title.text = "القرية  •  ${villagers.size}"
        title.textSize = 16f
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

    private fun spawnCustomer() {
        val used = view.customers.filter { it.state != Customer.LEAVE }.map { it.slot }.toSet()
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
        cu.food = Random.nextInt(4)
        cu.slot = slot
        view.customers.add(cu)
    }

    private fun moveToward(cu: Customer, target: Float, dt: Float) {
        val step = (if (cu.tourist >= 0) 62f else 74f) * d * dt
        cu.x += (target - cu.x).coerceIn(-step, step)
    }

    private fun pay(cu: Customer, ground: Float) {
        // Tourists pay double
        val price = intArrayOf(2, 2, 3, 4)[cu.food % 4] * (if (cu.tourist >= 0) 2 else 1)
        Village.addCoins(this, price)
        coinView.value = Village.coins(this)
        view.fx.add(CoinFx(cu.x, ground + 56f * d - 60f * d, price))
    }

    private fun update(dt: Float) {
        val h = view.height.toFloat()
        if (h == 0f) return
        val ground = h * VillageView.GROUND
        t += dt

        spawnIn -= dt
        if (spawnIn <= 0f && view.customers.size < 3) {
            spawnCustomer()
            spawnIn = 6f + Random.nextFloat() * 5f
        }
        val iter = view.customers.iterator()
        while (iter.hasNext()) {
            val cu = iter.next()
            cu.phase += dt
            when (cu.state) {
                Customer.WALK_IN -> {
                    val tx = VillageView.queueX(cu.slot, d)
                    moveToward(cu, tx, dt)
                    if (abs(cu.x - tx) < 2f) {
                        cu.state = Customer.ORDER
                        cu.timer = 1.4f
                    }
                }
                Customer.ORDER -> {
                    cu.timer -= dt
                    if (cu.timer <= 0f) {
                        cu.state = Customer.WAIT
                        cu.timer = 1.8f
                        chef?.funnyMove(1)
                    }
                }
                Customer.WAIT -> {
                    cu.timer -= dt
                    if (cu.timer <= 0f) {
                        pay(cu, ground)
                        cu.state = Customer.PAY
                        cu.timer = 0.9f
                    }
                }
                Customer.PAY -> {
                    cu.timer -= dt
                    if (cu.timer <= 0f) cu.state = Customer.LEAVE
                }
                else -> {
                    val tx = if (cu.tourist >= 0) spawnX() else cu.homeX
                    moveToward(cu, tx, dt)
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
        // The cook stirs the pot from time to time while somebody waits
        if (t >= chefAt && view.customers.any { it.state == Customer.WAIT || it.state == Customer.ORDER }) {
            chefAt = t + 3.5f
            chef?.funnyMove(0)
        }

        // Positions of the ghosts: villagers go to the counter and back when they are customers
        val gw = dp(62).toFloat()
        val gh = gw * 1.28f
        for ((i, g) in ghosts.withIndex()) {
            val cu = view.customers.firstOrNull { it.villager == i }
            val x = cu?.x ?: baseX(i)
            val walking = cu != null && (cu.state == Customer.WALK_IN || cu.state == Customer.LEAVE)
            val bob = if (walking) abs(sin(cu!!.phase * 9f)) * 3f * d else 0f
            g.translationX = x - gw / 2f
            g.translationY = ground + 50f * d - gh - bob
        }
        chef?.let {
            it.translationX = VillageView.restaurantX(d) - 16f * d - gw / 2f
            it.translationY = ground - 40f * d - gh + 8f * d
        }
    }

    // ---------------------------------------------------------------- touching a house

    private fun handleTap(x: Float, y: Float) {
        val ground = view.height * VillageView.GROUND
        if (abs(x - VillageView.restaurantX(d)) < 70f * d && y in (ground - 140f * d)..(ground + 10f * d)) {
            chef?.funnyMove(2)
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

    private fun icon(kind: Int, color: Int, onClick: () -> Unit): IconButtonView {
        val v = IconButtonView(this, kind, color)
        v.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(dp(74), dp(74))
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        v.layoutParams = lp
        return v
    }

    /** The restaurant's coins pay for the upgrade: before and after pictures, a coin meter, a big green tick. */
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
            buttons.addView(icon(IconButtonView.CLOSE, red) { dialog.dismiss() })
            content.addView(buttons)
            dialog.show()
            return
        }
        val cost = Village.upgradeCost(v.level)
        val have = Village.coins(this)
        content.addView(MeterView(this, 2, have, cost), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        val check = icon(IconButtonView.CHECK, green) {
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
        buttons.addView(icon(IconButtonView.CLOSE, red) { dialog.dismiss() })
        content.addView(buttons)
        dialog.show()
    }
}
