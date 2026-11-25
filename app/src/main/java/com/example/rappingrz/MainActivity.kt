package com.example.rappingrz

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rappingrz.ui.LibraryScreen
import com.example.rappingrz.ui.MusicViewModel
import com.example.rappingrz.ui.NowPlayingScreen
import com.example.rappingrz.ui.PlayPauseIcon
import com.example.rappingrz.ui.SkipIcon
import com.example.rappingrz.ui.theme.RappinGRZTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RappinGRZTheme {
                MusicScreen()
            }
        }
    }
}

@Composable
fun MusicScreen(vm: MusicViewModel = viewModel()) {
    val audioPermission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    // En Android 13+ también se pide el permiso de notificaciones (para los controles)
    val permissions = buildList {
        add(audioPermission)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[audioPermission] == true) vm.loadSongs()
    }

    LaunchedEffect(Unit) { launcher.launch(permissions) }

    var showPlayer by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showPlayer) { showPlayer = false }

    Box {
        Scaffold(bottomBar = { PlayerBar(vm, onOpen = { showPlayer = true }) }) { padding ->
            LibraryScreen(
                vm = vm,
                playerOpen = showPlayer,
                modifier = Modifier.padding(padding)
            )
        }

        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            NowPlayingScreen(vm, onClose = { showPlayer = false })
        }
    }
}

@Composable
fun PlayerBar(vm: MusicViewModel, onOpen: () -> Unit) {
    val neutral = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Al tocar el título se abre la ventana de la canción
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(
                    vm.currentSong?.title ?: "Nada reproduciéndose",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                vm.currentSong?.let {
                    Text(
                        it.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(onClick = vm::previous, colors = neutral) {
                SkipIcon(forward = false, iconSize = 18.dp)
            }
            TextButton(onClick = vm::togglePlay, colors = neutral) {
                PlayPauseIcon(vm.isPlaying, iconSize = 20.dp)
            }
            TextButton(onClick = vm::next, colors = neutral) {
                SkipIcon(forward = true, iconSize = 18.dp)
            }
        }
    }
}