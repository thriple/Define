package com.thriple.define.engine.music

/*
 * The synth rack. Every sound is a classic analog-style design: no samples, nothing pretending to be an
 * acoustic instrument. Drums follow the 808/909 recipes (pitched sine kick, noise snare, metallic hats),
 * the bass and acid voices are mono synths with resonant filters, pads are detuned supersaws.
 */

internal class Kick(private val sr: Float) {
    private var on = false
    private val osc = Osc()
    private var pEnv = 0f
    private var aEnv = 0f
    private var cEnv = 0f
    private var vel = 0f
    private val pK = decayCoef(0.11f, sr)
    private val aK = decayCoef(0.6f, sr)
    private val cK = decayCoef(0.008f, sr)
    private val noise = Noise(7)

    fun hit(v: Float) { on = true; osc.ph = 0.25f; pEnv = 1f; aEnv = 1f; cEnv = 1f; vel = v }

    fun run(): Float {
        if (!on) return 0f
        osc.freq(44f + 190f * pEnv * pEnv, sr)
        val body = osc.sine() * aEnv
        val click = noise.next() * cEnv * 0.35f
        pEnv *= pK; aEnv *= aK; cEnv *= cK
        if (aEnv < 1e-4f) on = false
        return sat((body * 1.7f + click)) * vel
    }
}

internal class Snare(private val sr: Float) {
    private var on = false
    private val t1 = Osc()
    private val t2 = Osc()
    private val hp = Svf(sr).apply { set(1400f, 0.7f) }
    private val noise = Noise(11)
    private var tEnv = 0f
    private var nEnv = 0f
    private var vel = 0f
    private val tK = decayCoef(0.12f, sr)
    private val nK = decayCoef(0.26f, sr)

    init { t1.freq(185f, sr); t2.freq(330f, sr) }

    fun hit(v: Float) { on = true; tEnv = 1f; nEnv = 1f; vel = v; t1.ph = 0f; t2.ph = 0f }

    fun run(): Float {
        if (!on) return 0f
        hp.run(noise.next())
        val v = (t1.sine() * 0.55f + t2.sine() * 0.3f) * tEnv + hp.high * nEnv * 0.9f
        tEnv *= tK; nEnv *= nK
        if (nEnv < 1e-4f) on = false
        return v * vel
    }
}

internal class Clap(private val sr: Float) {
    private var t = -1
    private val bp = Svf(sr).apply { set(1150f, 1.4f) }
    private val noise = Noise(23)
    private var vel = 0f
    private val burst = (0.0105f * sr).toInt()
    private var tail = 0f
    private val tailK = decayCoef(0.3f, sr)

    fun hit(v: Float) { t = 0; vel = v; tail = 1f }

    fun run(): Float {
        if (t < 0) return 0f
        bp.run(noise.next())
        val env = if (t < burst * 3) {
            val u = (t % burst).toFloat() / burst
            (1f - u) * (1f - u)
        } else {
            tail *= tailK; tail
        }
        t++
        if (t > burst * 3 && tail < 1e-4f) t = -1
        return bp.band * env * vel * 2.2f
    }
}

/**
 * Synth taiko: a big low drum built like a trailer-score "hybrid" hit. A sine body that drops in pitch,
 * a low-passed noise slap for the skin, long decay into the reverb.
 */
internal class Taiko(private val sr: Float, private val tune: Float, seed: Int) {
    private val osc = Osc()
    private val osc2 = Osc()
    private val lp = Svf(sr).apply { set(520f * tune, 0.9f) }
    private val noise = Noise(seed)
    private var pEnv = 0f
    private var aEnv = 0f
    private var nEnv = 0f
    private var vel = 0f
    private var n = 0
    private val pK = decayCoef(0.14f, sr)
    private val aK = decayCoef(1.1f, sr)
    private val nK = decayCoef(0.09f, sr)

    fun hit(v: Float) { pEnv = 1f; aEnv = 1f; nEnv = 1f; vel = v; osc.ph = 0.25f; osc2.ph = 0.25f }

