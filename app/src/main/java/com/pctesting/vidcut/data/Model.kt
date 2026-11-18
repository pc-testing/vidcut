package com.pctesting.vidcut.data

import org.json.JSONArray
import org.json.JSONObject

enum class AspectRatio(val id: String, val label: String, val w: Int, val h: Int) {
    PORTRAIT("9:16", "9:16", 9, 16),
    LANDSCAPE("16:9", "16:9", 16, 9),
    SQUARE("1:1", "1:1", 1, 1);

    val ratio: Float get() = w.toFloat() / h.toFloat()

    /** Output size where the short side is [shortSide] pixels. */
    fun outputSize(shortSide: Int): Pair<Int, Int> = when (this) {
        PORTRAIT -> shortSide to (shortSide * 16 / 9)
        LANDSCAPE -> (shortSide * 16 / 9) to shortSide
        SQUARE -> shortSide to shortSide
    }

    companion object {
        fun fromId(id: String?): AspectRatio = entries.firstOrNull { it.id == id } ?: PORTRAIT
    }
}

/**
 * One piece of media on the main track. For a video, [trimStartMs]..[trimEndMs] is the used part of
 * the source. For a photo, trimStart is always 0 and trimEnd is how long the photo shows.
 */
data class Clip(
    val id: String,
    val path: String,
    val isImage: Boolean,
    val mime: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val speed: Float = 1f,
    val volume: Float = 1f,
) {
    val trimmedMs: Long get() = trimEndMs - trimStartMs
    val outputDurationMs: Long get() = if (isImage) trimmedMs else (trimmedMs / speed).toLong()

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("path", path).put("isImage", isImage).put("mime", mime)
        .put("sourceDurationMs", sourceDurationMs).put("trimStartMs", trimStartMs)
        .put("trimEndMs", trimEndMs).put("speed", speed.toDouble()).put("volume", volume.toDouble())

    companion object {
        const val MIN_MS = 300L
        const val PHOTO_DEFAULT_MS = 3000L
        const val PHOTO_MAX_MS = 60_000L

        fun fromJson(o: JSONObject) = Clip(
            id = o.getString("id"),
            path = o.getString("path"),
            isImage = o.optBoolean("isImage"),
            mime = o.optString("mime", "video/mp4"),
            sourceDurationMs = o.optLong("sourceDurationMs"),
            trimStartMs = o.optLong("trimStartMs"),
            trimEndMs = o.optLong("trimEndMs"),
            speed = o.optDouble("speed", 1.0).toFloat(),
            volume = o.optDouble("volume", 1.0).toFloat(),
        )
    }
}

/** Text shown over the video. [x],[y] are the centre as fractions of the canvas; [size] is in units where the canvas is 360 wide. */
data class TextItem(
    val id: String,
    val text: String,
    val color: Int,
    val size: Float,
    val x: Float,
    val y: Float,
    val startMs: Long,
    val endMs: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("text", text).put("color", color).put("size", size.toDouble())
        .put("x", x.toDouble()).put("y", y.toDouble()).put("startMs", startMs).put("endMs", endMs)

    companion object {
        fun fromJson(o: JSONObject) = TextItem(
            id = o.getString("id"),
            text = o.optString("text"),
            color = o.optInt("color", -1),
            size = o.optDouble("size", 28.0).toFloat(),
            x = o.optDouble("x", 0.5).toFloat(),
            y = o.optDouble("y", 0.7).toFloat(),
            startMs = o.optLong("startMs"),
            endMs = o.optLong("endMs", 3000),
        )
    }
}

data class Music(
    val path: String,
    val name: String,
    val durationMs: Long,
    val volume: Float = 0.6f,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("path", path).put("name", name).put("durationMs", durationMs).put("volume", volume.toDouble())

    companion object {
        fun fromJson(o: JSONObject) = Music(
            path = o.getString("path"),
            name = o.optString("name", "Music"),
            durationMs = o.optLong("durationMs"),
            volume = o.optDouble("volume", 0.6).toFloat(),
        )
    }
}

data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val aspect: AspectRatio = AspectRatio.PORTRAIT,
    val clips: List<Clip> = emptyList(),
    val texts: List<TextItem> = emptyList(),
    val music: Music? = null,
    val originalVolume: Float = 1f,
    val muteOriginal: Boolean = false,
) {
    val durationMs: Long get() = clips.sumOf { it.outputDurationMs }

    /** Start time of each clip on the output timeline. */
    fun clipStarts(): LongArray {
        val starts = LongArray(clips.size)
        var t = 0L
        clips.forEachIndexed { i, c -> starts[i] = t; t += c.outputDurationMs }
        return starts
    }

    /** Volume actually used for a clip's own sound. */
    fun effectiveVolume(clip: Clip): Float = if (muteOriginal) 0f else (clip.volume * originalVolume).coerceIn(0f, 1f)

    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("version", 1)
            .put("id", id).put("name", name).put("createdAt", createdAt).put("updatedAt", updatedAt)
            .put("aspect", aspect.id)
            .put("clips", JSONArray().apply { clips.forEach { put(it.toJson()) } })
            .put("texts", JSONArray().apply { texts.forEach { put(it.toJson()) } })
            .put("originalVolume", originalVolume.toDouble())
            .put("muteOriginal", muteOriginal)
        music?.let { o.put("music", it.toJson()) }
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Project {
            val clipsArr = o.optJSONArray("clips") ?: JSONArray()
            val textsArr = o.optJSONArray("texts") ?: JSONArray()
            return Project(
                id = o.getString("id"),
                name = o.optString("name", "Untitled project"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
                aspect = AspectRatio.fromId(o.optString("aspect")),
                clips = (0 until clipsArr.length()).map { Clip.fromJson(clipsArr.getJSONObject(it)) },
                texts = (0 until textsArr.length()).map { TextItem.fromJson(textsArr.getJSONObject(it)) },
                music = o.optJSONObject("music")?.let { Music.fromJson(it) },
                originalVolume = o.optDouble("originalVolume", 1.0).toFloat(),
                muteOriginal = o.optBoolean("muteOriginal"),
            )
        }
    }
}
