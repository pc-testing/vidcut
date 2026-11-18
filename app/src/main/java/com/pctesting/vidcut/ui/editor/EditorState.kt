package com.pctesting.vidcut.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pctesting.vidcut.data.AspectRatio
import com.pctesting.vidcut.data.Clip
import com.pctesting.vidcut.data.Music
import com.pctesting.vidcut.data.Project
import com.pctesting.vidcut.data.ProjectStore
import com.pctesting.vidcut.data.TextItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Panel { None, ClipTools, Speed, Volume, Text, Audio, Canvas }

/**
 * Holds the project being edited, undo/redo history and what's selected. Every change is saved to
 * disk automatically shortly after it happens.
 */
class EditorState(
    initial: Project,
    private val store: ProjectStore,
    private val scope: CoroutineScope,
) {
    var project by mutableStateOf(initial)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var saved by mutableStateOf(true)
        private set

    var selectedClipId by mutableStateOf<String?>(null)
    var selectedTextId by mutableStateOf<String?>(null)
    var panel by mutableStateOf(Panel.None)

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    private var lastKey: String? = null
    private var saveJob: Job? = null

    val selectedClip: Clip? get() = project.clips.firstOrNull { it.id == selectedClipId }
    val selectedText: TextItem? get() = project.texts.firstOrNull { it.id == selectedTextId }

    /**
     * Applies a change. Changes that share the same [key] in a row (e.g. moving a slider) become a
     * single undo step.
     */
    fun edit(key: String? = null, transform: (Project) -> Project) {
        val before = project
        val after = transform(before)
        if (after == before) return
        if (key == null || key != lastKey) {
            undoStack.addLast(before)
            if (undoStack.size > 100) undoStack.removeFirst()
            redoStack.clear()
        }
        lastKey = key
        project = after.copy(updatedAt = System.currentTimeMillis())
        refreshFlags()
        scheduleSave()
    }

    /** Ends a run of merged edits so the next change starts a new undo step. */
    fun endGesture() { lastKey = null }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(project)
        project = prev.copy(updatedAt = System.currentTimeMillis())
        lastKey = null
        fixSelection()
        refreshFlags()
        scheduleSave()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(project)
        project = next.copy(updatedAt = System.currentTimeMillis())
        lastKey = null
        fixSelection()
        refreshFlags()
        scheduleSave()
    }

    private fun fixSelection() {
        if (selectedClip == null) {
            selectedClipId = null
            if (panel == Panel.ClipTools || panel == Panel.Speed || panel == Panel.Volume) panel = Panel.None
        }
        if (selectedText == null) {
            selectedTextId = null
            if (panel == Panel.Text) panel = Panel.None
        }
    }

    private fun refreshFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun scheduleSave() {
        saved = false
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(600)
            saveNow()
        }
    }

    suspend fun saveNow() {
        val snapshot = project
        withContext(Dispatchers.IO) { store.save(snapshot) }
        if (project === snapshot) saved = true
    }

    // ---- Clips ----

    /** Index of the clip under the given timeline time. */
    fun clipIndexAt(timeMs: Long): Int {
        val clips = project.clips
        if (clips.isEmpty()) return -1
        val starts = project.clipStarts()
        for (i in clips.indices.reversed()) if (timeMs >= starts[i]) return i
        return 0
    }

    fun selectClip(id: String?) {
        endGesture()
        selectedClipId = id
        selectedTextId = null
        panel = if (id != null) Panel.ClipTools else Panel.None
    }

    fun selectText(id: String?) {
        endGesture()
        selectedTextId = id
        selectedClipId = null
        panel = if (id != null) Panel.Text else Panel.None
    }

    fun addClips(newClips: List<Clip>, atTimeMs: Long) {
        if (newClips.isEmpty()) return
        edit {
            val idx = if (it.clips.isEmpty()) 0 else clipIndexAt(atTimeMs) + 1
            val list = it.clips.toMutableList()
            list.addAll(idx.coerceIn(0, list.size), newClips)
            it.copy(clips = list)
        }
    }

    /** Splits the clip under the playhead. Returns false if the playhead is too close to a clip edge. */
    fun splitAt(timeMs: Long): Boolean {
        val idx = clipIndexAt(timeMs)
        if (idx < 0) return false
        val clip = project.clips[idx]
        val start = project.clipStarts()[idx]
        val localOut = timeMs - start
        if (localOut < Clip.MIN_MS || clip.outputDurationMs - localOut < Clip.MIN_MS) return false
        val cutSource = if (clip.isImage) localOut else clip.trimStartMs + (localOut * clip.speed).toLong()
        val first: Clip
        val second: Clip
        if (clip.isImage) {
            first = clip.copy(trimEndMs = localOut)
            second = clip.copy(id = UUID.randomUUID().toString(), trimStartMs = 0, trimEndMs = clip.trimmedMs - localOut)
        } else {
            first = clip.copy(trimEndMs = cutSource)
            second = clip.copy(id = UUID.randomUUID().toString(), trimStartMs = cutSource)
        }
        edit {
            val list = it.clips.toMutableList()
            list[idx] = first
            list.add(idx + 1, second)
            it.copy(clips = list)
        }
        selectedClipId = second.id
        return true
    }

    fun deleteClip(id: String) {
        edit { p -> p.copy(clips = p.clips.filterNot { it.id == id }) }
        if (selectedClipId == id) {
            selectedClipId = null
            panel = Panel.None
        }
    }

    fun moveClip(from: Int, to: Int) {
        if (from == to) return
        edit {
            val list = it.clips.toMutableList()
            if (from !in list.indices) return@edit it
            val c = list.removeAt(from)
            list.add(to.coerceIn(0, list.size), c)
            it.copy(clips = list)
        }
    }

    /** Moves the start or end of a clip. Values are in source time (or photo duration). */
    fun trimClip(id: String, newStart: Long, newEnd: Long) {
        edit("trim-$id") { p ->
            p.copy(clips = p.clips.map { c ->
                if (c.id != id) c
                else if (c.isImage) c.copy(trimStartMs = 0, trimEndMs = (newEnd - newStart).coerceIn(Clip.MIN_MS, Clip.PHOTO_MAX_MS))
                else {
                    val s = newStart.coerceIn(0L, maxOf(0L, c.trimEndMs - Clip.MIN_MS))
                    val e = newEnd.coerceIn(minOf(s + Clip.MIN_MS, c.sourceDurationMs), c.sourceDurationMs)
                    c.copy(trimStartMs = s, trimEndMs = e)
                }
            })
        }
    }

    fun setSpeed(id: String, speed: Float) {
        edit("speed-$id") { p -> p.copy(clips = p.clips.map { if (it.id == id) it.copy(speed = speed.coerceIn(0.5f, 2f)) else it }) }
    }

    fun setClipVolume(id: String, volume: Float) {
        edit("volume-$id") { p -> p.copy(clips = p.clips.map { if (it.id == id) it.copy(volume = volume.coerceIn(0f, 1f)) else it }) }
    }

    // ---- Text ----

    fun addText(atTimeMs: Long): TextItem {
        val total = project.durationMs
        val start = atTimeMs.coerceIn(0L, maxOf(0L, total - Clip.MIN_MS))
        val end = if (total > 0) minOf(start + 3000, total) else start + 3000
        val t = TextItem(
            id = UUID.randomUUID().toString(), text = "Your text", color = 0xFFFFFFFF.toInt(),
            size = 28f, x = 0.5f, y = 0.7f, startMs = start, endMs = maxOf(end, start + Clip.MIN_MS),
        )
        edit { it.copy(texts = it.texts + t) }
        selectText(t.id)
        return t
    }

    fun updateText(id: String, key: String, transform: (TextItem) -> TextItem) {
        edit("text-$key-$id") { p -> p.copy(texts = p.texts.map { if (it.id == id) transform(it) else it }) }
    }

    fun deleteText(id: String) {
        edit { p -> p.copy(texts = p.texts.filterNot { it.id == id }) }
        if (selectedTextId == id) {
            selectedTextId = null
            panel = Panel.None
        }
    }

    // ---- Audio & canvas ----

    fun setMusic(music: Music?) = edit { it.copy(music = music) }

    fun setMusicVolume(v: Float) = edit("music-volume") { p -> p.copy(music = p.music?.copy(volume = v.coerceIn(0f, 1f))) }

    fun setOriginalVolume(v: Float) = edit("original-volume") { it.copy(originalVolume = v.coerceIn(0f, 1f)) }

    fun setMuteOriginal(mute: Boolean) = edit { it.copy(muteOriginal = mute) }

    fun setAspect(a: AspectRatio) = edit { it.copy(aspect = a) }
}
