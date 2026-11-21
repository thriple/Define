package com.thriple.define.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.thriple.define.engine.Ink
import com.thriple.define.engine.Pictogram
import kotlin.math.min

/**
 * Looping neon stick-figure demo of an exercise, at the pace it should be done. Pass [at] (seconds into the
 * repetition) to drive it from the music's beat clock instead of its own.
 */
@Composable
fun PictogramView(exerciseId: String, modifier: Modifier = Modifier, at: Float? = null) {
    val time by rememberFrameTime()
    Canvas(modifier) {
        val f = Pictogram.frame(exerciseId, at ?: time)
        val s = min(size.width, size.height)
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        fun col(i: Ink): Color = when (i) {
            Ink.FAR -> Color.White.copy(alpha = 0.38f)
            Ink.BODY -> Color.White
            Ink.WORK -> Palette.Magenta
            Ink.WEIGHT -> Palette.Gold
            Ink.SCENE -> Palette.Cyan.copy(alpha = 0.45f)
        }
        for (pass in 0..1) {
            for (l in f.lines) {
                val c = col(l.ink)
                val w = l.width * s * (if (pass == 0) 2.4f else 1f)
                drawLine(
                    if (pass == 0) c.copy(alpha = c.alpha * 0.18f) else c,
                    Offset(ox + l.x1 * s, oy + l.y1 * s), Offset(ox + l.x2 * s, oy + l.y2 * s),
                    strokeWidth = w, cap = StrokeCap.Round,
                )
            }
            for (d in f.dots) {
                val c = col(d.ink)
                val r = d.r * s * (if (pass == 0) 1.6f else 1f)
                if (d.filled) drawCircle(if (pass == 0) c.copy(alpha = c.alpha * 0.2f) else c, r, Offset(ox + d.x * s, oy + d.y * s))
                else drawCircle(c, r, Offset(ox + d.x * s, oy + d.y * s), style = Stroke(0.02f * s))
            }
        }
    }
}
