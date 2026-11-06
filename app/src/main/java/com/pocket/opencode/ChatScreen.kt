package com.pocket.opencode

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
        "Найди потенциальные ошибки"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AccentDim),
            contentAlignment = Alignment.Center
        ) {
            Text(
                ">_",
                color = Accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Спроси что угодно",
            color = TextMain,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Агент читает файлы, запускает команды и показывает каждый шаг прямо здесь.",
            color = Muted,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(22.dp))
        suggestions.forEach { text ->
            Surface(
                color = Card,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { vm.onInputChange(text) }
            ) {
                Text(
                    text,
                    color = TextMain,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                )
            }
        }
    }
}

@Composable
private fun Composer(vm: PocketViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
    ) {
        Surface(
            color = Card2,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Line.copy(alpha = 0.85f)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = vm.input,
                    onValueChange = vm::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (vm.status == ConnState.CONNECTED) "Задача агенту…" else "Нет связи с сервером",
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
                    val enabled = vm.input.isNotBlank() && vm.status == ConnState.CONNECTED && !vm.busy
                    SendButton(enabled) { vm.send() }
                }
            }
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (enabled) Accent else Card2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Send,
            contentDescription = "Отправить",
            tint = if (enabled) Color(0xFF171128) else Dim,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Danger.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Clear,
            contentDescription = "Остановить",
            tint = Danger,
            modifier = Modifier.size(20.dp)
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(Modifier.widthIn(max = 340.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6E52E8), Color(0xFF4A2FAF))
                        )
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.10f),
                        RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
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
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AccentDim),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "AI",
                    color = Accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
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
    Surface(
        color = CodeBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Card2)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    code,
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
                    if (prettyInput.isNotBlank() && prettyInput != summary) {
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
