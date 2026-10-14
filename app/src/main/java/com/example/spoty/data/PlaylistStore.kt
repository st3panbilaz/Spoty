package com.example.spoty.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>
)

object PlaylistStore {

    private const val PREFS_NAME = "spoty_prefs"
    private const val KEY_PLAYLISTS = "playlists"

    fun load(context: Context): List<Playlist> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<Playlist>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val idsArr = obj.getJSONArray("songIds")
                val ids = mutableListOf<Long>()
                for (j in 0 until idsArr.length()) ids.add(idsArr.getLong(j))
                result.add(
                    Playlist(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        songIds = ids
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, playlists: List<Playlist>) {
        val arr = JSONArray()
        for (p in playlists) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            val idsArr = JSONArray()
            for (id in p.songIds) idsArr.put(id)
            obj.put("songIds", idsArr)
            arr.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    fun create(context: Context, name: String): List<Playlist> {
        val current = load(context).toMutableList()
        current.add(
            Playlist(
                id = UUID.randomUUID().toString(),
                name = name,
                songIds = emptyList()
            )
        )
        save(context, current)
        return current
    }

    fun delete(context: Context, playlistId: String): List<Playlist> {
        val current = load(context).filterNot { it.id == playlistId }
        save(context, current)
        return current
    }

    fun toggleSong(context: Context, playlistId: String, songId: Long): List<Playlist> {
        val current = load(context).toMutableList()
        val idx = current.indexOfFirst { it.id == playlistId }
        if (idx == -1) return current

        val playlist = current[idx]
        val newIds = playlist.songIds.toMutableList()
        if (newIds.contains(songId)) newIds.remove(songId) else newIds.add(songId)
        current[idx] = playlist.copy(songIds = newIds)
        save(context, current)
        return current
    }

    fun isSongInPlaylist(context: Context, playlistId: String, songId: Long): Boolean {
        val p = load(context).firstOrNull { it.id == playlistId } ?: return false
        return p.songIds.contains(songId)
    }
}