package com.thriple.define.ui

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin

/**
 * The "puppet" behind the flat hero image: body landmarks per stage (pixels in the 463x900 stage image,
 * measured automatically from each silhouette by art/tools/rig_landmarks.py + rig_fit.py) and the rules
 * that turn breath / sway / pump into a displacement for any point of the image.
 *
 * Anatomy: the ribcage widens around the chest; shoulders, neck and head ride up together (the head is only
 * translated, never scaled); the arms hang from the shoulders and move rigidly with them down to the hands;
 * the abdomen takes less of the lift toward the hips; legs are planted. Sway shifts the upper body over the
 * feet, bending the legs, instead of rotating the whole figure.
 *
 * The same math exists as AGSL in [HERO_SHADER] (per-pixel, Android 13+) and here for the mesh fallback.
 */
data class Rig(
    val w: Float, val h: Float,
    val shoulder: Float, val armpit: Float, val hand: Float, val crotch: Float, val feet: Float,
    val cx: Float, val torsoHalf: Float,
)

object HeroRigs {
    val stages = listOf(
        Rig(w = 463f, h = 900f, shoulder = 182.0f, armpit = 330.0f, hand = 520.0f, crotch = 471.0f, feet = 875f, cx = 233.4f, torsoHalf = 66f),
        Rig(w = 463f, h = 900f, shoulder = 176.0f, armpit = 329.0f, hand = 515.0f, crotch = 473.0f, feet = 880f, cx = 229.9f, torsoHalf = 68f),
        Rig(w = 463f, h = 900f, shoulder = 174.0f, armpit = 334.0f, hand = 516.0f, crotch = 476.0f, feet = 889f, cx = 230.4f, torsoHalf = 70f),
        Rig(w = 463f, h = 900f, shoulder = 173.0f, armpit = 338.0f, hand = 515.0f, crotch = 479.0f, feet = 890f, cx = 230.2f, torsoHalf = 72f),
        Rig(w = 463f, h = 900f, shoulder = 165.0f, armpit = 356.0f, hand = 514.0f, crotch = 478.0f, feet = 884f, cx = 230.5f, torsoHalf = 74f),
        Rig(w = 463f, h = 900f, shoulder = 169.9f, armpit = 365.0f, hand = 512.0f, crotch = 477.0f, feet = 889f, cx = 231.5f, torsoHalf = 76f),
        Rig(w = 463f, h = 900f, shoulder = 168.5f, armpit = 356.0f, hand = 513.0f, crotch = 478.0f, feet = 888f, cx = 230.8f, torsoHalf = 78f),
        Rig(w = 463f, h = 900f, shoulder = 167.1f, armpit = 378.5f, hand = 508.0f, crotch = 478.0f, feet = 887f, cx = 231.6f, torsoHalf = 80f),
    )

    fun forStage(stage: Int): Rig = stages[(stage - 1).coerceIn(0, stages.lastIndex)]
}

private fun sstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Writes (dx, dy) for point (x, y) into out[0], out[1]. Mirrors disp() in the shader. */
fun rigDisplacement(r: Rig, x: Float, y: Float, breath: Float, sway: Float, pump: Float, out: FloatArray) {
    val a = 0.0065f * r.h * (breath + 0.6f * pump)
    val b = 0.016f * breath + 0.05f * pump
    val chestY = r.shoulder + 0.55f * (r.armpit - r.shoulder)
    val armBlend = sstep(r.torsoHalf - 6f, r.torsoHalf + 12f, abs(x - r.cx))
    val armFade = 1f - sstep(r.hand, r.hand + 0.05f * r.h, y)
    val lift = (1f - armBlend) * (1f - sstep(r.shoulder, r.crotch, y)) + armBlend * armFade
    val q = (y - chestY) / (0.55f * (r.armpit - r.shoulder))
    val g = exp(-q * q)
    val gArm = sstep(r.shoulder, chestY, y) * armFade
    var dx = (1f - armBlend) * b * (x - r.cx) * g + armBlend * sign(x - r.cx) * b * r.torsoHalf * gArm
    dx += 0.004f * r.h * sway * (1f - sstep(r.crotch, r.feet, y))
    out[0] = dx
    out[1] = -a * lift
}

/** Cape flutter offset for a point with cape weight [cape] (0..1). Mirrors the shader. */
fun capeFlutter(r: Rig, x: Float, y: Float, cape: Float, time: Float, out: FloatArray) {
    val hem = sstep(r.hand, r.feet, y)
    out[0] = cape * hem * 0.012f * r.h * sin(y * 0.035f - time * 2.6f)
    out[1] = cape * hem * 0.004f * r.h * sin(x * 0.05f - time * 1.9f)
}

const val HERO_SHADER = """
uniform shader image;
uniform shader rigmap;
uniform float H;
uniform float sh;
uniform float armpit;
uniform float hand;
uniform float crotch;
uniform float feet;
uniform float cx;
uniform float th;
uniform float breath;
uniform float sway;
uniform float pump;
uniform float time;
uniform float glow;

float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float2 disp(float2 p) {
    float A = 0.0065 * H * (breath + 0.6 * pump);
    float B = 0.016 * breath + 0.05 * pump;
    float chestY = sh + 0.55 * (armpit - sh);
    float armBlend = sstep(th - 6.0, th + 12.0, abs(p.x - cx));
    float armFade = 1.0 - sstep(hand, hand + 0.05 * H, p.y);
    float lift = (1.0 - armBlend) * (1.0 - sstep(sh, crotch, p.y)) + armBlend * armFade;
    float q = (p.y - chestY) / (0.55 * (armpit - sh));
    float g = exp(-q * q);
    float gArm = sstep(sh, chestY, p.y) * armFade;
    float dx = (1.0 - armBlend) * B * (p.x - cx) * g + armBlend * sign(p.x - cx) * B * th * gArm;
    dx += 0.004 * H * sway * (1.0 - sstep(crotch, feet, p.y));
    return float2(dx, -A * lift);
}

half4 main(float2 p) {
    float2 src = p - disp(p);
    float cape = float(rigmap.eval(src).r);
    float hem = sstep(hand, feet, p.y);
    src.x -= cape * hem * 0.012 * H * sin(p.y * 0.035 - time * 2.6);
    src.y -= cape * hem * 0.004 * H * sin(p.x * 0.05 - time * 1.9);
    float4 c = float4(image.eval(src));
    float gl = float(rigmap.eval(src).g);
    float pulse = 0.55 + 0.45 * sin(time * 3.0);
    c.rgb += float3(0.25, 0.95, 1.0) * gl * glow * pulse * c.a;
    return half4(c);
}
"""
