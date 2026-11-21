package com.thriple.define.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thriple.define.engine.Effort
import com.thriple.define.engine.PodResult
import com.thriple.define.engine.Rarity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- effort

@Composable
fun EffortScreen(onPick: (Effort) -> Unit) {
    val options = listOf(
        Effort.EASY to listOf(Palette.Lime, Palette.Cyan),
        Effort.SOLID to listOf(Palette.Cyan, Palette.Violet),
        Effort.BRUTAL to listOf(Palette.Orange, Palette.Magenta),
        Effort.FAILED to listOf(Color(0xFF5A5878), Color(0xFF3A3858)),
    )
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { for (i in 1..options.size) { delay(120); shown = i } }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("MISSION DONE", style = Type.Big.copy(color = Palette.Lime))
        Spacer(Modifier.height(6.dp))
        Text("How did that feel?", style = Type.Sub)
        Text("Easy or Solid moves you up next time, if your body agrees at the next check-in.", style = Type.Body, textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        options.forEachIndexed { i, (effort, colors) ->
            AnimatedVisibility(i < shown, enter = slideInVertically(spring(dampingRatio = 0.6f)) { it } + fadeIn()) {
                HypeButton(effort.label.uppercase(), Modifier.fillMaxWidth(), colors = colors, pulse = false) { onPick(effort) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------------------------------------------------------------- supply pod

private val rarityCycle = listOf(Rarity.STANDARD, Rarity.ENHANCED, Rarity.ELITE, Rarity.LEGENDARY, Rarity.COSMIC)

@Composable
fun PodScreen(
    pod: PodResult,
    xpGained: Int,
    rankUp: String?,
    shieldEarned: Boolean,
    streak: Int,
    onContinue: () -> Unit,
) {
    val fx = LocalFx.current
    val scope = rememberCoroutineScope()
    val time by rememberFrameTime()
    var taps by remember { mutableIntStateOf(0) }
    var opened by remember { mutableStateOf(false) }
    var center by remember { mutableStateOf(Offset.Zero) }
    val drop = remember { Animatable(-1400f) }
    val wobble = remember { Animatable(0f) }
    val podScale = remember { Animatable(1f) }
    val seed = remember { Random.nextInt() }
    val glowColor = Palette.rarity(pod.rarity)

    LaunchedEffect(Unit) {
        Sfx.play("whoosh")
        drop.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 180f))
    }
    LaunchedEffect(Unit) { delay(520); Sfx.play("boom", 1.3f, 0.6f); Haptics.thud(); fx.shake(14f); fx.ring(center, Palette.Cyan, 600f) }

    fun tap() {
        if (opened) return
        taps += 1
        Sfx.play("crack", 0.8f + taps * 0.15f)
        if (taps < 3) Music.crack(taps - 1)
        Haptics.click()
        fx.burst(center, listOf(Palette.White, glowColor), count = 10 + taps * 6, speed = 700f)
        scope.launch {
            wobble.snapTo(10f + taps * 6f)
            wobble.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 500f))
        }
        if (taps >= 3) {
            opened = true
            scope.launch {
                podScale.animateTo(1.25f, tween(160))
                if (!Music.reveal(pod.rarity.ordinal)) { Sfx.play("boom"); Sfx.play("reveal") }
                Haptics.rumble()
                fx.flash(if (pod.rarity >= Rarity.ELITE) glowColor else Color.White, 0.7f, 0.9f)
                fx.ring(center, glowColor, 1400f, 40f)
                fx.burst(center, listOf(glowColor, Palette.Magenta, Palette.Cyan, Palette.Lime, Palette.Gold), count = 70, speed = 1600f)
                fx.shake(30f)
                fx.confetti(if (pod.rarity >= Rarity.LEGENDARY) 220 else 120)
                if (rankUp != null) { delay(900); if (!Music.rankUp()) Sfx.play("levelup"); fx.flash(Palette.Gold, 0.6f, 0.5f) }
            }
        }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.Center) {
        // Rays behind the reveal
        Canvas(Modifier.fillMaxSize()) {
            if (!opened) return@Canvas
            val c = Offset(size.width / 2f, size.height * 0.42f)
            rotate(time * 20f, c) {
                for (i in 0 until 16) {
                    rotate(i * 22.5f, c) {
                        drawRect(
                            Brush.verticalGradient(listOf(glowColor.copy(alpha = 0.35f), Color.Transparent), startY = c.y, endY = c.y - size.height),
                            topLeft = Offset(c.x - 22f, c.y - size.height), size = Size(44f, size.height),
                        )
                    }
                }
            }
        }

        if (!opened) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SUPPLY POD", style = Type.Title.copy(color = Palette.Cyan))
                if (pod.pityTriggered) Text("It's glowing hot...", style = Type.Sub.copy(color = Palette.Gold))
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.size(230.dp, 270.dp)
                        .onGloballyPositioned { center = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) }
                        .graphicsLayer {
                            translationY = drop.value
                            rotationZ = wobble.value * sin(time * 50f) * 0.6f
                            scaleX = podScale.value; scaleY = podScale.value
                        }
                        .bouncy { tap() },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val t = time
                        // Teaser glow: cycles through rarity colours, faster with each crack.
                        val cycle = rarityCycle[((t * (1.5f + taps * 2.5f)).toInt()) % rarityCycle.size]
                        val glow = if (taps == 0) Palette.Cyan else Palette.rarity(cycle)
                        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.5f + 0.1f * taps), Color.Transparent)), radius = size.minDimension * 0.85f)
                        val body = Size(size.width * 0.78f, size.height * 0.86f)
                        val tl = Offset((size.width - body.width) / 2, (size.height - body.height) / 2)
                        drawRoundRect(
                            Brush.verticalGradient(listOf(Color(0xFF3A3A6E), Color(0xFF14123A), Color(0xFF2A2860))),
                            topLeft = tl, size = body, cornerRadius = CornerRadius(60f, 60f),
                        )
                        // seams
                        for (k in 1..3) {
                            val y = tl.y + body.height * k / 4f
                            drawLine(glow.copy(alpha = 0.6f + 0.4f * sin(t * 4f + k)), Offset(tl.x + 16f, y), Offset(tl.x + body.width - 16f, y), strokeWidth = 6f, cap = StrokeCap.Round)
                        }
                        drawRoundRect(glow.copy(alpha = 0.8f), topLeft = tl, size = body, cornerRadius = CornerRadius(60f, 60f), style = Stroke(5f))
                        // cracks leak light
                        val rnd = Random(seed)
                        repeat(taps * 2) {
                            val p = Path()
                            var x = tl.x + body.width * (0.2f + 0.6f * rnd.nextFloat())
                            var y = tl.y + body.height * (0.15f + 0.7f * rnd.nextFloat())
                            p.moveTo(x, y)
                            repeat(5) {
                                val a = rnd.nextFloat() * 6.28f
                                x += cos(a) * 40f; y += sin(a) * 40f
                                p.lineTo(x, y)
                            }
                            drawPath(p, glow, style = Stroke(14f, cap = StrokeCap.Round))
                            drawPath(p, Color.White, style = Stroke(4f, cap = StrokeCap.Round))
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(if (taps == 0) "TAP TO CRACK IT OPEN" else "AGAIN! ${3 - taps} MORE", style = Type.Sub.copy(color = Palette.Gold))
            }
        } else {
            RevealCard(pod, xpGained, rankUp, shieldEarned, streak, onContinue)
        }
    }
}

