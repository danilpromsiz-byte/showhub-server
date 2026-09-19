package com.example.tvmediaapp.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.tvmediaapp.ui.theme.LocalAccentColor
import com.example.tvmediaapp.ui.theme.TextGray

@Composable
fun NeonSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    message: String? = null,
    accentColor: Color = LocalAccentColor.current
) {
    val transition = rememberInfiniteTransition(label = "NeonSpinnerTransition")

    // Smooth continuous 360-degree rotation
    val baseRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "baseRotation"
    )

    // Breathing arc sweep for organic, silky flow
    val sweepAngle by transition.animateFloat(
        initialValue = 40f,
        targetValue = 240f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweepAngle"
    )

    // Soft, pulsing ambient glow
    val glowAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val strokePx = strokeWidth.toPx()
                val glowStrokePx = strokePx * 2.0f

                // 1. Faint circular background track
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    style = Stroke(width = strokePx)
                )

                // Rotate the canvas so the gradient is always strictly aligned with the arc
                rotate(degrees = baseRotation) {
                    // 2. Soft outer glow arc
                    drawArc(
                        color = accentColor.copy(alpha = glowAlpha),
                        startAngle = 0f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = glowStrokePx, cap = StrokeCap.Round)
                    )

                    // 3. Crisp inner neon arc with ultra-smooth gradient
                    val gradientBrush = Brush.sweepGradient(
                        0.0f to accentColor.copy(alpha = 0.05f),
                        0.4f to accentColor.copy(alpha = 0.60f),
                        0.85f to accentColor,
                        1.0f to accentColor
                    )

                    drawArc(
                        brush = gradientBrush,
                        startAngle = 0f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
        }

        if (!message.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextGray
            )
        }
    }
}
