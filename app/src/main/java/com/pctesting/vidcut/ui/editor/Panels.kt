package com.pctesting.vidcut.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pctesting.vidcut.data.AspectRatio
import com.pctesting.vidcut.data.Clip
import com.pctesting.vidcut.data.TextItem
import com.pctesting.vidcut.ui.common.DoneButton
import com.pctesting.vidcut.ui.common.OutlineButton
import com.pctesting.vidcut.ui.common.VcSlider
import com.pctesting.vidcut.ui.common.formatPrecise
import com.pctesting.vidcut.ui.common.formatSeconds
import com.pctesting.vidcut.ui.common.formatSpeed
import com.pctesting.vidcut.ui.theme.Vc
import kotlin.math.roundToInt

/** Rounded panel that sits above the toolbar. */
@Composable
fun PanelSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Vc.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(c.surface)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
    ) { content() }
}

@Composable
private fun PanelHeader(title: String, value: String? = null, onDone: () -> Unit) {
    val c = Vc.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(value, color = c.accentText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
        }
        DoneButton(onClick = onDone)
    }
}

@Composable
fun SpeedPanel(clip: Clip, state: EditorState) {
    val c = Vc.colors
    PanelSurface {
        if (clip.isImage) {
            PanelHeader("Speed", onDone = { state.panel = Panel.ClipTools })
            Spacer(Modifier.height(8.dp))
            Text(
                "Speed only applies to videos. To change how long a photo shows, drag the white edges of the clip on the timeline.",
                color = c.textSecondary, fontSize = 13.sp,
            )
            return@PanelSurface
        }
        PanelHeader("Speed", formatSpeed(clip.speed), onDone = { state.endGesture(); state.panel = Panel.ClipTools })
        Spacer(Modifier.height(8.dp))
        VcSlider(
            value = clip.speed,
            onValueChange = { state.setSpeed(clip.id, (it * 10).roundToInt() / 10f) },
            valueRange = 0.5f..2f,
            steps = 14,
            onValueChangeFinished = { state.endGesture() },
        )
        Row(Modifier.fillMaxWidth()) {
            listOf("0.5x", "1x", "1.5x", "2x").forEachIndexed { i, s ->
                Text(
                    s, color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f),
                    textAlign = when (i) { 0 -> TextAlign.Start; 3 -> TextAlign.End; else -> TextAlign.Center },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Clip length ${formatSeconds(clip.trimmedMs)} → ${formatSeconds(clip.outputDurationMs)}",
            color = c.textSecondary, fontSize = 13.sp,
        )
    }
}

@Composable
fun VolumePanel(clip: Clip, state: EditorState) {
    val c = Vc.colors
    PanelSurface {
        PanelHeader("Clip volume", "${(clip.volume * 100).roundToInt()}%", onDone = { state.endGesture(); state.panel = Panel.ClipTools })
        Spacer(Modifier.height(8.dp))
        if (clip.isImage) {
            Text("Photos have no sound.", color = c.textSecondary, fontSize = 13.sp)
        } else {
            VcSlider(
                value = clip.volume,
                onValueChange = { state.setClipVolume(clip.id, it) },
                valueRange = 0f..1f,
                onValueChangeFinished = { state.endGesture() },
            )
            if (state.project.muteOriginal) {
                Text("All clip audio is muted. Tap Audio on the left of the timeline to unmute.", color = c.textSecondary, fontSize = 13.sp)
            }
        }
    }
}

private val TextColors = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFFD60A, 0xFF2FD3E0, 0xFFFF6FAE, 0xFFFF9F43, 0xFFFF4D4D, 0xFF7CFC8A,
).map { it.toInt() }

private val ColorNames = listOf("White", "Black", "Yellow", "Cyan", "Pink", "Orange", "Red", "Green")

