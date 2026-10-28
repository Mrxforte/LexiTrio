package com.example.ui.screens.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Speech bubble tail direction.
 */
enum class SpeechBubbleTail {
    Bottom,
    Left
}

/**
 * Custom Compose Shape for speech bubbles with seamless triangular pointer tail.
 */
class SpeechBubbleShape(
    private val tail: SpeechBubbleTail,
    private val cornerRadius: Dp = 16.dp,
    private val tailWidth: Dp = 14.dp,
    private val tailHeight: Dp = 9.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cr = with(density) { cornerRadius.toPx() }
        val tw = with(density) { tailWidth.toPx() }
        val th = with(density) { tailHeight.toPx() }

        val path = Path()

        when (tail) {
            SpeechBubbleTail.Bottom -> {
                val boxBottom = size.height - th
                val boxTop = 0f
                val boxLeft = 0f
                val boxRight = size.width
                val tailCenter = size.width / 2f

                path.moveTo(boxLeft + cr, boxTop)
                path.lineTo(boxRight - cr, boxTop)
                path.quadraticTo(boxRight, boxTop, boxRight, boxTop + cr)
                path.lineTo(boxRight, boxBottom - cr)
                path.quadraticTo(boxRight, boxBottom, boxRight - cr, boxBottom)

                // Tail on bottom
                path.lineTo(tailCenter + tw / 2f, boxBottom)
                path.lineTo(tailCenter, size.height)
                path.lineTo(tailCenter - tw / 2f, boxBottom)

                path.lineTo(boxLeft + cr, boxBottom)
                path.quadraticTo(boxLeft, boxBottom, boxLeft, boxBottom - cr)
                path.lineTo(boxLeft, boxTop + cr)
                path.quadraticTo(boxLeft, boxTop, boxLeft + cr, boxTop)
                path.close()
            }
            SpeechBubbleTail.Left -> {
                val boxLeft = th
                val boxRight = size.width
                val boxTop = 0f
                val boxBottom = size.height
                val tailCenter = size.height * 0.45f

                path.moveTo(boxLeft + cr, boxTop)
                path.lineTo(boxRight - cr, boxTop)
                path.quadraticTo(boxRight, boxTop, boxRight, boxTop + cr)
                path.lineTo(boxRight, boxBottom - cr)
                path.quadraticTo(boxRight, boxBottom, boxRight - cr, boxBottom)
                path.lineTo(boxLeft + cr, boxBottom)
                path.quadraticTo(boxLeft, boxBottom, boxLeft, boxBottom - cr)

                // Tail on left
                path.lineTo(boxLeft, tailCenter + tw / 2f)
                path.lineTo(0f, tailCenter)
                path.lineTo(boxLeft, tailCenter - tw / 2f)

                path.lineTo(boxLeft, boxTop + cr)
                path.quadraticTo(boxLeft, boxTop, boxLeft + cr, boxTop)
                path.close()
            }
        }
        return Outline.Generic(path)
    }
}

/**
 * Speech bubble wrapper composable.
 */
@Composable
fun SpeechBubble(
    tail: SpeechBubbleTail,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = SpeechBubbleShape(tail = tail)
    val paddingModifier = when (tail) {
        SpeechBubbleTail.Bottom -> Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 21.dp)
        SpeechBubbleTail.Left -> Modifier.padding(start = 22.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
    }

    Surface(
        shape = shape,
        color = Color.White,
        border = BorderStroke(1.5.dp, ElingoColors.BorderLight),
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Box(modifier = paddingModifier) {
            content()
        }
    }
}

/**
 * Screen 1 App Icon: Squircle with cute smiling face.
 */
