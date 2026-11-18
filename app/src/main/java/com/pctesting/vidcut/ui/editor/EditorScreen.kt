package com.pctesting.vidcut.ui.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pctesting.vidcut.data.Clip
import com.pctesting.vidcut.data.ProjectStore
import com.pctesting.vidcut.media.Exporter
import com.pctesting.vidcut.media.PreviewPlayer
import com.pctesting.vidcut.media.rememberThumbnail
import com.pctesting.vidcut.ui.common.AccentButton
import com.pctesting.vidcut.ui.common.IconBtn
import com.pctesting.vidcut.ui.common.OutlineButton
import com.pctesting.vidcut.ui.common.SheetOverlay
import com.pctesting.vidcut.ui.common.formatDuration
import com.pctesting.vidcut.ui.common.formatTimecode
import com.pctesting.vidcut.ui.theme.Vc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun EditorScreen(store: ProjectStore, projectId: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var state by remember(projectId) { mutableStateOf<EditorState?>(null) }
    LaunchedEffect(projectId) {
        val p = withContext(Dispatchers.IO) { store.load(projectId) }
        if (p == null) onClose() else state = EditorState(p, store, scope)
    }
    val s = state
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Vc.colors.accent)
        }
    } else {
        EditorContent(s, store, onClose)
    }
}

private sealed interface ExportStage {
    data object Options : ExportStage
    data class Running(val percent: Int) : ExportStage
    data class Done(val uri: Uri) : ExportStage
    data class Failed(val message: String) : ExportStage
}

