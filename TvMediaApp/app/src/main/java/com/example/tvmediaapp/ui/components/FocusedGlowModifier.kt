package com.example.tvmediaapp.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders a soft external glow/blur outside the bounds of a focused element.
 * Provides high-end TV UI elevation and active state prominence.
 */
fun Modifier.focusedGlow(
    isFocused: Boolean,
    color: Color = Color(0xFF60A5FA),
    radius: Dp = 10.dp,
    shapeRadius: Dp = 8.dp,
    alpha: Float = 0.45f
): Modifier {
    if (!isFocused) return this

    return this.drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().asFrameworkPaint().apply {
                this.isAntiAlias = true
                this.color = color.copy(alpha = alpha).toArgb()
                this.style = android.graphics.Paint.Style.FILL
                this.maskFilter = BlurMaskFilter(radius.toPx(), BlurMaskFilter.Blur.OUTER)
            }
            canvas.nativeCanvas.drawRoundRect(
                0f,
                0f,
                size.width,
                size.height,
                shapeRadius.toPx(),
                shapeRadius.toPx(),
                paint
            )
        }
    }
}
