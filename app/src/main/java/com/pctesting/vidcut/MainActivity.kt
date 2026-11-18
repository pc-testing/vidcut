package com.pctesting.vidcut

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pctesting.vidcut.data.ProjectStore
import com.pctesting.vidcut.data.Settings
import com.pctesting.vidcut.data.ThemeMode
import com.pctesting.vidcut.ui.editor.EditorScreen
import com.pctesting.vidcut.ui.home.HomeScreen
import com.pctesting.vidcut.ui.theme.Vc
import com.pctesting.vidcut.ui.theme.VidCutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = Settings(this)
        val store = ProjectStore(this)

        setContent {
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            VidCutTheme(dark = dark) {
                var openProjectId by rememberSaveable { mutableStateOf<String?>(null) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Vc.colors.background)
                        .safeDrawingPadding(),
                ) {
                    val id = openProjectId
                    if (id == null) {
                        HomeScreen(
                            store = store,
                            themeMode = themeMode,
                            onThemeChange = { themeMode = it; settings.themeMode = it },
                            onOpen = { openProjectId = it },
                        )
                    } else {
                        EditorScreen(store = store, projectId = id, onClose = { openProjectId = null })
                    }
                }
            }
        }
    }
}
