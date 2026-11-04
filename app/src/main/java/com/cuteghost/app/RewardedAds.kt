package com.cuteghost.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.UUID
import java.util.TimeZone
import kotlin.random.Random

/** Something that can show an optional rewarded ad. A real one is plugged in at [RewardedAds.provider]. */
interface AdProvider {
    val name: String
    fun isReady(): Boolean

    /** Shows the ad and reports whether it was watched to the end (only then is the reward given). */
    fun show(activity: Activity, onResult: (Boolean) -> Unit)
}

/** Used until a real provider is set up: it shows nothing and says that no ad is available. */
object NoAdProvider : AdProvider {
    override val name = "none"
    override fun isReady() = false
    override fun show(activity: Activity, onResult: (Boolean) -> Unit) {
        onResult(false)
    }
}

/** Only for testing the reward flow: a fake "ad", clearly labelled, that lasts three seconds. */
class TestAdProvider : AdProvider {
    override val name = "test"
    override fun isReady() = true

    override fun show(activity: Activity, onResult: (Boolean) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var done = false
        val tv = TextView(activity)
        tv.text = "إعلان تجريبي (للاختبار فقط)\nتُمنح المكافأة بعد 3 ثوانٍ"
        tv.textSize = 18f
        tv.gravity = Gravity.CENTER
        tv.setPadding(40, 60, 40, 40)
        val dialog = AlertDialog.Builder(activity)
            .setView(tv)
            .setCancelable(false)
            .setNegativeButton("إغلاق قبل النهاية") { _, _ ->
                if (!done) {
                    done = true
                    onResult(false)
                }
            }
            .create()
        dialog.show()
        handler.postDelayed({
            if (!done) {
                done = true
                if (dialog.isShowing) dialog.dismiss()
                onResult(true)
            }
        }, 3_000L)
    }
}

/**
 * Optional rewarded ads for the grown-ups, never forced on the child: the reward is named before the
 * ad starts, a parent question comes first, ads are spaced out and limited per day, and the reward is
 * given only when the provider says the ad was watched to the end, once per ad.
 */
object RewardedAds {
    private const val PREFS = "ghost_ads"
    private const val MIN_GAP_MS = 3L * 60L * 1000L
    private const val MAX_PER_DAY = 5

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun testMode(context: Context): Boolean =
        context.applicationContext.getSharedPreferences("ghost_prefs", Context.MODE_PRIVATE).getBoolean("ad_test_mode", false)

    fun setTestMode(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences("ghost_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("ad_test_mode", on).apply()
    }

    /**
     * The provider in use. A real one goes here once a provider whose terms allow apps for children is
     * chosen and set up. Until then nothing real is shown, and the test mode only shows a fake ad.
     */
    fun provider(context: Context): AdProvider = if (testMode(context)) TestAdProvider() else NoAdProvider

    private fun dayNumber(): Int {
        val now = System.currentTimeMillis()
        return ((now + TimeZone.getDefault().getOffset(now)) / 86_400_000L).toInt()
    }

    /** Why an ad cannot be shown now, or null when it can. */
    fun blockedReason(context: Context): String? {
        if (!provider(context).isReady()) {
            return "الإعلانات غير مفعّلة بعد: لم يُضبط مزوّد إعلانات يناسب تطبيقات الأطفال"
        }
        val p = prefs(context)
        if (System.currentTimeMillis() - p.getLong("last", 0L) < MIN_GAP_MS) return "انتظر قليلًا بين إعلان وآخر"
        val count = if (p.getInt("day", -1) == dayNumber()) p.getInt("count", 0) else 0
        if (count >= MAX_PER_DAY) return "وصلتم إلى حد الإعلانات لهذا اليوم، عودوا غدًا"
        return null
    }

    /** The grown-up question, the named reward, then the ad. [onGranted] runs once, only after a full view. */
    fun watch(activity: Activity, offer: String, onGranted: () -> Unit) {
        val reason = blockedReason(activity)
        if (reason != null) {
            Toast.makeText(activity, reason, Toast.LENGTH_LONG).show()
            return
        }
        askParent(activity, offer) {
            val id = UUID.randomUUID().toString()
            prefs(activity).edit().putString("pending", id).apply()
            provider(activity).show(activity) { watched ->
                val p = prefs(activity)
                if (watched && p.getString("pending", "") == id) {
                    // One reward for one ad: the pending number is used up
                    val today = dayNumber()
                    val count = if (p.getInt("day", -1) == today) p.getInt("count", 0) else 0
                    p.edit().putString("pending", "").putLong("last", System.currentTimeMillis())
                        .putInt("day", today).putInt("count", count + 1).apply()
                    onGranted()
                } else {
                    p.edit().putString("pending", "").apply()
                    Toast.makeText(activity, "لم تكتمل المشاهدة، فلا مكافأة", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun askParent(activity: Activity, offer: String, onPass: () -> Unit) {
        val a = 6 + Random.nextInt(4)
        val b = 6 + Random.nextInt(4)
        val box = LinearLayout(activity)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(48, 24, 48, 0)
        val msg = TextView(activity)
        msg.text = "للأهل فقط.\nالمكافأة: $offer\n\nللتأكد أنكم من الأهل: كم يساوي $a × $b ؟"
        msg.textSize = 16f
        msg.setTextColor(Color.parseColor("#2B2540"))
        box.addView(msg)
        val input = EditText(activity)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        box.addView(input)
        val dialog = AlertDialog.Builder(activity)
            .setTitle("مشاهدة إعلان اختياري")
            .setView(box)
            .setPositiveButton("شاهد الإعلان", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.toString().trim() == (a * b).toString()) {
                    dialog.dismiss()
                    onPass()
                } else {
                    Toast.makeText(activity, "إجابة غير صحيحة", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }
}
