@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package vn.lucbao.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import vn.lucbao.api.Playback
import vn.lucbao.api.Track
import vn.lucbao.engine.EngineHttpDataSource

/** Turns the engine's [Playback] description into an ExoPlayer media source. */
object MediaSources {
    fun build(playback: Playback, item: MediaItem): MediaSource {
        val sources = playback.tracks.map { t -> build(t, item) }
        require(sources.isNotEmpty()) { "No tracks" }
        return if (sources.size == 1) sources[0]
        else MergingMediaSource(true, *sources.toTypedArray())
    }

    private fun build(t: Track, item: MediaItem): MediaSource {
        val factory = EngineHttpDataSource.Factory(t.kind)
        val trackItem = item.buildUpon().setUri(t.uri).build()
        return when (t.kind) {
            Track.KIND_DASH -> {
                val manifest = DashManifestParser().parse(
                    Uri.parse(t.uri), t.manifest.byteInputStream()
                )
                DashMediaSource.Factory(factory).createMediaSource(manifest, trackItem)
            }
            Track.KIND_HLS -> HlsMediaSource.Factory(factory).createMediaSource(
                trackItem.buildUpon().setMimeType(MimeTypes.APPLICATION_M3U8).build()
            )
            else -> ProgressiveMediaSource.Factory(factory).createMediaSource(trackItem)
        }
    }
}
