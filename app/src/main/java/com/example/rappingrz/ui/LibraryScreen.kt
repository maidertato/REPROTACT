package com.example.rappingrz.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.Normalizer
import kotlin.math.roundToInt

private val TABS = listOf("Pistas", "Álbumes", "Listas", "Artistas")
private const val TAB_ALBUMS = 1
private const val TAB_PLAYLISTS = 2
private const val TAB_ARTISTS = 3
private const val NO_DETAIL = -1

private val DIACRITICS = Regex("\\p{M}+")

private data class SongGroup(val name: String, val songs: List<Song>)

/** Texto sin tildes y en minúsculas, para ordenar y buscar la letra inicial. */
private fun sortKey(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()

private fun sortedByTitle(songs: List<Song>): List<Song> =
    songs.map { it to sortKey(it.title) }.sortedBy { it.second }.map { it.first }

private fun letterOf(name: String): String {
    val first = sortKey(name).firstOrNull() ?: return "#"
    return if (first in 'a'..'z') first.uppercaseChar().toString() else "#"
}

/** Letras presentes en la lista, cada una con la posición de su primer elemento. */
private fun indexLetters(names: List<String>): List<Pair<String, Int>> {
    val result = LinkedHashMap<String, Int>()
    names.forEachIndexed { index, name -> result.putIfAbsent(letterOf(name), index) }
    return result.toList()
}

private fun buildGroups(
    songs: List<Song>,
    key: (Song) -> String,
    emptyName: String
): List<SongGroup> =
    songs.groupBy { key(it).ifBlank { emptyName } }
        .map { (name, list) -> SongGroup(name, list) }
        .sortedWith(compareBy<SongGroup> { it.name == emptyName }.thenBy { sortKey(it.name) })

private fun countLabel(n: Int) = if (n == 1) "1 canción" else "$n canciones"

private fun matches(song: Song, query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() ||
            song.title.contains(q, ignoreCase = true) ||
            song.artist.contains(q, ignoreCase = true) ||
            song.album.contains(q, ignoreCase = true)
}

@Composable
fun LibraryScreen(
    vm: MusicViewModel,
    playerOpen: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val neutral = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface)

    var query by rememberSaveable { mutableStateOf("") }
    var detailKind by rememberSaveable { mutableStateOf(NO_DETAIL) }
    var detailName by rememberSaveable { mutableStateOf("") } // nombre, o id si es una lista

    var songToAdd by remember { mutableStateOf<Song?>(null) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var showAddSongs by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var headerMenu by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState { TABS.size }
    val scope = rememberCoroutineScope()

    val byId = remember(vm.songs) { vm.songs.associateBy { it.id } }
    val filtered = remember(vm.songs, query) {
        sortedByTitle(vm.songs.filter { matches(it, query) })
    }
    val albums = remember(filtered) { buildGroups(filtered, { it.album }, "Sin disco") }
    val artists = remember(filtered) { buildGroups(filtered, { it.artist }, "") }
    val filteredPlaylists = vm.playlists.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)
    }

    // Con el reproductor abierto, el botón atrás cierra primero el reproductor
    BackHandler(enabled = detailKind != NO_DETAIL && !playerOpen) { detailKind = NO_DETAIL }

    Column(modifier.fillMaxSize().padding(top = 12.dp)) {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (detailKind == NO_DETAIL) {
            LibraryTabs(
                selected = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } }
            )

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> SongList(
                        songs = filtered,
                        vm = vm,
                        emptyText = if (vm.songs.isEmpty()) "No hay canciones" else "Sin resultados",
                        onAdd = { songToAdd = it },
                        showIndex = true
                    )
                    TAB_ALBUMS -> GroupList(albums, vm.songs.isEmpty()) {
                        detailKind = TAB_ALBUMS
                        detailName = it.name
                    }
                    TAB_PLAYLISTS -> PlaylistList(
                        playlists = filteredPlaylists,
                        byId = byId,
                        onNew = { showNewPlaylist = true },
                        onOpen = {
                            detailKind = TAB_PLAYLISTS
                            detailName = it.id.toString()
                        }
                    )
                    else -> GroupList(artists, vm.songs.isEmpty()) {
                        detailKind = TAB_ARTISTS
                        detailName = it.name
                    }
                }
            }
        } else {
            val playlist = if (detailKind == TAB_PLAYLISTS) {
                vm.playlists.firstOrNull { it.id.toString() == detailName }
            } else null

            val detailSongs = when (detailKind) {
                TAB_PLAYLISTS -> playlist?.songIds.orEmpty()
                    .mapNotNull { byId[it] }
                    .filter { matches(it, query) }
                TAB_ALBUMS -> albums.firstOrNull { it.name == detailName }?.songs.orEmpty()
                else -> artists.firstOrNull { it.name == detailName }?.songs.orEmpty()
            }
            val title = if (detailKind == TAB_PLAYLISTS) playlist?.name.orEmpty() else detailName

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { detailKind = NO_DETAIL }) {
                    Box(Modifier.rotate(90f)) { ChevronDownIcon(iconSize = 24.dp) }
                }
                Text(
                    title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlist != null) {
                    // Botón + para añadir canciones a esta lista
                    IconButton(onClick = { showAddSongs = true }) { PlusIcon(iconSize = 22.dp) }
                    Box {
                        IconButton(onClick = { headerMenu = true }) { MoreIcon(iconSize = 24.dp) }
                        DropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Renombrar lista") },
                                onClick = {
                                    headerMenu = false
                                    renaming = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar lista") },
                                onClick = {
                                    headerMenu = false
                                    deleting = true
                                }
                            )
                        }
                    }
                }
            }

            if (playlist != null) {
                PlaylistSongList(
                    songs = detailSongs,
                    vm = vm,
                    emptyText = if (playlist.songIds.isEmpty())
                        "Esta lista está vacía.\nPulsa + para añadir canciones."
                    else "Sin resultados",
                    reorderEnabled = query.isBlank(),
                    onAdd = { songToAdd = it },
                    onRemove = { vm.removeFromPlaylist(playlist.id, it.id) },
                    onReorder = { vm.reorderPlaylist(playlist.id, it) }
                )
            } else {
                SongList(
                    songs = detailSongs,
                    vm = vm,
                    emptyText = "Sin resultados",
                    onAdd = { songToAdd = it }
                )
            }

            if (showAddSongs && playlist != null) {
                AddSongsDialog(playlist = playlist, vm = vm, onDismiss = { showAddSongs = false })
            }
            if (renaming && playlist != null) {
                NameDialog(
                    title = "Renombrar lista",
                    initial = playlist.name,
                    confirmLabel = "Guardar",
                    onConfirm = {
                        vm.renamePlaylist(playlist.id, it)
                        renaming = false
                    },
                    onDismiss = { renaming = false }
                )
            }
            if (deleting && playlist != null) {
                AlertDialog(
                    onDismissRequest = { deleting = false },
                    title = { Text("Eliminar lista") },
                    text = { Text("¿Eliminar «${playlist.name}»? Las canciones no se borran del teléfono.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                vm.deletePlaylist(playlist.id)
                                deleting = false
                                detailKind = NO_DETAIL
                            },
                            colors = neutral
                        ) { Text("Eliminar") }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleting = false }, colors = neutral) { Text("Cancelar") }
                    }
                )
            }
        }
    }

    songToAdd?.let { song ->
        AddToPlaylistDialog(song = song, vm = vm, onDismiss = { songToAdd = null })
    }

    if (showNewPlaylist) {
        NameDialog(
            title = "Nueva lista",
            initial = "",
            confirmLabel = "Crear",
            onConfirm = {
                vm.createPlaylist(it)
                showNewPlaylist = false
            },
            onDismiss = { showNewPlaylist = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Buscador y pestañas
// ---------------------------------------------------------------------------

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current

    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceVariant)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchIcon(iconSize = 20.dp, color = scheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Buscar",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                CloseIcon(iconSize = 16.dp, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LibraryTabs(selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            TABS.forEachIndexed { index, label ->
                val isSelected = index == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) }
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .height(2.dp)
                            .fillMaxWidth(0.5f)
                            .background(if (isSelected) scheme.onSurface else Color.Transparent)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
    }
}

