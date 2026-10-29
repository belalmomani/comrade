package com.cuteghost.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The cloud playground: the mother and the babies play on a swing, a slide and a star whirl, each
 * toy with its own movement, and change toys every 12 seconds. The little balloon starts the
 * ghosts' balloon game, which can be played once a day.
 */
class PlaygroundActivity : Activity() {

    private class Player(val view: GhostView, val idx: Int) {
        var toy = 0
        var prev = 0
        var trans = 1f
    }

    private lateinit var root: FrameLayout
    private var play: PlaygroundView? = null
    private val players = ArrayList<Player>()
    private var t = 0f
    private var lastTick = 0L
    private var running = false
    private var cycle = 0
    private var cycleAt = 0f
    private var inGame = false
    private var gameGhost: GhostView? = null
    private var balloonButton: Button? = null

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
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(Color.parseColor("#BFE3FF"))
        setContentView(root)
        showPlayground()
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
    }

    override fun onBackPressed() {
        if (inGame) showPlayground() else super.onBackPressed()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun pill(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(22).toFloat()
        return g
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

    private fun label(text: String, size: Float): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = size
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.background = pill(Color.parseColor("#997C5CFF"))
        tv.setPadding(dp(14), dp(8), dp(14), dp(8))
        return tv
    }

    private fun ghostWidth(): Int = dp(64)

    // ---------------------------------------------------------------- the playground

    private fun showPlayground() {
        inGame = false
        root.removeAllViews()
        players.clear()
        gameGhost = null
        val match = FrameLayout.LayoutParams.MATCH_PARENT
        val view = PlaygroundView(this)
        view.onBalloonTapped = { startBalloon() }
        root.addView(view, FrameLayout.LayoutParams(match, match))
        play = view

        // The mother and up to two babies play (each on a different toy)
        val babies = HomeStore.babyColorIndexes(this).take(2)
        val gw = ghostWidth()
        val gh = (gw * 1.28f).toInt()
        for (i in 0..babies.size) {
            val ghost = GhostView(this)
            if (i > 0) {
                ghost.setBodyColor(Family.color(babies[i - 1]))
                ghost.personality = Family.personalityFor(babies[i - 1])
            }
            ghost.playful = true
            ghost.setOnClickListener { ghost.showFed() }
            root.addView(ghost, FrameLayout.LayoutParams(gw, gh))
            val p = Player(ghost, i)
            p.toy = i % 3
            p.prev = p.toy
            players.add(p)
        }

        val bar = android.widget.LinearLayout(this)
        bar.orientation = android.widget.LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(12), dp(30), dp(12), dp(8))
        bar.addView(button("رجوع", Color.parseColor("#CC7C5CFF")) { finish() })
        val title = label("مدينة ألعاب سحابية", 16f)
        val tlp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        tlp.setMargins(dp(10), 0, 0, 0)
        bar.addView(title, tlp)
        root.addView(bar, FrameLayout.LayoutParams(match, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        val bottom = android.widget.LinearLayout(this)
        bottom.gravity = Gravity.CENTER
        bottom.setPadding(dp(16), dp(8), dp(16), dp(20))
        val b = button("", Color.parseColor("#E5469A")) { startBalloon() }
        balloonButton = b
        bottom.addView(b, android.widget.LinearLayout.LayoutParams(dp(260), dp(54)))
        root.addView(bottom, FrameLayout.LayoutParams(match, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        refreshBalloonButton()
    }

    private fun refreshBalloonButton() {
        val b = balloonButton ?: return
        if (HomeStore.canPlayBalloon(this)) {
            b.text = "منطاد الأشباح (مرة في اليوم)"
            b.background = pill(Color.parseColor("#E5469A"))
        } else {
            b.text = "منطاد الأشباح: عد غدًا"
            b.background = pill(Color.parseColor("#9A8FC0"))
        }
    }

    /** Where a ghost is (centre x, centre y, tilt in degrees) when it plays on [toy]. */
    private fun toyPos(toy: Int, idx: Int, w: Float, h: Float, gh: Float): FloatArray {
        when (toy) {
            0 -> { // the swing: seated, swinging back and forth
                val a = PlayGeo.swingAngle(t)
                val sx = PlayGeo.swingPivotX(w) + PlayGeo.swingLen(h) * sin(a)
                val sy = PlayGeo.swingPivotY(h) + PlayGeo.swingLen(h) * cos(a)
                return floatArrayOf(sx, sy - gh * 0.32f, a * 28.6f)
            }
            1 -> { // the slide: climbs, waits, slides down tilted, lands with a hop
                val tx = PlayGeo.slideTopX(w)
                val ty = PlayGeo.slideTopY(h)
                val bx = PlayGeo.slideBottomX(w)
                val by = PlayGeo.slideBottomY(h)
                val u = ((t + idx * 1.5f) % 4.5f) / 4.5f
                var p = 0f
                var lift = 0f
                var rot = 0f
                when {
                    u < 0.25f -> { val q = u / 0.25f; p = 1f - q; lift = abs(sin(q * 14f)) * 0.02f * h }
                    u < 0.40f -> { p = 0f; rot = sin(t * 8f) * 4f }
                    u < 0.80f -> { val q = (u - 0.4f) / 0.4f; p = q * q; rot = atan2(by - ty, bx - tx) * 57.3f }
                    else -> { val q = (u - 0.8f) / 0.2f; p = 1f; lift = sin(q * PI.toFloat()) * 0.05f * h }
                }
                return floatArrayOf(tx + (bx - tx) * p, ty + (by - ty) * p - gh * 0.38f - lift, rot)
            }
            else -> { // the star whirl: rides around the turning platform
                val a = t * 1.3f + idx * 2.1f
                val r = PlayGeo.whirlR(w) * 0.8f
                return floatArrayOf(
                    PlayGeo.whirlX(w) + cos(a) * r,
                    PlayGeo.whirlY(h) + sin(a) * 0.035f * h - gh * 0.32f,
                    sin(a) * 10f
                )
            }
        }
    }

    private fun update(dt: Float) {
        if (inGame) return
        t += dt
        val view = play ?: return
        view.time = t
        view.invalidate()
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        if (w == 0f || h == 0f) return
        if (t - cycleAt > 12f) {
            cycleAt = t
            cycle++
            for (p in players) {
                p.prev = p.toy
                p.toy = (p.idx + cycle) % 3
                p.trans = 0f
            }
        }
        val gw = ghostWidth().toFloat()
        val gh = gw * 1.28f
        for (p in players) {
            p.trans = min(1f, p.trans + dt / 0.8f)
            val to = toyPos(p.toy, p.idx, w, h, gh)
            var x = to[0]
            var y = to[1]
            var rot = to[2]
            if (p.trans < 1f) {
                val from = toyPos(p.prev, p.idx, w, h, gh)
                val e = p.trans * p.trans * (3f - 2f * p.trans)
                x = from[0] + (to[0] - from[0]) * e
                y = from[1] + (to[1] - from[1]) * e - sin(PI.toFloat() * p.trans) * 0.08f * h
                rot = from[2] + (to[2] - from[2]) * e
            }
            p.view.translationX = x - gw / 2f
            p.view.translationY = y - gh / 2f
            p.view.rotation = rot
        }
    }

    // ---------------------------------------------------------------- the balloon game

    private fun startBalloon() {
        if (inGame) return
        if (!HomeStore.canPlayBalloon(this)) {
            Toast.makeText(this, "منطاد الأشباح يطير مرة واحدة في اليوم. عد غدًا!", Toast.LENGTH_LONG).show()
            return
        }
        HomeStore.markBalloonPlayed(this)
        inGame = true
        root.removeAllViews()
        players.clear()
        val match = FrameLayout.LayoutParams.MATCH_PARENT
        val gw = dp(56)
        val gh = (gw * 1.28f).toInt()
        val game = BalloonGameView(this, object : BalloonGameView.Listener {
            override fun onFrame(basketX: Float, basketY: Float) {
                gameGhost?.let {
                    it.translationX = basketX - gw / 2f
                    it.translationY = basketY - gh * 0.9f
                }
            }

            override fun onFinished(collected: Int) {
                finishGame(collected)
            }
        })
        root.addView(game, FrameLayout.LayoutParams(match, match))
        val ghost = GhostView(this)
        ghost.playful = true
        ghost.showFed()
        root.addView(ghost, FrameLayout.LayoutParams(gw, gh))
        gameGhost = ghost
        val hint = label("حرّك إصبعك لتقود المنطاد واجمع النجوم", 15f)
        val lp = FrameLayout.LayoutParams(match, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        lp.setMargins(dp(14), dp(30), dp(14), 0)
        root.addView(hint, lp)
    }

    private fun finishGame(collected: Int) {
        gameGhost?.showFed()
        Wardrobe.addStars(this, collected)
        HomeStore.addMaterials(this, 1 + collected / 5)
        AlertDialog.Builder(this)
            .setTitle("انتهت الرحلة!")
            .setMessage("جمعتم $collected نجمة. المنطاد يطير مرة أخرى غدًا.")
            .setCancelable(false)
            .setPositiveButton("العودة إلى المدينة") { _, _ -> showPlayground() }
            .show()
    }
}
