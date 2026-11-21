package com.thriple.define.engine.music

import com.thriple.define.engine.Motion
import com.thriple.define.engine.Pace
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.floor
import kotlin.math.roundToInt

/** What the music is doing; each screen asks for one. */
enum class Scene { SILENT, HOME, CALM, WORK, REST, POD, VICTORY, TRANSFORM }

/**
 * Adaptive synth score, rendered in real time. Written in electronic idioms only: four-on-the-floor,
 * sidechain pumping, octave and off-beat bass, supersaw chords, 16th-note arpeggios through a ping-pong
 * delay, acid lines, risers, impacts and filter sweeps. Nothing imitates an acoustic instrument.
 *
 * Thread model: commands from the UI are queued and applied on the audio thread at the start of a block.
 * The beat clock lives here; the UI maps "frames heard" to beats with [beatAt], so visuals follow the music.
 */
class MusicEngine(val sampleRate: Int, seed: Long) {
    private val sr = sampleRate.toFloat()
    private val queue = ConcurrentLinkedQueue<Runnable>()

    // ------------------------------------------------------------------ instruments and effects
    private val kick = Kick(sr)
    private val snare = Snare(sr)
    private val clap = Clap(sr)
    private val hats = Metal(sr, 1f, 3)
    private val crash = Metal(sr, 1.45f, 4)
    private val taikoLo = Taiko(sr, 1f, 41)
    private val taikoHi = Taiko(sr, 1.5f, 42)
    private val bass = MonoSynth(sr, acid = false)
    private val acid = MonoSynth(sr, acid = true)
    private val pads = Array(10) { SawVoice(sr, it) }
    private val stabs = Array(12) { SawVoice(sr, 100 + it) }
    private val plucks = Array(8) { Pluck(sr) }
    private val blips = Array(6) { Pluck(sr) }
    private val motion = Array(8) { Pluck(sr) }
    private var motionNext = 0
    private val risers = Array(2) { Riser(sr, it) }
    private val impact = Impact(sr)
    private val downer = Downlifter(sr)
    private val dive = Dive(sr)
    private val delay = PingPong(sr)
    private val reverb = Reverb(sr)
    private var pluckNext = 0
    private var blipNext = 0
    private var riserNext = 0

    // ------------------------------------------------------------------ harmony
    private val scale = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    private val root: Int = 53 + Math.floorMod(seed, 8L).toInt()   // F3..C4, one key per day
    private val acidNotes = IntArray(16)
    private val acidAccent = BooleanArray(16)
    private val acidSlide = BooleanArray(16)
    private val acidRest = BooleanArray(16)
    private var transpose = 0
    private val voicing = IntArray(4)
    private var voicingSet = false
    private var chordRoot = 0          // semitones above the key root
    private var chordDegree = -1
    private var phraseBar = 0L

    init {
        val r = java.util.Random(seed * 7919 + 13)
        val pool = intArrayOf(0, 0, 0, 12, 7, 0, 10, 3, 12, 0)
        for (i in 0 until 16) {
            acidNotes[i] = pool[r.nextInt(pool.size)]
            acidRest[i] = i % 4 != 0 && r.nextFloat() < 0.22f
            acidAccent[i] = r.nextFloat() < 0.3f || i == 0
            acidSlide[i] = r.nextFloat() < 0.22f
        }
    }

    private fun progression(s: Scene): IntArray = when (s) {
        Scene.WORK -> intArrayOf(0, 6, 5, 6)        // i  VII VI VII   driving
        Scene.REST, Scene.CALM -> intArrayOf(5, 6, 0, 0) // VI VII i i      floating
        Scene.POD -> intArrayOf(0, 0, 5, 5)
        Scene.VICTORY -> intArrayOf(5, 6, 0, 2)     // VI VII i III    lifting
        Scene.TRANSFORM -> intArrayOf(5, 6, 5, 6)
        else -> intArrayOf(0, 5, 2, 6)              // i  VI III VII   home
    }

    private fun chordTones(degree: Int, lush: Boolean): IntArray {
        fun s(i: Int) = scale[i % 7] + 12 * (i / 7)
        val base = intArrayOf(s(degree), s(degree + 2), s(degree + 4))
        val extra = if (lush) s(degree + 6) else s(degree) + 12
        return intArrayOf(base[0], base[1], base[2], extra)
    }

