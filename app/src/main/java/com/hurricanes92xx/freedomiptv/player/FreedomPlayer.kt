package com.hurricanes92xx.freedomiptv.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Single player owner; screens attach/detach surfaces rather than create players. */
class FreedomPlayer(context: Context) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private var currentId: String? = null
    private var currentUrl: String? = null
    private var released = false

    var lastPlaybackError: String? by mutableStateOf(null)
        private set

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                lastPlaybackError = error.message
                    ?: error.localizedMessage
                    ?: "Playback failed. Check the stream and try again."
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) lastPlaybackError = null
            }
        })
    }

    @Synchronized
    fun play(id: String, url: String, mimeType: String? = null) {
        check(!released) { "Player has been released." }
        require(id.isNotBlank()) { "A channel or media ID is required." }
        require(url.isNotBlank()) { "A stream URL is required." }

        // Same media + active playback: don't reset the user's position.
        if (id == currentId && url == currentUrl && exoPlayer.isPlaying) return

        currentId = id
        currentUrl = url
        lastPlaybackError = null
        exoPlayer.setMediaItem(
            MediaItem.Builder().setMediaId(id).setUri(url).apply {
                mimeType?.let { setMimeType(it) }
            }.build()
        )
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    @Synchronized
    fun retry() {
        check(!released) { "Player has been released." }
        val item = exoPlayer.currentMediaItem ?: return
        lastPlaybackError = null
        // Reinstalling the item resets a failed decoder/source cleanly.
        exoPlayer.setMediaItem(item, exoPlayer.currentPosition.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    @Synchronized
    fun stop() {
        if (released) return
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        currentId = null
        currentUrl = null
        lastPlaybackError = null
    }

    @Synchronized
    fun release() {
        if (released) return
        released = true
        currentId = null
        currentUrl = null
        exoPlayer.release()
    }
}
