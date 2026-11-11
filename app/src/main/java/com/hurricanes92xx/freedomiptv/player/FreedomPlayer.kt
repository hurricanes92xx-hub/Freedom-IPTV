package com.hurricanes92xx.freedomiptv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Single player owner; screens should attach/detach surfaces rather than create players. */
class FreedomPlayer(context: Context) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private var currentId: String? = null

    fun play(id: String, url: String, mimeType: String? = null) {
        if (id == currentId && exoPlayer.isPlaying) return
        currentId = id
        val builder = MediaItem.Builder().setMediaId(id).setUri(url)
        if (mimeType != null) builder.setMimeType(mimeType)
        exoPlayer.setMediaItem(builder.build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun release() { exoPlayer.release() }
}
