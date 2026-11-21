package com.thriple.define.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

enum class Motion { LIFT, LOWER, HOLD, REST }

data class PacePhase(val word: String, val beats: Float, val motion: Motion)

/**
 * The rhythm of one repetition, measured in musical beats at a tempo. The music, the on-screen cue words,
 * the stick figure and the haptics all read from this, so they stay locked together.
 */
class Pace(val bpm: Float, val phases: List<PacePhase>) {
    val beats: Float = phases.sumOf { it.beats.toDouble() }.toFloat()
    val secondsPerBeat: Float get() = 60f / bpm
    val seconds: Float get() = beats * secondsPerBeat

    /** True when the movement starts at the top and goes down first (push-ups, overhead triceps). */
    val startsHigh: Boolean = phases.firstOrNull { it.motion == Motion.LIFT || it.motion == Motion.LOWER }?.motion == Motion.LOWER

    /** Index of the phase at [beat] (beats since the repetition started) and how far through it, 0..1. */
    fun phaseAt(beat: Float): Pair<Int, Float> {
        var b = beat - floor(beat / beats) * beats
        for ((i, p) in phases.withIndex()) {
            if (b < p.beats) return i to (b / p.beats)
            b -= p.beats
        }
        return phases.lastIndex to 1f
    }

    /** Height of the weight (or body), 0 = low, 1 = high, eased like a real movement. */
    fun height(beat: Float): Float {
        var h = if (startsHigh) 1f else 0f
        var b = beat - floor(beat / beats) * beats
        for (p in phases) {
            if (b < p.beats) {
                val u = ease(b / p.beats)
                return when (p.motion) {
                    Motion.LIFT -> h + (1f - h) * u
                    Motion.LOWER -> h * (1f - u)
                    else -> h
                }
            }
            b -= p.beats
            h = when (p.motion) { Motion.LIFT -> 1f; Motion.LOWER -> 0f; else -> h }
        }
        return h
    }

    /** How far from the starting position, 0..1 (what the stick figure animates). */
    fun out(beat: Float): Float = if (startsHigh) 1f - height(beat) else height(beat)

    /** Which lift of the repetition is current (0, 1, 2 for Y, T, W). */
    fun liftIndex(beat: Float): Int {
        val (i, _) = phaseAt(beat)
        return (phases.take(i + 1).count { it.motion == Motion.LIFT } - 1).coerceAtLeast(0)
    }

    private fun ease(u: Float) = 0.5f - 0.5f * cos(PI.toFloat() * u.coerceIn(0f, 1f))
}

object Paces {
    private fun p(word: String, beats: Float) = PacePhase(
        word, beats,
        when (word) {
            "UP", "PRESS", "PULL", "Y", "T", "W" -> Motion.LIFT
            "DOWN", "LOWER" -> Motion.LOWER
            "" -> Motion.REST
            else -> Motion.HOLD
        },
    )

    /**
     * Tempos sit in the 128–140 BPM range so one repetition is two bars of music: a lift of about a second,
     * a lowering of about two (slow lowering is safer for joints and tendons that heal slower after 40).
     */
    fun of(id: String): Pace = when (id) {
        "curls" -> Pace(132f, listOf(p("UP", 2f), p("HOLD", 1f), p("DOWN", 4f), p("", 1f)))
        "press" -> Pace(132f, listOf(p("PRESS", 2f), p("HOLD", 1f), p("LOWER", 4f), p("", 1f)))
        "triceps" -> Pace(132f, listOf(p("LOWER", 4f), p("", 1f), p("UP", 2f), p("", 1f)))
        "pushups" -> Pace(140f, listOf(p("DOWN", 4f), p("", 1f), p("UP", 2f), p("", 1f)))
        "row" -> Pace(132f, listOf(p("PULL", 2f), p("SQUEEZE", 1f), p("LOWER", 4f), p("", 1f)))
        "ytw" -> Pace(128f, listOf(
            p("Y", 1f), p("HOLD", 1f), p("LOWER", 1f), p("", 1f),
            p("T", 1f), p("HOLD", 1f), p("LOWER", 1f), p("", 1f),
            p("W", 1f), p("HOLD", 1f), p("LOWER", 1f), p("", 1f),
        ))
        "wallslides" -> Pace(128f, listOf(p("UP", 3f), p("HOLD", 1f), p("DOWN", 3f), p("", 1f)))
        else -> Pace(120f, listOf(p("GO", 2f), p("", 2f)))
    }
}
