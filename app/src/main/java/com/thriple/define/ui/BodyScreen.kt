package com.thriple.define.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thriple.define.engine.Body
import com.thriple.define.engine.BodyCheck
import com.thriple.define.engine.Muscle
import kotlinx.coroutines.delay

val levelColors = listOf(Palette.Lime, Palette.Cyan, Palette.Gold, Palette.Orange, Palette.Magenta)
private val SharpRed = Color(0xFFFF3B3B)

/**
 * Body check: every muscle and joint on one screen, all starting at Fresh (or where the last check left
 * them, if it was recent). Tap a dot to set how it feels; anything Sore or worse can be marked as sharp pain.
 */
@Composable
fun BodyCheckScreen(previous: BodyCheck?, preWorkout: Boolean, onBack: (() -> Unit)?, onDone: (BodyCheck) -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val recent = previous?.takeIf { now - it.at < 36 * Body.HOUR }
    val levels = remember { mutableStateListOf<Int>().apply { Muscle.entries.forEach { add(recent?.level(it) ?: 0) } } }
    val sharp = remember { mutableStateListOf<Muscle>().apply { recent?.sharp?.let { addAll(it) } } }
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { for (i in 1..Muscle.entries.size + 2) { delay(60); shown = i } }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Text("←", style = Type.Title, modifier = Modifier.clip(RoundedCornerShape(12.dp)).bouncy(onBack).padding(8.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (preWorkout) "BODY CHECK" else "HOW'S YOUR BODY?", style = Type.Big.copy(fontSize = 28.sp, color = Palette.Cyan))
                Text(
                    "Tap anything that isn't fresh. The rest can stay green.",
                    style = Type.Body.copy(fontSize = 13.sp), textAlign = TextAlign.Center,
                )
            }
            if (onBack != null) Spacer(Modifier.width(40.dp))
        }
        previous?.let {
            val h = ((now - it.at) / Body.HOUR).toInt()
            Text(
                if (recent != null) "Starting from your check ${if (h < 1) "just now" else "$h h ago"}" else "Last check: ${h / 24} days ago",
                style = Type.Label.copy(fontSize = 11.sp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            var row = 0
            for ((group, joint) in listOf("MUSCLES" to false, "JOINTS" to true)) {
                Text(group, style = Type.Label.copy(color = Palette.Gold), modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp))
                for (m in Muscle.entries.filter { it.joint == joint }) {
                    val i = row++
                    AnimatedVisibility(i < shown, enter = slideInHorizontally(spring(dampingRatio = 0.7f)) { it / 2 } + fadeIn()) {
                        MuscleRow(
                            m, levels[m.ordinal], m in sharp,
                            onLevel = { lv -> levels[m.ordinal] = lv; if (lv < 2) sharp.remove(m) },
                            onSharp = { if (m in sharp) sharp.remove(m) else sharp.add(m) },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val anySore = levels.any { it >= 2 }
        HypeButton(
            if (preWorkout) (if (anySore) "PLAN AROUND IT" else "ALL GOOD · LET'S GO") else "LOG IT",
            Modifier.fillMaxWidth(), colors = listOf(Palette.Cyan, Palette.Violet),
        ) {
            onDone(BodyCheck(System.currentTimeMillis(), levels.toList(), sharp.toSet()))
        }
    }
}

@Composable
private fun MuscleRow(m: Muscle, value: Int, sharp: Boolean, onLevel: (Int) -> Unit, onSharp: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, (if (value >= 2) levelColors[value] else Color.White).copy(alpha = if (value >= 2) 0.5f else 0.1f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(112.dp)) {
            Text(m.label.uppercase(), style = Type.Sub.copy(fontSize = 13.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Body.levelNames[value], style = Type.Label.copy(fontSize = 11.sp, color = Palette.Dim))
                if (value >= 2) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (sharp) "SHARP ✓" else "SHARP?",
                        style = Type.Label.copy(fontSize = 10.sp, color = if (sharp) Palette.White else SharpRed),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (sharp) SharpRed else Color.Transparent)
                            .border(1.dp, SharpRed, RoundedCornerShape(8.dp))
                            .bouncy { Haptics.tick(); onSharp() }
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (lv in 0..4) {
                val selected = lv == value
                val pop = remember { Animatable(1f) }
                LaunchedEffect(selected) {
                    if (selected) { pop.snapTo(1.3f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)) }
                }
                Box(
                    Modifier.weight(1f).height(34.dp)
                        .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) levelColors[lv] else levelColors[lv].copy(alpha = 0.12f))
                        .bouncy { Sfx.play("tick", 0.8f + lv * 0.15f); Haptics.tick(); onLevel(lv) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$lv", style = Type.Sub.copy(fontSize = 14.sp, color = if (selected) Palette.Ink else Palette.Dim))
                }
            }
        }
    }
}

/** The changes the body check made to today's mission, with the reasons, and a way to train anyway. */
@Composable
fun AdjustScreen(title: String, notes: List<String>, onGo: () -> Unit, onAnyway: () -> Unit) {
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { for (i in 1..notes.size) { delay(140); shown = i; Haptics.tick() } }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text("MISSION ADJUSTED", style = Type.Big.copy(fontSize = 30.sp, color = Palette.Gold), textAlign = TextAlign.Center)
        Text(title, style = Type.Title.copy(color = Palette.Cyan), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Training around soreness keeps you consistent. Training through it is how small aches become long breaks.", style = Type.Body, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            notes.forEachIndexed { i, n ->
                AnimatedVisibility(i < shown, enter = slideInHorizontally(spring(dampingRatio = 0.65f)) { it } + fadeIn()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f)).padding(12.dp),
                    ) {
                        Text("›", style = Type.Title.copy(color = Palette.Gold, fontSize = 20.sp), modifier = Modifier.width(18.dp))
                        Text(n, style = Type.Body.copy(color = Palette.White))
                    }
                }
            }
        }
        HypeButton("LET'S GO", Modifier.fillMaxWidth(), colors = listOf(Palette.Magenta, Palette.Orange), onClick = onGo)
        Text(
            "TRAIN ANYWAY (FULL PLAN)",
            style = Type.Label.copy(color = Palette.Dim),
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).bouncy(onAnyway).padding(14.dp),
        )
    }
}

/** The app's reply after a body check: a stamp and a line in its voice, sliding down from the top. */
@Composable
fun ReplyBanner(text: String?, onDismiss: () -> Unit) {
    LaunchedEffect(text) { if (text != null) { delay(6500); onDismiss() } }
    AnimatedVisibility(
        text != null,
        enter = slideInVertically(spring(dampingRatio = 0.6f, stiffness = 300f)) { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Column(
            Modifier.statusBarsPadding().padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Palette.InkDeep.copy(alpha = 0.94f))
                .border(1.5.dp, Palette.Lime.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                .bouncy(onDismiss)
                .padding(16.dp),
        ) {
            Text("✓ RECOVERY LOG UPDATED", style = Type.Label.copy(color = Palette.Lime))
            Spacer(Modifier.height(6.dp))
            Text(text ?: "", style = Type.Sub.copy(fontSize = 16.sp, color = Palette.White, lineHeight = 21.sp))
        }
    }
}
