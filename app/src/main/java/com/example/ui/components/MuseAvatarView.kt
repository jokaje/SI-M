package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.CreatureDna
import com.example.model.CreatureEmotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-fidelity 64x64 Muse Pixel Creature renderer for SI-M.
 * Implements living procedural animations: breathing, blinking, ear/wing perk,
 * tail physics, aura sparkles, voice waves, and interactive petting!
 */
@Composable
fun MuseAvatarView(
    dna: CreatureDna,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    showGrid: Boolean = true,
    emotion: CreatureEmotion = dna.emotion,
    onPet: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "museAnim")
    val animTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    var happyTrigger by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    val bodyColor = remember(dna.bodyHex) { dna.parseColor(dna.bodyHex, Color(0xFFF26B38)) }
    val skinColor = remember(dna.skinHex) { dna.parseColor(dna.skinHex, Color(0xFFFFF0DC)) }
    val cyanColor = remember(dna.cyanHex) { dna.parseColor(dna.cyanHex, Color(0xFF6CF8EC)) }
    val irisColor = remember(dna.irisHex) { dna.parseColor(dna.irisHex, Color(0xFF1CD4A8)) }
    val blushColor = remember(dna.blushHex) { dna.parseColor(dna.blushHex, Color(0xFFFF7898)) }

    Box(
        modifier = modifier
            .size(size)
            .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = cyanColor.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF090D1A))
            .border(1.5.dp, cyanColor.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                happyTrigger = 1.0f
                onPet?.invoke()
                scope.launch {
                    delay(1800)
                    happyTrigger = 0f
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = animTime * 0.05f
            val canvasW = this.size.width
            val canvasH = this.size.height

            // Calculate motion parameters based on emotion
            val isPet = happyTrigger > 0f
            val bob = if (isPet) sin(t * 8f) * (canvasH * 0.035f) else sin(t * 1.8f) * (canvasH * 0.018f)
            val breathe = sin(t * 2.2f) * 0.04f
            val earPerk = when (emotion) {
                CreatureEmotion.LISTENING -> 1.0f
                CreatureEmotion.SURPRISED -> 1.2f
                CreatureEmotion.SLEEPY -> -0.6f
                CreatureEmotion.ANGRY -> -0.4f
                else -> if (isPet) 0.8f else 0.0f
            }

            val cx = canvasW * 0.5f
            val cy = canvasH * 0.52f + bob
            val baseRx = (canvasW * 0.26f) * (1f + breathe)
            val baseRy = (canvasH * 0.32f) * (1f - breathe)

            // 1. Soft Cosmic Aura & Orbital Glow
            drawAuraGlow(cx, cy, baseRx * 1.7f, cyanColor, t)

            // 2. Wings (if equipped)
            if (dna.wings > 0) {
                drawWings(cx, cy, baseRx, baseRy, dna.wings, cyanColor, bodyColor, t)
            }

            // 3. Tail (if equipped)
            if (dna.tail > 0) {
                drawTail(cx, cy, baseRx, baseRy, dna.tail, bodyColor, skinColor, cyanColor, t, isPet)
            }

            // 4. Ears / Horns / Gills
            drawEars(cx, cy, baseRx, baseRy, dna.ears, bodyColor, skinColor, cyanColor, blushColor, earPerk, t)

            // 5. Feet
            drawFeet(cx, cy, baseRx, baseRy, bodyColor, bob)

            // 6. Main Torso Body
            drawBody(cx, cy, baseRx, baseRy, bodyColor, cyanColor)

            // 7. Belly Pattern / Mask
            drawBelly(cx, cy, baseRx, baseRy, dna.belly, skinColor, cyanColor)

            // 8. Arms
            drawArms(cx, cy, baseRx, baseRy, bodyColor, emotion, isPet, t)

            // 9. Floating Crown / Forehead Gem
            if (dna.crown > 0) {
                drawCrown(cx, cy - baseRy * 0.9f, dna.crown, cyanColor, Color.White, t)
            }

            // 10. Cute Face (Eyes, Brows, Blushes, Mouth)
            drawFace(cx, cy - baseRy * 0.15f, baseRx, baseRy, dna, irisColor, blushColor, emotion, isPet, t)

            // 11. Emotion-Specific Overlays (Hearts, Sparkles, Waves, Zzz)
            drawEmotionEffects(cx, cy - baseRy, emotion, isPet, cyanColor, blushColor, t)

            // 12. Hardware Pixel Grid Overlay (OLED matrix look)
            if (showGrid) {
                drawPixelGrid(canvasW, canvasH)
            }
        }
    }
}

