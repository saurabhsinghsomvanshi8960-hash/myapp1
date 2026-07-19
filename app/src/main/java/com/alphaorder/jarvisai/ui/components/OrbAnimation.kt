package com.alphaorder.jarvisai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.alphaorder.jarvisai.ui.theme.JarvisCyan
import com.alphaorder.jarvisai.ui.theme.JarvisCyanDim
import com.alphaorder.jarvisai.viewmodel.JarvisState
import kotlin.math.cos
import kotlin.math.sin

/**
 * The central animated orb. Visual behaviour changes per [state]:
 * - IDLE: slow breathing pulse
 * - LISTENING: ripple rings that react to [micLevel]
 * - THINKING: rotating particle ring
 * - SPEAKING: glow pulses with [micLevel] (drive this from TTS amplitude/heuristic)
 * - OFFLINE / ERROR: dim, desaturated
 */
@Composable
fun OrbAnimation(
    state: JarvisState,
    micLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    // Breathing scale for IDLE
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    // Rotation for THINKING particle ring
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Ripple expansion for LISTENING
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_progress"
    )

    val orbColor = when (state) {
        JarvisState.OFFLINE, JarvisState.ERROR -> Color.Gray
        else -> JarvisCyan
    }

    Canvas(modifier = modifier.size(220.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension / 4f

        when (state) {
            JarvisState.IDLE -> {
                drawGlowCircle(center, baseRadius * breathScale, orbColor, alpha = 0.9f)
            }

            JarvisState.LISTENING -> {
                drawGlowCircle(center, baseRadius * (0.9f + micLevel * 0.3f), orbColor, alpha = 1f)
                // ripple rings
                for (i in 0..2) {
                    val progress = (rippleProgress + i / 3f) % 1f
                    val radius = baseRadius + progress * baseRadius * 1.8f
                    val alpha = (1f - progress) * 0.5f
                    drawCircle(
                        color = orbColor.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            JarvisState.THINKING -> {
                drawGlowCircle(center, baseRadius * 0.85f, orbColor, alpha = 0.7f)
                // rotating particles
                val particleCount = 8
                for (i in 0 until particleCount) {
                    val angle = Math.toRadians((rotation + i * (360f / particleCount)).toDouble())
                    val px = center.x + (baseRadius * 1.5f) * cos(angle).toFloat()
                    val py = center.y + (baseRadius * 1.5f) * sin(angle).toFloat()
                    drawCircle(
                        color = orbColor.copy(alpha = 0.8f),
                        radius = 5.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }

            JarvisState.SPEAKING -> {
                drawGlowCircle(center, baseRadius * (0.95f + micLevel * 0.25f), orbColor, alpha = 1f)
                drawCircle(
                    color = orbColor.copy(alpha = 0.3f),
                    radius = baseRadius * (1.3f + micLevel * 0.4f),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            JarvisState.OFFLINE, JarvisState.ERROR -> {
                drawGlowCircle(center, baseRadius * 0.8f, orbColor, alpha = 0.5f)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlowCircle(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float
) {
    // Outer soft glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha * 0.5f), Color.Transparent),
            center = center,
            radius = radius * 2.2f
        ),
        radius = radius * 2.2f,
        center = center
    )
    // Core orb
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), JarvisCyanDim.copy(alpha = alpha * 0.6f)),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
