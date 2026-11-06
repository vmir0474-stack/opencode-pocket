package com.pocket.opencode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SkillsScreen(vm: PocketViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<SkillInfo?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadSkills() }

    if (creating || editing != null) {
        SkillEditor(vm, if (creating) null else editing) {
            creating = false
            editing = null
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Скилы", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Библиотека OpenCode", color = Muted, fontSize = 11.5.sp)
            }
            if (vm.skillBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Accent,
                    strokeWidth = 1.5.dp
                )
            } else {
                TextButton(onClick = { vm.loadSkills() }) {
                    Text("Обновить", color = Accent, fontSize = 11.5.sp)
                }
            }
        }

        if (vm.skillStatus.isNotBlank()) Text(vm.skillStatus, color = Mint, fontSize = 11.5.sp)
        if (vm.skillError.isNotBlank()) {
            Text(vm.skillError, color = Danger, fontSize = 11.5.sp, lineHeight = 16.sp)
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск скила", color = Muted, fontSize = 11.sp) },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            colors = skillFieldColors()
        )

        Button(
            onClick = { creating = true },
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Новый скил")
        }

        Panel {
            SectionTitle("Скилы сервера")
            Spacer(Modifier.height(8.dp))
            val filtered = vm.skills.filter {
                search.isBlank() ||
                    it.name.contains(search, ignoreCase = true) ||
                    it.id.contains(search, ignoreCase = true) ||
                    it.description.contains(search, ignoreCase = true)
            }
            if (filtered.isEmpty()) {
                Text(
                    if (vm.skills.isEmpty()) "Скилы не загружены" else "Ничего не найдено",
                    color = Dim,
                    fontSize = 11.5.sp
                )
            } else {
                filtered.forEachIndexed { index, skill ->
                    if (index > 0) HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = skill }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                skill.name,
                                color = TextMain,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (skill.description.isNotBlank()) {
                                Text(
                                    skill.description,
                                    color = Muted,
                                    fontSize = 10.5.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            skillSource(skill),
                            color = when (skillSource(skill)) {
                                "builtin" -> Dim
                                "project" -> Mint
                                else -> Accent
                            },
                            fontSize = 9.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillEditor(vm: PocketViewModel, original: SkillInfo?, onClose: () -> Unit) {
    var name by remember(original?.id) { mutableStateOf(original?.name.orEmpty()) }
    var description by remember(original?.id) { mutableStateOf(original?.description.orEmpty()) }
    var body by remember(original?.id) { mutableStateOf(original?.let { skillBody(it.content) }.orEmpty()) }
    var project by remember(original?.id) {
        mutableStateOf(original?.path?.contains("/.opencode/skills/") == true)
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val readOnly = original != null && !vm.isSkillWritable(original.path)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) {
                Text("Назад", color = Accent, fontSize = 12.sp)
            }
            Text(
                if (original == null) "Новый скил" else "Скил",
                color = TextMain,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (readOnly) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(original?.content.orEmpty())) }) {
                    Text("Копировать", color = Accent, fontSize = 11.sp)
                }
            }
        }

        if (readOnly) {
            Text(
                "Встроенный скил — только чтение. Скопируй текст и создай свой.",
                color = Warn,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Название", color = Muted, fontSize = 11.sp) },
            singleLine = true,
            enabled = !readOnly,
            shape = RoundedCornerShape(13.dp),
            colors = skillFieldColors()
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Описание", color = Muted, fontSize = 11.sp) },
            enabled = !readOnly,
            shape = RoundedCornerShape(13.dp),
            colors = skillFieldColors()
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            label = { Text("Инструкции (markdown)", color = Muted, fontSize = 11.sp) },
            enabled = !readOnly,
            shape = RoundedCornerShape(13.dp),
            colors = skillFieldColors()
        )

        if (!readOnly && original == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(
                    label = "Глобальный",
                    accent = !project,
                    onClick = { project = false }
                )
                ChipButton(
                    label = "Проектный",
                    accent = project,
                    onClick = { project = true }
                )
            }
        }

        if (vm.skillError.isNotBlank()) {
            Text(vm.skillError, color = Danger, fontSize = 11.5.sp, lineHeight = 16.sp)
        }

        if (!readOnly) {
            Button(
                onClick = {
                    vm.saveSkill(name, description, body, project)
                    onClose()
                },
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && !vm.skillBusy
            ) {
                Text("Сохранить")
            }
        }
        if (original != null && !readOnly) {
            OutlinedButton(
                onClick = { confirmDelete = true },
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Удалить скил", color = Danger)
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Card,
            title = { Text("Удалить скил?", color = TextMain, fontSize = 16.sp) },
            text = {
                Text(original.path, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteSkill(original.path)
                    onClose()
                }) {
                    Text("Удалить", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена", color = Muted)
                }
            }
        )
    }
}

private fun skillSource(skill: SkillInfo): String = when {
    skill.path.contains("/.opencode/skills/") -> "project"
    skill.path.contains("/opencode/skills/") -> "global"
    else -> "builtin"
}

private fun skillBody(content: String): String {
    val text = content.trimStart()
    if (!text.startsWith("---")) return content
    val end = text.indexOf("\n---", 3)
    if (end < 0) return content
    return text.substring(end + 4).trimStart('\n')
}

@Composable
private fun skillFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent.copy(alpha = 0.5f),
    unfocusedBorderColor = Line,
    focusedContainerColor = Card2,
    unfocusedContainerColor = Card2,
    cursorColor = Accent,
    focusedTextColor = TextMain,
    unfocusedTextColor = TextMain,
    disabledTextColor = Muted,
    disabledBorderColor = Line,
    disabledContainerColor = Card
)
