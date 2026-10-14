package com.example.spoty.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object FolderScanner {

    private val AUDIO_EXTENSIONS = listOf(
        ".mp3", ".m4a", ".flac", ".wav", ".ogg", ".aac", ".opus"
    )

    fun scanFolder(context: Context, treeUri: Uri): List<Song> {
        val result = mutableListOf<Song>()
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return result
        scanRecursive(context, root, result)
        return result.sortedBy { it.title.lowercase() }
    }

    private fun scanRecursive(context: Context, dir: DocumentFile, out: MutableList<Song>) {
        val children = dir.listFiles()
        for (file in children) {
            if (file.isDirectory) {
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

    private fun fileToSong(context: Context, uri: Uri, fileName: String): Song? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: fileName.substringBeforeLast('.')
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "Неизвестный исполнитель"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Неизвестный альбом"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            val id = uri.toString().hashCode().toLong()

            Song(id, 0L, uri.toString(), title, artist, album, duration)
        } catch (e: Exception) {
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
            } catch (e2: Exception) { null }
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}