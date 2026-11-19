package com.cuteghost.app

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The parents' panel (behind the multiplication question): daily goals, and the optional rest-time
 * lock that covers the phone for a chosen number of minutes.
 */
class ParentActivity : Activity() {

    companion object {
        private const val REQ_OVERLAY = 42
        private const val REQ_EXPORT = 43
        private const val REQ_IMPORT = 44
    }

    private var built = false
    private var pendingMinutes = 0

    private lateinit var goalsInfo: TextView
    private lateinit var lockInfo: TextView
    private lateinit var soundInfo: TextView
    private lateinit var scheduleInfo: TextView
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

        // ----- Daily schedule -----
        col.addView(heading("جدول وقت اللعب اليومي"))
        col.addView(
            body(
                "يبدأ وقت اللعب وحده كل يوم في الساعة التي تختارونها. يعمل ما دام الشبح ظاهرًا على الشاشة، " +
                    "فلا يبدأ إن كان الشبح مخفيًا أو التطبيق مغلقًا. يلزم أن تكون صلاحية «الظهور فوق التطبيقات» مفعّلة."
            )
        )
        val scheduleCheck = CheckBox(this)
        scheduleCheck.text = "تفعيل الجدول اليومي"
        scheduleCheck.textSize = 16f
        scheduleCheck.isChecked = ScreenLock.scheduleEnabled(this)
        scheduleCheck.setOnCheckedChangeListener { _, on ->
            ScreenLock.setSchedule(this, on, ScreenLock.scheduleStart(this), ScreenLock.scheduleLength(this))
            refresh()
        }
        col.addView(scheduleCheck)
        scheduleInfo = body("")
        col.addView(scheduleInfo)
        col.addView(button("اختيار ساعة البدء") {
            val start = ScreenLock.scheduleStart(this)
            TimePickerDialog(this, { _, hour, minute ->
                ScreenLock.setSchedule(this, ScreenLock.scheduleEnabled(this), hour * 60 + minute, ScreenLock.scheduleLength(this))
                refresh()
            }, start / 60, start % 60, false).show()
        })
        val lengthRow = LinearLayout(this)
        lengthRow.orientation = LinearLayout.HORIZONTAL
        lengthRow.gravity = Gravity.CENTER
        for (m in intArrayOf(15, 30, 60, 120)) {
            val b = button("$m د") {
                ScreenLock.setSchedule(this, ScreenLock.scheduleEnabled(this), ScreenLock.scheduleStart(this), m)
                refresh()
            }
            lengthRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(body("مدة الجدول اليومي:"))
        col.addView(lengthRow)

        // ----- Sound -----
        col.addView(heading("الصوت"))
        col.addView(body("الأصوات قليلة أصلًا وتتباعد عن بعضها. اختاروا مستواها:"))
        soundInfo = body("")
        col.addView(soundInfo)
        val soundRow = LinearLayout(this)
        soundRow.orientation = LinearLayout.HORIZONTAL
        soundRow.gravity = Gravity.CENTER
        val levels = arrayOf(
            "مغلق" to Sounds.LEVEL_OFF,
            "هادئ جدًا" to Sounds.LEVEL_VERY_QUIET,
            "هادئ" to Sounds.LEVEL_QUIET
        )
        for ((name, level) in levels) {
            val b = button(name) {
                Sounds.setLevel(this, level)
                if (level != Sounds.LEVEL_OFF) Sounds.play(this, Snd.POP, true)
                refresh()
            }
            soundRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(soundRow)

        // ----- Backup -----
        col.addView(heading("النسخ الاحتياطي لتقدم الطفل"))
        col.addView(
            body(
                "يحفظ ملفًا واحدًا فيه الأسماء والنجوم والملابس والبيت والقرية والمزرعة والأهداف والملصقات. " +
                    "احفظوه في «التنزيلات» أو أرسلوه لأنفسكم، ثم استعيدوه من هذه اللوحة على هذا الهاتف " +
                    "أو على هاتف جديد بعد تثبيت التطبيق. الاستعادة تستبدل التقدم الحالي."
            )
        )
        col.addView(button("حفظ نسخة احتياطية") { startExport() })
        col.addView(button("استعادة من نسخة") { startRestore() })

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
        val startMin = ScreenLock.scheduleStart(this)
        val hour12 = if (startMin / 60 % 12 == 0) 12 else startMin / 60 % 12
        val part = if (startMin / 60 < 12) "صباحًا" else "مساءً"
        scheduleInfo.text = (if (ScreenLock.scheduleEnabled(this)) "الجدول مفعّل: " else "الجدول متوقف: ") +
            "كل يوم الساعة $hour12:" + (startMin % 60).toString().padStart(2, '0') + " $part لمدة ${ScreenLock.scheduleLength(this)} دقيقة."
        soundInfo.text = "المستوى الحالي: " + when (Sounds.level(this)) {
            Sounds.LEVEL_OFF -> "مغلق"
            Sounds.LEVEL_VERY_QUIET -> "هادئ جدًا"
            else -> "هادئ"
        }
        goalsInfo.text = "اليوم: ${DailyGoals.doneCount(this)} من ${DailyGoals.total()}" +
            "  •  الملصقات: ${DailyGoals.stickers(this)}" +
            "  •  أيام متتالية: ${DailyGoals.streak(this)}"
    }

    // ---------- Backup ----------

    private fun startExport() {
        val name = "cute-ghost-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, name)
        try {
            startActivityForResult(intent, REQ_EXPORT)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "لا يوجد تطبيق لحفظ الملفات في هذا الهاتف", Toast.LENGTH_LONG).show()
        }
    }

