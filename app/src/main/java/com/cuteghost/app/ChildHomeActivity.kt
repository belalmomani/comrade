package com.cuteghost.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
import kotlin.math.max
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
    private lateinit var sky: SkyView
    private val tiles = ArrayList<View>()
    private var lastTouch = System.currentTimeMillis()
    private var hintIndex = 0
    private var swayRight = true
    private var warned = false

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

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        lastTouch = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
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
        // The sky: a sun that slowly sets while play time runs, so the child sees time passing
        sky = SkyView(this)
        root.addView(
            sky,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL
        column.setPadding(dp(16), dp(SkyView.HORIZON_DP + 8), dp(16), dp(16))

        val widthSide = (resources.displayMetrics.widthPixels - dp(16 * 2 + 16 * 3)) / 2
        val heightSide = (resources.displayMetrics.heightPixels - dp(SkyView.HORIZON_DP + 8 + 24 + 40 + 40)) / 3
        val side = max(dp(90), min(min(widthSide, dp(150)), heightSide))
        val village = tile(IconButtonView.VILLAGE, "#4CC38A", VillageActivity::class.java)
        val house = tile(IconButtonView.HOUSES, "#B07CFF", HouseActivity::class.java)
        val play = tile(IconButtonView.PLAY, "#FF8A2B", PlaygroundActivity::class.java)
        goalsTile = tile(IconButtonView.CHECK, "#FFB400", GoalsActivity::class.java)
        val games = tile(IconButtonView.GAMES, "#3DA5F5", MiniGamesActivity::class.java)

        val rowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowParams.topMargin = dp(8)
        tiles.clear()
        tiles.add(village)
        tiles.add(house)
        tiles.add(play)
        tiles.add(goalsTile)
        tiles.add(games)
        column.addView(row(village, house, side), rowParams)
        column.addView(row(play, goalsTile, side), rowParams)

        // The ghost says hello; touching it brings the floating ghost onto the phone
        ghostPicture = ImageView(this)
        ghostPicture.setImageResource(R.drawable.ic_launcher)
        ghostPicture.setPadding(dp(14), dp(14), dp(14), dp(14))
        ghostPicture.setOnClickListener { onGhostTouched() }
        column.addView(row(games, ghostPicture, side), rowParams)

        root.addView(
            column,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        // For grown-ups only: small, in the corner
        parentButton = TextView(this)
        parentButton.text = "للأهل"
        parentButton.textSize = 13f
        parentButton.gravity = Gravity.CENTER
        parentButton.setTextColor(Color.parseColor("#5C5675"))
        val pill = GradientDrawable()
        pill.setColor(Color.parseColor("#88FFFFFF"))
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

    /** In the last two minutes the ghost gets sleepy: it sways slowly, and a soft lullaby plays once. */
    private fun sleepyWarning(active: Boolean) {
        val msLeft = ScreenLock.until(this) - System.currentTimeMillis()
        if (active && msLeft in 1..120_000L) {
            if (!warned) {
                warned = true
                Sounds.play(this, Snd.LULLABY)
            }
            swayRight = !swayRight
            ghostPicture.animate().rotation(if (swayRight) 7f else -7f).setDuration(900L).start()
            ghostPicture.alpha = 0.85f
        } else if (warned || ghostPicture.rotation != 0f) {
            warned = false
            ghostPicture.animate().rotation(0f).setDuration(200L).start()
            ghostPicture.alpha = 1f
        }
    }

    /** If nothing was touched for a while, one of the big buttons gives a little bounce (no sound). */
    private fun idleHint() {
        if (System.currentTimeMillis() - lastTouch < 10_000L || tiles.isEmpty()) return
        hintIndex = (hintIndex + 1) % tiles.size
        val tile = tiles[hintIndex]
        tile.animate().scaleX(1.12f).scaleY(1.12f).setDuration(260L).withEndAction {
            tile.animate().scaleX(1f).scaleY(1f).setDuration(260L).start()
        }.start()
        lastTouch = System.currentTimeMillis() - 4_000L // the next one comes about six seconds later
    }

    private fun syncLock() {
        val now = ScreenLock.isActive(this)
        sleepyWarning(now)
        idleHint()
        sky.setDayLeft(if (now) ScreenLock.fractionLeft(this) else 1f)
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


/**
 * The sky behind the home screen. In normal times the sun is high and smiling. During play time it
 * sinks slowly behind the hills while the sky turns orange and then purple, with stars at the very end.
 */
private class SkyView(context: Context) : View(context) {

    companion object {
        /** Where the hills start, measured from the top of the screen. */
        const val HORIZON_DP = 190
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var dayLeft = 1f

    fun setDayLeft(value: Float) {
        val v = value.coerceIn(0f, 1f)
        if (v != dayLeft) {
            dayLeft = v
            invalidate()
        }
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        val k = f.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
        )
    }

    /** Three colors spread over the length of play time: noon, evening, night. */
    private fun ramp(noon: String, evening: String, night: String, dusk: Float): Int {
        return if (dusk < 0.6f) {
            mix(Color.parseColor(noon), Color.parseColor(evening), dusk / 0.6f)
        } else {
            mix(Color.parseColor(evening), Color.parseColor(night), (dusk - 0.6f) / 0.4f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        val dusk = 1f - dayLeft
        val horizon = HORIZON_DP * d

        // sky
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, 0f, horizon,
            ramp("#8FD3FF", "#FFC27A", "#5B4B8A", dusk),
            ramp("#E6F6FF", "#FFE9C7", "#FF9A76", dusk),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, horizon + 2f, paint)
        paint.shader = null

        // stars come out at the very end
        if (dusk > 0.75f) {
            paint.color = Color.WHITE
            paint.alpha = (255 * ((dusk - 0.75f) / 0.25f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(w * 0.15f, horizon * 0.22f, 2.5f * d, paint)
            canvas.drawCircle(w * 0.3f, horizon * 0.1f, 2f * d, paint)
            canvas.drawCircle(w * 0.72f, horizon * 0.16f, 2.5f * d, paint)
            canvas.drawCircle(w * 0.88f, horizon * 0.3f, 2f * d, paint)
            canvas.drawCircle(w * 0.62f, horizon * 0.06f, 1.8f * d, paint)
        }

        // the sun sinks from high up to behind the hills
        val r = 34f * d
        val cx = w / 2f
        val startY = 62f * d
        val endY = horizon + r * 0.9f
        val cy = startY + (endY - startY) * dusk
        paint.color = mix(Color.parseColor("#FFE066"), Color.parseColor("#FF7A45"), dusk)
        paint.alpha = 70
        canvas.drawCircle(cx, cy, r * 1.55f, paint)
        paint.color = mix(Color.parseColor("#FFD43B"), Color.parseColor("#FF7A45"), dusk)
        canvas.drawCircle(cx, cy, r, paint)
        // a face: round eyes by day, sleepy closed eyes near the end
        paint.color = Color.parseColor("#7A4B2A")
        if (dusk < 0.8f) {
            canvas.drawCircle(cx - r * 0.35f, cy - r * 0.1f, r * 0.09f, paint)
            canvas.drawCircle(cx + r * 0.35f, cy - r * 0.1f, r * 0.09f, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.08f
            paint.strokeCap = Paint.Cap.ROUND
            rect.set(cx - r * 0.5f, cy - r * 0.2f, cx - r * 0.2f, cy)
            canvas.drawArc(rect, 0f, 180f, false, paint)
            rect.set(cx + r * 0.2f, cy - r * 0.2f, cx + r * 0.5f, cy)
            canvas.drawArc(rect, 0f, 180f, false, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.strokeCap = Paint.Cap.ROUND
        rect.set(cx - r * 0.35f, cy + r * 0.02f, cx + r * 0.35f, cy + r * 0.5f)
        canvas.drawArc(rect, 20f, 140f, false, paint)
        paint.style = Paint.Style.FILL

        // ground and hills, over the lower part of the sun
        paint.color = ramp("#BFE8A8", "#A9D49A", "#6E9A7A", dusk)
        canvas.drawRect(0f, horizon, w, h, paint)
        paint.color = ramp("#7ED08A", "#6FB67A", "#4F8F6A", dusk)
        rect.set(-0.25f * w, horizon - 26f * d, 0.55f * w, horizon + 70f * d)
        canvas.drawOval(rect, paint)
        rect.set(0.3f * w, horizon - 38f * d, 1.3f * w, horizon + 80f * d)
        canvas.drawOval(rect, paint)
    }
}
