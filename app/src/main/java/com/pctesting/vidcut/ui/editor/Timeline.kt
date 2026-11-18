package com.pctesting.vidcut.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pctesting.vidcut.data.Clip
import com.pctesting.vidcut.data.TextItem
import com.pctesting.vidcut.media.PreviewPlayer
import com.pctesting.vidcut.media.rememberThumbnail
import com.pctesting.vidcut.ui.common.formatSeconds
import com.pctesting.vidcut.ui.common.formatSpeed
import com.pctesting.vidcut.ui.theme.DarkColors
import com.pctesting.vidcut.ui.theme.Vc
import kotlin.math.roundToInt

private val PxPerSecondDp = 64.dp
private val ClipHeight = 56.dp
private val LaneHeight = 28.dp

private val SlotSize = 64.dp
private val SquareSize = 56.dp
private val ClipRowTop = 28.dp

private class Holder(var value: Int)

/** An in-progress clip move. [fingerX] and [stripStart] are in timeline-box pixels. */
private data class ReorderDrag(val from: Int, val fingerX: Float, val stripStart: Float)

private class ReorderCallbacks(
    val start: (index: Int, localX: Float) -> Unit,
    val drag: (dx: Float) -> Unit,
    val end: () -> Unit,
    val cancel: () -> Unit,
)

private fun reorderTarget(r: ReorderDrag, slotPx: Float, count: Int): Int {
    if (count <= 0) return 0
    val pos = (r.fingerX - r.stripStart) / slotPx
    return if (pos < 0f) 0 else pos.toInt().coerceIn(0, count - 1)
}

/** Reorder mode: every clip as an equal square; the held one follows the finger. */
@Composable
private fun ReorderOverlay(clips: List<Clip>, r: ReorderDrag, slotPx: Float) {
    val c = Vc.colors
    val density = LocalDensity.current
    val target = reorderTarget(r, slotPx, clips.size)
    val squarePx = with(density) { SquareSize.toPx() }
    val topPx = with(density) { ClipRowTop.roundToPx() }
    Box(Modifier.fillMaxSize()) {
        // Hide the full-length clips underneath.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = ClipRowTop - 6.dp)
                .height(SlotSize + 4.dp)
                .background(c.background),
        )
        Text(
            "Drag to move · let go to drop",
            color = c.icon, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
        // Where the clip will land.
        val slotX = r.stripStart + target * slotPx + (slotPx - squarePx) / 2f
        val dashColor = c.textTertiary
        Box(
            Modifier
                .offset { IntOffset(slotX.roundToInt(), topPx) }
                .size(SquareSize)
                .drawBehind {
                    drawRoundRect(
                        color = dashColor,
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    )
                },
        )
        clips.forEachIndexed { j, clip ->
            key(clip.id) {
                if (j != r.from) {
                    val visual = if (j < r.from) {
                        if (j >= target) j + 1 else j
                    } else {
                        if (j - 1 >= target) j else j - 1
                    }
                    val x by animateFloatAsState(
                        targetValue = r.stripStart + visual * slotPx + (slotPx - squarePx) / 2f,
                        label = "reorder-x",
                    )
                    ReorderSquare(clip, j + 1, Modifier.offset { IntOffset(x.roundToInt(), topPx) }.size(SquareSize), lifted = false)
                }
            }
        }
        clips.getOrNull(r.from)?.let { held ->
            val liftedPx = with(density) { (SquareSize + 8.dp).toPx() }
            ReorderSquare(
                held, r.from + 1,
                Modifier
                    .offset { IntOffset((r.fingerX - liftedPx / 2f).roundToInt(), topPx - with(density) { 4.dp.roundToPx() }) }
                    .zIndex(3f)
                    .shadow(12.dp, RoundedCornerShape(10.dp))
                    .size(SquareSize + 8.dp),
                lifted = true,
            )
        }
    }
}