// ---------------------------------------------------------------------------
// Listas de canciones
// ---------------------------------------------------------------------------

/** Lista normal de canciones. Con [showIndex] muestra la barra de letras a la derecha. */
@Composable
private fun SongList(
    songs: List<Song>,
    vm: MusicViewModel,
    emptyText: String,
    onAdd: (Song) -> Unit,
    showIndex: Boolean = false
) {
    if (songs.isEmpty()) {
        EmptyMessage(emptyText)
        return
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<String?>(null) }
    val letters = remember(songs, showIndex) {
        if (showIndex) indexLetters(songs.map { it.title }) else emptyList()
    }
    val hasIndex = letters.size >= 2

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(end = if (hasIndex) 20.dp else 0.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    vm = vm,
                    onClick = { vm.playQueue(songs, song) },
                    onAdd = onAdd,
                    onRemove = null
                )
            }
        }
        if (hasIndex) {
            AlphabetBar(
                letters = letters.map { it.first },
                onSelect = { i -> scope.launch { listState.scrollToItem(letters[i].second) } },
                onActive = { activeLetter = it },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
            activeLetter?.let { LetterBubble(it, Modifier.align(Alignment.Center)) }
        }
    }
}

/** Lista de una playlist: se puede reordenar con pulsación larga y arrastrando. */
@Composable
private fun PlaylistSongList(
    songs: List<Song>,
    vm: MusicViewModel,
    emptyText: String,
    reorderEnabled: Boolean,
    onAdd: (Song) -> Unit,
    onRemove: (Song) -> Unit,
    onReorder: (List<Long>) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyMessage(emptyText)
        return
    }
    val scheme = MaterialTheme.colorScheme

    // Copia local para ir moviendo las filas mientras arrastras; se guarda al soltar
    var local by remember(songs.map { it.id }) { mutableStateOf(songs) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        local = local.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(local, key = { it.id }) { song ->
            ReorderableItem(reorderState, key = song.id) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag")
                Surface(shadowElevation = elevation, color = scheme.surface) {
                    Box(
                        Modifier.then(
                            if (reorderEnabled) {
                                Modifier.longPressDraggableHandle(
                                    onDragStopped = { onReorder(local.map { it.id }) }
                                )
                            } else Modifier
                        )
                    ) {
                        SongRow(
                            song = song,
                            vm = vm,
                            onClick = { vm.playQueue(local, song) },
                            onAdd = onAdd,
                            onRemove = onRemove,
                            swipeEnabled = !isDragging
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    vm: MusicViewModel,
    onClick: () -> Unit,
    onAdd: (Song) -> Unit,
    onRemove: ((Song) -> Unit)?,
    swipeEnabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isCurrent = vm.currentSong?.id == song.id

    SwipeToQueue(
        enabled = swipeEnabled,
        onQueue = {
            vm.addToQueue(song)
            Toast.makeText(context, "Añadida a la cola", Toast.LENGTH_SHORT).show()
        }
    ) {
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
            leadingContent = { CoverTile(song.coverPath, 48.dp) },
            trailingContent = {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        MoreIcon(iconSize = 20.dp, color = scheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Añadir a lista") },
                            onClick = {
                                menuOpen = false
                                onAdd(song)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Añadir a la cola") },
                            onClick = {
                                menuOpen = false
                                vm.addToQueue(song)
                                Toast.makeText(context, "Añadida a la cola", Toast.LENGTH_SHORT).show()
                            }
                        )
                        if (onRemove != null) {
                            DropdownMenuItem(
                                text = { Text("Quitar de esta lista") },
                                onClick = {
                                    menuOpen = false
                                    onRemove(song)
                                }
                            )
                        }
                    }
                }
            },
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}

/**
 * Deslizar la fila hacia la izquierda y soltar: añade la canción a la cola.
 * Con [enabled] = false no reacciona (por ejemplo, mientras se reordena).
 */
@Composable
private fun SwipeToQueue(
    enabled: Boolean,
    onQueue: () -> Unit,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val maxPx = with(density) { 140.dp.toPx() }
    val thresholdPx = with(density) { 88.dp.toPx() }
    val currentOnQueue by rememberUpdatedState(onQueue)
    var offsetX by remember { mutableFloatStateOf(0f) }

    Box(Modifier.fillMaxWidth()) {
        // Fondo que se descubre al deslizar
        Box(
            Modifier
                .matchParentSize()
                .background(scheme.surfaceVariant)
                .padding(end = 24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (offsetX < 0f) {
                Text(
                    "Añadir a la cola",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant.copy(
                        alpha = (-offsetX / thresholdPx).coerceIn(0f, 1f)
                    )
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(scheme.surface)
                .then(
                    if (enabled) {
                        Modifier.pointerInput(thresholdPx, maxPx) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val trigger = offsetX <= -thresholdPx
                                    scope.launch {
                                        if (trigger) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            currentOnQueue()
                                        }
                                        animate(offsetX, 0f) { value, _ -> offsetX = value }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        animate(offsetX, 0f) { value, _ -> offsetX = value }
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    offsetX = (offsetX + dragAmount).coerceIn(-maxPx, 0f)
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            content()
        }
    }
}

@Composable
private fun GroupList(
    groups: List<SongGroup>,
    libraryEmpty: Boolean,
    onOpen: (SongGroup) -> Unit
) {
    if (groups.isEmpty()) {
        EmptyMessage(if (libraryEmpty) "No hay canciones" else "Sin resultados")
        return
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<String?>(null) }
    val letters = remember(groups) { indexLetters(groups.map { it.name }) }
    val hasIndex = letters.size >= 2

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(end = if (hasIndex) 20.dp else 0.dp)
        ) {
            items(groups, key = { it.name }) { group ->
                ListItem(
                    headlineContent = {
                        Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text(countLabel(group.songs.size)) },
                    leadingContent = {
                        CoverTile(group.songs.firstNotNullOfOrNull { it.coverPath }, 48.dp)
                    },
                    modifier = Modifier.clickable { onOpen(group) }
                )
            }
        }
        if (hasIndex) {
            AlphabetBar(
                letters = letters.map { it.first },
                onSelect = { i -> scope.launch { listState.scrollToItem(letters[i].second) } },
                onActive = { activeLetter = it },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
            activeLetter?.let { LetterBubble(it, Modifier.align(Alignment.Center)) }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    byId: Map<Long, Song>,
    onNew: () -> Unit,
    onOpen: (Playlist) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text("Nueva lista") },
                leadingContent = {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        PlusIcon(iconSize = 22.dp, color = scheme.onSurfaceVariant)
                    }
                },
                modifier = Modifier.clickable(onClick = onNew)
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            val songs = playlist.songIds.mapNotNull { byId[it] }
            ListItem(
                headlineContent = {
                    Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = { Text(countLabel(songs.size)) },
                leadingContent = {
                    CoverTile(songs.firstNotNullOfOrNull { it.coverPath }, 48.dp)
                },
                modifier = Modifier.clickable { onOpen(playlist) }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Barra lateral de letras
// ---------------------------------------------------------------------------

/** Letras pequeñas a la derecha: toca o arrastra para saltar a esa letra. */
@Composable
private fun AlphabetBar(
    letters: List<String>,
    onSelect: (Int) -> Unit,
    onActive: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var current by remember { mutableStateOf(-1) }

    Column(
        modifier
            .fillMaxHeight()
            .width(22.dp)
            .padding(vertical = 8.dp)
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var lastIndex = -1

                    fun pick(y: Float) {
                        val index = ((y / size.height) * letters.size).toInt()
                            .coerceIn(0, letters.size - 1)
                        if (index != lastIndex) {
                            lastIndex = index
                            current = index
                            onSelect(index)
                            onActive(letters[index])
                        }
                    }

                    pick(down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        pick(change.position.y)
                        change.consume()
                    } while (change.pressed)

                    current = -1
                    onActive(null)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEachIndexed { index, letter ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    letter,
                    fontSize = 10.sp,
                    fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == current) scheme.onSurface else scheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Letra grande que aparece en el centro mientras arrastras por la barra. */
@Composable
private fun LetterBubble(letter: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, style = MaterialTheme.typography.headlineLarge, color = scheme.onSurface)
    }
}

// ---------------------------------------------------------------------------
// Añadir varias canciones a una lista (botón +)
// ---------------------------------------------------------------------------

@Composable
private fun AddSongsDialog(playlist: Playlist, vm: MusicViewModel, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val neutral = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface)
    var query by remember { mutableStateOf("") }
    val songs = remember(vm.songs, query) {
        sortedByTitle(vm.songs.filter { matches(it, query) })
    }
    val inList = playlist.songIds.toSet()

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
                        "Añadir a «${playlist.name}»",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = onDismiss, colors = neutral) { Text("Listo") }
                }
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                if (songs.isEmpty()) {
                    EmptyMessage("Sin resultados")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(songs, key = { it.id }) { song ->
                            val added = song.id in inList
                            ListItem(
                                headlineContent = {
                                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = { CoverTile(song.coverPath, 48.dp) },
                                trailingContent = { SelectMark(added) },
                                modifier = Modifier.clickable {
                                    if (added) vm.removeFromPlaylist(playlist.id, song.id)
                                    else vm.addToPlaylist(playlist.id, song.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectMark(selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.onSurface else Color.Transparent)
            .border(1.5.dp, if (selected) scheme.onSurface else scheme.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Text("✓", color = scheme.surface, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------------------
// Piezas comunes
// ---------------------------------------------------------------------------

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Cuadrado con la portada (o una nota musical si no tiene). */
@Composable
private fun CoverTile(path: String?, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    val thumb = rememberThumbnail(path)
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

/** Versión pequeña de la portada, para que las listas vayan fluidas. */
@Composable
private fun rememberThumbnail(path: String?): ImageBitmap? {
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