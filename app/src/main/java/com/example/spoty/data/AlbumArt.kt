package com.example.spoty.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream

/**
 * Загружает обложку альбома по albumId.
 */
object AlbumArt {

    fun load(context: Context, albumId: Long): Bitmap? {
        // Если albumId = 0 (например, для треков из FolderScanner) — обложки нет.
        if (albumId <= 0L) return null

        val uri: Uri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
        return try {
            val input: InputStream = context.contentResolver.openInputStream(uri)
                ?: return null
            input.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }
}