    /** Smooth voice leading: each chord tone goes to the octave nearest the previous chord's centre. */
    private fun voice(tones: IntArray) {
        val centre = if (voicingSet) voicing.average().toFloat() else (root + 7).toFloat()
        for (i in tones.indices) {
            var n = root + transpose + tones[i]
            while (n - centre > 6f) n -= 12
            while (centre - n > 6f) n += 12
            voicing[i] = n.coerceIn(root - 5, root + 19)
        }
        voicing.sort()
        voicingSet = true
    }

    // ------------------------------------------------------------------ clock
    private var frame = 0L
    private var beat = 0.0
    private var bpm = 100.0
    private var bpmTarget = 100.0
    private var step = -1L
    private val ringN = 1024
    private val ringFrame = LongArray(ringN)
    private val ringBeat = DoubleArray(ringN)
    private val ringRate = DoubleArray(ringN)
    private var ringHead = -1
    private val lock = Any()

    /** Frames rendered so far. */
    val framesRendered: Long get() = synchronized(lock) { frame }

    /** The beat that was being played at [atFrame] (frames since the engine started). */
    fun beatAt(atFrame: Long): Double = synchronized(lock) {
        if (ringHead < 0) return 0.0
        var i = ringHead
        repeat(ringN) {
            if (ringFrame[i] <= atFrame || it == ringN - 1) {
                return ringBeat[i] + (atFrame - ringFrame[i]) * ringRate[i]
            }
            i = (i - 1 + ringN) % ringN
        }
        ringBeat[ringHead]
    }

    // ------------------------------------------------------------------ scene state
    private var scene = Scene.SILENT
    private var pending: Scene? = null
    private var energy = 0.5f
    private var musicOn = true
    private val musicGain = Smooth(0f, 0.0004f)
    private val masterGain = Smooth(1f, 0.001f)
    private var drumMuteUntil = -1.0
    private var dropOnEntry = false

    private var pace: Pace? = null
    private var paceStartStep = 0L
    private var paceReps = 0
    private var paceRiser = false

    // layer levels, recomputed every 16th note
    private var lKick = 0
    private var lSnare = 0
    private var lHats = 0
    private var lBass = 0
    private var lArp = 0
    private var lAcid = false
    private var lTaiko = 0
    private var lPad = false
    private var lLush = false
    private var lBright = 0.4f
    private var lCountIn = 0           // 16ths left in a count-in, 0 when none
    private var arpIndex = 0

    private val bright = Smooth(0.4f, 0.004f)
    private var sc = 0f
    private val scK = decayCoef(0.32f, sr)
    private var dcL = 0f
    private var dcR = 0f
    private var dcInL = 0f
    private var dcInR = 0f

    // scheduled one-shots (sparkles, stab sequences)
    private val evAt = LongArray(64)
    private val evKind = IntArray(64)
    private val evA = FloatArray(64)
    private val evB = FloatArray(64)
    private var evN = 0

    // ================================================================== public commands (any thread)

    fun scene(s: Scene, tempo: Float? = null, energy: Float? = null) = post {
        energy?.let { this.energy = it.coerceIn(0f, 1f) }
        tempo?.let { bpmTarget = it.toDouble() }
        if (s != scene || pending != null) pending = s
    }

    fun music(on: Boolean) = post { musicOn = on }

    /** Pace a round: count-in, then [reps] repetitions starting exactly at [startBeat]. */
    fun pace(p: Pace, startBeat: Double, reps: Int) = post {
        bpmTarget = p.bpm.toDouble(); bpm = bpmTarget
        pace = p
        paceStartStep = (startBeat * 4).roundToInt().toLong()
        paceReps = reps
        paceRiser = false
        phraseBar = paceStartStep / 16
        pending = Scene.WORK
    }

    fun stopPace() = post { pace = null }

