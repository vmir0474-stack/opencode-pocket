package com.pocket.opencode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Установка Pocket Dev: proot уже внутри APK, Ubuntu base скачивается
 * с cdimage.ubuntu.com, проверяется по SHA-256 и распаковывается в
 * приватный каталог приложения. После установки агент получает команду
 * `dev` в PATH сервера.
 */
@Composable
fun DevPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by DevEnvironment.state.collectAsState()
    val info = remember { DeviceDiagnostics.collect(context) }

    var confirmReinstall by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DevEnvironment.refresh(context)
        if (DevEnvironment.state.value.installed) DevEnvironment.test(context)
    }

    Panel {
        SectionTitle("Pocket Dev") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(
                    if (state.toolsReady) "proot" else "proot нет",
                    if (state.toolsReady) Mint else Warn,
                )
                StatusPill(
                    if (state.installed) "rootfs" else "rootfs нет",
                    if (state.installed) Mint else Warn,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Ubuntu ${DevEnvironment.UBUNTU_VERSION} через proot. Работает без root " +
                "и без Termux: внутри доступны apt, git, node, python и другие пакеты. " +
                "Агент запускает окружение командой dev.",
            color = Muted,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(10.dp))

        KeyValueRow(
            "Версия",
            state.version.ifBlank { "не установлена" },
            valueColor = if (state.installed) Mint else Dim,
        )
        KeyValueRow(
            "Свободно",
            "${state.freeBytes / (1024 * 1024)} МБ",
            valueColor = if (state.freeBytes > 512L * 1024 * 1024) TextMain else Warn,
        )
        KeyValueRow("Каталог", state.rootfs, mono = true)
        KeyValueRow("Android", "${info.android} · API ${info.sdk}")
        KeyValueRow(
            "Страница",
            if (info.pageSize > 0) "${info.pageSize / 1024} КБ" else "?",
            valueColor = if (info.pageSize >= 16384) Warn else TextMain,
        )
        if (info.pageSize >= 16384) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Устройство использует страницы 16 КБ. Эта сборка proot на них " +
                    "ещё не проверена — запуск может не работать.",
                color = Warn,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }

        if (state.busy || state.phase.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(state.phase, color = Muted, fontSize = 11.5.sp)
            if (state.busy) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = Line,
                )
            }
        }

        if (state.error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(state.error, color = Warn, fontSize = 11.5.sp, lineHeight = 16.sp)
        }

        if (state.checked && state.broken) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Часть файлов окружения не читается: обычно так бывает после apt, " +
                    "запущенного из root-оболочки, — файлы получают чужие SELinux-метки. " +
                    "Переустановка скачает Ubuntu заново и вернёт доступ.",
                color = Warn,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.installed) {
                Button(
                    onClick = { scope.launch { DevEnvironment.install(context) } },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text(
                        if (state.busy) "Устанавливаю…" else "Установить Ubuntu",
                        fontSize = 11.5.sp,
                    )
                }
            }
            OutlinedButton(
                onClick = { scope.launch { DevEnvironment.test(context) } },
                enabled = !state.busy,
                shape = RoundedCornerShape(13.dp),
            ) {
                Text("Проверить окружение", fontSize = 11.5.sp)
            }
            if (state.installed && state.broken) {
                OutlinedButton(
                    onClick = { confirmReinstall = true },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text("Переустановить", fontSize = 11.5.sp)
                }
            }
        }

        if (confirmReinstall) {
            AlertDialog(
                onDismissRequest = { confirmReinstall = false },
                containerColor = Card,
                title = {
                    Text(
                        "Переустановить окружение?",
                        color = TextMain,
                        fontSize = 17.sp,
                    )
                },
                text = {
                    Text(
                        "Ubuntu будет скачана и распакована заново. Пакеты, установленные " +
                            "через apt, и файлы внутри окружения будут удалены. Настройки " +
                            "приложения и файлы Android не затрагиваются.",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmReinstall = false
                            scope.launch { DevEnvironment.reinstall(context) }
                        },
                    ) {
                        Text("Переустановить", color = Warn, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmReinstall = false }) {
                        Text("Отмена", color = Muted, fontSize = 12.sp)
                    }
                },
            )
        }

        if (state.test.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                state.test,
                color = Muted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }
    }
}
