package com.thriple.define.engine

import kotlin.random.Random

/** One planned exercise for today's session. */
data class PlannedExercise(val exercise: Exercise, val step: Step)

data class SessionPlan(
    val type: DayType,
    val items: List<PlannedExercise>,
    val gatedFrom: DayType?,          // planned day swapped to RECHARGE because that area is still sore
    val welcomeBack: Boolean,          // returned after 4+ days away
    val deload: Boolean,               // lighter week
) {
    val totalSets: Int get() = items.sumOf { it.step.sets }
}

/** Result of finishing a session: the new state plus what happened, for the reward screens. */
data class SessionOutcome(
    val state: AppState,
    val xpGained: Int,
    val stepUps: List<String>,         // exercise ids queued to step up (applied at next check-in)
    val stepBacks: List<String>,
    val streakBefore: Int,
    val shieldEarned: Boolean,
)

object Logic {
    const val WELCOME_BACK_DAYS = 4
    const val STAGES = 8

    /** Progress fraction needed for each hero stage. Front-loaded so the first unlocks come quickly. */
    val stageThresholds = listOf(0.0, 0.06, 0.14, 0.24, 0.36, 0.50, 0.68, 0.88)

    fun newState(today: Long) = AppState(startDay = today)

    /** Average ladder progress over the strength exercises, 0..1. */
    fun progress(state: AppState): Double {
        val ex = Program.strength
        return ex.sumOf { state.stepIndex(it.id).toDouble() / (it.ladder.size - 1).coerceAtLeast(1) } / ex.size
    }

    fun earnedStage(state: AppState): Int {
        val p = progress(state)
        return stageThresholds.indexOfLast { p >= it - 1e-9 } + 1
    }

    fun isDeloadWeek(state: AppState, today: Long): Boolean = ((today - state.startDay) / 7) % 5 == 4L

    fun plannedType(state: AppState): DayType = Program.rotation[state.rotationIndex % Program.rotation.size]

    private fun clampIndex(state: AppState, ex: Exercise, idx: Int): Int =
        idx.coerceIn(0, Program.maxIndex(ex, state.weights))

    private fun shift(state: AppState, ids: Collection<String>, delta: Int): AppState {
        if (ids.isEmpty()) return state
        val steps = state.steps.toMutableMap()
        for (id in ids) {
            val ex = Program.byId[id] ?: continue
            steps[id] = clampIndex(state, ex, state.stepIndex(id) + delta)
        }
        return state.copy(steps = steps)
    }

    /** Keeps the streak honest when the app opens: missed days are covered by shields or reset it. */
    fun refreshStreak(state: AppState, today: Long): AppState {
        if (state.streakCountedDay < 0 || state.streakCountedDay >= today - 1) return state
        val missed = (today - 1 - state.streakCountedDay).toInt()
        return if (missed <= state.shields) {
            state.copy(shields = state.shields - missed, streakCountedDay = today - 1)
        } else {
            state.copy(streak = 0, streakCountedDay = -1)
        }
    }

    /** Old-style check-in by area, kept for tests and callers that only know areas. */
    fun checkIn(state: AppState, today: Long, soreness: Map<Area, Int>): Pair<AppState, SessionPlan> {
        val levels = Muscle.entries.map { m ->
            val area = when (m) {
                Muscle.BICEPS, Muscle.TRICEPS, Muscle.ELBOWS, Muscle.WRISTS -> Area.ARMS
                Muscle.CHEST -> Area.CHEST
                Muscle.SHOULDERS, Muscle.SHOULDER_JOINT -> Area.SHOULDERS
                Muscle.UPPER_BACK -> Area.BACK
            }
            if (m.joint) 0 else soreness[area] ?: 0
        }
        val r = checkIn(state, today, BodyCheck(today * 24 * Body.HOUR + 8 * Body.HOUR, levels), record = false)
        return r.state to r.plan
    }

    /** A body check on its own (from the home screen or the evening nudge). First check of a day earns 15 XP. */
    fun logBody(state: AppState, check: BodyCheck): AppState {
        val day = Body.dayOf(check.at)
        val firstToday = state.body.none { Body.dayOf(it.at) == day }
        return state.copy(body = (state.body + check).takeLast(300), xp = state.xp + if (firstToday) 15 else 0)
    }

