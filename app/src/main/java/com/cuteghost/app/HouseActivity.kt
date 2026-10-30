package com.cuteghost.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * "Ghost house": the six houses, the inside of the chosen house with its sleeping ghosts, and the
 * "decorate your house" shop with preview. Everything is saved in [HomeStore] and [FurnStore].
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
    private var itemsRow: LinearLayout? = null
    private var infoText: TextView? = null
    private var applyBtn: Button? = null
    private var removeBtn: Button? = null
    private var shopCat = FCat.BEDROOM
    private var selected: Furn? = null

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

    private fun button(text: String, color: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.textSize = 14f
        b.setTextColor(Color.WHITE)
        b.background = pill(color)
        b.setOnClickListener { onClick() }
        return b
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
        column.setPadding(dp(14), dp(34), dp(14), dp(24))
        column.addView(label("منزل الشبح", 26f, Color.parseColor("#2B2540"), true))
        column.addView(label("النجوم: ${Wardrobe.stars(this)}   •   المواد: ${HomeStore.materials(this)}", 15f, Color.parseColor("#5C5675"), false))

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
        val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
        blp.topMargin = dp(18)
        column.addView(button("رجوع", Color.parseColor("#7C5CFF")) { finish() }, blp)

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

    /** A closed house: a preview and what is needed to open it. */
    private fun showLockedDialog(def: HouseDef) {
        val have = Wardrobe.stars(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(10), dp(16), dp(0))
        content.addView(HouseCardView(this, def, false, false, 1), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(210)))
        content.addView(label(def.about, 15f, Color.parseColor("#2B2540"), false))
        content.addView(
            label(
                "لفتحه تحتاج ${def.starCost} نجمة، وعندك $have.\nتُجمع النجوم برعاية الأشباح ومن البوابة السحرية.",
                14f, Color.parseColor("#5C5675"), false
            )
        )
        val dialog = AlertDialog.Builder(this)
            .setTitle(def.name)
            .setView(content)
            .setPositiveButton("افتحه بـ ${def.starCost} نجمة", null)
            .setNegativeButton("إغلاق", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (HomeStore.unlock(this, def.id)) {
                    dialog.dismiss()
                    Toast.makeText(this, "فُتح ${def.name}! صار متاحًا دائمًا", Toast.LENGTH_LONG).show()
                    showList()
                } else {
                    Toast.makeText(this, "تحتاج ${def.starCost} نجمة وعندك ${Wardrobe.stars(this)}", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    // =====================================================================
    // Inside the house: the sleeping ghosts
    // =====================================================================

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
        bar.setPadding(dp(12), dp(30), dp(12), dp(8))
        val title = label("${def.name}  •  المستوى $level", 16f, Color.WHITE, true)
        title.background = pill(Color.parseColor("#997C5CFF"))
        title.setPadding(dp(14), dp(8), dp(14), dp(8))
        bar.addView(button("المنازل", Color.parseColor("#CC7C5CFF")) { showList() })
        val tlp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tlp.setMargins(dp(10), 0, 0, 0)
        bar.addView(title, tlp)
        root.addView(bar, FrameLayout.LayoutParams(match, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER
        bottom.setPadding(dp(16), dp(8), dp(16), dp(22))
        val shopBtn = button("زيّن منزلك", Color.parseColor("#E5469A")) { openShop() }
        bottom.addView(shopBtn, LinearLayout.LayoutParams(dp(165), dp(54)))
        val upBtn = button("ترقية المنزل", Color.parseColor("#7C5CFF")) { showUpgradeDialog() }
        val ulp = LinearLayout.LayoutParams(dp(165), dp(54))
        ulp.leftMargin = dp(10)
        bottom.addView(upBtn, ulp)
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
    // "Decorate your house": categories, items, preview, buy, place
    // =====================================================================

    // =====================================================================
    // Upgrading the house: levels 1 to 5, each one changes how it looks
    // =====================================================================

    private fun artColumn(title: String, level: Int): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.addView(HouseArtView(this, houseId, level), LinearLayout.LayoutParams(dp(120), dp(130)))
        col.addView(label(title, 14f, Color.parseColor("#2B2540"), true))
        return col
    }

    private fun showUpgradeDialog() {
        val id = houseId
        val level = HomeStore.level(this, id)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(10), dp(16), 0)
        val previews = LinearLayout(this)
        previews.orientation = LinearLayout.HORIZONTAL
        previews.gravity = Gravity.CENTER
        previews.addView(artColumn("الحالي: المستوى $level", level))
        if (level < HouseUpgrades.MAX_LEVEL) {
            previews.addView(label("  ←  ", 22f, Color.parseColor("#7C5CFF"), true))
            previews.addView(artColumn("القادم: المستوى ${level + 1}", level + 1))
        }
        content.addView(previews)

        val builder = AlertDialog.Builder(this).setTitle(HouseDefs.get(id).name).setView(content)
            .setNegativeButton("إغلاق", null)
        if (level >= HouseUpgrades.MAX_LEVEL) {
            content.addView(label("منزلك في أعلى مستوى، وفيه كل التفاصيل!", 15f, Color.parseColor("#2B2540"), false))
            builder.show()
            return
        }
        val cost = HouseUpgrades.cost(level)
        content.addView(label("المستوى ${level + 1} يضيف: ${HouseUpgrades.adds(level + 1)}", 15f, Color.parseColor("#2B2540"), false))
        content.addView(
            label(
                "التكلفة: ${cost.first} نجمة (عندك ${Wardrobe.stars(this)}) و${cost.second} مادة (عندك ${HomeStore.materials(this)}).\nأثاثك كله يبقى كما هو.",
                14f, Color.parseColor("#5C5675"), false
            )
        )
        val dialog = builder.setPositiveButton("رقِّ المنزل", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (Wardrobe.stars(this) < cost.first || HomeStore.materials(this) < cost.second) {
                    Toast.makeText(this, "تحتاج ${cost.first} نجمة و${cost.second} مادة", Toast.LENGTH_LONG).show()
                } else if (Wardrobe.spendStars(this, cost.first) && HomeStore.spendMaterials(this, cost.second)) {
                    HomeStore.setLevel(this, id, level + 1)
                    dialog.dismiss()
                    Toast.makeText(this, "ارتقى المنزل إلى المستوى ${level + 1}!", Toast.LENGTH_LONG).show()
                    showInterior(id, true)
                }
            }
        }
        dialog.show()
    }

    private fun openShop() {
        if (panel != null) return
        bottomBar?.visibility = View.GONE
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.background = pill(Color.parseColor("#F8F5FF"), 24)
        p.setPadding(dp(10), dp(10), dp(10), dp(14))

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(label("زيّن منزلك   •   النجوم: ${Wardrobe.stars(this)}", 16f, Color.parseColor("#2B2540"), true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(button("إغلاق", Color.parseColor("#9A8FC0")) { closeShop() }, LinearLayout.LayoutParams(dp(90), dp(44)))
        p.addView(head)

        val tabsScroll = HorizontalScrollView(this)
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        for (cat in FCat.values()) {
            val b = button(Furniture.catName(cat), if (cat == shopCat) Color.parseColor("#7C5CFF") else Color.parseColor("#B9A8F5")) {
                shopCat = cat
                refreshShop()
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42))
            lp.setMargins(dp(3), dp(6), dp(3), dp(4))
            tabs.addView(b, lp)
        }
        tabsScroll.addView(tabs)
        tabsScroll.tag = tabs
        p.addView(tabsScroll)

        val itemsScroll = HorizontalScrollView(this)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        itemsScroll.addView(row)
        p.addView(itemsScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(134)))

        val info = label("اختر عنصرًا لمعاينته في الغرفة", 14f, Color.parseColor("#5C5675"), false)
        p.addView(info)
        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER
        val apply = button("", Color.parseColor("#4FAE5A")) { }
        val remove = button("إزالة من الغرفة", Color.parseColor("#E5484D")) { }
        val alp = LinearLayout.LayoutParams(0, dp(48), 1f)
        alp.setMargins(dp(4), dp(6), dp(4), 0)
        actions.addView(apply, alp)
        actions.addView(remove, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), dp(6), dp(4), 0) })
        p.addView(actions)

        panel = p
        itemsRow = row
        infoText = info
        applyBtn = apply
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

    /** Redraws the category tabs, the item cards and the action buttons. */
    private fun refreshShop() {
        val p = panel ?: return
        val tabsScroll = p.getChildAt(1) as HorizontalScrollView
        val tabs = tabsScroll.tag as LinearLayout
        val cats = FCat.values()
        for (i in cats.indices) {
            (tabs.getChildAt(i) as Button).background =
                pill(if (cats[i] == shopCat) Color.parseColor("#7C5CFF") else Color.parseColor("#B9A8F5"))
        }
        val row = itemsRow ?: return
        row.removeAllViews()
        for (item in Furniture.inCategory(shopCat)) {
            val card = FurnCardView(
                this, item,
                FurnStore.isOwned(this, item),
                state.placed[item.slot] == item.id,
                selected?.id == item.id
            )
            card.setOnClickListener { selectItem(item) }
            val lp = LinearLayout.LayoutParams(dp(100), dp(128))
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
        val apply = applyBtn ?: return
        val remove = removeBtn ?: return
        val item = selected
        apply.visibility = View.GONE
        remove.visibility = View.GONE
        if (item == null) {
            info.text = "اختر عنصرًا لمعاينته في الغرفة"
            return
        }
        val isPlaced = state.placed[item.slot] == item.id
        val owned = FurnStore.isOwned(this, item)
        val have = Wardrobe.stars(this)
        when {
            isPlaced -> {
                info.text = "${item.name}: موضوع في الغرفة"
                if (Furniture.isRemovable(item.slot)) remove.visibility = View.VISIBLE
                remove.setOnClickListener { removeItem(item) }
            }
            owned -> {
                info.text = "${item.name}: عندك، ضعه في الغرفة"
                apply.visibility = View.VISIBLE
                apply.text = "ضعه في المنزل"
                apply.setOnClickListener { placeItem(item) }
            }
            item.source == FSource.STARS -> {
                info.text = "${item.name}: ${item.stars} نجمة (عندك $have)"
                apply.visibility = View.VISIBLE
                apply.text = "اشتره بـ ${item.stars} نجمة"
                apply.setOnClickListener { buyItem(item) }
            }
            else -> {
                info.text = "${item.name}: مكافأة إعلان اختياري تُضاف في المرحلة القادمة، أو اشتره بـ ${item.stars} نجمة (عندك $have)"
                apply.visibility = View.VISIBLE
                apply.text = "اشتره بـ ${item.stars} نجمة"
                apply.setOnClickListener { buyItem(item) }
            }
        }
    }

    private fun buyItem(item: Furn) {
        if (FurnStore.buy(this, item)) {
            placeItem(item)
        } else {
            Toast.makeText(this, "تحتاج ${item.stars} نجمة وعندك ${Wardrobe.stars(this)}", Toast.LENGTH_LONG).show()
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