@Composable
fun ElingoIconMascot(
    modifier: Modifier = Modifier.size(110.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cornerRadius = w * 0.25f

        // Squircle body with vertical gradient
        val bodyBrush = Brush.verticalGradient(
            colors = listOf(ElingoColors.PurpleGradientStart, ElingoColors.PurpleGradientEnd)
        )
        drawRoundRect(
            brush = bodyBrush,
            size = size,
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )

        // Eyes
        val eyeRadius = w * 0.082f
        val eyeY = h * 0.44f
        val leftEyeX = w * 0.35f
        val rightEyeX = w * 0.65f

        // White sclera
        drawCircle(color = Color.White, radius = eyeRadius, center = Offset(leftEyeX, eyeY))
        drawCircle(color = Color.White, radius = eyeRadius, center = Offset(rightEyeX, eyeY))

        // Pupil (looking slightly up/center)
        val pupilRadius = eyeRadius * 0.58f
        val pupilOffsetY = -eyeRadius * 0.12f
        drawCircle(
            color = ElingoColors.MascotNavy,
            radius = pupilRadius,
            center = Offset(leftEyeX, eyeY + pupilOffsetY)
        )
        drawCircle(
            color = ElingoColors.MascotNavy,
            radius = pupilRadius,
            center = Offset(rightEyeX, eyeY + pupilOffsetY)
        )

        // Specular highlight in eyes
        val highlightRadius = pupilRadius * 0.38f
        drawCircle(
            color = Color.White,
            radius = highlightRadius,
            center = Offset(leftEyeX + pupilRadius * 0.35f, eyeY + pupilOffsetY - pupilRadius * 0.35f)
        )
        drawCircle(
            color = Color.White,
            radius = highlightRadius,
            center = Offset(rightEyeX + pupilRadius * 0.35f, eyeY + pupilOffsetY - pupilRadius * 0.35f)
        )

        // Rosy Cheeks (Blush)
        val cheekRadius = w * 0.065f
        val cheekY = h * 0.56f
        drawCircle(
            color = ElingoColors.CheekPink.copy(alpha = 0.82f),
            radius = cheekRadius,
            center = Offset(w * 0.20f, cheekY)
        )
        drawCircle(
            color = ElingoColors.CheekPink.copy(alpha = 0.82f),
            radius = cheekRadius,
            center = Offset(w * 0.80f, cheekY)
        )

        // Cute smiling mouth
        val mouthPath = Path().apply {
            moveTo(w * 0.43f, h * 0.58f)
            quadraticTo(w * 0.50f, h * 0.67f, w * 0.57f, h * 0.58f)
        }
        drawPath(
            path = mouthPath,
            color = ElingoColors.MouthDark,
            style = Stroke(width = w * 0.032f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Screen 2 Mascot: Full size water droplet mascot "El" with animated waving arm!
 */
@Composable
fun ElingoCharacterMascot(
    modifier: Modifier = Modifier.size(width = 150.dp, height = 180.dp),
    isWaving: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "armWave"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // --- Stick Feet and Legs ---
        val legStroke = 3.5.dp.toPx()
        val legColor = ElingoColors.MascotNavy
        val legTopY = h * 0.90f
        val legBottomY = h * 0.99f

        // Left leg
        drawLine(
            color = legColor,
            start = Offset(cx - w * 0.10f, legTopY),
            end = Offset(cx - w * 0.11f, legBottomY),
            strokeWidth = legStroke,
            cap = StrokeCap.Round
        )
        // Left shoe
        drawRoundRect(
            color = legColor,
            topLeft = Offset(cx - w * 0.17f, legBottomY - 3.dp.toPx()),
            size = Size(18.dp.toPx(), 7.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        // Right leg
        drawLine(
            color = legColor,
            start = Offset(cx + w * 0.10f, legTopY),
            end = Offset(cx + w * 0.11f, legBottomY),
            strokeWidth = legStroke,
            cap = StrokeCap.Round
        )
        // Right shoe
        drawRoundRect(
            color = legColor,
            topLeft = Offset(cx + w * 0.07f, legBottomY - 3.dp.toPx()),
            size = Size(18.dp.toPx(), 7.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        // --- Stick Arms ---
        val armStroke = 3.2.dp.toPx()

        // Left arm (resting at side/hip)
        val leftArmPath = Path().apply {
            moveTo(cx - w * 0.35f, h * 0.65f)
            quadraticTo(cx - w * 0.47f, h * 0.68f, cx - w * 0.44f, h * 0.75f)
        }
        drawPath(
            path = leftArmPath,
            color = legColor,
            style = Stroke(width = armStroke, cap = StrokeCap.Round)
        )
        // Left hand (little fist)
        drawCircle(
            color = legColor,
            radius = 4.2.dp.toPx(),
            center = Offset(cx - w * 0.44f, h * 0.75f)
        )

        // Right arm (raised up waving!)
        val waveAdjust = if (isWaving) waveOffset.dp.toPx() else 0f
        val handX = cx + w * 0.48f + waveAdjust * 0.5f
        val handY = h * 0.48f - waveAdjust

        val rightArmPath = Path().apply {
            moveTo(cx + w * 0.34f, h * 0.64f)
            quadraticTo(cx + w * 0.42f, h * 0.57f, handX, handY)
        }
        drawPath(
            path = rightArmPath,
            color = legColor,
            style = Stroke(width = armStroke, cap = StrokeCap.Round)
        )
        // Right hand (open waving palm with fingers)
        drawCircle(
            color = legColor,
            radius = 4.8.dp.toPx(),
            center = Offset(handX, handY)
        )
        // Little finger nubs for cute waving hand
        drawCircle(
            color = legColor,
            radius = 2.2.dp.toPx(),
            center = Offset(handX - 2.5.dp.toPx(), handY - 4.5.dp.toPx())
        )
        drawCircle(
            color = legColor,
            radius = 2.4.dp.toPx(),
            center = Offset(handX + 1.5.dp.toPx(), handY - 5.dp.toPx())
        )
        drawCircle(
            color = legColor,
            radius = 2.2.dp.toPx(),
            center = Offset(handX + 5.dp.toPx(), handY - 3.dp.toPx())
        )

        // --- Droplet Body Shape ---
        val dropletPath = Path().apply {
            val topTipY = h * 0.12f
            val bottomY = h * 0.92f

            moveTo(cx, topTipY)
            // Right shoulder curve down to right plump belly
            cubicTo(
                cx + w * 0.18f, h * 0.32f,
                cx + w * 0.43f, h * 0.56f,
                cx + w * 0.40f, h * 0.78f
            )
            // Right bottom curve towards center
            cubicTo(
                cx + w * 0.38f, bottomY + 2.dp.toPx(),
                cx + w * 0.20f, bottomY,
                cx, bottomY
            )
            // Left bottom curve
            cubicTo(
                cx - w * 0.20f, bottomY,
                cx - w * 0.38f, bottomY + 2.dp.toPx(),
                cx - w * 0.40f, h * 0.78f
            )
            // Left shoulder curve back up to tip
            cubicTo(
                cx - w * 0.43f, h * 0.56f,
                cx - w * 0.18f, h * 0.32f,
                cx, topTipY
            )
            close()
        }

        val dropletBrush = Brush.verticalGradient(
            colors = listOf(ElingoColors.PurpleGradientStart, ElingoColors.PurpleGradientEnd),
            startY = h * 0.10f,
            endY = h * 0.92f
        )
        drawPath(path = dropletPath, brush = dropletBrush)

        // Subtle specular highlight on droplet top-left
        val highlightPath = Path().apply {
            moveTo(cx - w * 0.05f, h * 0.22f)
            quadraticTo(cx - w * 0.18f, h * 0.42f, cx - w * 0.26f, h * 0.60f)
        }
        drawPath(
            path = highlightPath,
            color = Color.White.copy(alpha = 0.22f),
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // --- Face Features ---
        val eyeRadius = w * 0.088f
        val eyeY = h * 0.58f
        val leftEyeX = cx - w * 0.15f
        val rightEyeX = cx + w * 0.15f

        // Eyebrows
        val browStroke = 2.2.dp.toPx()
        val leftBrow = Path().apply {
            moveTo(leftEyeX - eyeRadius * 0.8f, eyeY - eyeRadius * 1.35f)
            quadraticTo(leftEyeX, eyeY - eyeRadius * 1.6f, leftEyeX + eyeRadius * 0.8f, eyeY - eyeRadius * 1.35f)
        }
        drawPath(path = leftBrow, color = ElingoColors.EyebrowPurple, style = Stroke(browStroke, cap = StrokeCap.Round))

        val rightBrow = Path().apply {
            moveTo(rightEyeX - eyeRadius * 0.8f, eyeY - eyeRadius * 1.35f)
            quadraticTo(rightEyeX, eyeY - eyeRadius * 1.6f, rightEyeX + eyeRadius * 0.8f, eyeY - eyeRadius * 1.35f)
        }
        drawPath(path = rightBrow, color = ElingoColors.EyebrowPurple, style = Stroke(browStroke, cap = StrokeCap.Round))

        // Eyes (White)
        drawCircle(color = Color.White, radius = eyeRadius, center = Offset(leftEyeX, eyeY))
        drawCircle(color = Color.White, radius = eyeRadius, center = Offset(rightEyeX, eyeY))

        // Pupils
        val pupilRadius = eyeRadius * 0.58f
        drawCircle(color = ElingoColors.MascotNavy, radius = pupilRadius, center = Offset(leftEyeX, eyeY))
        drawCircle(color = ElingoColors.MascotNavy, radius = pupilRadius, center = Offset(rightEyeX, eyeY))

        // Highlights in eyes
        val h1 = pupilRadius * 0.42f
        val h2 = pupilRadius * 0.22f
        drawCircle(color = Color.White, radius = h1, center = Offset(leftEyeX + pupilRadius * 0.30f, eyeY - pupilRadius * 0.30f))
        drawCircle(color = Color.White, radius = h2, center = Offset(leftEyeX - pupilRadius * 0.35f, eyeY + pupilRadius * 0.30f))

        drawCircle(color = Color.White, radius = h1, center = Offset(rightEyeX + pupilRadius * 0.30f, eyeY - pupilRadius * 0.30f))
        drawCircle(color = Color.White, radius = h2, center = Offset(rightEyeX - pupilRadius * 0.35f, eyeY + pupilRadius * 0.30f))

        // Cheeks (Blush)
        val cheekRadius = w * 0.075f
        val cheekY = h * 0.68f
        drawCircle(color = ElingoColors.CheekPink.copy(alpha = 0.85f), radius = cheekRadius, center = Offset(cx - w * 0.28f, cheekY))
        drawCircle(color = ElingoColors.CheekPink.copy(alpha = 0.85f), radius = cheekRadius, center = Offset(cx + w * 0.28f, cheekY))

        // Open Smiling Mouth with pink tongue
        val mouthWidth = w * 0.17f
        val mouthTopY = h * 0.67f
        val mouthBottomY = h * 0.74f

        val mouthPath = Path().apply {
            moveTo(cx - mouthWidth / 2f, mouthTopY)
            lineTo(cx + mouthWidth / 2f, mouthTopY)
            quadraticTo(cx, mouthBottomY + 2.dp.toPx(), cx - mouthWidth / 2f, mouthTopY)
            close()
        }
        drawPath(path = mouthPath, color = ElingoColors.MouthDark)

        // Tongue
        val tonguePath = Path().apply {
            moveTo(cx - mouthWidth * 0.32f, mouthBottomY - 1.dp.toPx())
            quadraticTo(cx, mouthBottomY - 5.dp.toPx(), cx + mouthWidth * 0.32f, mouthBottomY - 1.dp.toPx())
            quadraticTo(cx, mouthBottomY + 2.dp.toPx(), cx - mouthWidth * 0.32f, mouthBottomY - 1.dp.toPx())
            close()
        }
        drawPath(path = tonguePath, color = ElingoColors.TonguePink)
    }
}

/**
 * Screen 3 & 4 Mini Mascot: Compact ~65dp version of El waving towards the speech bubble.
 */
@Composable
fun ElingoMiniMascot(
    modifier: Modifier = Modifier.size(width = 68.dp, height = 76.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "miniWave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "miniArmWave"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // Stick feet
        val legStroke = 2.2.dp.toPx()
        val legColor = ElingoColors.MascotNavy
        val legTopY = h * 0.88f
        val legBottomY = h * 0.98f

        drawLine(color = legColor, start = Offset(cx - w * 0.10f, legTopY), end = Offset(cx - w * 0.11f, legBottomY), strokeWidth = legStroke, cap = StrokeCap.Round)
        drawRoundRect(color = legColor, topLeft = Offset(cx - w * 0.16f, legBottomY - 2.dp.toPx()), size = Size(10.dp.toPx(), 4.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))

        drawLine(color = legColor, start = Offset(cx + w * 0.10f, legTopY), end = Offset(cx + w * 0.11f, legBottomY), strokeWidth = legStroke, cap = StrokeCap.Round)
        drawRoundRect(color = legColor, topLeft = Offset(cx + w * 0.08f, legBottomY - 2.dp.toPx()), size = Size(10.dp.toPx(), 4.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))

        // Left arm
        val armStroke = 2.2.dp.toPx()
        drawLine(color = legColor, start = Offset(cx - w * 0.35f, h * 0.65f), end = Offset(cx - w * 0.44f, h * 0.74f), strokeWidth = armStroke, cap = StrokeCap.Round)
        drawCircle(color = legColor, radius = 2.5.dp.toPx(), center = Offset(cx - w * 0.44f, h * 0.74f))

        // Right waving arm
        val waveAdjust = waveOffset.dp.toPx()
        val handX = cx + w * 0.46f + waveAdjust * 0.5f
        val handY = h * 0.48f - waveAdjust
        drawLine(color = legColor, start = Offset(cx + w * 0.34f, h * 0.64f), end = Offset(handX, handY), strokeWidth = armStroke, cap = StrokeCap.Round)
        drawCircle(color = legColor, radius = 3.dp.toPx(), center = Offset(handX, handY))

        // Droplet body
        val dropletPath = Path().apply {
            val topTipY = h * 0.12f
            val bottomY = h * 0.90f
            moveTo(cx, topTipY)
            cubicTo(cx + w * 0.18f, h * 0.32f, cx + w * 0.43f, h * 0.56f, cx + w * 0.40f, h * 0.78f)
            cubicTo(cx + w * 0.38f, bottomY, cx + w * 0.20f, bottomY, cx, bottomY)
            cubicTo(cx - w * 0.20f, bottomY, cx - w * 0.38f, bottomY, cx - w * 0.40f, h * 0.78f)
            cubicTo(cx - w * 0.43f, h * 0.56f, cx - w * 0.18f, h * 0.32f, cx, topTipY)
            close()
        }
        val dropletBrush = Brush.verticalGradient(
            colors = listOf(ElingoColors.PurpleGradientStart, ElingoColors.PurpleGradientEnd),
            startY = h * 0.10f,
            endY = h * 0.90f
        )
        drawPath(path = dropletPath, brush = dropletBrush)

        // Eyes
        val eyeRadius = w * 0.088f
        val eyeY = h * 0.58f
        val leftEyeX = cx - w * 0.15f
        val rightEyeX = cx + w * 0.15f

        drawCircle(color = Color.White, radius = eyeRadius, center = Offset(leftEyeX, eyeY))
        drawCircle(color = Color.White, radius = eyeRadius, center = Offset(rightEyeX, eyeY))

        val pupilRadius = eyeRadius * 0.58f
        drawCircle(color = ElingoColors.MascotNavy, radius = pupilRadius, center = Offset(leftEyeX, eyeY))
        drawCircle(color = ElingoColors.MascotNavy, radius = pupilRadius, center = Offset(rightEyeX, eyeY))

        drawCircle(color = Color.White, radius = pupilRadius * 0.4f, center = Offset(leftEyeX + pupilRadius * 0.3f, eyeY - pupilRadius * 0.3f))
        drawCircle(color = Color.White, radius = pupilRadius * 0.4f, center = Offset(rightEyeX + pupilRadius * 0.3f, eyeY - pupilRadius * 0.3f))

        // Cheeks
        drawCircle(color = ElingoColors.CheekPink.copy(alpha = 0.85f), radius = w * 0.07f, center = Offset(cx - w * 0.27f, h * 0.68f))
        drawCircle(color = ElingoColors.CheekPink.copy(alpha = 0.85f), radius = w * 0.07f, center = Offset(cx + w * 0.27f, h * 0.68f))

        // Mouth
        val mouthPath = Path().apply {
            moveTo(cx - w * 0.07f, h * 0.68f)
            quadraticTo(cx, h * 0.74f, cx + w * 0.07f, h * 0.68f)
        }
        drawPath(path = mouthPath, color = ElingoColors.MouthDark, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * Screen 1: Rotating 8-dot circular loading spinner.
 */
@Composable
fun CircularDotsSpinner(
    modifier: Modifier = Modifier.size(36.dp),
    color: Color = ElingoColors.Purple
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dotsSpinner")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier) {
        val count = 8
        val center = Offset(size.width / 2f, size.height / 2f)
        val orbitRadius = size.width * 0.38f
        val dotRadius = size.width * 0.07f

        for (i in 0 until count) {
            val angleDeg = (i * (360f / count)) + rotationAngle
            val angleRad = (angleDeg * PI / 180.0).toFloat()
            val x = center.x + orbitRadius * cos(angleRad)
            val y = center.y + orbitRadius * sin(angleRad)
            val alpha = ((i + 1) / count.toFloat()).coerceIn(0.18f, 1f)

            drawCircle(
                color = color.copy(alpha = alpha),
                radius = dotRadius,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Pixel-perfect crisp Flag Badge (Rounded card with accurate national flag vector art).
 */
@Composable
fun FlagBadge(
    code: String,
    modifier: Modifier = Modifier.size(width = 34.dp, height = 24.dp)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .border(0.5.dp, Color(0x22000000), RoundedCornerShape(4.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            when (code.lowercase()) {
                "id", "indonesia" -> {
                    // Indonesia: Red top, White bottom
                    drawRect(color = ElingoColors.FlagRed, topLeft = Offset.Zero, size = Size(w, h / 2f))
                    drawRect(color = Color.White, topLeft = Offset(0f, h / 2f), size = Size(w, h / 2f))
                }
                "en", "us", "usa", "english" -> {
                    // US Flag: 13 stripes and blue canton
                    val stripeH = h / 13f
                    for (i in 0 until 13) {
                        val stripeColor = if (i % 2 == 0) ElingoColors.FlagUsRed else Color.White
                        drawRect(color = stripeColor, topLeft = Offset(0f, i * stripeH), size = Size(w, stripeH))
                    }
                    val cantonW = w * 0.42f
                    val cantonH = stripeH * 7f
                    drawRect(color = ElingoColors.FlagUsBlue, topLeft = Offset.Zero, size = Size(cantonW, cantonH))

                    // Star dots in blue canton
                    val cols = 3
                    val rows = 3
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            val sx = cantonW * (0.22f + c * 0.28f)
                            val sy = cantonH * (0.22f + r * 0.28f)
                            drawCircle(color = Color.White, radius = 0.9.dp.toPx(), center = Offset(sx, sy))
                        }
                    }
                }
                "zh", "cn", "mandarin", "chinese" -> {
                    // China: Red with gold stars
                    drawRect(color = ElingoColors.FlagChinaRed)
                    // Big star
                    drawCircle(color = ElingoColors.FlagChinaGold, radius = 3.2.dp.toPx(), center = Offset(w * 0.22f, h * 0.35f))
                    // Small stars arc
                    drawCircle(color = ElingoColors.FlagChinaGold, radius = 1.1.dp.toPx(), center = Offset(w * 0.38f, h * 0.18f))
                    drawCircle(color = ElingoColors.FlagChinaGold, radius = 1.1.dp.toPx(), center = Offset(w * 0.44f, h * 0.28f))
                    drawCircle(color = ElingoColors.FlagChinaGold, radius = 1.1.dp.toPx(), center = Offset(w * 0.44f, h * 0.42f))
                    drawCircle(color = ElingoColors.FlagChinaGold, radius = 1.1.dp.toPx(), center = Offset(w * 0.38f, h * 0.52f))
                }
                "es", "spanish" -> {
                    // Spain: Red-Yellow-Red
                    val redH = h * 0.25f
                    val yellowH = h * 0.50f
                    drawRect(color = ElingoColors.FlagRed, topLeft = Offset.Zero, size = Size(w, redH))
                    drawRect(color = ElingoColors.FlagYellow, topLeft = Offset(0f, redH), size = Size(w, yellowH))
                    drawRect(color = ElingoColors.FlagRed, topLeft = Offset(0f, redH + yellowH), size = Size(w, redH))

                    // Coat of arms crest badge
                    drawRoundRect(
                        color = ElingoColors.FlagRed,
                        topLeft = Offset(w * 0.22f, redH + yellowH * 0.25f),
                        size = Size(w * 0.14f, yellowH * 0.5f),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
                "hi", "in", "hindi", "indian" -> {
                    // India: Saffron, White, Green
                    val bandH = h / 3f
                    drawRect(color = ElingoColors.FlagOrange, topLeft = Offset.Zero, size = Size(w, bandH))
                    drawRect(color = Color.White, topLeft = Offset(0f, bandH), size = Size(w, bandH))
                    drawRect(color = ElingoColors.FlagGreen, topLeft = Offset(0f, bandH * 2f), size = Size(w, bandH))

                    // Ashoka Chakra (navy blue circle)
                    val chakraCenter = Offset(w / 2f, h / 2f)
                    drawCircle(color = Color(0xFF000080), radius = bandH * 0.38f, center = chakraCenter, style = Stroke(width = 1.dp.toPx()))
                    drawCircle(color = Color(0xFF000080), radius = 1.dp.toPx(), center = chakraCenter)
                }
                "fr", "french" -> {
                    // France: Blue, White, Red vertical
                    val bandW = w / 3f
                    drawRect(color = ElingoColors.FlagBlue, topLeft = Offset.Zero, size = Size(bandW, h))
                    drawRect(color = Color.White, topLeft = Offset(bandW, 0f), size = Size(bandW, h))
                    drawRect(color = ElingoColors.FlagRed, topLeft = Offset(bandW * 2f, 0f), size = Size(bandW, h))
                }
                "ru", "russian" -> {
                    // Russia: White, Blue, Red horizontal
                    val bandH = h / 3f
                    drawRect(color = Color.White, topLeft = Offset.Zero, size = Size(w, bandH))
                    drawRect(color = Color(0xFF0039A6), topLeft = Offset(0f, bandH), size = Size(w, bandH))
                    drawRect(color = Color(0xFFD52B1E), topLeft = Offset(0f, bandH * 2f), size = Size(w, bandH))
                }
                "uz", "uzbek" -> {
                    // Uzbekistan: Cyan, White, Green
                    val bandH = h / 3f
                    drawRect(color = ElingoColors.FlagUzbekCyan, topLeft = Offset.Zero, size = Size(w, bandH))
                    drawRect(color = Color.White, topLeft = Offset(0f, bandH), size = Size(w, bandH))
                    drawRect(color = ElingoColors.FlagUzbekGreen, topLeft = Offset(0f, bandH * 2f), size = Size(w, bandH))

                    // Thin red pinstripes
                    val pinH = 0.7.dp.toPx()
                    drawRect(color = Color(0xFFD52B1E), topLeft = Offset(0f, bandH - pinH), size = Size(w, pinH))
                    drawRect(color = Color(0xFFD52B1E), topLeft = Offset(0f, bandH * 2f), size = Size(w, pinH))

                    // Crescent in cyan band
                    drawCircle(color = Color.White, radius = bandH * 0.32f, center = Offset(w * 0.22f, bandH * 0.5f))
                    drawCircle(color = ElingoColors.FlagUzbekCyan, radius = bandH * 0.26f, center = Offset(w * 0.25f, bandH * 0.5f))
                }
                "de", "german" -> {
                    // Germany: Black, Red, Gold
                    val bandH = h / 3f
                    drawRect(color = Color(0xFF1A1A1A), topLeft = Offset.Zero, size = Size(w, bandH))
                    drawRect(color = Color(0xFFDD0000), topLeft = Offset(0f, bandH), size = Size(w, bandH))
                    drawRect(color = Color(0xFFFFCE00), topLeft = Offset(0f, bandH * 2f), size = Size(w, bandH))
                }
                else -> {
                    drawRect(color = ElingoColors.PurpleLight)
                }
            }
        }
    }
}

/**
 * Capsule Progress Bar at top of Screen 3 & 4.
 */
@Composable
fun ElingoProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
        .width(180.dp)
        .height(8.dp)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(ElingoColors.PurpleTrack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(percent = 50))
                .background(ElingoColors.Purple)
        )
    }
}

/**
 * Selectable Language Option Card for Screen 3 & 4.
 */
@Composable
fun ElingoLanguageCard(
    flagCode: String,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) ElingoColors.Purple else ElingoColors.BorderLight
    val borderWidth = if (isSelected) 2.dp else 1.5.dp
    val bgColor = if (isSelected) Color(0xFFFCFBFF) else Color.White

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(borderWidth, borderColor),
        color = bgColor,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            FlagBadge(code = flagCode)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = ElingoColors.TextDark
            )
        }
    }
}

/**
 * "Your Native Language" Card for Screen 3.
 */
@Composable
fun ElingoNativeLanguageCard(
    flagCode: String,
    name: String,
    onChangeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, ElingoColors.Purple),
        color = Color.White,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            FlagBadge(code = flagCode)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ElingoColors.TextDark,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Change",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ElingoColors.Purple,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onChangeClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Standard Elingo Primary or Secondary Button.
 */
@Composable
fun ElingoButton(
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean = true,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isPrimary) ElingoColors.Purple else ElingoColors.PurpleLight
    val textColor = if (isPrimary) Color.White else ElingoColors.Purple

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (isPrimary) 0.5.sp else 0.sp
            )
        }
    }
}
