package com.thriple.define.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thriple.define.engine.AppState
import com.thriple.define.engine.Body
import com.thriple.define.engine.Muscle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/** Chart colours for the five muscles, validated for colour-blind separation on the dark surface. */
private val muscleColor = mapOf(
    Muscle.BICEPS to Color(0xFF1A9FC0),
    Muscle.TRICEPS to Color(0xFFE8478F),
    Muscle.CHEST to Color(0xFFC0801F),
    Muscle.SHOULDERS to Color(0xFF8463F0),
    Muscle.UPPER_BACK to Color(0xFF469F38),
)
private val muscles = muscleColor.keys.toList()
private val Grid = Color.White.copy(alpha = 0.10f)
private val Axis = Color.White.copy(alpha = 0.45f)

/** Settings → Recovery: how long each muscle takes to heal, and how that changes over time. */
@Composable
fun RecoveryScreen(state: AppState, today: Long, onBack: () -> Unit) {
    val eps = remember(state) { Body.episodes(state) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", style = Type.Title, modifier = Modifier.clip(RoundedCornerShape(12.dp)).bouncy(onBack).padding(8.dp))
            Text("RECOVERY", style = Type.Title, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Built from your body checks. A muscle counts as recovered when you rate it \"a bit tight\" or better again. " +
                "Expect these times to drop over the first months: muscles get less sore once they're used to the work.",
            style = Type.Body.copy(fontSize = 13.sp),
        )
        Spacer(Modifier.height(14.dp))

        // table view: the numbers behind the charts
        Card("TYPICAL HEALING TIME") {
            for (m in Muscle.entries) {
                val mine = eps.filter { it.muscle == m }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(muscleColor[m] ?: Color.White.copy(alpha = 0.5f)))
                    Spacer(Modifier.width(8.dp))
                    Text(m.label, style = Type.Body.copy(color = Palette.White), modifier = Modifier.weight(1f))
                    Text(
                        if (mine.isEmpty()) "no data yet" else "~${Body.typicalHours(state, m, eps).roundToInt()} h  ·  ${mine.size}×",
                        style = Type.Sub.copy(fontSize = 13.sp, color = if (mine.isEmpty()) Palette.Dim else Palette.White),
                    )
                }
            }
        }

        if (eps.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "The charts fill in as you log. Do a body check the evening after a workout and the evening after that " +
                    "(the reminder will ask), and within a couple of weeks you'll see how long each muscle really takes.",
                style = Type.Body.copy(color = Palette.Gold), textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(12.dp))
        Card("HEALING TIME OVER THE WEEKS", legend = true) {
            HealingOverTime(eps, grow.value)
        }
        Spacer(Modifier.height(12.dp))
        Card("SORENESS AFTER A WORKOUT", legend = true) {
            Curves(state, grow.value)
        }
        Spacer(Modifier.height(12.dp))
        Card("LAST 4 WEEKS") {
            Calendar(state, today, grow.value)
        }
        Spacer(Modifier.height(12.dp))
        Card("BIGGER WORKOUT, LONGER HEAL?", legend = true) {
            WorkloadScatter(eps, grow.value)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Card(title: String, legend: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Ink.copy(alpha = 0.85f)).padding(14.dp)) {
        Text(title, style = Type.Label.copy(color = Palette.Gold))
        if (legend) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                muscles.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(muscleColor.getValue(m)))
                        Spacer(Modifier.width(3.dp))
                        Text(m.label, style = Type.Label.copy(fontSize = 10.sp, color = Palette.Dim))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Plot area with a y axis (labels on the left) and horizontal grid lines. Returns the plot rect origin/size. */
private fun DrawScope.frame(yMax: Float, yTicks: List<Float>, yLabel: (Float) -> String): Pair<Offset, Size> {
    val left = 34.dp.toPx(); val bottom = 18.dp.toPx(); val top = 6.dp.toPx()
    val o = Offset(left, top)
    val s = Size(size.width - left - 4.dp.toPx(), size.height - top - bottom)
    val paint = android.graphics.Paint().apply { color = android.graphics.Color.argb(150, 255, 255, 255); textSize = 10.dp.toPx(); isAntiAlias = true; textAlign = android.graphics.Paint.Align.RIGHT }
    for (v in yTicks) {
        val y = o.y + s.height * (1f - v / yMax)
        drawLine(Grid, Offset(o.x, y), Offset(o.x + s.width, y), 1f)
        drawContext.canvas.nativeCanvas.drawText(yLabel(v), o.x - 5.dp.toPx(), y + 3.dp.toPx(), paint)
    }
    drawLine(Axis, Offset(o.x, o.y + s.height), Offset(o.x + s.width, o.y + s.height), 1.5f)
    return o to s
}

private fun DrawScope.xLabel(text: String, x: Float, align: android.graphics.Paint.Align = android.graphics.Paint.Align.CENTER) {
    val paint = android.graphics.Paint().apply { color = android.graphics.Color.argb(150, 255, 255, 255); textSize = 10.dp.toPx(); isAntiAlias = true; textAlign = align }
    drawContext.canvas.nativeCanvas.drawText(text, x, size.height - 3.dp.toPx(), paint)
}

