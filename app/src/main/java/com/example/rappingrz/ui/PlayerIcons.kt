package com.example.rappingrz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Icono de play / pausa dibujado a mano (sin emojis). */
@Composable
fun PlayPauseIcon(
    isPlaying: Boolean,
    iconSize: Dp = 24.dp,
    color: Color = LocalContentColor.current
) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        if (isPlaying) {
            val barW = w * 0.26f
            val gap = w * 0.16f
            val left = (w - 2 * barW - gap) / 2
            val corner = CornerRadius(barW / 4)
            drawRoundRect(color, Offset(left, h * 0.05f), Size(barW, h * 0.9f), corner)
            drawRoundRect(color, Offset(left + barW + gap, h * 0.05f), Size(barW, h * 0.9f), corner)
        } else {
            val path = Path().apply {
                moveTo(w * 0.22f, h * 0.05f)
                lineTo(w * 0.22f, h * 0.95f)
                lineTo(w * 0.92f, h * 0.5f)
                close()
            }
            drawPath(path, color)
        }
    }
}

/** Icono de siguiente (forward = true) o anterior (forward = false). */
@Composable
fun SkipIcon(
    forward: Boolean,
    iconSize: Dp = 24.dp,
    color: Color = LocalContentColor.current
) {
    Canvas(Modifier.size(iconSize)) {
        if (forward) {
            drawSkipNext(color)
        } else {
            scale(scaleX = -1f, scaleY = 1f, pivot = center) { drawSkipNext(color) }
        }
    }
}

private fun DrawScope.drawSkipNext(color: Color) {
    val w = size.width
    val h = size.height
    val triangle = Path().apply {
        moveTo(w * 0.08f, h * 0.1f)
        lineTo(w * 0.08f, h * 0.9f)
        lineTo(w * 0.70f, h * 0.5f)
        close()
    }
    drawPath(triangle, color)
    drawRoundRect(
        color,
        Offset(w * 0.78f, h * 0.1f),
        Size(w * 0.14f, h * 0.8f),
        CornerRadius(w * 0.04f)
    )
}

/** Flecha hacia abajo (cerrar la ventana de la canción). */
@Composable
fun ChevronDownIcon(
    iconSize: Dp = 24.dp,
    color: Color = LocalContentColor.current
) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.38f)
            lineTo(w * 0.5f, h * 0.66f)
            lineTo(w * 0.8f, h * 0.38f)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** Tres puntos verticales (menú). */
@Composable
fun MoreIcon(
    iconSize: Dp = 24.dp,
    color: Color = LocalContentColor.current
) {
    Canvas(Modifier.size(iconSize)) {
        val radius = size.width * 0.075f
        val cx = size.width / 2
        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            drawCircle(color, radius, Offset(cx, size.height * fraction))
        }
    }
}
