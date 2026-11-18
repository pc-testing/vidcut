package com.pctesting.vidcut.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pctesting.vidcut.ui.theme.Vc
import java.util.Locale

fun formatDuration(ms: Long): String {
    val totalSec = (ms + 500) / 1000
    return "%d:%02d".format(Locale.US, totalSec / 60, totalSec % 60)
}

fun formatTimecode(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / 1000
    return "%02d:%02d".format(Locale.US, totalSec / 60, totalSec % 60)
}

fun formatPrecise(ms: Long): String {
    val t = ms.coerceAtLeast(0L)
    return "%02d:%02d.%d".format(Locale.US, t / 60000, (t / 1000) % 60, (t % 1000) / 100)
}

fun formatSeconds(ms: Long): String = "%.1fs".format(Locale.US, ms / 1000f)

fun formatSpeed(s: Float): String {
    val r = Math.round(s * 10) / 10f
    return if (r == r.toInt().toFloat()) "${r.toInt()}x" else "${r}x"
}

/** 44dp round icon button. */
@Composable
fun IconBtn(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Vc.colors.icon,
    enabled: Boolean = true,
    size: Dp = 22.dp,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else Vc.colors.textTertiary, modifier = Modifier.size(size))
    }
}

@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 52.dp) {
    val c = Vc.colors
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(c.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(20.dp))
            Box(Modifier.width(8.dp))
        }
        Text(text, color = c.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Vc.colors.text) {
    val c = Vc.colors
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, c.sliderTrack, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Box(Modifier.width(8.dp))
        }
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Round accent check button used to close tool panels. */
@Composable
fun DoneButton(onClick: () -> Unit, contentDescription: String = "Done") {
    val c = Vc.colors
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(c.accent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, contentDescription = contentDescription, tint = c.onAccent, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun VcSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val c = Vc.colors
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = c.text,
            activeTrackColor = c.accent,
            inactiveTrackColor = c.sliderTrack,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

/** A bottom sheet drawn inside the screen, with a scrim that closes it when tapped. */
@Composable
fun SheetOverlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Vc.colors
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Close",
                    onClick = onDismiss,
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(c.surface)
                // Swallow taps so they don't reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.sliderTrack),
            )
            Box(Modifier.height(10.dp))
            content()
        }
    }
}
