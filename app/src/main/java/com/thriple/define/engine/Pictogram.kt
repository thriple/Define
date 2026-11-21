package com.thriple.define.engine

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Animated exercise diagrams: a geometric stick figure posed by joint angles and two-bone IK, looping at the
 * exercise's tempo. Pure Kotlin (drawn by ui/PictogramView on Android; rendered on the JVM to check poses).
 * Coordinates live in a 1x1 box, y down, floor at y = 0.92.
 */
enum class Ink { FAR, BODY, WORK, WEIGHT, SCENE }

data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val width: Float, val ink: Ink)
data class Dot(val x: Float, val y: Float, val r: Float, val ink: Ink, val filled: Boolean = true)

class Frame {
    val lines = mutableListOf<Line>()
    val dots = mutableListOf<Dot>()
    fun line(a: P, b: P, ink: Ink, w: Float = 0.035f) { lines += Line(a.x, a.y, b.x, b.y, w, ink) }
    fun dot(a: P, r: Float, ink: Ink, filled: Boolean = true) { dots += Dot(a.x, a.y, r, ink, filled) }
}

data class P(val x: Float, val y: Float) {
    operator fun plus(o: P) = P(x + o.x, y + o.y)
    operator fun minus(o: P) = P(x - o.x, y - o.y)
    operator fun times(k: Float) = P(x * k, y * k)
}

object Pictogram {
    private const val TORSO = 0.25f
    private const val UPPER = 0.13f
    private const val FORE = 0.12f
    private const val THIGH = 0.19f
    private const val SHIN = 0.19f
    private const val HEAD = 0.045f
    private const val NECK = 0.025f
    private const val FLOOR = 0.92f

    /** Unit vector for an angle in degrees: 0 = straight down, 90 = forward (+x), 180 = straight up. */
    private fun dir(deg: Float): P { val r = deg * PI.toFloat() / 180f; return P(sin(r), cos(r)) }
    private fun at(p: P, deg: Float, len: Float) = p + dir(deg) * len
    private fun lerp(a: Float, b: Float, u: Float) = a + (b - a) * u
    private fun ease(u: Float) = 0.5f - 0.5f * cos(PI.toFloat() * u.coerceIn(0f, 1f))

    /** Elbow/knee for a two-bone limb from root to target; bend = +1/-1 picks the side it folds to. */
    private fun ik(root: P, target: P, a: Float, b: Float, bend: Float): P {
        val d = target - root
        val dist = hypot(d.x, d.y).coerceIn(0.0001f, a + b - 0.0001f)
        val base = atan2(d.y, d.x)
        val cosA = ((a * a + dist * dist - b * b) / (2 * a * dist)).coerceIn(-1f, 1f)
        val ang = base - bend * acos(cosA)
        return P(root.x + a * cos(ang), root.y + a * sin(ang))
    }

    /**
     * Rep cycle 0..1 (0 = start position, 1 = end position) with the given phase durations in seconds:
     * move out, hold, move back, rest. Returns eased position.
     */
    private fun cycle(t: Float, out: Float, hold: Float, back: Float, rest: Float): Float {
        val x = t % (out + hold + back + rest)
        return when {
            x < out -> ease(x / out)
            x < out + hold -> 1f
            x < out + hold + back -> 1f - ease((x - out - hold) / back)
            else -> 0f
        }
    }

    private fun floor(f: Frame) = f.line(P(0.04f, FLOOR + 0.02f), P(0.96f, FLOOR + 0.02f), Ink.SCENE, 0.012f)

    /** Standing side view facing right; arm angles absolute. Returns the near hand. */
    private fun standingSide(f: Frame, x: Float, upper: Float, fore: Float, farUpper: Float = upper, farFore: Float = fore,
                             work: Set<String> = emptySet(), lean: Float = 0f, hipShift: Float = 0f): Pair<P, P> {
        val ankle = P(x, FLOOR)
        val hip = P(x + hipShift, FLOOR - THIGH - SHIN)
        val knee = ik(hip, ankle, THIGH, SHIN, -1f)
        val shoulder = at(hip, 180f - lean, TORSO)
        val head = at(shoulder, 180f - lean, NECK + HEAD)
        // far side first (dimmer)
        val fe = at(shoulder, farUpper, UPPER); val fh = at(fe, farFore, FORE)
        f.line(shoulder, fe, Ink.FAR); f.line(fe, fh, Ink.FAR)
        f.line(hip, P(x - 0.03f, FLOOR), Ink.FAR)
        f.line(hip, knee, Ink.BODY); f.line(knee, ankle, Ink.BODY); f.line(ankle, P(x + 0.05f, FLOOR), Ink.BODY, 0.03f)
        f.line(hip, shoulder, if ("torso" in work) Ink.WORK else Ink.BODY, 0.05f)
        f.dot(head, HEAD, Ink.BODY)
        val e = at(shoulder, upper, UPPER); val h = at(e, fore, FORE)
        f.line(shoulder, e, if ("upper" in work) Ink.WORK else Ink.BODY)
        f.line(e, h, if ("fore" in work) Ink.WORK else Ink.BODY)
        return h to fh
    }