    /** Round complete. [rarity] 0..4 (the surge) scales the celebration. */
    fun roundDone(rarity: Int) = post {
        pace = null
        val chord = chordTones(0, true)
        impact.hit(0.55f + 0.1f * rarity)
        crash.hit(0.6f, 1.8f)
        stabChord(chord, 12, 0.75f, 0.6f)
        sparkle(8 + 4 * rarity, 0.5f)
        if (rarity >= 2) downer.start(2.2f)
        if (rarity >= 3) liftStabs()
    }

    /** Supply pod: each tap a rising stab. */
    fun crack(n: Int) = post {
        val up = intArrayOf(0, 3, 5, 7)[n.coerceIn(0, 3)]
        stabChord(intArrayOf(0, 7, 12), up, 0.7f, 0.35f)
        clap.hit(0.6f)
    }

    fun reveal(rarity: Int) = post {
        impact.hit(0.8f + 0.05f * rarity)
        crash.hit(0.8f, 2.4f)
        taikoLo.hit(1f); taikoHi.hit(0.7f)
        stabChord(chordTones(0, true), 12, 0.9f, 1.2f)
        sparkle(12 + 6 * rarity, 0.6f)
        if (rarity >= 2) downer.start(2.6f)
        if (rarity >= 3) { transpose = 2; liftStabs() }
        enterNow(Scene.VICTORY, 124.0)
    }

    fun rankUp() = post {
        stabChord(chordTones(6, false), 12, 0.6f, 0.5f)
        schedule(frame + (sr * 0.25f).toLong(), EV_CHORD, 0f, 0.7f)
        sparkle(16, 0.5f)
    }

    /** Transformation charge-up: everything drops out under a riser of [seconds]. */
    fun charge(seconds: Float) = post {
        enterNow(Scene.TRANSFORM, 128.0)
        startRiser(seconds)
    }

    fun boom() = post {
        transpose = 2
        impact.hit(1f)
        taikoLo.hit(1f); taikoHi.hit(0.8f)
        crash.hit(0.9f, 2.8f)
        kick.hit(1f); sc = 1f
        stabChord(chordTones(0, true), 12, 1f, 1.6f)
        sparkle(28, 0.6f)
        downer.start(3f)
        enterNow(Scene.VICTORY, 128.0)
    }

    /** Missed round: power-down dive and a bar of silence from the drums. */
    fun fail() = post {
        pace = null
        dive.start((root + 24).toFloat(), 0.9f)
        drumMuteUntil = beat + 4.0
    }

    /** Build-up into whatever comes next, e.g. the end of a rest. */
    fun riser(seconds: Float) = post { startRiser(seconds) }

    fun master(level: Float) = post { masterGain.target = level }

    private fun post(cmd: () -> Unit) { queue.add(Runnable(cmd)) }

    // ================================================================== rendering (audio thread)

    /** Fills [out] with [frames] interleaved stereo float samples. */
    fun render(out: FloatArray, frames: Int) {
        while (true) queue.poll()?.run() ?: break
        var i = 0
        while (i < frames) {
            val n = minOf(32, frames - i)
            control(n)
            for (j in 0 until n) {
                val o = (i + j) * 2
                mix(out, o)
            }
            i += n
        }
    }

    private fun control(n: Int) {
        // tempo glides at up to 24 BPM per second
        val maxStep = 24.0 * n / sr
        bpm += (bpmTarget - bpm).coerceIn(-maxStep, maxStep)
        val rate = bpm / 60.0 / sr

        // 16th-note sequencing, quantized to 32-sample blocks (< 1 ms)
        val s = floor(beat * 4).toLong()
        if (s != step) { step = s; onStep(s) }

        // scheduled events
        var e = 0
        while (e < evN) {
            if (evAt[e] <= frame) { fire(evKind[e], evA[e], evB[e]); evN--; evAt[e] = evAt[evN]; evKind[e] = evKind[evN]; evA[e] = evA[evN]; evB[e] = evB[evN] } else e++
        }

        // the "breath": the filters open a little as the weight goes up
        val p = pace
        if (p != null) {
            val rel = beat - paceStartStep / 4.0
            if (rel >= 0) {
                val h = p.height(rel.toFloat())
                bright.target = (lBright + 0.16f * h).coerceAtMost(1f)
            } else {
                bright.target = lBright + 0.5f * (1f - (-rel / 8.0).toFloat().coerceIn(0f, 1f))
            }
        } else {
            bright.target = lBright
        }

        val b = bright.value
        val padCut = 260f * Math.pow(2.0, (b * 5.2f).toDouble()).toFloat()
        for (v in pads) v.cutoff = padCut
        bass.cutoff = 140f + 900f * b
        acid.cutoff = 180f + 700f * b
        delay.delay = (0.75 * 60.0 / bpm * sr).toInt()
        musicGain.target = if (musicOn && scene != Scene.SILENT) 1f else 0f

        synchronized(lock) {
            if (ringHead < 0 || frame - ringFrame[ringHead] >= 256) {
                ringHead = (ringHead + 1) % ringN
                ringFrame[ringHead] = frame
                ringBeat[ringHead] = beat
                ringRate[ringHead] = rate
            }
            frame += n
        }
        beat += rate * n
    }

