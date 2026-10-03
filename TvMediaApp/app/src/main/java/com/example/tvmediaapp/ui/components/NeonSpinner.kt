package com.example.tvmediaapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun NeonSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    message: String? = null,
    accentColor: Color = LocalAccentColor.current
) {
    val rotationAnim = remember { Animatable(0f) }
    val sweepAnim = remember { Animatable(50f) }

    LaunchedEffect(Unit) {
        launch {
            while (isActive) {
                rotationAnim.animateTo(
                    targetValue = rotationAnim.value + 360f,
                    animationSpec = tween(durationMillis = 1100, easing = LinearEasing)
                )
                rotationAnim.snapTo(rotationAnim.value % 360f)
            }
        }
        launch {
            while (isActive) {
                sweepAnim.animateTo(
                    targetValue = 260f,
                    animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
                )
                sweepAnim.animateTo(
                    targetValue = 50f,
                    animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
                )
            }
        }
    }

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
                        rotationZ = rotationAnim.value
                    }
            ) {
                val strokePx = strokeWidth.toPx()
                val glowStrokePx = strokePx * 2.2f
                val sweep = sweepAnim.value

                // Soft outer glowing halo
                drawArc(
                    color = accentColor.copy(alpha = 0.35f),
                    startAngle = 0f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = glowStrokePx, cap = StrokeCap.Round)
                )

                // Crisp vibrant neon stroke
                drawArc(
                    color = accentColor,
                    startAngle = 0f,
                    sweepAngle = sweep,
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
