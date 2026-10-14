package com.example.spoty.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// Модель плейлиста: id, имя и список id треков (сами треки хранить не нужно).
data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>
)

// Простое хранилище плейлистов в SharedPreferences. Формат — JSON-строка.
// Для большого количества плейлистов стоит перейти на Room, но пока хватает.
object PlaylistStore {

    private const val PREFS_NAME = "spoty_prefs"
    private const val KEY_PLAYLISTS = "playlists"

    fun load(context: Context): List<Playlist> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("songIds")
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    songIds = List(ids.length()) { j -> ids.getLong(j) }
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun save(context: Context, playlists: List<Playlist>) {
        val arr = JSONArray()
        playlists.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("songIds", JSONArray().apply { p.songIds.forEach { put(it) } })
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    fun create(context: Context, name: String): List<Playlist> =
        (load(context) + Playlist(UUID.randomUUID().toString(), name, emptyList()))
            .also { save(context, it) }

    fun delete(context: Context, id: String): List<Playlist> =
        load(context).filterNot { it.id == id }.also { save(context, it) }

    // Добавляет трек в плейлист или убирает его, если уже есть.
    fun toggleSong(context: Context, playlistId: String, songId: Long): List<Playlist> {
        val all = load(context).toMutableList()
        val idx = all.indexOfFirst { it.id == playlistId }
        if (idx == -1) return all
        val p = all[idx]
        val newIds = p.songIds.toMutableList()
        if (!newIds.remove(songId)) newIds.add(songId)
        all[idx] = p.copy(songIds = newIds)
        save(context, all)
        return all
    }
}