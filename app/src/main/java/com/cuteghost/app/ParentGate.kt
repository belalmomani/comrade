package com.cuteghost.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

/** The "grown-ups only" question (a multiplication a small child cannot answer). */
object ParentGate {

    fun ask(
        activity: Activity,
        title: String,
        note: String,
        onCancel: () -> Unit,
        onPass: () -> Unit
    ) {
        val a = 12 + Random.nextInt(8)
        val b = 6 + Random.nextInt(4)
        val box = LinearLayout(activity)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(48, 24, 48, 0)
        val msg = TextView(activity)
        val intro = if (note.isEmpty()) "" else note + "\n\n"
        msg.text = intro + "للأهل فقط. للتأكد أنكم من الأهل: كم يساوي $a × $b ؟"
        msg.textSize = 16f
        msg.setTextColor(Color.parseColor("#2B2540"))
        box.addView(msg)
        val input = EditText(activity)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        box.addView(input)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("متابعة", null)
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
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                dialog.dismiss()
                onCancel()
            }
        }
        dialog.show()
    }
}
