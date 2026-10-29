package com.cuteghost.app

import android.content.Context
import android.graphics.Color

/** Where in the room an item goes. */
enum class FSlot {
    BED, COVER, PILLOW, RUG, SOFA, TABLE, LAMP, PICTURE, CLOCK, PLANT, SHELF, CLOSET,
    FLOOR, WALL, WINDOW, CURTAIN, MAGIC, GHOST_HAT
}

enum class FCat { BEDROOM, LIVING, DECOR, SURFACES, WINDOWS, MAGIC, GHOST }

/** How an item is obtained: free, with stars, or as a (planned) optional ad reward. */
enum class FSource { FREE, STARS, AD }

/**
 * One piece of furniture or decoration. [style] and the two colours drive its drawing.
 * [stars] is the price in stars (for ad items it is the price when no ad is watched).
 */
class Furn(
    val id: String,
    val cat: FCat,
    val slot: FSlot,
    val name: String,
    val source: FSource,
    val stars: Int,
    val style: Int,
    val c1: Int,
    val c2: Int
)

object Furniture {
    private fun c(s: String): Int = Color.parseColor(s)

    val items: List<Furn> = listOf(
        // Bedroom
        Furn("bed_cloud", FCat.BEDROOM, FSlot.BED, "سرير السحاب", FSource.FREE, 0, 0, c("#E4DAFF"), c("#B9A8F5")),
        Furn("bed_wood", FCat.BEDROOM, FSlot.BED, "سرير خشبي", FSource.STARS, 8, 1, c("#B98A5E"), c("#8B5E3C")),
        Furn("bed_star", FCat.BEDROOM, FSlot.BED, "سرير النجوم", FSource.AD, 14, 2, c("#7C5CFF"), c("#FFC93C")),
        Furn("bed_moon", FCat.BEDROOM, FSlot.BED, "سرير القمر", FSource.STARS, 12, 3, c("#5B8DEF"), c("#FFE27A")),
        Furn("cover_pink", FCat.BEDROOM, FSlot.COVER, "غطاء وردي", FSource.FREE, 0, 0, c("#FFB6D0"), c("#FFFFFF")),
        Furn("cover_blue", FCat.BEDROOM, FSlot.COVER, "غطاء أزرق", FSource.STARS, 4, 0, c("#9CC7FF"), c("#FFFFFF")),
        Furn("cover_stars", FCat.BEDROOM, FSlot.COVER, "غطاء النجوم", FSource.AD, 8, 1, c("#7C5CFF"), c("#FFE27A")),
        Furn("cover_rainbow", FCat.BEDROOM, FSlot.COVER, "غطاء قوس قزح", FSource.STARS, 10, 2, c("#FFFFFF"), c("#FFFFFF")),
        Furn("pillow_round", FCat.BEDROOM, FSlot.PILLOW, "وسادة ناعمة", FSource.FREE, 0, 0, c("#FFD6E8"), c("#FFFFFF")),
        Furn("pillow_heart", FCat.BEDROOM, FSlot.PILLOW, "وسادة قلب", FSource.STARS, 4, 1, c("#FF8FB8"), c("#FFFFFF")),
        Furn("pillow_star", FCat.BEDROOM, FSlot.PILLOW, "وسادة نجمة", FSource.AD, 6, 2, c("#FFD54A"), c("#FFFFFF")),
        Furn("closet_small", FCat.BEDROOM, FSlot.CLOSET, "خزانة صغيرة", FSource.STARS, 8, 0, c("#D9B88F"), c("#B98A5E")),
        // Living room
        Furn("sofa_cream", FCat.LIVING, FSlot.SOFA, "كنب كريمي", FSource.FREE, 0, 0, c("#F3E3C8"), c("#FFD6E8")),
        Furn("sofa_purple", FCat.LIVING, FSlot.SOFA, "كنب بنفسجي", FSource.AD, 12, 0, c("#A98BFF"), c("#FFE27A")),
        Furn("sofa_mint", FCat.LIVING, FSlot.SOFA, "كنب نعناعي", FSource.STARS, 9, 0, c("#62D6A9"), c("#FFFFFF")),
        Furn("table_round", FCat.LIVING, FSlot.TABLE, "طاولة صغيرة", FSource.FREE, 0, 0, c("#D9B88F"), c("#FFFFFF")),
        Furn("table_star", FCat.LIVING, FSlot.TABLE, "طاولة النجوم", FSource.STARS, 6, 1, c("#7C5CFF"), c("#FFE27A")),
        Furn("rug_round", FCat.LIVING, FSlot.RUG, "سجادة دائرية", FSource.FREE, 0, 0, c("#D9CCFF"), c("#B9A8F5")),
        Furn("rug_stripes", FCat.LIVING, FSlot.RUG, "سجادة مخططة", FSource.STARS, 5, 1, c("#FFC9A8"), c("#FFFFFF")),
        Furn("rug_rainbow", FCat.LIVING, FSlot.RUG, "سجادة قوس قزح", FSource.AD, 8, 2, c("#FFFFFF"), c("#FFFFFF")),
        // Decor
        Furn("lamp_moon", FCat.DECOR, FSlot.LAMP, "مصباح القمر", FSource.FREE, 0, 0, c("#FFE680"), c("#FFE27A")),
        Furn("lamp_star", FCat.DECOR, FSlot.LAMP, "مصباح النجمة", FSource.AD, 8, 1, c("#FFD54A"), c("#FFC93C")),
        Furn("lamp_mushroom", FCat.DECOR, FSlot.LAMP, "مصباح الفطر", FSource.STARS, 6, 2, c("#FF9EB5"), c("#E5484D")),
        Furn("picture_cloud", FCat.DECOR, FSlot.PICTURE, "لوحة السحاب", FSource.FREE, 0, 0, c("#BFD9FF"), c("#FFFFFF")),
        Furn("picture_star", FCat.DECOR, FSlot.PICTURE, "لوحة النجوم", FSource.STARS, 4, 1, c("#5B3FD6"), c("#FFE27A")),
        Furn("clock_round", FCat.DECOR, FSlot.CLOCK, "ساعة الحائط", FSource.STARS, 3, 0, c("#FFFFFF"), c("#7C5CFF")),
        Furn("plant_small", FCat.DECOR, FSlot.PLANT, "نبتة صغيرة", FSource.FREE, 0, 0, c("#5FC577"), c("#C97B4A")),
        Furn("plant_flower", FCat.DECOR, FSlot.PLANT, "نبتة بزهرة", FSource.STARS, 5, 1, c("#5FC577"), c("#FF8FB8")),
        Furn("shelf_small", FCat.DECOR, FSlot.SHELF, "رف صغير", FSource.STARS, 5, 0, c("#B98A5E"), c("#FF8FB8")),
        // Floors and walls
        Furn("floor_wood", FCat.SURFACES, FSlot.FLOOR, "أرضية خشبية", FSource.FREE, 0, 0, c("#D9B88F"), c("#B98A5E")),
        Furn("floor_pink", FCat.SURFACES, FSlot.FLOOR, "أرضية وردية", FSource.STARS, 3, 1, c("#FFD6E8"), c("#FFF0F6")),
        Furn("floor_cloud", FCat.SURFACES, FSlot.FLOOR, "أرضية السحاب", FSource.AD, 6, 2, c("#CFE3FF"), c("#FFFFFF")),
        Furn("wall_lavender", FCat.SURFACES, FSlot.WALL, "جدار بنفسجي", FSource.FREE, 0, 0, c("#F1E9FF"), c("#FFFFFF")),
        Furn("wall_mint", FCat.SURFACES, FSlot.WALL, "جدار نعناعي", FSource.STARS, 3, 0, c("#DDF7EA"), c("#FFFFFF")),
        Furn("wall_peach", FCat.SURFACES, FSlot.WALL, "جدار خوخي", FSource.STARS, 3, 0, c("#FFE5D6"), c("#FFFFFF")),
        Furn("wall_stars", FCat.SURFACES, FSlot.WALL, "جدار النجوم", FSource.AD, 6, 1, c("#3B2F7A"), c("#FFE27A")),
        // Windows and curtains
        Furn("win_round", FCat.WINDOWS, FSlot.WINDOW, "نافذة دائرية", FSource.FREE, 0, 0, c("#7C5CFF"), c("#FFFFFF")),
        Furn("win_magic", FCat.WINDOWS, FSlot.WINDOW, "نافذة سحرية", FSource.AD, 10, 1, c("#FF8FB8"), c("#FFE27A")),
        Furn("curtain_blue", FCat.WINDOWS, FSlot.CURTAIN, "ستائر زرقاء", FSource.FREE, 0, 0, c("#9CC7FF"), c("#FFFFFF")),
        Furn("curtain_pink", FCat.WINDOWS, FSlot.CURTAIN, "ستائر وردية", FSource.STARS, 3, 0, c("#FFB6D0"), c("#FFFFFF")),
        Furn("curtain_stars", FCat.WINDOWS, FSlot.CURTAIN, "ستائر النجوم", FSource.AD, 6, 1, c("#7C5CFF"), c("#FFE27A")),
        // Magic decorations
        Furn("magic_stars", FCat.MAGIC, FSlot.MAGIC, "نجوم مضيئة", FSource.STARS, 6, 0, c("#FFE27A"), c("#FFFFFF")),
        Furn("magic_crystals", FCat.MAGIC, FSlot.MAGIC, "بلورات", FSource.AD, 10, 1, c("#8FD0FF"), c("#FFFFFF")),
        Furn("magic_clouds", FCat.MAGIC, FSlot.MAGIC, "سحب صغيرة", FSource.STARS, 5, 2, c("#FFFFFF"), c("#D9CCFF")),
        Furn("magic_fireflies", FCat.MAGIC, FSlot.MAGIC, "فراشات ضوئية", FSource.AD, 8, 3, c("#FFF3B0"), c("#FFE27A")),
        // Ghost accessories (worn by the sleeping ghosts)
        Furn("cap_blue", FCat.GHOST, FSlot.GHOST_HAT, "قبعة نوم زرقاء", FSource.FREE, 0, 0, c("#5B8DEF"), c("#FFFFFF")),
        Furn("cap_pink", FCat.GHOST, FSlot.GHOST_HAT, "قبعة نوم وردية", FSource.STARS, 3, 1, c("#FF8FB8"), c("#FFFFFF")),
        Furn("cap_star", FCat.GHOST, FSlot.GHOST_HAT, "قبعة نوم بنجمة", FSource.AD, 6, 2, c("#7C5CFF"), c("#FFE27A"))
    )

