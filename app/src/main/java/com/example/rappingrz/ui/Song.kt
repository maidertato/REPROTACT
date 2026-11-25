package com.example.rappingrz.ui

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val album: String = "",
    val coverPath: String? = null,
    val originalTitle: String = title,
    val originalArtist: String = artist
)