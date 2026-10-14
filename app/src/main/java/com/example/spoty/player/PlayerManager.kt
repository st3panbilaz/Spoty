package com.example.spoty.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.spoty.data.Song
import com.google.common.util.concurrent.MoreExecutors

// Мостик между UI и сервисом. Раньше тут был ExoPlayer, теперь — MediaController.
class PlayerManager(context: Context) {

    private var mediaController: MediaController? = null

    var currentSong: Song? = null

    // Имена без "is"/"set" — иначе конфликт с методами setShuffle/setRepeatMode.
    var shuffleEnabled: Boolean = false
    var currentRepeatMode: Int = REPEAT_ALL

    // Колбэки наружу: сюда MainActivity подписывается, чтобы обновлять UI,
    // когда трек меняется из шторки.
    var onSongChanged: ((Song?) -> Unit)? = null
    var onPlayingChanged: ((Boolean) -> Unit)? = null
    var queueProvider: (() -> List<Song>)? = null

    companion object {
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
    }

    // Ловим события плеера: переключение трека и play/pause.
    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.toLongOrNull() ?: return
            val song = queueProvider?.invoke()?.firstOrNull { it.id == id } ?: return
            currentSong = song
            onSongChanged?.invoke(song)
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            onPlayingChanged?.invoke(isPlaying)
        }
    }

    init {
        // Подключаемся к сервису — асинхронно.
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        MediaController.Builder(context, sessionToken).buildAsync().also { future ->
            future.addListener({
                mediaController = future.get()
                mediaController?.addListener(listener)
                // Сразу применяем то, что было выставлено в UI.
                mediaController?.shuffleModeEnabled = shuffleEnabled
                mediaController?.repeatMode = mapRepeat(currentRepeatMode)
            }, MoreExecutors.directExecutor())
        }
    }

    fun setShuffle(enabled: Boolean) {
        shuffleEnabled = enabled
        mediaController?.shuffleModeEnabled = enabled
    }

    fun setRepeatMode(mode: Int) {
        currentRepeatMode = mode
        mediaController?.repeatMode = mapRepeat(mode)
    }

    private fun mapRepeat(mode: Int) = when (mode) {
        REPEAT_OFF -> Player.REPEAT_MODE_OFF
        REPEAT_ALL -> Player.REPEAT_MODE_ALL
        REPEAT_ONE -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_ALL
    }

    // Главный метод: кладём в плеер ВСЮ очередь. Тогда next/prev работают сами,
    // и из приложения, и из шторки. mediaId нужен для синхронизации UI.
    fun playQueue(queue: List<Song>, startSong: Song) {
        currentSong = startSong
        val items = queue.map { song ->
            val uri = if (song.path.startsWith("content://")) song.path else "file://${song.path}"
            MediaItem.Builder().setUri(uri).setMediaId(song.id.toString()).build()
        }
        val startIndex = queue.indexOfFirst { it.id == startSong.id }.coerceAtLeast(0)
        mediaController?.apply {
            setMediaItems(items, startIndex, 0L)
            shuffleModeEnabled = shuffleEnabled
            repeatMode = mapRepeat(currentRepeatMode)
            prepare()
            play()
        }
    }

    // Одиночный трек — на всякий случай.
    fun playSong(song: Song) {
        currentSong = song
        val uri = if (song.path.startsWith("content://")) song.path else "file://${song.path}"
        mediaController?.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun isPlaying() = mediaController?.isPlaying ?: false
    fun seekTo(ms: Long) { mediaController?.seekTo(ms) }
    fun getCurrentPosition() = mediaController?.currentPosition ?: 0L
    fun getDuration() = mediaController?.duration?.takeIf { it > 0 } ?: 0L
    fun skipToNext() { mediaController?.seekToNextMediaItem() }
    fun skipToPrevious() { mediaController?.seekToPreviousMediaItem() }

    fun nextSong(list: List<Song>): Song? {
        if (list.isEmpty()) return null
        if (shuffleEnabled) return list.random()
        val cur = currentSong ?: return list.firstOrNull()
        val i = list.indexOfFirst { it.id == cur.id }
        if (i == -1) return list.firstOrNull()
        return list[(i + 1) % list.size]
    }

    fun previousSong(list: List<Song>): Song? {
        if (list.isEmpty()) return null
        if (shuffleEnabled) return list.random()
        val cur = currentSong ?: return list.firstOrNull()
        val i = list.indexOfFirst { it.id == cur.id }
        if (i == -1) return list.firstOrNull()
        return list[(i - 1 + list.size) % list.size]
    }

    fun songForAutoAdvance(list: List<Song>): Song? {
        if (list.isEmpty()) return null
        if (currentRepeatMode == REPEAT_ONE) return currentSong
        if (shuffleEnabled) return list.random()
        val cur = currentSong ?: return list.firstOrNull()
        val i = list.indexOfFirst { it.id == cur.id }
        if (i == -1) return list.firstOrNull()
        return if (i == list.size - 1) {
            if (currentRepeatMode == REPEAT_ALL) list.first() else null
        } else list[i + 1]
    }

    fun release() {
        mediaController?.removeListener(listener)
        mediaController?.release()
        mediaController = null
    }
}