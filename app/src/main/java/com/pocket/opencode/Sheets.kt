package com.pocket.opencode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsSheet(vm: PocketViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Card,
        contentColor = TextMain,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Line) }
    ) {
        Column(Modifier.fillMaxHeight(0.78f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Сессии", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${vm.sessions.size} на сервере",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = { vm.refreshSessions() }) {
                    Text("Обновить", color = Muted, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        vm.newSession()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Новая", fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = Line)

            if (vm.sessions.isEmpty()) {
                EmptyHint("Сессий пока нет", "Создай новую сессию или отправь первое сообщение в чат.")
            }

            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(vm.sessions, key = { it.id }) { session ->
                    val current = session.id == vm.sessionId
                    val active = vm.activeSessions.contains(session.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (current) AccentDim.copy(alpha = 0.45f) else Card)
                            .clickable {
                                vm.selectSession(session.id)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (active) {
                                    Box3(Mint)
                                    Spacer(Modifier.width(7.dp))
                                }
                                Text(
                                    session.title,
                                    color = TextMain,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    relativeTime(session.updated),
                                    color = Dim,
                                    fontSize = 10.sp
                                )
                                if (session.cost > 0.0) {
                                    Text(
                                        "  ·  $${String.format(Locale.US, "%.3f", session.cost)}",
                                        color = Dim,
                                        fontSize = 10.sp
                                    )
                                }
                                if (session.tokensInput > 0L) {
                                    Text(
                                        "  ·  ${shortNumber(session.tokensInput)}→${shortNumber(session.tokensOutput)}",
                                        color = Dim,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            if (session.directory.isNotBlank()) {
                                Text(
                                    shortPath(session.directory, 44),
                                    color = Dim,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (current) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = Line.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsSheet(vm: PocketViewModel, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = vm.models.filter { model ->
        query.isBlank() ||
            model.name.contains(query, ignoreCase = true) ||
            model.id.contains(query, ignoreCase = true) ||
            model.providerID.contains(query, ignoreCase = true)
    }
    val groups = filtered.groupBy { it.providerID }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Card,
        contentColor = TextMain,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Line) }
    ) {
        Column(Modifier.fillMaxHeight(0.82f)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Модель", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    vm.modelLabel,
                    color = Muted,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск модели", color = Dim, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = Dim, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent.copy(alpha = 0.5f),
                        unfocusedBorderColor = Line,
                        focusedContainerColor = Card2,
                        unfocusedContainerColor = Card2,
                        cursorColor = Accent
                    )
                )
                Spacer(Modifier.height(10.dp))
            }
            HorizontalDivider(color = Line)

            if (filtered.isEmpty()) {
                EmptyHint("Ничего не найдено", "Попробуй другой запрос или проверь подключение.")
            }

            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 28.dp)) {
                groups.forEach { (provider, list) ->
                    item(key = "provider_$provider") {
                        Text(
                            provider.uppercase(Locale.getDefault()),
                            color = Accent,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)
                        )
                    }
                    items(list, key = { "model_${it.providerID}_${it.id}" }) { model ->
                        val selected = model.id == vm.selectedModelId &&
                            model.providerID == vm.selectedModelProvider
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(if (selected) AccentDim.copy(alpha = 0.35f) else Card)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.selectModel(model) }
                                    .padding(horizontal = 20.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        model.name,
                                        color = if (selected) Accent else TextMain,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        model.id,
                                        color = Dim,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (model.variants.isNotEmpty()) {
                                    Text(
                                        "${model.variants.size} вар.",
                                        color = Dim,
                                        fontSize = 9.5.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = Accent,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            if (selected && model.variants.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ChipButton(
                                        label = "по умолчанию",
                                        accent = vm.selectedVariant == null
                                    ) { vm.selectVariant(null) }
                                    model.variants.forEach { variant ->
                                        ChipButton(
                                            label = variant,
                                            accent = vm.selectedVariant == variant,
                                            onClick = { vm.selectVariant(variant) }
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = Line.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsSheet(vm: PocketViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Card,
        contentColor = TextMain,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Line) }
    ) {
        Column(Modifier.fillMaxHeight(0.7f)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("Агент", color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("Кто выполняет задачу", color = Muted, fontSize = 11.5.sp)
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider(color = Line)
            if (vm.agents.isEmpty()) {
                EmptyHint("Агенты не загружены", "Проверь соединение с сервером.")
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 28.dp)) {
                items(vm.agents, key = { it.id }) { agent ->
                    val selected = agent.id == vm.selectedAgent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) AccentDim.copy(alpha = 0.35f) else Card)
                            .clickable {
                                vm.selectAgent(agent.id)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    agent.name,
                                    color = if (selected) Accent else TextMain,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    agent.mode,
                                    color = Dim,
                                    fontSize = 9.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Card2)
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            if (agent.description.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    agent.description,
                                    color = Muted,
                                    fontSize = 11.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = Accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = Line.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun Box3(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color)
    )
}
