package com.thriple.define.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import com.thriple.define.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

val heroStages = listOf(
    R.drawable.hero_stage1, R.drawable.hero_stage2, R.drawable.hero_stage3, R.drawable.hero_stage4,
    R.drawable.hero_stage5, R.drawable.hero_stage6, R.drawable.hero_stage7, R.drawable.hero_stage8,
)
private val heroRigMaps = listOf(
    R.drawable.hero_rig1, R.drawable.hero_rig2, R.drawable.hero_rig3, R.drawable.hero_rig4,
    R.drawable.hero_rig5, R.drawable.hero_rig6, R.drawable.hero_rig7, R.drawable.hero_rig8,
)

/**
 * The hero, alive. The flat stage image is deformed by the body rig (see HeroRig.kt): breathing that moves
 * the chest, shoulders, head and arms but not the legs, a weight-shift sway over planted feet, a cape in
 * gusting wind, and pulsing glow on the eye lenses, emblem and suit trim. [pump] (0..1) puffs the chest and
 * shoulders, e.g. after training. Behind: aura, turning light rays and a glowing floor.
 *
 * [lively] (home screen) adds: idle moves every so often (cape gust, power flare, energy crackle, flex,
 * rising energy), touch (tap = a quick flex; press and hold = charge up, release = shockwave), everything
 * pulsing on the music's beat, phone-tilt depth, the planet horizon, and the streak constellation.
 */
