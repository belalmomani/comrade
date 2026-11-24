package com.cuteghost.app

import android.content.Context

/**
 * The sticker album: stickers the child collects by discovering things, grouped in five pages (animals, games,
 * seasons, family, discoveries). A sticker is only ever given once. Nothing in the album is a duty: a sticker that
 * is missing is just an empty place, and the album shows no counts of what is left.
 */
object Album {

    const val ANIMALS = 0
    const val GAMES = 1
    const val SEASONS = 2
    const val FAMILY = 3
    const val DISCOVERIES = 4
    const val CATEGORIES = 5

    class Sticker(val id: String, val category: Int)

    private val animalNames = arrayOf("cat", "duck", "cow", "dog", "sheep")

    val all: List<Sticker> = listOf(
        // animals: found by choosing the right animal three times in "whose voice is it?"
        Sticker("animal_cat", ANIMALS),
        Sticker("animal_duck", ANIMALS),
        Sticker("animal_cow", ANIMALS),
        Sticker("animal_dog", ANIMALS),
        Sticker("animal_sheep", ANIMALS),
        // games: the first finished round of each game
        Sticker("game_bubbles", GAMES),
        Sticker("game_ghost_bubbles", GAMES),
        Sticker("game_shapes", GAMES),
        Sticker("game_which", GAMES),
        Sticker("game_colors", GAMES),
        Sticker("game_twins", GAMES),
        Sticker("game_butterfly", GAMES),
        Sticker("game_ball", GAMES),
        Sticker("game_band", GAMES),
        Sticker("game_animals", GAMES),
        Sticker("game_counting", GAMES),
        Sticker("game_doctor", GAMES),
        Sticker("game_class", GAMES),
        // seasons: the first time each one is seen
        Sticker("season_winter", SEASONS),
        Sticker("season_spring", SEASONS),
        Sticker("season_summer", SEASONS),
        Sticker("season_autumn", SEASONS),
        Sticker("season_rain", SEASONS),
        // family
        Sticker("family_first_egg", FAMILY),
        Sticker("family_first_twins", FAMILY),
        Sticker("family_first_grown", FAMILY),
        // discoveries
        Sticker("treasure_chest", DISCOVERIES),
        Sticker("treasure_gem", DISCOVERIES),
        Sticker("treasure_star", DISCOVERIES),
        Sticker("discover_golden_bubble", DISCOVERIES),
        Sticker("discover_all_toys", DISCOVERIES),
        Sticker("civic_park", DISCOVERIES),
        Sticker("civic_toys", DISCOVERIES),
        Sticker("civic_school", DISCOVERIES),
        Sticker("civic_library", DISCOVERIES),
        Sticker("civic_hospital", DISCOVERIES)
    )

    private const val PREFS = "ghost_goals"
    private const val KEY_ALBUM = "album"
    private const val KEY_HITS = "animal_hits_"

    /** Right answers needed for an animal's sticker. */
    private const val ANIMAL_HITS = 3

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun stickers(category: Int): List<Sticker> = all.filter { it.category == category }

    fun has(context: Context, id: String): Boolean =
        prefs(context).getStringSet(KEY_ALBUM, emptySet())?.contains(id) == true

    fun count(context: Context, category: Int): Int {
        var n = 0
        for (s in all) if (s.category == category && has(context, s.id)) n++
        return n
    }

    /** Gives a sticker. Returns true only when it was new (an unknown id gives nothing). */
    fun unlock(context: Context, id: String): Boolean {
        if (all.none { it.id == id }) return false
        val p = prefs(context)
        val old = p.getStringSet(KEY_ALBUM, emptySet()) ?: emptySet()
        if (old.contains(id)) return false
        val set = HashSet<String>(old)
        set.add(id)
        p.edit().putStringSet(KEY_ALBUM, set).apply()
        return true
    }

    fun gameStickerId(game: Int): String = when (game) {
        MiniGames.GAME_BUBBLES -> "game_bubbles"
        MiniGames.GAME_SHAPES -> "game_shapes"
        MiniGames.GAME_COLORS -> "game_colors"
        MiniGames.GAME_COUNTING -> "game_counting"
        MiniGames.GAME_DOCTOR -> "game_doctor"
        MiniGames.GAME_CLASS -> "game_class"
        MiniGames.GAME_GHOST_BUBBLES -> "game_ghost_bubbles"
        MiniGames.GAME_BUTTERFLY -> "game_butterfly"
        MiniGames.GAME_BALL -> "game_ball"
        MiniGames.GAME_WHICH -> "game_which"
        MiniGames.GAME_TWINS -> "game_twins"
        MiniGames.GAME_BAND -> "game_band"
        MiniGames.GAME_ANIMALS -> "game_animals"
        else -> ""
    }

    /** The season shown by the weather layer: 0 winter, 1 spring, 2 summer, 3 autumn, 4 rain. */
    fun seasonStickerId(season: Int): String = when (season) {
        0 -> "season_winter"
        1 -> "season_spring"
        2 -> "season_summer"
        3 -> "season_autumn"
        else -> "season_rain"
    }

    /** The child chose this animal rightly. Its sticker comes after a few right answers. Returns true when it just did. */
    fun animalHit(context: Context, animal: Int): Boolean {
        if (animal < 0 || animal >= animalNames.size) return false
        val p = prefs(context)
        val key = KEY_HITS + animal
        val hits = p.getInt(key, 0) + 1
        p.edit().putInt(key, hits).apply()
        return hits >= ANIMAL_HITS && unlock(context, "animal_" + animalNames[animal])
    }
}
