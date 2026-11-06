package com.pocket.opencode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private sealed interface ViewerRequest {
    data class File(val path: String) : ViewerRequest
    data class Diff(val path: String) : ViewerRequest
}

@Composable
fun ProjectScreen(vm: PocketViewModel) {
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Проект", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    vm.directoryLabel.ifBlank { "каталог не определён" },
                    color = Dim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { vm.refreshProject() }, enabled = !vm.projectBusy) {
                if (vm.projectBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Accent,
                        strokeWidth = 1.6.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, "Обновить", tint = Muted)
                }
            }
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ВЕТКА", color = Dim, fontSize = 9.5.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    vm.vcsSummary?.current?.ifBlank { "—" } ?: "—",
                    color = TextMain,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f)
                )
                vm.vcsSummary?.provider?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = Accent,
                        fontSize = 9.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(AccentDim)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
            if (vm.vcsFiles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val additions = vm.vcsFiles.sumOf { it.additions }
                    val deletions = vm.vcsFiles.sumOf { it.deletions }
                    Text(
                        "${vm.vcsFiles.size} изм.",
                        color = TextMain,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Card2)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        "+$additions",
                        color = Mint,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Mint.copy(alpha = 0.10f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        "-$deletions",
                        color = Danger,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Danger.copy(alpha = 0.10f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (vm.vcsFiles.isNotEmpty()) {
            SectionTitle("Изменения", trailing = {
                Text("${vm.vcsFiles.size}", color = Dim, fontSize = 10.sp)
            })
            Panel(color = Card) {
                vm.vcsFiles.take(40).forEachIndexed { index, file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewer = ViewerRequest.Diff(file.path) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (file.status) {
                                "added" -> "A"
                                "deleted" -> "D"
                                else -> "M"
                            },
                            color = when (file.status) {
                                "added" -> Mint
                                "deleted" -> Danger
                                else -> Warn
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(18.dp)
                        )
                        Text(
                            file.path,
                            color = TextMain,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("+${file.additions}", color = Mint, fontSize = 9.5.sp)
                        Spacer(Modifier.width(5.dp))
                        Text("-${file.deletions}", color = Danger, fontSize = 9.5.sp)
                    }
                    if (index != vm.vcsFiles.take(40).lastIndex) {
                        HorizontalDivider(color = Line.copy(alpha = 0.4f))
                    }
                }
            }
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ФАЙЛЫ",
                    color = Dim,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    vm.projectPath,
                    color = Accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (vm.projectPath != ".") {
                    IconButton(
                        onClick = {
                            val parent = vm.projectPath.substringBeforeLast('/', "")
                            vm.browseProject(parent.ifBlank { "." })
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, "Вверх", tint = Muted)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (!vm.projectLoaded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = Accent,
                        strokeWidth = 1.4.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Загрузка каталога…", color = Muted, fontSize = 12.sp)
                }
            } else if (vm.projectFiles.isEmpty()) {
                EmptyHint("Каталог пуст", "Агент работает в директории, где запущен сервер.")
            } else {
                vm.projectFiles
                    .filter { it.path != "." && it.path != ".." }
                    .forEachIndexed { index, entry ->
                        val name = entry.path.substringAfterLast('/')
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (entry.type == "directory") {
                                        vm.browseProject(entry.path)
                                    } else {
                                        viewer = ViewerRequest.File(entry.path)
                                    }
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (entry.type == "directory") "▸" else "·",
                                color = if (entry.type == "directory") Accent else Dim,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(18.dp)
                            )
                            Text(
                                name,
                                color = if (entry.type == "directory") TextMain else TextMain.copy(alpha = 0.86f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (entry.type == "directory") {
                                Text("папка", color = Dim, fontSize = 9.sp)
                            }
                        }
                        if (index != vm.projectFiles.size - 1) {
                            HorizontalDivider(color = Line.copy(alpha = 0.35f))
                        }
                    }
            }
        }

        Text(
            "Нажми на изменённый файл, чтобы увидеть diff. Файлы открываются только для чтения.",
            color = Dim,
            fontSize = 10.5.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(6.dp))
    }

    viewer?.let { request ->
        ViewerDialog(vm, request) { viewer = null }
    }
}

@Composable
private fun ViewerDialog(vm: PocketViewModel, request: ViewerRequest, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var content by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(request) {
        loading = true
        error = null
        note = null
        when (request) {
            is ViewerRequest.File -> {
                vm.readFile(request.path)
                    .onSuccess { raw ->
                        content = if (raw.length > 200_000) {
                            note = "Показаны первые 200 000 символов из ${raw.length}"
                            raw.take(200_000)
                        } else {
                            raw
                        }
                        if (raw.contains('\u0000')) {
                            content = ""
                            note = "Похоже, это бинарный файл (${raw.length} байт)."
                        }
                    }
                    .onFailure { error = it.message ?: "Не удалось прочитать файл" }
            }

            is ViewerRequest.Diff -> {
                vm.loadDiff(request.path)
                    .onSuccess { diff ->
                        if (diff == null) {
                            note = "Файл не найден в рабочем diff."
                        } else {
                            content = diff.patch
                            if (diff.more) note = "Diff обрезан сервером."
                        }
                    }
                    .onFailure { error = it.message ?: "Не удалось загрузить diff" }
            }
        }
        loading = false
    }

    val path = when (request) {
        is ViewerRequest.File -> request.path
        is ViewerRequest.Diff -> request.path
    }
    val isDiff = request is ViewerRequest.Diff

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(10.dp),
            shape = RoundedCornerShape(22.dp),
            color = Card,
            border = BorderStroke(1.dp, Line)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isDiff) "Diff" else "Файл",
                            color = Accent,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            path,
                            color = TextMain,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (content.isNotBlank()) {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(content)) }) {
                            Text("Копировать", color = Muted, fontSize = 11.sp)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Close, "Закрыть", tint = Muted)
                    }
                }
                HorizontalDivider(color = Line)

                when {
                    loading -> Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Accent,
                            strokeWidth = 1.6.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Загрузка…", color = Muted, fontSize = 12.sp)
                    }

                    error != null -> Text(
                        error.orEmpty(),
                        color = Danger,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(18.dp)
                    )

                    content.isBlank() -> Column(Modifier.padding(18.dp)) {
                        Text(note ?: "Пустой файл", color = Muted, fontSize = 12.sp)
                    }

                    else -> Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        note?.let {
                            Text(
                                it,
                                color = Warn,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        SelectionContainer {
                            Text(
                                if (isDiff) diffAnnotated(content) else AnnotatedString(content),
                                color = Color(0xFFCFCBE0),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
