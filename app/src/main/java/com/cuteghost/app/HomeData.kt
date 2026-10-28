package com.cuteghost.app

import android.content.Context
import android.graphics.Color

/** One of the six houses: its name, price in stars and the colours of its room. */
class HouseDef(
    val id: Int,
    val name: String,
    val about: String,
    val starCost: Int,
    val wall: Int,
    val floor: Int,
    val sky1: Int,
    val sky2: Int,
    val accent: Int
)

object HouseDefs {
    val all: List<HouseDef> = listOf(
        HouseDef(
            0, "المنزل العائم فوق السحاب", "بيت بنفسجي صغير يطفو بين الغيوم", 0,
            Color.parseColor("#F1E9FF"), Color.parseColor("#D9C9FF"),
            Color.parseColor("#BFD9FF"), Color.parseColor("#F4EDFF"), Color.parseColor("#7C5CFF")
        ),
        HouseDef(
            1, "بيت الفطر السحري", "فطرة حمراء كبيرة بنقاط بيضاء", 10,
            Color.parseColor("#FFE9EF"), Color.parseColor("#E9C9A8"),
            Color.parseColor("#CFF1D8"), Color.parseColor("#FFF3C9"), Color.parseColor("#E5484D")
        ),
        HouseDef(
            2, "القصر البلوري", "أبراج من البلور المتلألئ", 25,
            Color.parseColor("#E6F6FF"), Color.parseColor("#CFE8FF"),
            Color.parseColor("#A9D8FF"), Color.parseColor("#F0E6FF"), Color.parseColor("#4FB8F0")
        ),
        HouseDef(
            3, "بيت الشجرة", "كوخ دافئ فوق شجرة كبيرة", 15,
            Color.parseColor("#F3E3C8"), Color.parseColor("#C9A57A"),
            Color.parseColor("#BFE8C8"), Color.parseColor("#E6F7D6"), Color.parseColor("#4FAE5A")
        ),
        HouseDef(
            4, "قصر النجوم", "قصر ذهبي تحت سماء الليل", 40,
            Color.parseColor("#3B2F7A"), Color.parseColor("#54449B"),
            Color.parseColor("#1A1450"), Color.parseColor("#4B3A99"), Color.parseColor("#FFC93C")
        ),
        HouseDef(
            5, "المنزل الثلجي", "بيت جليدي دافئ وسط الثلج", 30,
            Color.parseColor("#EAF4FF"), Color.parseColor("#D6E4F3"),
            Color.parseColor("#D8EBFF"), Color.parseColor("#FFFFFF"), Color.parseColor("#6FC3F5")
        )
    )

    fun get(id: Int): HouseDef = all.firstOrNull { it.id == id } ?: all[0]
}

/** Saved progress of the houses: which are open, which one is chosen, levels and materials. */
object HomeStore {
    private const val PREFS = "ghost_home"
    private const val KEY_SELECTED = "selected_house"
    private const val KEY_UNLOCKED = "unlocked_houses"
    private const val KEY_MATERIALS = "materials"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selected(context: Context): Int {
        val id = prefs(context).getInt(KEY_SELECTED, 0)
        return if (isUnlocked(context, id)) id else 0
    }

    fun select(context: Context, id: Int) {
        if (isUnlocked(context, id)) prefs(context).edit().putInt(KEY_SELECTED, id).apply()
    }

    private fun unlockedSet(context: Context): Set<Int> =
        prefs(context).getString(KEY_UNLOCKED, "").orEmpty()
            .split(',').mapNotNull { it.toIntOrNull() }.toSet()

    /** The first house is always open. */
    fun isUnlocked(context: Context, id: Int): Boolean = id == 0 || id in unlockedSet(context)

    /** Opens a house for good by spending its stars (it stays open without paying again). */
    fun unlock(context: Context, id: Int): Boolean {
        if (isUnlocked(context, id)) return true
        val def = HouseDefs.get(id)
        if (!Wardrobe.spendStars(context, def.starCost)) return false
        val set = unlockedSet(context) + id
        prefs(context).edit().putString(KEY_UNLOCKED, set.joinToString(",")).apply()
        return true
    }

    fun level(context: Context, id: Int): Int =
        prefs(context).getInt("level_$id", 1).coerceIn(1, 5)

    fun setLevel(context: Context, id: Int, level: Int) {
        prefs(context).edit().putInt("level_$id", level.coerceIn(1, 5)).apply()
    }

    fun materials(context: Context): Int = prefs(context).getInt(KEY_MATERIALS, 0)

    fun addMaterials(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_MATERIALS, materials(context) + count).apply()
    }

    /** Colour indexes of the babies of the family (they sleep in the beds of the house). */
    fun babyColorIndexes(context: Context): List<Int> {
        val p = context.applicationContext.getSharedPreferences("ghost_prefs", Context.MODE_PRIVATE)
        val n = p.getInt("baby_count", 0)
        val list = ArrayList<Int>()
        for (i in 1..n) list.add(p.getInt("b${i}_c", i - 1))
        return list
    }
}
