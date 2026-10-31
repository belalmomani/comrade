package com.cuteghost.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * "Ghost house", made for a three-year-old: big round picture buttons instead of words. The houses,
 * the inside of the chosen house with its sleeping ghosts, the "decorate" shop, the upgrade, the
 * village and the playground are each one touch away from inside the house.
 */
class HouseActivity : Activity() {

    companion object {
        const val EXTRA_INTERIOR = "open_interior"
    }

    private lateinit var root: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private var inInterior = false

    // The room being shown
    private var houseId = 0
    private var state = SceneState(emptyMap())
    private val ghostViews = ArrayList<GhostView>()
    private var bottomBar: View? = null

    // The decoration shop
    private var panel: LinearLayout? = null
    private var tabsRow: LinearLayout? = null
    private var itemsRow: LinearLayout? = null
    private var infoText: TextView? = null
    private var checkBtn: IconButtonView? = null
    private var removeBtn: IconButtonView? = null
    private var shopCat = FCat.BEDROOM
    private var selected: Furn? = null

    private val green = Color.parseColor("#2EAD4B")
    private val red = Color.parseColor("#E5484D")
    private val purple = Color.parseColor("#7C5CFF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(Color.parseColor("#F5F3FF"))
        setContentView(root)
        if (intent.getBooleanExtra(EXTRA_INTERIOR, false)) {
            showInterior(HomeStore.selected(this))
        } else {
            showList()
        }
    }

