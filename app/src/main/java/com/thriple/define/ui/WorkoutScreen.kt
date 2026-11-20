package com.thriple.define.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thriple.define.engine.Coaching
import com.thriple.define.engine.Measure
import com.thriple.define.engine.PlannedExercise
import com.thriple.define.engine.Rarity
import com.thriple.define.engine.SessionPlan
import com.thriple.define.engine.SurgeResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REST_SECONDS = 60

private enum class Phase { WORK, REST }
private enum class View { PRIMER, INTRO, WORK, REST }

/** One "slot" = one set (per side for one-sided moves). */
private data class Slot(val item: PlannedExercise, val set: Int, val sets: Int, val side: String?)

private fun slotsFor(plan: SessionPlan): List<Slot> = plan.items.flatMap { item ->
    val sides = if (item.exercise.perSide) listOf("LEFT", "RIGHT") else listOf(null)
    (1..item.step.sets).flatMap { set -> sides.map { side -> Slot(item, set, item.step.sets, side) } }
}

@Composable
fun WorkoutScreen(
    plan: SessionPlan,
    primerSeen: Boolean,
    onPrimerSeen: () -> Unit,
    onSurge: () -> SurgeResult,
    onFinish: (completed: Map<String, Boolean>, reps: Int) -> Unit,
) {
    val fx = LocalFx.current
    val slots = remember(plan) { slotsFor(plan) }
    var index by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.WORK) }
    var totalReps by remember { mutableIntStateOf(0) }
    val completed = remember { mutableStateMapOf<String, Boolean>().apply { plan.items.forEach { put(it.exercise.id, true) } } }
    val slot = slots[index]
    val isLast = index == slots.lastIndex
    var primerDone by remember { mutableStateOf(primerSeen) }
    val introduced = remember { mutableStateListOf<String>() }
    val view = when {
        !primerDone -> View.PRIMER
        phase == Phase.REST -> View.REST
        slot.item.exercise.id !in introduced -> View.INTRO
        else -> View.WORK
    }

    fun advance() {
        if (isLast) {
            onFinish(completed.toMap(), totalReps)
        } else {
            val restNext = slot.item.exercise.measure == Measure.REPS && slots[index + 1].side != "RIGHT"
            index += 1
            phase = if (restNext) Phase.REST else Phase.WORK
            Sfx.play("whoosh")
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SessionProgress(index, slots.size, plan.type.title)
        Spacer(Modifier.height(14.dp))
        AnimatedContent(
            targetState = index to view,
            transitionSpec = {
                (slideInHorizontally(spring(dampingRatio = 0.75f, stiffness = 300f)) { it } + fadeIn()) togetherWith
                    (slideOutHorizontally(tween(260)) { -it } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "slot",
        ) { (i, v) ->
            val s = slots[i]
            if (v == View.PRIMER) {
                PrimerPanel { primerDone = true; onPrimerSeen() }
            } else if (v == View.INTRO) {
                IntroPanel(s) { introduced.add(s.item.exercise.id) }
            } else if (v == View.REST) {
                RestPanel(next = s, onDone = { phase = Phase.WORK; Sfx.play("pop") })
            } else {
                WorkPanel(
                    slot = s,
                    onRep = { totalReps += 1 },
                    onSetDone = { at ->
                        val surge = onSurge()
                        celebrateSet(fx, at, surge)
                        advance()
                    },
                    onHowTo = { introduced.remove(s.item.exercise.id) },
                    onGiveUp = {
                        completed[s.item.exercise.id] = false
                        Sfx.play("fail"); Haptics.thud()
                        advance()
                    },
                )
            }
        }
    }
}

private fun celebrateSet(fx: FxState, at: Offset, surge: SurgeResult) {
    val c = Palette.rarity(surge.rarity)
    Sfx.play("set")
    Haptics.click()
    fx.ring(at, c, 700f, 26f)
    fx.burst(at, listOf(c, Palette.Magenta, Palette.Cyan, Palette.Lime), count = 34, speed = 1100f)
    fx.text("+${surge.power} POWER", Offset(at.x, at.y - 40f), c, 34f)
    if (surge.rarity >= Rarity.ENHANCED) {
        Sfx.play("surge")
        fx.text("${surge.rarity.label.uppercase()} SURGE!", Offset(at.x, at.y + 120f), c, 30f)
    }
    if (surge.rarity >= Rarity.ELITE) {
        fx.flash(c, 0.6f, 0.6f)
        fx.shake(22f)
        fx.confetti(90)
        Haptics.rumble()
    }
}

@Composable
private fun SessionProgress(done: Int, total: Int, title: String) {
    val p by animateFloatAsState(done.toFloat() / total, tween(600, easing = FastOutSlowInEasing), label = "p")
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Type.Sub.copy(color = Palette.Cyan), modifier = Modifier.weight(1f))
            Text("$done / $total DONE", style = Type.Label)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.12f))) {
            Box(
                Modifier.fillMaxWidth(p.coerceAtLeast(0.02f)).height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(Palette.Cyan, Palette.Lime)))
            )
        }
    }
}

