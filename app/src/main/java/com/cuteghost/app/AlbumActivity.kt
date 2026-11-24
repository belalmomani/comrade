package com.cuteghost.app

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The sticker album, for a child who cannot read: five round tabs (animals, games, seasons, family, discoveries)
 * and a page of places. A sticker found is shown in colour, and a place still empty is a faint star outline.
 * It shows no numbers and no "missing" counts, so nothing in it is a duty.
 */
class AlbumActivity : Activity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.addView(
            AlbumView(this),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        val back = IconButtonView(this, IconButtonView.BACK, Color.parseColor("#9A8FC0"))
        back.setOnClickListener { finish() }
        val lp = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.START)
        lp.setMargins(dp(12), dp(36), dp(12), dp(12))
        root.addView(back, lp)
        setContentView(root)
    }
}

private class AlbumView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val start = SystemClock.uptimeMillis()
    private var category = Album.ANIMALS
    private val bounce = FloatArray(16)
    private var lastBounceTick = SystemClock.uptimeMillis()
    private var sky: Shader? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sky = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.parseColor("#BDE6FF"), Color.parseColor("#F5F3FF"), Shader.TileMode.CLAMP
        )
    }

    // ---------- Layout ----------

    private fun tabY(): Float = 118f * d
    private fun tabX(i: Int): Float = width * (2 * i + 1) / (2f * Album.CATEGORIES)
    private fun tabR(): Float = min(width / 12f, 28f * d)
    private fun cardLeft(): Float = 14f * d
    private fun cardRight(): Float = width - 14f * d
    private fun cardTop(): Float = tabY() + tabR() + 22f * d
    private fun cardBottom(): Float = height - 108f * d
    private fun pad(): Float = 12f * d

    private fun cols(n: Int): Int = if (n <= 6) 3 else 4
    private fun cellW(n: Int): Float = (cardRight() - cardLeft() - 2f * pad()) / cols(n)
    private fun rowsOf(n: Int): Int = (n + cols(n) - 1) / cols(n)
    private fun cellH(n: Int): Float = (cardBottom() - cardTop() - 2f * pad()) / rowsOf(n)
    private fun slotX(i: Int, n: Int): Float = cardLeft() + pad() + cellW(n) * (i % cols(n) + 0.5f)
    private fun slotY(i: Int, n: Int): Float = cardTop() + pad() + cellH(n) * (i / cols(n) + 0.5f)
    private fun slotR(n: Int): Float = min(cellW(n), cellH(n)) * 0.42f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        for (i in 0 until Album.CATEGORIES) {
            val dx = e.x - tabX(i)
            val dy = e.y - tabY()
            if (dx * dx + dy * dy < (tabR() * 1.3f) * (tabR() * 1.3f)) {
                if (category != i) {
                    category = i
                    for (k in bounce.indices) bounce[k] = 0f
                }
                invalidate()
                return true
            }
        }
        val list = Album.stickers(category)
        val n = list.size
        for (i in 0 until n) {
            if (abs(e.x - slotX(i, n)) < slotR(n) && abs(e.y - slotY(i, n)) < slotR(n)) {
                if (Album.has(context, list[i].id) && i < bounce.size) bounce[i] = 0.5f
                break
            }
        }
        return true
    }

    // ---------- Drawing ----------

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawCircle(x, y, r, paint)
    }

    private fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, rad, rad, paint)
    }

    private fun stroke(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color
        c.drawLine(x1, y1, x2, y2, paint)
        paint.style = Paint.Style.FILL
    }

    /** The picture of one sticker, in a circle of radius [r]. */
    private fun drawSticker(c: Canvas, id: String, cx: Float, cy: Float, r: Float, t: Float) {
        path.reset()
        paint.style = Paint.Style.FILL
        when (id) {
            "animal_cat" -> drawAnimal(c, paint, 0, cx, cy, r)
            "animal_duck" -> drawAnimal(c, paint, 1, cx, cy, r)
            "animal_cow" -> drawAnimal(c, paint, 2, cx, cy, r)
            "animal_dog" -> drawAnimal(c, paint, 3, cx, cy, r)
            "animal_sheep" -> drawAnimal(c, paint, 4, cx, cy, r)
            "game_bubbles" -> {
                circle(c, cx - 0.35f * r, cy + 0.25f * r, 0.5f * r, withAlpha(PALETTE[1], 150))
                circle(c, cx + 0.4f * r, cy - 0.1f * r, 0.4f * r, withAlpha(PALETTE[0], 150))
                circle(c, cx - 0.05f * r, cy - 0.5f * r, 0.3f * r, withAlpha(PALETTE[3], 150))
            }
            "game_ghost_bubbles" -> {
                drawCuteGhost(c, paint, cx - 0.3f * r, cy + 0.2f * r, 0.45f * r, t, 0f)
                circle(c, cx + 0.4f * r, cy - 0.3f * r, 0.4f * r, withAlpha(Color.parseColor("#FFC93C"), 200))
                drawShape(c, paint, 3, cx + 0.4f * r, cy - 0.3f * r, 0.2f * r, Color.parseColor("#FF8A2B"), false, d)
            }
            "game_shapes" -> {
                drawShape(c, paint, 0, cx - 0.4f * r, cy + 0.15f * r, 0.42f * r, PALETTE[0], false, d)
                drawShape(c, paint, 2, cx + 0.4f * r, cy - 0.1f * r, 0.45f * r, PALETTE[2], false, d)
            }
            "game_which" -> {
                drawShape(c, paint, 0, cx, cy, 0.5f * r, PALETTE[0], false, d)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * d
                paint.color = Color.parseColor("#FFC93C")
                c.drawCircle(cx, cy, 0.8f * r, paint)
                paint.style = Paint.Style.FILL
            }
            "game_colors" -> {
                circle(c, cx - 0.5f * r, cy, 0.3f * r, PALETTE[0])
                circle(c, cx, cy, 0.3f * r, PALETTE[1])
                circle(c, cx + 0.5f * r, cy, 0.3f * r, PALETTE[2])
                box(c, cx - 0.8f * r, cy + 0.45f * r, cx + 0.8f * r, cy + 0.65f * r, 0.08f * r, Color.parseColor("#B5803F"))
            }
            "game_twins" -> {
                drawBookItem(c, paint, 0, cx - 0.42f * r, cy, 0.4f * r)
                drawBookItem(c, paint, 0, cx + 0.42f * r, cy, 0.4f * r)
            }
            "game_butterfly" -> drawButterfly(c, paint, cx, cy, 0.7f * r, PALETTE[0], t * 5f)
            "game_ball" -> drawBall(c, paint, 1, cx, cy, 0.7f * r, 0, 0f)
            "game_band" -> {
                box(c, cx - 0.6f * r, cy, cx + 0.6f * r, cy + 0.6f * r, 0.1f * r, PALETTE[0])
                rect.set(cx - 0.6f * r, cy - 0.15f * r, cx + 0.6f * r, cy + 0.15f * r)
                paint.color = Color.parseColor("#FFF3D6")
                c.drawOval(rect, paint)
            }
            "game_animals" -> {
                paint.color = Color.parseColor("#7C5CFF")
                rect.set(cx - 0.7f * r, cy - 0.15f * r, cx - 0.4f * r, cy + 0.15f * r)
                c.drawRect(rect, paint)
                path.moveTo(cx - 0.4f * r, cy - 0.15f * r)
                path.lineTo(cx, cy - 0.5f * r)
                path.lineTo(cx, cy + 0.5f * r)
                path.lineTo(cx - 0.4f * r, cy + 0.15f * r)
                path.close()
                c.drawPath(path, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * d
                rect.set(cx + 0.1f * r, cy - 0.3f * r, cx + 0.55f * r, cy + 0.3f * r)
                c.drawArc(rect, -50f, 100f, false, paint)
                rect.set(cx + 0.3f * r, cy - 0.5f * r, cx + 0.85f * r, cy + 0.5f * r)
                c.drawArc(rect, -50f, 100f, false, paint)
                paint.style = Paint.Style.FILL
            }
            "game_counting" -> {
                box(c, cx - 0.8f * r, cy - 0.55f * r, cx - 0.02f * r, cy + 0.55f * r, 0.1f * r, Color.WHITE)
                box(c, cx + 0.02f * r, cy - 0.55f * r, cx + 0.8f * r, cy + 0.55f * r, 0.1f * r, Color.WHITE)
                for (k in 0 until 3) circle(c, cx - 0.55f * r + k * 0.28f * r, cy, 0.09f * r, PALETTE[k])
                stroke(c, cx, cy - 0.55f * r, cx, cy + 0.55f * r, 2f * d, Color.parseColor("#B8B0D8"))
            }
            "game_doctor" -> {
                circle(c, cx, cy, 0.8f * r, Color.WHITE)
                box(c, cx - 0.12f * r, cy - 0.5f * r, cx + 0.12f * r, cy + 0.5f * r, 0.04f * r, Color.parseColor("#E5484D"))
                box(c, cx - 0.5f * r, cy - 0.12f * r, cx + 0.5f * r, cy + 0.12f * r, 0.04f * r, Color.parseColor("#E5484D"))
            }
            "game_class" -> {
                box(c, cx - 0.85f * r, cy - 0.6f * r, cx + 0.85f * r, cy + 0.6f * r, 0.12f * r, Color.parseColor("#9A6B3F"))
                box(c, cx - 0.74f * r, cy - 0.48f * r, cx + 0.74f * r, cy + 0.48f * r, 0.08f * r, Color.parseColor("#2F6B55"))
                drawShape(c, paint, 3, cx, cy, 0.3f * r, Color.WHITE, false, d)
            }
            "season_winter" -> {
                circle(c, cx, cy, 0.85f * r, Color.parseColor("#D9F0FF"))
                for (k in 0 until 3) {
                    val a = k * 1.0472f
                    stroke(c, cx - cos(a) * 0.6f * r, cy - sin(a) * 0.6f * r, cx + cos(a) * 0.6f * r, cy + sin(a) * 0.6f * r, 3f * d, Color.parseColor("#4DA3FF"))
                }
            }
            "season_spring" -> drawBookItem(c, paint, 2, cx, cy, 0.85f * r)
            "season_summer" -> {
                for (k in 0 until 8) {
                    val a = k * 0.7854f
                    stroke(c, cx + cos(a) * 0.62f * r, cy + sin(a) * 0.62f * r, cx + cos(a) * 0.9f * r, cy + sin(a) * 0.9f * r, 3f * d, Color.parseColor("#FFB000"))
                }
                circle(c, cx, cy, 0.5f * r, Color.parseColor("#FFD43B"))
            }
            "season_autumn" -> {
                c.save()
                c.rotate(-30f, cx, cy)
                rect.set(cx - 0.35f * r, cy - 0.7f * r, cx + 0.35f * r, cy + 0.5f * r)
                paint.color = Color.parseColor("#E8731A")
                c.drawOval(rect, paint)
                stroke(c, cx, cy - 0.5f * r, cx, cy + 0.85f * r, 3f * d, Color.parseColor("#8A5A2B"))
                c.restore()
            }
            "season_rain" -> {
                circle(c, cx - 0.35f * r, cy - 0.25f * r, 0.35f * r, Color.parseColor("#9AA8BA"))
                circle(c, cx + 0.1f * r, cy - 0.4f * r, 0.42f * r, Color.parseColor("#9AA8BA"))
                circle(c, cx + 0.5f * r, cy - 0.2f * r, 0.3f * r, Color.parseColor("#9AA8BA"))
                for (k in 0 until 3) {
                    stroke(c, cx - 0.4f * r + k * 0.4f * r, cy + 0.2f * r, cx - 0.5f * r + k * 0.4f * r, cy + 0.55f * r, 3f * d, Color.parseColor("#4DA3FF"))
                }
            }
            "family_first_egg" -> {
                rect.set(cx - 0.5f * r, cy - 0.7f * r, cx + 0.5f * r, cy + 0.7f * r)
                paint.color = Color.parseColor("#FFF3D6")
                c.drawOval(rect, paint)
                circle(c, cx - 0.15f * r, cy - 0.15f * r, 0.12f * r, PALETTE[1])
                circle(c, cx + 0.2f * r, cy + 0.15f * r, 0.12f * r, PALETTE[0])
                circle(c, cx - 0.1f * r, cy + 0.35f * r, 0.1f * r, PALETTE[3])
            }
            "family_first_twins" -> {
                drawCuteGhost(c, paint, cx - 0.45f * r, cy + 0.1f * r, 0.4f * r, t, 0f)
                drawCuteGhost(c, paint, cx + 0.45f * r, cy + 0.1f * r, 0.4f * r, t + 1f, 0f)
            }
            "family_first_grown" -> {
                drawCuteGhost(c, paint, cx - 0.1f * r, cy + 0.05f * r, 0.55f * r, t, 0f)
                drawShape(c, paint, 3, cx + 0.6f * r, cy - 0.55f * r, 0.25f * r, Color.parseColor("#FFC93C"), false, d)
            }
            "treasure_chest" -> {
                box(c, cx - 0.65f * r, cy - 0.3f * r, cx + 0.65f * r, cy + 0.5f * r, 0.12f * r, Color.parseColor("#B5703A"))
                box(c, cx - 0.65f * r, cy - 0.3f * r, cx + 0.65f * r, cy, 0.12f * r, Color.parseColor("#D88C4A"))
                box(c, cx - 0.1f * r, cy - 0.1f * r, cx + 0.1f * r, cy + 0.2f * r, 0.04f * r, Color.parseColor("#FFD43B"))
            }
            "treasure_gem" -> {
                path.moveTo(cx - 0.6f * r, cy - 0.15f * r)
                path.lineTo(cx - 0.3f * r, cy - 0.55f * r)
                path.lineTo(cx + 0.3f * r, cy - 0.55f * r)
                path.lineTo(cx + 0.6f * r, cy - 0.15f * r)
                path.lineTo(cx, cy + 0.6f * r)
                path.close()
                paint.color = PALETTE[1]
                c.drawPath(path, paint)
                circle(c, cx - 0.2f * r, cy - 0.3f * r, 0.1f * r, Color.argb(160, 255, 255, 255))
            }
            "treasure_star" -> drawShape(c, paint, 3, cx, cy, 0.75f * r, Color.parseColor("#FFC93C"), false, d)
            "discover_golden_bubble" -> {
                circle(c, cx, cy, 0.75f * r, withAlpha(Color.parseColor("#FFC93C"), 200))
                drawShape(c, paint, 3, cx, cy, 0.4f * r, Color.parseColor("#FF8A2B"), false, d)
            }
            "discover_all_toys" -> {
                circle(c, cx, cy + 0.1f * r, 0.5f * r, Color.parseColor("#B5703A"))
                circle(c, cx - 0.4f * r, cy - 0.35f * r, 0.2f * r, Color.parseColor("#B5703A"))
                circle(c, cx + 0.4f * r, cy - 0.35f * r, 0.2f * r, Color.parseColor("#B5703A"))
                circle(c, cx, cy + 0.2f * r, 0.2f * r, Color.parseColor("#F1D3B0"))
                circle(c, cx - 0.15f * r, cy - 0.05f * r, 0.05f * r, Color.parseColor("#2B2540"))
                circle(c, cx + 0.15f * r, cy - 0.05f * r, 0.05f * r, Color.parseColor("#2B2540"))
            }
            "civic_park" -> {
                box(c, cx - 0.07f * r, cy, cx + 0.07f * r, cy + 0.7f * r, 0.03f * r, Color.parseColor("#8A5A2B"))
                circle(c, cx, cy - 0.25f * r, 0.5f * r, Color.parseColor("#3E9B4F"))
                circle(c, cx - 0.35f * r, cy - 0.05f * r, 0.3f * r, Color.parseColor("#3E9B4F"))
                circle(c, cx + 0.35f * r, cy - 0.05f * r, 0.3f * r, Color.parseColor("#3E9B4F"))
            }
            "civic_toys" -> {
                box(c, cx - 0.6f * r, cy - 0.1f * r, cx + 0.6f * r, cy + 0.6f * r, 0.08f * r, Color.parseColor("#FF9EC4"))
                for (k in 0 until 4) {
                    box(c, cx - 0.6f * r + k * 0.3f * r, cy - 0.55f * r, cx - 0.3f * r + k * 0.3f * r, cy - 0.1f * r, 0f, if (k % 2 == 0) PALETTE[0] else Color.WHITE)
                }
                box(c, cx - 0.12f * r, cy + 0.15f * r, cx + 0.12f * r, cy + 0.6f * r, 0.04f * r, Color.parseColor("#8A5A2B"))
            }
            "civic_school" -> {
                box(c, cx - 0.65f * r, cy - 0.1f * r, cx + 0.65f * r, cy + 0.65f * r, 0.06f * r, Color.parseColor("#FFD66B"))
                path.moveTo(cx - 0.75f * r, cy - 0.1f * r)
                path.lineTo(cx, cy - 0.65f * r)
                path.lineTo(cx + 0.75f * r, cy - 0.1f * r)
                path.close()
                paint.color = Color.parseColor("#E5584A")
                c.drawPath(path, paint)
                box(c, cx - 0.12f * r, cy + 0.2f * r, cx + 0.12f * r, cy + 0.65f * r, 0.03f * r, Color.parseColor("#8A5A2B"))
            }
            "civic_library" -> {
                box(c, cx - 0.8f * r, cy - 0.5f * r, cx - 0.02f * r, cy + 0.5f * r, 0.08f * r, Color.parseColor("#8E7CF0"))
                box(c, cx + 0.02f * r, cy - 0.5f * r, cx + 0.8f * r, cy + 0.5f * r, 0.08f * r, Color.parseColor("#8E7CF0"))
                for (k in 0 until 3) {
                    box(c, cx - 0.6f * r, cy - 0.3f * r + k * 0.28f * r, cx - 0.2f * r, cy - 0.2f * r + k * 0.28f * r, 0.02f * r, Color.WHITE)
                    box(c, cx + 0.2f * r, cy - 0.3f * r + k * 0.28f * r, cx + 0.6f * r, cy - 0.2f * r + k * 0.28f * r, 0.02f * r, Color.WHITE)
                }
            }
            "civic_hospital" -> {
                box(c, cx - 0.65f * r, cy - 0.4f * r, cx + 0.65f * r, cy + 0.65f * r, 0.06f * r, Color.WHITE)
                box(c, cx - 0.1f * r, cy - 0.3f * r, cx + 0.1f * r, cy + 0.1f * r, 0.03f * r, Color.parseColor("#E5484D"))
                box(c, cx - 0.3f * r, cy - 0.15f * r, cx + 0.3f * r, cy + 0.05f * r, 0.03f * r, Color.parseColor("#E5484D"))
                box(c, cx - 0.12f * r, cy + 0.3f * r, cx + 0.12f * r, cy + 0.65f * r, 0.03f * r, Color.parseColor("#BFE8FF"))
            }
            else -> drawShape(c, paint, 3, cx, cy, 0.7f * r, PALETTE[2], false, d)
        }
    }

    /** The picture on a round tab: a cat, a ball, the sun, an egg, a star. */
    private fun drawTab(c: Canvas, i: Int, x: Float, y: Float, r: Float, t: Float) {
        when (i) {
            Album.ANIMALS -> drawAnimal(c, paint, 0, x, y, r * 0.62f)
            Album.GAMES -> drawBall(c, paint, 1, x, y, r * 0.62f, 0, 0f)
            Album.SEASONS -> drawSticker(c, "season_summer", x, y, r * 0.8f, t)
            Album.FAMILY -> drawSticker(c, "family_first_egg", x, y, r * 0.85f, t)
            else -> drawShape(c, paint, 3, x, y, r * 0.6f, Color.parseColor("#FFC93C"), false, d)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val now = SystemClock.uptimeMillis()
        val t = (now - start) / 1000f
        val dt = ((now - lastBounceTick) / 1000f).coerceIn(0f, 0.1f)
        lastBounceTick = now
        for (k in bounce.indices) if (bounce[k] > 0f) bounce[k] = max(0f, bounce[k] - dt)

        paint.style = Paint.Style.FILL
        paint.shader = sky
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // the five tabs
        for (i in 0 until Album.CATEGORIES) {
            val selected = i == category
            val r = tabR() * (if (selected) 1.15f else 1f)
            circle(canvas, tabX(i) + 2f * d, tabY() + 4f * d, r, Color.argb(34, 0, 0, 0))
            circle(canvas, tabX(i), tabY(), r, if (selected) Color.WHITE else Color.parseColor("#E7E1F7"))
            if (selected) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * d
                paint.color = Color.parseColor("#FFC93C")
                canvas.drawCircle(tabX(i), tabY(), r, paint)
                paint.style = Paint.Style.FILL
            }
            drawTab(canvas, i, tabX(i), tabY(), r, t)
        }

        // the page
        box(canvas, cardLeft() + 3f * d, cardTop() + 6f * d, cardRight() + 3f * d, cardBottom() + 6f * d, 26f * d, Color.argb(34, 0, 0, 0))
        box(canvas, cardLeft(), cardTop(), cardRight(), cardBottom(), 26f * d, Color.parseColor("#FFFDF7"))

        val list = Album.stickers(category)
        val n = list.size
        var all = true
        for (i in 0 until n) {
            val s = list[i]
            val x = slotX(i, n)
            val y = slotY(i, n)
            val r = slotR(n)
            if (Album.has(context, s.id)) {
                val pop = if (i < bounce.size) 1f + 0.18f * sin(bounce[i] / 0.5f * 3.1416f) else 1f
                val tilt = ((s.id.hashCode() and 7) - 3).toFloat()
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(tilt)
                canvas.scale(pop, pop)
                box(canvas, -r + 2f * d, -r + 4f * d, r + 2f * d, r + 4f * d, 0.3f * r, Color.argb(40, 0, 0, 0))
                box(canvas, -r, -r, r, r, 0.3f * r, Color.WHITE)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * d
                paint.color = Color.parseColor("#EFE9FF")
                rect.set(-r, -r, r, r)
                canvas.drawRoundRect(rect, 0.3f * r, 0.3f * r, paint)
                paint.style = Paint.Style.FILL
                drawSticker(canvas, s.id, 0f, 0f, r * 0.72f, t)
                canvas.restore()
            } else {
                all = false
                box(canvas, x - r, y - r, x + r, y + r, 0.3f * r, Color.parseColor("#F1EEFA"))
                drawShape(canvas, paint, 3, x, y, r * 0.42f, Color.parseColor("#CFC8E6"), true, d)
            }
        }

        // the ghost watches over the album, and jumps for joy when a page is full
        drawCuteGhost(canvas, paint, w / 2f, h - 70f * d, 32f * d, t, if (all && n > 0) 1f else 0f)
        postInvalidateDelayed(50L)
    }
}
