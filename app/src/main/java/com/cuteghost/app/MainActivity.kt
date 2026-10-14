package com.cuteghost.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

class MainActivity : Activity() {

    companion object {
        private const val REQ_MIC = 100
    }

    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var pendingShow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        showVersion()

        findViewById<Button>(R.id.showButton).setOnClickListener { proceedShow() }
        findViewById<Button>(R.id.hideButton).setOnClickListener {
            pendingShow = false
            GhostOverlayService.stop(this)
            refreshStatusLater()
        }
        findViewById<Button>(R.id.parentButton).setOnClickListener { showParentGate() }
    }

    override fun onResume() {
        super.onResume()
        if (pendingShow) {
            pendingShow = false
            if (Settings.canDrawOverlays(this)) {
                proceedShow()
            } else {
                Toast.makeText(this, R.string.permission_not_granted, Toast.LENGTH_LONG).show()
            }
        }
        refreshStatus()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                proceedShow()
            } else {
                Toast.makeText(this, R.string.mic_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Overlay permission -> AI key -> microphone permission -> start. */
    private fun proceedShow() {
        if (!Settings.canDrawOverlays(this)) {
            showPermissionDialog()
            return
        }
        if (!ChatSettings.hasKey(this)) {
            showNeedKeyDialog()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        startGhost()
    }

    private fun startGhost() {
        try {
            GhostOverlayService.start(this)
        } catch (e: RuntimeException) {
            Toast.makeText(this, R.string.start_failed, Toast.LENGTH_LONG).show()
        }
        refreshStatusLater()
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_title)
            .setMessage(R.string.permission_message)
            .setPositiveButton(R.string.open_settings) { _, _ -> openOverlaySettings() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showNeedKeyDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.need_key_title)
            .setMessage(R.string.need_key_message)
            .setPositiveButton(R.string.open_parent_settings) { _, _ -> showParentGate() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Simple arithmetic gate so small children cannot reach the settings. */
    private fun showParentGate() {
        val a = Random.nextInt(3, 9)
        val b = Random.nextInt(3, 9)
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this)
            .setTitle(R.string.parent_gate_title)
            .setMessage(getString(R.string.parent_gate_message, a, b))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (input.text.toString().trim() == (a + b).toString()) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    Toast.makeText(this, R.string.parent_gate_wrong, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openOverlaySettings() {
        pendingShow = true
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (e2: ActivityNotFoundException) {
                pendingShow = false
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun showVersion() {
        val name = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        if (name != null) {
            findViewById<TextView>(R.id.versionText).text = getString(R.string.version_label, name)
        }
    }

    private fun refreshStatusLater() {
        handler.postDelayed({ refreshStatus() }, 400L)
    }

    private fun refreshStatus() {
        statusText.setText(
            if (GhostOverlayService.isRunning) R.string.status_running else R.string.status_stopped
        )
    }
}