    class CheckInResult(
        val state: AppState,
        val plan: SessionPlan,
        /** The unadjusted plan for "train anyway", or null when nothing was changed. */
        val anyway: SessionPlan?,
        /** Plain-language reasons for each change. */
        val notes: List<String>,
    )

    /**
     * Applies a body check and plans today's session.
     * - Pending step-ups go through only for exercises the body is fine with; one that would be dropped
     *   steps back instead.
     * - Each exercise is kept, kept without a step-up, made a round lighter, or dropped, depending on how
     *   sore the muscles and joints it works are (see [Body.adjust]).
     * - If that leaves a strength day with fewer than two exercises, the other strength day is used when the
     *   body is ready for it, otherwise a Recharge day. The planned day then waits for next time.
     * - Very sore muscles get their stretch added at the end.
     */
    fun checkIn(state: AppState, today: Long, check: BodyCheck, record: Boolean = true): CheckInResult {
        var s = refreshStreak(state, today)
        val welcomeBack = s.lastSessionDay >= 0 && today - s.lastSessionDay >= WELCOME_BACK_DAYS
        if (welcomeBack) s = shift(s, Program.strength.map { it.id }, -1)

        val up = s.pendingUp.filter { Body.adjust(it, check).first == Adjust.NORMAL }
        val back = s.pendingUp.filter { Body.adjust(it, check).first == Adjust.DROP }
        val held = s.pendingUp - up.toSet() - back.toSet()
        s = shift(s, up, +1)
        s = shift(s, back, -1)
        s = s.copy(pendingUp = emptySet())
        if (record) {
            val firstToday = s.body.none { Body.dayOf(it.at) == Body.dayOf(check.at) }
            s = s.copy(body = (s.body + check).takeLast(300), xp = s.xp + if (firstToday) 15 else 0)
        }

        val planned = plannedType(s)
        val deloadWeek = isDeloadWeek(s, today)
        fun build(type: DayType, adjusted: Boolean): Pair<SessionPlan, List<String>> {
            val notes = ArrayList<String>()
            val deload = type != DayType.RECHARGE && deloadWeek
            val items = ArrayList<PlannedExercise>()
            for (ex in Program.forDay(type)) {
                val base = ex.ladder[clampIndex(s, ex, s.stepIndex(ex.id))]
                var step = if (deload) base.copy(sets = maxOf(1, (base.sets * 0.6).toInt())) else base
                if (adjusted && ex.isStrength) {
                    val (a, m) = Body.adjust(ex.id, check)
                    val why = m?.let { reason(it, check) } ?: ""
                    when (a) {
                        Adjust.DROP -> { notes += "${ex.name}: off today. $why"; continue }
                        Adjust.LIGHTER -> if (step.sets > 1) { step = step.copy(sets = step.sets - 1); notes += "${ex.name}: one round fewer. $why" }
                        Adjust.NO_STEP_UP -> if (ex.id in held) notes += "${ex.name}: staying at your current level for now. $why"
                        Adjust.NORMAL -> Unit
                    }
                }
                items += PlannedExercise(ex, step)
            }
            if (adjusted && type == DayType.RECHARGE) {
                // stretches for whatever hurts go first
                items.sortByDescending { pe -> Muscle.entries.count { check.strain(it) >= 2 && Body.eases(pe.exercise.id, it) } }
            }
            return SessionPlan(type, items, if (type != planned) planned else null, welcomeBack, deload) to notes
        }

        val original = build(planned, adjusted = false).first
        var (plan, notes) = build(planned, adjusted = true)
        if (planned != DayType.RECHARGE && plan.items.count { it.exercise.isStrength } < 2) {
            val other = if (planned == DayType.PUSH) DayType.PULL else DayType.PUSH
            val (alt, altNotes) = build(other, adjusted = true)
            val trainedSore = Body.trained(planned).filter { check.strain(it) >= 3 }.ifEmpty { Body.trained(planned) }
            if (alt.items.count { it.exercise.isStrength } >= 2) {
                plan = alt
                notes = listOf("Swapped to ${nice(other)} today: ${names(trainedSore)} need another day. ${nice(planned)} waits for next time.") + altNotes
            } else {
                plan = build(DayType.RECHARGE, adjusted = true).first
                notes = listOf("Swapped to a Recharge day: ${names(trainedSore)} need another day, and so does the rest. Stretches for the sore spots come first. Recharge keeps your streak.")
            }
        }
        // a finishing stretch for very sore muscles on a training day
        if (plan.type != DayType.RECHARGE) {
            val stretch = Program.forDay(DayType.RECHARGE).firstOrNull { ex ->
                Muscle.entries.any { !it.joint && check.strain(it) >= 3 && Body.eases(ex.id, it) }
            }
            if (stretch != null) {
                plan = plan.copy(items = plan.items + PlannedExercise(stretch, stretch.ladder[0]))
                notes = notes + "Added ${stretch.name.lowercase()} at the end for the sore spot."
            }
        }
        val changed = notes.isNotEmpty()
        return CheckInResult(s, plan, if (changed) original else null, notes)
    }