    /** Front view (seen from behind or in front); arm angles per side: 0 = down, 90 = out, 180 = up. */
    private fun front(f: Frame, upper: Float, fore: Float, work: Boolean = true, upperR: Float = upper, foreR: Float = fore) {
        val cx = 0.5f
        val pelvis = P(cx, FLOOR - THIGH - SHIN)
        val neck = P(cx, pelvis.y - TORSO)
        val sl = P(cx - 0.09f, neck.y + 0.02f); val sr = P(cx + 0.09f, neck.y + 0.02f)
        f.line(P(cx - 0.05f, pelvis.y), P(cx - 0.07f, FLOOR), Ink.BODY)
        f.line(P(cx + 0.05f, pelvis.y), P(cx + 0.07f, FLOOR), Ink.BODY)
        f.line(P(cx - 0.05f, pelvis.y), P(cx + 0.05f, pelvis.y), Ink.BODY)
        f.line(pelvis, neck, Ink.BODY, 0.05f)
        f.line(sl, sr, Ink.BODY, 0.045f)
        f.dot(P(cx, neck.y - NECK - HEAD + 0.01f), HEAD, Ink.BODY)
        val ink = if (work) Ink.WORK else Ink.BODY
        // right side: +x; left side mirrored
        val er = sr + P(sin(rad(upperR)), cos(rad(upperR))) * UPPER
        val hr = er + P(sin(rad(foreR)), cos(rad(foreR))) * FORE
        val el = sl + P(-sin(rad(upper)), cos(rad(upper))) * UPPER
        val hl = el + P(-sin(rad(fore)), cos(rad(fore))) * FORE
        f.line(sr, er, ink); f.line(er, hr, ink); f.line(sl, el, ink); f.line(el, hl, ink)
    }

    private fun rad(d: Float) = d * PI.toFloat() / 180f
    private fun bell(f: Frame, at: P) { f.dot(at, 0.032f, Ink.WEIGHT) }

    /** The rhythm of one repetition: (cue word, seconds). Derived from [Paces], so it matches the music. */
    fun phases(id: String): List<Pair<String, Float>> = Paces.of(id).let { pc -> pc.phases.map { it.word to it.beats * pc.secondsPerBeat } }

    /** Seconds per repetition at the demonstrated pace. */
    fun period(id: String): Float = Paces.of(id).seconds

    /** Position 0..1 away from the start for a paced exercise, [t] seconds into the loop. */
    private fun paced(id: String, t: Float): Float = Paces.of(id).let { it.out(t / it.secondsPerBeat) }

