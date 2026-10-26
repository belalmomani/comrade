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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * "Ghost house": the six houses to choose from, and the inside of the chosen house where the
 * little ghosts sleep in their beds. Progress is saved in [HomeStore].
 */
class HouseActivity : Activity() {

    companion object {
        const val EXTRA_INTERIOR = "open_interior"
    }

    private lateinit var root: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private var inInterior = false

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
        if (inInterior) showList() else super.onBackPressed()
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

    private fun pill(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(22).toFloat()
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
        b.textSize = 15f
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
        handler.removeCallbacksAndMessages(null)
        root.removeAllViews()

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
        val back = button("رجوع", Color.parseColor("#7C5CFF")) { finish() }
        val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
        blp.topMargin = dp(18)
        column.addView(back, blp)

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
        val preview = HouseCardView(this, def, false, false, 1)
        content.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(210)))
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

    private fun showInterior(houseId: Int) {
        inInterior = true
        handler.removeCallbacksAndMessages(null)
        root.removeAllViews()
        val def = HouseDefs.get(houseId)
        val level = HomeStore.level(this, houseId)
        val match = FrameLayout.LayoutParams.MATCH_PARENT

        root.addView(RoomSceneView(this, houseId, level, false), FrameLayout.LayoutParams(match, match))
        val ghostLayer = FrameLayout(this)
        root.addView(ghostLayer, FrameLayout.LayoutParams(match, match))
        root.addView(RoomSceneView(this, houseId, level, true), FrameLayout.LayoutParams(match, match))

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
            lp.leftMargin = (box[0] * s).toInt()
            lp.topMargin = (oy + box[1] * s).toInt()
            layer.addView(ghost, lp)
        }
    }

    /** A tap wakes the ghost for a moment: it hops happily and then falls asleep again. */
    private fun wake(ghost: GhostView) {
        ghost.setForcedSleep(false)
        ghost.showFed()
        handler.removeCallbacksAndMessages(ghost)
        handler.postAtTime({ ghost.setForcedSleep(true) }, ghost, SystemClock.uptimeMillis() + 3_500L)
    }
}
