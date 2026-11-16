package com.cuteghost.app

import android.app.Activity
import android.app.AlertDialog
<<<<<<< HEAD
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The parents' panel (behind the multiplication question): daily goals, and the optional rest-time
 * lock that covers the phone for a chosen number of minutes.
 */
class ParentActivity : Activity() {

    companion object {
        private const val REQ_OVERLAY = 42
    }

    private var built = false
    private var pendingMinutes = 0

    private lateinit var goalsInfo: TextView
    private lateinit var lockInfo: TextView
    private lateinit var stopLockButton: Button

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this))
        ParentGate.ask(this, "لوحة الأهل", "", { finish() }) { buildUi() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun heading(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 20f
        t.setTextColor(Color.parseColor("#2B2540"))
        t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.setPadding(0, dp(28), 0, dp(6))
        return t
    }

    private fun body(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.setTextColor(Color.parseColor("#5C5675"))
        t.setLineSpacing(dp(3).toFloat(), 1f)
        t.setPadding(0, dp(4), 0, dp(4))
        return t
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.textSize = 16f
        b.setOnClickListener { onClick() }
        return b
    }

    private fun buildUi() {
        built = true
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#F5F3FF"))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutDirection = View.LAYOUT_DIRECTION_RTL
        col.setPadding(dp(20), dp(32), dp(20), dp(32))
        scroll.addView(col)

        val title = TextView(this)
        title.text = "لوحة الأهل"
        title.textSize = 26f
        title.setTextColor(Color.parseColor("#2B2540"))
        title.gravity = Gravity.CENTER
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        col.addView(title)

        // ----- Daily goals -----
        col.addView(heading("الأهداف اليومية"))
        col.addView(
            body(
                "خمسة أهداف مصورة كل يوم: إطعام، سقي، استحمام، تنظيف الأسنان، ورعاية المزرعة. " +
                    "عند إنجاز الخمسة يحصل الطفل على ملصق و${DailyGoals.REWARD_STARS} نجوم و" +
                    "${DailyGoals.REWARD_MATERIALS} مواد بناء، مرة واحدة في اليوم. لا يُخصم شيء إن فاته يوم."
            )
        )
        val goalsCheck = CheckBox(this)
        goalsCheck.text = "تفعيل الأهداف اليومية"
        goalsCheck.textSize = 16f
        goalsCheck.isChecked = DailyGoals.enabled(this)
        goalsCheck.setOnCheckedChangeListener { _, on ->
            DailyGoals.setEnabled(this, on)
            refresh()
        }
        col.addView(goalsCheck)
        goalsInfo = body("")
        col.addView(goalsInfo)
        col.addView(button("عرض أهداف اليوم") {
            startActivity(Intent(this, GoalsActivity::class.java))
        })

        // ----- Play time (lock) -----
        col.addView(heading("وقت اللعب (تجميد الهاتف على اللعبة)"))
        col.addView(
            body(
                "اختياري ومغلق دائمًا ما لم تبدؤوه أنتم. عند البدء يبقى الهاتف على شاشة الطفل وألعابه فقط " +
                    "(القرية والبيت وملعب الأطفال والأهداف) للمدة التي تختارونها، ولا يستطيع فتح تطبيقات أخرى. " +
                    "يطلب أندرويد منكم الموافقة على «تثبيت الشاشة» مرة عند البدء، اضغطوا «ابدأ». " +
                    "ولا تنسوا أن هذه الميزة قد تكون متوقفة في إعدادات الهاتف (الأمان ← تثبيت التطبيق).\n" +
                    "للإنهاء قبل الوقت: زر «للأهل» في شاشة الطفل ثم هذه اللوحة ثم «إنهاء وقت اللعب الآن»."
            )
        )
        lockInfo = body("")
        col.addView(lockInfo)
        stopLockButton = button("إنهاء وقت اللعب الآن") {
            ScreenLock.stop(this)
            try {
                stopLockTask()
            } catch (e: RuntimeException) {
                // it was not pinned
            }
            refresh()
        }
        col.addView(stopLockButton)

        col.addView(body("ابدؤوا وقت لعب لمدة:"))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        for (m in intArrayOf(15, 30, 60, 120)) {
            val b = button("$m د") { confirmLock(m) }
            row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(row)

        // ----- Other -----
        col.addView(heading("أخرى"))
        col.addView(button("إخفاء الشبح العائم") {
            GhostOverlayService.stop(this)
        })
        col.addView(button("الإعدادات المتقدمة والتحديث والاختبار") {
            startActivity(Intent(this, MainActivity::class.java))
        })

        col.addView(button("إغلاق") { finish() }, topMargin(28))

        setContentView(scroll)
        refresh()
    }

    private fun topMargin(dpValue: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(dpValue)
        return lp
    }

    private fun refresh() {
        if (!built) return
        if (ScreenLock.isActive(this)) {
            lockInfo.text = "وقت اللعب يعمل الآن، والمتبقي ${ScreenLock.minutesLeft(this)} دقيقة."
            stopLockButton.visibility = View.VISIBLE
        } else {
            lockInfo.text = "وقت اللعب غير مفعّل الآن."
            stopLockButton.visibility = View.GONE
        }
        goalsInfo.text = "اليوم: ${DailyGoals.doneCount(this)} من ${DailyGoals.total()}" +
            "  •  الملصقات: ${DailyGoals.stickers(this)}" +
            "  •  أيام متتالية: ${DailyGoals.streak(this)}"
    }

    private fun confirmLock(minutes: Int) {
        AlertDialog.Builder(this)
            .setTitle("بدء وقت اللعب")
            .setMessage(
                "سيبقى الهاتف على ألعاب الطفل فقط لمدة $minutes دقيقة.\n" +
                    "للإنهاء قبل الوقت: زر «للأهل» ثم «إنهاء وقت اللعب الآن»."
            )
            .setPositiveButton("ابدأ") { _, _ -> beginLock(minutes) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun beginLock(minutes: Int) {
        if (!Settings.canDrawOverlays(this)) {
            pendingMinutes = minutes
            Toast.makeText(this, "فعّلوا «الظهور فوق التطبيقات» ثم ارجعوا", Toast.LENGTH_LONG).show()
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQ_OVERLAY
            )
            return
        }
        ScreenLock.start(this, minutes)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
=======
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The parents' panel (behind the multiplication question): daily goals, and the optional rest-time
 * lock that covers the phone for a chosen number of minutes.
 */
class ParentActivity : Activity() {

    companion object {
        private const val REQ_ADMIN = 41
        private const val REQ_OVERLAY = 42
    }

    private var built = false
    private var pendingMinutes = 0
    private var updatingChecks = false

    private lateinit var goalsInfo: TextView
    private lateinit var lockInfo: TextView
    private lateinit var stopLockButton: Button
    private lateinit var systemCheck: CheckBox
    private lateinit var adminButton: Button

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this))
        ParentGate.ask(this, "لوحة الأهل", "", { finish() }) { buildUi() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun heading(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 20f
        t.setTextColor(Color.parseColor("#2B2540"))
        t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.setPadding(0, dp(28), 0, dp(6))
        return t
    }

    private fun body(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.setTextColor(Color.parseColor("#5C5675"))
        t.setLineSpacing(dp(3).toFloat(), 1f)
        t.setPadding(0, dp(4), 0, dp(4))
        return t
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.textSize = 16f
        b.setOnClickListener { onClick() }
        return b
    }

    private fun buildUi() {
        built = true
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#F5F3FF"))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutDirection = View.LAYOUT_DIRECTION_RTL
        col.setPadding(dp(20), dp(32), dp(20), dp(32))
        scroll.addView(col)

        val title = TextView(this)
        title.text = "لوحة الأهل"
        title.textSize = 26f
        title.setTextColor(Color.parseColor("#2B2540"))
        title.gravity = Gravity.CENTER
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        col.addView(title)

        // ----- Daily goals -----
        col.addView(heading("الأهداف اليومية"))
        col.addView(
            body(
                "خمسة أهداف مصورة كل يوم: إطعام، سقي، استحمام، تنظيف الأسنان، ورعاية المزرعة. " +
                    "عند إنجاز الخمسة يحصل الطفل على ملصق و${DailyGoals.REWARD_STARS} نجوم و" +
                    "${DailyGoals.REWARD_MATERIALS} مواد بناء، مرة واحدة في اليوم. لا يُخصم شيء إن فاته يوم."
            )
        )
        val goalsCheck = CheckBox(this)
        goalsCheck.text = "تفعيل الأهداف اليومية"
        goalsCheck.textSize = 16f
        goalsCheck.isChecked = DailyGoals.enabled(this)
        goalsCheck.setOnCheckedChangeListener { _, on ->
            DailyGoals.setEnabled(this, on)
            refresh()
        }
        col.addView(goalsCheck)
        goalsInfo = body("")
        col.addView(goalsInfo)
        col.addView(button("عرض أهداف اليوم") {
            startActivity(Intent(this, GoalsActivity::class.java))
        })

        // ----- Rest time (lock) -----
        col.addView(heading("وقت الراحة (قفل الهاتف)"))
        col.addView(
            body(
                "اختياري ومغلق دائمًا ما لم تبدؤوه أنتم. عند البدء تظهر شاشة نوم تغطي الهاتف طوال المدة " +
                    "التي تختارونها، فلا يستطيع الطفل استعماله. للإنهاء قبل الوقت اضغطوا «للأهل» في تلك " +
                    "الشاشة وأجيبوا عن سؤال الضرب.\n" +
                    "تنبيه: المكالمات الواردة لا تظهر فوق شاشة النوم، فأنهوها أولًا إن انتظرتم اتصالًا. " +
                    "وإعادة تشغيل الهاتف تنهي القفل."
            )
        )
        lockInfo = body("")
        col.addView(lockInfo)
        stopLockButton = button("إنهاء وقت الراحة الآن") {
            ScreenLock.stop(this)
            refresh()
        }
        col.addView(stopLockButton)

        col.addView(body("ابدؤوا وقت راحة لمدة:"))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        for (m in intArrayOf(15, 30, 60, 120)) {
            val b = button("$m د") { confirmLock(m) }
            row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(row)

        systemCheck = CheckBox(this)
        systemCheck.text = "قفل الهاتف بنظام أندرويد أيضًا عند البدء"
        systemCheck.textSize = 16f
        systemCheck.setOnCheckedChangeListener { _, on ->
            if (updatingChecks) return@setOnCheckedChangeListener
            if (on) {
                ScreenLock.setSystemLockWanted(this, true)
                if (!LockAdmin.isActive(this)) {
                    try {
                        startActivityForResult(LockAdmin.activateIntent(this), REQ_ADMIN)
                    } catch (e: ActivityNotFoundException) {
                        ScreenLock.setSystemLockWanted(this, false)
                        Toast.makeText(this, "تعذّر فتح إعدادات مدير الجهاز", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                ScreenLock.setSystemLockWanted(this, false)
            }
            refresh()
        }
        col.addView(systemCheck)
        col.addView(
            body(
                "يُطفئ الشاشة ويُظهر قفل هاتفكم (الرمز أو البصمة) إن كان مضبوطًا، فلا يفتحه الطفل بدونه. " +
                    "يحتاج السماح لهذا التطبيق بصلاحية «مدير الجهاز»، وهي تُستخدم لإطفاء الشاشة فقط."
            )
        )
        adminButton = button("إلغاء تفعيل مدير الجهاز") {
            LockAdmin.deactivate(this)
            ScreenLock.setSystemLockWanted(this, false)
            refresh()
        }
        col.addView(adminButton)

        col.addView(button("إغلاق") { finish() }, topMargin(28))

        setContentView(scroll)
        refresh()
    }

    private fun topMargin(dpValue: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(dpValue)
        return lp
    }

    private fun refresh() {
        if (!built) return
        updatingChecks = true
        systemCheck.isChecked = ScreenLock.systemLockWanted(this) && LockAdmin.isActive(this)
        updatingChecks = false
        adminButton.visibility = if (LockAdmin.isActive(this)) View.VISIBLE else View.GONE
        if (ScreenLock.isActive(this)) {
            lockInfo.text = "وقت الراحة يعمل الآن، والمتبقي ${ScreenLock.minutesLeft(this)} دقيقة."
            stopLockButton.visibility = View.VISIBLE
        } else {
            lockInfo.text = "وقت الراحة غير مفعّل الآن."
            stopLockButton.visibility = View.GONE
        }
        goalsInfo.text = "اليوم: ${DailyGoals.doneCount(this)} من ${DailyGoals.total()}" +
            "  •  الملصقات: ${DailyGoals.stickers(this)}" +
            "  •  أيام متتالية: ${DailyGoals.streak(this)}"
    }

    private fun confirmLock(minutes: Int) {
        AlertDialog.Builder(this)
            .setTitle("بدء وقت الراحة")
            .setMessage(
                "سيُغطّى الهاتف بشاشة نوم لمدة $minutes دقيقة.\n" +
                    "للإنهاء قبل الوقت: زر «للأهل» ثم حل سؤال الضرب."
            )
            .setPositiveButton("ابدأ") { _, _ -> beginLock(minutes) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun beginLock(minutes: Int) {
        if (!Settings.canDrawOverlays(this)) {
            pendingMinutes = minutes
            Toast.makeText(this, "فعّلوا «الظهور فوق التطبيقات» ثم ارجعوا", Toast.LENGTH_LONG).show()
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQ_OVERLAY
            )
            return
        }
        ScreenLock.start(this, minutes)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ADMIN && !LockAdmin.isActive(this)) {
            ScreenLock.setSystemLockWanted(this, false)
            Toast.makeText(this, "لم يُفعَّل مدير الجهاز، فلن يُستخدم قفل النظام", Toast.LENGTH_LONG).show()
        }
>>>>>>> branch 'main' of https://github.com/belalmomani/comrade.git
        if (requestCode == REQ_OVERLAY) {
            val m = pendingMinutes
            pendingMinutes = 0
            if (m > 0 && Settings.canDrawOverlays(this)) beginLock(m)
        }
        refresh()
    }
}
