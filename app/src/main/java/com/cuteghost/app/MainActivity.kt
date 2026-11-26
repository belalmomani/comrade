package com.cuteghost.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQ_NOTIF = 101
        private const val PREFS = "ghost_prefs"
        private const val KEY_ASKED_NOTIF = "asked_notifications"
    }

    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var pendingShow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Edge.apply(this)
        statusText = findViewById(R.id.statusText)
        showVersion()
        findViewById<TextView>(R.id.privacyNote).setText(R.string.main_privacy_note_local)

        findViewById<Button>(R.id.showButton).setOnClickListener { proceedShow() }
        findViewById<Button>(R.id.villageButton).setOnClickListener {
            startActivity(Intent(this, VillageActivity::class.java))
        }
        findViewById<Button>(R.id.playButton).setOnClickListener {
            startActivity(Intent(this, PlaygroundActivity::class.java))
        }
        findViewById<Button>(R.id.goalsButton).setOnClickListener {
            startActivity(Intent(this, GoalsActivity::class.java))
        }
        findViewById<Button>(R.id.parentButton).setOnClickListener {
            startActivity(Intent(this, ParentActivity::class.java))
        }
        findViewById<Button>(R.id.houseButton).setOnClickListener {
            startActivity(Intent(this, HouseActivity::class.java))
        }
        findViewById<Button>(R.id.hideButton).setOnClickListener {
            pendingShow = false
            GhostOverlayService.stop(this)
            refreshStatusLater()
        }
        findViewById<Button>(R.id.feedButton).setOnClickListener { testNeed(false) }
        findViewById<Button>(R.id.drinkButton).setOnClickListener { testNeed(true) }
        findViewById<Button>(R.id.eggButton).setOnClickListener { testEgg() }
        findViewById<Button>(R.id.neglectButton).setOnClickListener { testNeglect() }
        findViewById<Button>(R.id.seasonButton).setOnClickListener { testSeasonNow() }
        findViewById<Button>(R.id.peekButton).setOnClickListener { testSurprise(true) }
        findViewById<android.widget.CheckBox>(R.id.gentleCheck).apply {
            isChecked = Gentle.isOn(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Gentle.set(this@MainActivity, on) }
        }
        findViewById<android.widget.CheckBox>(R.id.soundCheck).apply {
            isChecked = Sounds.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Sounds.setEnabled(this@MainActivity, on) }
        }
        findViewById<Button>(R.id.mysteryEggButton).setOnClickListener { GhostOverlayService.mysteryEggNow(this) }
        findViewById<Button>(R.id.bathButton).setOnClickListener { GhostOverlayService.bathNow(this) }
        findViewById<Button>(R.id.brushButton).setOnClickListener { GhostOverlayService.brushNow(this) }
        findViewById<Button>(R.id.giveButton).setOnClickListener {
            Wardrobe.addStars(this, 20)
            HomeStore.addMaterials(this, 10)
            android.widget.Toast.makeText(this, "أُضيفت 20 نجمة و10 مواد للتجربة", android.widget.Toast.LENGTH_SHORT).show()
        }
        val mischiefPrefs = getSharedPreferences("ghost_prefs", MODE_PRIVATE)
        findViewById<android.widget.CheckBox>(R.id.mischiefCheck).apply {
            isChecked = mischiefPrefs.getBoolean("mischief_enabled", true)
            setOnCheckedChangeListener { _, on ->
                mischiefPrefs.edit().putBoolean("mischief_enabled", on).apply()
            }
        }
        findViewById<Button>(R.id.mischiefButton).setOnClickListener { GhostOverlayService.mischiefNow(this) }
        findViewById<Button>(R.id.growButton).setOnClickListener { GhostOverlayService.growNow(this) }
        findViewById<Button>(R.id.portalButton).setOnClickListener { testSurprise(false) }

        val namesField = findViewById<EditText>(R.id.namesField)
        namesField.setText(ChildNames.raw(this))
        findViewById<Button>(R.id.saveNamesButton).setOnClickListener {
            ChildNames.save(this, namesField.text.toString())
            Toast.makeText(this, R.string.names_saved, Toast.LENGTH_SHORT).show()
        }

        setupWardrobe()
        askNotificationPermissionOnce()
        if (!BuildConfig.DEBUG) hideTestControls()
    }

    /** Developer testing buttons exist only in debug builds; the store version never shows them. */
    private fun hideTestControls() {
        val ids = intArrayOf(
            R.id.feedButton, R.id.drinkButton, R.id.eggButton, R.id.neglectButton, R.id.seasonButton,
            R.id.unlockAllButton, R.id.growButton, R.id.mischiefButton, R.id.giveButton, R.id.bathButton,
            R.id.brushButton, R.id.mysteryEggButton, R.id.peekButton, R.id.portalButton
        )
        for (id in ids) findViewById<View>(id)?.visibility = View.GONE
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
        refreshWardrobe()
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
    }

    // ---------- Showing the ghost ----------

    /** Overlay permission -> start. */
    private fun proceedShow() {
        if (!Settings.canDrawOverlays(this)) {
            showPermissionDialog()
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

    // ---------- Test buttons ----------

    /** Asks the running mother to be hungry or thirsty right now. */
    private fun testNeed(water: Boolean) {
        if (!GhostOverlayService.isRunning) {
            Toast.makeText(this, R.string.feed_need_ghost, Toast.LENGTH_LONG).show()
            return
        }
        try {
            GhostOverlayService.needNow(this, water)
        } catch (e: RuntimeException) {
            Toast.makeText(this, R.string.start_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun testEgg() {
        if (!GhostOverlayService.isRunning) {
            Toast.makeText(this, R.string.feed_need_ghost, Toast.LENGTH_LONG).show()
            return
        }
        try {
            GhostOverlayService.eggNow(this)
        } catch (e: RuntimeException) {
            Toast.makeText(this, R.string.start_failed, Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Wardrobe: hats, scarves, wings, glow, star trail ----------

    private val wardrobeSlots = listOf(
        Triple(R.id.hatButton, Wardrobe.Slot.HAT, R.string.slot_hat),
        Triple(R.id.scarfButton, Wardrobe.Slot.SCARF, R.string.slot_scarf),
        Triple(R.id.wingsButton, Wardrobe.Slot.WINGS, R.string.slot_wings),
        Triple(R.id.glowButton, Wardrobe.Slot.GLOW, R.string.slot_glow),
        Triple(R.id.trailButton, Wardrobe.Slot.TRAIL, R.string.slot_trail)
    )

    /** The list of available items of each category, shown above its button when it is opened. */
    private val wardrobePanels = HashMap<Wardrobe.Slot, android.widget.GridLayout>()
    private var openSlot: Wardrobe.Slot? = null

    private fun dpi(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun setupWardrobe() {
        for ((buttonId, slot, _) in wardrobeSlots) {
            val button = findViewById<Button>(buttonId)
            val parent = button.parent as android.view.ViewGroup
            val panel = android.widget.GridLayout(this)
            panel.columnCount = 3
            panel.visibility = android.view.View.GONE
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dpi(8)
            parent.addView(panel, parent.indexOfChild(button), lp)
            wardrobePanels[slot] = panel
            // Touching a category opens (or closes) the list of what is available in it
            button.setOnClickListener {
                openSlot = if (openSlot == slot) null else slot
                refreshWardrobe()
            }
        }
        findViewById<Button>(R.id.unlockAllButton).setOnClickListener {
            Wardrobe.unlockAll(this)
            refreshWardrobe()
            Toast.makeText(this, R.string.wardrobe_unlocked, Toast.LENGTH_SHORT).show()
        }
        refreshWardrobe()
    }

    private fun refreshWardrobe() {
        findViewById<TextView>(R.id.starsText).text = getString(R.string.stars_label, Wardrobe.stars(this))
        for ((buttonId, slot, labelRes) in wardrobeSlots) {
            val item = Wardrobe.equippedItem(this, slot)
            val itemName = if (item != null) getString(item.nameRes) else getString(R.string.item_none)
            val isOpen = openSlot == slot
            findViewById<Button>(buttonId).text =
                getString(R.string.wardrobe_slot_format, getString(labelRes), itemName) + if (isOpen) "  ▴" else "  ▾"
            val panel = wardrobePanels[slot] ?: continue
            panel.visibility = if (isOpen) android.view.View.VISIBLE else android.view.View.GONE
            if (isOpen) fillWardrobePanel(panel, slot)
        }
    }

    /** "None" and every item of the category that is already available; the worn one is highlighted. */
    private fun fillWardrobePanel(panel: android.widget.GridLayout, slot: Wardrobe.Slot) {
        panel.removeAllViews()
        val current = Wardrobe.equipped(this, slot)
        val options = ArrayList<Pair<String, Int>>()
        options.add(Pair(getString(R.string.item_none), 0))
        for (w in Wardrobe.items.filter { x -> x.slot == slot && Wardrobe.isUnlocked(this, x) }.sortedBy { x -> x.variant }) {
            options.add(Pair(getString(w.nameRes), w.variant))
        }
        for (option in options) {
            val chosen = option.second == current
            val chip = TextView(this)
            chip.text = option.first
            chip.textSize = 14f
            chip.gravity = android.view.Gravity.CENTER
            chip.setPadding(dpi(6), dpi(12), dpi(6), dpi(12))
            chip.setTextColor(if (chosen) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#2B2540"))
            val bg = android.graphics.drawable.GradientDrawable()
            bg.setColor(if (chosen) android.graphics.Color.parseColor("#7C5CFF") else android.graphics.Color.parseColor("#EDE7FF"))
            bg.cornerRadius = dpi(14).toFloat()
            chip.background = bg
            chip.setOnClickListener {
                Wardrobe.equip(this, slot, option.second)
                refreshWardrobe()
                outfitChanged()
            }
            val lp = android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED),
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
            )
            lp.width = 0
            lp.setMargins(dpi(3), dpi(3), dpi(3), dpi(3))
            panel.addView(chip, lp)
        }
        if (options.size == 1) {
            val hint = TextView(this)
            hint.text = "لم تُفتح قطع هنا بعد: تُفتح بالرعاية والنجوم والهدايا"
            hint.textSize = 13f
            hint.gravity = android.view.Gravity.CENTER
            hint.setTextColor(android.graphics.Color.parseColor("#5C5675"))
            val lp = android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED),
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 3, 1f)
            )
            lp.width = 0
            lp.setMargins(dpi(3), dpi(3), dpi(3), dpi(3))
            panel.addView(hint, lp)
        }
    }

    /** The running ghosts put on the new outfit right away. */
    private fun outfitChanged() {
        if (!GhostOverlayService.isRunning) return
        try {
            GhostOverlayService.outfitNow(this)
        } catch (e: RuntimeException) {
            // the outfit is applied at the next refresh anyway
        }
    }

    /** Test buttons for the surprises: a baby peeking from the edge, or the magic portal. */
    private fun testSurprise(peek: Boolean) {
        if (!GhostOverlayService.isRunning) {
            Toast.makeText(this, R.string.feed_need_ghost, Toast.LENGTH_LONG).show()
            return
        }
        try {
            if (peek) GhostOverlayService.peekNow(this) else GhostOverlayService.portalNow(this)
        } catch (e: RuntimeException) {
            Toast.makeText(this, R.string.start_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun testSeasonNow() {
        if (!GhostOverlayService.isRunning) {
            Toast.makeText(this, R.string.feed_need_ghost, Toast.LENGTH_LONG).show()
            return
        }
        try {
            GhostOverlayService.seasonNow(this)
        } catch (e: RuntimeException) {
            Toast.makeText(this, R.string.start_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun testNeglect() {
        if (!GhostOverlayService.isRunning) {
            Toast.makeText(this, R.string.feed_need_ghost, Toast.LENGTH_LONG).show()
            return
        }
        try {
            GhostOverlayService.neglectNow(this)
        } catch (e: RuntimeException) {
            Toast.makeText(this, R.string.start_failed, Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Notifications ----------

    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIF, false)) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        prefs.edit().putBoolean(KEY_ASKED_NOTIF, true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
    }

    // ---------- Status ----------

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