    fun run(): Float {
        if (aEnv < 1e-4f) return 0f
        if (n and 7 == 0) { val f = 64f * tune * (1f + 0.7f * pEnv); osc.freq(f, sr); osc2.freq(f * 1.51f, sr) }
        n++
        val body = osc.sine() * aEnv + osc2.sine() * aEnv * aEnv * 0.35f
        val skin = lp.run(noise.next()) * nEnv
        pEnv *= pK; aEnv *= aK; nEnv *= nK
        return sat(body * 1.4f + skin * 1.6f) * vel
    }
}

/** 808-style metallic noise: six detuned square waves through a high-pass. Hats and crash. */
internal class Metal(private val sr: Float, spread: Float, seed: Int) {
    private val oscs = floatArrayOf(205.3f, 304.4f, 369.6f, 522.7f, 540f, 800f).map { f -> Osc().apply { freq(f * spread, sr) } }
    private val hp = Svf(sr).apply { set(if (spread > 1.2f) 4800f else 7200f, 0.8f) }
    private val noise = Noise(seed)
    private var env = 0f
    private var k = 0f
    private var vel = 0f

    fun hit(v: Float, decay: Float) { env = 1f; vel = v; k = decayCoef(decay, sr) }
    fun choke() { env *= 0.2f }

    fun run(): Float {
        if (env < 1e-4f) return 0f
        var m = 0f
        for (o in oscs) m += o.raw()
        hp.run(m * 0.12f + noise.next() * 0.5f)
        val v = hp.high * env * vel
        env *= k
        return v
    }
}

/**
 * Monophonic synth: two saws and a sub through a resonant low-pass with its own envelope.
 * As bass it pumps under the kick; as "acid" it gets high resonance, accents and slides.
 */
internal class MonoSynth(private val sr: Float, private val acid: Boolean) {
    private val o1 = Osc()
    private val o2 = Osc()
    private val sub = Osc()
    private val lp = Svf(sr)
    private var pitch = 45f
    private var target = 45f
    private var sliding = false
    private var gate = 0
    private var amp = 0f
    private var fEnv = 0f
    private var vel = 0f
    private var accent = false
    private var n = 0
    private val atk = 1f / (0.003f * sr)
    private val rel = decayCoef(if (acid) 0.05f else 0.07f, sr)
    private val glide = 1f - decayCoef(0.09f, sr)
    private val fK = decayCoef(if (acid) 0.35f else 0.25f, sr)
    private val fKAccent = decayCoef(0.18f, sr)

    /** Base cutoff in Hz, set by the engine (the "breathing" filter). */
    var cutoff = 400f

    fun note(midi: Float, lengthSamples: Int, velocity: Float, accented: Boolean = false, slide: Boolean = false) {
        target = midi
        sliding = slide && gate > 0
        if (!sliding) { pitch = midi; fEnv = 1f }
        gate = lengthSamples
        vel = velocity
        accent = accented
    }

    fun run(): Float {
        if (gate <= 0 && amp < 1e-4f) return 0f
        if (n and 15 == 0) {
            if (sliding) pitch += (target - pitch) * (glide * 16f).coerceAtMost(1f)
            val f = hz(pitch)
            o1.freq(f, sr); o2.freq(f * 1.0047f, sr); sub.freq(f * 0.5f, sr)
        }
        if (n and 7 == 0) {
            val mod = if (acid) (if (accent) 5200f else 2600f) else 1400f
            lp.set(cutoff + mod * fEnv, if (acid) (if (accent) 9f else 6f) else 1.0f)
        }
        n++
        if (gate > 0) { gate--; amp = (amp + atk).coerceAtMost(1f) } else amp *= rel
        fEnv *= if (accent) fKAccent else fK
        val raw = if (acid) o1.saw() else (o1.saw() * 0.5f + o2.saw() * 0.5f + sub.sine() * 0.8f)
        val y = lp.run(raw)
        return sat(y * (if (acid) 2.2f else 1.4f)) * amp * vel * (if (accent) 1.25f else 1f)
    }
}