private fun DrawScope.drawAuraGlow(cx: Float, cy: Float, radius: Float, color: Color, t: Float) {
    val pulse = 1f + sin(t * 1.5f) * 0.08f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.08f), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius * pulse
        ),
        radius = radius * pulse,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawBody(cx: Float, cy: Float, rx: Float, ry: Float, bodyColor: Color, cyanColor: Color) {
    // Shaded egg-like creature torso
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                bodyColor.copy(alpha = 1f),
                bodyColor,
                Color(
                    red = (bodyColor.red * 0.72f).coerceIn(0f, 1f),
                    green = (bodyColor.green * 0.72f).coerceIn(0f, 1f),
                    blue = (bodyColor.blue * 0.72f).coerceIn(0f, 1f)
                )
            ),
            center = Offset(cx - rx * 0.2f, cy - ry * 0.25f),
            radius = rx * 1.3f
        ),
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f)
    )
    // Subtle rim lighting
    drawOval(
        color = cyanColor.copy(alpha = 0.4f),
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f),
        style = Stroke(width = 2.5f)
    )
}

private fun DrawScope.drawBelly(cx: Float, cy: Float, rx: Float, ry: Float, bellyType: Int, skinColor: Color, cyanColor: Color) {
    when (bellyType) {
        0 -> { // Glowing belly core
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(cyanColor.copy(alpha = 0.85f), cyanColor.copy(alpha = 0.2f), Color.Transparent),
                    center = Offset(cx, cy + ry * 0.2f),
                    radius = rx * 0.6f
                ),
                topLeft = Offset(cx - rx * 0.45f, cy - ry * 0.05f),
                size = Size(rx * 0.9f, ry * 0.6f)
            )
        }
        1 -> { // Face hoodie panel
            drawOval(
                color = skinColor,
                topLeft = Offset(cx - rx * 0.65f, cy - ry * 0.65f),
                size = Size(rx * 1.3f, ry * 0.8f)
            )
        }
        2 -> { // Fox cheek & bib
            val bibPath = Path().apply {
                moveTo(cx - rx * 0.5f, cy)
                quadraticTo(cx, cy + ry * 0.65f, cx + rx * 0.5f, cy)
                quadraticTo(cx, cy + ry * 0.25f, cx - rx * 0.5f, cy)
                close()
            }
            drawPath(bibPath, color = skinColor)
        }
        else -> { // Round pearl belly
            drawOval(
                color = skinColor,
                topLeft = Offset(cx - rx * 0.45f, cy + ry * 0.05f),
                size = Size(rx * 0.9f, ry * 0.65f)
            )
        }
    }
}