@Composable
private fun ReorderSquare(clip: Clip, number: Int, modifier: Modifier, lifted: Boolean) {
    val c = Vc.colors
    val shape = RoundedCornerShape(if (lifted) 10.dp else 8.dp)
    Box(
        modifier
            .clip(shape)
            .background(c.thumbPlaceholder)
            .then(if (lifted) Modifier.border(2.dp, c.accentText, shape) else Modifier),
    ) {
        val thumb by rememberThumbnail(clip.path, clip.isImage, clip.trimStartMs, 128)
        thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Text(
            if (lifted) "$number · ${formatSeconds(clip.outputDurationMs)}" else "$number",
            color = if (lifted) DarkColors.accentText else DarkColors.text,
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(DarkColors.background)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/**
 * Horizontal timeline with the playhead fixed in the middle. Scrolling it scrubs the video.
 * Tap a clip to select it, drag its white edges to trim. Long-press a clip to reorder: all clips
 * shrink to squares so they fit on screen, the held one follows the finger.
 */
@Composable
fun Timeline(state: EditorState, preview: PreviewPlayer, onAddClips: () -> Unit, modifier: Modifier = Modifier) {
    val c = Vc.colors
    val density = LocalDensity.current
    val pxPerMs = with(density) { PxPerSecondDp.toPx() } / 1000f
    val project = state.project
    val totalPx = (project.durationMs * pxPerMs).roundToInt()
    val scroll = rememberScrollState()
    val lastSet = remember { Holder(-1) }
    val dragged by scroll.interactionSource.collectIsDraggedAsState()

    // Keep the timeline under the playhead while playing / after seeks.
    LaunchedEffect(preview.positionMs, totalPx) {
        if (!dragged && !scroll.isScrollInProgress) {
            val target = (preview.positionMs * pxPerMs).roundToInt()
            if (scroll.value != target) {
                lastSet.value = target
                scroll.scrollTo(target)
            }
        }
    }
    LaunchedEffect(dragged) {
        if (dragged && preview.isPlaying) preview.pause()
    }
    // User scrolling scrubs the preview.
    LaunchedEffect(scroll, pxPerMs) {
        snapshotFlow { scroll.value }.collect { v ->
            if (v != lastSet.value && scroll.isScrollInProgress) {
                lastSet.value = -1
                preview.seekTo((v / pxPerMs).toLong())
            }
        }
    }

    var reorder by remember { mutableStateOf<ReorderDrag?>(null) }
    val haptics = LocalHapticFeedback.current
    val slotPx = with(density) { SlotSize.toPx() }

    BoxWithConstraints(modifier.fillMaxWidth().height(164.dp).background(c.background)) {
        val half = maxWidth / 2
        val boxWidthPx = constraints.maxWidth.toFloat()
        val totalDp = with(density) { totalPx.toDp() }
        val clipCount = project.clips.size

        val callbacks = ReorderCallbacks(
            start = { index, localX ->
                val clips = state.project.clips
                if (index in clips.indices) {
                    var startPx = 0f
                    for (i in 0 until index) startPx += clips[i].outputDurationMs * pxPerMs
                    val fingerX = boxWidthPx / 2f + startPx - scroll.value + localX
                    preview.pause()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.selectClip(clips[index].id)
                    // Place the square strip so the held clip's square sits under the finger.
                    reorder = ReorderDrag(index, fingerX, fingerX - (index * slotPx + slotPx / 2f))
                }
            },
            drag = { dx -> reorder?.let { reorder = it.copy(fingerX = it.fingerX + dx) } },
            end = {
                val r = reorder
                reorder = null
                if (r != null) {
                    val target = reorderTarget(r, slotPx, state.project.clips.size)
                    state.moveClip(r.from, target)
                }
            },
            cancel = { reorder = null },
        )

        // Auto-scroll the square strip when the finger is near either edge.
        val reordering = reorder != null
        LaunchedEffect(reordering, boxWidthPx, clipCount) {
            val edge = with(density) { 48.dp.toPx() }
            while (reorder != null) {
                val r = reorder ?: break
                val maxStart = boxWidthPx / 2f - slotPx / 2f
                val minStart = boxWidthPx / 2f - (clipCount - 1) * slotPx - slotPx / 2f
                var s = r.stripStart
                if (r.fingerX < edge && s < maxStart) s = minOf(s + 14f, maxStart)
                else if (r.fingerX > boxWidthPx - edge && s > minStart) s = maxOf(s - 14f, minStart)
                if (s != r.stripStart) reorder = r.copy(stripStart = s)
                delay(16)
            }
        }

        Row(Modifier.fillMaxSize().horizontalScroll(scroll)) {
            Spacer(Modifier.width(half))
            Column(Modifier.width(totalDp).padding(top = 6.dp)) {
                Ruler(project.durationMs, pxPerMs)
                Spacer(Modifier.height(6.dp))
                ClipsRow(state, pxPerMs, callbacks)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.alpha(if (reordering) 0.35f else 1f)) { TextLane(state, pxPerMs) }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.alpha(if (reordering) 0.35f else 1f)) { MusicLane(state, totalDp) }
            }
            Spacer(Modifier.width(half))
        }

        reorder?.let { r -> ReorderOverlay(project.clips, r, slotPx) }
        if (reordering) return@BoxWithConstraints

        // Fixed controls.
        val muted = project.muteOriginal
        Column(
            Modifier
                .padding(start = 8.dp, top = 30.dp)
                .size(52.dp, ClipHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(c.surface)
                .clickable(role = Role.Switch, onClickLabel = if (muted) "Unmute clip audio" else "Mute clip audio") {
                    state.setMuteOriginal(!muted)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null, tint = c.icon, modifier = Modifier.size(18.dp),
            )
            Text(if (muted) "Muted" else "Audio", color = c.icon, fontSize = 10.sp)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 10.dp, top = 36.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.text)
                .clickable(role = Role.Button, onClickLabel = "Add clips", onClick = onAddClips)
                .semantics { contentDescription = "Add clips" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = c.background, modifier = Modifier.size(24.dp))
        }
        // Playhead.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(2.dp)
                .fillMaxHeight()
                .background(c.playhead),
        )
    }
}

