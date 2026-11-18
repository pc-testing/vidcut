package com.pctesting.vidcut.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Keeps projects as JSON files in the app's private storage. Imported media is copied into the app
 * too, so projects keep working even if the original file is moved or deleted from the gallery.
 */
class ProjectStore(context: Context) {
    private val appContext = context.applicationContext
    private val projectsDir = File(appContext.filesDir, "projects").apply { mkdirs() }
    private val mediaDir = File(appContext.filesDir, "media").apply { mkdirs() }

    fun list(): List<Project> = projectsDir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { runCatching { Project.fromJson(JSONObject(it.readText())) }.getOrNull() }
        ?.sortedByDescending { it.updatedAt }
        ?: emptyList()

    fun load(id: String): Project? = runCatching {
        Project.fromJson(JSONObject(File(projectsDir, "$id.json").readText()))
    }.getOrNull()

    @Synchronized
    fun save(project: Project) {
        val target = File(projectsDir, "${project.id}.json")
        val tmp = File(projectsDir, "${project.id}.json.tmp")
        tmp.writeText(project.toJson().toString())
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun create(name: String, clips: List<Clip>): Project {
        val now = System.currentTimeMillis()
        val p = Project(id = UUID.randomUUID().toString(), name = name, createdAt = now, updatedAt = now, clips = clips)
        save(p)
        return p
    }

    fun nextName(): String {
        val names = list().map { it.name }.toSet()
        var n = 1
        while ("Project $n" in names) n++
        return "Project $n"
    }

    fun rename(id: String, name: String) {
        load(id)?.let { save(it.copy(name = name, updatedAt = System.currentTimeMillis())) }
    }

    fun duplicate(id: String) {
        val p = load(id) ?: return
        val now = System.currentTimeMillis()
        save(p.copy(id = UUID.randomUUID().toString(), name = "${p.name} copy", createdAt = now, updatedAt = now))
    }

    /** Deletes a project and any media files no other project uses. */
    fun delete(id: String) {
        val p = load(id)
        File(projectsDir, "$id.json").delete()
        if (p != null) {
            val stillUsed = list().flatMap { o -> o.clips.map { it.path } + listOfNotNull(o.music?.path) }.toSet()
            (p.clips.map { it.path } + listOfNotNull(p.music?.path))
                .filter { it !in stillUsed }
                .forEach { File(it).delete() }
        }
    }

    /** Copies a picked photo or video into the app and returns a clip for it, or null if it can't be read. */
    fun importVisual(uri: Uri): Clip? {
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri) ?: guessMime(uri)
        val id = UUID.randomUUID().toString()
        return if (mime.startsWith("image/")) {
            val file = File(mediaDir, "$id.jpg")
            if (!importPhoto(uri, file)) return null
            Clip(
                id = id, path = file.absolutePath, isImage = true, mime = "image/jpeg",
                sourceDurationMs = Clip.PHOTO_MAX_MS, trimStartMs = 0, trimEndMs = Clip.PHOTO_DEFAULT_MS,
            )
        } else {
            val ext = when {
                mime.contains("webm") -> "webm"
                mime.contains("3gp") -> "3gp"
                mime.contains("matroska") -> "mkv"
                else -> "mp4"
            }
            val file = File(mediaDir, "$id.$ext")
            if (!copy(uri, file)) return null
            val duration = durationOf(file)
            if (duration <= 0) { file.delete(); return null }
            Clip(
                id = id, path = file.absolutePath, isImage = false, mime = mime,
                sourceDurationMs = duration, trimStartMs = 0, trimEndMs = duration,
            )
        }
    }

    fun importMusic(uri: Uri): Music? {
        val name = displayName(uri) ?: "Music"
        val ext = name.substringAfterLast('.', "m4a").take(5)
        val file = File(mediaDir, "${UUID.randomUUID()}.$ext")
        if (!copy(uri, file)) return null
        val duration = durationOf(file)
        if (duration <= 0) { file.delete(); return null }
        return Music(path = file.absolutePath, name = name, durationMs = duration)
    }

    private fun copy(uri: Uri, target: File): Boolean = runCatching {
        appContext.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { input.copyTo(it, 1 shl 16) }
        }
        true
    }.getOrElse { target.delete(); false }

    /** Saves the photo upright (EXIF rotation applied) and no bigger than 2160 px on its long side. */
    private fun importPhoto(uri: Uri, target: File): Boolean = runCatching {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2160) sample *= 2
        val decoded = resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val orientation = runCatching {
            resolver.openInputStream(uri)!!.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        }
        var bmp = if (m.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        val longSide = maxOf(bmp.width, bmp.height)
        if (longSide > 2160) {
            val s = 2160f / longSide
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt() / 2 * 2, (bmp.height * s).toInt() / 2 * 2, true)
        } else if (bmp.width % 2 != 0 || bmp.height % 2 != 0) {
            // Even dimensions keep the video encoder happy.
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width / 2 * 2, bmp.height / 2 * 2)
        }
        target.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        true
    }.getOrElse { target.delete(); false }

    private fun durationOf(file: File): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { r.release() }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun guessMime(uri: Uri): String {
        val s = uri.toString().lowercase()
        return if (listOf(".jpg", ".jpeg", ".png", ".webp", ".heic").any { s.endsWith(it) }) "image/jpeg" else "video/mp4"
    }
}
