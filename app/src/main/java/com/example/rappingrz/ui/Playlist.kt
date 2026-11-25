package com.example.rappingrz.ui

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>
)

/** Guarda las listas de reproducción en un archivo propio de la app. */
class PlaylistStore(context: Context) {

    private val file = File(context.filesDir, "playlists.json")

    fun load(): List<Playlist> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val ids = obj.getJSONArray("songs")
                Playlist(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    songIds = (0 until ids.length()).map { ids.getLong(it) }
                )
            }
        } catch (ex: Exception) {
            Log.e("PlaylistStore", "No se pudieron leer las listas", ex)
            emptyList()
        }
    }

    fun save(playlists: List<Playlist>) {
        try {
            val array = JSONArray()
            playlists.forEach { playlist ->
                array.put(
                    JSONObject()
                        .put("id", playlist.id)
                        .put("name", playlist.name)
                        .put("songs", JSONArray(playlist.songIds))
                )
            }
            file.writeText(array.toString())
        } catch (ex: Exception) {
            Log.e("PlaylistStore", "No se pudieron guardar las listas", ex)
        }
    }
}