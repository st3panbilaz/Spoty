package com.example.spoty.player

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

// Сервис, который держит музыку живой в фоне и рисует уведомление в шторке.
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this).build()

        // Обманка для системы: говорим, что кнопки next/prev ВСЕГДА доступны.
        // Иначе Android может их скрыть в шторке, если очередь кончилась.
        val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return Player.Commands.Builder()
                    .addAll(super.getAvailableCommands())
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    // Вызывается, когда юзер свайпнул приложение из недавних.
    // Именно тут музыка останавливается.
    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.run {
            if (playWhenReady) pause()
            stop()
            release()
        }
        mediaSession?.release()
        mediaSession = null
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}