package com.thriple.define.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.thriple.define.R
import kotlin.math.sin

val heroStages = listOf(
    R.drawable.hero_stage1, R.drawable.hero_stage2, R.drawable.hero_stage3, R.drawable.hero_stage4,
    R.drawable.hero_stage5, R.drawable.hero_stage6, R.drawable.hero_stage7, R.drawable.hero_stage8,
)

/**
 * The hero, alive: breathing, a slight sway, a pulsing aura and slowly turning light rays behind,
 * and a glowing floor. [energy] (0..1) brightens the aura, e.g. with streak momentum.
 */
@Composable
fun HeroView(stage: Int, modifier: Modifier = Modifier, aura: Color = Palette.Cyan, energy: Float = 0.5f, rays: Boolean = true) {
    val time by rememberFrameTime()
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time
            val c = Offset(size.width / 2f, size.height * 0.42f)
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
            val floor = Offset(size.width / 2f, size.height * 0.97f)
            drawOval(
                Brush.radialGradient(listOf(aura.copy(alpha = 0.55f), Color.Transparent), floor, size.width * 0.32f),
                topLeft = Offset(floor.x - size.width * 0.32f, floor.y - size.height * 0.035f),
                size = Size(size.width * 0.64f, size.height * 0.07f),
            )
        }
        Image(
            painter = painterResource(heroStages[(stage - 1).coerceIn(0, heroStages.lastIndex)]),
            contentDescription = "Your hero, stage $stage",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = time
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    scaleY = 1f + 0.012f * sin(t * 2.4f)
                    scaleX = 1f - 0.004f * sin(t * 2.4f)
                    rotationZ = 0.6f * sin(t * 0.7f)
                    translationY = -4f * sin(t * 1.2f)
                },
        )
    }
}
