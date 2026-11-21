package com.thriple.define.engine

import kotlin.math.roundToInt

enum class Muscle(val label: String, val joint: Boolean = false) {
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    CHEST("Chest"),
    SHOULDERS("Shoulders"),
    UPPER_BACK("Upper back"),
    ELBOWS("Elbows", joint = true),
    SHOULDER_JOINT("Shoulder joint", joint = true),
    WRISTS("Wrists", joint = true),
}

/**
 * One body check: a level 0..4 per muscle or joint (index = [Muscle.ordinal]) and which ones are sharp pain.
 * 0 Fresh, 1 A bit tight, 2 Sore, 3 Very sore, 4 Wrecked.
 */
data class BodyCheck(val at: Long, val levels: List<Int>, val sharp: Set<Muscle> = emptySet()) {
    fun level(m: Muscle): Int = levels.getOrElse(m.ordinal) { 0 }

    /** How much it limits training: a joint ache counts one higher than muscle soreness; sharp pain is a 4. */
    fun strain(m: Muscle): Int = when {
        m in sharp && level(m) > 0 -> 4
        m.joint && level(m) >= 2 -> (level(m) + 1).coerceAtMost(4)
        else -> level(m)
    }

    val worst: Muscle? get() = Muscle.entries.filter { strain(it) >= 2 }.maxByOrNull { strain(it) }

    companion object {
        fun fresh(at: Long) = BodyCheck(at, List(Muscle.entries.size) { 0 })
    }
}

enum class Adjust { NORMAL, NO_STEP_UP, LIGHTER, DROP }

object Body {
    const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    /** The phone's offset from UTC, set by the app, so "which day" matches the local calendar. */
    @Volatile var zoneOffsetMillis: Long = 0

    fun dayOf(at: Long): Long = Math.floorDiv(at + zoneOffsetMillis, DAY)

    /** Muscles to ask about on [day]: the main ones from a strength workout 1–2 days earlier, if not yet checked today. */
    fun checkInDue(state: AppState, day: Long): List<Muscle> {
        val last = state.history.lastOrNull { it.type != DayType.RECHARGE } ?: return emptyList()
        if (day - last.day !in 1..2) return emptyList()
        if (state.body.any { dayOf(it.at) == day }) return emptyList()
        return trained(last.type)
    }

    /** "triceps and chest" */
    fun list(ms: List<Muscle>): String {
        val l = ms.map { it.label.lowercase() }
        return if (l.size <= 1) l.joinToString() else l.dropLast(1).joinToString(", ") + " and " + l.last()
    }
    val levelNames = listOf("Fresh", "A bit tight", "Sore", "Very sore", "Wrecked")

    /** How hard each exercise works each muscle and joint, 0..1. */
    private val loads: Map<String, Map<Muscle, Float>> = mapOf(
        "pushups" to mapOf(Muscle.CHEST to 1f, Muscle.TRICEPS to 0.6f, Muscle.SHOULDERS to 0.5f, Muscle.WRISTS to 0.4f, Muscle.ELBOWS to 0.3f),
        "press" to mapOf(Muscle.SHOULDERS to 1f, Muscle.TRICEPS to 0.6f, Muscle.SHOULDER_JOINT to 0.6f, Muscle.ELBOWS to 0.3f, Muscle.WRISTS to 0.2f, Muscle.UPPER_BACK to 0.2f),
        "triceps" to mapOf(Muscle.TRICEPS to 1f, Muscle.ELBOWS to 0.7f, Muscle.SHOULDER_JOINT to 0.3f),
        "curls" to mapOf(Muscle.BICEPS to 1f, Muscle.ELBOWS to 0.5f, Muscle.WRISTS to 0.4f),
        "row" to mapOf(Muscle.UPPER_BACK to 1f, Muscle.BICEPS to 0.6f, Muscle.SHOULDERS to 0.3f, Muscle.ELBOWS to 0.2f, Muscle.WRISTS to 0.2f),
        "ytw" to mapOf(Muscle.UPPER_BACK to 0.7f, Muscle.SHOULDERS to 0.5f, Muscle.SHOULDER_JOINT to 0.3f),
    )

