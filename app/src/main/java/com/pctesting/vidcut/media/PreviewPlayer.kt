package com.pctesting.vidcut.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.image.ImageOutput
import com.pctesting.vidcut.data.Clip
import com.pctesting.vidcut.data.Project
import java.io.File

/**
 * Plays the project's clips back to back with ExoPlayer, plus the background music on a second
 * player kept in step. Positions exposed here are on the edited (output) timeline.
 */
class PreviewPlayer(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val musicPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ALL
    }
    private val main = Handler(Looper.getMainLooper())

    private var clips: List<Clip> = emptyList()
    private var starts = LongArray(0)
    private var mediaKey: List<String> = emptyList()
    private var project: Project? = null
    private var musicPath: String? = null

    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var currentIndex by mutableIntStateOf(0)
        private set
    var imageBitmap by mutableStateOf<Bitmap?>(null)
        private set
    /** Width / height of the current video, or 0 if not known yet. */
    var videoAspect by mutableFloatStateOf(0f)
        private set

    init {
        player.setImageOutput(object : ImageOutput {
            override fun onImageAvailable(presentationTimeUs: Long, bitmap: Bitmap) {
                main.post { imageBitmap = bitmap }
            }

            override fun onDisabled() {}
        })
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex
                applyClipSettings()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) startMusic() else musicPlayer.pause()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    positionMs = durationMs
                    musicPlayer.pause()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
        })
    }

    /** Call whenever the project changes. Only rebuilds the playlist when the media itself changed. */
    fun setProject(p: Project) {
        project = p
        clips = p.clips
        starts = p.clipStarts()
        durationMs = p.durationMs
        val key = p.clips.map { "${it.path}|${it.trimStartMs}|${it.trimEndMs}|${it.isImage}" }
        if (key != mediaKey) {
            mediaKey = key
            val keep = positionMs.coerceIn(0L, durationMs)
            if (clips.isEmpty()) {
                player.clearMediaItems()
                positionMs = 0
                imageBitmap = null
            } else {
                val (idx, media) = locate(keep)
                player.setMediaItems(clips.map { toMediaItem(it) }, idx, media)
                player.prepare()
                currentIndex = idx
                positionMs = keep
            }
        }
        applyClipSettings()

        val music = p.music
        if (music?.path != musicPath) {
            musicPath = music?.path
            if (music == null) {
                musicPlayer.stop()
                musicPlayer.clearMediaItems()
            } else {
                musicPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(music.path))))
                musicPlayer.prepare()
                if (isPlaying) startMusic()
            }
        }
        musicPlayer.volume = music?.volume ?: 0f
    }

    private fun toMediaItem(c: Clip): MediaItem {
        val b = MediaItem.Builder().setUri(Uri.fromFile(File(c.path))).setMediaId(c.id)
        return if (c.isImage) {
            b.setMimeType(c.mime).setImageDurationMs(c.trimmedMs).build()
        } else {
            b.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(c.trimStartMs)
                    .setEndPositionMs(c.trimEndMs)
                    .build(),
            ).build()
        }
    }

    /** Clip index and position inside that clip's (clipped) media for a timeline time. */
    private fun locate(timeMs: Long): Pair<Int, Long> {
        if (clips.isEmpty()) return 0 to 0L
        var i = clips.lastIndex
        while (i > 0 && timeMs < starts[i]) i--
        val c = clips[i]
        val local = (timeMs - starts[i]).coerceIn(0L, maxOf(0L, c.outputDurationMs - 1))
        val media = if (c.isImage) local else (local * c.speed).toLong()
        return i to media
    }

    private fun applyClipSettings() {
        val c = clips.getOrNull(currentIndex) ?: return
        val p = project ?: return
        player.setPlaybackSpeed(if (c.isImage) 1f else c.speed)
        player.volume = p.effectiveVolume(c)
    }

    /** Reads the player's position into [positionMs]; call regularly while playing. */
    fun tick() {
        if (clips.isEmpty()) return
        if (player.playbackState == Player.STATE_ENDED) {
            positionMs = durationMs
            return
        }
        val i = player.currentMediaItemIndex.coerceIn(0, clips.lastIndex)
        val c = clips[i]
        val local = player.currentPosition
        val out = if (c.isImage) local else (local / c.speed).toLong()
        positionMs = (starts[i] + out).coerceIn(0L, durationMs)
    }

    fun play() {
        if (clips.isEmpty()) return
        if (player.playbackState == Player.STATE_ENDED || positionMs >= durationMs - 50) seekTo(0)
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun seekTo(timeMs: Long) {
        if (clips.isEmpty()) return
        val t = timeMs.coerceIn(0L, durationMs)
        positionMs = t
        val (idx, media) = locate(t)
        player.seekTo(idx, media)
        currentIndex = idx
        applyClipSettings()
        syncMusic()
    }

    private fun startMusic() {
        if (musicPath == null) return
        syncMusic()
        musicPlayer.play()
    }

    private fun syncMusic() {
        if (musicPath == null) return
        val d = project?.music?.durationMs ?: 0L
        if (d > 0) musicPlayer.seekTo(positionMs % d)
    }

    fun release() {
        player.release()
        musicPlayer.release()
    }
}
