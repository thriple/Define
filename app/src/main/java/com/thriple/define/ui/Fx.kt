package com.thriple.define.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Seconds since this composable started, updated every frame. Read it in draw lambdas only. */
@Composable
fun rememberFrameTime(): State<Float> = produceState(0f) {
    val start = withFrameNanos { it }
    while (true) {
        withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
}

/** Slow-moving glowing colour blobs plus drifting sparkles. The app's ever-present motion layer. */
@Composable
fun LivingBackground(colors: List<Color>, modifier: Modifier = Modifier, intensity: Float = 1f) {
    val time by rememberFrameTime()
    val sparkles = remember { List(46) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    Canvas(modifier.fillMaxSize()) {
        val t = time
        drawRect(Brush.verticalGradient(listOf(Palette.InkDeep, Palette.Ink, Palette.InkDeep)))
        colors.forEachIndexed { i, c ->
            val sp = 0.05f + i * 0.017f
            val cx = size.width * (0.5f + 0.42f * sin(t * sp * 2f + i * 1.7f))
            val cy = size.height * (0.5f + 0.40f * cos(t * sp * 1.3f + i * 2.3f))
            val r = size.minDimension * (0.55f + 0.12f * sin(t * 0.3f + i))
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = 0.42f * intensity), Color.Transparent), Offset(cx, cy), r),
                radius = r, center = Offset(cx, cy),
            )
        }
        sparkles.forEach { (x, y, s) ->
            val py = ((y - t * (0.012f + s * 0.03f)) % 1f + 1f) % 1f
            val px = x + 0.02f * sin(t * (0.5f + s) + y * 10f)
            val a = (0.25f + 0.55f * (0.5f + 0.5f * sin(t * (1.5f + 3f * s) + x * 20f))) * intensity
            drawCircle(Color.White.copy(alpha = a), radius = 1.2f + 2.4f * s, center = Offset(px * size.width, py * size.height))
        }
    }
}

// ---------------------------------------------------------------- full-screen effects layer

sealed class Effect(val start: Float, val duration: Float)
class Burst(val at: Offset, val colors: List<Color>, val count: Int, val speed: Float, start: Float, duration: Float = 1.1f, val seed: Int = Random.nextInt()) : Effect(start, duration)
class Ring(val at: Offset, val color: Color, val maxRadius: Float, start: Float, duration: Float = 0.7f, val width: Float = 18f) : Effect(start, duration)
class Flash(val color: Color, start: Float, duration: Float = 0.5f, val peak: Float = 0.85f) : Effect(start, duration)
class Confetti(start: Float, duration: Float = 3.2f, val count: Int = 140, val seed: Int = Random.nextInt()) : Effect(start, duration)
class FloatText(val text: String, val at: Offset, val color: Color, start: Float, duration: Float = 1.4f, val sizeSp: Float = 30f) : Effect(start, duration)

/** Spawns effects anywhere in the app; drawn above everything by [FxLayer]. Also owns screen shake. */
class FxState {
    val effects = mutableStateListOf<Effect>()
    var now = 0f
    private var shakeStart = -10f
    private var shakeAmp = 0f

    fun burst(at: Offset, colors: List<Color>, count: Int = 26, speed: Float = 900f) { effects += Burst(at, colors, count, speed, now) }
    fun ring(at: Offset, color: Color, maxRadius: Float = 500f, width: Float = 18f) { effects += Ring(at, color, maxRadius, now, width = width) }
    fun flash(color: Color, duration: Float = 0.5f, peak: Float = 0.85f) { effects += Flash(color, now, duration, peak) }
    fun confetti(count: Int = 140) { effects += Confetti(now, count = count) }
    fun text(text: String, at: Offset, color: Color, sizeSp: Float = 30f) { effects += FloatText(text, at, color, now, sizeSp = sizeSp) }
    fun shake(amplitude: Float = 18f) { shakeStart = now; shakeAmp = amplitude }

    fun shakeOffset(t: Float): Offset {
        val k = (t - shakeStart) / 0.45f
        if (k !in 0f..1f) return Offset.Zero
        val a = shakeAmp * (1f - k) * (1f - k)
        return Offset(a * sin(t * 90f), a * cos(t * 73f))
    }
}

val LocalFx = staticCompositionLocalOf { FxState() }

private val confettiColors = listOf(Palette.Magenta, Palette.Cyan, Palette.Lime, Palette.Orange, Palette.Gold, Palette.Violet, Palette.White)