    private fun mix(out: FloatArray, o: Int) {
        bright.next()
        val mg = musicGain.next()
        sc *= scK
        val duck = 1f - 0.6f * sc
        val duckBass = 1f - 0.5f * sc

        // drums
        val k = kick.run()
        val sn = snare.run()
        val cl = clap.run()
        val hh = hats.run()
        val cr = crash.run()
        val tl = taikoLo.run() * 0.75f
        val th = taikoHi.run() * 0.6f
        var dL = k * 0.95f + sn * 0.5f + cl * 0.42f + hh * 0.2f * 0.8f + cr * 0.16f * 0.7f
        var dR = k * 0.95f + sn * 0.5f + cl * 0.42f + hh * 0.2f * 1.2f + cr * 0.16f * 1.3f
        dL += tl * 1.1f + th * 0.8f
        dR += tl * 0.9f + th * 1.2f
        var rev = sn * 0.5f + cl * 0.4f + cr * 0.1f + (tl + th) * 0.45f

        // music bus (pumps under the kick)
        val bs = bass.run() * 0.5f * duckBass
        val ac = acid.run() * 0.26f * duck
        var pL = 0f
        var pR = 0f
        for (v in pads) { if (v.active) { v.run(); pL += v.outL; pR += v.outR } }
        pL *= 0.55f * duck; pR *= 0.55f * duck
        var ar = 0f
        for (v in plucks) ar += v.run()
        ar *= 0.2f * duck
        var mL = bs + ac + pL + ar * 0.8f
        var mR = bs + ac + pR + ar * 1.2f
        var dly = ar * 0.55f + ac * 0.25f
        rev += (pL + pR) * 0.25f + ar * 0.3f

        // cues (always on while sound is on, even with music off)
        var tr = 0f
        for (v in motion) tr += v.run()
        tr *= 0.26f
        var bl = 0f
        for (v in blips) bl += v.run()
        bl *= 0.3f
        var sL = 0f
        var sR = 0f
        for (v in stabs) { if (v.active) { v.run(); sL += v.outL; sR += v.outR } }
        sL *= 0.5f; sR *= 0.5f
        var rL = 0f
        var rR = 0f
        for (v in risers) { v.run(); rL += v.outL; rR += v.outR }
        val im = impact.run() * 0.75f
        val dn = downer.run() * 0.22f
        val dv = dive.run()
        val cL = tr + bl + sL + rL * 0.3f + im + dn * 0.8f + dv
        val cR = tr + bl + sR + rR * 0.3f + im + dn * 1.2f + dv
        dly += tr * 0.4f + bl * 0.6f
        rev += (sL + sR) * 0.35f + im * 0.25f + bl * 0.3f + tr * 0.25f

        dL *= mg; dR *= mg; mL *= mg; mR *= mg
        delay.run(dly)
        reverb.run(rev)
        var l = dL + mL + cL + delay.outL * 0.45f + reverb.outL * 0.9f
        var r = dR + mR + cR + delay.outR * 0.45f + reverb.outR * 0.9f

        // DC blocker, then gentle saturation as a limiter
        dcL = l - dcInL + 0.995f * dcL; dcInL = l; l = dcL
        dcR = r - dcInR + 0.995f * dcR; dcInR = r; r = dcR
        val g = masterGain.next() * 0.82f
        out[o] = sat(l * g) * 0.92f
        out[o + 1] = sat(r * g) * 0.92f
    }