    override fun onBackPressed() {
        when {
            panel != null -> closeShop()
            inInterior -> showList()
            else -> super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        // The floating house on the screen takes the chosen look
        if (GhostOverlayService.isRunning) {
            try {
                GhostOverlayService.outfitNow(this)
            } catch (e: RuntimeException) {
                // it is refreshed later anyway
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun pill(color: Int, radius: Int = 22): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radius).toFloat()
        return g
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.gravity = Gravity.CENTER
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        return t
    }

    /** A big round picture button of [sizeDp] with small margins (for a LinearLayout). */
    private fun icon(kind: Int, color: Int, sizeDp: Int, onClick: () -> Unit): IconButtonView {
        val v = IconButtonView(this, kind, color)
        v.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        v.layoutParams = lp
        return v
    }

    // =====================================================================
    // The houses to choose from
    // =====================================================================

    private fun showList() {
        inInterior = false
        panel = null
        handler.removeCallbacksAndMessages(null)
        root.removeAllViews()
        ghostViews.clear()

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(dp(14), dp(30), dp(14), dp(24))
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(icon(IconButtonView.BACK, purple, 60) { finish() })
        head.addView(label("منزل الشبح", 26f, Color.parseColor("#2B2540"), true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(head)

        val selectedId = HomeStore.selected(this)
        for (pair in HouseDefs.all.chunked(2)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (def in pair) {
                val unlocked = HomeStore.isUnlocked(this, def.id)
                val card = HouseCardView(this, def, unlocked, def.id == selectedId, HomeStore.level(this, def.id))
                card.setOnClickListener { onHouseTapped(def) }
                val lp = LinearLayout.LayoutParams(0, dp(215), 1f)
                lp.setMargins(dp(5), dp(8), dp(5), dp(4))
                row.addView(card, lp)
            }
            column.addView(row)
        }
        val scroll = ScrollView(this)
        scroll.addView(column)
        root.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun onHouseTapped(def: HouseDef) {
        if (HomeStore.isUnlocked(this, def.id)) {
            HomeStore.select(this, def.id)
            showInterior(def.id)
        } else {
            showLockedDialog(def)
        }
    }

    /** A closed house: its picture, a star meter and a big green tick to open it. */
    private fun showLockedDialog(def: HouseDef) {
        val have = Wardrobe.stars(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.setPadding(dp(16), dp(10), dp(16), dp(10))
        content.addView(HouseCardView(this, def, false, false, 1), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200)))
        content.addView(MeterView(this, 0, have, def.starCost), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        val buttons = LinearLayout(this)
        buttons.gravity = Gravity.CENTER
        val dialog = AlertDialog.Builder(this).setView(content).create()
        val check = icon(IconButtonView.CHECK, green, 74) {
            if (HomeStore.unlock(this, def.id)) {
                dialog.dismiss()
                Toast.makeText(this, "فُتح ${def.name}!", Toast.LENGTH_LONG).show()
                showList()
            } else {
                Toast.makeText(this, "ينقصك ${def.starCost - Wardrobe.stars(this)} نجمة", Toast.LENGTH_LONG).show()
            }
        }
        check.dimmed = have < def.starCost
        buttons.addView(check)
        buttons.addView(icon(IconButtonView.CLOSE, red, 74) { dialog.dismiss() })
        content.addView(buttons)
        dialog.show()
    }

    // =====================================================================
    // Inside the house: the sleeping ghosts and the big picture buttons
    // =====================================================================

    private fun canUpgrade(id: Int): Boolean {
        val level = HomeStore.level(this, id)
        if (level >= HouseUpgrades.MAX_LEVEL) return false
        val cost = HouseUpgrades.cost(level)
        return Wardrobe.stars(this) >= cost.first && HomeStore.materials(this) >= cost.second
    }

    private fun showInterior(id: Int, celebrate: Boolean = false) {
        inInterior = true
        panel = null
        selected = null
        houseId = id
        handler.removeCallbacksAndMessages(null)
        root.removeAllViews()
        ghostViews.clear()
        val def = HouseDefs.get(id)
        val level = HomeStore.level(this, id)
        state = SceneState(FurnStore.placed(this, id))
        val match = FrameLayout.LayoutParams.MATCH_PARENT

        root.addView(RoomSceneView(this, id, level, false, state), FrameLayout.LayoutParams(match, match))
        val ghostLayer = FrameLayout(this)
        root.addView(ghostLayer, FrameLayout.LayoutParams(match, match))
        root.addView(RoomSceneView(this, id, level, true, state), FrameLayout.LayoutParams(match, match))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(12), dp(28), dp(12), dp(8))
        bar.addView(icon(IconButtonView.BACK, purple, 54) { finish() })
        val title = label("${def.name}  •  $level", 16f, Color.WHITE, true)
        title.background = pill(Color.parseColor("#997C5CFF"))
        title.setPadding(dp(14), dp(8), dp(14), dp(8))
        val tlp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tlp.setMargins(dp(10), 0, 0, 0)
        bar.addView(title, tlp)
        root.addView(bar, FrameLayout.LayoutParams(match, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        // Five big picture buttons: decorate, upgrade, houses, village, playground
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER
        bottom.setPadding(dp(6), dp(8), dp(6), dp(18))
        bottom.addView(icon(IconButtonView.SHOP, Color.parseColor("#E5469A"), 62) { openShop() })
        val up = icon(IconButtonView.UPGRADE, purple, 62) { showUpgradeDialog() }
        up.badge = canUpgrade(id)
        bottom.addView(up)
        bottom.addView(icon(IconButtonView.HOUSES, Color.parseColor("#3D8BFD"), 62) { showList() })
        bottom.addView(icon(IconButtonView.VILLAGE, Color.parseColor("#3DAE5C"), 62) {
            startActivity(Intent(this, VillageActivity::class.java))
        })
        bottom.addView(icon(IconButtonView.PLAY, Color.parseColor("#FF8A2B"), 62) {
            startActivity(Intent(this, PlaygroundActivity::class.java))
        })
        root.addView(bottom, FrameLayout.LayoutParams(match, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        bottomBar = bottom

        if (celebrate) state.celebrateUntil = SystemClock.uptimeMillis() + 1_600L
        root.post { placeGhosts(ghostLayer, level, 0) }
    }

    /** The babies of the family sleep in the beds (one default ghost if there are no babies yet). */
    private fun placeGhosts(layer: FrameLayout, level: Int, attempt: Int) {
        if (!inInterior) return
        if (root.width == 0 || root.height == 0) {
            if (attempt < 10) handler.postDelayed({ placeGhosts(layer, level, attempt + 1) }, 80L)
            return
        }
        val beds = RoomSceneView.bedCount(level)
        var colors = HomeStore.babyColorIndexes(this)
        if (colors.isEmpty()) colors = listOf(0)
        val count = minOf(beds, colors.size)
        val s = root.width / RoomSceneView.SCENE_W
        val oy = (root.height - RoomSceneView.SCENE_H * s) / 2f
        for (i in 0 until count) {
            val box = RoomSceneView.ghostBox(i, beds)
            val ghost = GhostView(this)
            ghost.setBodyColor(Family.color(colors[i]))
            ghost.playful = true
            ghost.personality = Family.personalityFor(colors[i])
            ghost.setForcedSleep(true)
            ghost.setOnClickListener { wake(ghost) }
            val lp = FrameLayout.LayoutParams((box[2] * s).toInt(), (box[3] * s).toInt())
            lp.gravity = Gravity.TOP or Gravity.LEFT
            lp.leftMargin = (box[0] * s).toInt()
            lp.topMargin = (oy + box[1] * s).toInt()
            layer.addView(ghost, lp)
            ghostViews.add(ghost)
        }
        applyGhostHat()
    }

    /** A tap wakes the ghost for a moment: it hops happily and then falls asleep again. */
    private fun wake(ghost: GhostView) {
        ghost.setForcedSleep(false)
        ghost.showFed()
        handler.removeCallbacksAndMessages(ghost)
        handler.postAtTime({ ghost.setForcedSleep(true) }, ghost, SystemClock.uptimeMillis() + 3_500L)
    }

    /** The sleeping ghosts wear the night cap chosen in the shop (or the one being previewed). */
    private fun applyGhostHat() {
        val hat = state.itemFor(FSlot.GHOST_HAT)
        val variant = if (hat == null) 0 else 4 + hat.style
        for (g in ghostViews) g.setOutfit(variant, 0, 0, 0, 0)
    }

    // =====================================================================
    // Upgrading the house: pictures, meters and a big green tick
    // =====================================================================

    private fun artColumn(level: Int): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.addView(HouseArtView(this, houseId, level), LinearLayout.LayoutParams(dp(125), dp(135)))
        col.addView(label("$level", 18f, Color.parseColor("#2B2540"), true))
        return col
    }

    private fun showUpgradeDialog() {
        val id = houseId
        val level = HomeStore.level(this, id)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.setPadding(dp(16), dp(10), dp(16), dp(10))
        val previews = LinearLayout(this)
        previews.orientation = LinearLayout.HORIZONTAL
        previews.gravity = Gravity.CENTER
        previews.addView(artColumn(level))
        if (level < HouseUpgrades.MAX_LEVEL) {
            previews.addView(label("  ←  ", 26f, purple, true))
            previews.addView(artColumn(level + 1))
        }
        content.addView(previews)
        val dialog = AlertDialog.Builder(this).setView(content).create()
        val buttons = LinearLayout(this)
        buttons.gravity = Gravity.CENTER

        if (level >= HouseUpgrades.MAX_LEVEL) {
            content.addView(label("منزلك في أعلى مستوى!", 17f, Color.parseColor("#2B2540"), true))
            buttons.addView(icon(IconButtonView.CLOSE, red, 74) { dialog.dismiss() })
            content.addView(buttons)
            dialog.show()
            return
        }
        val cost = HouseUpgrades.cost(level)
        val stars = Wardrobe.stars(this)
        val materials = HomeStore.materials(this)
        content.addView(label(HouseUpgrades.adds(level + 1), 14f, Color.parseColor("#5C5675"), false))
        content.addView(MeterView(this, 0, stars, cost.first), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))
        content.addView(MeterView(this, 1, materials, cost.second), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))
        val check = icon(IconButtonView.CHECK, green, 74) {
            if (stars < cost.first || materials < cost.second) {
                Toast.makeText(this, "ينقصك نجوم أو مواد", Toast.LENGTH_LONG).show()
            } else if (Wardrobe.spendStars(this, cost.first) && HomeStore.spendMaterials(this, cost.second)) {
                HomeStore.setLevel(this, id, level + 1)
                dialog.dismiss()
                showInterior(id, true)
            }
        }
        check.dimmed = stars < cost.first || materials < cost.second
        buttons.addView(check)
        buttons.addView(icon(IconButtonView.CLOSE, red, 74) { dialog.dismiss() })
        content.addView(buttons)
        dialog.show()
    }

    // =====================================================================
    // "Decorate your house": picture tabs, big picture cards, one big tick
    // =====================================================================

    private fun openShop() {
        if (panel != null) return
        bottomBar?.visibility = View.GONE
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.background = pill(Color.parseColor("#F8F5FF"), 24)
        p.setPadding(dp(8), dp(8), dp(8), dp(12))

        // Picture tabs, the first button closes the shop
        val tabsScroll = HorizontalScrollView(this)
        tabsScroll.isHorizontalScrollBarEnabled = false
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.gravity = Gravity.CENTER_VERTICAL
        tabs.addView(icon(IconButtonView.BACK, Color.parseColor("#9A8FC0"), 56) { closeShop() })
        for (cat in FCat.values()) {
            tabs.addView(icon(IconButtonView.forCategory(cat), Color.parseColor("#B07CFF"), 56) {
                shopCat = cat
                refreshShop()
            })
        }
        tabsScroll.addView(tabs)
        p.addView(tabsScroll)

        val itemsScroll = HorizontalScrollView(this)
        itemsScroll.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        itemsScroll.addView(row)
        p.addView(itemsScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(152)))

        val info = label("اختر صورة لتراها في الغرفة", 13f, Color.parseColor("#5C5675"), false)
        p.addView(info)
        val actions = LinearLayout(this)
        actions.gravity = Gravity.CENTER
        val check = icon(IconButtonView.CHECK, green, 66) { }
        val remove = icon(IconButtonView.CLOSE, red, 66) { }
        actions.addView(check)
        actions.addView(remove)
        p.addView(actions)

        panel = p
        tabsRow = tabs
        itemsRow = row
        infoText = info
        checkBtn = check
        removeBtn = remove
        root.addView(p, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        refreshShop()
    }

    private fun closeShop() {
        state.previewSlot = null
        state.previewItem = null
        applyGhostHat()
        panel?.let { root.removeView(it) }
        panel = null
        selected = null
        bottomBar?.visibility = View.VISIBLE
    }

    /** Redraws the tabs and the item cards (what the child can use now comes first). */
    private fun refreshShop() {
        if (panel == null) return
        val tabs = tabsRow ?: return
        val cats = FCat.values()
        for (i in cats.indices) {
            (tabs.getChildAt(i + 1) as IconButtonView).chosen = cats[i] == shopCat
        }
        val row = itemsRow ?: return
        row.removeAllViews()
        val have = Wardrobe.stars(this)
        val ordered = Furniture.inCategory(shopCat).sortedBy {
            when {
                FurnStore.isOwned(this, it) -> 0
                it.stars <= have -> 1
                else -> 2
            }
        }
        for (item in ordered) {
            val card = FurnCardView(
                this, item,
                FurnStore.isOwned(this, item),
                state.placed[item.slot] == item.id,
                selected?.id == item.id
            )
            card.setOnClickListener { selectItem(item) }
            val lp = LinearLayout.LayoutParams(dp(112), dp(146))
            lp.setMargins(dp(4), dp(2), dp(4), dp(2))
            row.addView(card, lp)
        }
        refreshAction()
    }

    /** The item is shown in the room right away, before it is chosen. */
    private fun selectItem(item: Furn) {
        selected = item
        state.previewSlot = item.slot
        state.previewItem = item.id
        applyGhostHat()
        refreshShop()
    }

    private fun refreshAction() {
        val info = infoText ?: return
        val check = checkBtn ?: return
        val remove = removeBtn ?: return
        val item = selected
        check.visibility = View.GONE
        remove.visibility = View.GONE
        if (item == null) {
            info.text = "اختر صورة لتراها في الغرفة"
            return
        }
        val isPlaced = state.placed[item.slot] == item.id
        val owned = FurnStore.isOwned(this, item)
        val have = Wardrobe.stars(this)
        when {
            isPlaced -> {
                info.text = "${item.name}: في الغرفة"
                if (Furniture.isRemovable(item.slot)) {
                    remove.visibility = View.VISIBLE
                    remove.setOnClickListener { removeItem(item) }
                }
            }
            owned -> {
                info.text = "${item.name}: ضعه في الغرفة"
                check.visibility = View.VISIBLE
                check.dimmed = false
                check.setOnClickListener { placeItem(item) }
            }
            else -> {
                info.text = "${item.name}: ${item.stars} نجمة (عندك $have)"
                check.visibility = View.VISIBLE
                check.dimmed = have < item.stars
                check.setOnClickListener { buyItem(item) }
            }
        }
    }

    private fun buyItem(item: Furn) {
        if (FurnStore.buy(this, item)) {
            placeItem(item)
        } else {
            Toast.makeText(this, "ينقصك ${item.stars - Wardrobe.stars(this)} نجمة", Toast.LENGTH_LONG).show()
        }
    }

    /** Puts the item in the room: a celebration, and the ghosts wake up happy for a moment. */
    private fun placeItem(item: Furn) {
        FurnStore.place(this, houseId, item.slot, item.id)
        state.placed = FurnStore.placed(this, houseId)
        state.previewSlot = null
        state.previewItem = null
        state.celebrateUntil = SystemClock.uptimeMillis() + 1_600L
        for (g in ghostViews) wake(g)
        applyGhostHat()
        refreshShop()
    }

    private fun removeItem(item: Furn) {
        FurnStore.place(this, houseId, item.slot, null)
        state.placed = FurnStore.placed(this, houseId)
        state.previewSlot = null
        state.previewItem = null
        applyGhostHat()
        refreshShop()
    }
}
