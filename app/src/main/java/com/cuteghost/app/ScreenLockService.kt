package com.cuteghost.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Rest time: covers the whole screen with a sleeping ghost for the minutes a parent chose.
 * The only way out before the time is over is the "for grown-ups" button, which asks a
 * multiplication question with its own number keys (no keyboard needed).
 */
class ScreenLockService : Service() {

    companion object {
        private const val TAG = "ScreenLockService"
        private const val CHANNEL_ID = "ghost_rest"
        private const val NOTIFICATION_ID = 3
        const val ACTION_LOCK = "com.companion.theghost.REST_START"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var root: FrameLayout? = null
    private var gate: View? = null
    private var leftText: TextView? = null
    private var questionText: TextView? = null
    private var answerText: TextView? = null
    private var answer = ""
    private var expected = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!ScreenLock.isActive(this@ScreenLockService)) {
                finishLock()
                return
            }
            leftText?.text = "باقي ${ScreenLock.minutesLeft(this@ScreenLockService)} دقيقة"
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ScreenLock.isActive(this) || !Settings.canDrawOverlays(this)) {
            finishLock()
            return START_NOT_STICKY
        }
        val first = root == null
        if (!showOverlay()) {
            finishLock()
            return START_NOT_STICKY
        }
        if (first && intent?.action == ACTION_LOCK && ScreenLock.systemLockWanted(this)) {
            LockAdmin.lockNow(this)
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    /** Ends the rest time: the cover goes away and the saved end time is cleared. */
    private fun finishLock() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        ScreenLock.stop(this)
        stopSelf()
    }

    // ---------- Foreground notification ----------

    private fun enterForeground(): Boolean {
        return try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not start foreground service", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "وقت الراحة", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_stat_ghost)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("الهاتف في وقت الراحة")
            .setOngoing(true)
            .build()
    }

    // ---------- The cover ----------

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float, color: String, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(Color.parseColor(color))
        t.gravity = Gravity.CENTER
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD)
        return t
    }

    /** A rounded touch target made from a TextView, so that it does not depend on any theme. */
    private fun pill(text: String, bg: String, fg: String, size: Float): TextView {
        val t = label(text, size, fg, true)
        val g = GradientDrawable()
        g.setColor(Color.parseColor(bg))
        g.cornerRadius = dp(18).toFloat()
        t.background = g
        t.setPadding(dp(18), dp(12), dp(18), dp(12))
        return t
    }

    private fun wrapParams(topMarginDp: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.CENTER_HORIZONTAL
        lp.topMargin = dp(topMarginDp)
        return lp
    }

    @Suppress("DEPRECATION")
    private fun showOverlay(): Boolean {
        if (root != null) return true
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val view = buildRoot()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        view.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        return try {
            wm.addView(view, p)
            root = view
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not show the rest screen", e)
            false
        }
    }

    private fun removeOverlay() {
        val v = root
        root = null
        gate = null
        leftText = null
        questionText = null
        answerText = null
        if (v != null) {
            try {
                windowManager?.removeView(v)
            } catch (e: RuntimeException) {
                // already gone
            }
        }
    }

    private fun buildRoot(): FrameLayout {
        val frame = FrameLayout(this)
        frame.setBackgroundColor(Color.parseColor("#1E1B3A"))
        frame.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL
        column.setPadding(dp(24), dp(48), dp(24), dp(40))

        column.addView(
            LockArtView(this),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        column.addView(label("الهاتف يستريح الآن", 26f, "#FFFFFF", true), wrapParams(8))
        val left = label("", 18f, "#C9C2F0", false)
        leftText = left
        column.addView(left, wrapParams(8))
        val parent = pill("للأهل", "#33FFFFFF", "#FFFFFF", 16f)
        parent.setOnClickListener { openGate() }
        column.addView(parent, wrapParams(28))
        frame.addView(
            column,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val g = buildGate()
        g.visibility = View.GONE
        gate = g
        frame.addView(
            g,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        return frame
    }

    private fun buildGate(): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setBackgroundColor(Color.parseColor("#F5F3FF"))
        box.setPadding(dp(24), dp(24), dp(24), dp(24))
        box.isClickable = true

        val q = label("", 22f, "#2B2540", true)
        questionText = q
        box.addView(q, wrapParams(0))
        val a = label(" ", 34f, "#7C5CFF", true)
        answerText = a
        box.addView(a, wrapParams(12))

        val keys = arrayOf(
            arrayOf("1", "2", "3"),
            arrayOf("4", "5", "6"),
            arrayOf("7", "8", "9"),
            arrayOf("⌫", "0", "✓")
        )
        for (row in keys) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER
            for (k in row) {
                val key = pill(k, "#E3DCFF", "#2B2540", 26f)
                key.setOnClickListener { onKey(k) }
                val lp = LinearLayout.LayoutParams(dp(76), dp(64))
                lp.setMargins(dp(6), dp(6), dp(6), dp(6))
                line.addView(key, lp)
            }
            box.addView(line, wrapParams(0))
        }
        val back = pill("رجوع", "#D9D3F5", "#2B2540", 16f)
        back.setOnClickListener { closeGate() }
        box.addView(back, wrapParams(20))
        return box
    }

    private fun newQuestion(wrong: Boolean) {
        val a = 12 + Random.nextInt(8)
        val b = 6 + Random.nextInt(4)
        expected = a * b
        answer = ""
        val head = if (wrong) "إجابة غير صحيحة. سؤال جديد:" else "للأهل فقط"
        questionText?.text = head + "\nكم يساوي $a × $b ؟"
        answerText?.text = " "
    }

    private fun openGate() {
        newQuestion(false)
        gate?.visibility = View.VISIBLE
    }

    private fun closeGate() {
        answer = ""
        gate?.visibility = View.GONE
    }

    private fun onKey(k: String) {
        if (k == "✓") {
            if (answer == expected.toString()) {
                finishLock()
            } else {
                newQuestion(true)
            }
            return
        }
        if (k == "⌫") {
            if (answer.isNotEmpty()) answer = answer.substring(0, answer.length - 1)
        } else if (answer.length < 3) {
            answer += k
        }
        answerText?.text = if (answer.isEmpty()) " " else answer
    }
}