private fun targetLabel(s: Slot): String {
    val step = s.item.step
    val amount = if (s.item.exercise.measure == Measure.SECONDS) "HOLD ${step.reps} SEC" else "DO IT ${step.reps} TIMES"
    val weight = if (step.weightLb > 0) " · ${step.weightLb} LB" else ""
    val side = s.side?.let { " · ${it} SIDE" } ?: ""
    val round = if (s.sets > 1) "ROUND ${s.set} OF ${s.sets} · " else ""
    return "$round$amount$weight$side"
}

@Composable
private fun WorkPanel(slot: Slot, onRep: () -> Unit, onSetDone: (Offset) -> Unit, onHowTo: () -> Unit, onGiveUp: () -> Unit) {
    val ex = slot.item.exercise
    val guide = Coaching.guide(ex.id)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ex.name.uppercase(), style = Type.Big.copy(fontSize = 28.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(targetLabel(slot), style = Type.Sub.copy(color = Palette.Gold, fontSize = 15.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PictogramView(ex.id, Modifier.size(96.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(guide.oneTime, style = Type.Body.copy(fontSize = 14.sp, lineHeight = 19.sp))
                Spacer(Modifier.height(6.dp))
                Text("HOW TO DO IT ›", style = Type.Label.copy(color = Palette.Cyan), modifier = Modifier.clip(RoundedCornerShape(10.dp)).bouncy(onHowTo).padding(vertical = 4.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        if (ex.measure == Measure.SECONDS) {
            TimedTarget(slot.item.step.reps, onSetDone)
        } else {
            RepTarget(slot.item.step.reps, onRep, onSetDone)
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (ex.measure == Measure.SECONDS) "SKIP" else "CAN'T FINISH THIS ROUND",
            style = Type.Label.copy(color = Palette.Dim),
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).bouncy(onGiveUp).padding(14.dp),
        )
    }
}

/** Tap once per rep. Every tap pops, bursts and climbs in pitch; the ring fills toward the target. */
@Composable
private fun RepTarget(target: Int, onRep: () -> Unit, onDone: (Offset) -> Unit) {
    val fx = LocalFx.current
    val scope = rememberCoroutineScope()
    var reps by remember { mutableIntStateOf(0) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var center by remember { mutableStateOf(Offset.Zero) }
    val punch = remember { Animatable(1f) }
    val fill by animateFloatAsState(reps.toFloat() / target, spring(dampingRatio = 0.55f, stiffness = 260f), label = "fill")
    val time by rememberFrameTime()
    val repCallback by rememberUpdatedState(onRep)
    val doneCallback by rememberUpdatedState(onDone)

    Box(
        Modifier.size(280.dp)
            .onGloballyPositioned { origin = it.positionInRoot(); center = origin + Offset(it.size.width / 2f, it.size.height / 2f) }
            .graphicsLayer { scaleX = punch.value; scaleY = punch.value }
            .pointerInput(target) {
                detectTapGestures { pos ->
                    if (reps >= target) return@detectTapGestures
                    reps += 1
                    repCallback()
                    val at = origin + pos
                    Sfx.play("tick", 0.75f + 0.09f * reps)
                    Haptics.tick()
                    fx.burst(at, listOf(Palette.Cyan, Palette.Magenta, Palette.Lime), count = 14, speed = 650f)
                    fx.ring(center, Palette.Cyan.copy(alpha = 0.7f), 420f, 10f)
                    scope.launch { punch.snapTo(0.9f); punch.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 600f)) }
                    if (reps >= target) {
                        scope.launch { delay(180); doneCallback(center) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time
            val stroke = 22.dp.toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawCircle(Brush.radialGradient(listOf(Palette.Magenta.copy(alpha = 0.35f), Color.Transparent)), radius = size.minDimension / 2)
            drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            rotate(-90f) {
                drawArc(
                    Brush.sweepGradient(listOf(Palette.Cyan, Palette.Lime, Palette.Gold, Palette.Magenta, Palette.Cyan)),
                    0f, 360f * fill.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            rotate(t * 40f) {
                for (k in 0 until 24) {
                    rotate(k * 15f) {
                        drawCircle(Palette.Cyan.copy(alpha = 0.25f + 0.25f * kotlin.math.sin(t * 3f + k)), 3.dp.toPx(), Offset(size.width / 2f, 3.dp.toPx()))
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = reps,
                transitionSpec = { (scaleIn(spring(dampingRatio = 0.4f), initialScale = 1.8f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
                label = "reps",
            ) { r -> Text("$r", style = Type.Huge.copy(fontSize = 96.sp)) }
            Text(if (reps == 0) "TAP AFTER EACH ONE" else "OF $target", style = Type.Label)
        }
    }
}

/** Timed holds: press start, the ring drains, done. */
@Composable
private fun TimedTarget(seconds: Int, onDone: (Offset) -> Unit) {
    var running by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(seconds) }
    var center by remember { mutableStateOf(Offset.Zero) }
    val frac by animateFloatAsState(left.toFloat() / seconds, tween(950), label = "t")
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (left > 0) {
            delay(1000)
            left -= 1
            if (left in 1..3) { Sfx.play("tick", 1.4f); Haptics.tick() }
        }
        onDone(center)
    }
    Box(
        Modifier.size(260.dp).onGloballyPositioned { center = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            rotate(-90f) {
                drawArc(Brush.sweepGradient(listOf(Palette.Lime, Palette.Cyan, Palette.Lime)), 0f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        if (running) {
            Text("$left", style = Type.Huge.copy(fontSize = 88.sp))
        } else {
            HypeButton("START", colors = listOf(Palette.Lime, Palette.Cyan)) { running = true }
        }
    }
}

@Composable
private fun RestPanel(next: Slot, onDone: () -> Unit) {
    var left by remember { mutableIntStateOf(REST_SECONDS) }
    val frac by animateFloatAsState(left.toFloat() / REST_SECONDS, tween(950), label = "rest")
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left -= 1
            if (left in 1..3) { Sfx.play("tick", 1.2f); Haptics.tick() }
        }
        onDone()
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("REST", style = Type.Label.copy(fontSize = 16.sp))
        Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                rotate(-90f) {
                    drawArc(Palette.Violet, 0f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Text("$left", style = Type.Huge)
        }
        Spacer(Modifier.height(18.dp))
        Text("NEXT", style = Type.Label)
        Text(next.item.exercise.name.uppercase(), style = Type.Title, textAlign = TextAlign.Center)
        Text(targetLabel(next), style = Type.Sub.copy(color = Palette.Gold, fontSize = 14.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        HypeButton("I'M READY", colors = listOf(Palette.Violet, Palette.Magenta), pulse = false, onClick = onDone)
    }
}

@Composable
private fun PrimerPanel(onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Coaching.primerTitle, style = Type.Title.copy(color = Palette.Cyan), textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Coaching.primer.forEachIndexed { i, line ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("${i + 1}", style = Type.Sub.copy(color = Palette.Gold), modifier = Modifier.width(26.dp))
                Text(line, style = Type.Body.copy(color = Palette.White))
            }
        }
        Spacer(Modifier.height(18.dp))
        HypeButton("GOT IT", Modifier.fillMaxWidth(), colors = listOf(Palette.Cyan, Palette.Violet), onClick = onDone)
    }
}

/** Shown before the first round of each exercise (and from "How to do it"). */
@Composable
private fun IntroPanel(slot: Slot, onGo: () -> Unit) {
    val ex = slot.item.exercise
    val g = Coaching.guide(ex.id)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("NEXT UP", style = Type.Label)
        Text(ex.name.uppercase(), style = Type.Big.copy(fontSize = 28.sp), textAlign = TextAlign.Center)
        PictogramView(ex.id, Modifier.size(190.dp))
        Text(targetLabel(slot), style = Type.Sub.copy(color = Palette.Gold, fontSize = 15.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(g.oneTime, style = Type.Sub.copy(fontSize = 15.sp, color = Palette.White), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        g.steps.forEachIndexed { i, step ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("${i + 1}", style = Type.Sub.copy(color = Palette.Cyan), modifier = Modifier.width(24.dp))
                Text(step, style = Type.Body.copy(color = Palette.White))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("PACE: " + g.pace, style = Type.Body.copy(color = Palette.Lime), modifier = Modifier.fillMaxWidth())
        if (g.avoid.isNotEmpty()) Text("AVOID: " + g.avoid, style = Type.Body.copy(color = Palette.Orange), modifier = Modifier.fillMaxWidth())
        g.easier?.let { Text(it, style = Type.Body, modifier = Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(16.dp))
        HypeButton("LET'S GO", Modifier.fillMaxWidth(), colors = listOf(Palette.Magenta, Palette.Orange), onClick = onGo)
    }
}