@Composable
private fun EditorContent(s: EditorState, store: ProjectStore, onClose: () -> Unit) {
    val c = Vc.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview = remember { PreviewPlayer(context) }
    val exporter = remember { Exporter(context) }
    var importing by remember { mutableStateOf<String?>(null) }
    var exportStage by remember { mutableStateOf<ExportStage?>(null) }
    var resolution by remember { mutableIntStateOf(1080) }

    DisposableEffect(Unit) {
        onDispose {
            exporter.cancel()
            preview.release()
            // Make sure the latest edits are on disk even if the delayed save didn't run yet.
            runCatching { store.save(s.project) }
        }
    }
    LaunchedEffect(s.project) { preview.setProject(s.project) }
    LaunchedEffect(preview.isPlaying) {
        while (preview.isPlaying) {
            preview.tick()
            delay(33)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        preview.pause()
        runCatching { store.save(s.project) }
    }

    fun close() {
        preview.pause()
        scope.launch {
            s.saveNow()
            onClose()
        }
    }

    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val clips = mutableListOf<Clip>()
            uris.forEachIndexed { i, uri ->
                importing = "Importing ${i + 1} of ${uris.size}…"
                withContext(Dispatchers.IO) { store.importVisual(uri) }?.let { clips += it }
            }
            importing = null
            if (clips.size < uris.size) Toast.makeText(context, "Some files couldn't be opened and were skipped", Toast.LENGTH_LONG).show()
            s.addClips(clips, preview.positionMs)
        }
    }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = "Adding music…"
            val m = withContext(Dispatchers.IO) { store.importMusic(uri) }
            importing = null
            if (m == null) Toast.makeText(context, "Couldn't open that audio file", Toast.LENGTH_LONG).show()
            else s.setMusic(m)
        }
    }

    fun startExport() {
        preview.pause()
        exportStage = ExportStage.Running(0)
        exporter.start(s.project, resolution, object : Exporter.Listener {
            override fun onProgress(percent: Int) {
                if (exportStage is ExportStage.Running) exportStage = ExportStage.Running(percent)
            }

            override fun onCompleted(file: File) {
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        runCatching { Exporter.saveToGallery(context, file) }.getOrNull().also { file.delete() }
                    }
                    exportStage = if (uri != null) ExportStage.Done(uri) else ExportStage.Failed("The video was made but couldn't be saved to your gallery.")
                }
            }

            override fun onError(message: String) {
                exportStage = ExportStage.Failed(message)
            }
        })
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startExport()
        else exportStage = ExportStage.Failed("VidCut needs storage permission to save videos to your gallery on this version of Android.")
    }
    fun requestExport() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else startExport()
    }

    BackHandler {
        when {
            exportStage is ExportStage.Running -> { exporter.cancel(); exportStage = null }
            exportStage != null -> exportStage = null
            s.panel == Panel.Speed || s.panel == Panel.Volume -> { s.endGesture(); s.panel = Panel.ClipTools }
            s.panel == Panel.Text -> s.selectText(null)
            s.panel != Panel.None || s.selectedClipId != null -> s.selectClip(null)
            else -> close()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                name = s.project.name,
                saved = s.saved,
                onClose = { close() },
                onExport = {
                    if (s.project.clips.isEmpty()) Toast.makeText(context, "Add a clip first", Toast.LENGTH_SHORT).show()
                    else { preview.pause(); exportStage = ExportStage.Options }
                },
            )
            PreviewArea(s, preview, Modifier.fillMaxWidth().weight(1f))
            PlaybackRow(s, preview)

            val panel = s.panel
            if (panel != Panel.Text) {
                Timeline(s, preview, onAddClips = {
                    addPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                })
            }
            val clip = s.selectedClip
            val text = s.selectedText
            when {
                panel == Panel.Speed && clip != null -> SpeedPanel(clip, s)
                panel == Panel.Volume && clip != null -> VolumePanel(clip, s)
                panel == Panel.Text && text != null -> Box(Modifier.heightIn(max = 380.dp)) { TextPanel(text, s, s.project.durationMs) }
                panel == Panel.Audio -> AudioPanel(s, onPickMusic = { musicPicker.launch(arrayOf("audio/*")) })
                panel == Panel.Canvas -> CanvasPanel(s)
                else -> {}
            }
            if (panel != Panel.Text) {
                if (clip != null) {
                    ClipToolbar(s, panel, onSplit = {
                        if (!s.splitAt(preview.positionMs)) {
                            Toast.makeText(context, "Move the playhead inside the clip to split it", Toast.LENGTH_SHORT).show()
                        }
                    })
                } else {
                    MainToolbar(
                        panel = panel,
                        onEdit = {
                            val i = s.clipIndexAt(preview.positionMs)
                            if (i >= 0) s.selectClip(s.project.clips[i].id)
                            else Toast.makeText(context, "Add a clip first", Toast.LENGTH_SHORT).show()
                        },
                        onAudio = { s.selectClip(null); s.panel = if (panel == Panel.Audio) Panel.None else Panel.Audio },
                        onText = { preview.pause(); s.addText(preview.positionMs) },
                        onSpeed = {
                            val i = s.clipIndexAt(preview.positionMs)
                            if (i >= 0) { s.selectClip(s.project.clips[i].id); s.panel = Panel.Speed }
                            else Toast.makeText(context, "Add a clip first", Toast.LENGTH_SHORT).show()
                        },
                        onCanvas = { s.selectClip(null); s.panel = if (panel == Panel.Canvas) Panel.None else Panel.Canvas },
                    )
                }
            }
        }

        when (val st = exportStage) {
            null -> {}
            ExportStage.Options -> ExportOptionsSheet(
                s = s,
                resolution = resolution,
                onResolution = { resolution = it },
                onExport = { requestExport() },
                onDismiss = { exportStage = null },
            )
            is ExportStage.Running -> ExportProgressScreen(s, st.percent, resolution, onCancel = { exporter.cancel(); exportStage = null })
            is ExportStage.Done -> ExportResultScreen(
                success = true,
                message = "Saved to your gallery in Movies/VidCut.",
                onShare = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("video/mp4")
                        .putExtra(Intent.EXTRA_STREAM, st.uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, "Share video"))
                },
                onBack = { exportStage = null },
            )
            is ExportStage.Failed -> ExportResultScreen(success = false, message = st.message, onShare = null, onBack = { exportStage = null })
        }

        importing?.let { msg ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                contentAlignment = Alignment.Center,
            ) {
                Column(Modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = c.accent)
                    Spacer(Modifier.height(16.dp))
                    Text(msg, color = c.text, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun TopBar(name: String, saved: Boolean, onClose: () -> Unit, onExport: () -> Unit) {
    val c = Vc.colors
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.Filled.Close, "Close project", onClick = onClose, tint = c.text)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (saved) "Saved" else "Saving…", color = c.textSecondary, fontSize = 11.sp)
        }
        AccentButton("Export", onClick = onExport, height = 36.dp)
    }
}

@Composable
private fun PlaybackRow(s: EditorState, preview: PreviewPlayer) {
    val c = Vc.colors
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${formatTimecode(preview.positionMs)} / ${formatTimecode(s.project.durationMs)}",
            color = c.icon, fontSize = 12.sp, modifier = Modifier.weight(1f),
        )
        IconBtn(
            if (preview.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (preview.isPlaying) "Pause" else "Play",
            onClick = { if (preview.isPlaying) preview.pause() else preview.play() },
            tint = c.text, size = 30.dp,
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            IconBtn(Icons.AutoMirrored.Filled.Undo, "Undo", onClick = { s.undo() }, enabled = s.canUndo, tint = c.text, size = 20.dp)
            IconBtn(Icons.AutoMirrored.Filled.Redo, "Redo", onClick = { s.redo() }, enabled = s.canRedo, tint = c.text, size = 20.dp)
        }
    }
}

@Composable
private fun ToolbarContainer(content: @Composable RowScope.() -> Unit) {
    val c = Vc.colors
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(Modifier.fillMaxWidth().height(76.dp).background(c.toolbar), content = content)
    }
}

