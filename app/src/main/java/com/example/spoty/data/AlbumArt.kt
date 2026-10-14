package com.example.spoty.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream

object AlbumArt {

    fun load(context: Context, albumId: Long): Bitmap? {
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