private fun DrawScope.drawEars(
    cx: Float, cy: Float, rx: Float, ry: Float, earType: Int,
    bodyColor: Color, skinColor: Color, cyanColor: Color, blushColor: Color, perk: Float, t: Float
) {
    val earTop = cy - ry * 0.7f
    when (earType) {
        1 -> { // Fox Ears
            val leftEar = Path().apply {
                moveTo(cx - rx * 0.75f, earTop)
                lineTo(cx - rx * 0.65f - perk * 10f, earTop - ry * 0.7f)
                lineTo(cx - rx * 0.2f, earTop - ry * 0.2f)
                close()
            }
            val rightEar = Path().apply {
                moveTo(cx + rx * 0.75f, earTop)
                lineTo(cx + rx * 0.65f + perk * 10f, earTop - ry * 0.7f)
                lineTo(cx + rx * 0.2f, earTop - ry * 0.2f)
                close()
            }
            drawPath(leftEar, bodyColor)
            drawPath(rightEar, bodyColor)
            // Inner ear blush
            drawCircle(blushColor.copy(alpha = 0.7f), radius = rx * 0.15f, center = Offset(cx - rx * 0.5f, earTop - ry * 0.3f))
            drawCircle(blushColor.copy(alpha = 0.7f), radius = rx * 0.15f, center = Offset(cx + rx * 0.5f, earTop - ry * 0.3f))
        }
        2 -> { // Dragon Horns
            val hornL = Path().apply {
                moveTo(cx - rx * 0.5f, earTop)
                quadraticTo(cx - rx * 0.85f, earTop - ry * 0.4f, cx - rx * 0.6f, earTop - ry * 0.75f)
                lineTo(cx - rx * 0.35f, earTop - ry * 0.2f)
                close()
            }
            val hornR = Path().apply {
                moveTo(cx + rx * 0.5f, earTop)
                quadraticTo(cx + rx * 0.85f, earTop - ry * 0.4f, cx + rx * 0.6f, earTop - ry * 0.75f)
                lineTo(cx + rx * 0.35f, earTop - ry * 0.2f)
                close()
            }
            drawPath(hornL, skinColor)
            drawPath(hornR, skinColor)
        }
        3 -> { // Axolotl Gills
            for (i in 0..2) {
                val wave = sin(t * 3f + i) * 6f
                val gy = earTop + i * (ry * 0.2f)
                drawOval(
                    color = blushColor,
                    topLeft = Offset(cx - rx * 1.35f + wave, gy),
                    size = Size(rx * 0.5f, ry * 0.14f)
                )
                drawOval(
                    color = blushColor,
                    topLeft = Offset(cx + rx * 0.85f - wave, gy),
                    size = Size(rx * 0.5f, ry * 0.14f)
                )
            }
        }
        4 -> { // Owl tufts
            drawCircle(bodyColor, radius = rx * 0.22f, center = Offset(cx - rx * 0.55f, earTop - ry * 0.3f))
            drawCircle(bodyColor, radius = rx * 0.22f, center = Offset(cx + rx * 0.55f, earTop - ry * 0.3f))
        }
        5 -> { // Flame Crest
            val wave = sin(t * 4f) * 8f
            drawCircle(cyanColor, radius = rx * 0.26f, center = Offset(cx + wave, earTop - ry * 0.4f))
            drawCircle(bodyColor, radius = rx * 0.22f, center = Offset(cx, earTop - ry * 0.2f))
        }
        6 -> { // Lop bunny ears
            drawOval(bodyColor, topLeft = Offset(cx - rx * 1.25f, earTop), size = Size(rx * 0.38f, ry * 0.9f))
            drawOval(bodyColor, topLeft = Offset(cx + rx * 0.87f, earTop), size = Size(rx * 0.38f, ry * 0.9f))
        }
        7 -> { // Alien Antennae
            val wave = sin(t * 3f) * 6f
            drawLine(cyanColor, Offset(cx - rx * 0.35f, earTop), Offset(cx - rx * 0.5f + wave, earTop - ry * 0.6f), strokeWidth = 3f)
            drawLine(cyanColor, Offset(cx + rx * 0.35f, earTop), Offset(cx + rx * 0.5f - wave, earTop - ry * 0.6f), strokeWidth = 3f)
            drawCircle(Color.White, radius = 6f, center = Offset(cx - rx * 0.5f + wave, earTop - ry * 0.6f))
            drawCircle(Color.White, radius = 6f, center = Offset(cx + rx * 0.5f - wave, earTop - ry * 0.6f))
        }
    }
}

private fun DrawScope.drawCrown(cx: Float, topY: Float, crownType: Int, cyanColor: Color, white: Color, t: Float) {
    val floatY = topY + sin(t * 2.5f) * 4f
    when (crownType) {
        1 -> { // Floating Star Gem
            drawCircle(white, radius = 7f, center = Offset(cx, floatY))
            drawCircle(cyanColor, radius = 4f, center = Offset(cx, floatY))
        }
        2 -> { // Crescent Moon
            drawCircle(cyanColor, radius = 10f, center = Offset(cx, floatY))
            drawCircle(Color(0xFF090D1A), radius = 8f, center = Offset(cx + 4f, floatY - 2f))
        }
        3 -> { // Crystal Spike
            val path = Path().apply {
                moveTo(cx, floatY - 14f)
                lineTo(cx - 7f, floatY + 6f)
                lineTo(cx + 7f, floatY + 6f)
                close()
            }
            drawPath(path, cyanColor)
        }
        4 -> { // Halo Ring
            drawOval(cyanColor, topLeft = Offset(cx - 18f, floatY - 4f), size = Size(36f, 10f), style = Stroke(width = 3.5f))
        }
    }
}

private fun DrawScope.drawWings(cx: Float, cy: Float, rx: Float, ry: Float, wingType: Int, cyanColor: Color, bodyColor: Color, t: Float) {
    val flap = sin(t * 4.5f) * 8f
    val wingColor = if (wingType == 2) cyanColor.copy(alpha = 0.65f) else bodyColor.copy(alpha = 0.85f)
    drawOval(wingColor, topLeft = Offset(cx - rx * 1.55f, cy - ry * 0.4f + flap), size = Size(rx * 0.7f, ry * 0.45f))
    drawOval(wingColor, topLeft = Offset(cx + rx * 0.85f, cy - ry * 0.4f + flap), size = Size(rx * 0.7f, ry * 0.45f))
}

