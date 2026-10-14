package com.example.spoty.data

/**
 * Одна песня. Используется везде — в списках, плеере, плейлистах.
 *
 * id       — уникальный идентификатор (из MediaStore или hashCode от uri папки)
 * albumId  — нужен для загрузки обложки через content://media/external/audio/albumart
 * path     — путь к файлу: обычный `/storage/...` или content:// URI
 * title, artist, album, duration — метаданные (duration в миллисекундах)
 */
data class Song(
    val id: Long,
    val albumId: Long,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long
)