    private fun startRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        try {
            startActivityForResult(intent, REQ_IMPORT)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "لا يوجد تطبيق لاختيار الملفات في هذا الهاتف", Toast.LENGTH_LONG).show()
        }
    }

    private fun writeBackup(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(Backup.export(this).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "تم حفظ النسخة الاحتياطية", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر حفظ الملف", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmRestore(uri: Uri) {
        val text: String? = try {
            contentResolver.openInputStream(uri)?.use { input -> String(input.readBytes(), Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
        if (text == null || text.length > 2_000_000) {
            Toast.makeText(this, "تعذّرت قراءة الملف", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("استعادة نسخة احتياطية")
            .setMessage(
                "ستُستبدل بيانات الطفل الحالية (النجوم والملابس والبيت والقرية والمزرعة والأهداف) " +
                    "بما في النسخة، ثم يُغلق التطبيق ليبدأ من جديد. متابعة؟"
            )
            .setPositiveButton("استعد") { _, _ -> doRestore(text) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun doRestore(text: String) {
        try {
            val n = Backup.restore(this, text)
            GhostOverlayService.stop(this)
            AlertDialog.Builder(this)
                .setTitle("تمت الاستعادة")
                .setMessage("استُعيدت $n قيمة. سيُغلق التطبيق الآن، افتحوه من جديد.")
                .setCancelable(false)
                .setPositiveButton("حسنًا") { _, _ ->
                    finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
                .show()
        } catch (e: Backup.BadBackupException) {
            Toast.makeText(this, e.message ?: "نسخة غير صالحة", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّرت الاستعادة", Toast.LENGTH_LONG).show()
        }
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
        val picked = data?.data
        if (resultCode == RESULT_OK && picked != null) {
            if (requestCode == REQ_EXPORT) writeBackup(picked)
            if (requestCode == REQ_IMPORT) confirmRestore(picked)
        }
        if (requestCode == REQ_OVERLAY) {
            val m = pendingMinutes
            pendingMinutes = 0
            if (m > 0 && Settings.canDrawOverlays(this)) beginLock(m)
        }
        refresh()
    }
}
