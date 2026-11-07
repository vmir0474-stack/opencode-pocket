package com.pocket.opencode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------------------------------------------------ */
/*  Палитра: тёплый графит и сигнальный янтарь.                        */
/*  Никаких кислотно-зелёных и фиолетовых градиентов — только тёплый  */
/*  металл, бумага и спокойный холодный код.                           */
/* ------------------------------------------------------------------ */

val Bg = Color(0xFF0B0D11)
val Card = Color(0xFF12151C)
val Card2 = Color(0xFF1A1F28)
val Line = Color(0xFF2B313D)
val Accent = Color(0xFFF2B45E)
val Accent2 = Color(0xFFE07A4F)
val AccentDim = Color(0xFF2A2116)
val Mint = Color(0xFF6FD6B8)
val TextMain = Color(0xFFF3EFE7)
val Muted = Color(0xFFACA698)
val Dim = Color(0xFF777168)
val Danger = Color(0xFFFF7E8A)
val Warn = Color(0xFFFFA36B)
val CodeBg = Color(0xFF0D1015)
val Sky = Color(0xFF7FB7FF)

private val PocketColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF241505),
    primaryContainer = AccentDim,
    onPrimaryContainer = TextMain,
    secondary = Mint,
    onSecondary = Color(0xFF06140F),
    secondaryContainer = Color(0xFF13241F),
    onSecondaryContainer = TextMain,
    tertiary = Sky,
    onTertiary = Color(0xFF061321),
    background = Bg,
    onBackground = TextMain,
    surface = Card,
    onSurface = TextMain,
    surfaceVariant = Card2,
    onSurfaceVariant = Muted,
    surfaceContainer = Card,
    surfaceContainerHigh = Card2,
    outline = Line,
    outlineVariant = Line.copy(alpha = 0.55f),
    error = Danger,
    onError = Color(0xFF2A0A10)
)

private val PocketTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp
    )
)

private val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PocketColorScheme,
        typography = PocketTypography,
        shapes = PocketShapes,
        content = content
    )
}

/* ------------------------------------------------------------------ */
/*  Базовые поверхности и строки                                       */
/* ------------------------------------------------------------------ */

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Card,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = color,
        border = BorderStroke(1.dp, Line.copy(alpha = 0.72f))
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = TextMain,
                fontSize = 24.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.size(2.dp))
                Text(
                    subtitle,
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = actions
        )
    }
}

@Composable
fun StatusPill(label: String, tone: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(tone.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = tone,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ChipButton(
    label: String,
    modifier: Modifier = Modifier,
    tone: Color = Card2,
    accent: Boolean = false,
    maxWidth: Dp = Dp.Unspecified,
    onClick: (() -> Unit)? = null
) {
    val background = if (accent) Accent.copy(alpha = 0.14f) else tone
    val textColor = if (accent) Accent else TextMain
    val border = if (accent) {
        BorderStroke(1.dp, Accent.copy(alpha = 0.28f))
    } else {
        BorderStroke(1.dp, Line.copy(alpha = 0.55f))
    }
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            label,
            color = textColor,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium,
            modifier = if (maxWidth != Dp.Unspecified) {
                Modifier.widthIn(max = maxWidth)
            } else {
                Modifier
            }
        )
        if (onClick != null) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.55f),
                modifier = Modifier.size(14.dp)
            )
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
            text,
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyHint(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(6.dp))
        Text(
            body,
            color = Dim,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            key,
            color = Dim,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.width(102.dp)
        )
        Text(
            value,
            color = valueColor,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MetricPill(text: String, tint: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = tint,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.11f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun PocketIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Muted
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(44.dp)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else Dim.copy(alpha = 0.5f),
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
fun pocketFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent.copy(alpha = 0.62f),
    unfocusedBorderColor = Line,
    disabledBorderColor = Line.copy(alpha = 0.5f),
    focusedContainerColor = Card2.copy(alpha = 0.62f),
    unfocusedContainerColor = Card2.copy(alpha = 0.38f),
    disabledContainerColor = Card2.copy(alpha = 0.25f),
    cursorColor = Accent,
    focusedTextColor = TextMain,
    unfocusedTextColor = TextMain,
    disabledTextColor = Muted,
    focusedLabelColor = Accent,
    unfocusedLabelColor = Muted,
    focusedPlaceholderColor = Dim,
    unfocusedPlaceholderColor = Dim
)

@Composable
fun PocketSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Search
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = Dim, fontSize = 13.sp) },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = Dim,
                modifier = Modifier.size(19.dp)
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Очистить",
                        tint = Muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = TextStyle(color = TextMain, fontSize = 14.sp, lineHeight = 19.sp),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        colors = pocketFieldColors()
    )
}

/* ------------------------------------------------------------------ */
/*  Утилиты                                                            */
/* ------------------------------------------------------------------ */

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
