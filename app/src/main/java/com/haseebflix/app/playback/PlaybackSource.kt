package com.haseebflix.app.playback

import com.haseebflix.app.domain.MediaType
import kotlinx.coroutines.flow.MutableStateFlow

data class PlaybackRequest(
    val type: MediaType, val id: Int, val season: Int, val episode: Int,
    val title: String, val preferredHeight: Int, val audioLanguage: String,
)

data class Stream(val url: String, val qualityLabel: String? = null)

/**
 * The single seam between the app and whatever provides video.
 * TMDB supplies metadata, artwork and trailer keys, but NOT video streams.
 * Implement this interface with a source you are licensed to use and bind it in AppModule.
 * Returning null makes the player show the "no source" state with a trailer fallback.
 */
interface PlaybackSource {
    suspend fun resolve(request: PlaybackRequest): Stream?
}

class NoPlaybackSource : PlaybackSource {
    override suspend fun resolve(request: PlaybackRequest): Stream? = null
}

/** Lets MainActivity know when to enter picture-in-picture. */
object PlayerState {
    @Volatile var active: Boolean = false
    val inPip = MutableStateFlow(false)
}