@Composable
fun HeroView(
    stage: Int,
    modifier: Modifier = Modifier,
    aura: Color = Palette.Cyan,
    energy: Float = 0.5f,
    rays: Boolean = true,
    pump: Float = 0f,
    glow: Float = 1f,
    lively: Boolean = false,
    constellation: Int = 0,
    dropIn: Boolean = false,
) {
    val idx = (stage - 1).coerceIn(0, heroStages.lastIndex)
    val image = ImageBitmap.imageResource(heroStages[idx])
    val rigMap = ImageBitmap.imageResource(heroRigMaps[idx])
    val rig = HeroRigs.forStage(stage)
    val renderer = remember(idx) { HeroRenderer(image.asAndroidBitmap(), rigMap.asAndroidBitmap(), rig) }
    val timeState = rememberFrameTime()
    val time by timeState
    val fx = LocalFx.current
    val tilt = LocalTilt.current
    val acts = remember { HeroActs() }
    val drop = remember { Animatable(if (dropIn) 1f else 0f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val geo = remember { HeroGeo() }
    val stars = remember { constellationPoints() }

    LaunchedEffect(dropIn) {
        if (!dropIn) return@LaunchedEffect
        delay(250)
        drop.animateTo(0f, tween(560, easing = CubicBezierEasing(0.55f, 0f, 1f, 0.6f)))
        val feet = origin + geo.feet
        fx.shake(28f)
        fx.ring(feet, aura, 900f, 30f)
        fx.ring(feet, Color.White.copy(alpha = 0.7f), 520f, 14f)
        fx.burst(feet, listOf(aura, Color.White, Palette.Violet), count = 40, speed = 900f)
        Music.hit(0.8f)
        Haptics.thud()
        acts.start(HeroActs.FLEX, timeState.value)
        acts.start(HeroActs.GUST, timeState.value)
    }
    LaunchedEffect(lively) {
        if (!lively) return@LaunchedEffect
        delay(7000)
        while (true) {
            acts.start(Random.nextInt(HeroActs.COUNT), timeState.value)
            delay(Random.nextLong(12_000, 26_000))
        }
    }

    fun tap() {
        val t = timeState.value
        acts.start(HeroActs.FLEX, t); acts.start(HeroActs.FLARE, t)
        val c = origin + geo.chest
        fx.ring(c, aura, 420f, 12f)
        fx.burst(c, listOf(aura, Color.White, Palette.Magenta), count = 18, speed = 650f)
        Music.tap()
        Haptics.click()
    }

    fun blast(power: Float) {
        val t = timeState.value
        acts.start(HeroActs.FLARE, t); acts.start(HeroActs.GUST, t); acts.start(HeroActs.CRACKLE, t)
        val c = origin + geo.chest
        fx.ring(c, aura, 900f + 800f * power, 34f)
        fx.ring(c, Color.White, 600f + 500f * power, 16f)
        fx.burst(c, listOf(aura, Palette.Magenta, Palette.Gold, Color.White), count = (40 + 70 * power).toInt(), speed = 1500f)
        fx.flash(aura, 0.35f, 0.2f + 0.4f * power)
        fx.shake(10f + 24f * power)
        Music.hit(0.45f + 0.55f * power)
        if (power > 0.6f) Haptics.rumble() else Haptics.thud()
    }

    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(lively) {
                if (!lively) return@pointerInput
                detectTapGestures(onPress = {
                    val t0 = timeState.value
                    acts.chargeFrom = t0
                    val released = tryAwaitRelease()
                    acts.chargeFrom = -1f
                    val held = timeState.value - t0
                    if (released) { if (held < 0.3f) tap() else blast((held / 1.6f).coerceAtMost(1f)) }
                })
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time
            val tl = if (lively) tilt.value else Offset.Zero
            val beat = if (lively) visualBeat(t) else 0f
            val kick = if (lively) beatPulse(beat) else 0f
            val k = min(size.width / rig.w, size.height / rig.h)
            val ox = (size.width - rig.w * k) / 2f
            val oy = size.height - rig.h * k
            val c = Offset(size.width / 2f, oy + rig.armpit * k)
            val ground = Offset(size.width / 2f, oy + rig.feet * k)
            geo.chest = c; geo.feet = ground
            val flare = acts.env(HeroActs.FLARE, t, 0.15f, 0.4f, 1.2f)
            val gust = acts.env(HeroActs.GUST, t, 0.4f, 1.2f, 1.6f)
            val crackle = acts.env(HeroActs.CRACKLE, t, 0.05f, 1.1f, 0.3f)
            val flex = acts.env(HeroActs.FLEX, t, 0.35f, 0.5f, 0.9f)
            val rise = acts.env(HeroActs.RISE, t, 0.5f, 1.5f, 1.0f)
            val charge = if (acts.chargeFrom >= 0f) ((t - acts.chargeFrom - 0.2f) / 1.4f).coerceIn(0f, 1f) else 0f

            if (lively) drawConstellation(stars, constellation, t, kick, Offset(-tl.x * 18f, -tl.y * 18f), aura)

            val pulse = 0.85f + 0.15f * sin(t * 2.2f)
            if (rays) {
                val rayA = 0.10f + 0.12f * energy + 0.08f * kick + 0.25f * flare + 0.2f * charge
                rotate(degrees = t * (8f + 30f * charge), pivot = c) {
                    for (i in 0 until 12) {
                        rotate(degrees = i * 30f, pivot = c) {
                            drawRect(
                                Brush.verticalGradient(listOf(aura.copy(alpha = rayA.coerceAtMost(0.8f)), Color.Transparent), startY = c.y, endY = c.y - size.height * 0.75f),
                                topLeft = Offset(c.x - size.width * 0.035f, c.y - size.height * 0.75f),
                                size = Size(size.width * 0.07f, size.height * 0.75f),
                            )
                        }
                    }
                }
            }
            val r = size.minDimension * (0.55f + 0.06f * energy + 0.04f * kick + 0.1f * flare + 0.12f * charge) * pulse
            drawCircle(Brush.radialGradient(listOf(aura.copy(alpha = (0.35f + 0.3f * energy + 0.12f * kick + 0.3f * charge).coerceAtMost(0.95f)), Color.Transparent), c, r), r, c)

            if (lively) {
                // the planet the hero stands on: dark disc, glowing atmosphere on the rim
                val R = size.width * 1.6f
                val pc = Offset(size.width / 2f - tl.x * 6f, ground.y - 4f + R - tl.y * 4f)
                drawCircle(Brush.radialGradient(0.0f to Color.Transparent, 0.955f to Color.Transparent, 0.99f to aura.copy(alpha = 0.32f + 0.1f * kick), 1f to Color.Transparent, center = pc, radius = R * 1.04f), R * 1.04f, pc)
                drawCircle(Brush.verticalGradient(listOf(Color(0xFF120A2E), Palette.InkDeep), startY = pc.y - R, endY = pc.y - R + size.height * 0.5f), R, pc)
                drawCircle(aura.copy(alpha = 0.75f), R, pc, style = Stroke(2.5f))
                // surface glints sliding along the horizon
                for (i in 0 until 3) {
                    val u = ((t * 0.03f + i / 3f) % 1f)
                    val ang = (-0.5f + u) * 0.9f
                    val gx = pc.x + R * sin(ang); val gy = pc.y - R * cos(ang)
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f * sin(PI.toFloat() * u)), Color.Transparent), Offset(gx, gy), 40f), 40f, Offset(gx, gy))
                }
                // a ripple across the ground on every bar
                val u = (beat / 4f) - floor(beat / 4f)
                val rx = rig.w * k * (0.25f + 1.3f * u)
                drawOval(aura.copy(alpha = 0.45f * (1f - u) * (1f - u)), topLeft = Offset(ground.x - rx, ground.y - rx * 0.08f), size = Size(rx * 2f, rx * 0.16f), style = Stroke(2.5f))
            }
            drawOval(
                Brush.radialGradient(listOf(aura.copy(alpha = 0.55f + 0.2f * kick), Color.Transparent), ground, rig.w * k * 0.45f),
                topLeft = Offset(ground.x - rig.w * k * 0.45f, ground.y - rig.h * k * 0.03f),
                size = Size(rig.w * k * 0.9f, rig.h * k * 0.06f),
            )

            // energy motes rising from the ground into the chest (more when charging or on a "rise")
            if (lively) {
                val motes = 10 + (26 * maxOf(rise, charge)).toInt()
                for (i in 0 until motes) {
                    val sp = 0.25f + (i * 37 % 11) / 22f + 0.6f * charge
                    val p = ((t * sp + i * 0.618f) % 1f)
                    val side = ((i * 53 % 17) / 17f - 0.5f) * rig.w * k * 1.1f
                    val x = ground.x + side * (1f - p * p)
                    val y = ground.y + (c.y - ground.y) * p
                    drawCircle(aura.copy(alpha = 0.7f * sin(PI.toFloat() * p)), 2f + 2.5f * (1f - p), Offset(x, y))
                }
            }

            // breath ~14 a minute (a touch quicker in than out); slow weight-shift sway; tremble while charging
            val phase = (t / 4.2f) % 1f
            val breath = if (phase < 0.42f) 0.5f - 0.5f * cos(PI.toFloat() * phase / 0.42f)
                         else 0.5f + 0.5f * cos(PI.toFloat() * (phase - 0.42f) / 0.58f)
            val sway = sin(2f * PI.toFloat() * t / 9.5f) + charge * 0.35f * sin(t * 55f)
            val wind = 1.1f + 1.5f * gust + 0.3f * sin(t * 0.41f) * sin(t * 0.93f + 1f)
            acts.advanceCape(t, 1f + 0.9f * gust)
            val heroGlow = glow * (1f + 0.35f * kick + 2.5f * flare + 2f * charge)
            val heroPump = (pump + 0.6f * flex + 0.35f * charge).coerceAtMost(1.2f)
            val dropY = -drop.value * size.height * 1.3f
            drawIntoCanvas { canvas ->
                renderer.draw(canvas.nativeCanvas, ox + tl.x * 10f, oy + dropY + tl.y * 6f, k, t, breath, sway, heroPump, heroGlow, wind, acts.capePhase)
            }

            // crackling energy around the body
            if (lively && (crackle > 0f || charge > 0.3f)) {
                val amt = maxOf(crackle, charge)
                val seed = (t * 14f).toInt()
                val rnd = Random(seed)
                val bodyC = Offset(c.x + tl.x * 10f, oy + dropY + (rig.shoulder + rig.crotch) * 0.5f * k)
                val rx = rig.torsoHalf * k * 2.1f
                val ry = (rig.crotch - rig.shoulder) * k * 0.95f
                repeat((2 + 4 * amt).toInt()) {
                    val a0 = rnd.nextFloat() * 6.283f
                    val a1 = a0 + (rnd.nextFloat() - 0.5f) * 1.2f
                    val p0 = Offset(bodyC.x + rx * cos(a0), bodyC.y + ry * sin(a0))
                    val p1 = Offset(bodyC.x + rx * 1.15f * cos(a1), bodyC.y + ry * 1.1f * sin(a1))
                    val path = bolt(p0, p1, rnd)
                    drawPath(path, aura.copy(alpha = 0.35f * amt), style = Stroke(7f, cap = StrokeCap.Round))
                    drawPath(path, Color.White.copy(alpha = 0.9f * amt), style = Stroke(1.8f, cap = StrokeCap.Round))
                }
            }

            // wind streaks in a gust
            if (gust > 0.02f) {
                for (i in 0 until 9) {
                    val sp = 0.9f + (i % 4) * 0.3f
                    val u = ((t * sp + i * 0.37f) % 1f)
                    val y = oy + rig.h * k * (0.15f + 0.08f * i)
                    val x = size.width * (1.15f - 1.3f * u)
                    val len = size.width * 0.18f
                    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f * gust), Color.Transparent), x, x + len), Offset(x, y), Offset(x + len, y), strokeWidth = 2f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** Where the chest and feet are drawn (local px), for effects placed in screen space. */
