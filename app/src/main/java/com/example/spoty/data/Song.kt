package com.example.spoty.data

data class Song(
    val id: Long,
    val albumId: Long,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long
)