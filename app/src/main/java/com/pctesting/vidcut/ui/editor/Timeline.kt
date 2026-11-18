package com.pctesting.vidcut.ui.editor

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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

private class Holder(var value: Int)

/**
 * Horizontal timeline with the playhead fixed in the middle. Scrolling it scrubs the video.
 * Tap a clip to select it, drag its white edges to trim, long-press and drag to reorder.
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

    BoxWithConstraints(modifier.fillMaxWidth().height(164.dp).background(c.background)) {
        val half = maxWidth / 2
        val totalDp = with(density) { totalPx.toDp() }
        Row(Modifier.fillMaxSize().horizontalScroll(scroll)) {
            Spacer(Modifier.width(half))
            Column(Modifier.width(totalDp).padding(top = 6.dp)) {
                Ruler(project.durationMs, pxPerMs)
                Spacer(Modifier.height(6.dp))
                ClipsRow(state, pxPerMs)
                Spacer(Modifier.height(8.dp))
                TextLane(state, pxPerMs)
                Spacer(Modifier.height(6.dp))
                MusicLane(state, totalDp)
            }
            Spacer(Modifier.width(half))
        }

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
private fun ClipsRow(state: EditorState, pxPerMs: Float) {
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
    val currentWidths by rememberUpdatedState(widths)
    Row(Modifier.height(ClipHeight)) {
        clips.forEachIndexed { index, clip -> key(clip.id) {
            val currentIndex by rememberUpdatedState(index)
            val currentClip by rememberUpdatedState(clip)
            var dragX by remember(clip.id) { mutableFloatStateOf(0f) }
            var dragging by remember(clip.id) { mutableStateOf(false) }
            val selected = clip.id == state.selectedClipId
            val wDp = with(density) { widths[index].toDp() }
            Box(
                Modifier
                    .zIndex(if (dragging) 2f else if (selected) 1f else 0f)
                    .graphicsLayer {
                        translationX = dragX
                        if (dragging) { scaleY = 1.06f; alpha = 0.9f }
                    }
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
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = true; state.selectClip(currentClip.id) },
                            onDragEnd = {
                                val ws = currentWidths
                                val from = currentIndex
                                var start = 0f
                                for (i in 0 until from) start += ws[i]
                                val myCenter = start + ws[from] / 2f + dragX
                                var acc = 0f
                                var target = 0
                                for (i in ws.indices) {
                                    val center = acc + ws[i] / 2f
                                    acc += ws[i]
                                    if (i != from && center < myCenter) target++
                                }
                                dragX = 0f
                                dragging = false
                                state.moveClip(from, target)
                            },
                            onDragCancel = { dragX = 0f; dragging = false },
                        ) { change, amount ->
                            change.consume()
                            dragX += amount.x
                        }
                    }
                    .semantics { contentDescription = "Clip ${index + 1}, ${formatSeconds(clip.outputDurationMs)}" },
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
