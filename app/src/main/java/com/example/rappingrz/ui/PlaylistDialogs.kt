package com.example.rappingrz.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Pide un nombre (para crear o renombrar una lista). */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val neutral = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = scheme.onSurface,
        focusedLabelColor = scheme.onSurface,
        cursorColor = scheme.onSurface
    )
    var name by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                colors = fieldColors
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                colors = neutral
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = neutral) { Text("Cancelar") }
        }
    )
}

/** Elegir en qué lista se añade una canción (o crear una nueva). */
@Composable
fun AddToPlaylistDialog(song: Song, vm: MusicViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val neutral = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface)
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        NameDialog(
            title = "Nueva lista",
            initial = "",
            confirmLabel = "Crear",
            onConfirm = { name ->
                val id = vm.createPlaylist(name)
                vm.addToPlaylist(id, song.id)
                Toast.makeText(context, "Añadida a ${name.ifBlank { "Nueva lista" }}", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            onDismiss = { creating = false }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Añadir a lista") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DialogRow(text = "Nueva lista", showPlus = true) { creating = true }
                    vm.playlists.forEach { playlist ->
                        val already = song.id in playlist.songIds
                        DialogRow(
                            text = playlist.name,
                            trailing = if (already) "✓" else playlist.songIds.size.toString()
                        ) {
                            val added = vm.addToPlaylist(playlist.id, song.id)
                            val message = if (added) "Añadida a ${playlist.name}" else "Ya estaba en ${playlist.name}"
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss, colors = neutral) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun DialogRow(
    text: String,
    trailing: String = "",
    showPlus: Boolean = false,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showPlus) {
            PlusIcon(iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing.isNotEmpty()) {
            Text(trailing, color = scheme.onSurfaceVariant)
        }
    }
}