@Composable
private fun Readout(text: String?) {
    Text(text ?: "Tap a point for details", style = Type.Label.copy(fontSize = 11.sp, color = if (text == null) Palette.Dim.copy(alpha = 0.6f) else Palette.White))
}

@Composable
private fun HealingOverTime(eps: List<Body.Episode>, grow: Float) {
    var picked by remember { mutableStateOf<Body.Episode?>(null) }
    val fmt = remember { DateTimeFormatter.ofPattern("MMM d") }
    if (eps.isEmpty()) { Empty(); return }
    val t0 = eps.first().sessionAt; val t1 = maxOf(eps.last().sessionAt, t0 + 7 * 24 * Body.HOUR)
    val yMax = ((eps.maxOf { it.hours } / 24f).toInt() + 1) * 24f
    val plot = remember { PlotBox() }
    fun px(e: Body.Episode): Offset {
        val (o, s) = plot.v
        return Offset(o.x + s.width * (e.sessionAt - t0).toFloat() / (t1 - t0), o.y + s.height * (1f - e.hours / yMax))
    }
    Readout(picked?.let { "${it.muscle.label}: ${it.hours.roundToInt()} h to recover after the ${date(it.sessionAt, fmt)} workout (peaked at ${Body.levelNames[it.peak].lowercase()})" })
    Canvas(Modifier.fillMaxWidth().height(170.dp).pointerInput(eps) {
        detectTapGestures { tap -> picked = eps.minByOrNull { (px(it) - tap).getDistance() }?.takeIf { (px(it) - tap).getDistance() < 60f } }
    }) {
        plot.v = frame(yMax, (0..(yMax / 24f).toInt()).map { it * 24f }) { "${it.toInt()}h" }
        val (o, s) = plot.v
        xLabel(date(t0, fmt), o.x, android.graphics.Paint.Align.LEFT)
        xLabel(date(t1, fmt), o.x + s.width, android.graphics.Paint.Align.RIGHT)
        clipRect(right = o.x + s.width * grow) {
            for (m in muscles) {
                val pts = eps.filter { it.muscle == m }.map { px(it) }
                if (pts.size >= 2) {
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                    drawPath(path, muscleColor.getValue(m), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                pts.forEach { p -> drawCircle(Palette.Ink, 6.dp.toPx(), p); drawCircle(muscleColor.getValue(m), 4.dp.toPx(), p) }
            }
        }
        picked?.let { e -> drawCircle(Color.White, 7.dp.toPx(), px(e), style = Stroke(2f)) }
    }
}

@Composable
private fun Curves(state: AppState, grow: Float) {
    val curves = remember(state) { muscles.associateWith { Body.curve(state, it) } }
    if (curves.values.all { c -> c.all { it == null } }) { Empty(); return }
    var picked by remember { mutableStateOf<Pair<Muscle, Int>?>(null) }
    val plot = remember { PlotBox() }
    fun px(bin: Int, v: Float): Offset {
        val (o, s) = plot.v
        return Offset(o.x + s.width * bin / 8f, o.y + s.height * (1f - v / 4f))
    }
    Readout(picked?.let { (m, b) -> "${m.label}, ${b * 12}–${b * 12 + 12} h after: average ${"%.1f".format(curves.getValue(m)[b] ?: 0f)} (${Body.levelNames[((curves.getValue(m)[b] ?: 0f)).roundToInt()].lowercase()})" })
    Canvas(Modifier.fillMaxWidth().height(170.dp).pointerInput(curves) {
        detectTapGestures { tap ->
            var best: Pair<Muscle, Int>? = null; var bd = 60f
            for ((m, c) in curves) c.forEachIndexed { b, v -> if (v != null) { val d = (px(b, v) - tap).getDistance(); if (d < bd) { bd = d; best = m to b } } }
            picked = best
        }
    }) {
        plot.v = frame(4f, listOf(0f, 1f, 2f, 3f, 4f)) { listOf("fresh", "tight", "sore", "v.sore", "wreck")[it.toInt()] }
        val (o, s) = plot.v
        for (b in 0..8 step 2) xLabel(if (b == 8) "96h+" else "${b * 12}h", o.x + s.width * b / 8f)
        clipRect(right = o.x + s.width * grow) {
            for ((m, c) in curves) {
                val pts = c.mapIndexedNotNull { b, v -> v?.let { px(b, it) } }
                if (pts.size >= 2) drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, muscleColor.getValue(m), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                pts.forEach { drawCircle(muscleColor.getValue(m), 3.5.dp.toPx(), it) }
            }
        }
    }
}

@Composable
private fun Calendar(state: AppState, today: Long, grow: Float) {
    val days = 28
    val rows = remember(state) { Muscle.entries.map { it to Body.calendar(state, it, today, days) } }
    val workouts = remember(state) { state.history.map { it.day }.toSet() }
    var picked by remember { mutableStateOf<Pair<Muscle, Int>?>(null) }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE MMM d") }
    Readout(picked?.let { (m, d) -> "${m.label}, ${LocalDate.ofEpochDay(today - days + 1 + d).format(fmt)}: " + (rows.first { it.first == m }.second[d]?.let { Body.levelNames[it].lowercase() } ?: "no check") })
    val labelW = 92.dp
    Row {
        Column(Modifier.width(labelW).padding(top = 12.dp)) {
            rows.forEach { (m, _) -> Box(Modifier.height(18.dp), contentAlignment = Alignment.CenterStart) { Text(m.label, style = Type.Label.copy(fontSize = 10.sp, color = Palette.Dim)) } }
        }
        Canvas(Modifier.weight(1f).height((12 + 18 * rows.size).dp).pointerInput(rows) {
            detectTapGestures { tap ->
                val cw = size.width / days.toFloat(); val top = 12.dp.toPx(); val rh = 18.dp.toPx()
                val d = (tap.x / cw).toInt(); val r = ((tap.y - top) / rh).toInt()
                picked = if (d in 0 until days && r in rows.indices) rows[r].first to d else null
            }
        }) {
            val cw = size.width / days; val top = 12.dp.toPx(); val rh = 18.dp.toPx(); val gap = 2.dp.toPx()
            for (d in 0 until days) {
                if ((today - days + 1 + d) in workouts) drawCircle(Palette.Lime, 2.5.dp.toPx(), Offset(cw * d + cw / 2, 5.dp.toPx()))
            }
            rows.forEachIndexed { r, (_, cells) ->
                cells.forEachIndexed { d, lv ->
                    if (d.toFloat() / days > grow) return@forEachIndexed
                    val c = if (lv == null) Color.White.copy(alpha = 0.05f) else Palette.Magenta.copy(alpha = 0.12f + 0.22f * lv)
                    drawRoundRect(c, Offset(cw * d + gap / 2, top + rh * r + gap / 2), Size(cw - gap, rh - gap), CornerRadius(3.dp.toPx()))
                }
            }
            picked?.let { (m, d) ->
                val r = rows.indexOfFirst { it.first == m }
                drawRoundRect(Color.White, Offset(cw * d, top + rh * r), Size(cw, rh), CornerRadius(3.dp.toPx()), style = Stroke(1.5f))
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Lime)); Spacer(Modifier.width(4.dp))
        Text("workout   ", style = Type.Label.copy(fontSize = 10.sp, color = Palette.Dim))
        for (lv in 0..4) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Magenta.copy(alpha = 0.12f + 0.22f * lv)))
            Spacer(Modifier.width(2.dp))
        }
        Text(" fresh → wrecked", style = Type.Label.copy(fontSize = 10.sp, color = Palette.Dim))
    }
}

