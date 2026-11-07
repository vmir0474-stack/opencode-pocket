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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProvidersScreen(vm: PocketViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var keyTarget by remember { mutableStateOf<IntegrationInfo?>(null) }
    var keyText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadProviders() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = "Модели",
            subtitle = "Провайдеры, свои ключи и выбор модели"
        ) {
            if (vm.providerBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Accent,
                    strokeWidth = 1.5.dp
                )
            } else {
                TextButton(onClick = {
                    vm.loadProviders()
                    vm.refreshModels()
                }) {
                    Text("Обновить", color = Accent, fontSize = 11.5.sp)
                }
            }
        }

        if (vm.providerStatus.isNotBlank()) {
            Text(vm.providerStatus, color = Mint, fontSize = 11.5.sp)
        }
        if (vm.providerError.isNotBlank()) {
            Text(vm.providerError, color = Danger, fontSize = 11.5.sp, lineHeight = 16.sp)
        }

        Panel {
            SectionTitle("Подключённые провайдеры")
            Spacer(Modifier.height(8.dp))
            val connected = vm.integrations.filter { it.connected }
            if (connected.isEmpty()) {
                Text(
                    "Пока только бесплатные модели OpenCode. Добавь свой ключ, чтобы снять лимит.",
                    color = Muted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            } else {
                connected.forEachIndexed { index, provider ->
                    if (index > 0) HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 8.dp))
                    val creds = vm.credentials.filter { it.integrationID == provider.id }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(provider.name, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            val labels = when {
                                creds.isNotEmpty() -> creds.joinToString(", ") { it.label }
                                provider.connections.any { it.type == "env" } -> "переменная окружения"
                                else -> provider.connections.joinToString(", ") { it.type }
                            }
                            Text(labels, color = Muted, fontSize = 10.5.sp)
                        }
                        if (creds.isNotEmpty()) {
                            TextButton(onClick = { vm.removeProvider(creds.first().id) }) {
                                Text("Удалить", color = Danger, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        Panel {
            SectionTitle("Добавить провайдера")
            Spacer(Modifier.height(10.dp))
            PocketSearchField(
                value = search,
                onValueChange = { search = it },
                placeholder = "Поиск: anthropic, openai, openrouter…"
            )
            Spacer(Modifier.height(8.dp))
            val all = vm.integrations.filter { ProviderCatalog.matches(it, search) }
            val limit = if (search.isBlank() && !showAll) ProviderCatalog.curated.size else 60
            val shown = if (search.isBlank() && !showAll) {
                all.filter { it.id in ProviderCatalog.curated }
            } else {
                all.take(limit)
            }
            if (vm.integrations.isEmpty()) {
                Text(
                    "Список провайдеров не загружен — проверь подключение к серверу.",
                    color = Dim,
                    fontSize = 11.5.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    shown.forEach { provider ->
                        ProviderRow(provider) {
                            if (provider.supportsKey) {
                                keyTarget = provider
                                keyText = ""
                            } else {
                                vm.providerStatus = "Для ${provider.name} нужен вход через браузер — позже"
                            }
                        }
                    }
                }
                if (search.isBlank() && !showAll && all.size > shown.size) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showAll = true },
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Показать всех (${all.size})", fontSize = 11.5.sp)
                    }
                }
            }
        }

        Panel {
            SectionTitle("Модели")
            Spacer(Modifier.height(10.dp))
            PocketSearchField(
                value = modelSearch,
                onValueChange = { modelSearch = it },
                placeholder = "Поиск модели или провайдера"
            )
            Spacer(Modifier.height(10.dp))
            val filtered = vm.models.filter {
                modelSearch.isBlank() ||
                    it.name.contains(modelSearch, ignoreCase = true) ||
                    it.id.contains(modelSearch, ignoreCase = true) ||
                    it.providerID.contains(modelSearch, ignoreCase = true)
            }
            if (filtered.isEmpty()) {
                Text("Модели не найдены", color = Dim, fontSize = 11.5.sp)
            } else {
                filtered.groupBy { it.providerID }.forEach { (provider, list) ->
                    Text(
                        provider,
                        color = Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    list.forEach { model ->
                        val active = model.id == vm.selectedModelId &&
                            model.providerID == vm.selectedModelProvider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { vm.selectModel(model) }
                                .background(if (active) AccentDim else Card)
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    model.name,
                                    color = if (active) Accent else TextMain,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    model.id,
                                    color = Dim,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (active) {
                                Text("выбрана", color = Accent, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    keyTarget?.let { provider ->
        AlertDialog(
            onDismissRequest = {
                keyTarget = null
                keyText = ""
            },
            containerColor = Card,
            title = { Text("Ключ ${provider.name}", color = TextMain, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "Ключ сохраняется в базе OpenCode на устройстве и отправляется только этому провайдеру.",
                        color = Muted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API-ключ", color = Muted, fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(13.dp),
                        colors = providerFieldColors()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.addProviderKey(provider.id, keyText)
                        keyTarget = null
                        keyText = ""
                    },
                    enabled = keyText.isNotBlank() && !vm.providerBusy
                ) {
                    Text("Проверить и сохранить", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    keyTarget = null
                    keyText = ""
                }) {
                    Text("Отмена", color = Muted)
                }
            }
        )
    }
}

@Composable
private fun ProviderRow(provider: IntegrationInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                provider.name,
                color = TextMain,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                provider.id,
                color = Dim,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        val hint = when {
            provider.connected -> "подключён"
            provider.supportsKey -> "ключ"
            else -> "браузер"
        }
        Text(
            hint,
            color = if (provider.connected) Mint else if (provider.supportsKey) Accent else Dim,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun providerFieldColors() = pocketFieldColors()
