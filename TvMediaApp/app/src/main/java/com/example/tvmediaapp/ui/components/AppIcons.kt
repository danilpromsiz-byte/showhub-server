package com.example.tvmediaapp.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor

@Composable
fun AppIcon(
    @DrawableRes resId: Int = 0,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 16.dp,
    @DrawableRes iconResId: Int = resId
) {
    val actualResId = if (iconResId != 0) iconResId else resId
    Image(
        painter = painterResource(id = actualResId),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size)
    )
}