@Composable
private fun RowScope.ToolItem(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val c = Vc.colors
    val color = if (active) c.accentText else c.icon
    Column(
        Modifier
            .weight(1f)
            .height(76.dp)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun MainToolbar(
    panel: Panel,
    onEdit: () -> Unit,
    onAudio: () -> Unit,
    onText: () -> Unit,
    onSpeed: () -> Unit,
    onCanvas: () -> Unit,
) {
    ToolbarContainer {
        ToolItem(Icons.Filled.ContentCut, "Edit", onClick = onEdit)
        ToolItem(Icons.Filled.MusicNote, "Audio", active = panel == Panel.Audio, onClick = onAudio)
        ToolItem(Icons.Filled.TextFields, "Text", onClick = onText)
        ToolItem(Icons.Filled.Speed, "Speed", onClick = onSpeed)
        ToolItem(Icons.Filled.AspectRatio, "Canvas", active = panel == Panel.Canvas, onClick = onCanvas)
    }
}

@Composable
private fun ClipToolbar(s: EditorState, panel: Panel, onSplit: () -> Unit) {
    val c = Vc.colors
    ToolbarContainer {
        Box(Modifier.weight(1f).height(76.dp), contentAlignment = Alignment.Center) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back to main tools", onClick = { s.selectClip(null) }, tint = c.icon)
        }
        ToolItem(Icons.Filled.ContentCut, "Split", onClick = onSplit)
        ToolItem(Icons.Filled.Speed, "Speed", active = panel == Panel.Speed) {
            s.endGesture()
            s.panel = if (panel == Panel.Speed) Panel.ClipTools else Panel.Speed
        }
        ToolItem(Icons.AutoMirrored.Filled.VolumeUp, "Volume", active = panel == Panel.Volume) {
            s.endGesture()
            s.panel = if (panel == Panel.Volume) Panel.ClipTools else Panel.Volume
        }
        ToolItem(Icons.Filled.Delete, "Delete") { s.selectedClipId?.let { s.deleteClip(it) } }
    }
}

@Composable
private fun ExportOptionsSheet(
    s: EditorState,
    resolution: Int,
    onResolution: (Int) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Vc.colors
    val (w, h) = s.project.aspect.outputSize(resolution)
    SheetOverlay(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Export video", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconBtn(Icons.Filled.Close, "Close", onClick = onDismiss)
        }
        Spacer(Modifier.height(12.dp))
        Text("Resolution", color = c.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.field).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(720, 1080).forEach { r ->
                val selected = r == resolution
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) c.accent else c.field)
                        .clickable(role = Role.RadioButton) { onResolution(r) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${r}p", fontSize = 15.sp,
                        color = if (selected) c.onAccent else c.icon,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.field)) {
            InfoRow("Format", "MP4 (H.264)")
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            InfoRow("Canvas", "${s.project.aspect.label} · $w × $h")
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            InfoRow("Length", formatDuration(s.project.durationMs))
        }
        Spacer(Modifier.height(20.dp))
        AccentButton("Export to gallery", onClick = onExport, icon = Icons.Filled.FileUpload, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = Vc.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = c.text, fontSize = 14.sp)
    }
}

@Composable
private fun ExportProgressScreen(s: EditorState, percent: Int, resolution: Int, onCancel: () -> Unit) {
    val c = Vc.colors
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.4f))
        val first = s.project.clips.firstOrNull()
        Box(
            Modifier
                .fillMaxWidth(
                    when (s.project.aspect) {
                        com.pctesting.vidcut.data.AspectRatio.PORTRAIT -> 0.5f
                        com.pctesting.vidcut.data.AspectRatio.SQUARE -> 0.75f
                        com.pctesting.vidcut.data.AspectRatio.LANDSCAPE -> 1f
                    },
                )
                .aspectRatio(s.project.aspect.ratio)
                .clip(RoundedCornerShape(12.dp))
                .background(c.thumbPlaceholder),
        ) {
            if (first != null) {
                val thumb by rememberThumbnail(first.path, first.isImage, first.trimStartMs, 512)
                thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Exporting…", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (percent >= 0) Text("$percent%", color = c.accentText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        if (percent >= 0) {
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = c.accent, trackColor = c.sliderTrack, strokeCap = StrokeCap.Round,
                gapSize = 0.dp, drawStopIndicator = {},
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = c.accent, trackColor = c.sliderTrack,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("${resolution}p · MP4 · Keep VidCut open until it finishes", color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        OutlineButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ExportResultScreen(success: Boolean, message: String, onShare: (() -> Unit)?, onBack: () -> Unit) {
    val c = Vc.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (success) c.accentText else c.danger,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(if (success) "Saved to gallery" else "Export didn't finish", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = c.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        if (onShare != null) {
            AccentButton("Share", onClick = onShare, icon = Icons.Filled.Share, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
        OutlineButton("Back to edit", onClick = onBack, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.width(1.dp))
    }
}
