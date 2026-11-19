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

    /**
     * Applies the morning check-in (soreness 0..4 per area) and plans today's session.
     * Pending step-ups go through only if their areas are fresh (<= 1); a 4 steps that exercise back.
     * If the planned day's areas are still very sore (>= 3), today becomes RECHARGE and the plan waits.
     */
    fun checkIn(state: AppState, today: Long, soreness: Map<Area, Int>): Pair<AppState, SessionPlan> {
        var s = refreshStreak(state, today)
        val welcomeBack = s.lastSessionDay >= 0 && today - s.lastSessionDay >= WELCOME_BACK_DAYS
        if (welcomeBack) s = shift(s, Program.strength.map { it.id }, -1)

        fun sore(ex: Exercise) = ex.areas.maxOf { soreness[it] ?: 0 }
        val up = s.pendingUp.filter { id -> Program.byId[id]?.let { sore(it) <= 1 } == true }
        val back = s.pendingUp.filter { id -> Program.byId[id]?.let { sore(it) >= 4 } == true }
        s = shift(s, up, +1)
        s = shift(s, back, -1)
        s = s.copy(pendingUp = emptySet())

        val planned = plannedType(s)
        val plannedSore = Program.forDay(planned).flatMap { it.areas }.toSet().maxOf { soreness[it] ?: 0 }
        val type = if (planned != DayType.RECHARGE && plannedSore >= 3) DayType.RECHARGE else planned
        val deload = type != DayType.RECHARGE && isDeloadWeek(s, today)
        val items = Program.forDay(type).map { ex ->
            val base = ex.ladder[clampIndex(s, ex, s.stepIndex(ex.id))]
            val step = if (deload) base.copy(sets = maxOf(1, (base.sets * 0.6).toInt())) else base
            PlannedExercise(ex, step)
        }
        return s to SessionPlan(type, items, if (type != planned) planned else null, welcomeBack, deload)
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
            history = (s.history + SessionRecord(today, plan.type, sets, reps, effort, inWindow)).takeLast(400),
        )
        return SessionOutcome(s, xp, up, back, streakBefore, shieldEarned)
    }

    fun rollSurge(state: AppState, rng: Random): Pair<AppState, SurgeResult> {
        val r = Rewards.rollSurge(rng, state.streak)
        return state.copy(power = state.power + r.power) to r
    }
}
