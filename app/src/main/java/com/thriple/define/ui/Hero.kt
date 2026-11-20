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
 * the chest, shoulders, head and arms but not the legs, a weight-shift sway over planted feet, a fluttering
 * cape, and pulsing glow on the eye lenses, emblem and suit trim. [pump] (0..1) puffs the chest and
 * shoulders, e.g. after training. Behind: aura, slowly turning light rays and a glowing floor.
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
) {
    val idx = (stage - 1).coerceIn(0, heroStages.lastIndex)
    val image = ImageBitmap.imageResource(heroStages[idx])
    val rigMap = ImageBitmap.imageResource(heroRigMaps[idx])
    val rig = HeroRigs.forStage(stage)
    val renderer = remember(idx) { HeroRenderer(image.asAndroidBitmap(), rigMap.asAndroidBitmap(), rig) }
    val time by rememberFrameTime()

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time
            val k = min(size.width / rig.w, size.height / rig.h)
            val ox = (size.width - rig.w * k) / 2f
            val oy = size.height - rig.h * k
            val c = Offset(size.width / 2f, oy + rig.armpit * k)
            val pulse = 0.85f + 0.15f * sin(t * 2.2f)
            if (rays) {
                rotate(degrees = t * 8f, pivot = c) {
                    for (i in 0 until 12) {
                        rotate(degrees = i * 30f, pivot = c) {
                            drawRect(
                                Brush.verticalGradient(listOf(aura.copy(alpha = 0.10f + 0.12f * energy), Color.Transparent), startY = c.y, endY = c.y - size.height * 0.75f),
                                topLeft = Offset(c.x - size.width * 0.035f, c.y - size.height * 0.75f),
                                size = Size(size.width * 0.07f, size.height * 0.75f),
                            )
                        }
                    }
                }
            }
            val r = size.minDimension * (0.55f + 0.06f * energy) * pulse
            drawCircle(Brush.radialGradient(listOf(aura.copy(alpha = 0.35f + 0.3f * energy), Color.Transparent), c, r), r, c)
            val floor = Offset(size.width / 2f, oy + rig.feet * k)
            drawOval(
                Brush.radialGradient(listOf(aura.copy(alpha = 0.55f), Color.Transparent), floor, rig.w * k * 0.45f),
                topLeft = Offset(floor.x - rig.w * k * 0.45f, floor.y - rig.h * k * 0.03f),
                size = Size(rig.w * k * 0.9f, rig.h * k * 0.06f),
            )

            // Breath: ~14 breaths a minute, a touch quicker in than out. Sway: slow weight shift.
            val phase = (t / 4.2f) % 1f
            val breath = if (phase < 0.42f) 0.5f - 0.5f * cos(PI.toFloat() * phase / 0.42f)
                         else 0.5f + 0.5f * cos(PI.toFloat() * (phase - 0.42f) / 0.58f)
            val sway = sin(2f * PI.toFloat() * t / 9.5f)
            drawIntoCanvas { canvas ->
                renderer.draw(canvas.nativeCanvas, ox, oy, k, t, breath, sway, pump, glow)
            }
        }
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

    fun draw(canvas: android.graphics.Canvas, ox: Float, oy: Float, k: Float, t: Float, breath: Float, sway: Float, pump: Float, glow: Float) {
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(k, k)
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            ShaderPath.update(shader, t, breath, sway, pump, glow)
            paint.shader = shader as Shader
            canvas.drawRect(0f, 0f, rig.w, rig.h, paint)
        } else {
            for (i in 0 until capeW.size) {
                val x = base[i * 2]; val y = base[i * 2 + 1]
                rigDisplacement(rig, x, y, breath, sway, pump, tmp)
                capeFlutter(rig, x, y, capeW[i], t, tmp2)
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

    fun update(shader: Any, t: Float, breath: Float, sway: Float, pump: Float, glow: Float) {
        val s = shader as RuntimeShader
        s.setFloatUniform("time", t)
        s.setFloatUniform("breath", breath)
        s.setFloatUniform("sway", sway)
        s.setFloatUniform("pump", pump)
        s.setFloatUniform("glow", glow)
    }
}
