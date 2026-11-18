package com.pctesting.vidcut.ui.home

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pctesting.vidcut.BuildConfig
import com.pctesting.vidcut.data.Clip
import com.pctesting.vidcut.data.Project
import com.pctesting.vidcut.data.ProjectStore
import com.pctesting.vidcut.data.ThemeMode
import com.pctesting.vidcut.media.rememberThumbnail
import com.pctesting.vidcut.ui.common.IconBtn
import com.pctesting.vidcut.ui.common.SheetOverlay
import com.pctesting.vidcut.ui.common.formatDuration
import com.pctesting.vidcut.ui.theme.DarkColors
import com.pctesting.vidcut.ui.theme.LightColors
import com.pctesting.vidcut.ui.theme.Vc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    store: ProjectStore,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onOpen: (String) -> Unit,
) {
    val c = Vc.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<Project>?>(null) }
    var importing by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Project?>(null) }
    var deleting by remember { mutableStateOf<Project?>(null) }

    fun refresh() {
        scope.launch { projects = withContext(Dispatchers.IO) { store.list() } }
    }
    LaunchedEffect(Unit) { refresh() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val clips = mutableListOf<Clip>()
            uris.forEachIndexed { i, uri ->
                importing = "Importing ${i + 1} of ${uris.size}…"
                withContext(Dispatchers.IO) { store.importVisual(uri) }?.let { clips += it }
            }
            importing = null
            if (clips.isEmpty()) {
                Toast.makeText(context, "Couldn't open those files", Toast.LENGTH_LONG).show()
            } else {
                if (clips.size < uris.size) {
                    Toast.makeText(context, "Some files couldn't be opened and were skipped", Toast.LENGTH_LONG).show()
                }
                val p = withContext(Dispatchers.IO) { store.create(store.nextName(), clips) }
                onOpen(p.id)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("VidCut", color = c.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconBtn(Icons.Outlined.Settings, "Settings", onClick = { showSettings = true })
                }
                Spacer(Modifier.height(20.dp))
                NewProjectButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                })
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent projects", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    val n = projects?.size ?: 0
                    if (n > 0) Text(if (n == 1) "1 project" else "$n projects", color = c.textSecondary, fontSize = 13.sp)
                }
            }
            val list = projects
            if (list != null && list.isEmpty()) {
                item {
                    Text(
                        "No projects yet. Tap New project to pick videos and photos.",
                        color = c.textSecondary, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (list != null) {
                items(list, key = { it.id }) { p ->
                    ProjectRow(
                        project = p,
                        onOpen = { onOpen(p.id) },
                        onRename = { renaming = p },
                        onDuplicate = { scope.launch { withContext(Dispatchers.IO) { store.duplicate(p.id) }; refresh() } },
                        onDelete = { deleting = p },
                    )
                }
                if (list.isNotEmpty()) {
                    item {
                        Text(
                            "Long-press a project to rename, duplicate or delete",
                            color = c.textTertiary, fontSize = 12.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        )
                    }
                }
            }
        }

        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsSheet(themeMode = themeMode, onThemeChange = onThemeChange, onDismiss = { showSettings = false })
        }

        importing?.let { msg ->
            Box(Modifier.fillMaxSize().background(c.scrim).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = c.accent)
                    Spacer(Modifier.height(16.dp))
                    Text(msg, color = c.text, fontSize = 15.sp)
                }
            }
        }
    }

    renaming?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename project", color = c.text) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = c.text, unfocusedTextColor = c.text,
                        focusedBorderColor = c.accent, unfocusedBorderColor = c.sliderTrack,
                        focusedLabelColor = c.accentText, unfocusedLabelColor = c.textSecondary,
                        cursorColor = c.accentText,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = name.trim().ifEmpty { p.name }
                    renaming = null
                    scope.launch { withContext(Dispatchers.IO) { store.rename(p.id, newName) }; refresh() }
                }) { Text("Save", color = c.accentText) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = c.textSecondary) } },
            containerColor = c.surface,
        )
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${p.name}”?", color = c.text) },
            text = { Text("This removes the project from VidCut. Your original videos and photos stay in your gallery.", color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { withContext(Dispatchers.IO) { store.delete(p.id) }; refresh() }
                }) { Text("Delete", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = c.textSecondary) } },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun NewProjectButton(onClick: () -> Unit) {
    val c = Vc.colors
    Column(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("New project", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Import videos and photos from your gallery", color = c.textSecondary, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(
    project: Project,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Vc.colors
    var menu by remember { mutableStateOf(false) }
    val first = project.clips.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true }, onLongClickLabel = "Project options")
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(c.thumbPlaceholder)) {
            if (first != null) {
                val thumb by rememberThumbnail(first.path, first.isImage, first.trimStartMs, 192)
                thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Text(
                formatDuration(project.durationMs),
                color = DarkColors.text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(DarkColors.background)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(project.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val edited = DateUtils.getRelativeTimeSpanString(project.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            Text("${project.aspect.label} · Edited $edited", color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconBtn(Icons.Filled.MoreVert, "More options for ${project.name}", onClick = { menu = true }, tint = c.textSecondary, size = 20.dp)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surface) {
                DropdownMenuItem(text = { Text("Rename", color = c.text) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Duplicate", color = c.text) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Delete", color = c.danger) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun SettingsSheet(themeMode: ThemeMode, onThemeChange: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    val c = Vc.colors
    SheetOverlay(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconBtn(Icons.Filled.Close, "Close", onClick = onDismiss)
        }
        Spacer(Modifier.height(12.dp))
        Text("Theme", color = c.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeMode.entries.forEach { mode ->
                ThemeCard(mode = mode, selected = mode == themeMode, onClick = { onThemeChange(mode) }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Changes apply straight away, on every screen.", color = c.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text("Version", color = c.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", color = c.text, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ThemeCard(mode: ThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Vc.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accentText else c.border, RoundedCornerShape(14.dp))
            .background(c.field)
            .clickable(role = Role.RadioButton, onClickLabel = "Use ${mode.label} theme", onClick = onClick)
            .padding(top = 10.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(10.dp)
        when (mode) {
            ThemeMode.DARK -> MiniPreview(DarkColors.background, DarkColors.thumbPlaceholder, DarkColors.surface, shape)
            ThemeMode.LIGHT -> MiniPreview(LightColors.surface, LightColors.thumbPlaceholder, LightColors.border, shape)
            ThemeMode.SYSTEM -> Row(Modifier.size(64.dp, 96.dp).clip(shape).border(1.dp, c.border, shape)) {
                Box(Modifier.weight(1f).height(96.dp).background(LightColors.surface))
                Box(Modifier.weight(1f).height(96.dp).background(DarkColors.background))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            mode.label + if (selected) " ✓" else "",
            color = if (selected) c.text else c.icon,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun MiniPreview(bg: androidx.compose.ui.graphics.Color, block: androidx.compose.ui.graphics.Color, bar: androidx.compose.ui.graphics.Color, shape: RoundedCornerShape) {
    Column(
        Modifier.size(64.dp, 96.dp).clip(shape).background(bg).border(1.dp, Vc.colors.border, shape).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(4.dp)).background(block))
        Box(Modifier.fillMaxWidth(0.7f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Vc.colors.accent))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(bar))
    }
}
