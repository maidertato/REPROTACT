package com.example.rappingrz.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
fun NowPlayingScreen(vm: MusicViewModel, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val neutral = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface)

    // Mientras arrastras la barra, se muestra el valor del dedo en vez de la posición real
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val duration = vm.durationMs.coerceAtLeast(1L)
    val progress = dragValue ?: (vm.positionMs.toFloat() / duration)
    val shownMs = dragValue?.let { (it * duration).toLong() } ?: vm.positionMs

    var menuOpen by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    val song = vm.currentSong
    val cover = rememberCover(song?.coverPath)
    val subtitle = listOfNotNull(
        song?.artist,
        song?.album?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
        Column(
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Barra superior: cerrar a la izquierda; cola y menú a la derecha
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { ChevronDownIcon(iconSize = 26.dp) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showQueue = true }) { QueueIcon(iconSize = 24.dp) }

                    Box {
                        IconButton(onClick = { menuOpen = true }) { MoreIcon(iconSize = 24.dp) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Añadir a lista") },
                                enabled = song != null,
                                onClick = {
                                    menuOpen = false
                                    showAdd = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Editar datos") },
                                enabled = song != null,
                                onClick = {
                                    menuOpen = false
                                    showEdit = true
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) {
                    Image(
                        bitmap = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        "♪",
                        style = MaterialTheme.typography.displayLarge,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                song?.title ?: "Nada reproduciéndose",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(16.dp))

            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { vm.seekTo((it * duration).toLong()) }
                    dragValue = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = scheme.onSurface,
                    activeTrackColor = scheme.onSurface,
                    inactiveTrackColor = scheme.outlineVariant
                )
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatTime(shownMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    formatTime(vm.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = vm::toggleShuffle,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = scheme.onSurface.copy(alpha = if (vm.shuffle) 1f else 0.4f)
                    )
                ) { Text("⇄", style = MaterialTheme.typography.titleLarge) }

                TextButton(onClick = vm::previous, colors = neutral) {
                    SkipIcon(forward = false, iconSize = 28.dp)
                }
                TextButton(onClick = vm::togglePlay, colors = neutral) {
                    PlayPauseIcon(vm.isPlaying, iconSize = 40.dp)
                }
                TextButton(onClick = vm::next, colors = neutral) {
                    SkipIcon(forward = true, iconSize = 28.dp)
                }

                TextButton(
                    onClick = vm::toggleRepeat,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = scheme.onSurface.copy(alpha = if (vm.repeatAll) 1f else 0.4f)
                    )
                ) { Text("↻", style = MaterialTheme.typography.titleLarge) }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showEdit && song != null) {
        EditSongDialog(song = song, vm = vm, onDismiss = { showEdit = false })
    }
    if (showAdd && song != null) {
        AddToPlaylistDialog(song = song, vm = vm, onDismiss = { showAdd = false })
    }
    if (showQueue) {
        QueueDialog(vm = vm, onDismiss = { showQueue = false })
    }
}