    fun frame(id: String, t: Float): Frame {
        val f = Frame()
        floor(f)
        when (id) {
            "curls" -> {
                val u = paced(id, t)
                val (h, fh) = standingSide(f, 0.45f, lerp(0f, 8f, u), lerp(2f, 155f, u), work = setOf("upper"))
                bell(f, fh); bell(f, h)
            }
            "press" -> {
                val u = paced(id, t)
                val (h, fh) = standingSide(f, 0.45f, lerp(25f, 176f, u), lerp(172f, 180f, u), work = setOf("upper", "fore"))
                bell(f, fh); bell(f, h)
            }
            "triceps" -> {
                val u = paced(id, t)
                val (h, _) = standingSide(f, 0.45f, lerp(172f, 162f, u), lerp(180f, 215f, u), farUpper = lerp(172f, 162f, u), farFore = lerp(180f, 215f, u), work = setOf("upper"))
                bell(f, h)
            }
            "pushups" -> {
                val u = paced(id, t)
                val toes = P(0.16f, FLOOR)
                fun body(alpha: Float): Triple<P, P, P> {
                    val d = P(cos(rad(alpha)), -sin(rad(alpha)))
                    val hip = toes + d * (THIGH + SHIN); val sh = hip + d * TORSO; val head = sh + d * (NECK + HEAD)
                    return Triple(hip, sh, head)
                }
                val (_, shTop, _) = body(17f)
                val hand = P(shTop.x, FLOOR)
                val (hip, sh, head) = body(lerp(17f, 6f, u))
                f.line(toes, hip, Ink.BODY); f.line(hip, sh, Ink.WORK, 0.05f); f.dot(head, HEAD, Ink.BODY)
                val elbow = ik(sh, hand, UPPER, FORE, 1f)
                f.line(sh, elbow, Ink.WORK); f.line(elbow, hand, Ink.BODY); f.line(hand, hand + P(0.04f, 0f), Ink.BODY, 0.025f)
            }
            "row" -> {
                val u = paced(id, t)
                f.line(P(0.04f, 0.63f), P(0.62f, 0.63f), Ink.SCENE, 0.04f)
                f.line(P(0.08f, 0.63f), P(0.08f, FLOOR), Ink.SCENE, 0.02f); f.line(P(0.58f, 0.63f), P(0.58f, FLOOR), Ink.SCENE, 0.02f)
                val hip = P(0.26f, 0.50f); val sh = P(0.52f, 0.51f); val head = sh + P(NECK + HEAD, 0.01f)
                // far knee and hand on the bench
                val kneeFar = P(0.24f, 0.61f); f.line(hip, kneeFar, Ink.FAR); f.line(kneeFar, P(0.06f, 0.61f), Ink.FAR)
                f.line(sh, ik(sh, P(0.55f, 0.61f), UPPER, FORE, -1f), Ink.FAR); f.line(ik(sh, P(0.55f, 0.61f), UPPER, FORE, -1f), P(0.55f, 0.61f), Ink.FAR)
                // near leg standing on the floor
                val foot = P(0.32f, FLOOR); val knee = ik(hip, foot, THIGH + 0.04f, SHIN + 0.04f, 1f)
                f.line(hip, knee, Ink.BODY); f.line(knee, foot, Ink.BODY)
                f.line(hip, sh, Ink.WORK, 0.05f); f.dot(head, HEAD, Ink.BODY)
                val hand = P(lerp(0.535f, 0.45f, u), lerp(sh.y + UPPER + FORE - 0.01f, 0.57f, u))
                val elbow = ik(sh, hand, UPPER, FORE, -1f)
                f.line(sh, elbow, Ink.WORK); f.line(elbow, hand, Ink.BODY); bell(f, hand)
            }
            "ytw" -> {
                val pc = Paces.of(id)
                val k = pc.liftIndex(t / pc.secondsPerBeat)
                val u = paced(id, t)
                val (up, fo) = when (k) { 0 -> 150f to 150f; 1 -> 90f to 90f; else -> 55f to 145f }
                front(f, lerp(15f, up, u), lerp(15f, fo, u))
            }
            "wallslides" -> {
                val u = paced(id, t)
                f.lines += Line(0.2f, 0.06f, 0.8f, 0.06f, 0.01f, Ink.SCENE)
                front(f, lerp(80f, 155f, u), lerp(172f, 168f, u))
            }
            "circles" -> {
                val w = t * 5f
                front(f, 90f, 90f + 32f * sin(w), work = true, upperR = 90f, foreR = 90f + 32f * cos(w))
            }
            "hang" -> {
                val u = cycle(t, 0.7f, 0.05f, 0.7f, 0.05f)
                front(f, lerp(100f, -25f, u), lerp(100f, -25f, u))
            }
            "tristretch" -> {
                val u = cycle(t, 1.5f, 1.5f, 1.2f, 0.4f)
                front(f, upper = lerp(172f, 180f, u), fore = lerp(-35f, -50f, u), work = true, upperR = 150f, foreR = lerp(238f, 246f, u))
            }
            "pecstretch" -> {
                val u = cycle(t, 1.5f, 1.5f, 1.2f, 0.4f)
                f.line(P(0.30f, 0.05f), P(0.30f, FLOOR), Ink.SCENE, 0.03f)
                standingSide(f, 0.42f, -88f, 178f, farUpper = 10f, farFore = 10f, work = setOf("torso"), lean = lerp(0f, 10f, u), hipShift = lerp(0f, 0.035f, u))
            }
            else -> standingSide(f, 0.45f, 0f, 0f)
        }
        return f
    }
}
