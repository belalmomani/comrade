package com.cuteghost.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class Snd {
    GIGGLE, BOING, CRY, CHEW, GULP, SPLASH, SCRUB, BRUSH, SPARKLE, CHING, POP, HONK, SIREN, CHIRP, WHISTLE, GRUMBLE, LULLABY
}

/**
 * Soft sounds made in code (no sound files): giggles, chewing, splashes, coins, birds, a music-box lullaby...
 * They are quiet, spaced out so that nothing repeats too often, and the grown-ups can switch them off.
 */
object Sounds {
    private const val RATE = 22050
    private val handler = Handler(Looper.getMainLooper())
    private val cache = HashMap<Snd, ShortArray>()
    private val lastAt = HashMap<Snd, Long>()
    private var lullaby: AudioTrack? = null
    private var active = 0

    const val LEVEL_OFF = 0
    const val LEVEL_VERY_QUIET = 1
    const val LEVEL_QUIET = 2

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("ghost_prefs", Context.MODE_PRIVATE)

    /** 0 = off, 1 = very quiet, 2 = quiet. Before this setting existed: on meant quiet. */
    fun level(context: Context): Int {
        val p = prefs(context)
        return if (p.contains("sound_level")) {
            p.getInt("sound_level", LEVEL_QUIET).coerceIn(LEVEL_OFF, LEVEL_QUIET)
        } else if (p.getBoolean("sound_on", true)) {
            LEVEL_QUIET
        } else {
            LEVEL_OFF
        }
    }

    fun setLevel(context: Context, level: Int) {
        val l = level.coerceIn(LEVEL_OFF, LEVEL_QUIET)
        prefs(context).edit().putInt("sound_level", l).putBoolean("sound_on", l > LEVEL_OFF).apply()
        if (l == LEVEL_OFF) stopLullaby()
    }

    fun enabled(context: Context): Boolean = level(context) > LEVEL_OFF

    fun setEnabled(context: Context, on: Boolean) {
        if (!on) setLevel(context, LEVEL_OFF) else if (level(context) == LEVEL_OFF) setLevel(context, LEVEL_QUIET)
    }

    /** Spaced far apart on purpose: a sound is a small surprise, never a constant noise. */
    private fun gapMs(s: Snd): Long = when (s) {
        Snd.GIGGLE -> 30_000L
        Snd.BOING -> 30_000L
        Snd.CRY -> 60_000L
        Snd.CHEW -> 900L
        Snd.GULP -> 1_500L
        Snd.SPLASH -> 1_500L
        Snd.SCRUB, Snd.BRUSH -> 1_000L
        Snd.SPARKLE -> 2_500L
        Snd.CHING, Snd.POP -> 1_500L
        Snd.HONK -> 120_000L
        Snd.SIREN -> 300_000L
        Snd.CHIRP -> 45_000L
        Snd.WHISTLE -> 30_000L
        Snd.GRUMBLE -> 30_000L
        Snd.LULLABY -> 120_000L
    }

    /** Sounds that nobody asked for (the ghost hopping, birds, cars...). They also share one long quiet gap. */
    private fun ambient(s: Snd): Boolean = when (s) {
        Snd.GIGGLE, Snd.BOING, Snd.CRY, Snd.CHIRP, Snd.HONK, Snd.SIREN, Snd.GRUMBLE, Snd.WHISTLE -> true
        else -> false
    }

    private const val AMBIENT_GAP_MS = 20_000L
    private const val FORCED_MIN_GAP_MS = 2_000L
    private var lastAmbientAt = 0L

    // ---- making the sounds ----

