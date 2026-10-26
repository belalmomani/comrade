package com.cuteghost.app

import android.content.Context

/**
 * Outfits for the ghosts: hats, scarves, small wings, glowing colours and a star trail.
 * Gifts are unlocked by taking care of the ghosts (the source of the rewards is this one place,
 * so another reward source can be plugged in later).
 */
object Wardrobe {
    private const val PREFS = "ghost_prefs"
    private const val KEY_UNLOCKED = "wardrobe_unlocked"
    private const val KEY_MEALS = "stat_meals"
    private const val KEY_DRINKS = "stat_drinks"
    private const val KEY_HATCHED = "stat_hatched"
    private const val KEY_DAYS = "stat_days"
    private const val KEY_LAST_DAY = "stat_last_day"
    private const val KEY_STARS = "stat_stars"

    enum class Slot { HAT, SCARF, WINGS, GLOW, TRAIL }

    class Item(val id: String, val slot: Slot, val variant: Int, val nameRes: Int)

    val items: List<Item> = listOf(
        Item("hat_party", Slot.HAT, 1, R.string.item_hat_party),
        Item("hat_crown", Slot.HAT, 2, R.string.item_hat_crown),
        Item("hat_wizard", Slot.HAT, 3, R.string.item_hat_wizard),
        Item("scarf_red", Slot.SCARF, 1, R.string.item_scarf_red),
        Item("scarf_blue", Slot.SCARF, 2, R.string.item_scarf_blue),
        Item("wings", Slot.WINGS, 1, R.string.item_wings),
        Item("glow_pink", Slot.GLOW, 1, R.string.item_glow_pink),
        Item("glow_cyan", Slot.GLOW, 2, R.string.item_glow_cyan),
        Item("glow_gold", Slot.GLOW, 3, R.string.item_glow_gold),
        Item("trail_stars", Slot.TRAIL, 1, R.string.item_trail_stars)
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun unlockedIds(context: Context): Set<String> =
        prefs(context).getString(KEY_UNLOCKED, "").orEmpty()
            .split(',').filter { it.isNotEmpty() }.toSet()

    fun isUnlocked(context: Context, item: Item): Boolean = item.id in unlockedIds(context)

    /** Items that the care statistics have earned so far. */
    private fun earned(meals: Int, drinks: Int, hatched: Int, days: Int, stars: Int): Set<String> {
        val ids = HashSet<String>()
        if (meals >= 1) ids.add("hat_party")
        if (hatched >= 1) ids.add("hat_crown")
        if (meals >= 10) ids.add("hat_wizard")
        if (drinks >= 3) ids.add("scarf_red")
        if (drinks >= 10) ids.add("scarf_blue")
        if (meals + drinks >= 15) ids.add("wings")
        if (days >= 1) ids.add("glow_pink")
        if (days >= 3) ids.add("glow_cyan")
        if (days >= 7) ids.add("glow_gold")
        if (hatched >= 2 || days >= 5 || stars >= 5) ids.add("trail_stars")
        if (stars >= 15) ids.add("glow_gold")
        return ids
    }

    /** Records a completed meal or drink; returns the gifts that just became available. */
    fun recordCare(context: Context, meal: Boolean, dayNumber: Int): List<Item> {
        val p = prefs(context)
        val e = p.edit()
        if (meal) e.putInt(KEY_MEALS, p.getInt(KEY_MEALS, 0) + 1)
        else e.putInt(KEY_DRINKS, p.getInt(KEY_DRINKS, 0) + 1)
        if (p.getInt(KEY_LAST_DAY, -1) != dayNumber) {
            e.putInt(KEY_DAYS, p.getInt(KEY_DAYS, 0) + 1)
            e.putInt(KEY_LAST_DAY, dayNumber)
        }
        e.apply()
        return unlockNew(context)
    }

    /** Records a baby that hatched. */
    fun recordHatched(context: Context): List<Item> {
        val p = prefs(context)
        p.edit().putInt(KEY_HATCHED, p.getInt(KEY_HATCHED, 0) + 1).apply()
        return unlockNew(context)
    }

    private fun unlockNew(context: Context): List<Item> {
        val p = prefs(context)
        val have = unlockedIds(context)
        val now = earned(
            p.getInt(KEY_MEALS, 0), p.getInt(KEY_DRINKS, 0),
            p.getInt(KEY_HATCHED, 0), p.getInt(KEY_DAYS, 0), p.getInt(KEY_STARS, 0)
        )
        val fresh = items.filter { it.id in now && it.id !in have }
        if (fresh.isNotEmpty()) {
            p.edit().putString(KEY_UNLOCKED, (have + fresh.map { it.id }).joinToString(",")).apply()
        }
        return fresh
    }

    // ---------- Stars and surprise gifts from the portal ----------

    fun stars(context: Context): Int = prefs(context).getInt(KEY_STARS, 0)

    /** Adds collected stars; returns the gifts that became available because of them. */
    fun addStars(context: Context, count: Int): List<Item> {
        val p = prefs(context)
        p.edit().putInt(KEY_STARS, p.getInt(KEY_STARS, 0) + count).apply()
        return unlockNew(context)
    }

    /** Spends stars (for example to unlock a house). Returns false when there are not enough. */
    fun spendStars(context: Context, count: Int): Boolean {
        val p = prefs(context)
        val have = p.getInt(KEY_STARS, 0)
        if (have < count) return false
        p.edit().putInt(KEY_STARS, have - count).apply()
        return true
    }

    /** Unlocks one random item that is still locked, or returns null when everything is unlocked. */
    fun giftRandom(context: Context): Item? {
        val have = unlockedIds(context)
        val locked = items.filter { it.id !in have }
        val pick = locked.randomOrNull() ?: return null
        prefs(context).edit().putString(KEY_UNLOCKED, (have + pick.id).joinToString(",")).apply()
        return pick
    }

    /** For testing: unlocks everything. */
    fun unlockAll(context: Context) {
        prefs(context).edit().putString(KEY_UNLOCKED, items.joinToString(",") { it.id }).apply()
    }

    // ---------- What is worn ----------

    private fun key(slot: Slot) = "equip_" + slot.name.lowercase()

    /** The worn variant of a slot (0 = nothing). */
    fun equipped(context: Context, slot: Slot): Int = prefs(context).getInt(key(slot), 0)

    fun equippedItem(context: Context, slot: Slot): Item? {
        val v = equipped(context, slot)
        return items.firstOrNull { it.slot == slot && it.variant == v }
    }

    /** Next unlocked variant of the slot, or nothing after the last one. */
    fun cycle(context: Context, slot: Slot) {
        val unlocked = items.filter { it.slot == slot && isUnlocked(context, it) }.map { it.variant }.sorted()
        val current = equipped(context, slot)
        val next = unlocked.firstOrNull { it > current } ?: 0
        prefs(context).edit().putInt(key(slot), next).apply()
    }
}
