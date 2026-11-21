package com.thriple.define.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thriple.define.engine.AppState
import com.thriple.define.engine.Logic
import com.thriple.define.engine.Measure
import com.thriple.define.engine.Program

@Composable
fun SettingsScreen(
    state: AppState,
    canNotify: Boolean,
    onWeights: (Set<Int>) -> Unit,
    onSound: (Boolean) -> Unit,
    onMusic: (Boolean) -> Unit,
    onReminders: (Boolean) -> Unit,
    onAskNotify: () -> Unit,
    onTestReminder: () -> Unit,
    onRecovery: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", style = Type.Title, modifier = Modifier.clip(RoundedCornerShape(12.dp)).bouncy(onBack).padding(8.dp))
            Text("SETTINGS", style = Type.Title, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(16.dp))

        Section("DUMBBELLS YOU OWN") {
            Text("The plan only moves to a heavier weight if you own it.", style = Type.Body)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (w in listOf(25, 30, 35, 40, 45, 50)) {
                    val on = w in state.weights
                    Chip("$w", on, Modifier.weight(1f)) {
                        val next = if (on) state.weights - w else state.weights + w
                        if (next.isNotEmpty()) onWeights(next)
                    }
                }
            }
        }
        Section("REMINDERS") {
            Text("Mornings 7:00–7:50, lunch if you missed the morning, evenings 8:15 and 9:00.", style = Type.Body)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (state.remindersOn) "ON" else "OFF", state.remindersOn, Modifier.weight(1f)) { onReminders(!state.remindersOn) }
                Chip("TEST", false, Modifier.weight(1f)) { onTestReminder() }
            }
            if (!canNotify) {
                Spacer(Modifier.height(10.dp))
                Chip("ALLOW NOTIFICATIONS", true, Modifier.fillMaxWidth()) { onAskNotify() }
            }
        }
        Section("SOUND") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (state.soundOn) "SOUND ON" else "ALL MUTED", state.soundOn, Modifier.weight(1f)) { onSound(!state.soundOn) }
                Chip(if (state.musicOn && state.soundOn) "MUSIC ON" else "MUSIC OFF", state.musicOn && state.soundOn, Modifier.weight(1f)) { onMusic(!state.musicOn) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (Music.otherAudio && state.musicOn) "Something else was already playing, so the music is staying quiet. Cues still play."
                else "Music follows the workout. With music off you still get the countdown, the guide tone and the beeps. If you're already playing your own music, Define stays quiet.",
                style = Type.Body,
            )
        }
        Section("RECOVERY") {
            Text("How long each muscle takes to heal, from your body checks.", style = Type.Body)
            Spacer(Modifier.height(10.dp))
            Chip("RECOVERY CHARTS ›", true, Modifier.fillMaxWidth()) { onRecovery() }
        }
        Section("YOUR PLAN RIGHT NOW") {
            for (ex in Program.strength) {
                val i = state.stepIndex(ex.id).coerceAtMost(Program.maxIndex(ex, state.weights))
                val step = ex.ladder[i]
                val load = if (step.weightLb > 0) " @ ${step.weightLb} lb" else ""
                val unit = if (ex.measure == Measure.SECONDS) "s" else ""
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(ex.name, style = Type.Body, modifier = Modifier.weight(1f))
                    Text("${step.sets} rounds of ${step.reps}$unit$load", style = Type.Sub.copy(fontSize = 14.sp, color = Palette.Cyan))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Overall progress: ${(Logic.progress(state) * 100).toInt()}%  ·  best streak ${state.bestStreak}  ·  ${state.history.size} missions", style = Type.Label)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp)).padding(16.dp),
    ) {
        Text(title, style = Type.Label.copy(color = Palette.Gold))
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Chip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (on) Palette.Cyan else Color.White.copy(alpha = 0.1f))
            .bouncy { Sfx.play("tick"); Haptics.tick(); onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Sub.copy(fontSize = 14.sp, color = if (on) Palette.Ink else Palette.Dim))
    }
}