@Composable
private fun RevealCard(pod: PodResult, xpGained: Int, rankUp: String?, shieldEarned: Boolean, streak: Int, onContinue: () -> Unit) {
    val c = Palette.rarity(pod.rarity)
    val pop = remember { Animatable(0.2f) }
    var showButton by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 220f))
        delay(700); showButton = true
    }
    Column(
        Modifier.padding(24.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(pod.rarity.label.uppercase(), style = Type.Huge.copy(fontSize = 46.sp, color = c))
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Palette.Ink.copy(alpha = 0.75f))
                .border(3.dp, c, RoundedCornerShape(26.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(pod.gear.slot.label.uppercase(), style = Type.Label)
            Text(pod.gear.name, style = Type.Title.copy(color = c), textAlign = TextAlign.Center)
            Text(if (pod.duplicate) "Duplicate · +${pod.scrap} scrap" else "NEW FOR YOUR SUIT LOCKER", style = Type.Sub.copy(fontSize = 13.sp, color = if (pod.duplicate) Palette.Dim else Palette.Lime))
            Spacer(Modifier.height(16.dp))
            RollingNumber(pod.power, Type.Big.copy(color = Palette.Cyan), prefix = "+", suffix = " POWER")
            RollingNumber(xpGained + pod.xp, Type.Title.copy(color = Palette.Gold), prefix = "+", suffix = " XP")
            if (streak > 1) Text("Momentum x" + "%.1f".format(com.thriple.define.engine.Rewards.momentum(streak)) + " applied", style = Type.Label.copy(color = Palette.Orange))
            if (shieldEarned) { Spacer(Modifier.height(8.dp)); Text("+1 FORCE FIELD 🛡", style = Type.Sub.copy(color = Palette.Cyan)) }
            if (rankUp != null) { Spacer(Modifier.height(8.dp)); Text("RANK UP: ${rankUp.uppercase()}", style = Type.Title.copy(color = Palette.Gold)) }
        }
        Spacer(Modifier.height(22.dp))
        AnimatedVisibility(showButton, enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) {
            HypeButton("CONTINUE", Modifier.fillMaxWidth(), colors = listOf(c, Palette.Magenta), onClick = onContinue)
        }
    }
}

