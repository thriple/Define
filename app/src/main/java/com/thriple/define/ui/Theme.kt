package com.thriple.define.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.thriple.define.R
import com.thriple.define.engine.Rarity

object Palette {
    val Ink = Color(0xFF0B0A2A)
    val InkDeep = Color(0xFF05041A)
    val Magenta = Color(0xFFFF2E93)
    val Cyan = Color(0xFF00E5FF)
    val Lime = Color(0xFFC6FF00)
    val Orange = Color(0xFFFF8A00)
    val Violet = Color(0xFF7C4DFF)
    val Gold = Color(0xFFFFC233)
    val White = Color(0xFFFFFFFF)
    val Dim = Color(0xB3FFFFFF)

    /** Background blob colours shift with the time of day: sunrise mornings, pool-blue nights. */
    fun forHour(hour: Int): List<Color> = when (hour) {
        in 5..10 -> listOf(Orange, Magenta, Gold, Violet)
        in 11..16 -> listOf(Cyan, Magenta, Lime, Violet)
        in 17..22 -> listOf(Color(0xFF0091FF), Violet, Magenta, Cyan)
        else -> listOf(Violet, Color(0xFF0057FF), Magenta, Cyan)
    }

    fun rarity(r: Rarity): Color = Color(r.argb)
}

object Type {
    val Display = FontFamily(Font(R.font.bungee))
    val Heavy = FontFamily(Font(R.font.archivo_black))

    val Huge = TextStyle(fontFamily = Display, fontSize = 64.sp, color = Palette.White)
    val Big = TextStyle(fontFamily = Display, fontSize = 40.sp, color = Palette.White)
    val Title = TextStyle(fontFamily = Display, fontSize = 26.sp, color = Palette.White)
    val Sub = TextStyle(fontFamily = Heavy, fontSize = 17.sp, color = Palette.White)
    val Label = TextStyle(fontFamily = Heavy, fontSize = 13.sp, color = Palette.Dim, letterSpacing = 1.2.sp)
    val Body = TextStyle(fontSize = 15.sp, color = Palette.Dim, lineHeight = 21.sp)
}
