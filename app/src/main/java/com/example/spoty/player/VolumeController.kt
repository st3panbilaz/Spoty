package com.example.spoty.player

import android.content.Context
import android.media.AudioManager

class VolumeController(context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun getMaxVolume(): Int =
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun getCurrentVolume(): Int =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun setVolume(volume: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }
}