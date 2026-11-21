package com.thriple.define.ui

import android.content.Context
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- shared motion inputs

/** Phone tilt, -1..1 on each axis, relative to however it's being held (drifts back to 0 when still). */
val LocalTilt = compositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }

@Composable
fun rememberTilt(): State<Offset> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var fx = 0f; var fy = 0f; var sx = 0f; var sy = 0f; var init = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]; val y = e.values[1]
                if (!init) { fx = x; fy = y; sx = x; sy = y; init = true }
                fx += (x - fx) * 0.12f; fy += (y - fy) * 0.12f       // fast: the movement
                sx += (x - sx) * 0.006f; sy += (y - sy) * 0.006f     // slow: the resting grip
                state.value = Offset(((sx - fx) / 3f).coerceIn(-1f, 1f), ((fy - sy) / 3f).coerceIn(-1f, 1f))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (acc != null) sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    return state
}

/** The beat the visuals move to: the music's when it's playing, otherwise a steady 100 BPM. */
fun visualBeat(frameTime: Float): Float = Music.beatNow()?.toFloat() ?: (frameTime * 100f / 60f)

/** 1 on each beat, decaying to 0 before the next (a kick-drum shaped pulse). */
fun beatPulse(beat: Float): Float { val f = beat - floor(beat); val d = 1f - f; return d * d * d }

// ---------------------------------------------------------------- the cosmos

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Float, val color: Color)

private val starTints = listOf(Color(0xFFFFFFFF), Color(0xFFCFE6FF), Color(0xFFFFF1D6), Color(0xFFB9F3FF), Color(0xFFFFD1F2))

private fun starLayer(n: Int, seed: Int, rMin: Float, rMax: Float): List<Star> {
    val rnd = Random(seed)
    return List(n) {
        Star(rnd.nextFloat(), rnd.nextFloat(), rMin + (rMax - rMin) * rnd.nextFloat() * rnd.nextFloat(),
            rnd.nextFloat() * 6.28f, 0.6f + rnd.nextFloat() * 2.2f, starTints[rnd.nextInt(starTints.size)])
    }
}

private class Meteor(val start: Float, val x: Float, val y: Float, val angle: Float, val length: Float, val speed: Float)

/**
 * The app's ever-present backdrop: a drifting nebula (a GPU shader on Android 13+, soft colour clouds below
 * that), three parallax layers of twinkling stars that shift as the phone tilts and sparkle on the beat,
 * a few bright stars with lens spikes, and shooting stars.
 */
@Composable
fun LivingBackground(colors: List<Color>, modifier: Modifier = Modifier, intensity: Float = 1f, meteorsPerMinute: Float = 4f) {
    val time by rememberFrameTime()
    val tilt = LocalTilt.current
    val layers = remember { listOf(starLayer(110, 1, 0.6f, 1.6f), starLayer(60, 2, 1.0f, 2.4f), starLayer(26, 3, 1.6f, 3.4f)) }
    val bright = remember { starLayer(7, 4, 2.4f, 3.2f) }
    val nebula: Any? = remember { if (Build.VERSION.SDK_INT >= 33) Nebula.create() else null }
    val meteors = remember { ArrayList<Meteor>() }
    val rnd = remember { Random(System.nanoTime()) }
    val nextMeteor = remember { floatArrayOf(3f) }

    Canvas(modifier.fillMaxSize()) {
        val t = time
        val tl = tilt.value
        val beat = visualBeat(t)
        val kick = beatPulse(beat)
        drawRect(Brush.verticalGradient(listOf(Palette.InkDeep, Palette.Ink, Palette.InkDeep)))

        // nebula
        val drewNebula = nebula != null && Build.VERSION.SDK_INT >= 33 &&
            Nebula.draw(this, nebula, t, kick, Offset(-tl.x * 12f, -tl.y * 12f), colors, intensity)
        if (!drewNebula) {
            colors.forEachIndexed { i, c ->
                val sp = 0.05f + i * 0.017f
                val cx = size.width * (0.5f + 0.42f * sin(t * sp * 2f + i * 1.7f)) - tl.x * 12f
                val cy = size.height * (0.5f + 0.40f * cos(t * sp * 1.3f + i * 2.3f)) - tl.y * 12f
                val r = size.minDimension * (0.55f + 0.12f * sin(t * 0.3f + i))
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = (0.38f + 0.06f * kick) * intensity), Color.Transparent), Offset(cx, cy), r), radius = r, center = Offset(cx, cy))
            }
        }

        // star layers: far ones barely move, near ones drift and shift most with tilt
        val beatIndex = floor(beat).toInt()
        layers.forEachIndexed { li, stars ->
            val depth = 6f + li * 14f
            val drift = 0.004f + li * 0.006f
            stars.forEachIndexed { si, s ->
                val px = ((s.x - tl.x * depth / size.width) % 1f + 1f) % 1f
                val py = (((s.y + t * drift * 0.1f) - tl.y * depth / size.height) % 1f + 1f) % 1f
                var a = 0.35f + 0.4f * (0.5f + 0.5f * sin(t * s.speed + s.phase))
                // a few stars flash on each beat
                val chosen = ((si * 7919 + beatIndex * 104729 + li * 31) and 1023) < 70
                if (chosen) a += 0.6f * kick
                drawCircle(s.color.copy(alpha = (a * intensity).coerceIn(0f, 1f)), s.r * (1f + if (chosen) 0.5f * kick else 0f), Offset(px * size.width, py * size.height))
            }
        }

        // bright stars with four-point lens spikes
        bright.forEach { s ->
            val c = Offset(((s.x - tl.x * 40f / size.width) % 1f + 1f) % 1f * size.width, s.y * size.height * 0.85f)
            val tw = 0.6f + 0.4f * sin(t * s.speed * 0.7f + s.phase) + 0.25f * kick
            val len = (10f + 14f * tw) * s.r / 2.4f
            drawCircle(Brush.radialGradient(listOf(s.color.copy(alpha = 0.55f * tw), Color.Transparent), c, len), len, c)
            for (k in 0 until 2) {
                val (dx, dy) = if (k == 0) len * 1.8f to 0f else 0f to len * 1.8f
                drawLine(Brush.linearGradient(listOf(Color.Transparent, s.color.copy(alpha = 0.8f * tw), Color.Transparent), Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy)),
                    Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), strokeWidth = 1.6f, cap = StrokeCap.Round)
            }
            drawCircle(Color.White.copy(alpha = 0.9f), s.r * 0.9f, c)
        }

        // shooting stars
        if (t > nextMeteor[0]) {
            meteors += Meteor(t, rnd.nextFloat() * 1.1f - 0.05f, rnd.nextFloat() * 0.45f, (200f + rnd.nextFloat() * 40f) * PI.toFloat() / 180f,
                0.12f + rnd.nextFloat() * 0.18f, 0.9f + rnd.nextFloat() * 0.8f)
            nextMeteor[0] = t + (60f / meteorsPerMinute.coerceAtLeast(0.5f)) * (0.4f + rnd.nextFloat() * 1.2f)
        }
        meteors.removeAll { t - it.start > 1.4f }
        meteors.forEach { m ->
            val u = (t - m.start) / 1.4f
            val dirX = cos(m.angle); val dirY = -sin(m.angle)
            val headX = (m.x + dirX * u * m.speed) * size.width
            val headY = (m.y + dirY * u * m.speed) * size.height
            val tail = m.length * size.width * (1f - u * 0.5f)
            val a = (1f - u) * intensity
            val tailEnd = Offset(headX - dirX * tail, headY - dirY * tail * size.width / size.height)
            drawLine(Brush.linearGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), Offset(headX, headY), tailEnd), Offset(headX, headY), tailEnd, strokeWidth = 2.4f, cap = StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = a), 2.6f, Offset(headX, headY))
        }
    }
}