// ---------------------------------------------------------------- transformation

/** The body-stage unlock: charge up, white-out, the bigger hero bursts in. */
@Composable
fun TransformScreen(from: Int, to: Int, onDone: () -> Unit) {
    val fx = LocalFx.current
    val time by rememberFrameTime()
    var revealed by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }
    val charge = remember { Animatable(0f) }
    val heroScale = remember { Animatable(1f) }
    var center by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) {
        val scored = Music.charge(2.4f)
        for (i in 0 until 5) { if (!scored) Sfx.play("surge", 0.6f + i * 0.25f, 0.7f); Haptics.tick(); delay(420) }
    }
    LaunchedEffect(Unit) {
        charge.animateTo(1f, tween(2300, easing = LinearEasing))
        fx.flash(Color.White, 1.1f, 1f)
        if (!Music.boom()) { Sfx.play("boom"); Sfx.play("levelup") }
        Haptics.rumble()
        delay(180)
        revealed = true
        fx.ring(center, Palette.Cyan, 1500f, 50f)
        fx.ring(center, Palette.Magenta, 1100f, 30f)
        fx.burst(center, listOf(Palette.Cyan, Palette.Magenta, Palette.Gold, Palette.Lime, Color.White), count = 90, speed = 1800f)
        fx.confetti(220)
        fx.shake(36f)
        heroScale.snapTo(1.35f)
        heroScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 160f))
        delay(600); showButton = true
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Text(if (revealed) "NEW FORM UNLOCKED" else "POWER LEVEL RISING", style = Type.Title.copy(color = if (revealed) Palette.Gold else Palette.Cyan), textAlign = TextAlign.Center)
        Text(if (revealed) "STAGE $to" else "STAGE $from", style = Type.Huge.copy(fontSize = 52.sp))
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .onGloballyPositioned { center = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (revealed) return@Canvas
                val c = Offset(size.width / 2f, size.height / 2f)
                val k = charge.value
                val rnd = Random(3)
                repeat(60) {
                    val ang = rnd.nextFloat() * 6.28f + time * (0.5f + rnd.nextFloat())
                    val dist = size.maxDimension * (0.7f - ((time * (0.4f + rnd.nextFloat()) + rnd.nextFloat()) % 1f) * 0.65f)
                    drawCircle(
                        listOf(Palette.Cyan, Palette.Magenta, Palette.Gold)[it % 3].copy(alpha = 0.3f + 0.7f * k),
                        radius = 3f + 6f * k, center = Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist),
                    )
                }
            }
            val jitter = if (revealed) 0f else charge.value * charge.value * 18f
            HeroView(
                stage = if (revealed) to else from,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationX = jitter * sin(time * 80f)
                    translationY = jitter * cos(time * 67f)
                    scaleX = heroScale.value; scaleY = heroScale.value
                },
                aura = if (revealed) Palette.Gold else Palette.Cyan,
                energy = if (revealed) 1f else charge.value,
                pump = if (revealed) 1f else charge.value * 0.8f,
                glow = 1f + 2f * charge.value,
            )
        }
        AnimatedVisibility(showButton, enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) {
            HypeButton("LET'S GO", Modifier.fillMaxWidth(), colors = listOf(Palette.Gold, Palette.Magenta), onClick = onDone)
        }
    }
}
