package com.pctesting.vidcut.ui.editor

import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pctesting.vidcut.data.TextItem
import com.pctesting.vidcut.media.PreviewPlayer
import com.pctesting.vidcut.ui.theme.Vc
import kotlin.math.roundToInt

/** The video preview: canvas in the chosen aspect ratio, current frame, and draggable text overlays. */
@Composable
fun PreviewArea(state: EditorState, preview: PreviewPlayer, modifier: Modifier = Modifier) {
    val c = Vc.colors
    val project = state.project
    BoxWithConstraints(modifier.background(c.letterbox).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val ratio = project.aspect.ratio
        val boxW = maxWidth
        val boxH = maxHeight
        val canvasW = if (boxW / boxH > ratio) boxH * ratio else boxW
        val canvasH = canvasW / ratio

        Box(
            Modifier
                .size(canvasW, canvasH)
                .clipToBounds()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { state.selectText(null); if (state.panel == Panel.Text) state.panel = Panel.None })
                },
            contentAlignment = Alignment.Center,
        ) {
            val clip = project.clips.getOrNull(preview.currentIndex)
            if (project.clips.isEmpty()) {
                Text("Add clips to start", color = Color(0xFFC9C9D1), fontSize = 14.sp)
            } else {
                // Video, fitted inside the canvas.
                val va = preview.videoAspect
                val canvasRatio = canvasW / canvasH
                val (vw, vh) = when {
                    va <= 0f -> canvasW to canvasH
                    va > canvasRatio -> canvasW to canvasW / va
                    else -> canvasH * va to canvasH
                }
                AndroidView(
                    factory = { ctx -> TextureView(ctx).also { preview.player.setVideoTextureView(it) } },
                    modifier = Modifier.size(vw, vh),
                )
                DisposableEffect(preview) {
                    onDispose { preview.player.clearVideoSurface() }
                }
                if (clip?.isImage == true) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        preview.imageBitmap?.let {
                            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            val t = preview.positionMs
            project.texts.forEach { item ->
                val selected = item.id == state.selectedTextId
                if (selected || (t >= item.startMs && t < item.endMs)) {
                    TextOverlayView(item, selected, state)
                }
            }
        }
    }
}

@Composable
private fun TextOverlayView(item: TextItem, selected: Boolean, state: EditorState) {
    val c = Vc.colors
    val density = LocalDensity.current
    val current by rememberUpdatedState(item)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val fontPx = item.size * wPx / 360f
        val fontSp = with(density) { fontPx.toSp() }
        Layout(
            content = {
                Box(
                    Modifier
                        .then(if (selected) Modifier.border(1.5.dp, Color.White) else Modifier)
                        .pointerInput(item.id) {
                            detectTapGestures(onTap = { state.selectText(current.id) })
                        }
                        .pointerInput(item.id, wPx, hPx) {
                            detectDragGestures(
                                onDragStart = { state.selectText(current.id) },
                                onDragEnd = { state.endGesture() },
                            ) { change, drag ->
                                change.consume()
                                state.updateText(current.id, "move") { t ->
                                    t.copy(
                                        x = (t.x + drag.x / wPx).coerceIn(0f, 1f),
                                        y = (t.y + drag.y / hPx).coerceIn(0f, 1f),
                                    )
                                }
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .semantics { contentDescription = "Text overlay: ${item.text}. Drag to move." },
                ) {
                    Text(
                        item.text.ifBlank { " " },
                        style = TextStyle(
                            color = Color(item.color),
                            fontSize = fontSp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            shadow = Shadow(Color(0x8C000000), Offset(0f, fontPx * 0.04f), fontPx * 0.08f),
                        ),
                    )
                }
                if (selected) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF2F2F5))
                            .pointerInput(item.id) { detectTapGestures(onTap = { state.deleteText(current.id) }) }
                            .semantics { contentDescription = "Delete text" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, null, tint = Color(0xFF0B0B0D), modifier = Modifier.size(16.dp)) }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF2F2F5))
                            .pointerInput(item.id, wPx) {
                                detectDragGestures(onDragEnd = { state.endGesture() }) { change, drag ->
                                    change.consume()
                                    val delta = (drag.x + drag.y) / 2f / (wPx / 360f)
                                    state.updateText(current.id, "size") { t -> t.copy(size = (t.size + delta * 0.6f).coerceIn(10f, 120f)) }
                                }
                            }
                            .semantics { contentDescription = "Resize text" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.OpenInFull, null, tint = Color(0xFF0B0B0D), modifier = Modifier.size(14.dp)) }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val maxW = (constraints.maxWidth * 0.9f).roundToInt() + with(density) { 12.dp.roundToPx() }
            val textP = measurables[0].measure(Constraints(maxWidth = maxW))
            val handles = measurables.drop(1).map { it.measure(Constraints()) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                val x = (item.x * constraints.maxWidth - textP.width / 2f).roundToInt()
                val y = (item.y * constraints.maxHeight - textP.height / 2f).roundToInt()
                textP.place(x, y)
                if (handles.size == 2) {
                    handles[0].place(x - handles[0].width / 2, y - handles[0].height / 2)
                    handles[1].place(x + textP.width - handles[1].width / 2, y + textP.height - handles[1].height / 2)
                }
            }
        }
    }
}
