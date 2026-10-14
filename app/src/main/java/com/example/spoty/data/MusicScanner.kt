package com.example.spoty.data

import android.content.Context
import android.provider.MediaStore

object MusicScanner {

    fun scanAllSongs(context: Context): List<Song> {
        val songList = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = context.contentResolver.query(
            collection, projection, selection, null, sortOrder
        )

        cursor?.use {
            val idColumn       = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val albumIdColumn  = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val pathColumn     = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleColumn    = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn   = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn    = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (it.moveToNext()) {
                val id       = it.getLong(idColumn)
                val albumId  = it.getLong(albumIdColumn)
                val path     = it.getString(pathColumn) ?: continue
                val title    = it.getString(titleColumn) ?: "Неизвестно"
                val artist   = it.getString(artistColumn) ?: "Неизвестный исполнитель"
                val album    = it.getString(albumColumn) ?: "Неизвестный альбом"
                val duration = it.getLong(durationColumn)

                songList.add(Song(id, albumId, path, title, artist, album, duration))
            }
        }
        return songList
    }
}