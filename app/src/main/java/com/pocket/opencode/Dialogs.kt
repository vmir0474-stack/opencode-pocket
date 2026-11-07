package com.pocket.opencode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Показывает запросы, которые блокируют работу агента: разрешения и формы.
 * Разрешение имеет приоритет, форма появится сразу после ответа.
 */
@Composable
fun PendingDialogs(vm: PocketViewModel) {
    val permission = vm.permissions.firstOrNull()
    val form = vm.forms.firstOrNull()
    when {
        permission != null -> PermissionDialog(vm, permission)
        form != null -> FormDialog(vm, form)
    }
}

@Composable
private fun PermissionDialog(vm: PocketViewModel, request: PermissionAsk) {
    AlertDialog(
        onDismissRequest = { },
        containerColor = Card,
        icon = {
            Text(
                "Разрешение",
                color = Warn,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        title = {
            Text(
                permissionTitle(request.action),
                color = TextMain,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (request.message.isNotBlank()) {
                    Text(
                        request.message,
                        color = Muted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    request.action,
                    color = Accent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                if (request.resources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    request.resources.forEach { resource ->
                        Text(
                            resource,
                            color = TextMain.copy(alpha = 0.85f),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(CodeBg)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.replyPermission(request, "once") }) {
                Text("Разрешить", color = Mint, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.replyPermission(request, "always") }) {
                    Text("Всегда", color = Accent, fontSize = 12.sp)
                }
                TextButton(onClick = { vm.replyPermission(request, "reject") }) {
                    Text("Отклонить", color = Danger, fontSize = 12.sp)
                }
            }
        }
    )
}

private fun permissionTitle(action: String): String = when {
    action.contains("bash") || action.contains("shell") || action.contains("command") ->
        "Запустить команду?"

    action.contains("edit") || action.contains("write") || action.contains("patch") ->
        "Изменить файлы?"

    action.contains("read") || action.contains("glob") || action.contains("grep") ->
        "Прочитать файлы?"

    action.contains("webfetch") || action.contains("web") ->
        "Сходить в интернет?"

    else -> "Требуется разрешение"
}

@Composable
private fun FormDialog(vm: PocketViewModel, form: FormSpec) {
    val uriHandler = LocalUriHandler.current
    val values = remember(form.id) {
        mutableStateMapOf<String, Any?>().apply {
            form.fields.forEach { field -> put(field.key, initialValue(field)) }
        }
    }

    val visibleFields = form.fields.filter { field ->
        !field.hidden && conditionsMet(field, values)
    }
    val answerFields = visibleFields.filter { it.type != "external" }
    val ready = answerFields.all { field ->
        if (!field.required) true else !isBlankValue(values[field.key], field.type)
    }

    AlertDialog(
        onDismissRequest = { },
        containerColor = Card,
        // Без этого после закрытия клавиатуры окно диалога сохраняет старую
        // область касаний и кнопки «Ответить/Отмена» перестают нажиматься.
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        icon = {
            Text(
                "Форма",
                color = Accent,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        title = {
            Text(
                form.title,
                color = TextMain,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                visibleFields.filter { it.type != "external" }.forEach { field ->
                    FormFieldEditor(field = field, values = values)
                }
                if (visibleFields.any { it.type == "external" }) {
                    HorizontalDivider(color = Line)
                }
                visibleFields.filter { it.type == "external" && it.url.isNotBlank() }
                    .forEach { field ->
                        Column {
                            Text(
                                field.title,
                                color = TextMain,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (field.description.isNotBlank()) {
                                Text(field.description, color = Muted, fontSize = 10.5.sp)
                            }
                            Text(
                                field.url,
                                color = Accent,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .clickable { runCatching { uriHandler.openUri(field.url) } }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = { vm.replyForm(form, buildAnswer(form, values)) }
            ) {
                Text(
                    "Ответить",
                    color = if (ready) Mint else Dim,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.cancelForm(form) }) {
                Text("Отмена", color = Muted, fontSize = 12.sp)
            }
        }
    )
}

@Composable
private fun FormFieldEditor(field: FormFieldSpec, values: MutableMap<String, Any?>) {
    val label = field.title.ifBlank { field.key }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = TextMain,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (field.required) {
                Text("обязательно", color = Warn, fontSize = 9.sp)
            }
        }
        if (field.description.isNotBlank()) {
            Text(
                field.description,
                color = Muted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }
        if (field.type == "external") {
            return@Column
        }

        when {
            field.type == "boolean" -> {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (values[field.key] == true) "Да" else "Нет",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = values[field.key] == true,
                        onCheckedChange = { values[field.key] = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF241505),
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = Muted,
                            uncheckedTrackColor = Card2,
                            uncheckedBorderColor = Line
                        )
                    )
                }
            }

            field.type == "multiselect" -> {
                val selected = (values[field.key] as? Set<*>)?.filterIsInstance<String>()?.toSet()
                    ?: emptySet()
                field.options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                values[field.key] = if (selected.contains(option.value)) {
                                    selected - option.value
                                } else {
                                    selected + option.value
                                }
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selected.contains(option.value),
                            onCheckedChange = {
                                values[field.key] = if (selected.contains(option.value)) {
                                    selected - option.value
                                } else {
                                    selected + option.value
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Accent,
                                checkmarkColor = Color(0xFF241505),
                                uncheckedColor = Dim
                            )
                        )
                        Column(Modifier.weight(1f)) {
                            Text(option.label, color = TextMain, fontSize = 12.sp)
                            if (option.description.isNotBlank()) {
                                Text(option.description, color = Dim, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            field.options.isNotEmpty() -> {
                val current = values[field.key]?.toString().orEmpty()
                field.options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { values[field.key] = option.value }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = current == option.value,
                            onClick = { values[field.key] = option.value },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Accent,
                                unselectedColor = Dim
                            )
                        )
                        Column(Modifier.weight(1f)) {
                            Text(option.label, color = TextMain, fontSize = 12.sp)
                            if (option.description.isNotBlank()) {
                                Text(option.description, color = Dim, fontSize = 10.sp)
                            }
                        }
                    }
                }
                if (field.custom) {
                    // Показываем только введённое вручную значение, а не
                    // выбранный пресет: иначе кажется, что текст уже введён.
                    val customValue = current.takeIf { value ->
                        field.options.none { it.value == value }
                    }.orEmpty()
                    FormTextField(
                        value = customValue,
                        onValueChange = { values[field.key] = it },
                        placeholder = "Своё значение",
                        keyboardType = KeyboardType.Text
                    )
                }
            }

            field.type == "number" || field.type == "integer" -> {
                FormTextField(
                    value = values[field.key]?.toString().orEmpty(),
                    onValueChange = { values[field.key] = it },
                    placeholder = field.placeholder.ifBlank {
                        if (field.type == "integer") "целое число" else "число"
                    },
                    keyboardType = KeyboardType.Number,
                    hint = numberHint(field)
                )
            }

            else -> {
                FormTextField(
                    value = values[field.key]?.toString().orEmpty(),
                    onValueChange = { values[field.key] = it },
                    placeholder = field.placeholder.ifBlank { label },
                    keyboardType = when (field.format) {
                        "email" -> KeyboardType.Email
                        "uri" -> KeyboardType.Uri
                        else -> KeyboardType.Text
                    },
                    hint = field.format.takeIf { it.isNotBlank() }
                )
            }
        }
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    hint: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = Dim, fontSize = 12.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(
            color = TextMain,
            fontSize = 13.sp
        ),
        shape = RoundedCornerShape(11.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent.copy(alpha = 0.5f),
            unfocusedBorderColor = Line,
            focusedContainerColor = Card2,
            unfocusedContainerColor = Card2,
            cursorColor = Accent
        )
    )
    if (!hint.isNullOrBlank()) {
        Text(
            hint,
            color = Dim,
            fontSize = 9.5.sp,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}

private fun numberHint(field: FormFieldSpec): String? {
    val min = field.minimum
    val max = field.maximum
    return when {
        min != null && max != null -> "от ${trimNumber(min)} до ${trimNumber(max)}"
        min != null -> "не меньше ${trimNumber(min)}"
        max != null -> "не больше ${trimNumber(max)}"
        else -> null
    }
}

private fun trimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun initialValue(field: FormFieldSpec): Any? = when (field.type) {
    "boolean" -> field.defaultValue as? Boolean ?: false
    "multiselect" -> (field.defaultValue as? Set<*>)?.filterIsInstance<String>()?.toSet()
        ?: emptySet<String>()

    "number", "integer" -> when (val value = field.defaultValue) {
        is Number -> value.toString()
        is String -> value
        else -> ""
    }

    else -> field.defaultValue as? String ?: ""
}

private fun isBlankValue(value: Any?, type: String): Boolean = when (type) {
    "boolean" -> false
    "multiselect" -> (value as? Set<*>)?.isEmpty() ?: true
    else -> value?.toString().isNullOrBlank()
}

private fun conditionsMet(field: FormFieldSpec, values: Map<String, Any?>): Boolean =
    field.conditions.all { condition ->
        val current = values[condition.key]
        val text = when (current) {
            is Set<*> -> current.joinToString(",")
            null -> ""
            else -> current.toString()
        }
        if (condition.op == "neq") text != condition.value else text == condition.value
    }

private fun buildAnswer(form: FormSpec, values: Map<String, Any?>): JSONObject {
    val answer = JSONObject()
    form.fields.forEach { field ->
        if (field.hidden || field.type == "external" || !conditionsMet(field, values)) return@forEach
        val value = values[field.key]
        when (field.type) {
            "boolean" -> answer.put(field.key, value == true)
            "number" -> (value?.toString()?.toDoubleOrNull())?.let { answer.put(field.key, it) }
            "integer" -> (value?.toString()?.toLongOrNull())?.let { answer.put(field.key, it) }
            "multiselect" -> {
                val selected = (value as? Set<*>)?.filterIsInstance<String>() ?: emptyList()
                answer.put(field.key, JSONArray(selected))
            }
            else -> {
                val text = value?.toString().orEmpty()
                if (text.isNotBlank()) answer.put(field.key, text)
            }
        }
    }
    return answer
}
