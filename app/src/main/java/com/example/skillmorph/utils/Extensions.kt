
package com.example.skillmorph.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.skillmorph.ui.theme.BorderColor
import com.example.skillmorph.ui.theme.TransparentBlack
import com.example.skillmorph.ui.theme.TransparentWhite
import com.example.skillmorph.ui.theme.gradientBrush

fun Modifier.glassEffect(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    borderColor: Color = BorderColor
) = this
    .clip(shape)
    .background(TransparentBlack)
    .border(borderWidth, borderColor, shape)

fun Modifier.glassEffectGradient(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    borderColor: Color = BorderColor
) = this
    .clip(shape)
    .background(gradientBrush())
    .border(borderWidth, borderColor, shape)
