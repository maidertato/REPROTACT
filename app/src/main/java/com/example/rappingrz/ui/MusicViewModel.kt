package com.example.rappingrz.ui

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.rappingrz.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val QUEUED_KEY = "queued"

/** Una canción de la cola: su posición en el reproductor y si la añadió el usuario a mano. */
data class QueueItem(val index: Int, val song: Song, val queued: Boolean)

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private var controller: MediaController? = null

    private val controllerFuture = MediaController.Builder(
        app,
        SessionToken(app, ComponentName(app, PlaybackService::class.java))
    ).buildAsync()

    private val store = SongEditsStore(app)
    private val playlistStore = PlaylistStore(app)

    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var playlists by mutableStateOf(playlistStore.load())
        private set
    var currentSong by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeatAll by mutableStateOf(false)
        private set
    /** Canciones que sonarán a continuación, en el orden real (respeta el modo aleatorio). */
    var upcoming by mutableStateOf<List<QueueItem>>(emptyList())
        private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            refreshCurrent()
            refreshQueue()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            refreshQueue()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffle = shuffleModeEnabled
            refreshQueue()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            repeatAll = repeatMode == Player.REPEAT_MODE_ALL
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("MusicVM", "Error de reproducción", error)
        }
    }

    init {
        controllerFuture.addListener({
            try {
                val c = controllerFuture.get()
                c.addListener(listener)
                controller = c
                isPlaying = c.isPlaying
                shuffle = c.shuffleModeEnabled
                repeatAll = c.repeatMode == Player.REPEAT_MODE_ALL
                applyQueue()
            } catch (e: Exception) {
                Log.e("MusicVM", "No se pudo conectar con el servicio", e)
            }
        }, ContextCompat.getMainExecutor(app))

        // Actualiza la posición de la barra de avance cada medio segundo
        viewModelScope.launch {
            while (true) {
                controller?.let { c ->
                    positionMs = c.currentPosition.coerceAtLeast(0L)
                    durationMs = c.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    fun loadSongs() {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val list = mutableListOf<Song>()
        getApplication<Application>().contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val song = Song(
                    id,
                    c.getString(1) ?: "Desconocido",
                    c.getString(2) ?: "Artista desconocido",
                    ContentUris.withAppendedId(collection, id)
                )
                list += song.withEdit(store.get(id))
            }
        }
        songs = list
        applyQueue()
    }

    // Carga toda la biblioteca en el reproductor (solo si está vacío, para no cortar lo que ya suena)
    private fun applyQueue() {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (c.mediaItemCount == 0) {
            c.setMediaItems(songs.map { it.toMediaItem() })
            c.prepare()
        }
        refreshCurrent()
    }

    private fun refreshCurrent() {
        val id = controller?.currentMediaItem?.mediaId
        currentSong = songs.firstOrNull { it.id.toString() == id }
    }

    // Recorre la cola desde la canción actual, en el orden en que sonará de verdad
    private fun refreshQueue() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            upcoming = emptyList()
            return
        }
        val timeline = c.currentTimeline
        val byId = songs.associateBy { it.id.toString() }
        val result = mutableListOf<QueueItem>()
        var index = c.currentMediaItemIndex
        var guard = 0
        while (guard++ <= c.mediaItemCount) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            if (index == C.INDEX_UNSET) break
            val item = c.getMediaItemAt(index)
            val song = byId[item.mediaId] ?: continue
            result += QueueItem(index, song, item.isQueued())
        }
        upcoming = result
    }

    // Aplica los cambios guardados sobre los datos originales de la canción
    private fun Song.withEdit(e: SongEdit?) = copy(
        title = e?.title?.takeIf { it.isNotBlank() } ?: originalTitle,
        artist = e?.artist?.takeIf { it.isNotBlank() } ?: originalArtist,
        album = e?.album.orEmpty(),
        coverPath = e?.coverPath?.takeIf { it.isNotEmpty() && File(it).exists() }
    )

    // [queued] marca las canciones que el usuario ha añadido a la cola a mano
    private fun Song.toMediaItem(queued: Boolean = false) = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album.ifBlank { null })
                .setArtworkUri(coverPath?.let { Uri.fromFile(File(it)) })
                .setExtras(Bundle().apply { putBoolean(QUEUED_KEY, queued) })
                .build()
        )
        .build()

    private fun MediaItem.isQueued() = mediaMetadata.extras?.getBoolean(QUEUED_KEY) == true

    // ---------- Listas de reproducción ----------

    private fun updatePlaylists(newList: List<Playlist>) {
        playlists = newList
        playlistStore.save(newList)
    }

    fun createPlaylist(name: String): Long {
        val id = System.currentTimeMillis()
        updatePlaylists(playlists + Playlist(id, name.trim().ifBlank { "Nueva lista" }, emptyList()))
        return id
    }

    fun renamePlaylist(id: Long, name: String) {
        if (name.isBlank()) return
        updatePlaylists(playlists.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deletePlaylist(id: Long) {
        updatePlaylists(playlists.filter { it.id != id })
    }

    /** Devuelve false si la canción ya estaba en la lista. */
    fun addToPlaylist(playlistId: Long, songId: Long): Boolean {
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return false
        if (songId in playlist.songIds) return false
        updatePlaylists(
            playlists.map {
                if (it.id == playlistId) it.copy(songIds = it.songIds + songId) else it
            }
        )
        return true
    }

    fun removeFromPlaylist(playlistId: Long, songId: Long) {
        updatePlaylists(
            playlists.map {
                if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it
            }
        )
    }

    /** Guarda el nuevo orden de una lista. Lo que no aparezca en [orderedIds] se conserva al final. */
    fun reorderPlaylist(playlistId: Long, orderedIds: List<Long>) {
        updatePlaylists(
            playlists.map { playlist ->
                if (playlist.id != playlistId) playlist
                else playlist.copy(
                    songIds = orderedIds + playlist.songIds.filter { it !in orderedIds }
                )
            }
        )
    }

    // ---------- Edición de datos ----------

    fun saveDetails(song: Song, title: String, artist: String, album: String) {
        val current = store.get(song.id) ?: SongEdit()
        updateSong(
            song.id,
            current.copy(
                // Si el texto es igual al original, no se guarda como cambio
                title = title.trim().takeIf { it != song.originalTitle } ?: "",
                artist = artist.trim().takeIf { it != song.originalArtist } ?: "",
                album = album.trim()
            )
        )
    }

    fun setCover(song: Song, source: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { saveCover(song.id, source) } ?: return@launch
            val current = store.get(song.id) ?: SongEdit()
            deleteFile(current.coverPath)
            updateSong(song.id, current.copy(coverPath = path))
        }
    }

    fun removeCover(song: Song) {
        val current = store.get(song.id) ?: return
        deleteFile(current.coverPath)
        updateSong(song.id, current.copy(coverPath = ""))
    }

    fun resetSong(song: Song) {
        store.get(song.id)?.let { deleteFile(it.coverPath) }
        updateSong(song.id, null)
    }

    private fun updateSong(id: Long, edit: SongEdit?) {
        if (edit == null || edit == SongEdit()) store.remove(id) else store.put(id, edit)

        val index = songs.indexOfFirst { it.id == id }
        if (index < 0) return
        val updated = songs[index].withEdit(store.get(id))
        songs = songs.toMutableList().also { it[index] = updated }

        // Actualiza también lo que ve el reproductor (notificación, pantalla de bloqueo)
        controller?.let { c ->
            val mediaId = id.toString()
            for (i in 0 until c.mediaItemCount) {
                val item = c.getMediaItemAt(i)
                if (item.mediaId == mediaId) {
                    c.replaceMediaItem(i, updated.toMediaItem(queued = item.isQueued()))
                }
            }
        }
        refreshCurrent()
    }

    // Reduce la imagen elegida y la guarda dentro de la app, para que no dependa del original
    private fun saveCover(id: Long, source: Uri): String? {
        return try {
            val app = getApplication<Application>()
            val resolver = app.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            var sample = 1
            while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val dir = File(app.filesDir, "covers").apply { mkdirs() }
            val file = File(dir, "cover_${id}_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("MusicVM", "No se pudo guardar la portada", e)
            null
        }
    }

    private fun deleteFile(path: String) {
        if (path.isNotEmpty()) File(path).delete()
    }

    // ---------- Controles de reproducción ----------

    /** Reproduce [start] y deja como cola toda la lista [list] (álbum, artista, lista...). */
    fun playQueue(list: List<Song>, start: Song) {
        val c = controller ?: return
        val index = list.indexOfFirst { it.id == start.id }
        if (index < 0) return
        c.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    /**
     * Añade una canción a la cola (FIFO): suena justo después de la actual y de las que
     * ya hubieras añadido antes, y antes del resto de la lista que está sonando.
     */
    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            c.setMediaItems(listOf(song.toMediaItem(queued = true)))
            c.prepare()
            return
        }
        var insertAt = c.currentMediaItemIndex + 1
        while (insertAt < c.mediaItemCount && c.getMediaItemAt(insertAt).isQueued()) {
            insertAt++
        }
        c.addMediaItem(insertAt, song.toMediaItem(queued = true))
    }

    /** Salta a una canción de la cola y la reproduce. */
    fun playFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekToDefaultPosition(index)
        c.play()
    }

    /** Quita una canción de la cola (no la borra del teléfono). */
    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
            return
        }
        when (c.playbackState) {
            Player.STATE_IDLE -> c.prepare()
            Player.STATE_ENDED -> c.seekTo(0, 0)
        }
        c.play()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        positionMs = ms // para que la barra no "salte" hasta la siguiente actualización
        controller?.seekTo(ms)
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun toggleRepeat() {
        controller?.let {
            it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_ALL)
                Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
        }
    }

    override fun onCleared() {
        // Se suelta el mando, pero el servicio sigue reproduciendo si está sonando
        MediaController.releaseFuture(controllerFuture)
    }
}