/** Five detuned saws spread across the stereo field ("supersaw"). Pads and chord stabs. */
internal class SawVoice(private val sr: Float, seed: Int) {
    private val oscs = Array(5) { Osc() }
    private val detune = floatArrayOf(-0.19f, -0.085f, 0f, 0.09f, 0.2f)
    private val wl = FloatArray(5)
    private val wr = FloatArray(5)
    private val fl = Svf(sr)
    private val fr = Svf(sr)
    private val rnd = java.util.Random(seed.toLong())
    private var stage = 4
    private var env = 0f
    private var atkInc = 0f
    private var decK = 0f
    private var relK = 0f
    private var sustain = 0f
    private var fEnv = 0f
    private var fK = 0f
    private var fAmt = 0f
    private var vel = 0f
    private var n = 0
    var note = 0f; private set
    val active get() = stage < 4
    var outL = 0f; private set
    var outR = 0f; private set

    /** Shared brightness from the engine, as a cutoff in Hz. */
    var cutoff = 1200f

    init {
        val pans = floatArrayOf(-0.9f, -0.45f, 0f, 0.45f, 0.9f)
        for (i in 0 until 5) { wl[i] = (1f - pans[i]) * 0.5f; wr[i] = (1f + pans[i]) * 0.5f }
    }

    fun start(midi: Float, velocity: Float, attack: Float, decay: Float, sus: Float, release: Float, filterEnv: Float = 0f, filterDecay: Float = 0.3f) {
        note = midi
        for (i in 0 until 5) { oscs[i].freq(hz(midi + detune[i]), sr); oscs[i].ph = rnd.nextFloat() }
        stage = 0
        atkInc = 1f / (attack.coerceAtLeast(0.002f) * sr)
        decK = decayCoef(decay, sr)
        sustain = sus
        relK = decayCoef(release, sr)
        vel = velocity
        fAmt = filterEnv
        fEnv = 1f
        fK = decayCoef(filterDecay, sr)
        if (env < 0f) env = 0f
    }

    fun release() { if (stage < 3) stage = 3 }

    fun run() {
        if (stage >= 4) { outL = 0f; outR = 0f; return }
        when (stage) {
            0 -> { env += atkInc; if (env >= 1f) { env = 1f; stage = 1 } }
            1 -> { env = sustain + (env - sustain) * decK; if (env - sustain < 1e-3f) stage = 2 }
            2 -> env = sustain
            3 -> { env *= relK; if (env < 1e-4f) { stage = 4; env = 0f } }
        }
        if (n and 15 == 0) {
            val c = cutoff * (1f + fAmt * fEnv)
            fl.set(c, 0.8f); fr.set(c, 0.8f)
        }
        n++
        fEnv *= fK
        var l = 0f
        var r = 0f
        for (i in 0 until 5) { val s = oscs[i].saw(); l += s * wl[i]; r += s * wr[i] }
        val g = env * vel * 0.32f
        outL = fl.run(l) * g
        outR = fr.run(r) * g
    }
}

/** Pulse-wave pluck with a snappy filter envelope: arpeggios, count-in blips, sparkles. */
internal class Pluck(private val sr: Float) {
    private val o1 = Osc()
    private val o2 = Osc()
    private val lp = Svf(sr)
    private var aEnv = 0f
    private var fEnv = 0f
    private var aK = 0f
    private var fK = 0f
    private var vel = 0f
    private var bright = 0f
    private var n = 0
    val active get() = aEnv > 1e-4f

    fun start(midi: Float, velocity: Float, decay: Float, brightness: Float) {
        val f = hz(midi)
        o1.freq(f, sr); o2.freq(f * 1.004f, sr)
        aEnv = 1f; fEnv = 1f; vel = velocity; bright = brightness
        aK = decayCoef(decay, sr)
        fK = decayCoef(decay * 0.45f, sr)
    }

    fun run(): Float {
        if (aEnv < 1e-4f) return 0f
        if (n and 7 == 0) lp.set(260f + bright * 7000f * fEnv, 1.6f)
        n++
        val v = lp.run(o1.pulse(0.32f) * 0.7f + o2.saw() * 0.35f) * aEnv * vel
        aEnv *= aK; fEnv *= fK
        return v
    }
}

