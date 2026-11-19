package com.thriple.define.engine

/** Training program: exercises, their progression ladders and the three-day rotation. Pure Kotlin. */

enum class DayType(val title: String, val tagline: String) {
    PUSH("PUSH DAY", "Chest, shoulders, triceps"),
    PULL("PULL DAY", "Biceps, back, rear delts"),
    RECHARGE("RECHARGE", "5 minutes of mobility. Still counts."),
}

enum class Area(val label: String) { ARMS("Arms"), CHEST("Chest"), SHOULDERS("Shoulders"), BACK("Back") }

enum class Effort(val label: String) { EASY("Easy"), SOLID("Solid"), BRUTAL("Brutal"), FAILED("Couldn't finish") }

enum class Measure { REPS, SECONDS }

/** One rung of a ladder. weightLb = 0 means bodyweight. */
data class Step(val sets: Int, val reps: Int, val weightLb: Int)

data class Exercise(
    val id: String,
    val name: String,
    val day: DayType,
    val areas: Set<Area>,
    val cue: String,
    val ladder: List<Step>,
    val perSide: Boolean = false,
    val measure: Measure = Measure.REPS,
) {
    val isStrength: Boolean get() = day != DayType.RECHARGE
}

/** Builds a ladder: at each weight, reps climb by one per rung. */
private class Seg(val weightLb: Int, val sets: Int, val fromReps: Int, val toReps: Int)

private fun ladder(vararg segs: Seg): List<Step> =
    segs.flatMap { s -> (s.fromReps..s.toReps).map { Step(s.sets, it, s.weightLb) } }

object Program {
    val rotation = listOf(DayType.PUSH, DayType.PULL, DayType.RECHARGE)

    val exercises: List<Exercise> = listOf(
        // PUSH
        Exercise(
            "pushups", "Push-ups", DayType.PUSH, setOf(Area.CHEST, Area.ARMS, Area.SHOULDERS),
            "Body in one straight line. Chest to a fist's height off the floor.",
            ladder(Seg(0, 3, 6, 20), Seg(0, 4, 16, 20), Seg(0, 5, 18, 20)),
        ),
        Exercise(
            "press", "Overhead press", DayType.PUSH, setOf(Area.SHOULDERS, Area.ARMS),
            "Brace your core, press straight up, don't arch your back.",
            ladder(Seg(30, 3, 3, 12), Seg(35, 3, 6, 10), Seg(40, 3, 6, 8)),
        ),
        Exercise(
            "triceps", "Overhead triceps extension", DayType.PUSH, setOf(Area.ARMS),
            "One dumbbell in both hands, elbows pointing up, lower behind your head.",
            ladder(Seg(30, 3, 5, 12), Seg(35, 3, 8, 12)),
        ),
        // PULL
        Exercise(
            "curls", "Curls", DayType.PULL, setOf(Area.ARMS),
            "Elbows pinned to your sides. No swinging.",
            ladder(Seg(30, 3, 3, 12), Seg(35, 3, 6, 10), Seg(40, 3, 6, 8)),
        ),
        Exercise(
            "row", "One-arm row", DayType.PULL, setOf(Area.BACK, Area.ARMS),
            "Hand and knee on a bench or chair, pull the dumbbell to your hip.",
            ladder(Seg(30, 3, 6, 12), Seg(35, 3, 8, 12), Seg(40, 3, 8, 10)),
            perSide = true,
        ),
        Exercise(
            "ytw", "Y-T-W raises", DayType.PULL, setOf(Area.BACK, Area.SHOULDERS),
            "Face down, raise your arms in a Y, then T, then W. One letter each = 1 rep.",
            ladder(Seg(0, 2, 6, 12), Seg(0, 3, 8, 12)),
        ),
        // RECHARGE
        Exercise(
            "pecstretch", "Doorway chest stretch", DayType.RECHARGE, setOf(Area.CHEST),
            "Forearm on the door frame, step through gently.",
            listOf(Step(1, 30, 0)), perSide = true, measure = Measure.SECONDS,
        ),
        Exercise(
            "wallslides", "Wall slides", DayType.RECHARGE, setOf(Area.SHOULDERS),
            "Back and arms against the wall, slide up and down slowly.",
            listOf(Step(1, 10, 0)),
        ),
        Exercise(
            "circles", "Wrist and elbow circles", DayType.RECHARGE, setOf(Area.ARMS),
            "Slow circles both ways. Loosen everything up.",
            listOf(Step(1, 30, 0)), measure = Measure.SECONDS,
        ),
        Exercise(
            "hang", "Arm swings and shoulder rolls", DayType.RECHARGE, setOf(Area.BACK, Area.SHOULDERS),
            "Big relaxed swings, then roll the shoulders back.",
            listOf(Step(1, 30, 0)), measure = Measure.SECONDS,
        ),
    )

    val byId: Map<String, Exercise> = exercises.associateBy { it.id }

    fun forDay(day: DayType): List<Exercise> = exercises.filter { it.day == day }

    val strength: List<Exercise> get() = exercises.filter { it.isStrength }

    /** Highest ladder index allowed with the dumbbells the user owns (bodyweight is always allowed). */
    fun maxIndex(ex: Exercise, weights: Set<Int>): Int {
        var max = 0
        ex.ladder.forEachIndexed { i, s -> if (s.weightLb == 0 || s.weightLb in weights) max = i }
        // Never jump past a missing weight: stop at the first rung whose weight isn't owned.
        val firstMissing = ex.ladder.indexOfFirst { it.weightLb != 0 && it.weightLb !in weights }
        return if (firstMissing == -1) max else maxOf(0, firstMissing - 1)
    }
}
