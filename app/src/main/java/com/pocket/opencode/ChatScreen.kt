package com.pocket.opencode

import android.graphics.Bitmap
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Search
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
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChipButton(
            label = vm.sessionLabel,
            accent = true,
            maxWidth = 230.dp,
            onClick = onSessions
        )
        ChipButton(label = vm.modelLabel, maxWidth = 210.dp, onClick = onModels)
        vm.variantLabel?.let {
            ChipButton(label = it.uppercase(Locale.getDefault()), tone = AccentDim) { onModels() }
        }
        ChipButton(label = vm.agentLabel, onClick = onAgents)
        if (vm.usageLabel.isNotBlank()) {
            ChipButton(label = vm.usageLabel, tone = Color(0xFF13241F))
        }
    }
}

@Composable
private fun WorkspaceOnboarding(vm: PocketViewModel) {
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
            "Чтобы агент видел /sdcard/OpenCode, выдай «Доступ ко всем файлам». " +
                "Без него сервер работает во внутренней папке приложения.",
            color = Muted,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.openAllFilesSettings() },
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
                    live = vm.running && message.id == liveAssistantId
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
                .size(70.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AccentDim, Color(0xFF1B1436))
                    )
                )
                .border(1.dp, Accent.copy(alpha = 0.32f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(27.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Опиши задачу",
            color = TextMain,
            fontSize = 20.sp,
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
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(Accent.copy(alpha = 0.08f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
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
        Spacer(Modifier.height(22.dp))
        suggestions.forEach { text ->
            Surface(
                color = Card.copy(alpha = 0.86f),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { vm.onInputChange(text) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text,
                        color = TextMain,
                        fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text("→", color = Dim, fontSize = 14.sp)
                }
            }
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
            border = BorderStroke(1.dp, Accent.copy(alpha = 0.22f)),
            shadowElevation = 12.dp
        ) {
            Column {
                AttachmentStrip(vm, imagesBlocked, model?.name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 7.dp, end = 7.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    AttachButton(
                        enabled = vm.status == ConnState.CONNECTED &&
                            !vm.attachmentBusy && vm.attachments.size < 6
                    ) { picker.launch("image/*") }
                    OutlinedTextField(
                        value = vm.input,
                        onValueChange = vm::onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                if (vm.status == ConnState.CONNECTED) {
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
                            vm.status == ConnState.CONNECTED && !vm.busy && !vm.attachmentBusy
                        SendButton(enabled) { vm.send() }
                    }
                }
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
                    "ФАЙЛЫ WORKSPACE",
                    color = Dim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
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
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) Card else Color.Transparent)
            .border(1.dp, if (enabled) Line else Color.Transparent, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.AddPhotoAlternate,
            contentDescription = "Прикрепить изображение",
            tint = if (enabled) Accent else Dim,
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
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    if (enabled) listOf(Accent, Accent2) else listOf(Card2, Card2)
                )
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Send,
            contentDescription = "Отправить",
            tint = if (enabled) Bg else Dim,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Danger.copy(alpha = 0.16f))
            .border(1.dp, Danger.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Clear,
            contentDescription = "Остановить",
            tint = Danger,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MessageItem(message: UiMessage, live: Boolean) {
    when (message.role) {
        UiRole.USER -> UserBubble(message)
        UiRole.ASSISTANT -> AssistantMessage(message, live)
        UiRole.SHELL -> ShellMessage(message)
        UiRole.SYSTEM -> SystemMessage(message)
    }
}

@Composable
private fun UserBubble(message: UiMessage) {
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
                                listOf(Color(0xFF7C5CFF), Color(0xFF4B2DB8))
                            )
                        )
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.12f),
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
            Text(
                timeLabel(message.createdAt),
                color = Dim,
                fontSize = 9.5.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessage(message: UiMessage, live: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AccentDim, Color(0xFF1B1438))
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
        message.error?.takeIf { it.isNotBlank() }?.let { error ->
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Danger.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Danger.copy(alpha = 0.35f))
            ) {
                Text(
                    error,
                    color = Danger,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
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
    val chunks = remember(content, live) {
        markdownChunks(if (live) "$content ▍" else content)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunks.forEach { chunk ->
            when (chunk) {
                is MdChunk.Prose -> if (chunk.text.isNotEmpty()) {
                    SelectionContainer {
                        Text(
                            proseAnnotated(chunk.text),
                            color = TextMain,
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp
                        )
                    }
                }

                is MdChunk.Code -> CodeBlock(chunk.lang, chunk.text)
            }
        }
    }
}

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
                    lang.ifBlank { "код" }.uppercase(Locale.getDefault()),
                    color = Dim,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.4.sp,
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
                    color = Color(0xFFD8D5E8),
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
                "РАЗМЫШЛЕНИЯ",
                color = Accent.copy(alpha = 0.85f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
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
    val (statusLabel, statusColor) = toolStatusLabel(block.status)
    val running = block.status == "running" || block.status == "streaming"
    var expanded by rememberSaveable(block.callId) { mutableStateOf(running) }
    val summary = toolSummary(block)
    val prettyInput = remember(block.input) { prettyJson(block.input) }
    val patches = remember(block.name, block.input) { parseEditPatches(block.name, block.input) }

    Surface(
        color = Card2.copy(alpha = 0.75f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (block.status == "error") Danger.copy(alpha = 0.4f) else Line)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
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
                            maxLines = if (expanded) 4 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
                        BlockLabel(if (patches.size == 1) "ИЗМЕНЕНИЯ" else "ИЗМЕНЕНИЯ · ${patches.size}")
                        patches.forEachIndexed { index, patch ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            EditDiffView(patch)
                        }
                    } else if (prettyInput.isNotBlank() && prettyInput != summary) {
                        BlockLabel("ВХОД")
                        ScrollableCode(prettyInput, maxHeight = 200.dp)
                    }
                    if (block.output.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        BlockLabel(if (block.shellId != null) "ВЫВОД КОМАНДЫ" else "РЕЗУЛЬТАТ")
                        ScrollableCode(block.output, maxHeight = 320.dp)
                    }
                    block.error?.takeIf { it.isNotBlank() }?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = Danger, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
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
                    BlockLabel("БЫЛО")
                    ScrollableCode(patch.old, maxHeight = 140.dp)
                }
                if (patch.new.isNotBlank()) {
                    if (patch.old.isNotBlank()) Spacer(Modifier.height(6.dp))
                    BlockLabel("СТАЛО")
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
            color = if (line.kind == 'h') Accent else Color(0xFFD5D2E4),
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
                    "ИТОГ ХОДА",
                    color = Accent,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                shell.exit?.let {
                    Text(
                        "exit $it",
                        color = if (it == 0) Mint else Danger,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            if (shell.output.isNotBlank()) {
                SelectionContainer {
                    Text(
                        shell.output,
                        color = Color(0xFFD8D5E8),
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
        color = Dim,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(bottom = 5.dp)
    )
}

@Composable
private fun ScrollableCode(text: String, maxHeight: androidx.compose.ui.unit.Dp) {
    SelectionContainer {
        Text(
            text,
            color = Color(0xFFCFCBE0),
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
