package com.pocket.opencode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSheet(onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val bridge = remember { DebugBridge.info() }
    var refresh by remember { mutableIntStateOf(0) }
    val events = remember(refresh) { DebugLog.recent(120).asReversed() }
    val token = bridge.optString("token")
    val masked = if (token.length > 10) token.take(6) + "…" + token.takeLast(4) else "—"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Card,
        contentColor = TextMain,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Line) }
    ) {
        Column(Modifier.fillMaxHeight(0.82f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.BugReport, null, tint = Accent)
                Spacer(Modifier.padding(horizontal = 6.dp))
                Column(Modifier.weight(1f)) {
                    Text("Отладка", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (bridge.optBoolean("enabled")) {
                            "127.0.0.1:${bridge.optInt("port")} · только loopback"
                        } else {
                            "мост не запущен"
                        },
                        color = if (bridge.optBoolean("enabled")) Mint else Warn,
                        fontSize = 10.5.sp
                    )
                }
                TextButton(onClick = { refresh++ }) {
                    Text("Обновить", color = Muted, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                color = Card2,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Токен", color = Dim, fontSize = 9.5.sp, letterSpacing = 1.2.sp)
                    Text(
                        masked,
                        color = TextMain,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = {
                    val diagnostics = JSONObject().apply {
                        put("bridge", JSONObject(bridge.toString()).put("token", "***"))
                        put("events", events.take(60).joinToString("\n") { it.toLine() })
                    }
                    clipboard.setText(AnnotatedString(diagnostics.toString(2)))
                }) {
                    Text("Копировать", color = Accent, fontSize = 11.5.sp)
                }
                TextButton(onClick = {
                    DebugLog.clear()
                    refresh++
                }) {
                    Text("Очистить", color = Danger, fontSize = 11.5.sp)
                }
                TextButton(onClick = { refresh++ }) {
                    Text("${events.size} событий", color = Muted, fontSize = 11.5.sp)
                }
            }

            HorizontalDivider(color = Line)
            if (events.isEmpty()) {
                Text(
                    "Пока пусто. Здесь появятся ошибки API, сервера и падения.",
                    color = Dim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(events) { event ->
                        EventRow(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: DebugEvent) {
    val time = remember(event.ts) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(event.ts))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time, color = Dim, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(
                event.kind,
                color = when (event.kind) {
                    "crash", "api", "server" -> Warn
                    else -> Accent
                },
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            event.message,
            color = TextMain,
            fontSize = 11.5.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        event.data["stack"]?.let {
            Text(
                it,
                color = Dim,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(color = Line.copy(alpha = 0.45f))
}