@Composable
private fun Ruler(durationMs: Long, pxPerMs: Float) {
    val c = Vc.colors
    Box(Modifier.fillMaxWidth().height(16.dp)) {
        val stepMs = 2000L
        var t = 0L
        while (t <= durationMs) {
            val x = (t * pxPerMs).roundToInt()
            val sec = t / 1000
            Text(
                "%02d:%02d".format(sec / 60, sec % 60),
                color = c.textTertiary, fontSize = 11.sp,
                modifier = Modifier.offset { IntOffset(x, 0) },
            )
            t += stepMs
        }
    }
}

@Composable
private fun ClipsRow(state: EditorState, pxPerMs: Float, reorder: ReorderCallbacks) {
    val c = Vc.colors
    val density = LocalDensity.current
    val clips = state.project.clips
    if (clips.isEmpty()) {
        Box(Modifier.height(ClipHeight).width(220.dp), contentAlignment = Alignment.CenterStart) {
            Text("Tap + to add clips", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
        }
        return
    }
    val widths = clips.map { it.outputDurationMs * pxPerMs }
    val callbacks by rememberUpdatedState(reorder)
    Row(Modifier.height(ClipHeight)) {
        clips.forEachIndexed { index, clip -> key(clip.id) {
            val currentIndex by rememberUpdatedState(index)
            val currentClip by rememberUpdatedState(clip)
            val selected = clip.id == state.selectedClipId
            val wDp = with(density) { widths[index].toDp() }
            Box(
                Modifier
                    .zIndex(if (selected) 1f else 0f)
                    .width(wDp)
                    .fillMaxHeight()
                    .padding(end = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.thumbPlaceholder)
                    .then(if (selected) Modifier.border(2.dp, c.text, RoundedCornerShape(6.dp)) else Modifier)
                    .pointerInput(clip.id) {
                        detectTapGestures(onTap = {
                            val id = currentClip.id
                            state.selectClip(if (state.selectedClipId == id) null else id)
                        })
                    }
                    .pointerInput(clip.id) {
                        // Long-press switches the timeline into reorder mode (clips shrink to squares).
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset -> callbacks.start(currentIndex, offset.x) },
                            onDragEnd = { callbacks.end() },
                            onDragCancel = { callbacks.cancel() },
                        ) { change, amount ->
                            change.consume()
                            callbacks.drag(amount.x)
                        }
                    }
                    .semantics {
                        contentDescription = "Clip ${index + 1}, ${formatSeconds(clip.outputDurationMs)}"
                        val actions = mutableListOf<CustomAccessibilityAction>()
                        if (index > 0) actions += CustomAccessibilityAction("Move clip left") { state.moveClip(currentIndex, currentIndex - 1); true }
                        if (index < clips.lastIndex) actions += CustomAccessibilityAction("Move clip right") { state.moveClip(currentIndex, currentIndex + 1); true }
                        customActions = actions
                    },
            ) {
                ClipThumbs(clip, widths[index], pxPerMs)
                val label = if (!clip.isImage && clip.speed != 1f) {
                    "${formatSpeed(clip.speed)} · ${formatSeconds(clip.outputDurationMs)}"
                } else formatSeconds(clip.outputDurationMs)
                Text(
                    label,
                    color = if (selected) DarkColors.accentText else DarkColors.text,
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = if (selected) 18.dp else 5.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(DarkColors.background)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
                if (selected) {
                    TrimHandle(Modifier.align(Alignment.CenterStart), "Trim start") { dx ->
                        val cl = currentClip
                        val d = (dx / pxPerMs).toLong()
                        if (cl.isImage) state.trimClip(cl.id, 0, cl.trimmedMs - d)
                        else state.trimClip(cl.id, cl.trimStartMs + (d * cl.speed).toLong(), cl.trimEndMs)
                    }
                    TrimHandle(Modifier.align(Alignment.CenterEnd), "Trim end") { dx ->
                        val cl = currentClip
                        val d = (dx / pxPerMs).toLong()
                        if (cl.isImage) state.trimClip(cl.id, 0, cl.trimmedMs + d)
                        else state.trimClip(cl.id, cl.trimStartMs, cl.trimEndMs + (d * cl.speed).toLong())
                    }
                }
            }
        } }
    }
}

