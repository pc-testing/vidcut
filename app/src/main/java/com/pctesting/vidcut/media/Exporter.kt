package com.pctesting.vidcut.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.pctesting.vidcut.data.Project
import com.pctesting.vidcut.data.TextItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Turns a project into an MP4 with Media3 Transformer. Must be used from the main thread. */
class Exporter(private val context: Context) {

    interface Listener {
        /** 0–100, or -1 when progress isn't known. */
        fun onProgress(percent: Int)
        fun onCompleted(file: File)
        fun onError(message: String)
    }

    private var transformer: Transformer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var poller: Runnable? = null

    val outputFile: File get() = File(context.cacheDir, "export.mp4")

    fun start(project: Project, shortSide: Int, listener: Listener) {
        cancel()
        val (outW, outH) = project.aspect.outputSize(shortSide)
        val composition = try {
            buildComposition(project, outW, outH)
        } catch (e: Exception) {
            listener.onError("Couldn't prepare the export: ${e.message}")
            return
        }
        val out = outputFile
        out.delete()

        val t = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopPolling()
                    transformer = null
                    listener.onCompleted(out)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    stopPolling()
                    transformer = null
                    listener.onError(exportException.message ?: "Export failed (code ${exportException.errorCode})")
                }
            })
            .build()
        transformer = t
        t.start(composition, out.absolutePath)

        val holder = ProgressHolder()
        val r = object : Runnable {
            override fun run() {
                val tr = transformer ?: return
                val state = tr.getProgress(holder)
                listener.onProgress(if (state == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else -1)
                handler.postDelayed(this, 200)
            }
        }
        poller = r
        handler.post(r)
    }

    fun cancel() {
        stopPolling()
        transformer?.cancel()
        transformer = null
    }

    private fun stopPolling() {
        poller?.let { handler.removeCallbacks(it) }
        poller = null
    }

    private fun buildComposition(project: Project, outW: Int, outH: Int): Composition {
        require(project.clips.isNotEmpty()) { "The project has no clips" }

        val items = project.clips.map { clip ->
            val uri = Uri.fromFile(File(clip.path))
            val audio = mutableListOf<AudioProcessor>()
            val video = mutableListOf<Effect>()
            val builder: EditedMediaItem.Builder
            if (clip.isImage) {
                val mi = MediaItem.Builder().setUri(uri).setMimeType(clip.mime).setImageDurationMs(clip.trimmedMs).build()
                builder = EditedMediaItem.Builder(mi)
                    .setDurationUs(clip.trimmedMs * 1000)
                    .setFrameRate(30)
            } else {
                val mi = MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.trimStartMs)
                            .setEndPositionMs(clip.trimEndMs)
                            .build(),
                    )
                    .build()
                builder = EditedMediaItem.Builder(mi)
                if (clip.speed != 1f) {
                    // Speed effects must come first in both lists.
                    val pair = Effects.createExperimentalSpeedChangingEffect(ConstantSpeed(clip.speed))
                    audio.add(pair.first)
                    video.add(pair.second)
                }
                val vol = project.effectiveVolume(clip)
                if (vol < 0.999f) audio.add(volumeProcessor(vol))
            }
            video.add(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT))
            builder.setEffects(Effects(audio, video)).build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence(items))

        project.music?.let { m ->
            val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(m.path))))
                .setRemoveVideo(true)
                .setEffects(Effects(listOf(volumeProcessor(m.volume)), emptyList()))
                .build()
            sequences.add(EditedMediaItemSequence(listOf(musicItem), /* isLooping= */ true))
        }

        val compositionEffects = if (project.texts.isNotEmpty()) {
            val overlay: TextureOverlay = TextBitmapOverlay(project.texts, outW, outH)
            Effects(emptyList(), listOf<Effect>(OverlayEffect(ImmutableList.of(overlay))))
        } else {
            Effects.EMPTY
        }

        return Composition.Builder(sequences)
            .setEffects(compositionEffects)
            .experimentalSetForceAudioTrack(true)
            .build()
    }

    private fun volumeProcessor(volume: Float): AudioProcessor {
        val p = ChannelMixingAudioProcessor()
        p.putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1).scaleBy(volume))
        p.putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(volume))
        return p
    }

    private class ConstantSpeed(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    companion object {
        /** Copies the exported file into the phone's gallery (Movies/VidCut) and returns its Uri. */
        fun saveToGallery(context: Context, file: File): Uri? {
            val name = "VidCut_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
            val resolver = context.contentResolver
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VidCut")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VidCut")
                dir.mkdirs()
                val target = File(dir, name)
                file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    @Suppress("DEPRECATION")
                    put(MediaStore.Video.Media.DATA, target.absolutePath)
                }
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            }
        }
    }
}

/**
 * Draws the project's text overlays onto a transparent frame-sized bitmap. A different bitmap is
 * returned for each set of visible texts, so the overlay updates when texts appear or disappear.
 */
class TextBitmapOverlay(
    private val texts: List<TextItem>,
    private val width: Int,
    private val height: Int,
) : BitmapOverlay() {
    private val cache = HashMap<String, Bitmap>()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val t = presentationTimeUs / 1000
        val visible = texts.filter { t >= it.startMs && t < it.endMs && it.text.isNotBlank() }
        val key = visible.joinToString(",") { it.id }
        return cache.getOrPut(key) { render(visible) }
    }

    private fun render(visible: List<TextItem>): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        for (item in visible) drawText(canvas, item, width, height)
        return bmp
    }

    companion object {
        fun drawText(canvas: Canvas, item: TextItem, width: Int, height: Int) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.color
                textSize = item.size * width / 360f
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, Color.argb(140, 0, 0, 0))
            }
            val maxW = (width * 0.9f).toInt()
            val desired = item.text.lines().maxOf { paint.measureText(it) }.toInt() + 2
            val layoutW = desired.coerceIn(1, maxW)
            val layout = StaticLayout.Builder.obtain(item.text, 0, item.text.length, paint, layoutW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(item.x * width - layoutW / 2f, item.y * height - layout.height / 2f)
            layout.draw(canvas)
            canvas.restore()
        }
    }
}
