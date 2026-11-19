package com.thriple.define.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sound effects synthesized in code (no audio assets), written once to WAV files in the cache and
 * played through SoundPool. Playback rate doubles as pitch, which drives the rising rep combo.
 */
object Sfx {
    private const val RATE = 44100
    private var pool: SoundPool? = null
    private val ids = mutableMapOf<String, Int>()
    var enabled = true

    fun init(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
        val dir = File(context.cacheDir, "sfx_v1").apply { mkdirs() }
        for ((name, gen) in generators) {
            val f = File(dir, "$name.wav")
            if (!f.exists()) writeWav(f, gen())
            ids[name] = p.load(f.path, 1)
        }
        pool = p
    }

    fun play(name: String, rate: Float = 1f, volume: Float = 0.9f) {
        if (!enabled) return
        val id = ids[name] ?: return
        pool?.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    private fun samples(seconds: Double) = FloatArray((seconds * RATE).toInt())

    private fun tone(out: FloatArray, from: Int, len: Int, f0: Double, f1: Double, amp: Double, decay: Double, harm: Double = 0.0) {
        var phase = 0.0
        for (i in 0 until len) {
            val idx = from + i
            if (idx >= out.size) break
            val t = i.toDouble() / len
            val f = f0 + (f1 - f0) * t
            phase += 2 * PI * f / RATE
            val env = exp(-decay * t) * minOf(1.0, i / (RATE * 0.004))
            out[idx] += (amp * env * (sin(phase) + harm * sin(3 * phase))).toFloat()
        }
    }

    private fun noise(out: FloatArray, from: Int, len: Int, amp: Double, decay: Double, smooth: Int, seed: Int, rise: Boolean = false) {
        val rnd = Random(seed)
        var acc = 0.0
        for (i in 0 until len) {
            val idx = from + i
            if (idx >= out.size) break
            val t = i.toDouble() / len
            acc += (rnd.nextDouble(-1.0, 1.0) - acc) / smooth
            val env = if (rise) sin(PI * t) else exp(-decay * t)
            out[idx] += (amp * env * acc * smooth.coerceAtMost(6)).toFloat()
        }
    }

    private val generators: Map<String, () -> FloatArray> = mapOf(
        "tick" to { samples(0.07).also { tone(it, 0, it.size, 1320.0, 1500.0, 0.5, 6.0, 0.2) } },
        "pop" to { samples(0.11).also { tone(it, 0, it.size, 900.0, 380.0, 0.6, 4.0) } },
        "set" to {
            samples(0.42).also { o ->
                listOf(523.0, 659.0, 784.0, 1047.0).forEachIndexed { k, f -> tone(o, k * 3000, 9000, f, f, 0.35, 3.0, 0.25) }
            }
        },
        "surge" to { samples(0.45).also { tone(it, 0, it.size, 280.0, 1700.0, 0.45, 1.5, 0.15); noise(it, 0, it.size, 0.12, 0.0, 3, 3, rise = true) } },
        "crack" to { samples(0.16).also { noise(it, 0, it.size, 0.9, 7.0, 2, 11); tone(it, 0, it.size, 220.0, 90.0, 0.4, 8.0) } },
        "boom" to { samples(0.7).also { noise(it, 0, it.size, 0.8, 5.0, 12, 5); tone(it, 0, it.size, 90.0, 40.0, 0.7, 4.0) } },
        "reveal" to {
            samples(1.2).also { o ->
                listOf(523.0, 659.0, 784.0, 1047.0, 1319.0).forEachIndexed { k, f -> tone(o, k * 1200, o.size - k * 1200, f, f * 1.003, 0.16, 2.2, 0.1) }
            }
        },
        "whoosh" to { samples(0.55).also { noise(it, 0, it.size, 0.5, 0.0, 4, 9, rise = true) } },
        "levelup" to {
            samples(0.8).also { o ->
                listOf(392.0, 523.0, 659.0, 784.0, 1047.0, 1319.0, 1568.0, 2093.0).forEachIndexed { k, f -> tone(o, k * 2600, 9000, f, f, 0.3, 3.0, 0.3) }
            }
        },
        "fail" to { samples(0.35).also { tone(it, 0, it.size, 330.0, 200.0, 0.4, 2.5, 0.3) } },
    )

    private fun writeWav(file: File, data: FloatArray) {
        val peak = data.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6f)
        val scale = if (peak > 0.95f) 0.95f / peak else 1f
        val pcm = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in data) pcm.putShort((v * scale * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.size * 2)
        }
        FileOutputStream(file).use { it.write(header.array()); it.write(pcm.array()) }
    }
}

object Haptics {
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun go(effect: VibrationEffect) {
        try { vibrator?.vibrate(effect) } catch (e: Exception) { }
    }

    fun tick() = if (Build.VERSION.SDK_INT >= 29) go(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)) else go(VibrationEffect.createOneShot(12, 120))
    fun click() = if (Build.VERSION.SDK_INT >= 29) go(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)) else go(VibrationEffect.createOneShot(25, 220))
    fun thud() = go(VibrationEffect.createWaveform(longArrayOf(0, 40, 30, 80), intArrayOf(0, 255, 0, 180), -1))
    fun rumble() = go(VibrationEffect.createWaveform(longArrayOf(0, 60, 40, 60, 40, 120, 60, 300), intArrayOf(0, 120, 0, 170, 0, 220, 0, 255), -1))
}