@Composable
private fun WorkloadScatter(eps: List<Body.Episode>, grow: Float) {
    if (eps.size < 2) { Empty(); return }
    var picked by remember { mutableStateOf<Body.Episode?>(null) }
    val xMax = eps.maxOf { it.volume } * 1.15f
    val yMax = ((eps.maxOf { it.hours } / 24f).toInt() + 1) * 24f
    val plot = remember { PlotBox() }
    fun px(e: Body.Episode): Offset {
        val (o, s) = plot.v
        return Offset(o.x + s.width * e.volume / xMax, o.y + s.height * (1f - e.hours / yMax))
    }
    Readout(picked?.let { "${it.muscle.label}: workload ${it.volume.roundToInt()}, ${it.hours.roundToInt()} h to recover" })
    Canvas(Modifier.fillMaxWidth().height(160.dp).pointerInput(eps) {
        detectTapGestures { tap -> picked = eps.minByOrNull { (px(it) - tap).getDistance() }?.takeIf { (px(it) - tap).getDistance() < 60f } }
    }) {
        plot.v = frame(yMax, (0..(yMax / 24f).toInt()).map { it * 24f }) { "${it.toInt()}h" }
        val (o, s) = plot.v
        xLabel("lighter", o.x, android.graphics.Paint.Align.LEFT)
        xLabel("workload (rounds × how hard it works the muscle) →", o.x + s.width, android.graphics.Paint.Align.RIGHT)
        eps.forEach { e ->
            val p = px(e)
            drawCircle(Palette.Ink, 6.dp.toPx() * grow, p)
            drawCircle(muscleColor[e.muscle] ?: Color.White, 4.5.dp.toPx() * grow, p)
        }
        picked?.let { drawCircle(Color.White, 7.dp.toPx(), px(it), style = Stroke(2f)) }
    }
}

@Composable
private fun Empty() {
    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        Text("Not enough check-ins yet", style = Type.Label.copy(color = Palette.Dim))
    }
}

private fun date(millis: Long, fmt: DateTimeFormatter): String = LocalDate.ofEpochDay(Body.dayOf(millis)).format(fmt)

/** Where the plot area was last drawn, so taps can be matched to points. */
private class PlotBox { var v: Pair<Offset, Size> = Offset.Zero to Size.Zero }
