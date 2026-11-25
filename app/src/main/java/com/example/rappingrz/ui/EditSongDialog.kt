package com.example.rappingrz.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Carga la portada desde un archivo sin bloquear la pantalla. */
@Composable
fun rememberCover(path: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path.isNullOrEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
        }
    }
    return bitmap
}

@Composable
fun EditSongDialog(song: Song, vm: MusicViewModel, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val neutral = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = scheme.onSurface,
        focusedLabelColor = scheme.onSurface,
        cursorColor = scheme.onSurface
    )

    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var album by remember(song.id) { mutableStateOf(song.album) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.setCover(song, uri) }

    val cover = rememberCover(song.coverPath)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar canción") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
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
                            Text("♪", color = scheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        TextButton(
                            onClick = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = neutral
                        ) { Text("Cambiar portada") }
                        if (song.coverPath != null) {
                            TextButton(onClick = { vm.removeCover(song) }, colors = neutral) {
                                Text("Quitar portada")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Autor") },
                    singleLine = true,
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Disco (opcional)") },
                    singleLine = true,
                    colors = fieldColors
                )

                TextButton(
                    onClick = {
                        vm.resetSong(song)
                        onDismiss()
                    },
                    colors = neutral
                ) { Text("Restaurar datos originales") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.saveDetails(song, title, artist, album)
                    onDismiss()
                },
                colors = neutral
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = neutral) { Text("Cancelar") }
        }
    )
}