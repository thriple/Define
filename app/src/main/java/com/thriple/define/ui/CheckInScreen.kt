package com.thriple.define.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.thriple.define.engine.Area
import kotlinx.coroutines.delay

private val levels = listOf("Fresh", "Fine", "Tight", "Sore", "Wrecked")
private val levelColors = listOf(Palette.Lime, Palette.Cyan, Palette.Gold, Palette.Orange, Palette.Magenta)

/** Two-second body check: how sore is each area? Drives step-ups and the recovery gate. */
@Composable
fun CheckInScreen(onDone: (Map<Area, Int>) -> Unit) {
    val soreness = remember { mutableStateMapOf<Area, Int>().apply { Area.entries.forEach { put(it, 1) } } }
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { for (i in 1..Area.entries.size) { delay(110); shown = i } }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))
        Text("BODY CHECK", style = Type.Big.copy(color = Palette.Cyan))
        Text("How does each area feel right now?", style = Type.Body, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Area.entries.forEachIndexed { i, area ->
            AnimatedVisibility(i < shown, enter = slideInHorizontally(spring(dampingRatio = 0.6f)) { it } + fadeIn()) {
                AreaRow(area.label, soreness[area] ?: 1) { soreness[area] = it }
            }
            Spacer(Modifier.height(14.dp))
        }
        Spacer(Modifier.weight(1f))
        HypeButton("LOCKED IN", Modifier.fillMaxWidth(), colors = listOf(Palette.Cyan, Palette.Violet)) {
            onDone(soreness.toMap())
        }
    }
}

@Composable
private fun AreaRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = Type.Sub, modifier = Modifier.weight(1f))
            Text(levels[value], style = Type.Sub.copy(color = levelColors[value]))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (lv in levels.indices) {
                val selected = lv == value
                val pop = remember { Animatable(1f) }
                LaunchedEffect(selected) {
                    if (selected) { pop.snapTo(1.25f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)) }
                }
                Box(
                    Modifier.weight(1f).height(38.dp)
                        .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) levelColors[lv] else Color.White.copy(alpha = 0.1f))
                        .bouncy { Sfx.play("tick", 0.8f + lv * 0.15f); Haptics.tick(); onChange(lv) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${lv}", style = Type.Sub.copy(fontSize = 15.sp, color = if (selected) Palette.Ink else Palette.Dim))
                }
            }
        }
    }
}
