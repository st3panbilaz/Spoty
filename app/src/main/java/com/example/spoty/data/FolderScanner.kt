package com.example.spoty.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Сканирует выбранную пользователем папку (через ACTION_OPEN_DOCUMENT_TREE).
 */
object FolderScanner {

    // Поддерживаемые расширения. Если хочешь добавить формат — просто допиши сюда.
    private val AUDIO_EXTENSIONS = listOf(
        ".mp3", ".m4a", ".flac", ".wav", ".ogg", ".aac", ".opus"
    )

    fun scanFolder(context: Context, treeUri: Uri): List<Song> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val result = mutableListOf<Song>()
        scanRecursive(context, root, result)
        // Сортируем по названию — так же, как делает MusicScanner.
        return result.sortedBy { it.title.lowercase() }
    }

    /** Рекурсивно обходит папку и все вложенные. */
    private fun scanRecursive(context: Context, dir: DocumentFile, out: MutableList<Song>) {
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                // Уходим вглубь
                scanRecursive(context, file, out)
            } else if (file.isFile) {
                val name = file.name ?: continue
                val lower = name.lowercase()
                if (AUDIO_EXTENSIONS.any { lower.endsWith(it) }) {
                    val song = fileToSong(context, file.uri, name)
                    if (song != null) out.add(song)
                }
            }
        }
    }

    /**
     * Достаёт метаданные из файла через MediaMetadataRetriever.
     * Если теги пустые — подставляем имя файла и заглушки.
     */
    private fun fileToSong(context: Context, uri: Uri, fileName: String): Song? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)

            val title  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: fileName.substringBeforeLast('.')
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "Неизвестный исполнитель"
            val album  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Неизвестный альбом"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L

            // У файлов из папки нет "родного" MediaStore-id, поэтому используем
            // hashCode от URI — он уникален для каждого файла.
            val id = uri.toString().hashCode().toLong()

            // albumId = 0 — обложку из папки так просто не достать,
            // в UI будет серый placeholder.
            Song(
                id = id,
                albumId = 0L,
                path = uri.toString(),
                title = title,
                artist = artist,
                album = album,
                duration = duration
            )
        } catch (e: Exception) {
            // Если файл битый — вернём хотя бы имя файла.
            try {
                Song(
                    id = uri.toString().hashCode().toLong(),
                    albumId = 0L,
                    path = uri.toString(),
                    title = fileName.substringBeforeLast('.'),
                    artist = "Неизвестный исполнитель",
                    album = "Неизвестный альбом",
                    duration = 0L
                )
            } catch (e2: Exception) {
                null
            }
        } finally {
            // Retriever обязательно нужно освобождать.
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}