package com.pocket.opencode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MigrationPanel(vm: PocketViewModel) {
    var confirmImport by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val command = "mkdir -p /sdcard/OpenCode/import\n" +
        "tar czf /sdcard/OpenCode/import/opencode-backup.tar.gz -C \$HOME " +
        ".local/share/opencode .config/opencode"

    Panel {
        SectionTitle("Перенос из Termux")
        Spacer(Modifier.height(8.dp))
        Text(
            "Сделай архив в Termux, затем укажи его путь здесь. Сессии, скилы и ключи " +
                "переедут в приложение, а текущие данные сохранятся в резервную копию.",
            color = Muted,
            fontSize = 11.5.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(
                command,
                color = TextMain,
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CodeBg)
                    .padding(10.dp)
            )
        }
        TextButton(
            onClick = { clipboard.setText(AnnotatedString(command)) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 8.dp,
                vertical = 0.dp
            )
        ) {
            Text("Копировать команду", color = Accent, fontSize = 11.sp)
        }

        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = vm.migrationPath,
            onValueChange = vm::onMigrationPathChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Путь к архиву", color = Muted, fontSize = 11.sp) },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent.copy(alpha = 0.5f),
                unfocusedBorderColor = Line,
                focusedContainerColor = Card2,
                unfocusedContainerColor = Card2,
                cursorColor = Accent,
                focusedTextColor = TextMain,
                unfocusedTextColor = TextMain
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.previewMigration() },
                shape = RoundedCornerShape(13.dp),
                enabled = !vm.migrationBusy
            ) {
                Text("Проверить архив", fontSize = 11.5.sp)
            }
            Button(
                onClick = { confirmImport = true },
                shape = RoundedCornerShape(13.dp),
                enabled = !vm.migrationBusy
            ) {
                Text("Импортировать", fontSize = 11.5.sp)
            }
        }

        if (vm.migrationBusy || vm.migrationNotice.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.migrationBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = Accent,
                        strokeWidth = 1.5.dp
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    vm.migrationNotice,
                    color = if (vm.migrationBusy) Muted else Mint,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        if (vm.migrationError.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(vm.migrationError, color = Danger, fontSize = 11.sp, lineHeight = 16.sp)
        }

        vm.migrationPreview?.let { preview ->
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Сессии", preview.sessions.toString())
            KeyValueRow("Скилы", preview.skills.toString())
            KeyValueRow("Ключи", preview.credentials.toString())
            if (preview.problems.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                preview.problems.forEach {
                    Text(it, color = Warn, fontSize = 10.5.sp, lineHeight = 15.sp)
                }
            }
        }

        vm.migrationResult?.let { result ->
            Spacer(Modifier.height(10.dp))
            Text(
                "Импорт завершён: сессий ${result.sessions}, скилов ${result.skills}, " +
                    "ключей ${result.credentials}.",
                color = Mint,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Резервная копия: ${result.backupDir.absolutePath}",
                color = Muted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
            if (result.warnings.isNotEmpty()) {
                result.warnings.take(5).forEach {
                    Text(it, color = Warn, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(result.backupDir.absolutePath))
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 0.dp
                )
            ) {
                Text("Копировать путь к копии", color = Accent, fontSize = 11.sp)
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            containerColor = Card,
            title = { Text("Импортировать из Termux?", color = TextMain, fontSize = 16.sp) },
            text = {
                Text(
                    "Сервер будет остановлен. Текущие сессии, скилы и ключи сохранятся " +
                        "в папке backup внутри данных приложения.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    vm.importMigration()
                }) {
                    Text("Импортировать", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) {
                    Text("Отмена", color = Muted)
                }
            }
        )
    }
}