private fun DrawScope.drawTail(cx: Float, cy: Float, rx: Float, ry: Float, tailType: Int, bodyColor: Color, skinColor: Color, cyanColor: Color, t: Float, isPet: Boolean) {
    val speed = if (isPet) 12f else 3.5f
    val wag = sin(t * speed) * (rx * 0.4f)
    val tailBaseX = cx + rx * 0.55f
    val tailBaseY = cy + ry * 0.6f

    val tailPath = Path().apply {
        moveTo(tailBaseX, tailBaseY)
        quadraticTo(tailBaseX + rx * 0.6f + wag, tailBaseY - ry * 0.4f, tailBaseX + rx * 0.8f + wag, tailBaseY - ry * 0.7f)
        quadraticTo(tailBaseX + rx * 0.4f + wag, tailBaseY - ry * 0.2f, tailBaseX - rx * 0.1f, tailBaseY)
        close()
    }
    drawPath(tailPath, bodyColor)
    // Tip
    drawCircle(if (tailType == 2) cyanColor else skinColor, radius = rx * 0.16f, center = Offset(tailBaseX + rx * 0.78f + wag, tailBaseY - ry * 0.68f))
}

private fun DrawScope.drawFeet(cx: Float, cy: Float, rx: Float, ry: Float, bodyColor: Color, bob: Float) {
    val footY = cy + ry * 0.85f
    drawOval(bodyColor, topLeft = Offset(cx - rx * 0.6f, footY), size = Size(rx * 0.45f, ry * 0.2f))
    drawOval(bodyColor, topLeft = Offset(cx + rx * 0.15f, footY), size = Size(rx * 0.45f, ry * 0.2f))
}

private fun DrawScope.drawArms(cx: Float, cy: Float, rx: Float, ry: Float, bodyColor: Color, emotion: CreatureEmotion, isPet: Boolean, t: Float) {
    val armY = cy + ry * 0.1f
    val armWave = if (isPet) sin(t * 12f) * 6f else 0f
    drawOval(bodyColor, topLeft = Offset(cx - rx * 0.95f, armY + armWave), size = Size(rx * 0.28f, ry * 0.35f))
    drawOval(bodyColor, topLeft = Offset(cx + rx * 0.67f, armY - armWave), size = Size(rx * 0.28f, ry * 0.35f))
}

