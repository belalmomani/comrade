package com.cuteghost.app

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView

/**
 * The village: a wide landscape (scroll sideways) with the house of every ghost that grew up and
 * moved out, the oldest first. Each ghost stands in front of its own house.
 */
class VillageActivity : Activity() {

    private lateinit var root: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        setContentView(root)
        build()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun build() {
        val dm = resources.displayMetrics
        val d = dm.density
        val villagers = Village.list(this)
        val match = FrameLayout.LayoutParams.MATCH_PARENT
        val worldW = maxOf(dm.widthPixels, dp(VillageView.START_DP * 2 + VillageView.SLOT_DP * maxOf(villagers.size, 1)))

        val scroll = HorizontalScrollView(this)
        scroll.isHorizontalScrollBarEnabled = false
        val world = FrameLayout(this)
        world.addView(VillageView(this, villagers), FrameLayout.LayoutParams(worldW, match))
        scroll.addView(world, FrameLayout.LayoutParams(worldW, match))
        root.addView(scroll, FrameLayout.LayoutParams(match, match))

        // Every grown-up ghost stands in front of its house
        val gw = dp(62)
        val gh = (gw * 1.28f).toInt()
        val placed = ArrayList<Pair<GhostView, Int>>()
        for ((i, v) in villagers.withIndex()) {
            val ghost = GhostView(this)
            if (!v.isMother || v.colorIndex >= 0) ghost.setBodyColor(Family.color(v.colorIndex.coerceAtLeast(0)))
            if (v.colorIndex < 0) ghost.setBodyColor(null)
            ghost.playful = true
            ghost.setOnClickListener { ghost.showFed() }
            world.addView(ghost, FrameLayout.LayoutParams(gw, gh, Gravity.TOP or Gravity.LEFT))
            placed.add(Pair(ghost, i))
        }
        root.post {
            val ground = root.height * VillageView.GROUND
            for ((ghost, i) in placed) {
                val cx = VillageView.houseCenterX(i, d) + 44f * VillageView.houseScale(d) * 0.55f
                ghost.translationX = cx - gw / 2f
                ghost.translationY = ground + 50f * d - gh
            }
        }

        val bar = android.widget.LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(12), dp(30), dp(12), dp(8))
        val back = Button(this)
        back.text = "رجوع"
        back.setTextColor(Color.WHITE)
        back.background = GradientDrawable().apply { setColor(Color.parseColor("#CC7C5CFF")); cornerRadius = dp(22).toFloat() }
        back.setOnClickListener { finish() }
        val backIcon = IconButtonView(this, IconButtonView.BACK, Color.parseColor("#7C5CFF"))
        backIcon.setOnClickListener { finish() }
        bar.addView(backIcon, android.widget.LinearLayout.LayoutParams(dp(56), dp(56)))
        val title = TextView(this)
        title.text = "القرية  •  ${villagers.size} منزل"
        title.textSize = 16f
        title.setTextColor(Color.WHITE)
        title.gravity = Gravity.CENTER
        title.background = GradientDrawable().apply { setColor(Color.parseColor("#997C5CFF")); cornerRadius = dp(22).toFloat() }
        title.setPadding(dp(14), dp(8), dp(14), dp(8))
        val tlp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tlp.setMargins(dp(10), 0, 0, 0)
        bar.addView(title, tlp)
        root.addView(bar, FrameLayout.LayoutParams(match, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
    }
}
