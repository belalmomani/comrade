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

    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences("ghost_prefs", Context.MODE_PRIVATE).getBoolean("sound_on", true)

    fun setEnabled(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences("ghost_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("sound_on", on).apply()
        if (!on) stopLullaby()
    }

    private fun gapMs(s: Snd): Long = when (s) {
        Snd.GIGGLE -> 2200L
        Snd.BOING -> 350L
        Snd.CRY -> 4000L
        Snd.CHEW -> 280L
        Snd.GULP -> 500L
        Snd.SPLASH -> 400L
        Snd.SCRUB, Snd.BRUSH -> 220L
        Snd.SPARKLE -> 700L
        Snd.CHING, Snd.POP -> 120L
        Snd.HONK -> 8_000L
        Snd.SIREN -> 30_000L
        Snd.CHIRP -> 6_000L
        Snd.WHISTLE -> 2_500L
        Snd.GRUMBLE -> 3_000L
        Snd.LULLABY -> 60_000L
    }

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

    /** Plays a sound once (quiet, and not again too soon). */
    fun play(context: Context, s: Snd, force: Boolean = false) {
        if (!enabled(context) || active >= 8) return
        val now = System.currentTimeMillis()
        if (!force && now - (lastAt[s] ?: 0L) < gapMs(s)) return
        lastAt[s] = now
        val pcm = data(s)
        val t = track(context, pcm) ?: return
        try {
            t.setVolume(if (s == Snd.LULLABY) 0.45f else 0.8f)
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
            t.setVolume(0.28f)
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