private class HeroGeo { var chest = Offset.Zero; var feet = Offset.Zero }

/** Timestamps of the hero's idle moves, plus the cape's own clock. */
private class HeroActs {
    private val started = FloatArray(COUNT) { -100f }
    var chargeFrom = -1f
    var capePhase = 0f
    private var lastT = -1f

    fun start(which: Int, t: Float) { started[which] = t }

    fun env(which: Int, t: Float, attack: Float, hold: Float, release: Float): Float {
        val d = t - started[which]
        val v = when {
            d < 0f -> 0f
            d < attack -> d / attack
            d < attack + hold -> 1f
            d < attack + hold + release -> 1f - (d - attack - hold) / release
            else -> 0f
        }
        return v * v * (3f - 2f * v)
    }

    fun advanceCape(t: Float, speed: Float) {
        if (lastT >= 0f) capePhase += (t - lastT).coerceIn(0f, 0.1f) * speed
        lastT = t
    }

    companion object {
        const val GUST = 0
        const val FLARE = 1
        const val CRACKLE = 2
        const val FLEX = 3
        const val RISE = 4
        const val COUNT = 5
    }
}

/** A jagged lightning path from a to b (midpoint displacement). */
private fun bolt(a: Offset, b: Offset, rnd: Random): Path {
    val pts = ArrayList<Offset>(); pts += a; pts += b
    var rough = (b - a).getDistance() * 0.35f
    repeat(4) {
        val next = ArrayList<Offset>()
        for (i in 0 until pts.size - 1) {
            val p = pts[i]; val q = pts[i + 1]
            val m = (p + q) / 2f
            val n = Offset(-(q.y - p.y), q.x - p.x)
            val len = n.getDistance().coerceAtLeast(1e-3f)
            next += p; next += m + n / len * ((rnd.nextFloat() - 0.5f) * rough)
        }
        next += pts.last()
        pts.clear(); pts.addAll(next)
        rough *= 0.5f
    }
    return Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y) }
}