@RequiresApi(33)
private object Nebula {
    private const val SRC = """
uniform float2 res;
uniform float time;
uniform float pulse;
uniform float2 shift;
uniform float amount;
uniform half4 c1;
uniform half4 c2;
uniform half4 c3;

float hash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}
half4 main(float2 fc) {
    float2 uv = (fc + shift) / res.y;
    float t = time * 0.025;
    float2 q = float2(fbm(uv * 1.6 + float2(t, 0.3 * t)), fbm(uv * 1.6 + float2(5.2, 1.3) - float2(0.4 * t, t)));
    float n = fbm(uv * 2.2 + 1.9 * q + float2(0.7 * t, -0.4 * t));
    float cloud = smoothstep(0.34, 0.82, n);
    float lanes = smoothstep(0.5, 0.75, fbm(uv * 5.0 - 2.0 * q)) * 0.6;
    float3 col = mix(float3(c1.rgb), float3(c2.rgb), smoothstep(0.3, 0.8, q.x));
    col = mix(col, float3(c3.rgb), smoothstep(0.45, 0.9, q.y) * 0.8);
    float glowCore = smoothstep(0.6, 0.9, n);
    float a = clamp(cloud * (1.0 - lanes) * (0.7 + 0.25 * pulse) + 0.3 * glowCore, 0.0, 1.0) * amount;
    col = col * 1.3 + glowCore * 0.45;
    return half4(half3(clamp(col * a, 0.0, 1.0)), half(a));
}
"""

    fun create(): Any? = try { RuntimeShader(SRC) } catch (_: Exception) { null }

    /** Returns false if the shader can't be used, so the caller draws the fallback. */
    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, any: Any, t: Float, pulse: Float, shift: Offset, colors: List<Color>, intensity: Float): Boolean {
        return try {
            val shader = any as RuntimeShader
            val c = colors + colors + colors
            shader.setFloatUniform("res", scope.size.width, scope.size.height)
            shader.setFloatUniform("time", t)
            shader.setFloatUniform("pulse", pulse)
            shader.setFloatUniform("shift", shift.x, shift.y)
            shader.setFloatUniform("amount", 0.8f * intensity)
            for ((i, name) in listOf("c1", "c2", "c3").withIndex()) {
                val col = c[i]
                shader.setFloatUniform(name, col.red, col.green, col.blue, 1f)
            }
            scope.drawRect(ShaderBrush(shader), blendMode = BlendMode.SrcOver)
            true
        } catch (_: Exception) { false }
    }
}
