package com.pocket.opencode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun SettingsScreen(vm: PocketViewModel) {
    var revealPassword by rememberSaveable { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showConfig by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = "Связь",
            subtitle = "OpenCode Server API v2 · встроенный и внешний режим"
        )

        ServerPanel(vm)
        WorkspacePanel(vm)
        EnvironmentPanel(vm)
        QuotaPanel(vm)
        MigrationPanel(vm)
        McpPanel(vm)

        Panel {
            SectionTitle("Конфиг OpenCode") {
                TextButton(
                    onClick = {
                        showConfig = !showConfig
                        if (showConfig) vm.loadConfig()
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 0.dp
                    )
                ) {
                    Text(if (showConfig) "Скрыть" else "Открыть", color = Accent, fontSize = 11.sp)
                }
            }
            if (showConfig) {
                Spacer(Modifier.height(8.dp))
                Text(
                    vm.configFilePath().absolutePath,
                    color = Dim,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.configText,
                    onValueChange = {
                        vm.configText = it
                        vm.configError = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    label = { Text("opencode.json", color = Muted, fontSize = 11.sp) },
                    shape = RoundedCornerShape(13.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    colors = fieldColors()
                )
                if (vm.configError.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(vm.configError, color = Danger, fontSize = 11.sp, lineHeight = 16.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.saveConfig(vm.configText) },
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Сохранить", fontSize = 11.5.sp)
                    }
                    OutlinedButton(
                        onClick = { vm.loadConfig() },
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Text("Сбросить", fontSize = 11.5.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.restartEmbedded() },
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Перезагрузить сервер", fontSize = 11.5.sp)
                }
            }
        }

        if (!vm.isEmbedded) {
            Panel {
                SectionTitle("Подключение")
                Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = vm.baseUrl,
                onValueChange = vm::onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Адрес сервера", color = Muted, fontSize = 11.sp) },
                placeholder = { Text("http://127.0.0.1:4096", color = Dim, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors()
            )
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(
                value = vm.password,
                onValueChange = vm::onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Пароль сервера", color = Muted, fontSize = 11.sp) },
                placeholder = { Text("если задан", color = Dim, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                visualTransformation = if (revealPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(
                        onClick = { revealPassword = !revealPassword },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Text(
                            if (revealPassword) "скрыть" else "показать",
                            color = Muted,
                            fontSize = 10.5.sp
                        )
                    }
                },
                colors = fieldColors()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.connect() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                    enabled = vm.status != ConnState.CONNECTING
                ) {
                    if (vm.status == ConnState.CONNECTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Accent,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (vm.status == ConnState.CONNECTED) "Переподключиться" else "Подключиться")
                }
                OutlinedButton(
                    onClick = { vm.forgetPassword() },
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text("Забыть пароль", fontSize = 11.5.sp)
                }
            }
            if (vm.statusDetail.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    vm.statusDetail,
                    color = if (vm.status == ConnState.ERROR) Danger else Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Line)
            Spacer(Modifier.height(10.dp))
            Text(
                "Пароль хранится в зашифрованном виде (Android Keystore, AES-256-GCM) и никуда не отправляется, кроме самого сервера.",
                color = Dim,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
            }
        }

        Panel {
            SectionTitle("Активная сессия")
            Spacer(Modifier.height(8.dp))
            if (vm.sessionId == null) {
                Text("Сессия не выбрана", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { vm.newSession() },
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Создать сессию")
                }
            } else {
                KeyValueRow("Название", vm.sessionTitle.ifBlank { "Без названия" })
                KeyValueRow("ID", vm.sessionId.orEmpty(), mono = true)
                KeyValueRow("Агент", vm.agentLabel)
                KeyValueRow("Модель", vm.modelLabel)
                vm.variantLabel?.let { KeyValueRow("Вариант", it, mono = true) }
                KeyValueRow("Каталог", vm.sessionDirectory.ifBlank { vm.directoryLabel }, mono = true)
                if (vm.tokensIn > 0L || vm.tokensOut > 0L) {
                    KeyValueRow(
                        "Токены",
                        "${shortNumber(vm.tokensIn)} → ${shortNumber(vm.tokensOut)}",
                        mono = true
                    )
                }
                if (vm.sessionCost > 0.0) {
                    KeyValueRow(
                        "Стоимость",
                        "$" + String.format(Locale.US, "%.4f", vm.sessionCost),
                        mono = true
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.newSession() },
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Новая сессия")
                    }
                    OutlinedButton(
                        onClick = { vm.refreshEverything() },
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Text("Обновить")
                    }
                }
            }
        }

        Panel {
            SectionTitle("Данные на устройстве")
            Spacer(Modifier.height(8.dp))
            Text(
                "Адрес, пароль, выбранная сессия, черновик сообщения и история переписки сохраняются автоматически и восстанавливаются при запуске. История подтягивается с сервера заново при каждом подключении.",
                color = Muted,
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { confirmReset = true },
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сбросить настройки и историю", color = Danger, fontSize = 12.sp)
            }
        }

        Text(
            "OpenCode Pocket · не открывай сервер в публичный интернет без пароля и TLS.",
            color = Dim,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Card,
            title = { Text("Сбросить всё?", color = TextMain, fontSize = 17.sp) },
            text = {
                Text(
                    "Будут удалены адрес сервера, пароль, выбранная сессия, черновик и локальный кэш переписки. Сессии на сервере останутся.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        vm.resetAll()
                    }
                ) {
                    Text("Сбросить", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Отмена", color = Muted)
                }
            }
        )
    }
}

@Composable
private fun McpPanel(vm: PocketViewModel) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (vm.status == ConnState.CONNECTED) vm.loadMcp()
    }

    Panel {
        SectionTitle("MCP-серверы") {
            TextButton(
                onClick = { vm.loadMcp() },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 0.dp
                )
            ) {
                Text(if (vm.mcpBusy) "…" else "Обновить", color = Accent, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (vm.mcpServers.isEmpty()) {
            Text(
                "Ничего не подключено. Context7 и DeepWiki добавляются одной кнопкой.",
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
        vm.mcpServers.forEach { server ->
            HorizontalDivider(color = Line.copy(alpha = 0.4f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        server.title.ifBlank { server.name },
                        color = TextMain,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        server.status + server.error.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                        color = when (server.status) {
                            "connected" -> Mint
                            "failed", "needs_auth" -> Danger
                            else -> Muted
                        },
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = { vm.toggleMcp(server) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 0.dp
                    )
                ) {
                    Text(
                        if (server.connected) "Отключить" else "Подключить",
                        color = Accent,
                        fontSize = 10.5.sp
                    )
                }
                TextButton(
                    onClick = { vm.removeMcp(server.name) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 0.dp
                    )
                ) {
                    Text("Удалить", color = Danger, fontSize = 10.5.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.addMcpPreset("context7") },
                shape = RoundedCornerShape(13.dp),
                enabled = !vm.mcpBusy,
                modifier = Modifier.weight(1f)
            ) {
                Text("Context7", fontSize = 11.5.sp)
            }
            OutlinedButton(
                onClick = { vm.addMcpPreset("deepwiki") },
                shape = RoundedCornerShape(13.dp),
                enabled = !vm.mcpBusy,
                modifier = Modifier.weight(1f)
            ) {
                Text("DeepWiki", fontSize = 11.5.sp)
            }
        }
        TextButton(
            onClick = { showAdd = !showAdd },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 4.dp,
                vertical = 0.dp
            )
        ) {
            Text(if (showAdd) "Скрыть" else "Свой сервер", color = Muted, fontSize = 11.sp)
        }
        if (showAdd) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Имя", color = Muted, fontSize = 11.sp) },
                placeholder = { Text("my-mcp", color = Dim, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                colors = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL", color = Muted, fontSize = 11.sp) },
                placeholder = { Text("https://example.com/mcp", color = Dim, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.addRemoteMcp(name, url) },
                shape = RoundedCornerShape(13.dp),
                enabled = !vm.mcpBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Добавить и подключить", fontSize = 11.5.sp)
            }
        }
        if (vm.mcpStatus.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(vm.mcpStatus, color = Accent, fontSize = 10.5.sp)
        }
        if (vm.mcpError.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(vm.mcpError, color = Danger, fontSize = 10.5.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Локальные MCP (stdio) требуют node/python внутри сервера — доступны только remote.",
            color = Dim,
            fontSize = 9.5.sp,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun fieldColors() = pocketFieldColors()