/** 30 fixed star positions above and around the hero, in walking order (normalised 0..1). */
private fun constellationPoints(): List<Offset> {
    val rnd = Random(77)
    val pts = ArrayList<Offset>()
    while (pts.size < 30) {
        val p = Offset(0.06f + 0.88f * rnd.nextFloat(), 0.03f + 0.4f * rnd.nextFloat())
        if (abs(p.x - 0.5f) < 0.19f && p.y > 0.1f) continue                 // keep the hero's head clear
        if (pts.any { (it - p).getDistance() < 0.075f }) continue
        pts += p
    }
    // walk: start at the far left, always step to the nearest unvisited star
    val order = ArrayList<Offset>()
    val left = pts.toMutableList()
    var cur = left.minBy { it.x }
    while (left.isNotEmpty()) { left.remove(cur); order += cur; if (left.isNotEmpty()) cur = left.minBy { (it - cur).getDistance() } }
    return order
}

/** One star per day of the current streak, linked in order; the newest one pulses. Next few shown as ghosts. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConstellation(pts: List<Offset>, streak: Int, t: Float, kick: Float, shift: Offset, color: Color) {
    val lit = streak.coerceIn(0, pts.size)
    val ghosts = (lit + 3).coerceAtMost(pts.size)
    fun at(i: Int) = Offset(pts[i].x * size.width + shift.x, pts[i].y * size.height + shift.y)
    for (i in 1 until ghosts) {
        val litLine = i < lit
        drawLine(color.copy(alpha = if (litLine) 0.45f else 0.1f), at(i - 1), at(i), strokeWidth = if (litLine) 1.6f else 1f)
    }
    for (i in 0 until ghosts) {
        val p = at(i)
        if (i >= lit) { drawCircle(Color.White.copy(alpha = 0.16f), 2.2f, p); continue }
        val newest = i == lit - 1
        val tw = 0.75f + 0.25f * sin(t * 2f + i)
        val halo = if (newest) 22f + 8f * sin(t * 3f) + 10f * kick else 9f + 4f * kick
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f * tw), Color.Transparent), p, halo), halo, p)
        drawCircle(Color.White.copy(alpha = tw), if (newest) 4f else 2.8f, p)
    }
}

/** Draws one stage: per-pixel shader on Android 13+, a deformed mesh with the same rig math below that. */
private class HeroRenderer(private val bitmap: Bitmap, private val rigBitmap: Bitmap, private val rig: Rig) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shader: Any? = if (Build.VERSION.SDK_INT >= 33) {
        try { ShaderPath.create(bitmap, rigBitmap, rig) } catch (e: Exception) { null }   // fall back to the mesh
    } else null

    private val cols = 16
    private val rows = 40
    private val base = FloatArray((cols + 1) * (rows + 1) * 2)
    private val verts = FloatArray(base.size)
    private val capeW = FloatArray((cols + 1) * (rows + 1))
    private val tmp = FloatArray(2)
    private val tmp2 = FloatArray(2)

    init {
        var i = 0
        for (r in 0..rows) for (c in 0..cols) {
            val x = rig.w * c / cols
            val y = rig.h * r / rows
            base[i * 2] = x; base[i * 2 + 1] = y
            val px = x.toInt().coerceIn(0, rigBitmap.width - 1)
            val py = y.toInt().coerceIn(0, rigBitmap.height - 1)
            capeW[i] = ((rigBitmap.getPixel(px, py) shr 16) and 0xFF) / 255f
            i++
        }
    }

    fun draw(canvas: android.graphics.Canvas, ox: Float, oy: Float, k: Float, t: Float, breath: Float, sway: Float, pump: Float, glow: Float, wind: Float, capePhase: Float) {
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(k, k)
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            ShaderPath.update(shader, t, breath, sway, pump, glow, wind, capePhase)
            paint.shader = shader as Shader
            canvas.drawRect(0f, 0f, rig.w, rig.h, paint)
        } else {
            for (i in 0 until capeW.size) {
                val x = base[i * 2]; val y = base[i * 2 + 1]
                rigDisplacement(rig, x, y, breath, sway, pump, tmp)
                capeFlutter(rig, x, y, capeW[i], capePhase, wind, tmp2)
                verts[i * 2] = x + tmp[0] + tmp2[0]
                verts[i * 2 + 1] = y + tmp[1] + tmp2[1]
            }
            paint.shader = null
            canvas.drawBitmapMesh(bitmap, cols, rows, verts, 0, null, 0, paint)
        }
        canvas.restore()
    }
}