    fun byId(id: String): Furn? = items.firstOrNull { it.id == id }

    fun inCategory(cat: FCat): List<Furn> = items.filter { it.cat == cat }

    fun catName(cat: FCat): String = when (cat) {
        FCat.BEDROOM -> "غرفة النوم"
        FCat.LIVING -> "المعيشة"
        FCat.DECOR -> "الديكور"
        FCat.SURFACES -> "الأرضيات والجدران"
        FCat.WINDOWS -> "النوافذ والستائر"
        FCat.MAGIC -> "الزينة السحرية"
        FCat.GHOST -> "إكسسوارات الأشباح"
    }

    /** The bed and the window cannot be removed; everything else can. */
    fun isRemovable(slot: FSlot): Boolean = slot != FSlot.BED && slot != FSlot.WINDOW

    /** What a new house starts with. */
    val defaults: Map<FSlot, String> = mapOf(
        FSlot.BED to "bed_cloud",
        FSlot.COVER to "cover_pink",
        FSlot.PILLOW to "pillow_round",
        FSlot.RUG to "rug_round",
        FSlot.LAMP to "lamp_moon",
        FSlot.PICTURE to "picture_cloud",
        FSlot.CURTAIN to "curtain_blue",
        FSlot.WINDOW to "win_round"
    )
}

