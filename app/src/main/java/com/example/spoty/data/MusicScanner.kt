package com.example.spoty.data

import android.content.Context
import android.provider.MediaStore

/**
 * Сканирует всю музыку на устройстве через MediaStore.
 */
object MusicScanner {

    fun scanAllSongs(context: Context): List<Song> {
        val songList = mutableListOf<Song>()

        // EXTERNAL_CONTENT_URI — общий указатель на всю внешнюю память.
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        // Какие колонки хотим получить из базы.
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,      // полный путь к файлу
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )

        // IS_MUSIC != 0 — отсекает рингтоны, уведомления, звуки системы.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        // Сортировка по названию.
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = context.contentResolver.query(
            collection,
            projection,
            selection,
            null,          // selectionArgs — не нужны
            sortOrder
        )

        cursor?.use { c ->
            // Индексы колонок ищем ОДИН РАЗ до цикла — это быстрее,
            // чем каждый раз вызывать getColumnIndex внутри while.
            val idCol       = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val albumIdCol  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val pathCol     = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol      = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (c.moveToNext()) {
                val path = c.getString(pathCol) ?: continue

                // Если в теге пусто — ставим заглушки, чтобы UI не был пустым.
                songList.add(
                    Song(
                        id       = c.getLong(idCol),
                        albumId  = c.getLong(albumIdCol),
                        path     = path,
                        title    = c.getString(titleCol)  ?: "Неизвестно",
                        artist   = c.getString(artistCol) ?: "Неизвестный исполнитель",
                        album    = c.getString(albumCol)  ?: "Неизвестный альбом",
                        duration = c.getLong(durCol)
                    )
                )
            }
        }

        return songList
    }
}