@Composable
private fun ClipThumbs(clip: Clip, widthPx: Float, pxPerMs: Float) {
    val density = LocalDensity.current
    val tilePx = with(density) { 40.dp.toPx() }
    val count = (widthPx / tilePx).toInt().coerceIn(1, 30) + if (widthPx % tilePx > 1f) 1 else 0
    Row(Modifier.fillMaxSize()) {
        for (k in 0 until count) {
            val outMs = ((k * tilePx + tilePx / 2) / pxPerMs).toLong()
            val srcMs = if (clip.isImage) 0L else clip.trimStartMs + (outMs * clip.speed).toLong()
            val snapped = srcMs / 1000 * 1000
            val thumb by rememberThumbnail(clip.path, clip.isImage, snapped, 128)
            Box(Modifier.width(with(density) { tilePx.toDp() }).fillMaxHeight()) {
                thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun TrimHandle(modifier: Modifier, label: String, onDrag: (Float) -> Unit) {
    val c = Vc.colors
    val latest by rememberUpdatedState(onDrag)
    Box(
        modifier
            .width(14.dp)
            .fillMaxHeight()
            .background(c.text)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    latest(dx)
                }
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(2.dp, 16.dp).background(c.background))
    }
}

@Composable
private fun TextLane(state: EditorState, pxPerMs: Float) {
    val c = Vc.colors
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().height(LaneHeight)) {
        state.project.texts.forEach { t -> key(t.id) {
            val current by rememberUpdatedState(t)
            val selected = t.id == state.selectedTextId
            val x = (t.startMs * pxPerMs).roundToInt()
            val w = with(density) { ((t.endMs - t.startMs) * pxPerMs).coerceAtLeast(24f).toDp() }
            Box(
                Modifier
                    .offset { IntOffset(x, 0) }
                    .zIndex(if (selected) 1f else 0f)
                    .width(w)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.textTrack)
                    .then(if (selected) Modifier.border(2.dp, c.text, RoundedCornerShape(6.dp)) else Modifier)
                    .pointerInput(t.id) { detectTapGestures(onTap = { state.selectText(current.id) }) },
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = if (selected) 16.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.TextFields, contentDescription = null, tint = c.onTextTrack, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t.text, color = c.onTextTrack, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (selected) {
                    TrimHandle(Modifier.align(Alignment.CenterStart), "Text start") { dx ->
                        val cur = current
                        val d = (dx / pxPerMs).toLong()
                        state.updateText(cur.id, "time") { it.copy(startMs = (it.startMs + d).coerceIn(0L, it.endMs - Clip.MIN_MS)) }
                    }
                    TrimHandle(Modifier.align(Alignment.CenterEnd), "Text end") { dx ->
                        val cur = current
                        val d = (dx / pxPerMs).toLong()
                        state.updateText(cur.id, "time") { it.copy(endMs = maxOf(it.startMs + Clip.MIN_MS, it.endMs + d)) }
                    }
                }
            }
        } }
    }
}

@Composable
private fun MusicLane(state: EditorState, totalDp: Dp) {
    val c = Vc.colors
    val music = state.project.music
    val open = { state.selectClip(null); state.panel = Panel.Audio }
    if (music == null) {
        Row(
            Modifier
                .height(LaneHeight)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, c.sliderTrack, RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClick = open)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = c.icon, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add music", color = c.icon, fontSize = 12.sp)
        }
    } else {
        Row(
            Modifier
                .width(totalDp.coerceAtLeast(120.dp))
                .height(LaneHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(c.audioTrack)
                .clickable(role = Role.Button, onClickLabel = "Music settings", onClick = open)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = c.onAudioTrack, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "${music.name} · ${(music.volume * 100).roundToInt()}%",
                color = c.onAudioTrack, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
