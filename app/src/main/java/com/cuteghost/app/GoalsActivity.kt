package com.cuteghost.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Today's goals as pictures only, for a child who cannot read yet. */
class GoalsActivity : Activity() {

    private lateinit var goalsView: GoalsView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        goalsView = GoalsView(this)
        root.addView(
            goalsView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        val back = IconButtonView(this, IconButtonView.BACK, Color.parseColor("#9A8FC0"))
        back.setOnClickListener { finish() }
        val lp = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.START)
        lp.setMargins(dp(12), dp(36), dp(12), dp(12))
        root.addView(back, lp)
        // the sticker album
        val album = IconButtonView(this, IconButtonView.ALBUM, Color.parseColor("#7C5CFF"))
        album.setOnClickListener { startActivity(Intent(this, AlbumActivity::class.java)) }
        val alp = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.END)
        alp.setMargins(dp(12), dp(36), dp(12), dp(12))
        root.addView(album, alp)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        goalsView.refresh()
    }
}

private class GoalsView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private var sky: Shader? = null

    private val goals = DailyGoals.Goal.values()
    private var done = BooleanArray(goals.size)
    private var doneCount = 0
    private var streak = 0
    private var stickers = 0
    private var enabled = true

    /** 1 for a finished goal, a pale version for one that is still to do. */
    private var mul = 1f

    fun refresh() {
        enabled = DailyGoals.enabled(context)
        for (i in goals.indices) done[i] = DailyGoals.isDone(context, goals[i])
        doneCount = DailyGoals.doneCount(context)
        streak = DailyGoals.streak(context)
        stickers = DailyGoals.stickers(context)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sky = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.parseColor("#BDE6FF"), Color.parseColor("#F5F3FF"), Shader.TileMode.CLAMP
        )
    }

    private fun tint(hex: String): Int {
        val c = Color.parseColor(hex)
        return Color.argb((Color.alpha(c) * mul).toInt(), Color.red(c), Color.green(c), Color.blue(c))
    }

    private fun fill(hex: String) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = tint(hex)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        val t = SystemClock.uptimeMillis() / 1000f

        mul = 1f
        paint.style = Paint.Style.FILL
        paint.shader = sky
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val top = 100f * d
        val bottom = 76f * d
        val gap = 14f * d
        val cw = (w - gap * 3f) / 2f
        val ch = min(cw * 1.05f, (h - top - bottom - gap * 4f) / 3f)

        for (i in 0 until 6) {
            val x = gap + (i % 2) * (cw + gap)
            val y = top + gap + (i / 2) * (ch + gap)
            val cx = x + cw / 2f
            val cy = y + ch / 2f
            val isGoal = i < goals.size
            val finished = isGoal && done[i] && enabled

            // the card
            mul = 1f
            fill(if (finished) "#FFFFFF" else "#F4F1FF")
            rect.set(x, y, x + cw, y + ch)
            canvas.drawRoundRect(rect, 26f * d, 26f * d, paint)
            if (finished) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f * d
                paint.color = Color.parseColor("#4CC38A")
                canvas.drawRoundRect(rect, 26f * d, 26f * d, paint)
            }

            val s = min(cw, ch) * 0.32f
            if (isGoal) {
                mul = if (finished) 1f else 0.28f
                val bob = if (finished) sin(t * 3f + i) * s * 0.05f else 0f
                drawIcon(canvas, i, cx, cy + bob, s)
                if (finished) drawCheck(canvas, x + cw - 22f * d, y + 22f * d, 15f * d)
            } else {
                drawProgress(canvas, cx, cy, s)
            }
        }

        // finished days in a row, as golden stars
        mul = 1f
        val n = min(streak, 7)
        val sr = 15f * d
        val startX = w / 2f - (n - 1) * sr * 1.1f
        for (k in 0 until n) drawStar(canvas, startX + k * sr * 2.2f, h - bottom + 26f * d, sr, "#FFC83D")

        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.parseColor("#6B6590")
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 14f * d
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("الملصقات: $stickers", w / 2f, h - 14f * d, paint)
        if (!enabled) {
            canvas.drawText("الأهداف متوقفة من لوحة الأهل", w / 2f, top - 10f * d, paint)
        }
        postInvalidateDelayed(60L)
    }

    private fun drawCheck(c: Canvas, cx: Float, cy: Float, r: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#4CC38A")
        c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.28f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.WHITE
        path.reset()
        path.moveTo(cx - r * 0.45f, cy)
        path.lineTo(cx - r * 0.1f, cy + r * 0.38f)
        path.lineTo(cx + r * 0.5f, cy - r * 0.35f)
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawStar(c: Canvas, cx: Float, cy: Float, r: Float, hex: String) {
        path.reset()
        for (k in 0 until 10) {
            val a = -Math.PI / 2 + k * Math.PI / 5
            val rad = if (k % 2 == 0) r else r * 0.45f
            val px = cx + cos(a).toFloat() * rad
            val py = cy + sin(a).toFloat() * rad
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill(hex)
        c.drawPath(path, paint)
    }

    /** The sixth card: a ring that fills as the goals get done, with a star that turns gold at the end. */
    private fun drawProgress(c: Canvas, cx: Float, cy: Float, s: Float) {
        val d = resources.displayMetrics.density
        mul = 1f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 11f * d
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.parseColor("#E1DBF5")
        rect.set(cx - s * 1.1f, cy - s * 1.1f, cx + s * 1.1f, cy + s * 1.1f)
        c.drawArc(rect, 0f, 360f, false, paint)
        if (enabled && doneCount > 0) {
            paint.color = Color.parseColor("#FFC83D")
            c.drawArc(rect, -90f, 360f * doneCount / goals.size, false, paint)
        }
        val all = enabled && doneCount == goals.size
        mul = if (all) 1f else 0.35f
        drawStar(c, cx, cy + s * 0.05f, s * 0.8f, "#FFC83D")
    }

    private fun drawIcon(c: Canvas, index: Int, cx: Float, cy: Float, s: Float) {
        when (goals[index]) {
            DailyGoals.Goal.FOOD -> {
                // an apple
                fill("#E5484D")
                c.drawCircle(cx - s * 0.22f, cy + s * 0.12f, s * 0.62f, paint)
                c.drawCircle(cx + s * 0.22f, cy + s * 0.12f, s * 0.62f, paint)
                fill("#7A4B2A")
                rect.set(cx - s * 0.05f, cy - s * 0.85f, cx + s * 0.05f, cy - s * 0.4f)
                c.drawRoundRect(rect, s * 0.05f, s * 0.05f, paint)
                fill("#4CC38A")
                rect.set(cx + s * 0.05f, cy - s * 0.9f, cx + s * 0.6f, cy - s * 0.5f)
                c.drawOval(rect, paint)
                fill("#FFFFFF")
                c.drawCircle(cx - s * 0.45f, cy - s * 0.05f, s * 0.1f, paint)
            }
            DailyGoals.Goal.WATER -> {
                // a water drop
                path.reset()
                path.moveTo(cx, cy - s * 0.95f)
                path.cubicTo(cx + s * 0.95f, cy - s * 0.05f, cx + s * 0.75f, cy + s * 0.85f, cx, cy + s * 0.85f)
                path.cubicTo(cx - s * 0.75f, cy + s * 0.85f, cx - s * 0.95f, cy - s * 0.05f, cx, cy - s * 0.95f)
                path.close()
                fill("#3DA5F5")
                c.drawPath(path, paint)
                fill("#FFFFFF")
                c.drawCircle(cx - s * 0.3f, cy + s * 0.2f, s * 0.13f, paint)
            }
            DailyGoals.Goal.BATH -> {
                // soap bubbles
                paint.style = Paint.Style.FILL
                paint.color = tint("#CFEFFF")
                c.drawCircle(cx - s * 0.15f, cy + s * 0.1f, s * 0.7f, paint)
                c.drawCircle(cx + s * 0.6f, cy - s * 0.5f, s * 0.32f, paint)
                c.drawCircle(cx - s * 0.65f, cy - s * 0.6f, s * 0.24f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.07f
                paint.color = tint("#3DA5F5")
                c.drawCircle(cx - s * 0.15f, cy + s * 0.1f, s * 0.7f, paint)
                c.drawCircle(cx + s * 0.6f, cy - s * 0.5f, s * 0.32f, paint)
                c.drawCircle(cx - s * 0.65f, cy - s * 0.6f, s * 0.24f, paint)
                fill("#FFFFFF")
                c.drawCircle(cx - s * 0.42f, cy - s * 0.15f, s * 0.14f, paint)
            }
            DailyGoals.Goal.BRUSH -> {
                // a toothbrush
                c.save()
                c.rotate(-35f, cx, cy)
                fill("#2EC4B6")
                rect.set(cx - s * 0.95f, cy - s * 0.13f, cx + s * 0.25f, cy + s * 0.13f)
                c.drawRoundRect(rect, s * 0.13f, s * 0.13f, paint)
                fill("#FFFFFF")
                rect.set(cx + s * 0.2f, cy - s * 0.2f, cx + s * 0.95f, cy + s * 0.2f)
                c.drawRoundRect(rect, s * 0.1f, s * 0.1f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.08f
                paint.color = tint("#3DA5F5")
                c.drawRoundRect(rect, s * 0.1f, s * 0.1f, paint)
                paint.strokeCap = Paint.Cap.ROUND
                for (k in 0 until 4) {
                    val bx = cx + s * (0.32f + k * 0.17f)
                    c.drawLine(bx, cy - s * 0.2f, bx, cy - s * 0.42f, paint)
                }
                c.restore()
            }
            DailyGoals.Goal.FARM -> {
                // a carrot
                path.reset()
                path.moveTo(cx - s * 0.42f, cy - s * 0.45f)
                path.lineTo(cx + s * 0.42f, cy - s * 0.45f)
                path.lineTo(cx, cy + s * 0.95f)
                path.close()
                fill("#FF8A2B")
                c.drawPath(path, paint)
                fill("#4CC38A")
                for (k in -1..1) {
                    rect.set(cx + k * s * 0.3f - s * 0.12f, cy - s * 0.98f, cx + k * s * 0.3f + s * 0.12f, cy - s * 0.4f)
                    c.drawOval(rect, paint)
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.06f
                paint.color = tint("#D96A12")
                c.drawLine(cx - s * 0.2f, cy - s * 0.1f, cx + s * 0.12f, cy - s * 0.1f, paint)
                c.drawLine(cx - s * 0.1f, cy + s * 0.3f, cx + s * 0.1f, cy + s * 0.3f, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }
}
