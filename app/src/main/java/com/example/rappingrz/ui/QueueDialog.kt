package com.example.rappingrz.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Icono de cola: tres líneas y un pequeño triángulo de play. */
@Composable
fun QueueIcon(
    iconSize: Dp = 24.dp,
    color: Color = LocalContentColor.current
) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f
        drawLine(color, Offset(w * 0.12f, h * 0.25f), Offset(w * 0.88f, h * 0.25f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.12f, h * 0.45f), Offset(w * 0.88f, h * 0.45f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.12f, h * 0.65f), Offset(w * 0.48f, h * 0.65f), strokeWidth = stroke, cap = StrokeCap.Round)
        val triangle = Path().apply {
            moveTo(w * 0.58f, h * 0.56f)
            lineTo(w * 0.58f, h * 0.84f)
            lineTo(w * 0.88f, h * 0.70f)
            close()
        }
        drawPath(triangle, color)
    }
}

/** Pantalla con lo que suena ahora y lo que viene después. */
@Composable
fun QueueDialog(vm: MusicViewModel, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val neutral = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface)
    val current = vm.currentSong
    val queued = vm.upcoming.filter { it.queued }
    val rest = vm.upcoming.filter { !it.queued }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = scheme.surface) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Cola",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onDismiss, colors = neutral) { Text("Listo") }
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    item { SectionTitle("Sonando ahora") }
                    item {
                        if (current != null) {
                            QueueRow(song = current, isCurrent = true, onClick = null, onRemove = null)
                        } else {
                            Text(
                                "No hay nada sonando",
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }

                    if (queued.isNotEmpty()) {
                        item { SectionTitle("En cola") }
                        items(queued, key = { "q${it.index}" }) { entry ->
                            QueueRow(
                                song = entry.song,
                                isCurrent = false,
                                onClick = { vm.playFromQueue(entry.index) },
                                onRemove = { vm.removeFromQueue(entry.index) }
                            )
                        }
                    }

                    item { SectionTitle("A continuación") }
                    if (rest.isEmpty()) {
                        item {
                            Text(
                                "No hay más canciones",
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = scheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(rest, key = { "r${it.index}" }) { entry ->
                            QueueRow(
                                song = entry.song,
                                isCurrent = false,
                                onClick = { vm.playFromQueue(entry.index) },
                                onRemove = { vm.removeFromQueue(entry.index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun QueueRow(
    song: Song,
    isCurrent: Boolean,
    onClick: (() -> Unit)?,
    onRemove: (() -> Unit)?
) {
    val scheme = MaterialTheme.colorScheme
    ListItem(
        headlineContent = {
            Text(
                song.title,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { QueueCover(song.coverPath, 48.dp) },
        trailingContent = if (onRemove != null) {
            {
                IconButton(onClick = onRemove) {
                    CloseIcon(iconSize = 16.dp, color = scheme.onSurfaceVariant)
                }
            }
        } else null,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

@Composable
private fun QueueCover(path: String?, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    val thumb = rememberQueueThumb(path)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("♪", color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun rememberQueueThumb(path: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path.isNullOrEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 200 || bounds.outHeight / sample > 200) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            }
        }
    }
    return bitmap
}