/** Noise sweep plus rising saws: the build-up before a drop. */
internal class Riser(private val sr: Float, seed: Int) {
    private val bpL = Svf(sr)
    private val bpR = Svf(sr)
    private val nL = Noise(seed * 31 + 1)
    private val nR = Noise(seed * 31 + 2)
    private val s1 = Osc()
    private val s2 = Osc()
    private var t = 0
    private var len = 0
    private var root = 57f
    var outL = 0f; private set
    var outR = 0f; private set

    fun start(seconds: Float, rootMidi: Float) { len = (seconds * sr).toInt().coerceAtLeast(1); t = 0; root = rootMidi }

    fun run() {
        if (t >= len) { outL = 0f; outR = 0f; return }
        val u = t.toFloat() / len
        if (t and 15 == 0) {
            val c = 350f * Math.pow(28.0, u.toDouble()).toFloat()
            bpL.set(c, 2.2f); bpR.set(c * 1.08f, 2.2f)
            val f = hz(root - 12f + 24f * u * u)
            s1.freq(f, sr); s2.freq(f * 1.011f, sr)
        }
        t++
        val a = u * u * u
        val saws = (s1.saw() + s2.saw()) * 0.18f * u * u
        outL = (bpL.run(nL.next()) * 0.9f * a + saws * 0.8f) * a.coerceAtLeast(0.15f)
        outR = (bpR.run(nR.next()) * 0.9f * a + saws * 0.8f) * a.coerceAtLeast(0.15f)
        outL *= 1.6f; outR *= 1.6f
    }
}

/** Sub drop and noise slam on a downbeat. */
internal class Impact(private val sr: Float) {
    private val sub = Osc()
    private val lp = Svf(sr).apply { set(700f, 0.7f) }
    private val noise = Noise(99)
    private var pEnv = 0f
    private var aEnv = 0f
    private var nEnv = 0f
    private var vel = 0f
    private val pK = decayCoef(0.9f, sr)
    private val aK = decayCoef(1.8f, sr)
    private val nK = decayCoef(0.45f, sr)
    private var n = 0

    fun hit(v: Float) { pEnv = 1f; aEnv = 1f; nEnv = 1f; vel = v; sub.ph = 0.25f }

    fun run(): Float {
        if (aEnv < 1e-4f) return 0f
        if (n and 7 == 0) sub.freq(30f + 45f * pEnv, sr)
        n++
        val v = sat(sub.sine() * aEnv * 1.5f) + lp.run(noise.next()) * nEnv * 1.2f
        pEnv *= pK; aEnv *= aK; nEnv *= nK
        return v * vel
    }
}

/** Falling noise sweep after a big hit. */
internal class Downlifter(private val sr: Float) {
    private val bp = Svf(sr)
    private val noise = Noise(5)
    private var t = 0
    private var len = 0

    fun start(seconds: Float) { len = (seconds * sr).toInt(); t = 0 }

    fun run(): Float {
        if (t >= len) return 0f
        val u = t.toFloat() / len
        if (t and 15 == 0) bp.set(7000f * Math.pow(0.03, u.toDouble()).toFloat(), 1.8f)
        t++
        val a = (1f - u)
        return bp.run(noise.next()) * a * a * 1.3f
    }
}

/** "Power down": detuned saws diving two octaves with the filter closing. For a missed round. */
internal class Dive(private val sr: Float) {
    private val o1 = Osc()
    private val o2 = Osc()
    private val lp = Svf(sr)
    private var t = 0
    private var len = 0
    private var from = 69f

    fun start(midi: Float, seconds: Float) { from = midi; len = (seconds * sr).toInt(); t = 0 }

    fun run(): Float {
        if (t >= len) return 0f
        val u = t.toFloat() / len
        if (t and 15 == 0) {
            val f = hz(from - 30f * u)
            o1.freq(f, sr); o2.freq(f * 1.013f, sr)
            lp.set(5000f * (1f - u) + 120f, 2.5f)
        }
        t++
        return lp.run(o1.saw() + o2.saw()) * (1f - u) * 0.45f
    }
}
