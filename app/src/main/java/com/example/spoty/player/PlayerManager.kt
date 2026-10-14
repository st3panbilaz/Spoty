package com.example.spoty.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.spoty.data.Song
import com.google.common.util.concurrent.MoreExecutors

class PlayerManager(context: Context) {

    private var mediaController: MediaController? = null

    var currentSong: Song? = null

    // Переименовано, чтобы не конфликтовать с методом setShuffle()
    var shuffleEnabled: Boolean = false

    // Переименовано, чтобы не конфликтовать с методом setRepeatMode()
    var currentRepeatMode: Int = REPEAT_ALL

    var onSongChanged: ((Song?) -> Unit)? = null
    var onPlayingChanged: ((Boolean) -> Unit)? = null
    var queueProvider: (() -> List<Song>)? = null

    companion object {
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val idStr = mediaItem?.mediaId ?: return
            val id = idStr.toLongOrNull() ?: return
            val song = queueProvider?.invoke()?.firstOrNull { it.id == id }
            if (song != null) {
                currentSong = song
                onSongChanged?.invoke(song)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onPlayingChanged?.invoke(isPlaying)
        }
    }

    init {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            mediaController = future.get()
            mediaController?.addListener(listener)
            mediaController?.shuffleModeEnabled = shuffleEnabled
            mediaController?.repeatMode = mapRepeat(currentRepeatMode)
        }, MoreExecutors.directExecutor())
    }

    fun setShuffle(enabled: Boolean) {
        shuffleEnabled = enabled
        mediaController?.shuffleModeEnabled = enabled
    }

    fun setRepeatMode(mode: Int) {
        currentRepeatMode = mode
        mediaController?.repeatMode = mapRepeat(mode)
    }

    private fun mapRepeat(mode: Int): Int = when (mode) {
        REPEAT_OFF -> Player.REPEAT_MODE_OFF
        REPEAT_ALL -> Player.REPEAT_MODE_ALL
        REPEAT_ONE -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_ALL
    }

    fun playQueue(queue: List<Song>, startSong: Song) {
        currentSong = startSong
        val mediaItems = queue.map { song ->
            val uri = if (song.path.startsWith("content://")) song.path
            else "file://${song.path}"
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(song.id.toString())
                .build()
        }
        val startIndex = queue.indexOfFirst { it.id == startSong.id }
            .coerceAtLeast(0)

        mediaController?.apply {
            setMediaItems(mediaItems, startIndex, 0L)
            shuffleModeEnabled = shuffleEnabled
            repeatMode = mapRepeat(currentRepeatMode)
            prepare()
            play()
        }
    }

    fun playSong(song: Song) {
        currentSong = song
        val uri = if (song.path.startsWith("content://")) song.path
        else "file://${song.path}"
        val mediaItem = MediaItem.fromUri(uri)
        mediaController?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun isPlaying(): Boolean = mediaController?.isPlaying ?: false

    fun seekTo(positionMs: Long) { mediaController?.seekTo(positionMs) }

    fun getCurrentPosition(): Long = mediaController?.currentPosition ?: 0L

    fun getDuration(): Long {
        val d = mediaController?.duration ?: 0L
        return if (d > 0) d else 0L
    }

    fun skipToNext() { mediaController?.seekToNextMediaItem() }
    fun skipToPrevious() { mediaController?.seekToPreviousMediaItem() }

    fun nextSong(allSongs: List<Song>): Song? {
        if (allSongs.isEmpty()) return null
        if (shuffleEnabled) return allSongs.random()
        val current = currentSong ?: return allSongs.firstOrNull()
        val idx = allSongs.indexOfFirst { it.id == current.id }
        if (idx == -1) return allSongs.firstOrNull()
        return allSongs[(idx + 1) % allSongs.size]
    }

    fun previousSong(allSongs: List<Song>): Song? {
        if (allSongs.isEmpty()) return null
        if (shuffleEnabled) return allSongs.random()
        val current = currentSong ?: return allSongs.firstOrNull()
        val idx = allSongs.indexOfFirst { it.id == current.id }
        if (idx == -1) return allSongs.firstOrNull()
        return allSongs[(idx - 1 + allSongs.size) % allSongs.size]
    }

    fun songForAutoAdvance(allSongs: List<Song>): Song? {
        if (allSongs.isEmpty()) return null
        if (currentRepeatMode == REPEAT_ONE) return currentSong
        if (shuffleEnabled) return allSongs.random()
        val current = currentSong ?: return allSongs.firstOrNull()
        val idx = allSongs.indexOfFirst { it.id == current.id }
        if (idx == -1) return allSongs.firstOrNull()
        val isLast = idx == allSongs.size - 1
        return if (isLast) {
            if (currentRepeatMode == REPEAT_ALL) allSongs.first() else null
        } else {
            allSongs[idx + 1]
        }
    }

    fun setListener(l: Player.Listener) { mediaController?.addListener(l) }
    fun removeListener(l: Player.Listener) { mediaController?.removeListener(l) }

    fun release() {
        mediaController?.removeListener(listener)
        mediaController?.release()
        mediaController = null
    }
}