    /** What each stretch or mobility move helps. */
    private val eases: Map<String, Set<Muscle>> = mapOf(
        "tristretch" to setOf(Muscle.TRICEPS, Muscle.ELBOWS, Muscle.SHOULDER_JOINT),
        "pecstretch" to setOf(Muscle.CHEST, Muscle.BICEPS, Muscle.SHOULDERS),
        "wallslides" to setOf(Muscle.SHOULDER_JOINT, Muscle.UPPER_BACK, Muscle.SHOULDERS),
        "circles" to setOf(Muscle.ELBOWS, Muscle.WRISTS),
        "hang" to setOf(Muscle.SHOULDERS, Muscle.UPPER_BACK, Muscle.SHOULDER_JOINT),
    )

    fun load(id: String, m: Muscle): Float = loads[id]?.get(m) ?: 0f

    fun eases(id: String, m: Muscle): Boolean = eases[id]?.contains(m) == true

    /** How hard a day type works a muscle (its heaviest exercise). */
    fun dayLoad(type: DayType, m: Muscle): Float = Program.forDay(type).maxOfOrNull { load(it.id, m) } ?: 0f

    /** The main muscles a day works, heaviest first. */
    fun trained(type: DayType): List<Muscle> =
        Muscle.entries.filter { !it.joint && dayLoad(type, it) >= 0.6f }.sortedByDescending { dayLoad(type, it) }

    /** What to do with one exercise given how the body feels, and the muscle responsible. */
    fun adjust(id: String, check: BodyCheck): Pair<Adjust, Muscle?> {
        var worst: Muscle? = null
        var score = 0f
        for (m in Muscle.entries) {
            val s = load(id, m) * check.strain(m)
            if (s > score) { score = s; worst = m }
        }
        // sharp pain anywhere the move loads noticeably: off the list
        val sharpHit = Muscle.entries.firstOrNull { it in check.sharp && check.level(it) > 0 && load(id, it) >= 0.3f }
        if (sharpHit != null) return Adjust.DROP to sharpHit
        val a = when {
            score >= 3.5f -> Adjust.DROP
            score >= 2.2f -> Adjust.LIGHTER
            score >= 1.5f -> Adjust.NO_STEP_UP
            else -> Adjust.NORMAL
        }
        return a to worst
    }

    // ------------------------------------------------------------------ recovery history

    data class Episode(val muscle: Muscle, val sessionAt: Long, val peak: Int, val hours: Float, val volume: Float)

    fun sessionTime(r: SessionRecord): Long = if (r.at > 0) r.at else (r.day * 24 + 8) * HOUR

    /**
     * Each time a workout made a muscle sore (rated 2+ afterwards): how many hours until it was rated back to
     * "a bit tight" or better. Stops looking at the next workout that works the same muscle.
     */
    fun episodes(state: AppState): List<Episode> {
        val sessions = state.history.filter { it.type != DayType.RECHARGE }.sortedBy { sessionTime(it) }
        val checks = state.body.sortedBy { it.at }
        val out = ArrayList<Episode>()
        for (m in Muscle.entries) {
            val relevant = sessions.filter { dayLoad(it.type, m) >= 0.5f }
            for ((i, s) in relevant.withIndex()) {
                val t0 = sessionTime(s)
                val end = relevant.getOrNull(i + 1)?.let { sessionTime(it) } ?: (t0 + 8 * 24 * HOUR)
                val window = checks.filter { it.at > t0 && it.at < end }
                val peakIdx = window.indices.maxByOrNull { window[it].level(m) } ?: continue
                val peak = window[peakIdx].level(m)
                if (peak < 2) continue
                val ready = window.drop(peakIdx + 1).firstOrNull { it.level(m) <= 1 } ?: continue
                out += Episode(m, t0, peak, (ready.at - t0) / HOUR.toFloat(), s.sets * dayLoad(s.type, m))
            }
        }
        return out.sortedBy { it.sessionAt }
    }

    /** Typical hours from workout to recovered for this person, or a textbook 48 h until there's data. */
    fun typicalHours(state: AppState, m: Muscle, eps: List<Episode> = episodes(state)): Float {
        val h = eps.filter { it.muscle == m }.takeLast(5).map { it.hours }.sorted()
        return if (h.isEmpty()) 48f else h[h.size / 2]
    }

