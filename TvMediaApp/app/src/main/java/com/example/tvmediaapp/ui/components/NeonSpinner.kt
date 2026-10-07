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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
    val infiniteTransition = rememberInfiniteTransition(label = "NeonSpinnerTransition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val sweep by infiniteTransition.animateFloat(
        initialValue = 50f,
        targetValue = 260f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
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
            // 1. Static faint background circular track
            Canvas(modifier = Modifier.size(size)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    style = Stroke(width = strokeWidth.toPx())
                )
            }

            // 2. Hardware-accelerated rotating neon arc
            Canvas(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        rotationZ = rotation
                    }
            ) {
                val strokePx = strokeWidth.toPx()
                val glowStrokePx = strokePx * 2.2f
                val sweepAngle = sweep

                // Soft outer glowing halo
                drawArc(
                    color = accentColor.copy(alpha = 0.35f),
                    startAngle = 0f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = glowStrokePx, cap = StrokeCap.Round)
                )

                // Crisp vibrant neon stroke
                drawArc(
                    color = accentColor,
                    startAngle = 0f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
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