private fun DrawScope.drawFace(
    cx: Float, faceY: Float, rx: Float, ry: Float, dna: CreatureDna,
    irisColor: Color, blushColor: Color, emotion: CreatureEmotion, isPet: Boolean, t: Float
) {
    val eyeDist = rx * 0.38f
    val eyeRadius = rx * 0.16f

    // Eyes
    when {
        isPet || emotion == CreatureEmotion.LOVE -> {
            // Happy closed eyes curves (^_^)
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - eyeDist - eyeRadius, faceY - eyeRadius),
                size = Size(eyeRadius * 2f, eyeRadius * 1.6f),
                style = Stroke(width = 4f)
            )
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx + eyeDist - eyeRadius, faceY - eyeRadius),
                size = Size(eyeRadius * 2f, eyeRadius * 1.6f),
                style = Stroke(width = 4f)
            )
        }
        emotion == CreatureEmotion.SLEEPY -> {
            drawLine(Color.White, Offset(cx - eyeDist - eyeRadius, faceY), Offset(cx - eyeDist + eyeRadius, faceY), strokeWidth = 3.5f)
            drawLine(Color.White, Offset(cx + eyeDist - eyeRadius, faceY), Offset(cx + eyeDist + eyeRadius, faceY), strokeWidth = 3.5f)
        }
        emotion == CreatureEmotion.ERROR -> {
            // X eyes
            drawCross(cx - eyeDist, faceY, eyeRadius * 0.7f, Color.White)
            drawCross(cx + eyeDist, faceY, eyeRadius * 0.7f, Color.White)
        }
        else -> {
            // Normal / expressive animated eyes
            val gazeX = sin(t * 1.4f) * (eyeRadius * 0.3f)
            drawCircle(Color.White, radius = eyeRadius, center = Offset(cx - eyeDist, faceY))
            drawCircle(Color.White, radius = eyeRadius, center = Offset(cx + eyeDist, faceY))

            // Iris
            drawCircle(irisColor, radius = eyeRadius * 0.75f, center = Offset(cx - eyeDist + gazeX, faceY))
            drawCircle(irisColor, radius = eyeRadius * 0.75f, center = Offset(cx + eyeDist + gazeX, faceY))

            // Specular Sparkle Highlight
            drawCircle(Color.White, radius = eyeRadius * 0.28f, center = Offset(cx - eyeDist + gazeX - 2f, faceY - 2f))
            drawCircle(Color.White, radius = eyeRadius * 0.28f, center = Offset(cx + eyeDist + gazeX - 2f, faceY - 2f))
        }
    }

    // Rosy Cheeks Blush
    val blushRadius = rx * 0.16f
    drawCircle(blushColor.copy(alpha = if (isPet) 0.85f else 0.55f), radius = blushRadius, center = Offset(cx - eyeDist * 1.55f, faceY + eyeRadius * 0.9f))
    drawCircle(blushColor.copy(alpha = if (isPet) 0.85f else 0.55f), radius = blushRadius, center = Offset(cx + eyeDist * 1.55f, faceY + eyeRadius * 0.9f))

    // Cute Mouth
    val mouthY = faceY + eyeRadius * 1.25f
    when {
        isPet || emotion == CreatureEmotion.LOVE -> {
            drawArc(
                color = Color.White,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(cx - 10f, mouthY - 4f),
                size = Size(20f, 16f)
            )
        }
        emotion == CreatureEmotion.SPEAKING -> {
            val talkHeight = 6f + abs(sin(t * 9f)) * 12f
            drawOval(
                color = Color.White,
                topLeft = Offset(cx - 7f, mouthY),
                size = Size(14f, talkHeight)
            )
        }
        else -> {
            drawArc(
                color = Color.White,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - 7f, mouthY - 2f),
                size = Size(14f, 10f),
                style = Stroke(width = 3f)
            )
        }
    }
}

private fun DrawScope.drawCross(x: Float, y: Float, size: Float, color: Color) {
    drawLine(color, Offset(x - size, y - size), Offset(x + size, y + size), strokeWidth = 3f)
    drawLine(color, Offset(x + size, y - size), Offset(x - size, y + size), strokeWidth = 3f)
}

private fun DrawScope.drawEmotionEffects(
    cx: Float, topY: Float, emotion: CreatureEmotion, isPet: Boolean,
    cyanColor: Color, blushColor: Color, t: Float
) {
    if (isPet || emotion == CreatureEmotion.LOVE) {
        // Floating hearts
        val heartPh = (t * 0.8f) % 1f
        val hx = cx + sin(t * 3f) * 20f
        val hy = topY - heartPh * 30f
        drawHeart(hx, hy, 10f, blushColor)
    }

    if (emotion == CreatureEmotion.THINKING) {
        // Thought dots
        drawCircle(cyanColor, radius = 3.5f, center = Offset(cx + 25f, topY + 10f))
        drawCircle(cyanColor, radius = 5f, center = Offset(cx + 34f, topY))
        drawCircle(cyanColor, radius = 7f, center = Offset(cx + 46f, topY - 14f))
    }

    if (emotion == CreatureEmotion.SPEAKING || emotion == CreatureEmotion.LISTENING) {
        // Futuristic audio sonar rings
        val wavePh = (t * 2f) % 1f
        drawCircle(
            color = cyanColor.copy(alpha = (1f - wavePh) * 0.4f),
            radius = 35f + wavePh * 45f,
            center = Offset(cx, topY + 40f),
            style = Stroke(width = 2f)
        )
    }
}

private fun DrawScope.drawHeart(x: Float, y: Float, size: Float, color: Color) {
    val heartPath = Path().apply {
        moveTo(x, y + size * 0.6f)
        cubicTo(x - size, y - size * 0.4f, x - size * 0.5f, y - size, x, y - size * 0.4f)
        cubicTo(x + size * 0.5f, y - size, x + size, y - size * 0.4f, x, y + size * 0.6f)
        close()
    }
    drawPath(heartPath, color)
}

private fun DrawScope.drawPixelGrid(w: Float, h: Float) {
    val step = w / 48f
    val gridColor = Color(0x0CFFFFFF)
    var x = 0f
    while (x <= w) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= h) {
        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        y += step
    }
}