    data class Forecast(val muscle: Muscle, val level: Int, val hoursLeft: Float)

    /** Muscles still recovering according to the latest check, with an estimate of hours until ready. */
    fun forecast(state: AppState, now: Long): List<Forecast> {
        val last = state.body.maxByOrNull { it.at } ?: return emptyList()
        if (now - last.at > 4 * 24 * HOUR) return emptyList()
        val eps = episodes(state)
        return Muscle.entries.filter { last.level(it) >= 2 }.map { m ->
            val lastSession = state.history.filter { dayLoad(it.type, m) >= 0.5f }.maxOfOrNull { sessionTime(it) }
            val left = if (lastSession == null) 24f
                else (lastSession + typicalHours(state, m, eps) * HOUR - now) / HOUR.toFloat()
            Forecast(m, last.level(m), left.coerceAtLeast(if (last.level(m) >= 3) 12f else 0f))
        }.sortedByDescending { it.level }
    }

    /** Average rating in 12-hour bins after workouts that worked the muscle (0–12 h, 12–24 h, ... 96 h+). */
    fun curve(state: AppState, m: Muscle): List<Float?> {
        val bins = Array(9) { ArrayList<Int>() }
        val sessions = state.history.filter { dayLoad(it.type, m) >= 0.5f }.map { sessionTime(it) }.sorted()
        for (c in state.body) {
            val t0 = sessions.lastOrNull { it < c.at } ?: continue
            val b = ((c.at - t0) / (12 * HOUR)).toInt().coerceAtMost(8)
            bins[b] += c.level(m)
        }
        return bins.map { if (it.isEmpty()) null else it.average().toFloat() }
    }

    /** Highest rating per day for the last [days] days ending [today] (null = no check that day). */
    fun calendar(state: AppState, m: Muscle, today: Long, days: Int): List<Int?> {
        val byDay = state.body.groupBy { dayOf(it.at) }
        return (0 until days).map { i -> byDay[today - days + 1 + i]?.maxOf { it.level(m) } }
    }

    // ------------------------------------------------------------------ the hero's reply

    /** What the app says back after a check, in its voice. */
    fun reply(check: BodyCheck, state: AppState, now: Long): String {
        val sharp = check.sharp.filter { check.level(it) > 0 }
        if (sharp.isNotEmpty()) {
            return "Sharp pain logged: ${names(sharp)}. Anything that loads it is off the list until it settles. " +
                "If it's still there in two weeks, get it looked at."
        }
        val joints = Muscle.entries.filter { it.joint && check.level(it) >= 2 }
        if (joints.isNotEmpty()) {
            return "Joint ache logged: ${names(joints)}. Tendons heal slower than muscle, so I'll keep the load off it. " +
                "Nothing heroic until it's quiet."
        }
        val wrecked = Muscle.entries.filter { check.level(it) >= 4 }
        val sore = Muscle.entries.filter { check.level(it) in 2..3 }
        val f = forecast(state.copy(body = state.body + check), now).associateBy { it.muscle }
        return when {
            wrecked.isNotEmpty() -> {
                val h = wrecked.maxOf { f[it]?.hoursLeft ?: 24f }
                "Copy that. ${names(wrecked)} benched for about ${days(h)}. Tomorrow's mission works around ${if (wrecked.size == 1) "it" else "them"}."
            }
            sore.isNotEmpty() -> "Logged. ${names(sore)} on light duty. I'll ease off where ${if (sore.size == 1) "it's" else "they're"} working."
            check.levels.any { it == 1 } -> "Logged. A little tight is the sign of work done. Full power available."
            else -> "All systems green. Hero is ready for action."
        }
    }

    private fun names(ms: List<Muscle>): String {
        val l = ms.map { it.label.lowercase() }
        val s = if (l.size <= 1) l.joinToString() else l.dropLast(1).joinToString(", ") + " and " + l.last()
        return s.replaceFirstChar { it.uppercase() }
    }

    fun days(hours: Float): String = when {
        hours <= 18f -> "a day"
        hours <= 36f -> "a day or two"
        else -> "${(hours / 24f).roundToInt().coerceAtLeast(2)} days"
    }
}
