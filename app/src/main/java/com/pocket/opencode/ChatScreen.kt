package com.pocket.opencode

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: PocketViewModel) {
    var showSessions by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showAgents by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ContextBar(
            vm = vm,
            onSessions = { showSessions = true },
            onModels = { showModels = true },
            onAgents = { showAgents = true }
        )

        if (vm.isEmbedded && !vm.hasAllFilesAccess && !vm.onboardingDismissed) {
            WorkspaceOnboarding(vm)
        }

        Box(Modifier.weight(1f)) {
            Transcript(vm)
        }

        if (vm.status != ConnState.CONNECTED) {
            ConnectionBanner(vm)
        } else if (vm.statusDetail.isNotBlank()) {
            Text(
                vm.statusDetail,
                color = Muted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Composer(vm)
    }

    if (showSessions) {
        SessionsSheet(vm) { showSessions = false }
    }
    if (showModels) {
        ModelsSheet(vm) { showModels = false }
    }
    if (showAgents) {
        AgentsSheet(vm) { showAgents = false }
    }
}

@Composable
private fun ContextBar(
    vm: PocketViewModel,
    onSessions: () -> Unit,
    onModels: () -> Unit,
    onAgents: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChipButton(
                label = vm.sessionLabel,
                accent = true,
                maxWidth = 220.dp,
                onClick = onSessions
            )
            ChipButton(label = vm.modelLabel, maxWidth = 200.dp, onClick = onModels)
            vm.variantLabel?.let {
                ChipButton(label = it, tone = AccentDim, onClick = onModels)
            }
            ChipButton(label = vm.agentLabel, onClick = onAgents)
            if (vm.usageLabel.isNotBlank()) {
                MetricPill(vm.usageLabel, Mint)
            }
        }
        Spacer(Modifier.width(6.dp))
        val canStart = vm.status == ConnState.CONNECTED && !vm.busy
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (canStart) Accent.copy(alpha = 0.14f) else Color.Transparent)
                .border(
                    1.dp,
                    if (canStart) Accent.copy(alpha = 0.3f) else Line.copy(alpha = 0.5f),
                    CircleShape
                )
                .clickable(enabled = canStart) { vm.newSession() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "Новый чат",
                tint = if (canStart) Accent else Dim,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun WorkspaceOnboarding(vm: PocketViewModel) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentDim)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(9.dp))
            Text(
                "Доступ к файлам",
                color = TextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { vm.dismissOnboarding() },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text("Скрыть", color = Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (vm.legacyStorage) {
                "Чтобы агент видел /sdcard/OpenCode, разреши «Память» в свойствах " +
                    "приложения. Приложение работает в legacy-режиме хранилища, поэтому " +
                    "отдельный «Доступ ко всем файлам» не нужен. Без разрешения сервер " +
                    "работает во внутренней папке."
            } else {
                "Чтобы агент видел /sdcard/OpenCode, выдай «Доступ ко всем файлам» " +
                    "в разделе «Специальный доступ». Без доступа сервер работает во " +
                    "внутренней папке."
            },
            color = Muted,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (!vm.openAllFilesSettings()) {
                        Toast.makeText(
                            context,
                            if (vm.legacyStorage) {
                                "Настройки не открылись. Путь: Приложения → OpenCode Pocket → Разрешения → Память"
                            } else {
                                "Настройки не открылись. Путь: Приложения → OpenCode Pocket → " +
                                    "Специальный доступ → Доступ ко всем файлам"
                            },
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
                shape = RoundedCornerShape(11.dp)
            ) {
                Text("Открыть настройки", fontSize = 11.5.sp)
            }
            OutlinedButton(
                onClick = { vm.dismissOnboarding() },
                shape = RoundedCornerShape(11.dp)
            ) {
                Text("Внутренняя папка", fontSize = 11.5.sp)
            }
        }
    }
}

