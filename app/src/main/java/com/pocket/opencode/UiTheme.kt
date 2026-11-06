package com.pocket.opencode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Bg = Color(0xFF08070E)
val Card = Color(0xFF12111D)
val Card2 = Color(0xFF1A1828)
val Line = Color(0xFF2A2740)
val Accent = Color(0xFFA78BFA)
val AccentDim = Color(0xFF2B2347)
val Mint = Color(0xFF6EE7C8)
val TextMain = Color(0xFFF4F1FB)
val Muted = Color(0xFF9490A8)
val Dim = Color(0xFF6B6780)
val Danger = Color(0xFFFF7A8A)
val Warn = Color(0xFFFFC46B)
val CodeBg = Color(0xFF0B0A12)

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF16112B),
            primaryContainer = AccentDim,
            onPrimaryContainer = TextMain,
            secondary = Mint,
            onSecondary = Color(0xFF06140F),
            background = Bg,
            onBackground = TextMain,
            surface = Card,
            onSurface = TextMain,
            surfaceVariant = Card2,
            onSurfaceVariant = Muted,
            outline = Line,
            error = Danger,
            onError = Color(0xFF2A0A10)
        ),
        content = content
    )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Card,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = color,
        border = BorderStroke(1.dp, Line.copy(alpha = 0.7f))
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun StatusPill(label: String, tone: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(tone.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = tone,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ChipButton(
    label: String,
    modifier: Modifier = Modifier,
    tone: Color = Card2,
    accent: Boolean = false,
    maxWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    onClick: (() -> Unit)? = null
) {
    val background = if (accent) AccentDim else tone
    val textColor = if (accent) Accent else TextMain
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            label,
            color = textColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium,
            modifier = if (maxWidth != androidx.compose.ui.unit.Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier
        )
        if (onClick != null) {
            Text("⌄", color = textColor.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
fun SectionTitle(text: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text.uppercase(Locale.getDefault()),
            color = Dim,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyHint(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.size(6.dp))
        Text(
            body,
            color = Dim,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun KeyValueRow(
    key: String,
    value: String,
    mono: Boolean = false,
    valueColor: Color = TextMain
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(key, color = Dim, fontSize = 12.sp, modifier = Modifier.width(92.dp))
        Text(
            value,
            color = valueColor,
            fontSize = 12.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(1f)
        )
    }
}

fun relativeTime(millis: Long): String {
    if (millis <= 0L) return ""
    val diff = System.currentTimeMillis() - millis
    return when {
        diff < 60_000L -> "только что"
        diff < 3_600_000L -> "${diff / 60_000L} мин назад"
        diff < 86_400_000L -> "${diff / 3_600_000L} ч назад"
        diff < 172_800_000L -> "вчера"
        diff < 604_800_000L -> "${diff / 86_400_000L} дн назад"
        else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(millis))
    }
}

fun shortNumber(value: Long): String = when {
    value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}

fun shortPath(path: String, max: Int = 40): String {
    if (path.length <= max) return path
    return "…" + path.takeLast(max - 1)
}

fun statusTone(state: ConnState): Color = when (state) {
    ConnState.CONNECTED -> Mint
    ConnState.CONNECTING, ConnState.RECONNECTING -> Warn
    ConnState.ERROR -> Danger
    ConnState.IDLE -> Dim
}
