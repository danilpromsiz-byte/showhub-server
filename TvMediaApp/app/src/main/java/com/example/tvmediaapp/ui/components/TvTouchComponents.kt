@file:OptIn(
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.example.tvmediaapp.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonBorder
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonGlow
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ButtonShape
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults

/**
 * Universal touch modifier that enables touchscreen tap & long-press on any TV Material3 component.
 * TV components (androidx.tv.material3) exclusively handle D-Pad KeyEvents (DPAD_CENTER / ENTER)
 * and have zero built-in pointer/touch listeners. This modifier bridges touch inputs on phones/tablets
 * seamlessly without interfering with TV D-Pad navigation.
 */
fun Modifier.tvTouch(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = this.pointerInput(enabled, onClick, onLongClick) {
    if (!enabled) return@pointerInput
    detectTapGestures(
        onPress = { offset ->
            val press = PressInteraction.Press(offset)
            interactionSource?.emit(press)
            val released = tryAwaitRelease()
            if (released) {
                interactionSource?.emit(PressInteraction.Release(press))
            } else {
                interactionSource?.emit(PressInteraction.Cancel(press))
            }
        },
        onTap = {
            onClick()
        },
        onLongPress = if (onLongClick != null) {
            { onLongClick() }
        } else null
    )
}

/**
 * Touch-enabled wrapper for [androidx.tv.material3.Button].
 * Ensures full click and touch support on mobile devices while preserving all TV focus styles.
 */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    scale: ButtonScale = ButtonDefaults.scale(),
    glow: ButtonGlow = ButtonDefaults.glow(),
    shape: ButtonShape = ButtonDefaults.shape(),
    colors: ButtonColors = ButtonDefaults.colors(),
    border: ButtonBorder = ButtonDefaults.border(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val currentInteraction = interactionSource ?: remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        scale = scale,
        glow = glow,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = currentInteraction,
        modifier = modifier.tvTouch(
            enabled = enabled,
            interactionSource = currentInteraction,
            onLongClick = onLongClick,
            onClick = onClick
        ),
        content = content
    )
}

/**
 * Touch-enabled wrapper for [androidx.tv.material3.OutlinedButton].
 */
@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    scale: ButtonScale = ButtonDefaults.scale(),
    glow: ButtonGlow = ButtonDefaults.glow(),
    shape: ButtonShape = ButtonDefaults.shape(),
    colors: ButtonColors = OutlinedButtonDefaults.colors(),
    border: ButtonBorder = OutlinedButtonDefaults.border(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val currentInteraction = interactionSource ?: remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        scale = scale,
        glow = glow,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = currentInteraction,
        modifier = modifier.tvTouch(
            enabled = enabled,
            interactionSource = currentInteraction,
            onLongClick = onLongClick,
            onClick = onClick
        ),
        content = content
    )
}

/**
 * Touch-enabled wrapper for [androidx.tv.material3.Card].
 */
@Composable
fun AppCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: CardShape = CardDefaults.shape(),
    colors: CardColors = CardDefaults.colors(),
    scale: CardScale = CardDefaults.scale(),
    border: CardBorder = CardDefaults.border(),
    glow: CardGlow = CardDefaults.glow(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val currentInteraction = interactionSource ?: remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = shape,
        colors = colors,
        scale = scale,
        border = border,
        glow = glow,
        interactionSource = currentInteraction,
        modifier = modifier.tvTouch(
            interactionSource = currentInteraction,
            onLongClick = onLongClick,
            onClick = onClick
        ),
        content = content
    )
}