@Composable
fun FxLayer(fx: FxState, modifier: Modifier = Modifier) {
    val time by rememberFrameTime()
    val measurer = rememberTextMeasurer()
    LaunchedEffect(fx) {
        while (true) {
            withFrameNanos { }
            fx.now = time
            fx.effects.removeAll { time - it.start > it.duration }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = time
        for (e in fx.effects) {
            val k = ((t - e.start) / e.duration).coerceIn(0f, 1f)
            when (e) {
                is Burst -> {
                    val rnd = Random(e.seed)
                    repeat(e.count) {
                        val ang = rnd.nextFloat() * 2f * PI.toFloat()
                        val sp = e.speed * (0.35f + 0.65f * rnd.nextFloat())
                        val sec = k * e.duration
                        val drag = 1f - k * 0.55f
                        val p = Offset(
                            e.at.x + cos(ang) * sp * sec * drag,
                            e.at.y + sin(ang) * sp * sec * drag + 700f * sec * sec,
                        )
                        val c = e.colors[it % e.colors.size]
                        val r = (4f + 7f * rnd.nextFloat()) * (1f - k * 0.7f)
                        drawCircle(c.copy(alpha = 1f - k), r, p)
                    }
                }
                is Ring -> drawCircle(
                    e.color.copy(alpha = (1f - k) * 0.9f), radius = e.maxRadius * (1f - (1f - k) * (1f - k)),
                    center = e.at, style = Stroke(width = e.width * (1f - k) + 2f),
                )
                is Flash -> {
                    val a = if (k < 0.15f) k / 0.15f else 1f - (k - 0.15f) / 0.85f
                    drawRect(e.color.copy(alpha = (a * e.peak).coerceIn(0f, 1f)))
                }
                is Confetti -> {
                    val rnd = Random(e.seed)
                    repeat(e.count) {
                        val x0 = rnd.nextFloat() * size.width
                        val delay = rnd.nextFloat() * 0.35f
                        val fall = 0.55f + 0.6f * rnd.nextFloat()
                        val kk = ((k - delay) / (1f - delay)).coerceIn(0f, 1f)
                        if (kk > 0f) {
                            val y = -40f + kk * fall * size.height * 1.3f
                            val x = x0 + 40f * sin(kk * 12f + it)
                            val c = confettiColors[it % confettiColors.size]
                            rotate(degrees = kk * 720f * (if (it % 2 == 0) 1 else -1), pivot = Offset(x, y)) {
                                drawRect(c.copy(alpha = 1f - kk * kk), topLeft = Offset(x - 7f, y - 4f), size = Size(14f, 8f))
                            }
                        }
                    }
                }
                is FloatText -> {
                    val layout = measurer.measure(e.text, TextStyle(fontFamily = Type.Display, fontSize = e.sizeSp.sp, color = e.color))
                    val scale = if (k < 0.12f) 0.4f + 5f * k else 1f
                    val a = if (k < 0.7f) 1f else 1f - (k - 0.7f) / 0.3f
                    val tl = Offset(e.at.x - layout.size.width / 2f * scale, e.at.y - 160f * k - layout.size.height / 2f * scale)
                    drawText(layout, topLeft = tl, alpha = a.coerceIn(0f, 1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- buttons and numbers

/** Squash on press, springy overshoot on release. */
fun Modifier.bouncy(onClick: () -> Unit): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                scope.launch { scale.animateTo(0.88f, spring(stiffness = 900f)) }
                val released = tryAwaitRelease()
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 420f)) }
                if (released) click()
            })
        }
}

/** The big glossy call-to-action: gradient, moving shine, breathing pulse, pop + haptic on tap. */
@Composable
fun HypeButton(
    text: String,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(Palette.Magenta, Palette.Orange),
    pulse: Boolean = true,
    textStyle: TextStyle = Type.Title,
    onClick: () -> Unit,
) {
    val inf = rememberInfiniteTransition(label = "hype")
    val breathe by inf.animateFloat(1f, if (pulse) 1.045f else 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val shine by inf.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "s")
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .graphicsLayer { scaleX = breathe; scaleY = breathe }
            .bouncy { Sfx.play("pop"); Haptics.click(); onClick() }
            .clip(shape)
            .background(Brush.horizontalGradient(colors))
            .drawBehind {
                val x = size.width * shine
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                        start = Offset(x - size.width * 0.25f, 0f), end = Offset(x + size.width * 0.25f, size.height),
                    )
                )
            }
            .padding(horizontal = 26.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = textStyle, textAlign = TextAlign.Center)
    }
}

/** Count-up/down number that rolls to its new value. */
@Composable
fun RollingNumber(value: Int, style: TextStyle, modifier: Modifier = Modifier, prefix: String = "", suffix: String = "") {
    val shown by animateIntAsState(value, tween(900, easing = FastOutSlowInEasing), label = "roll")
    Text("$prefix$shown$suffix", style = style, modifier = modifier)
}
