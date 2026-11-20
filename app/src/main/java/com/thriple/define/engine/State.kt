package com.thriple.define.engine

/** Everything the app remembers. Immutable; logic returns modified copies. */
data class SessionRecord(
    val day: Long,           // epoch day
    val type: DayType,
    val sets: Int,
    val reps: Int,
    val effort: Effort?,
    val inWindow: Boolean,
)

data class AppState(
    val startDay: Long,
    val rotationIndex: Int = 0,
    val steps: Map<String, Int> = emptyMap(),        // exercise id -> ladder index
    val pendingUp: Set<String> = emptySet(),         // step up at next check-in if soreness allows
    val lastSessionDay: Long = -1,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val streakCountedDay: Long = -1,                 // last day that counts toward the streak (shields included)
    val shields: Int = 0,
    val xp: Int = 0,
    val power: Int = 0,
    val sinceElite: Int = 0,                         // pods since an Elite-or-better drop
    val sinceLegendary: Int = 0,                     // pods since a Legendary-or-better drop
    val gear: Map<String, Int> = emptyMap(),         // gear id -> copies owned
    val scrap: Int = 0,
    val shownStage: Int = 1,                         // hero stage the user has seen revealed
    val history: List<SessionRecord> = emptyList(),
    val weights: Set<Int> = setOf(30),
    val soundOn: Boolean = true,
    val remindersOn: Boolean = true,
    val seenPrimer: Boolean = false,
) {
    fun stepIndex(id: String): Int = steps[id] ?: 0
    fun doneOn(day: Long): Boolean = lastSessionDay == day
}
