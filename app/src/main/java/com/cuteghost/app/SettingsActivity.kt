package com.cuteghost.app

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Parent-only settings (reached through the arithmetic gate on the main screen). */
class SettingsActivity : Activity() {

    private lateinit var keyField: EditText
    private lateinit var modelField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val gap = (12 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        fun addText(resId: Int, size: Float, bold: Boolean) {
            root.addView(TextView(this).apply {
                setText(resId)
                textSize = size
                setTextColor(getColor(R.color.text_main))
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, gap)
            })
        }

        addText(R.string.settings_title, 22f, true)
        addText(R.string.settings_disclosure, 14f, false)

        keyField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
            val saved = ChatSettings.apiKey(this@SettingsActivity)
            hint = if (saved.isEmpty()) getString(R.string.settings_key_hint)
            else getString(R.string.settings_key_saved, saved.takeLast(4))
        }
        root.addView(keyField)

        modelField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            hint = getString(R.string.settings_model_hint)
            setText(ChatSettings.model(this@SettingsActivity))
        }
        root.addView(modelField)

        root.addView(Button(this).apply {
            setText(R.string.settings_save)
            setOnClickListener {
                ChatSettings.save(
                    this@SettingsActivity,
                    keyField.text.toString(),
                    modelField.text.toString()
                )
                Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        root.addView(Button(this).apply {
            setText(R.string.settings_clear_key)
            setOnClickListener {
                ChatSettings.clearKey(this@SettingsActivity)
                Toast.makeText(this@SettingsActivity, R.string.settings_key_cleared, Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
