package com.cuteghost.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/**
 * The child's home screen: a few giant picture buttons and nothing to read. Everything for grown-ups
 * (settings, the update, tests) is behind the small "للأهل" button, which asks the multiplication question.
 * During play time this screen is the "home" of the phone: it hides the system bars, asks Android to pin
 * the app, ignores the back button, and the guard service brings it back if the child leaves.
 */
class ChildHomeActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var locked = false
    private lateinit var parentButton: TextView
    private lateinit var ghostPicture: ImageView
    private lateinit var goalsTile: IconButtonView

    private val tick = object : Runnable {
        override fun run() {
            syncLock()
            handler.postDelayed(this, 1000L)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        locked = false
        syncLock()
        refreshBadges()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000L)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ScreenLock.isActive(this)) hideBars()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (ScreenLock.isActive(this)) return
        super.onBackPressed()
    }

    // ---------- Screen ----------

    private fun tile(kind: Int, color: String, target: Class<*>): IconButtonView {
        val b = IconButtonView(this, kind, Color.parseColor(color))
        b.setOnClickListener { startActivity(Intent(this, target)) }
        return b
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        val sky = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#BDE6FF"), Color.parseColor("#F5F3FF"))
        )
        root.background = sky

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER
        column.setPadding(dp(16), dp(72), dp(16), dp(24))

        // The ghost says hello; touching it brings the floating ghost onto the phone
        ghostPicture = ImageView(this)
        ghostPicture.setImageResource(R.drawable.ic_launcher)
        ghostPicture.setOnClickListener { onGhostTouched() }
        column.addView(ghostPicture, LinearLayout.LayoutParams(dp(120), dp(120)))

        val side = min((resources.displayMetrics.widthPixels - dp(16 * 2 + 16 * 3)) / 2, dp(168))
        val village = tile(IconButtonView.VILLAGE, "#4CC38A", VillageActivity::class.java)
        val house = tile(IconButtonView.HOUSES, "#B07CFF", HouseActivity::class.java)
        val play = tile(IconButtonView.PLAY, "#FF8A2B", PlaygroundActivity::class.java)
        goalsTile = tile(IconButtonView.CHECK, "#FFB400", GoalsActivity::class.java)

        val rowOne = row(village, house, side)
        val rowTwo = row(play, goalsTile, side)
        val rowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowParams.topMargin = dp(20)
        column.addView(rowOne, rowParams)
        column.addView(rowTwo, rowParams)
        root.addView(
            column,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        // For grown-ups only: small, in the corner
        parentButton = TextView(this)
        parentButton.text = "للأهل"
        parentButton.textSize = 13f
        parentButton.gravity = Gravity.CENTER
        parentButton.setTextColor(Color.parseColor("#6B6590"))
        val pill = GradientDrawable()
        pill.setColor(Color.parseColor("#66FFFFFF"))
        pill.cornerRadius = dp(16).toFloat()
        parentButton.background = pill
        parentButton.setPadding(dp(14), dp(8), dp(14), dp(8))
        parentButton.setOnClickListener { startActivity(Intent(this, ParentActivity::class.java)) }
        val plp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        )
        plp.setMargins(dp(14), dp(36), dp(14), dp(14))
        root.addView(parentButton, plp)

        setContentView(root)
    }

    private fun row(a: View, b: View, side: Int): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(side, side)
        lp.setMargins(dp(8), dp(8), dp(8), dp(8))
        r.addView(a, lp)
        r.addView(b, LinearLayout.LayoutParams(lp))
        return r
    }

    private fun refreshBadges() {
        goalsTile.badge = DailyGoals.enabled(this) && DailyGoals.doneCount(this) < DailyGoals.total()
    }

    // ---------- The floating ghost ----------

    private fun onGhostTouched() {
        ghostPicture.animate().scaleX(1.18f).scaleY(1.18f).setDuration(140L).withEndAction {
            ghostPicture.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
        }.start()
        if (ScreenLock.isActive(this) || GhostOverlayService.isRunning) return
        if (!Settings.canDrawOverlays(this)) {
            // The permission screens are for grown-ups: they land on the full settings screen
            ParentGate.ask(this, "للأهل", "لإظهار الشبح العائم يلزم تفعيل صلاحية العرض فوق التطبيقات.", {}) {
                startActivity(Intent(this, MainActivity::class.java))
            }
            return
        }
        try {
            GhostOverlayService.start(this)
        } catch (e: RuntimeException) {
            // the grown-ups' screen shows the reason
        }
    }

    // ---------- Play time ----------

    private fun syncLock() {
        val now = ScreenLock.isActive(this)
        if (now) {
            parentButton.text = "للأهل • وقت اللعب: باقي ${ScreenLock.minutesLeft(this)} د"
            if (!locked) enterKiosk()
        } else if (locked) {
            exitKiosk()
            parentButton.text = "للأهل"
        }
        locked = now
    }

    private fun enterKiosk() {
        hideBars()
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) startLockTask()
        } catch (e: RuntimeException) {
            // pinning is not available here: the guard service still brings this screen back
        }
    }

    private fun exitKiosk() {
        try {
            stopLockTask()
        } catch (e: RuntimeException) {
            // it was not pinned
        }
        showBars()
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    @Suppress("DEPRECATION")
    private fun showBars() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}