@RequiresApi(33)
private object ShaderPath {
    fun create(bitmap: Bitmap, rigBitmap: Bitmap, rig: Rig): RuntimeShader {
        val s = RuntimeShader(HERO_SHADER)
        val img = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }
        val map = BitmapShader(rigBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }
        s.setInputShader("image", img)
        s.setInputShader("rigmap", map)
        s.setFloatUniform("H", rig.h)
        s.setFloatUniform("sh", rig.shoulder)
        s.setFloatUniform("armpit", rig.armpit)
        s.setFloatUniform("hand", rig.hand)
        s.setFloatUniform("crotch", rig.crotch)
        s.setFloatUniform("feet", rig.feet)
        s.setFloatUniform("cx", rig.cx)
        s.setFloatUniform("th", rig.torsoHalf)
        return s
    }

    fun update(shader: Any, t: Float, breath: Float, sway: Float, pump: Float, glow: Float, wind: Float, capePhase: Float) {
        val s = shader as RuntimeShader
        s.setFloatUniform("wind", wind)
        s.setFloatUniform("cphase", capePhase)
        s.setFloatUniform("time", t)
        s.setFloatUniform("breath", breath)
        s.setFloatUniform("sway", sway)
        s.setFloatUniform("pump", pump)
        s.setFloatUniform("glow", glow)
    }
}