/** The picture on the rest screen: twinkling stars, a moon and a sleeping ghost with floating Z letters. */
private class LockArtView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = Path()
    private val rect = RectF()
    private val stars = Array(30) {
        floatArrayOf(Random.nextFloat(), Random.nextFloat(), 1.5f + Random.nextFloat() * 2.5f, Random.nextFloat() * 6.28f)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = SystemClock.uptimeMillis() / 1000f
        val d = resources.displayMetrics.density

        paint.style = Paint.Style.FILL
        for (s in stars) {
            paint.color = Color.WHITE
            paint.alpha = (150 + 100 * sin(t * 1.4f + s[3])).toInt().coerceIn(30, 255)
            canvas.drawCircle(s[0] * w, s[1] * h * 0.55f, s[2] * d, paint)
        }

        // the moon
        val mr = min(w, h) * 0.12f
        val mx = w * 0.8f
        val my = h * 0.16f
        paint.color = Color.parseColor("#FFF3B0")
        canvas.drawCircle(mx, my, mr, paint)
        paint.color = Color.parseColor("#1E1B3A")
        canvas.drawCircle(mx + mr * 0.45f, my - mr * 0.2f, mr * 0.85f, paint)

        // the sleeping ghost
        val r = min(w, h) * 0.2f
        val cx = w / 2f
        val cy = h * 0.5f + sin(t * 1.6f) * r * 0.06f
        val bottom = cy + r * 1.15f
        paint.color = Color.parseColor("#33B9A8FF")
        canvas.drawCircle(cx, cy + r * 0.3f, r * 1.7f, paint)

        body.reset()
        body.moveTo(cx - r, cy)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        body.arcTo(rect, 180f, 180f)
        body.lineTo(cx + r, bottom)
        for (i in 0 until 3) {
            val x1 = cx + r - (2f * i + 1f) * r / 3f
            val x2 = cx + r - (2f * i + 2f) * r / 3f
            val wave = if (i % 2 == 0) r * 0.3f else -r * 0.3f
            body.quadTo(x1, bottom + wave, x2, bottom)
        }
        body.close()
        paint.color = Color.parseColor("#F3F0FF")
        canvas.drawPath(body, paint)

        // closed eyes
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.07f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.parseColor("#2B2540")
        rect.set(cx - r * 0.55f, cy - r * 0.1f, cx - r * 0.15f, cy + r * 0.2f)
        canvas.drawArc(rect, 0f, 180f, false, paint)
        rect.set(cx + r * 0.15f, cy - r * 0.1f, cx + r * 0.55f, cy + r * 0.2f)
        canvas.drawArc(rect, 0f, 180f, false, paint)

        // cheeks and a tiny mouth
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FFB6D5")
        canvas.drawCircle(cx - r * 0.6f, cy + r * 0.38f, r * 0.12f, paint)
        canvas.drawCircle(cx + r * 0.6f, cy + r * 0.38f, r * 0.12f, paint)
        paint.color = Color.parseColor("#2B2540")
        canvas.drawCircle(cx, cy + r * 0.42f, r * 0.06f, paint)

        // floating Z letters
        paint.typeface = Typeface.DEFAULT_BOLD
        for (i in 0 until 3) {
            val phase = (t * 0.6f + i * 0.33f) % 1f
            paint.color = Color.WHITE
            paint.alpha = (255 * (1f - phase)).toInt().coerceIn(0, 255)
            paint.textSize = r * (0.25f + 0.12f * i)
            canvas.drawText("Z", cx + r * 0.95f + i * r * 0.22f, cy - r * 0.6f - phase * r * 0.8f, paint)
        }
        postInvalidateDelayed(60L)
    }
}