/** What is shown in the room right now, including an item that is only being previewed. */
class SceneState(var placed: Map<FSlot, String>) {
    var previewSlot: FSlot? = null
    var previewItem: String? = null
    var celebrateUntil = 0L

    fun itemFor(slot: FSlot): Furn? {
        val id = if (previewSlot == slot) previewItem else placed[slot]
        return if (id == null) null else Furniture.byId(id)
    }
}

/** Saved inventory and placement of the furniture of every house. */
object FurnStore {
    private const val PREFS = "ghost_home"
    private const val KEY_OWNED = "owned_items"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ownedSet(context: Context): Set<String> =
        prefs(context).getString(KEY_OWNED, "").orEmpty().split(',').filter { it.isNotEmpty() }.toSet()

    /** Free items are available from the start; the others once they were bought or won. */
    fun isOwned(context: Context, item: Furn): Boolean =
        item.source == FSource.FREE || item.id in ownedSet(context)

    /** Gives the item to the player (for example after a rewarded ad). */
    fun own(context: Context, id: String) {
        val set = ownedSet(context) + id
        prefs(context).edit().putString(KEY_OWNED, set.joinToString(",")).apply()
    }

    /** Buys an item with stars. It then belongs to the player for good. */
    fun buy(context: Context, item: Furn): Boolean {
        if (isOwned(context, item)) return true
        if (!Wardrobe.spendStars(context, item.stars)) return false
        own(context, item.id)
        return true
    }

    fun placed(context: Context, houseId: Int): Map<FSlot, String> {
        val raw = prefs(context).getString("placed_$houseId", null) ?: return Furniture.defaults
        val map = LinkedHashMap<FSlot, String>()
        for (part in raw.split(';')) {
            val kv = part.split('=')
            if (kv.size != 2) continue
            val slot = try {
                FSlot.valueOf(kv[0])
            } catch (e: IllegalArgumentException) {
                continue
            }
            if (Furniture.byId(kv[1]) != null) map[slot] = kv[1]
        }
        return map
    }

    /** Puts [itemId] in its slot of the house, or empties the slot when it is null. */
    fun place(context: Context, houseId: Int, slot: FSlot, itemId: String?) {
        val map = LinkedHashMap(placed(context, houseId))
        if (itemId == null) map.remove(slot) else map[slot] = itemId
        prefs(context).edit()
            .putString("placed_$houseId", map.entries.joinToString(";") { it.key.name + "=" + it.value })
            .apply()
    }
}