    // ================================================================== the score

    private fun enterNow(s: Scene, tempo: Double) {
        pending = null
        val calmBefore = scene == Scene.REST || scene == Scene.CALM || scene == Scene.SILENT || scene == Scene.TRANSFORM
        scene = s
        bpmTarget = tempo
        phraseBar = (step + 15) / 16
        dropOnEntry = calmBefore && grooves(s)
        if (s != Scene.VICTORY) transpose = 0
    }

    private fun grooves(s: Scene) = s == Scene.WORK || s == Scene.VICTORY

    private fun onStep(s: Long) {
        val sb = Math.floorMod(s, 16L).toInt()
        val bar = Math.floorDiv(s, 16L)
        pending?.let { next ->
            if (sb % 4 == 0) {
                val calmBefore = scene == Scene.REST || scene == Scene.CALM || scene == Scene.SILENT || scene == Scene.HOME || scene == Scene.POD
                if (next != Scene.VICTORY && scene == Scene.VICTORY) transpose = 0
                if (scene != next) phraseBar = if (sb == 0) bar else bar + 1
                scene = next
                pending = null
                if (calmBefore && grooves(next) && pace == null) dropOnEntry = true
            }
        }
        computeLayers(s)
        if (sb == 0) onBar(bar)

        val drumsOn = musicOn && beat >= drumMuteUntil
        val v = 0.6f + 0.4f * energyFor()

        if (dropOnEntry && sb == 0) {
            dropOnEntry = false
            if (musicOn) { crash.hit(0.55f, 2f); impact.hit(0.45f) }
        }

        if (drumsOn) {
            // kick
            val kickHit = when (lKick) {
                1 -> sb == 0 || sb == 8
                2 -> sb % 4 == 0
                3 -> sb == 0 || sb == 3          // heartbeat
                else -> false
            }
            if (kickHit) { kick.hit(if (lKick == 3 && sb == 3) 0.6f * v else v); sc = 1f }

            // snare + clap backbeat, fills at the end of each 4-bar phrase
            if (lSnare >= 1 && (sb == 4 || sb == 12)) {
                snare.hit(0.85f * v)
                if (lSnare >= 2) clap.hit(0.8f * v)
            }
            val phraseEnd = Math.floorMod(bar - phraseBar, 4L) == 3L
            if (lSnare >= 2 && phraseEnd && sb >= 13 && lCountIn == 0) snare.hit((0.35f + 0.15f * (sb - 12)) * v)

            // hats
            when (lHats) {
                1 -> if (sb % 2 == 0) hats.hit(if (sb % 4 == 2) 0.75f * v else 0.4f * v, 0.05f)
                2 -> if (sb % 4 == 2) hats.hit(0.7f * v, 0.32f) else hats.hit(if (sb % 2 == 1) 0.32f * v else 0.48f * v, 0.04f)
            }
        }

        if (drumsOn) {
            // taiko: 1 = downbeats, 2 = a driving "don . . don . . don ." pattern with a roll into each phrase,
            // 3 = slow heartbeat (pod)
            val phraseEnd = Math.floorMod(bar - phraseBar, 4L) == 3L
            when (lTaiko) {
                1 -> { if (sb == 0) taikoLo.hit(0.9f); if (sb == 8) taikoLo.hit(0.7f) }
                2 -> {
                    if (sb == 0 || sb == 3 || sb == 8 || sb == 11) taikoLo.hit(if (sb == 0) 1f else 0.75f)
                    if (sb == 6 || sb == 14) taikoHi.hit(0.7f)
                    if (phraseEnd && sb >= 12) { if (sb % 2 == 0) taikoHi.hit(0.5f + 0.1f * (sb - 12)) else taikoLo.hit(0.6f) }
                }
                3 -> { if (sb == 0) taikoLo.hit(0.9f); if (sb == 3) taikoLo.hit(0.55f) }
            }
        }

        if (musicOn) {
            // bass
            val bRoot = (root - 24 + transpose + chordRoot).toFloat()
            val sixteenth = (sr * 60f / bpm.toFloat() / 4f).toInt()
            when (lBass) {
                1 -> if (sb == 0) bass.note(bRoot, sixteenth * 15, 0.85f)
                2 -> if (sb % 2 == 0) bass.note(bRoot, (sixteenth * 1.5f).toInt(), if (sb % 4 == 0) 0.9f else 0.75f)
                3 -> if (sb % 4 != 0) bass.note(if (sb % 4 == 2) bRoot + 12f else bRoot, (sixteenth * 0.8f).toInt(), if (sb % 4 == 2) 0.85f else 0.7f)
            }
            // acid
            if (lAcid && !acidRest[sb]) {
                val slide = acidSlide[(sb + 15) % 16]
                acid.note((root - 12 + transpose + chordRoot + acidNotes[sb]).toFloat(), if (slide) sixteenth * 2 else (sixteenth * 0.6f).toInt(), 0.9f, acidAccent[sb], acidSlide[sb])
            }
            // arpeggio over the current chord, two octaves up and down
            val arpHit = when (lArp) { 1 -> sb % 2 == 0; 2 -> true; else -> false }
            if (arpHit && voicingSet) {
                val seq = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3, 2, 1)
                val idx = seq[arpIndex % seq.size]
                arpIndex++
                val note = voicing[idx % 4] + 12 * (idx / 4) + 12
                plucks[pluckNext].start(note.toFloat(), if (sb % 4 == 0) 0.9f else 0.65f, 0.22f, 0.35f + 0.5f * bright.value)
                pluckNext = (pluckNext + 1) % plucks.size
            }
        }

