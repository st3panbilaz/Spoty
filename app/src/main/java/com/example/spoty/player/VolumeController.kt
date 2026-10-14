package com.example.spoty.player

import android.content.Context
import android.media.AudioManager

/**
 * Регулирует СИСТЕМНУЮ громкость (STREAM_MUSIC).
 * Ползунок в плеере связан с реальной громкостью телефона
 */
class VolumeController(context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Максимум шкалы (обычно 15 у современных телефонов). */
    fun getMaxVolume(): Int =
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /** Текущее значение (0..max). */
    fun getCurrentVolume(): Int =
        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    /** Меняет громкость. flags = 0 — без системного UI и вибрации. */
    fun setVolume(volume: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }
}