package com.thriple.define.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thriple.define.engine.AppState
import com.thriple.define.engine.DayType
import com.thriple.define.engine.Logic
import com.thriple.define.engine.Program
import com.thriple.define.engine.Ranks
import com.thriple.define.engine.Rewards
import com.thriple.define.engine.Windows
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun HomeScreen(state: AppState, today: Long, onStart: () -> Unit, onSettings: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = LocalTime.now() } }
    val minute = now.hour * 60 + now.minute
    val done = state.doneOn(today)
    val planned = Logic.plannedType(state)
    val momentum = Rewards.momentum(state.streak)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top HUD: rank + XP bar, power, settings
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Ranks.name(state.xp).uppercase(), style = Type.Sub.copy(fontSize = 15.sp, color = Palette.Gold))
                Spacer(Modifier.height(5.dp))
                XpBar(Ranks.progress(state.xp))
            }
            Spacer(Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.End) {
                RollingNumber(state.power, Type.Title.copy(fontSize = 22.sp, color = Palette.Cyan), suffix = " ⚡")
                Text("POWER", style = Type.Label.copy(fontSize = 10.sp))
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).bouncy { Sfx.play("tick"); onSettings() },
                contentAlignment = Alignment.Center,
            ) { Text("⚙", style = Type.Sub.copy(fontSize = 20.sp)) }
        }

        Spacer(Modifier.height(10.dp))
        StreakRow(state.streak, momentum, state.shields)

        // The hero
        HeroView(
            stage = state.shownStage,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            aura = if (done) Palette.Lime else Palette.Cyan,
            energy = ((momentum - 1.0) / 2.0).toFloat().coerceIn(0.15f, 1f),
        )
        Text("STAGE ${state.shownStage} / ${Logic.STAGES}", style = Type.Label.copy(color = Palette.Gold))
        Spacer(Modifier.height(10.dp))

        MissionCard(state, done, planned, minute, onStart)
    }
}

@Composable
private fun XpBar(progress: Float) {
    val p by animateFloatAsState(progress, tween(1200, easing = FastOutSlowInEasing), label = "xp")
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.12f))) {
        Box(
            Modifier.fillMaxWidth(p.coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(listOf(Palette.Gold, Palette.Orange, Palette.Magenta)))
        )
    }
}

@Composable
private fun StreakRow(streak: Int, momentum: Double, shields: Int) {
    val inf = rememberInfiniteTransition(label = "flame")
    val flicker by inf.animateFloat(0.9f, 1.15f, infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("🔥", fontSize = 22.sp, modifier = Modifier.graphicsLayer { scaleX = flicker; scaleY = flicker })
        RollingNumber(streak, Type.Title.copy(fontSize = 22.sp, color = Palette.Orange), suffix = if (streak == 1) " DAY" else " DAYS")
        Text("x" + "%.1f".format(momentum), style = Type.Sub.copy(color = Palette.Lime))
        repeat(shields) { Text("🛡", fontSize = 18.sp) }
    }
}

@Composable
private fun MissionCard(state: AppState, done: Boolean, planned: DayType, minute: Int, onStart: () -> Unit) {
    val window = Windows.current(minute)
    val windowText = when {
        done -> "Mission complete. Your hero is recovering for tomorrow."
        window != null -> "${window.label} window: ${window.end - minute} min left" + if (window.label == "Morning") " · Early-Bird bonus live" else ""
        else -> {
            val next = Windows.next(minute)
            "Next window: ${next.label.lowercase()} at ${clock(next.start)} · you can still train any time"
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.05f))))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (done) {
            Text("MISSION COMPLETE", style = Type.Title.copy(color = Palette.Lime))
            Spacer(Modifier.height(6.dp))
            Text(windowText, style = Type.Body, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Next up: ${planned.title}", style = Type.Sub.copy(color = Palette.Cyan))
        } else {
            Text("TODAY", style = Type.Label)
            Text(planned.title, style = Type.Big.copy(fontSize = 34.sp, color = Palette.Cyan))
            Text(planned.tagline, style = Type.Body, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(Program.forDay(planned).joinToString("  ·  ") { it.name }, style = Type.Label.copy(fontSize = 11.sp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(windowText, style = Type.Body.copy(fontSize = 13.sp, color = if (window != null) Palette.Lime else Palette.Dim), textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            HypeButton("START MISSION", Modifier.fillMaxWidth(), onClick = onStart)
        }
    }
}

fun clock(minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12:${"%02d".format(minuteOfDay % 60)} ${if (h < 12) "AM" else "PM"}"
}
