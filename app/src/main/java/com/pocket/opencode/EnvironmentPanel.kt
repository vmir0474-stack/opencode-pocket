package com.pocket.opencode

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Панель показывает не рекламные обещания, а фактический результат проверки
 * PATH сервера. На rootless-телефоне видно, что доступно, а что нет, и
 * почему локальные stdio MCP не запустятся.
 */
@Composable
fun EnvironmentPanel(vm: PocketViewModel) {
    LaunchedEffect(Unit) { vm.refreshEnvironment() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Panel {
        SectionTitle("Окружение") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val report = vm.environment
                if (report != null) {
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(environmentSummary(report))) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Text("Копировать", color = Muted, fontSize = 11.sp)
                    }
                }
                TextButton(
                    onClick = { vm.refreshEnvironment() },
                    enabled = !vm.environmentBusy,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 0.dp
                    )
                ) {
                    Text(if (vm.environmentBusy) "…" else "Обновить", color = Accent, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        val report = vm.environment
        if (report == null) {
            Text(
                if (vm.environmentBusy) "Проверяю окружение…" else "Окружение ещё не проверено",
                color = Muted,
                fontSize = 11.5.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.refreshEnvironment() },
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Проверить окружение", fontSize = 11.5.sp)
            }
        } else {
            KeyValueRow(
                "Android",
                "${report.release.ifBlank { "?" }} · API ${report.sdk} · ${report.abi}"
            )
            KeyValueRow(
                "Хранилище",
                when (report.storageMode) {
                    "all_files" -> "Доступ ко всем файлам"
                    "legacy" -> "Разрешение Android (до 11)"
                    else -> "Только внутренняя папка"
                },
                valueColor = if (report.storageAccess) Mint else Warn
            )
            KeyValueRow(
                "Медиа",
                if (report.mediaAccess) "Чтение разрешено" else "Чтение не разрешено",
                valueColor = if (report.mediaAccess) Mint else Warn
            )
            KeyValueRow(
                "Батарея",
                if (report.batteryExempt) "Ограничения сняты" else "Оптимизируется системой",
                valueColor = if (report.batteryExempt) Mint else Warn
            )
            KeyValueRow(
                "Root",
                if (report.rootBinary) "su найден, приложению не нужен" else "не требуется"
            )
            KeyValueRow("Каталог", report.workspace, mono = true)

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Line)
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Доступные команды",
                    color = Muted,
                    fontSize = 11.5.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StatusPill("${report.available.size}", Mint)
            }
            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (report.availableNames.isEmpty()) {
                    Text("нет", color = Dim, fontSize = 11.sp)
                } else {
                    report.availableNames.forEach { name ->
                        MetricPill(name, Mint)
                    }
                }
            }

            if (report.missing.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Недоступны",
                    color = Muted,
                    fontSize = 11.5.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    report.missing.joinToString(", "),
                    color = Dim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!report.storageAccess) {
                    Button(
                        onClick = { vm.openAllFilesSettings() },
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Разрешить доступ к файлам", fontSize = 11.5.sp)
                    }
                }
                if (!report.batteryExempt) {
                    OutlinedButton(
                        onClick = {
                            if (!vm.openBatterySettings()) {
                                Toast.makeText(
                                    context,
                                    "Настройки батареи не открылись: Приложения → " +
                                        "OpenCode Pocket → Батарея",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Снять ограничения батареи", fontSize = 11.5.sp)
                    }
                } else if (report.storageAccess) {
                    OutlinedButton(
                        onClick = {
                            if (!vm.openBatterySettings()) {
                                Toast.makeText(
                                    context,
                                    "Настройки батареи не открылись: Приложения → " +
                                        "OpenCode Pocket → Батарея",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Настройки батареи", fontSize = 11.5.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            if (!vm.isEmbedded) {
                Text(
                    "Сейчас выбран внешний сервер: его PATH может отличаться. " +
                        "Здесь показано окружение приложения и встроенного сервера.",
                    color = Warn,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Сервер, rg, pocket-rs и удалённые MCP работают без root. " +
                    "git, node, python и локальные stdio MCP в APK отсутствуют — " +
                    "это ограничение сборки, а не доступов. Список команд записан " +
                    "в pocket-environment.md; при обычном JSON-конфиге путь " +
                    "автоматически добавлен в instructions opencode.json, " +
                    "поэтому агент видит его при старте.",
                color = Dim,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

private fun environmentSummary(report: EnvReport): String = buildString {
    appendLine("OpenCode Pocket · окружение")
    appendLine("Android ${report.release.ifBlank { "?" }} · API ${report.sdk} · ${report.abi}")
    appendLine(
        "Хранилище: " + when (report.storageMode) {
            "all_files" -> "доступ ко всем файлам"
            "legacy" -> "разрешение Android (до 11)"
            else -> "только внутренняя папка"
        }
    )
    appendLine("Батарея: " + if (report.batteryExempt) "ограничения сняты" else "оптимизируется системой")
    appendLine("Root: " + if (report.rootBinary) "su найден, приложению не нужен" else "не требуется")
    appendLine("Каталог: ${report.workspace}")
    appendLine("PATH сервера: ${report.serverPath}")
    appendLine()
    appendLine("Доступно (${report.available.size}): ${report.availableNames.joinToString(", ").ifBlank { "нет" }}")
    if (report.missing.isNotEmpty()) {
        appendLine("Недоступно: ${report.missing.joinToString(", ")}")
    }
}
