package com.example.rappingrz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Lupa dibujada a mano. */
@Composable
fun SearchIcon(
    iconSize: Dp = 24.dp,
    color: Color = LocalContentColor.current
) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val stroke = w * 0.1f
        drawCircle(
            color = color,
            radius = w * 0.28f,
            center = Offset(w * 0.43f, w * 0.43f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.65f, w * 0.65f),
            end = Offset(w * 0.88f, w * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** Cruz para borrar el texto de búsqueda. */
@Composable
fun CloseIcon(
    iconSize: Dp = 24.dp,
    color: Color = LocalContentColor.current
) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val stroke = w * 0.11f
        drawLine(
            color = color,
            start = Offset(w * 0.22f, w * 0.22f),
            end = Offset(w * 0.78f, w * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.78f, w * 0.22f),
            end = Offset(w * 0.22f, w * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** Símbolo + (nueva lista). */
@Composable
fun PlusIcon(
    iconSize: Dp = 24.dp,
    color: Color = LocalContentColor.current
) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val stroke = w * 0.1f
        drawLine(
            color = color,
            start = Offset(w * 0.5f, w * 0.18f),
            end = Offset(w * 0.5f, w * 0.82f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(w * 0.18f, w * 0.5f),
            end = Offset(w * 0.82f, w * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}