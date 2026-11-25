package com.example.rappingrz.ui

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/** Cambios que haces sobre una canción. Un texto vacío significa "sin cambio". */
data class SongEdit(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val coverPath: String = ""
)

/** Guarda los cambios en un archivo propio de la app (los archivos de música no se tocan). */
class SongEditsStore(context: Context) {

    private val file = File(context.filesDir, "song_edits.json")
    private val edits = mutableMapOf<Long, SongEdit>()

    init {
        load()
    }

    fun get(id: Long): SongEdit? = edits[id]

    fun put(id: Long, edit: SongEdit) {
        edits[id] = edit
        save()
    }

    fun remove(id: Long) {
        if (edits.remove(id) != null) save()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            for (key in root.keys()) {
                val o = root.getJSONObject(key)
                edits[key.toLong()] = SongEdit(
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    album = o.optString("album"),
                    coverPath = o.optString("coverPath")
                )
            }
        } catch (ex: Exception) {
            Log.e("SongEditsStore", "No se pudo leer el archivo de cambios", ex)
        }
    }

    private fun save() {
        try {
            val root = JSONObject()
            for ((id, edit) in edits) {
                root.put(
                    id.toString(),
                    JSONObject()
                        .put("title", edit.title)
                        .put("artist", edit.artist)
                        .put("album", edit.album)
                        .put("coverPath", edit.coverPath)
                )
            }
            file.writeText(root.toString())
        } catch (ex: Exception) {
            Log.e("SongEditsStore", "No se pudo guardar el archivo de cambios", ex)
        }
    }
}