    private fun tone(b: FloatArray, startMs: Int, durMs: Int, f0: Float, f1: Float, vol: Float, vib: Float = 0f) {
        val start = RATE * startMs / 1000
        val n = RATE * durMs / 1000
        var ph = 0.0
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= b.size) break
            var f = f0 + (f1 - f0) * (i / n.toFloat())
            if (vib > 0f) f += sin(i * 2.0 * PI * 6.0 / RATE).toFloat() * vib
            ph += 2.0 * PI * f / RATE
            val env = minOf(1f, i / (0.06f * n), (n - i) / (0.35f * n))
            b[idx] += (sin(ph) * vol * env).toFloat()
        }
    }

    private fun noise(b: FloatArray, startMs: Int, durMs: Int, vol: Float, smooth: Float, am: Float = 0f) {
        val start = RATE * startMs / 1000
        val n = RATE * durMs / 1000
        var last = 0f
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= b.size) break
            last += ((Random.nextFloat() * 2f - 1f) - last) * smooth
            var env = minOf(1f, i / (0.05f * n), (n - i) / (0.4f * n))
            if (am > 0f) env *= 0.55f + 0.45f * sin(i * 2.0 * PI * am / RATE).toFloat()
            b[idx] += last * vol * env
        }
    }

    /** A music-box note: a clear tone and a soft overtone that fade away. */
    private fun boxNote(b: FloatArray, startMs: Int, durMs: Int, f: Float, vol: Float) {
        val start = RATE * startMs / 1000
        val n = RATE * durMs / 1000
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= b.size) break
            val t = i / RATE.toFloat()
            val env = kotlin.math.exp(-t * 3.2f) * minOf(1f, i / (0.01f * RATE))
            val s = sin(2.0 * PI * f * t) + 0.35 * sin(2.0 * PI * f * 3.0 * t)
            b[idx] += (s * vol * env).toFloat()
        }
    }

    private fun make(s: Snd): ShortArray {
        val ms = when (s) {
            Snd.GIGGLE -> 700
            Snd.BOING -> 320
            Snd.CRY -> 900
            Snd.CHEW -> 380
            Snd.GULP -> 420
            Snd.SPLASH -> 500
            Snd.SCRUB, Snd.BRUSH -> 260
            Snd.SPARKLE -> 500
            Snd.CHING -> 450
            Snd.POP -> 150
            Snd.HONK -> 320
            Snd.SIREN -> 2400
            Snd.CHIRP -> 420
            Snd.WHISTLE -> 600
            Snd.GRUMBLE -> 380
            Snd.LULLABY -> 6800
        }
        val b = FloatArray(RATE * ms / 1000)
        when (s) {
            Snd.GIGGLE -> for (k in 0 until 5) tone(b, k * 110, 85, 620f + k * 45f, 820f + k * 55f, 0.45f)
            Snd.BOING -> tone(b, 0, 300, 180f, 520f, 0.5f, 18f)
            Snd.CRY -> {
                tone(b, 0, 420, 760f, 520f, 0.5f, 22f)
                tone(b, 460, 420, 740f, 480f, 0.5f, 22f)
            }
            Snd.CHEW -> for (k in 0 until 2) {
                noise(b, k * 170, 90, 0.7f, 0.18f)
                tone(b, k * 170, 70, 140f, 90f, 0.3f)
            }
            Snd.GULP -> {
                tone(b, 0, 130, 170f, 85f, 0.55f)
                tone(b, 200, 150, 150f, 75f, 0.55f)
            }
            Snd.SPLASH -> {
                noise(b, 0, 420, 0.5f, 0.35f)
                for (k in 0 until 4) tone(b, 60 + k * 90, 70, 400f + k * 120f, 700f + k * 90f, 0.18f)
            }
            Snd.SCRUB -> noise(b, 0, 240, 0.6f, 0.5f, 22f)
            Snd.BRUSH -> noise(b, 0, 240, 0.5f, 0.7f, 26f)
            Snd.SPARKLE -> {
                val notes = floatArrayOf(1047f, 1319f, 1568f, 2093f)
                for (k in notes.indices) tone(b, k * 90, 140, notes[k], notes[k], 0.3f)
            }
            Snd.CHING -> {
                tone(b, 0, 160, 2093f, 2093f, 0.35f)
                tone(b, 110, 300, 3136f, 3136f, 0.3f)
            }
            Snd.POP -> {
                tone(b, 0, 90, 700f, 180f, 0.55f)
                noise(b, 0, 40, 0.25f, 0.6f)
            }
            Snd.HONK -> {
                tone(b, 0, 300, 420f, 420f, 0.28f)
                tone(b, 0, 300, 530f, 530f, 0.28f)
            }
            Snd.SIREN -> for (cycle in 0 until 2) {
                tone(b, cycle * 1200, 600, 720f, 1150f, 0.3f)
                tone(b, cycle * 1200 + 600, 600, 1150f, 720f, 0.3f)
            }
            Snd.CHIRP -> for (k in 0 until 3) tone(b, k * 130, 80, 3200f + Random.nextFloat() * 400f, 4300f, 0.2f)
            Snd.WHISTLE -> {
                tone(b, 0, 260, 2300f, 2700f, 0.3f, 60f)
                tone(b, 300, 260, 2500f, 2300f, 0.3f, 60f)
            }
            Snd.GRUMBLE -> tone(b, 0, 340, 115f, 80f, 0.5f, 8f)
            Snd.LULLABY -> {
                // "Twinkle, twinkle" on a music box: C C G G A A G, F F E E D D C
                val c5 = 523f; val d5 = 587f; val e5 = 659f; val f5 = 698f; val g5 = 784f; val a5 = 880f
                val notes = floatArrayOf(c5, c5, g5, g5, a5, a5, g5, f5, f5, e5, e5, d5, d5, c5)
                val beats = intArrayOf(1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 2)
                var t = 0
                for (k in notes.indices) {
                    boxNote(b, t, 900, notes[k], 0.35f)
                    t += 420 * beats[k]
                }
            }
        }
        return ShortArray(b.size) { (b[it].coerceIn(-1f, 1f) * 28000f).toInt().toShort() }
    }

    private fun data(s: Snd): ShortArray = cache.getOrPut(s) { make(s) }

    // ---- the band: instruments that can be played again and again ----

    const val INSTR_BIG_DRUM = 0
    const val INSTR_SMALL_DRUM = 1
    const val INSTR_BELL = 2
    const val INSTR_XYLO = 3

    private val xyloNotes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f)
    private val instrCache = HashMap<Int, ShortArray>()
    private val instrLast = HashMap<Int, Long>()
    private var instrActive = 0

    /** A drum hit: a sine that drops in pitch and dies away fast. */
    private fun hit(b: FloatArray, startMs: Int, durMs: Int, f0: Float, f1: Float, vol: Float, decay: Float) {
        val start = RATE * startMs / 1000
        val n = RATE * durMs / 1000
        var ph = 0.0
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= b.size) break
            val t = i / RATE.toFloat()
            val f = f0 + (f1 - f0) * minOf(1f, i / (0.25f * n))
            ph += 2.0 * PI * f / RATE
            val env = kotlin.math.exp(-t * decay) * minOf(1f, i / (0.002f * RATE))
            b[idx] += (sin(ph) * vol * env).toFloat()
        }
    }

    /** A short burst of noise that dies away fast: the skin of a drum being struck. */
    private fun snap(b: FloatArray, startMs: Int, durMs: Int, vol: Float, decay: Float) {
        val start = RATE * startMs / 1000
        val n = RATE * durMs / 1000
        var last = 0f
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= b.size) break
            val t = i / RATE.toFloat()
            last += ((Random.nextFloat() * 2f - 1f) - last) * 0.7f
            b[idx] += last * vol * kotlin.math.exp(-t * decay)
        }
    }

    /** One pure tone that fades away (the parts of a bell, or a xylophone bar). */
    private fun partial(b: FloatArray, startMs: Int, durMs: Int, f: Float, vol: Float, decay: Float) {
        val start = RATE * startMs / 1000
        val n = RATE * durMs / 1000
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= b.size) break
            val t = i / RATE.toFloat()
            val env = kotlin.math.exp(-t * decay) * minOf(1f, i / (0.003f * RATE))
            b[idx] += (sin(2.0 * PI * f * t) * vol * env).toFloat()
        }
    }

    private fun makeInstrument(kind: Int, note: Int): ShortArray {
        val ms = when (kind) {
            INSTR_BIG_DRUM -> 600
            INSTR_SMALL_DRUM -> 320
            INSTR_BELL -> 1400
            else -> 800
        }
        val b = FloatArray(RATE * ms / 1000)
        when (kind) {
            INSTR_BIG_DRUM -> {
                hit(b, 0, 560, 130f, 50f, 0.9f, 6f)
                snap(b, 0, 40, 0.25f, 60f)
            }
            INSTR_SMALL_DRUM -> {
                hit(b, 0, 260, 280f, 180f, 0.55f, 14f)
                snap(b, 0, 120, 0.45f, 28f)
            }
            INSTR_BELL -> {
                partial(b, 0, 1400, 880f, 0.5f, 3f)
                partial(b, 0, 1000, 1760f, 0.3f, 4f)
                partial(b, 0, 800, 2429f, 0.22f, 5f)
                partial(b, 0, 500, 4752f, 0.12f, 8f)
            }
            else -> {
                val f = xyloNotes[note.coerceIn(0, xyloNotes.size - 1)]
                partial(b, 0, 700, f, 0.7f, 7f)
                partial(b, 0, 300, f * 3f, 0.25f, 16f)
            }
        }
        return ShortArray(b.size) { (b[it].coerceIn(-1f, 1f) * 28000f).toInt().toShort() }
    }

    // ---- animal voices, made in code (no recordings, no microphone, no internet) ----

    const val ANIMAL_CAT = 0
    const val ANIMAL_DUCK = 1
    const val ANIMAL_COW = 2
    const val ANIMAL_DOG = 3
    const val ANIMAL_SHEEP = 4

    private val animalCache = HashMap<Int, ShortArray>()
    private var animalActive = 0

    /** A voice: a tone with harmonics whose pitch glides, with an optional wobble (vibrato) and bleat (tremolo). */
    private fun voice(
        b: FloatArray, startMs: Int, durMs: Int, f0: Float, f1: Float, vol: Float,
        vibDepth: Float = 0f, vibHz: Float = 5f, tremHz: Float = 0f, tremDepth: Float = 0f
    ) {
        val start = RATE * startMs / 1000
        val n = RATE * durMs / 1000
        var ph = 0.0
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= b.size) break
            val k = i / n.toFloat()
            var f = f0 + (f1 - f0) * k
            if (vibDepth > 0f) f += sin(i * 2.0 * PI * vibHz / RATE).toFloat() * vibDepth
            ph += 2.0 * PI * f / RATE
            var env = minOf(1f, i / (0.12f * n), (n - i) / (0.3f * n))
            if (tremDepth > 0f) env *= 1f - tremDepth + tremDepth * (0.5f + 0.5f * sin(i * 2.0 * PI * tremHz / RATE).toFloat())
            val s = sin(ph) + 0.6 * sin(2.0 * ph) + 0.4 * sin(3.0 * ph) + 0.2 * sin(4.0 * ph)
            b[idx] += (s * vol * env * 0.45).toFloat()
        }
    }

    /** A nasal buzz (a saw wave made softer): the duck. */
    private fun buzz(b: FloatArray, startMs: Int, durMs: Int, f0: Float, f1: Float, vol: Float) {
        val start = RATE * startMs / 1000
        val n = RATE * durMs / 1000
        var ph = 0.0
        var last = 0f
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= b.size) break
            val k = i / n.toFloat()
            ph += (f0 + (f1 - f0) * k) / RATE
            if (ph >= 1.0) ph -= 1.0
            val saw = (2.0 * ph - 1.0).toFloat()
            last += (saw - last) * 0.3f
            val env = minOf(1f, i / (0.05f * n), (n - i) / (0.5f * n))
            b[idx] += last * vol * env
        }
    }

    private fun makeAnimal(kind: Int): ShortArray {
        val ms = when (kind) {
            ANIMAL_CAT -> 750
            ANIMAL_DUCK -> 700
            ANIMAL_COW -> 1300
            ANIMAL_DOG -> 600
            else -> 900
        }
        val b = FloatArray(RATE * ms / 1000)
        when (kind) {
            ANIMAL_CAT -> { // meow: up, then down
                voice(b, 0, 200, 450f, 850f, 0.8f)
                voice(b, 180, 450, 850f, 520f, 0.8f, 12f, 7f)
            }
            ANIMAL_DUCK -> { // quack quack
                buzz(b, 0, 160, 360f, 300f, 0.7f)
                buzz(b, 330, 160, 350f, 290f, 0.7f)
            }
            ANIMAL_COW -> voice(b, 0, 1200, 150f, 105f, 0.9f, 4f, 4f) // a long, low moo
            ANIMAL_DOG -> { // woof woof
                voice(b, 0, 170, 260f, 160f, 0.8f)
                snap(b, 0, 50, 0.25f, 50f)
                voice(b, 280, 170, 270f, 160f, 0.8f)
                snap(b, 280, 50, 0.25f, 50f)
            }
            else -> voice(b, 0, 800, 380f, 310f, 0.8f, 10f, 6f, 28f, 0.55f) // baa, shaking
        }
        var peak = 0f
        for (v in b) peak = maxOf(peak, kotlin.math.abs(v))
        val gain = if (peak > 0.9f) 0.9f / peak else 1f
        return ShortArray(b.size) { (b[it] * gain).coerceIn(-1f, 1f).times(28000f).toInt().toShort() }
    }

    /** The voice of an animal (ANIMAL_CAT and so on). It follows the sound level; with sound off it is silent. */
    fun playAnimal(context: Context, kind: Int) {
        val lvl = level(context)
        if (lvl == LEVEL_OFF || animalActive >= 2) return
        val pcm = animalCache.getOrPut(kind) { makeAnimal(kind) }
        val t = track(context, pcm) ?: return
        try {
            t.setVolume(if (lvl == LEVEL_VERY_QUIET) 0.22f else 0.55f)
            t.play()
            animalActive++
            handler.postDelayed({
                try {
                    t.stop()
                } catch (e: Exception) {
                    // already stopped
                }
                t.release()
                animalActive--
            }, pcm.size * 1000L / RATE + 250L)
        } catch (e: Exception) {
            t.release()
        }
    }

    /**
     * Plays one note of the band: a big drum, a small drum, the bell or a xylophone bar (note 0 to 3).
     * Unlike the other sounds it may be played as often as the child touches, but it follows the sound level.
     */
    fun playInstrument(context: Context, kind: Int, note: Int = 0) {
        val lvl = level(context)
        if (lvl == LEVEL_OFF || instrActive >= 6) return
        val key = kind * 10 + note
        val now = System.currentTimeMillis()
        if (now - (instrLast[key] ?: 0L) < 70L) return
        instrLast[key] = now
        val pcm = instrCache.getOrPut(key) { makeInstrument(kind, note) }
        val t = track(context, pcm) ?: return
        try {
            t.setVolume(if (lvl == LEVEL_VERY_QUIET) 0.22f else 0.55f)
            t.play()
            instrActive++
            handler.postDelayed({
                try {
                    t.stop()
                } catch (e: Exception) {
                    // already stopped
                }
                t.release()
                instrActive--
            }, pcm.size * 1000L / RATE + 250L)
        } catch (e: Exception) {
            t.release()
        }
    }

    private fun track(context: Context, pcm: ShortArray): AudioTrack? {
        return try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val t = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(pcm, 0, pcm.size)
            t
        } catch (e: Exception) {
            null
        }
    }

    /** Plays a sound once (very quiet, and not again too soon). */
    fun play(context: Context, s: Snd, force: Boolean = false) {
        if (!enabled(context) || active >= 3) return
        val now = System.currentTimeMillis()
        val last = lastAt[s] ?: 0L
        if (force) {
            if (now - last < FORCED_MIN_GAP_MS) return
        } else {
            if (now - last < gapMs(s)) return
            if (ambient(s) && now - lastAmbientAt < AMBIENT_GAP_MS) return
        }
        if (ambient(s)) lastAmbientAt = now
        lastAt[s] = now
        val pcm = data(s)
        val t = track(context, pcm) ?: return
        try {
            val veryQuiet = level(context) == LEVEL_VERY_QUIET
            t.setVolume(if (s == Snd.LULLABY) (if (veryQuiet) 0.07f else 0.15f) else (if (veryQuiet) 0.12f else 0.3f))
            t.play()
            active++
            handler.postDelayed({
                try {
                    t.stop()
                } catch (e: Exception) {
                    // already stopped
                }
                t.release()
                active--
            }, pcm.size * 1000L / RATE + 250L)
        } catch (e: Exception) {
            t.release()
        }
    }

    /** The music-box lullaby in a loop, very softly, while the ghosts sleep in their room. */
    fun startLullaby(context: Context) {
        if (!enabled(context) || lullaby != null) return
        val pcm = data(Snd.LULLABY)
        val t = track(context, pcm) ?: return
        try {
            t.setLoopPoints(0, pcm.size, -1)
            t.setVolume(if (level(context) == LEVEL_VERY_QUIET) 0.06f else 0.12f)
            t.play()
            lullaby = t
        } catch (e: Exception) {
            t.release()
        }
    }

    fun stopLullaby() {
        val t = lullaby ?: return
        lullaby = null
        try {
            t.stop()
        } catch (e: Exception) {
            // already stopped
        }
        t.release()
    }
}