        // pace: count-in blips and rep accents (cues, audible even with music off)
        val p = pace ?: return
        val rel = s - paceStartStep
        val repSteps = (p.beats * 4).roundToInt()
        if (rel < 0) {
            val left = (-rel).toInt()
            if (!paceRiser && left <= 32) { paceRiser = true; startRiser(left * 60f / bpm.toFloat() / 4f) }
            // 3, 2, 1 land on heavy hits (kick + taiko + clap), with the kick pounding every beat between
            if (left == 24 || left == 16 || left == 8) {
                val push = (24 - left) / 16f
                blip((root + transpose + 24).toFloat(), 1f)
                if (musicOn) { taikoLo.hit(0.85f + 0.15f * push); taikoHi.hit(0.5f + 0.2f * push); clap.hit(0.7f + 0.2f * push) }
            } else if (!musicOn && left % 4 == 0) {
                blip((root + transpose + 36).toFloat(), 0.4f, 0.05f)
            }
            if (left < 8 && musicOn) {
                // after "1": kick doubles to eighths and a snare roll climbs into the first rep
                if (left % 2 == 0 && left % 4 != 0) { kick.hit(0.8f); sc = 1f }
                snare.hit(0.2f + 0.45f * (8 - left) / 8f)
            }
            return
        }
        val k = (rel / repSteps).toInt()
        val within = (rel % repSteps).toInt()
        if (k >= paceReps) { pace = null; return }
        if (within == 0) {
            if (k == 0) { impact.hit(0.6f); crash.hit(0.7f, 2f) }
            else if (musicOn) crash.hit(0.25f + 0.45f * k / paceReps, 1.4f)
        }
        // metronome when there's no groove to follow
        if (!musicOn && within % 4 == 0) blip((root + transpose + 36).toFloat(), 0.3f, 0.05f)
        // the movement, played as arpeggios: a quick run up the chord while lifting, a slower walk down it
        // while lowering, so you can follow the weight by ear
        var acc = 0
        for (ph in p.phases) {
            val len = (ph.beats * 4).roundToInt()
            val moving = ph.motion == Motion.LIFT || ph.motion == Motion.LOWER
            if (moving && within == acc && musicOn) {
                // the beat follows the movement: a heavy hit to drive each lift, a lighter one to start lowering
                if (ph.motion == Motion.LIFT) { taikoLo.hit(1f); clap.hit(0.65f); kick.hit(1f); sc = 1f }
                else taikoHi.hit(0.6f)
            }
            if (moving && within >= acc && within < acc + len) {
                val j = within - acc
                val rate = if (len <= 8) 1 else 2
                if (j % rate == 0) {
                    val n = (len / rate).coerceAtLeast(1)
                    val pos = if (n <= 1) 1f else (j / rate).toFloat() / (n - 1)
                    val lift = ph.motion == Motion.LIFT
                    val idx = ((if (lift) pos else 1f - pos) * 7f).roundToInt().coerceIn(0, 7)
                    val note = voicing[idx % 4] + 12 * (idx / 4) + 12
                    motion[motionNext].start(
                        note.toFloat(),
                        if (lift) 0.55f + 0.3f * pos else 0.65f - 0.25f * pos,
                        if (lift) 0.2f else 0.32f,
                        if (lift) 0.3f + 0.45f * pos else 0.6f - 0.3f * pos,
                    )
                    motionNext = (motionNext + 1) % motion.size
                }
            }
            acc += len
        }
        if (k == paceReps - 1 && within >= repSteps - 4 && musicOn) snare.hit((0.4f + 0.12f * (within - repSteps + 4)))
    }

    private fun energyFor(): Float = when (scene) {
        Scene.HOME -> 0.3f + 0.5f * energy
        Scene.CALM, Scene.REST, Scene.POD -> 0.4f
        else -> 0.9f
    }

    private fun computeLayers(s: Long) {
        lCountIn = 0
        lTaiko = 0
        val e = energy
        when (scene) {
            Scene.SILENT -> set(0, 0, 0, 0, 0, false, false, 0.2f, false)
            Scene.HOME -> set(if (e > 0.2f) 1 else 0, if (e > 0.85f) 1 else 0, if (e > 0.45f) 1 else 0,
                if (e > 0.65f) 2 else 1, if (e > 0.75f) 2 else 1, false, true, 0.32f + 0.3f * e, false)
            Scene.CALM -> set(0, 0, 1, 1, 1, false, true, 0.38f, true)
            Scene.WORK -> set(2, 1, 1, 2, 0, false, true, 0.42f, false)
            Scene.REST -> set(0, 0, 0, 1, 1, false, true, 0.55f, true)
            Scene.POD -> set(3, 0, 0, 1, 0, false, true, 0.22f, false)
            Scene.VICTORY -> set(2, 2, 2, 3, 2, transpose > 0, true, 0.72f, false)
            Scene.TRANSFORM -> set(0, 0, 0, 0, 0, false, true, 0.3f, false)
        }
        lTaiko = when (scene) {
            Scene.POD -> 3
            Scene.TRANSFORM -> 2
            Scene.VICTORY -> 1
            else -> 0
        }
        if (scene == Scene.POD) lKick = 0
        val p = pace ?: return
        val rel = s - paceStartStep
        if (rel < 0) {
            val left = (-rel).toInt()
            lCountIn = left
            lKick = 2                                   // a steady beat to count along to
            if (left <= 24) { lHats = 0; lArp = 0; lSnare = 0 }
            if (left <= 8) lBass = 0
            return
        }
        val repSteps = (p.beats * 4).roundToInt()
        val k = (rel / repSteps).toInt()
        val f = k.toFloat() / paceReps.coerceAtLeast(1)
        lKick = 2
        lSnare = if (f >= 0.75f) 2 else 1
        lHats = if (f >= 0.25f) 2 else 1
        lBass = if (f >= 0.5f) 3 else 2
        lArp = 0 // the movement arpeggio carries the melody while pacing
        lAcid = k == paceReps - 1 || f >= 0.85f
        lTaiko = if (f >= 0.75f) 2 else if (f >= 0.5f) 1 else 0
        lBright = 0.36f + 0.12f * f
    }

    private fun set(kk: Int, sn: Int, hh: Int, bs: Int, ar: Int, ac: Boolean, pad: Boolean, br: Float, lush: Boolean) {
        lKick = kk; lSnare = sn; lHats = hh; lBass = bs; lArp = ar; lAcid = ac; lPad = pad; lBright = br; lLush = lush
    }

    private fun onBar(bar: Long) {
        val prog = progression(scene)
        val degree = prog[Math.floorMod(bar - phraseBar, prog.size.toLong()).toInt()]
        val tones = chordTones(degree, lLush)
        val changed = degree != chordDegree || !lPad || pads.none { it.active }
        chordDegree = degree
        chordRoot = scale[degree].let { if (it > 7) it - 12 else it }
        if (changed || !voicingSet) voice(tones)
        if (!musicOn || !lPad) { for (v in pads) v.release(); return }
        if (!changed) return
        for (v in pads) v.release()
        val attack = if (lLush || scene == Scene.HOME || scene == Scene.TRANSFORM) 0.6f else 0.03f
        var started = 0
        for (v in pads) {
            if (started >= 4) break
            if (!v.active) { v.start(voicing[started].toFloat(), 0.8f, attack, 1.2f, 0.75f, if (lLush) 1.4f else 0.5f); started++ }
        }
        // if all voices were still releasing, steal the quietest
        while (started < 4) { pads[started].start(voicing[started].toFloat(), 0.8f, attack, 1.2f, 0.75f, 0.5f); started++ }
    }

    // ------------------------------------------------------------------ one-shots

    private fun stabChord(tones: IntArray, up: Int, vel: Float, release: Float) {
        var used = 0
        for (t in tones) {
            val n = (root + transpose + t + up).toFloat()
            val v = stabs.firstOrNull { !it.active } ?: stabs[used % stabs.size]
            v.start(n, vel, 0.004f, 0.25f, 0.35f, release, filterEnv = 5f, filterDecay = 0.35f)
            v.cutoff = 900f
            v.release()
            used++
        }
        // octave-down body for weight
        stabs.firstOrNull { !it.active }?.apply {
            start((root + transpose + tones[0] + up - 12).toFloat(), vel * 0.8f, 0.004f, 0.3f, 0.3f, release, 4f, 0.3f)
            cutoff = 700f; release()
        }
    }

    /** Fast rising arpeggio across three octaves, through the echo. */
    private fun sparkle(notes: Int, gap: Float) {
        val tones = chordTones(0, true)
        val stepFrames = (sr * 60f / bpm.toFloat() / 8f * gap * 2f).toLong()
        for (i in 0 until notes) {
            val n = root + transpose + 24 + tones[i % 4] + 12 * ((i / 4) % 3)
            schedule(frame + i * stepFrames, EV_BLIP, n.toFloat(), 0.75f - 0.3f * i / notes)
        }
    }

    /** VI - VII - i stabs on successive eighths: the "lift" for big drops. */
    private fun liftStabs() {
        val eighth = (sr * 30f / bpm.toFloat()).toLong()
        schedule(frame + eighth * 2, EV_DEGREE, 5f, 0.7f)
        schedule(frame + eighth * 3, EV_DEGREE, 6f, 0.8f)
        schedule(frame + eighth * 4, EV_DEGREE, 0f, 1f)
    }

    private fun blip(midi: Float, vel: Float, decay: Float = 0.25f) {
        blips[blipNext].start(midi, vel, decay, 0.8f)
        blipNext = (blipNext + 1) % blips.size
    }

    private fun startRiser(seconds: Float) {
        risers[riserNext].start(seconds, (root + transpose + 12).toFloat())
        riserNext = (riserNext + 1) % risers.size
    }

    private fun schedule(at: Long, kind: Int, a: Float, b: Float) {
        if (evN >= evAt.size) return
        evAt[evN] = at; evKind[evN] = kind; evA[evN] = a; evB[evN] = b; evN++
    }

    private fun fire(kind: Int, a: Float, b: Float) {
        when (kind) {
            EV_BLIP -> blip(a, b, 0.3f)
            EV_CHORD -> stabChord(chordTones(a.toInt(), true), 12, b, 0.9f)
            EV_DEGREE -> { stabChord(chordTones(a.toInt(), false), 12, b, 0.5f); if (a == 0f) { impact.hit(0.7f); crash.hit(0.7f, 2f) } }
        }
    }

    private companion object {
        const val EV_BLIP = 1
        const val EV_CHORD = 2
        const val EV_DEGREE = 3
    }
}
