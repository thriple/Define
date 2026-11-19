package com.thriple.define.engine

/** Training windows and reminder nudges, in minutes after midnight. */
enum class Window(val label: String, val start: Int, val end: Int) {
    MORNING("Morning", 7 * 60, 7 * 60 + 50),
    LUNCH("Lunch", 11 * 60 + 30, 14 * 60),
    EVENING("Evening", 19 * 60, 21 * 60 + 30);

    fun contains(minute: Int): Boolean = minute in start until end
}

data class Nudge(val minute: Int, val title: String, val text: String, val lunchOnly: Boolean = false)

object Windows {
    fun current(minute: Int): Window? = Window.entries.firstOrNull { it.contains(minute) }

    fun next(minute: Int): Window = Window.entries.firstOrNull { it.start > minute } ?: Window.MORNING

    val nudges = listOf(
        Nudge(7 * 60, "Mission is live", "Your hero is suiting up. 10 minutes before work?"),
        Nudge(7 * 60 + 30, "20 minutes left", "The Early-Bird bonus is fading. Quick mission?"),
        Nudge(7 * 60 + 45, "Last call", "The Snooze is winning. One set and you beat it."),
        Nudge(12 * 60, "Home for lunch?", "You missed the morning. A lunch mission keeps the streak alive.", lunchOnly = true),
        Nudge(20 * 60 + 15, "Swim done?", "Your mission is waiting. Arms and chest, about 10 minutes."),
        Nudge(21 * 60, "Last call tonight", "Your streak ends at midnight. A Recharge counts too."),
    )

    /**
     * Next nudge strictly after [minute] on [day], as (epochDay, nudge). Nudges for a day on which a mission
     * is done are skipped. Lunch only fires if the morning was missed, which is the same condition.
     */
    fun nextNudge(day: Long, minute: Int, doneOn: (Long) -> Boolean): Pair<Long, Nudge> {
        var d = day
        var after = minute
        repeat(3) {
            if (!doneOn(d)) {
                val n = nudges.firstOrNull { it.minute > after }
                if (n != null) return d to n
            }
            d += 1
            after = -1
        }
        return d to nudges.first()
    }
}
