package com.pocket.opencode

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun ServerPanel(vm: PocketViewModel) {
    Panel {
        SectionTitle("Сервер")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipButton(
                label = "Встроенный",
                accent = vm.isEmbedded,
                onClick = { if (!vm.isEmbedded) vm.setMode(ServerMode.EMBEDDED) }
            )
            ChipButton(
                label = "Внешний",
                accent = !vm.isEmbedded,
                onClick = { if (vm.isEmbedded) vm.setMode(ServerMode.EXTERNAL) }
            )
            Spacer(Modifier.weight(1f))
            StatusPill(serverStatusLabel(vm), serverStatusTone(vm))
        }
        Spacer(Modifier.height(12.dp))
        if (vm.isEmbedded) {
            KeyValueRow("Порт", vm.serverStatus.port.takeIf { it > 0 }?.toString() ?: "—", mono = true)
            KeyValueRow("Версия", vm.serverStatus.version.ifBlank { vm.serverInfo?.version ?: "—" }, mono = true)
            KeyValueRow("PID", vm.serverInfo?.pid?.toString() ?: "—", mono = true)
            KeyValueRow(
                "Каталог",
                vm.serverStatus.workspace.ifBlank { vm.workspacePath }.let { shortPath(it, 46) },
                mono = true
            )
            if (vm.serverStatus.error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    vm.serverStatus.error,
                    color = Danger,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            val state = vm.serverStatus.state
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.startEmbedded() },
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.weight(1f),
                    enabled = state == EmbeddedState.STOPPED || state == EmbeddedState.ERROR
                ) {
                    Text("Запустить", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { vm.stopEmbedded() },
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.weight(1f),
                    enabled = state != EmbeddedState.STOPPED
                ) {
                    Text("Остановить", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.restartEmbedded() },
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Перезапустить сервер", fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Line)
            Spacer(Modifier.height(10.dp))
            LogBlock(vm)
        } else {
            Text(
                "Приложение подключается к серверу по адресу из блока «Подключение». " +
                    "Встроенный сервер и его уведомление при этом не запускаются.",
                color = Muted,
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Адрес", vm.baseUrl, mono = true)
            KeyValueRow("Статус", vm.statusLabel)
            KeyValueRow("Версия", vm.serverInfo?.version ?: "—", mono = true)
        }
    }
}

@Composable
private fun LogBlock(vm: PocketViewModel) {
    val clipboard = LocalClipboardManager.current
    val lines = vm.serverLog.takeLast(100)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Журнал сервера",
            color = Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = {
                clipboard.setText(AnnotatedString(vm.serverLog.joinToString("\n")))
            },
            enabled = lines.isNotEmpty(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text("Копировать", color = Accent, fontSize = 11.sp)
        }
    }
    if (lines.isEmpty()) {
        Text("Пока пусто", color = Dim, fontSize = 11.5.sp)
        return
    }
    SelectionContainer {
        Text(
            lines.joinToString("\n"),
            color = Muted,
            fontSize = 9.5.sp,
            lineHeight = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
fun WorkspacePanel(vm: PocketViewModel) {
    Panel {
        SectionTitle("Доступ к файлам и каталог")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Доступ ко всем файлам", color = TextMain, fontSize = 12.5.sp)
                Text(
                    if (vm.hasAllFilesAccess) "Разрешён в системных настройках" else "Не разрешён",
                    color = if (vm.hasAllFilesAccess) Mint else Warn,
                    fontSize = 10.5.sp
                )
            }
            StatusPill(
                if (vm.hasAllFilesAccess) "Разрешён" else "Нет доступа",
                if (vm.hasAllFilesAccess) Mint else Warn
            )
        }
        Spacer(Modifier.height(8.dp))
        val modernStorage = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        Text(
            if (modernStorage) {
                "Android выдаёт этот доступ только в системных настройках: " +
                    "«Настройки → Приложения → OpenCode Pocket → Доступ ко всем файлам». " +
                    "После выдачи вернись в приложение — сервер перезапустится сам."
            } else {
                "На Android 8–10 достаточно разрешить «Память» в свойствах приложения. " +
                    "После выдачи вернись в приложение — сервер перезапустится сам."
            },
            color = Dim,
            fontSize = 10.5.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { vm.openAllFilesSettings() },
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    vm.hasAllFilesAccess -> "Открыть настройки доступа"
                    modernStorage -> "Выдать доступ ко всем файлам"
                    else -> "Разрешить доступ к памяти"
                },
                fontSize = 11.5.sp
            )
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.workspacePath,
            onValueChange = vm::onWorkspacePathChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Папка проекта", color = Muted, fontSize = 11.sp) },
            placeholder = { Text(Workspace.defaultPath(), color = Dim, fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            colors = pocketFieldColors()
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.checkWorkspace() },
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Проверить доступ", fontSize = 11.5.sp)
            }
            OutlinedButton(
                onClick = { vm.restartEmbedded() },
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.weight(1f),
                enabled = vm.isEmbedded
            ) {
                Text("Перезапуск", fontSize = 11.5.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        val accessText = when {
            !vm.hasAllFilesAccess ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    "Нет «Доступа ко всем файлам» — /sdcard недоступен"
                } else {
                    "Нет разрешения на память — /sdcard недоступен"
                }
            vm.workspaceUsable -> "Каталог доступен для записи"
            else -> "Каталог ещё не создан или недоступен"
        }
        Text(
            accessText,
            color = if (vm.hasAllFilesAccess && vm.workspaceUsable) Mint else Warn,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )
        if (!vm.hasAllFilesAccess) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Без доступа сервер работает во внутренней папке приложения: " +
                    "файлы не видны в проводнике.",
                color = Dim,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
        if (vm.serverStatus.workspace.isNotBlank() &&
            File(vm.serverStatus.workspace).absolutePath !=
            File(vm.workspacePath.ifBlank { Workspace.defaultPath() }).absolutePath
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Фактически сервер использует: ${vm.serverStatus.workspace}",
                color = Muted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
        val activeWorkspace = vm.serverStatus.workspace.ifBlank {
            vm.workspacePath.ifBlank { Workspace.defaultPath() }
        }
        if (vm.sessionDirectory.isNotBlank() &&
            File(vm.sessionDirectory).absolutePath != File(activeWorkspace).absolutePath
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Текущая сессия создана в старом каталоге: ${vm.sessionDirectory}. " +
                    "Новые сессии уже используют $activeWorkspace, но эта останется " +
                    "внутри приложения, пока её не переместить.",
                color = Warn,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.moveSessionToWorkspace() },
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth(),
                enabled = !vm.movingSession && !vm.running && !vm.busy &&
                    vm.sessionId?.isNotBlank() == true
            ) {
                Text(
                    if (vm.movingSession) "Перемещаю…" else "Переместить сессию в рабочий каталог",
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

@Composable
fun QuotaPanel(vm: PocketViewModel) {
    Panel {
        SectionTitle("Аккаунт и квота")
        Spacer(Modifier.height(8.dp))
        Text(
            "Лимит бесплатных моделей считается на стороне аккаунта OpenCode, " +
                "а не приложением. Свой ключ провайдера снимает ограничение: " +
                "запросы идут напрямую в Anthropic, OpenAI, OpenRouter и другие.",
            color = Muted,
            fontSize = 11.5.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.selectTab(2) },
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Добавить свой ключ")
        }
    }
}

private fun serverStatusLabel(vm: PocketViewModel): String = when (vm.serverStatus.state) {
    EmbeddedState.RUNNING -> if (vm.status == ConnState.CONNECTED) "Работает" else "Запущен"
    EmbeddedState.STARTING -> "Запуск"
    EmbeddedState.ERROR -> "Ошибка"
    EmbeddedState.STOPPED -> "Остановлен"
}

private fun serverStatusTone(vm: PocketViewModel) = when (vm.serverStatus.state) {
    EmbeddedState.RUNNING -> if (vm.status == ConnState.CONNECTED) Mint else Warn
    EmbeddedState.STARTING -> Warn
    EmbeddedState.ERROR -> Danger
    EmbeddedState.STOPPED -> Dim
}
