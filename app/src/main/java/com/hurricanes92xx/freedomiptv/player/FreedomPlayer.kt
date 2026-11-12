package com.hurricanes92xx.freedomiptv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Single player owner; screens should attach/detach surfaces rather than create players. */
class FreedomPlayer(context: Context) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private var currentId: String? = null
    var lastPlaybackError: String? by mutableStateOf(null)
        private set

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                lastPlaybackError = error.localizedMessage ?: "Playback failed. Check the stream and try again."
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) lastPlaybackError = null
            }
        })
    }

    fun play(id: String, url: String, mimeType: String? = null) {
        if (id == currentId && exoPlayer.isPlaying) return
        currentId = id
        lastPlaybackError = null
        val builder = MediaItem.Builder().setMediaId(id).setUri(url)
        if (mimeType != null) builder.setMimeType(mimeType)
        exoPlayer.setMediaItem(builder.build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun retry() {
        lastPlaybackError = null
        exoPlayer.seekToDefaultPosition()
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun release() { exoPlayer.release() }
}