@Composable
private fun ConnectionBanner(vm: PocketViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Warn.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, null, tint = Warn, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(vm.statusLabel, color = Warn, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (vm.statusDetail.isNotBlank()) {
                Text(
                    vm.statusDetail,
                    color = Muted,
                    fontSize = 10.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(onClick = { vm.connect() }) {
            Text("Подключиться", color = Accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Transcript(vm: PocketViewModel) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var follow by remember { mutableStateOf(true) }
    val dragged by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(dragged) {
        if (dragged) follow = false
    }
    LaunchedEffect(vm.messages) {
        if (follow && vm.messages.isNotEmpty()) listState.scrollToItem(0)
    }

    if (vm.messages.isEmpty()) {
        EmptyChatState(vm)
        return
    }

    val reversed = remember(vm.messages) { vm.messages.asReversed() }
    val liveAssistantId = vm.messages.lastOrNull { it.role == UiRole.ASSISTANT }?.id

    val turnStats = remember(vm.messages, vm.running) {
        if (vm.running) null else summarizeTurn(vm.messages)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            turnStats?.let { stats ->
                item(key = "turn-summary") { TurnSummaryCard(vm, stats) }
            }
            itemsIndexed(reversed, key = { index, message -> "${message.id}#$index" }) { _, message ->
                MessageItem(
                    message = message,
                    live = vm.running && message.id == liveAssistantId,
                    onOpenModels = { vm.selectTab(2) }
                )
            }
        }
        if (!follow) {
            SmallFloatingActionButton(
                onClick = {
                    follow = true
                    scope.launch { listState.animateScrollToItem(0) }
                },
                containerColor = Card2,
                contentColor = Accent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "Вниз")
            }
        }
    }
}

@Composable
private fun EmptyChatState(vm: PocketViewModel) {
    val suggestions = listOf(
        "Что находится в проекте?",
        "Запусти тесты и покажи результат",
        "Найди и исправь ошибку"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2A2116), Color(0xFF161A21))
                    )
                )
                .border(1.dp, Accent.copy(alpha = 0.32f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Опиши задачу",
            color = TextMain,
            fontSize = 21.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Агент сам прочитает файлы, запустит команды и покажет каждый шаг — тебе не нужно ничего нажимать по ходу.",
            color = Muted,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Accent.copy(alpha = 0.08f))
                .border(1.dp, Accent.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "@",
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(6.dp))
            Text("приложить файл из workspace", color = Muted, fontSize = 10.5.sp)
        }
        Spacer(Modifier.height(24.dp))
        suggestions.forEach { text ->
            Surface(
                color = Card.copy(alpha = 0.86f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Line.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { vm.onInputChange(text) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text,
                        color = TextMain,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("→", color = Accent.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }
        }
        if (vm.directoryLabel.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Text(
                vm.directoryLabel,
                color = Dim,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Composer(vm: PocketViewModel) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> vm.attachImages(uris) }
    val model = vm.selectedModelChoice
    val imagesBlocked = vm.attachments.isNotEmpty() && model != null && !model.supportsImages
    val connected = vm.status == ConnState.CONNECTED
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
    ) {
        if (vm.mentionQuery != null) {
            MentionPanel(vm)
            Spacer(Modifier.height(6.dp))
        }
        Surface(
            color = Card2.copy(alpha = 0.98f),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(
                1.dp,
                if (connected) Accent.copy(alpha = 0.22f) else Line.copy(alpha = 0.8f)
            ),
            shadowElevation = 12.dp
        ) {
            Column {
                AttachmentStrip(vm, imagesBlocked, model?.name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 7.dp, end = 7.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AttachButton(
                        enabled = connected && !vm.attachmentBusy && vm.attachments.size < 6
                    ) { picker.launch("image/*") }
                    OutlinedTextField(
                        value = vm.input,
                        onValueChange = vm::onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                if (connected) {
                                    "Задача агенту…  @ — файл"
                                } else {
                                    "Нет связи с сервером"
                                },
                                color = Dim,
                                fontSize = 13.sp
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TextMain,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        maxLines = 6,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Accent,
                            focusedTextColor = TextMain,
                            unfocusedTextColor = TextMain
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    if (vm.running) {
                        StopButton { vm.interrupt() }
                    } else {
                        val enabled = (vm.input.isNotBlank() || vm.attachments.isNotEmpty()) &&
                            connected && !vm.busy && !vm.attachmentBusy
                        SendButton(enabled) { vm.send() }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                model?.name ?: vm.modelLabel,
                color = Dim,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (vm.attachments.isNotEmpty()) {
                Text(
                    "${vm.attachments.size}/6",
                    color = Muted,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MentionPanel(vm: PocketViewModel) {
    Surface(
        color = Card.copy(alpha = 0.98f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.30f)),
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "Файлы workspace",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (vm.mentionBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = Accent,
                        strokeWidth = 1.2.dp
                    )
                } else {
                    Text("${vm.mentionResults.size}", color = Dim, fontSize = 9.5.sp)
                }
            }
            val results = vm.mentionResults
            if (results.isEmpty()) {
                Text(
                    if (vm.mentionBusy) "Ищу…" else "Ничего не найдено — продолжай ввод",
                    color = Muted,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            } else {
                results.take(6).forEach { hit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.applyMention(hit) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "@",
                            color = Accent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                hit.name,
                                color = TextMain,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val dir = hit.path.substringBeforeLast('/', "")
                            if (dir.isNotBlank()) {
                                Text(
                                    dir,
                                    color = Dim,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text("→", color = Dim, fontSize = 12.sp)
                    }
                }
            }
            Text(
                "Выбери файл или продолжай печатать",
                color = Dim.copy(alpha = 0.8f),
                fontSize = 9.5.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AttachmentStrip(vm: PocketViewModel, blocked: Boolean, modelName: String?) {
    val hasNote = vm.attachmentNote.isNotBlank() || blocked || vm.attachmentBusy
    if (vm.attachments.isEmpty() && !hasNote) return
    Column(Modifier.fillMaxWidth()) {
        if (vm.attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vm.attachments.forEach { attachment ->
                    AttachmentThumb(attachment) { vm.removeAttachment(attachment.uri) }
                }
            }
        }
        val note = when {
            vm.attachmentBusy -> "готовлю изображение…"
            vm.attachmentNote.isNotBlank() -> vm.attachmentNote
            blocked -> "${modelName ?: "Модель"} не принимает изображения"
            else -> ""
        }
        if (note.isNotBlank()) {
            Text(
                note,
                color = if (blocked || vm.attachmentNote.isNotBlank()) Danger else Muted,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun AttachmentThumb(attachment: Attachment, onRemove: () -> Unit) {
    val bitmap = rememberBitmap(attachment.uri, 240)
    Box(Modifier.size(64.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(Card)
                .border(1.dp, Line, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.AddPhotoAlternate, null, tint = Dim, modifier = Modifier.size(20.dp))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xCC1B1530))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Убрать",
                tint = TextMain,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun AttachButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (enabled) listOf(Card2, Card)
                    else listOf(Card.copy(alpha = 0.45f), Card.copy(alpha = 0.45f))
                )
            )
            .border(
                1.dp,
                if (enabled) Line.copy(alpha = 0.9f) else Line.copy(alpha = 0.3f),
                shape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.AddPhotoAlternate,
            contentDescription = "Прикрепить изображение",
            tint = if (enabled) Accent.copy(alpha = 0.92f) else Dim.copy(alpha = 0.55f),
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun rememberBitmap(uri: String, maxSide: Int = 1200): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, maxSide) {
        value = withContext(Dispatchers.IO) {
            runCatching { AttachmentStore.decode(context, uri, maxSide) }.getOrNull()
        }
    }
    return bitmap
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.linearGradient(listOf(Accent, Accent2))
                } else {
                    Brush.linearGradient(listOf(Card2, Card2))
                }
            )
            .border(
                1.dp,
                if (enabled) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                shape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.ArrowUpward,
            contentDescription = "Отправить",
            tint = if (enabled) Bg else Dim.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(Danger.copy(alpha = 0.16f))
            .border(1.dp, Danger.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Stop,
            contentDescription = "Остановить",
            tint = Danger,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MessageItem(message: UiMessage, live: Boolean, onOpenModels: () -> Unit = {}) {
    when (message.role) {
        UiRole.USER -> UserBubble(message)
        UiRole.ASSISTANT -> AssistantMessage(message, live, onOpenModels)
        UiRole.SHELL -> ShellMessage(message)
        UiRole.SYSTEM -> SystemMessage(message)
    }
}

@Composable
private fun UserBubble(message: UiMessage) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val text = message.blocks.filterIsInstance<UiBlock.Text>()
        .joinToString("\n") { it.content }
    val images = message.blocks.filterIsInstance<UiBlock.Image>()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(Modifier.widthIn(max = 340.dp), horizontalAlignment = Alignment.End) {
            images.forEach { image ->
                val bitmap = rememberBitmap(image.uri, 900)
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = image.name.ifBlank { "Изображение" },
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .widthIn(max = 280.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    )
                }
            }
            if (text.isNotBlank()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2B2318), Color(0xFF1D2028))
                            )
                        )
                        .border(
                            1.dp,
                            Accent.copy(alpha = 0.22f),
                            RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
                        )
                ) {
                    SelectionContainer {
                        Text(
                            text,
                            color = TextMain,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (text.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable {
                                clipboard.setText(AnnotatedString(text))
                                Toast.makeText(context, "Сообщение скопировано", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Копировать сообщение",
                            tint = Dim,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    timeLabel(message.createdAt),
                    color = Dim,
                    fontSize = 9.5.sp
                )
            }
        }
    }
}

@Composable
private fun AssistantMessage(message: UiMessage, live: Boolean, onOpenModels: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val plain = remember(message.blocks) {
        message.blocks.filterIsInstance<UiBlock.Text>().joinToString("\n\n") { it.content }
    }
    val copyAnswer = {
        clipboard.setText(AnnotatedString(plain))
        Toast.makeText(context, "Ответ скопирован", Toast.LENGTH_SHORT).show()
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2A2116), Color(0xFF171B22))
                        )
                    )
                    .border(1.dp, Accent.copy(alpha = 0.28f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            val meta = listOfNotNull(
                message.agent,
                message.model
            ).joinToString(" · ")
            Text(
                meta.ifBlank { "агент" },
                color = Muted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (plain.isNotBlank()) {
                TextButton(
                    onClick = copyAnswer,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Копировать", color = Muted, fontSize = 10.5.sp)
                }
            }
            Text(timeLabel(message.createdAt), color = Dim, fontSize = 9.5.sp)
        }
        Spacer(Modifier.height(9.dp))

        val blocks = message.blocks
        if (blocks.isEmpty() && live) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    color = Accent,
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("думает…", color = Muted, fontSize = 12.sp)
            }
        }
        blocks.forEachIndexed { index, block ->
            val blockLive = live && index == blocks.lastIndex
            BlockView(block, blockLive)
            if (index != blocks.lastIndex) Spacer(Modifier.height(10.dp))
        }
        if (plain.length > 500) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = copyAnswer,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = Dim,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Копировать ответ", color = Dim, fontSize = 10.sp)
                }
            }
        }
        message.error?.takeIf { it.isNotBlank() }?.let { error ->
            val failure = providerFailure(message.errorType, error)
            val actionable = failure != ProviderFailure.OTHER
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Danger.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Danger.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text(
                        if (actionable) failureText(failure, error) else error,
                        color = Danger,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    if (actionable) {
                        TextButton(
                            onClick = onOpenModels,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                        ) {
                            Text("Открыть модели", color = Accent, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockView(block: UiBlock, live: Boolean) {
    when (block) {
        is UiBlock.Text -> if (block.content.isNotBlank()) {
            MarkdownText(block.content, live)
        }

        is UiBlock.Reasoning -> ReasoningBlock(block, live)
        is UiBlock.Tool -> ToolBlock(block)
        is UiBlock.Shell -> ShellBlock(block)
        is UiBlock.Image -> {
            val bitmap = rememberBitmap(block.uri, 900)
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = block.name.ifBlank { "Изображение" },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }
        }
    }
}

@Composable
private fun MarkdownText(content: String, live: Boolean) {
    val blocks = remember(content, live) {
        markdownBlocks(if (live) "$content ▍" else content)
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        blocks.forEachIndexed { index, block ->
            MarkdownBlock(block, first = index == 0)
        }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock, first: Boolean) {
    when (block) {
        is MdBlock.Heading -> {
            val size = when (block.level) {
                1 -> 21.sp
                2 -> 18.5.sp
                3 -> 16.5.sp
                4 -> 15.sp
                else -> 13.5.sp
            }
            val line = when (block.level) {
                1 -> 28.sp
                2 -> 25.sp
                3 -> 22.sp
                else -> 19.sp
            }
            val weight = if (block.level <= 2) FontWeight.Bold else FontWeight.SemiBold
            val color = if (block.level <= 4) TextMain else Muted
            SelectionContainer {
                Text(
                    inlineAnnotated(block.text),
                    color = color,
                    fontSize = size,
                    lineHeight = line,
                    fontWeight = weight,
                    modifier = Modifier.padding(top = if (first) 0.dp else 5.dp)
                )
            }
        }

        is MdBlock.Paragraph -> SelectionContainer {
            Text(
                proseAnnotated(block.text),
                color = TextMain,
                fontSize = 14.5.sp,
                lineHeight = 21.5.sp
            )
        }

        is MdBlock.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { ListItemRow(it) }
        }

        is MdBlock.OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { index, item -> ListItemRow(item, index + 1) }
        }

        is MdBlock.Quote -> QuoteBlock(block)

        is MdBlock.Table -> MarkdownTable(block)

        is MdBlock.Code -> CodeBlock(block.lang, block.text)

        MdBlock.Rule -> HorizontalDivider(
            color = Line,
            modifier = Modifier.padding(vertical = 3.dp)
        )
    }
}

@Composable
private fun ListItemRow(item: MdListItem, fallbackNumber: Int = 1) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (item.level * 16).dp),
        verticalAlignment = Alignment.Top
    ) {
        val marker = when {
            item.checked != null -> if (item.checked) "☑" else "☐"
            item.number != null -> "${item.number ?: fallbackNumber}."
            item.level == 0 -> "•"
            item.level == 1 -> "◦"
            else -> "▪"
        }
        val tint = when {
            item.checked == true -> Mint
            item.checked == false -> Muted
            item.number != null -> Muted
            item.level == 0 -> Accent
            else -> Muted
        }
        Text(
            marker,
            color = tint,
            fontSize = if (item.number != null) 12.sp else 14.sp,
            lineHeight = 21.sp,
            fontFamily = if (item.number != null) FontFamily.Monospace else FontFamily.Default,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(if (item.number != null) 28.dp else 18.dp)
                .padding(end = 6.dp)
        )
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                proseAnnotated(item.text),
                color = TextMain,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun QuoteBlock(block: MdBlock.Quote) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .width(3.dp)
                .heightIn(min = 22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Accent.copy(alpha = 0.55f))
        )
        Spacer(Modifier.width(10.dp))
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                inlineAnnotated(block.lines.joinToString("\n").trim()),
                color = Muted,
                fontSize = 13.5.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun MarkdownTable(block: MdBlock.Table) {
    val columns = maxOf(block.header.size, block.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    val widths = remember(block) {
        (0 until columns).map { column ->
            val longest = (listOf(block.header.getOrNull(column).orEmpty()) +
                block.rows.map { it.getOrNull(column).orEmpty() })
                .maxOf { cell -> visibleLength(cell) }
            (longest.coerceIn(7, 28) * 7.4f).dp
        }
    }
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(
            Modifier
                .clip(RoundedCornerShape(11.dp))
                .border(1.dp, Line, RoundedCornerShape(11.dp))
        ) {
            TableRow(block.header, widths, block.align, header = true, shaded = false)
            block.rows.forEachIndexed { index, row ->
                HorizontalDivider(color = Line.copy(alpha = 0.7f))
                TableRow(row, widths, block.align, header = false, shaded = index % 2 == 1)
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<androidx.compose.ui.unit.Dp>,
    align: List<MdAlign>,
    header: Boolean,
    shaded: Boolean
) {
    Row(
        Modifier.background(
            when {
                header -> Accent.copy(alpha = 0.10f)
                shaded -> Card2.copy(alpha = 0.32f)
                else -> Color.Transparent
            }
        )
    ) {
        widths.forEachIndexed { index, width ->
            val textAlign = when (align.getOrNull(index) ?: MdAlign.START) {
                MdAlign.START -> TextAlign.Start
                MdAlign.CENTER -> TextAlign.Center
                MdAlign.END -> TextAlign.End
            }
            SelectionContainer {
                Text(
                    inlineAnnotated(cells.getOrNull(index).orEmpty()),
                    color = TextMain.copy(alpha = if (header) 1f else 0.92f),
                    fontSize = 12.5.sp,
                    lineHeight = 17.5.sp,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = textAlign,
                    modifier = Modifier
                        .width(width)
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                )
            }
        }
    }
}

private fun visibleLength(cell: String): Int = cell
    .replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")
    .replace(Regex("[*_~`]"), "")
    .length

@Composable
private fun CodeBlock(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val spans by produceState<List<List<RustSpan>>?>(initialValue = null, lang, code) {
        value = withContext(Dispatchers.IO) {
            if (code.length <= 24_000) RustTools.highlight(context, lang, code) else null
        }
    }
    val annotated = remember(spans, code) {
        highlightedAnnotated(code, spans) ?: AnnotatedString(code)
    }
    Surface(
        color = CodeBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Card2.copy(alpha = 0.72f))
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.75f))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    lang.ifBlank { "код" },
                    color = Muted,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Копировать", color = Accent, fontSize = 10.5.sp)
                }
            }
            SelectionContainer {
                Text(
                    annotated,
                    color = Color(0xFFE2DDD2),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    softWrap = false,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ReasoningBlock(block: UiBlock.Reasoning, live: Boolean) {
    var expanded by rememberSaveable(block.ordinal) { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Размышления",
                color = Accent.copy(alpha = 0.9f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (live || block.streaming) {
                Spacer(Modifier.width(7.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    color = Accent,
                    strokeWidth = 1.2.dp
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (expanded) "свернуть" else "${block.content.length} симв.",
                color = Dim,
                fontSize = 9.5.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Dim,
                modifier = Modifier.size(17.dp)
            )
        }
        if (expanded && block.content.isNotBlank()) {
            Surface(
                color = Card.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                SelectionContainer {
                    Text(
                        block.content,
                        color = Muted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolBlock(block: UiBlock.Tool) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val (statusLabel, statusColor) = toolStatusLabel(block.status)
    val running = block.status == "running" || block.status == "streaming"
    var expanded by rememberSaveable(block.callId) { mutableStateOf(running) }
    var touched by rememberSaveable(block.callId) { mutableStateOf(false) }
    var rawInput by rememberSaveable(block.callId) { mutableStateOf(false) }
    LaunchedEffect(running) {
        // Завершённый инструмент сворачиваем, чтобы длинные ответы не тонули
        // в выводе команд. Ручное раскрытие пользователем не трогаем.
        if (!running && !touched) expanded = false
    }
    val summary = toolSummary(block)
    val prettyInput = remember(block.input) { prettyJson(block.input) }
    val fields = remember(block.name, block.input) { toolInputFields(block.name, block.input) }
    val patches = remember(block.name, block.input) { parseEditPatches(block.name, block.input) }

    Surface(
        color = Card2.copy(alpha = 0.75f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (block.status == "error") Danger.copy(alpha = 0.4f) else Line)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        touched = true
                        expanded = !expanded
                    }
                    .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentDim),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "›_",
                        color = Accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        toolTitle(block.name),
                        color = TextMain,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (summary.isNotBlank()) {
                        Text(
                            summary,
                            color = Muted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = if (expanded) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!expanded) {
                        val hint = when {
                            !block.error.isNullOrBlank() -> "ошибка"
                            block.output.isNotBlank() -> "результат · ${outputLineCount(block.output)} стр."
                            running -> "ждём результат…"
                            else -> ""
                        }
                        if (hint.isNotBlank()) {
                            Text(hint, color = Dim, fontSize = 9.5.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    statusLabel,
                    color = statusColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Dim,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expanded) {
                HorizontalDivider(color = Line)
                Column(Modifier.padding(12.dp)) {
                    if (patches.isNotEmpty()) {
                        BlockLabel(if (patches.size == 1) "Изменения" else "Изменения · ${patches.size}")
                        patches.forEachIndexed { index, patch ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            EditDiffView(patch)
                        }
                    } else {
                        if (fields.isNotEmpty() && !rawInput) {
                            BlockLabel("Параметры")
                            fields.forEach { (label, value) -> ToolField(label, value) }
                        }
                        if (prettyInput.isNotBlank() && (fields.isEmpty() || rawInput)) {
                            if (fields.isNotEmpty()) Spacer(Modifier.height(4.dp))
                            BlockLabel(if (fields.isEmpty()) "Вход" else "JSON")
                            ScrollableCode(prettyInput, maxHeight = 200.dp)
                        }
                        if (fields.isNotEmpty()) {
                            TextButton(
                                onClick = { rawInput = !rawInput },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    if (rawInput) "Скрыть JSON" else "Показать JSON",
                                    color = Dim,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    if (block.output.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BlockLabel(if (block.shellId != null) "Вывод команды" else "Результат")
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(block.output))
                                    Toast.makeText(context, "Вывод скопирован", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("Копировать", color = Accent, fontSize = 10.sp)
                            }
                        }
                        ToolOutput(block)
                    }
                    block.error?.takeIf { it.isNotBlank() }?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error,
                            color = Danger,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (block.output.isBlank() && block.error.isNullOrBlank() && running) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Accent,
                                strokeWidth = 1.4.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("ждём результат…", color = Muted, fontSize = 11.5.sp)
                        }
                    }
                    block.exit?.let {
                        Text(
                            "exit $it",
                            color = if (it == 0) Mint else Danger,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Параметр инструмента в виде «подпись — значение» вместо сырого JSON. */
@Composable
private fun ToolField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = Muted,
            fontSize = 10.5.sp,
            modifier = Modifier.width(84.dp)
        )
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                value,
                color = TextMain,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Результат инструмента: markdown для веб-страниц, моноширинный текст для
 * команд. Очень длинный вывод обрезается до явного «Показать полностью».
 */
@Composable
private fun ToolOutput(block: UiBlock.Tool) {
    var full by rememberSaveable(block.callId) { mutableStateOf(false) }
    val raw = block.output
    val limit = 12_000
    val shown = if (!full && raw.length > limit) raw.take(limit).trimEnd() else raw
    val markdown = remember(block.name, raw) { looksLikeMarkdown(block.name, raw) }

    Surface(
        color = if (markdown) Card.copy(alpha = 0.55f) else CodeBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Line.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (markdown) {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                MarkdownText(shown, live = false)
            }
        } else {
            ScrollableCode(prettyOutput(shown), maxHeight = 340.dp)
        }
    }
    if (!full && raw.length > limit) {
        TextButton(
            onClick = { full = true },
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
        ) {
            Text(
                "Показать полностью · ${raw.length / 1024} КБ",
                color = Accent,
                fontSize = 10.5.sp
            )
        }
    }
}

private fun outputLineCount(text: String): Int =
    text.count { it == '\n' } + if (text.endsWith('\n')) 0 else 1

private fun looksLikeMarkdown(name: String, text: String): Boolean {
    val lower = name.lowercase()
    if (lower in setOf("webfetch", "fetch", "websearch", "web_search")) return true
    if (text.length < 40) return false
    val head = text.take(4_000)
    return head.startsWith("#") ||
        head.contains("\n## ") ||
        head.contains("\n|") ||
        head.contains("```")
}

private fun prettyOutput(text: String): String {
    val trimmed = text.trim()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return text
    return runCatching { JSONObject(trimmed).toString(2) }
        .recoverCatching { org.json.JSONArray(trimmed).toString(2) }
        .getOrDefault(text)
}

/** Понятные поля известных инструментов; сырой JSON остаётся за кнопкой. */
private fun toolInputFields(name: String, raw: String): List<Pair<String, String>> {
    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
    val order = when (name.lowercase()) {
        "webfetch", "fetch" -> listOf("url", "format", "timeout")
        "websearch", "web_search" -> listOf("query", "max_results", "search_depth")
        "read", "view", "cat" -> listOf("filePath", "file_path", "path", "offset", "limit")
        "write", "create", "edit", "patch", "multiedit", "apply_patch" ->
            listOf("filePath", "file_path", "path", "description")
        "bash", "shell", "execute" -> listOf("command", "description", "workdir", "timeout")
        "grep", "search" -> listOf("pattern", "path", "include")
        "glob", "find" -> listOf("pattern", "path")
        "task", "agent" -> listOf("description", "prompt")
        else -> listOf(
            "description", "command", "query", "url", "filePath",
            "file_path", "path", "pattern", "prompt"
        )
    }
    val labels = mapOf(
        "url" to "Адрес",
        "format" to "Формат",
        "timeout" to "Таймаут",
        "query" to "Запрос",
        "max_results" to "Лимит",
        "search_depth" to "Глубина",
        "filePath" to "Файл",
        "file_path" to "Файл",
        "path" to "Путь",
        "offset" to "Смещение",
        "limit" to "Лимит",
        "description" to "Описание",
        "command" to "Команда",
        "workdir" to "Каталог",
        "pattern" to "Шаблон",
        "include" to "Фильтр",
        "prompt" to "Запрос"
    )
    val out = mutableListOf<Pair<String, String>>()
    val seen = mutableSetOf<String>()
    for (key in order) {
        if (!seen.add(key)) continue
        val value = obj.optText(key)?.replace('\n', ' ')?.trim() ?: continue
        if (value.isEmpty()) continue
        out += (labels[key] ?: key) to value.take(500)
        if (out.size >= 5) break
    }
    return out
}

@Composable
private fun EditDiffView(patch: EditPatch) {
    val context = LocalContext.current
    val unifiedDiff = remember(patch.unified) {
        patch.unified?.takeIf { it.isNotBlank() }?.let(::parseUnifiedPatch)
    }
    val rustDiff by produceState<RustDiff?>(
        initialValue = unifiedDiff,
        patch.path,
        patch.old,
        patch.new,
        patch.unified
    ) {
        if (unifiedDiff != null) {
            value = unifiedDiff
        } else if (patch.old.isNotBlank() || patch.new.isNotBlank()) {
            value = withContext(Dispatchers.IO) {
                RustTools.diff(context, patch.old, patch.new)
            }
        }
    }

    val diff = rustDiff
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val name = patch.path.substringAfterLast('/').ifBlank { "файл" }
            val dir = patch.path.substringBeforeLast('/', "")
            Text(
                name,
                color = TextMain,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (dir.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    dir,
                    color = Dim,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (diff != null && (diff.additions > 0 || diff.deletions > 0)) {
                Text(
                    "+${diff.additions}",
                    color = Mint,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "−${diff.deletions}",
                    color = Danger,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            diff != null && diff.lines.isNotEmpty() -> {
                DiffLines(diff)
                if (diff.truncated) {
                    Text(
                        "diff обрезан",
                        color = Warn,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            diff != null && diff.lines.isEmpty() -> Text(
                "Без текстовых изменений",
                color = Muted,
                fontSize = 10.5.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            else -> Column(Modifier.fillMaxWidth()) {
                if (patch.old.isNotBlank()) {
                    BlockLabel("Было")
                    ScrollableCode(patch.old, maxHeight = 140.dp)
                }
                if (patch.new.isNotBlank()) {
                    if (patch.old.isNotBlank()) Spacer(Modifier.height(6.dp))
                    BlockLabel("Стало")
                    ScrollableCode(patch.new, maxHeight = 160.dp)
                }
            }
        }
    }
}

@Composable
private fun DiffLines(diff: RustDiff) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Line.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .background(CodeBg)
            .verticalScroll(vertical)
    ) {
        Column(Modifier.horizontalScroll(horizontal).padding(vertical = 4.dp)) {
            diff.lines.forEach { line -> DiffRow(line) }
        }
    }
}

@Composable
private fun DiffRow(line: RustDiffLine) {
    val (background, tint) = when (line.kind) {
        'a' -> Color(0xFF102A20) to Mint
        'd' -> Color(0xFF2A121C) to Danger
        'h' -> Accent.copy(alpha = 0.08f) to Accent
        else -> Color.Transparent to Dim
    }
    Row(
        modifier = Modifier
            .background(background)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        val number = when (line.kind) {
            'd' -> line.oldLine
            'h' -> 0
            else -> line.newLine
        }
        Text(
            if (number > 0 && line.kind != 'h') number.toString() else "",
            color = Dim.copy(alpha = 0.75f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            softWrap = false,
            modifier = Modifier.width(34.dp).padding(end = 6.dp)
        )
        Text(
            when (line.kind) {
                'a' -> "+"
                'd' -> "−"
                'h' -> ""
                else -> " "
            },
            color = tint,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            softWrap = false,
            modifier = Modifier.width(10.dp)
        )
        Text(
            line.text,
            color = if (line.kind == 'h') Accent else Color(0xFFE2DDD2),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace,
            softWrap = false
        )
    }
}

@Composable
private fun TurnSummaryCard(vm: PocketViewModel, stats: TurnStats) {
    val clipboard = LocalClipboardManager.current
    val report = remember(stats, vm.tokensIn, vm.tokensOut, vm.sessionCost) {
        turnReport(stats, vm.tokensIn, vm.tokensOut, vm.sessionCost)
    }
    Surface(
        color = Card.copy(alpha = 0.9f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.26f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "Итог хода",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(report)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Копировать", color = Accent, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MetricChip("${stats.tools} инстр.", Muted)
                if (stats.edits > 0) MetricChip("${stats.edits} правок", Mint)
                if (stats.reads > 0) MetricChip("${stats.reads} чтений", Muted)
                if (stats.searches > 0) MetricChip("${stats.searches} поиск", Muted)
                if (stats.errors > 0) MetricChip("${stats.errors} ошибок", Danger)
            }
            if (stats.files.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                stats.files.take(5).forEach { path ->
                    Row(
                        modifier = Modifier.padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "·",
                            color = Dim,
                            fontSize = 10.sp,
                            modifier = Modifier.width(10.dp)
                        )
                        Text(
                            path,
                            color = Muted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (stats.files.size > 5) {
                    Text(
                        "и ещё ${stats.files.size - 5}",
                        color = Dim,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(start = 10.dp, top = 2.dp)
                    )
                }
            }
            if (stats.commands.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                stats.commands.take(3).forEach { command ->
                    Text(
                        "$ $command",
                        color = Muted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (vm.tokensIn > 0L || vm.tokensOut > 0L || vm.sessionCost > 0.0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "токены ${shortNumber(vm.tokensIn)} → ${shortNumber(vm.tokensOut)}" +
                        if (vm.sessionCost > 0.0) {
                            " · $${String.format(Locale.US, "%.4f", vm.sessionCost)}"
                        } else {
                            ""
                        },
                    color = Dim,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MetricChip(text: String, tint: Color) {
    Text(
        text,
        color = tint,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(tint.copy(alpha = 0.1f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun ShellMessage(message: UiMessage) {
    val shell = message.blocks.filterIsInstance<UiBlock.Shell>().firstOrNull() ?: return
    ShellBlock(shell)
}

@Composable
private fun ShellBlock(shell: UiBlock.Shell) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var expanded by rememberSaveable(shell.shellId) { mutableStateOf(true) }
    Surface(
        color = CodeBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Card2)
                    .clickable { expanded = !expanded }
                    .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$",
                    color = Mint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    shell.command,
                    color = TextMain,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    maxLines = if (expanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                shell.exit?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "exit $it",
                        color = if (it == 0) Mint else Danger,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (shell.output.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable {
                                clipboard.setText(AnnotatedString(shell.output))
                                Toast.makeText(context, "Вывод скопирован", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Копировать вывод",
                            tint = Dim,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Dim,
                    modifier = Modifier.size(17.dp)
                )
            }
            if (expanded && shell.output.isNotBlank()) {
                SelectionContainer {
                    Text(
                        shell.output,
                        color = Color(0xFFE2DDD2),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemMessage(message: UiMessage) {
    val failed = message.outcome == "failed"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            message.blocks.filterIsInstance<UiBlock.Text>().joinToString("\n") { it.content },
            color = if (failed) Danger else Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(Card.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun BlockLabel(text: String) {
    Text(
        text,
        color = Muted,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 5.dp)
    )
}

@Composable
private fun ScrollableCode(text: String, maxHeight: androidx.compose.ui.unit.Dp) {
    SelectionContainer {
        Text(
            text,
            color = Color(0xFFDCD7CC),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .background(CodeBg, RoundedCornerShape(9.dp))
                .padding(10.dp)
        )
    }
}

private fun toolSummary(block: UiBlock.Tool): String {
    val obj = runCatching { JSONObject(block.input) }.getOrNull()
        ?: return block.input.lineSequence().firstOrNull().orEmpty().take(180)
    val keys = listOf(
        "command", "description", "filePath", "file_path", "path",
        "pattern", "query", "url", "prompt", "task", "name"
    )
    for (key in keys) {
        val value = obj.optText(key) ?: continue
        return value.replace('\n', ' ').take(220)
    }
    return block.input.replace('\n', ' ').take(180)
}

private fun prettyJson(raw: String): String {
    if (raw.isBlank()) return ""
    return runCatching { JSONObject(raw).toString(2) }.getOrElse { raw }
}

private fun timeLabel(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}
