package com.thriple.define.engine.music

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.tan

/*
 * Small, allocation-free DSP building blocks. Everything runs per sample on the audio thread, so these
 * avoid objects, boxing and transcendental functions in the hot path where a table or recurrence works.
 */

internal fun hz(midi: Float): Float = 440f * 2f.pow((midi - 69f) / 12f)

/** Coefficient for an exponential decay that falls to -60 dB in [seconds]. */
internal fun decayCoef(seconds: Float, sr: Float): Float =
    if (seconds <= 0f) 0f else 10f.pow(-3f / (seconds * sr))

/** Soft saturation, roughly tanh but cheap. */
internal fun sat(x: Float): Float {
    val c = x.coerceIn(-3f, 3f)
    return c * (27f + c * c) / (27f + 9f * c * c)
}

internal object Sine {
    private const val N = 4096
    private val t = FloatArray(N + 1) { kotlin.math.sin(2.0 * PI * it / N).toFloat() }

    /** sin(2π·phase), phase in cycles. */
    fun of(phase: Float): Float {
        val p = (phase - floor(phase)) * N
        val i = p.toInt().coerceIn(0, N - 1)
        return t[i] + (t[i + 1] - t[i]) * (p - i)
    }
}

internal fun blep(t: Float, dt: Float): Float = when {
    t < dt -> { val x = t / dt; x + x - x * x - 1f }
    t > 1f - dt -> { val x = (t - 1f) / dt; x * x + x + x + 1f }
    else -> 0f
}

/** Band-limited (PolyBLEP) oscillator. */
internal class Osc {
    var ph = 0f
    var dt = 0f

    fun freq(f: Float, sr: Float) { dt = (f / sr).coerceIn(0f, 0.49f) }

    fun saw(): Float {
        val t = ph
        ph += dt; if (ph >= 1f) ph -= 1f
        return 2f * t - 1f - blep(t, dt)
    }

    fun pulse(pw: Float): Float {
        val t = ph
        ph += dt; if (ph >= 1f) ph -= 1f
        var v = if (t < pw) 1f else -1f
        v += blep(t, dt)
        var t2 = t - pw; if (t2 < 0f) t2 += 1f
        v -= blep(t2, dt)
        return v
    }

    fun sine(): Float {
        val t = ph
        ph += dt; if (ph >= 1f) ph -= 1f
        return Sine.of(t)
    }

    /** Naive square, used where aliasing is part of the sound (metallic cymbals). */
    fun raw(): Float {
        ph += dt; if (ph >= 1f) ph -= 1f
        return if (ph < 0.5f) 1f else -1f
    }
}

/** Zero-delay-feedback state-variable filter (Simper/Zavalishin). Stable under fast modulation. */
internal class Svf(private val sr: Float) {
    private var ic1 = 0f
    private var ic2 = 0f
    private var a1 = 1f
    private var a2 = 0f
    private var a3 = 0f
    private var k = 1f
    var low = 0f; private set
    var band = 0f; private set
    var high = 0f; private set

    fun set(cutoff: Float, q: Float) {
        val f = cutoff.coerceIn(20f, sr * 0.45f)
        val g = tan(PI * f / sr).toFloat()
        k = 1f / q.coerceAtLeast(0.3f)
        a1 = 1f / (1f + g * (g + k))
        a2 = g * a1
        a3 = g * a2
    }

    fun run(x: Float): Float {
        val v3 = x - ic2
        val v1 = a1 * ic1 + a2 * v3
        val v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2f * v1 - ic1
        ic2 = 2f * v2 - ic2
        low = v2; band = v1; high = x - k * v1 - v2
        return v2
    }

    fun reset() { ic1 = 0f; ic2 = 0f }
}

internal class Noise(seed: Int) {
    private var s = seed or 1
    fun next(): Float {
        s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5)
        return s * 4.656613e-10f
    }
}

/** One-pole smoother for control values. */
internal class Smooth(var value: Float, private val coef: Float) {
    var target = value
    fun next(): Float { value += (target - value) * coef; return value }
}

/** Stereo ping-pong echo with a darkening feedback path, synced to the tempo by the caller. */
internal class PingPong(private val sr: Float) {
    private val n = (sr * 2f).toInt()
    private val l = FloatArray(n)
    private val r = FloatArray(n)
    private var w = 0
    private var lpL = 0f
    private var lpR = 0f
    var delay = (sr * 0.3f).toInt()
    var feedback = 0.42f
    var outL = 0f; private set
    var outR = 0f; private set

    fun run(input: Float) {
        var rd = w - delay.coerceIn(1, n - 1); if (rd < 0) rd += n
        val dl = l[rd]; val dr = r[rd]
        lpL += (dl - lpL) * 0.35f
        lpR += (dr - lpR) * 0.35f
        l[w] = input + lpR * feedback
        r[w] = lpL * feedback
        outL = dl; outR = dr
        w++; if (w >= n) w = 0
    }
}

/** Compact Freeverb-style plate: 4 damped combs + 2 allpasses per channel. */
internal class Reverb(sr: Float) {
    private val scale = sr / 44100f
    private val combL = intArrayOf(1116, 1277, 1422, 1557).map { Comb((it * scale).toInt()) }
    private val combR = intArrayOf(1139, 1300, 1445, 1580).map { Comb((it * scale).toInt()) }
    private val apL = intArrayOf(556, 341).map { Allpass((it * scale).toInt()) }
    private val apR = intArrayOf(579, 364).map { Allpass((it * scale).toInt()) }
    var outL = 0f; private set
    var outR = 0f; private set

    fun run(input: Float) {
        val x = input * 0.12f
        var l = combL[0].run(x) + combL[1].run(x) + combL[2].run(x) + combL[3].run(x)
        var r = combR[0].run(x) + combR[1].run(x) + combR[2].run(x) + combR[3].run(x)
        l = apL[1].run(apL[0].run(l)); r = apR[1].run(apR[0].run(r))
        outL = l; outR = r
    }

    private class Comb(len: Int) {
        private val b = FloatArray(len.coerceAtLeast(1))
        private var i = 0
        private var f = 0f
        fun run(x: Float): Float {
            val y = b[i]
            f = y * 0.72f + f * 0.28f
            b[i] = x + f * 0.86f
            i++; if (i >= b.size) i = 0
            return y
        }
    }

    private class Allpass(len: Int) {
        private val b = FloatArray(len.coerceAtLeast(1))
        private var i = 0
        fun run(x: Float): Float {
            val y = b[i]
            b[i] = x + y * 0.5f
            i++; if (i >= b.size) i = 0
            return y - x
        }
    }
}