@Composable
fun TextPanel(item: TextItem, state: EditorState, totalMs: Long) {
    val c = Vc.colors
    PanelSurface(Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Text", color = c.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = item.text,
                    onValueChange = { v -> state.updateText(item.id, "text") { it.copy(text = v) } },
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.accentText),
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.field)
                        .border(1.dp, c.sliderTrack, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .semantics { contentDescription = "Text" },
                )
            }
            Spacer(Modifier.width(10.dp))
            DoneButton(onClick = { state.selectText(null) })
        }
        Spacer(Modifier.height(14.dp))
        Text("Colour", color = c.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextColors.forEachIndexed { i, col ->
                val selected = item.color == col
                Box(
                    Modifier
                        .size(34.dp)
                        .then(if (selected) Modifier.border(2.dp, c.text, CircleShape) else Modifier)
                        .padding(if (selected) 4.dp else 0.dp)
                        .clip(CircleShape)
                        .background(Color(col))
                        .border(1.dp, c.sliderTrack, CircleShape)
                        .clickable(role = Role.RadioButton, onClickLabel = ColorNames[i]) {
                            state.updateText(item.id, "color") { it.copy(color = col) }
                        }
                        .semantics { contentDescription = ColorNames[i] + if (selected) ", selected" else "" },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row {
            Text("Size", color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${item.size.roundToInt()}", color = c.text, fontSize = 12.sp)
        }
        VcSlider(
            value = item.size,
            onValueChange = { v -> state.updateText(item.id, "size") { it.copy(size = v) } },
            valueRange = 10f..120f,
            onValueChangeFinished = { state.endGesture() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeStepper("Starts", item.startMs, Modifier.weight(1f)) { delta ->
                state.updateText(item.id, "start") { it.copy(startMs = (it.startMs + delta).coerceIn(0L, it.endMs - Clip.MIN_MS)) }
            }
            TimeStepper("Ends", item.endMs, Modifier.weight(1f)) { delta ->
                state.updateText(item.id, "end") {
                    val upper = maxOf(totalMs, it.startMs + Clip.MIN_MS)
                    it.copy(endMs = (it.endMs + delta).coerceIn(it.startMs + Clip.MIN_MS, upper))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Drag the text on the video to move it. You can also drag the ends of the purple bar on the timeline.",
                color = c.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlineButton("Delete", onClick = { state.deleteText(item.id) }, icon = Icons.Filled.Delete, color = c.danger)
        }
    }
}

@Composable
private fun TimeStepper(label: String, valueMs: Long, modifier: Modifier, onStep: (Long) -> Unit) {
    val c = Vc.colors
    Column(modifier) {
        Text(label, color = c.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.field)
                .border(1.dp, c.sliderTrack, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("−", "$label earlier") { onStep(-500) }
            Text(formatPrecise(valueMs), color = c.text, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            StepButton("+", "$label later") { onStep(500) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    val c = Vc.colors
    Box(
        Modifier
            .size(44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = c.icon, fontSize = 20.sp) }
}

@Composable
fun AudioPanel(state: EditorState, onPickMusic: () -> Unit) {
    val c = Vc.colors
    val p = state.project
    PanelSurface {
        PanelHeader("Audio", onDone = { state.endGesture(); state.panel = Panel.None })
        Spacer(Modifier.height(6.dp))
        Text("Background music", color = c.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        val music = p.music
        if (music == null) {
            OutlineButton("Add music from phone", onClick = onPickMusic, icon = Icons.Filled.MusicNote, modifier = Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, null, tint = c.icon, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(music.name, color = c.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${(music.volume * 100).roundToInt()}%", color = c.text, fontSize = 13.sp)
            }
            VcSlider(
                value = music.volume,
                onValueChange = { state.setMusicVolume(it) },
                valueRange = 0f..1f,
                onValueChangeFinished = { state.endGesture() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton("Replace", onClick = onPickMusic, icon = Icons.Filled.SwapHoriz, modifier = Modifier.weight(1f))
                OutlineButton("Remove", onClick = { state.setMusic(null) }, icon = Icons.Filled.Delete, color = c.danger, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Original clip audio", color = c.text, fontSize = 14.sp)
                Text(if (p.muteOriginal) "Muted" else "${(p.originalVolume * 100).roundToInt()}%", color = c.textSecondary, fontSize = 12.sp)
            }
            Text("Mute", color = c.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = p.muteOriginal,
                onCheckedChange = { state.setMuteOriginal(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.onAccent, checkedTrackColor = c.accent,
                    uncheckedThumbColor = c.textSecondary, uncheckedTrackColor = c.field, uncheckedBorderColor = c.sliderTrack,
                ),
                modifier = Modifier.semantics { contentDescription = "Mute original clip audio" },
            )
        }
        if (!p.muteOriginal) {
            VcSlider(
                value = p.originalVolume,
                onValueChange = { state.setOriginalVolume(it) },
                valueRange = 0f..1f,
                onValueChangeFinished = { state.endGesture() },
            )
        }
    }
}

@Composable
fun CanvasPanel(state: EditorState) {
    val c = Vc.colors
    PanelSurface {
        PanelHeader("Canvas", onDone = { state.panel = Panel.None })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AspectRatio.entries.forEach { a ->
                val selected = state.project.aspect == a
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.field)
                        .border(if (selected) 2.dp else 1.dp, if (selected) c.accentText else c.border, RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton, onClickLabel = "Use ${a.label}") { state.setAspect(a) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val (bw, bh) = when (a) {
                        AspectRatio.PORTRAIT -> 23.dp to 40.dp
                        AspectRatio.LANDSCAPE -> 56.dp to 32.dp
                        AspectRatio.SQUARE -> 36.dp to 36.dp
                    }
                    Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(bw, bh)
                                .border(2.dp, if (selected) c.accentText else c.icon, RoundedCornerShape(4.dp)),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        a.label + when (a) {
                            AspectRatio.PORTRAIT -> " · Stories"
                            AspectRatio.LANDSCAPE -> " · YouTube"
                            AspectRatio.SQUARE -> " · Square"
                        },
                        color = if (selected) c.text else c.icon, fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Clips that don't match are fitted inside with black bars.", color = c.textSecondary, fontSize = 12.sp)
    }
}
