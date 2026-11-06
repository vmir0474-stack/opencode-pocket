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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Связь", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("OpenCode Server API v2", color = Muted, fontSize = 11.5.sp)
            }
        }

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

        Panel {
            SectionTitle("Сервер")
            Spacer(Modifier.height(8.dp))
            val info = vm.serverInfo
            if (info == null) {
                EmptyHint("Нет данных", "Подключись, чтобы увидеть информацию о сервере.")
            } else {
                KeyValueRow("Версия", info.version.ifBlank { "—" }, mono = true)
                KeyValueRow("PID", info.pid.toString(), mono = true)
                KeyValueRow("Хост", info.hostname.ifBlank { "—" }, mono = true)
                KeyValueRow(
                    "Каталог",
                    vm.directoryLabel.ifBlank { "—" },
                    mono = true
                )
                info.urls.firstOrNull()?.let {
                    KeyValueRow("URL", it, mono = true)
                }
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
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent.copy(alpha = 0.5f),
    unfocusedBorderColor = Line,
    focusedContainerColor = Card2,
    unfocusedContainerColor = Card2,
    cursorColor = Accent,
    focusedTextColor = TextMain,
    unfocusedTextColor = TextMain
)