    private fun reason(m: Muscle, check: BodyCheck): String = when {
        m in check.sharp -> "${m.label} has sharp pain."
        m.joint -> "${m.label}: joint ache, keeping the load off."
        else -> "${m.label} ${if (check.level(m) >= 4) "are wrecked" else "are still recovering"}."
    }.replace("Chest are", "Chest is").replace("Upper back are", "Upper back is")

    private fun nice(t: DayType) = t.title.lowercase().replaceFirstChar { it.uppercase() }

    private fun names(ms: List<Muscle>): String {
        val l = ms.map { it.label.lowercase() }
        return if (l.size <= 1) l.joinToString() else l.dropLast(1).joinToString(", ") + " and " + l.last()
    }

    /**
     * Finishes a session. [completed] = exercise id -> true if every set was finished.
     * Easy/Solid with everything done queues a step-up (confirmed by the next check-in); Brutal holds;
     * an unfinished exercise or "Couldn't finish" steps back one rung.
     */
    fun completeSession(
        state: AppState,
        today: Long,
        plan: SessionPlan,
        completed: Map<String, Boolean>,
        effort: Effort?,
        reps: Int,
        inWindow: Boolean,
        now: Long = (today * 24 + 8) * Body.HOUR,
    ): SessionOutcome {
        var s = refreshStreak(state, today)
        val streakBefore = s.streak
        val strengthIds = plan.items.filter { it.exercise.isStrength }.map { it.exercise.id }
        val back = strengthIds.filter { completed[it] != true || effort == Effort.FAILED }
        val up = if (effort == Effort.EASY || effort == Effort.SOLID) strengthIds.filter { completed[it] == true } else emptyList()
        if (!plan.deload) s = shift(s, back, -1)
        s = s.copy(pendingUp = s.pendingUp + (if (plan.deload) emptyList() else up))

        // Rotation only advances when the planned day was actually done (a gated day waits).
        if (plan.gatedFrom == null) s = s.copy(rotationIndex = s.rotationIndex + 1)

        // Streak: one per day.
        var shieldEarned = false
        if (s.streakCountedDay != today) {
            val newStreak = if (s.streakCountedDay == today - 1) s.streak + 1 else 1
            shieldEarned = newStreak % 7 == 0 && s.shields < 2
            s = s.copy(
                streak = newStreak,
                bestStreak = maxOf(s.bestStreak, newStreak),
                streakCountedDay = today,
                shields = if (shieldEarned) s.shields + 1 else s.shields,
            )
        }

        val sets = plan.totalSets
        val xp = 100 + 10 * sets + (if (inWindow) 50 else 0)
        s = s.copy(
            xp = s.xp + xp,
            lastSessionDay = today,
            history = (s.history + SessionRecord(today, plan.type, sets, reps, effort, inWindow, at = now)).takeLast(400),
        )
        return SessionOutcome(s, xp, up, back, streakBefore, shieldEarned)
    }

    fun rollSurge(state: AppState, rng: Random): Pair<AppState, SurgeResult> {
        val r = Rewards.rollSurge(rng, state.streak)
        return state.copy(power = state.power + r.power) to r
    }
}
