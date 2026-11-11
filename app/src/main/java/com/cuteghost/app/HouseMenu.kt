package com.cuteghost.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Three clear, cute round buttons beside the floating house: hide the ghost, the wardrobe (touching it
 * shows the hats, scarves and the rest that are available) and the village. It follows the house.
 */
class HouseMenu(
    private val context: Context,
    private val wm: WindowManager,
    private val host: Host
) {
    interface Host {
        fun onHideGhosts()
        fun onWardrobeChanged()
        fun onOpenVillage()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val d = context.resources.displayMetrics.density
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var tabs: LinearLayout? = null
    private var chips: GridLayout? = null
    private var hideBtn: IconButtonView? = null
    private var confirmBtn: IconButtonView? = null
    private var slot = Wardrobe.Slot.HAT
    private var hx = 0
    private var hy = 0
    private var hw = 0
    private var hh = 0
    private val revert = Runnable { showConfirm(false) }

    private fun dp(v: Int): Int = (v * d).toInt()

    private fun button(kind: Int, color: Int, sizeDp: Int, click: () -> Unit): IconButtonView {
        val b = IconButtonView(context, kind, color)
        b.setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        b.layoutParams = lp
        return b
    }

    fun show(houseX: Int, houseY: Int, houseW: Int, houseH: Int): Boolean {
        hx = houseX
        hy = houseY
        hw = houseW
        hh = houseH
        val r = LinearLayout(context)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL

        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        val hide = button(IconButtonView.MENU_HIDE, Color.parseColor("#7C5CFF"), 48) { showConfirm(true) }
        val confirm = button(IconButtonView.CHECK, Color.parseColor("#E5484D"), 48) { host.onHideGhosts() }
        confirm.visibility = View.GONE
        col.addView(hide)
        col.addView(confirm)
        col.addView(button(IconButtonView.MENU_WARDROBE, Color.parseColor("#E5469A"), 48) { togglePanel() })
        col.addView(button(IconButtonView.VILLAGE, Color.parseColor("#3DAE5C"), 48) { host.onOpenVillage() })
        hideBtn = hide
        confirmBtn = confirm
        r.addView(col)

        val p = LinearLayout(context)
        p.orientation = LinearLayout.VERTICAL
        p.visibility = View.GONE
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#F2F8F5FF"))
        bg.cornerRadius = dp(18).toFloat()
        p.background = bg
        p.setPadding(dp(6), dp(6), dp(6), dp(6))
        val tabRow = LinearLayout(context)
        tabRow.orientation = LinearLayout.HORIZONTAL
        val kinds = intArrayOf(IconButtonView.W_HAT, IconButtonView.W_SCARF, IconButtonView.W_WINGS, IconButtonView.W_GLOW, IconButtonView.W_TRAIL)
        val slots = Wardrobe.Slot.values()
        for (i in kinds.indices) {
            val b = IconButtonView(context, kinds[i], Color.parseColor("#B07CFF"))
            b.setOnClickListener {
                slot = slots[i]
                refreshPanel()
            }
            val lp = LinearLayout.LayoutParams(dp(34), dp(34))
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            tabRow.addView(b, lp)
        }
        p.addView(tabRow)
        val grid = GridLayout(context)
        grid.columnCount = 2
        p.addView(grid)
        r.addView(p)
        tabs = tabRow
        chips = grid
        panel = p

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        return try {
            wm.addView(r, lp)
            root = r
            params = lp
            reposition()
            true
        } catch (e: RuntimeException) {
            Log.e("HouseMenu", "Could not show the house menu", e)
            false
        }
    }

    /** Hiding the ghost needs a second touch (on a red tick), so a small child cannot hide it by accident. */
    private fun showConfirm(on: Boolean) {
        handler.removeCallbacks(revert)
        hideBtn?.visibility = if (on) View.GONE else View.VISIBLE
        confirmBtn?.visibility = if (on) View.VISIBLE else View.GONE
        if (on) handler.postDelayed(revert, 3_000L)
        root?.post { reposition() }
    }

    private fun togglePanel() {
        val p = panel ?: return
        if (p.visibility == View.VISIBLE) {
            p.visibility = View.GONE
        } else {
            p.visibility = View.VISIBLE
            refreshPanel()
        }
        root?.post { reposition() }
    }

    /** The category tabs and the items of the chosen category that are already available. */
    private fun refreshPanel() {
        val row = tabs ?: return
        val grid = chips ?: return
        val slots = Wardrobe.Slot.values()
        for (i in slots.indices) (row.getChildAt(i) as IconButtonView).chosen = slots[i] == slot
        grid.removeAllViews()
        val current = Wardrobe.equipped(context, slot)
        val options = ArrayList<Pair<String, Int>>()
        options.add(Pair(context.getString(R.string.item_none), 0))
        for (item in Wardrobe.items.filter { x -> x.slot == slot && Wardrobe.isUnlocked(context, x) }.sortedBy { x -> x.variant }) {
            options.add(Pair(context.getString(item.nameRes), item.variant))
        }
        for (option in options) {
            val chosen = option.second == current
            val chip = TextView(context)
            chip.text = option.first
            chip.textSize = 12f
            chip.gravity = Gravity.CENTER
            chip.setPadding(dp(4), dp(9), dp(4), dp(9))
            chip.setTextColor(if (chosen) Color.WHITE else Color.parseColor("#2B2540"))
            val bg = GradientDrawable()
            bg.setColor(if (chosen) Color.parseColor("#7C5CFF") else Color.parseColor("#EDE7FF"))
            bg.cornerRadius = dp(12).toFloat()
            chip.background = bg
            chip.setOnClickListener {
                Wardrobe.equip(context, slot, option.second)
                host.onWardrobeChanged()
                refreshPanel()
            }
            val lp = GridLayout.LayoutParams()
            lp.width = dp(76)
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            grid.addView(chip, lp)
        }
        root?.post { reposition() }
    }

    /** Follows the house: beside it on the side that has room, centred on it vertically. */
    fun moveTo(houseX: Int, houseY: Int) {
        hx = houseX
        hy = houseY
        reposition()
    }

    private fun reposition() {
        val r = root ?: return
        val p = params ?: return
        r.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val w = r.measuredWidth
        val h = r.measuredHeight
        val dm = context.resources.displayMetrics
        val gap = dp(6)
        var x = hx + hw + gap
        if (x + w > dm.widthPixels) x = maxOf(0, hx - w - gap)
        p.x = x
        p.y = (hy + (hh - h) / 2).coerceIn(0, maxOf(0, dm.heightPixels - h))
        try {
            wm.updateViewLayout(r, p)
        } catch (e: IllegalArgumentException) {
            Log.w("HouseMenu", "Could not move the house menu", e)
        }
    }

    fun remove() {
        handler.removeCallbacks(revert)
        val r = root ?: return
        root = null
        try {
            wm.removeViewImmediate(r)
        } catch (e: IllegalArgumentException) {
            // already removed
        }
    }
}
