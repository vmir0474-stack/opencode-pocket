package com.pocket.opencode

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ConnState { IDLE, CONNECTING, CONNECTED, RECONNECTING, ERROR }

private data class ShellMeta(val command: String, val sessionID: String)

class PocketViewModel(application: Application) : AndroidViewModel(application) {
    private val api = OpenCodeApi()
    private val settings = SettingsStore(application)
    private val cache = SessionCache(application)

    var tab by mutableIntStateOf(settings.tab)
    var baseUrl by mutableStateOf(settings.baseUrl)
    var password by mutableStateOf(settings.password)
    var status by mutableStateOf(ConnState.IDLE)
    var statusDetail by mutableStateOf("")
    var serverInfo by mutableStateOf<ServerInfo?>(null)
    var location by mutableStateOf<LocationInfo?>(null)
    var vcsSummary by mutableStateOf<VcsSummary?>(null)
    var vcsFiles by mutableStateOf<List<VcsFile>>(emptyList())
    var projectFiles by mutableStateOf<List<FileEntry>>(emptyList())
    var projectPath by mutableStateOf(".")
    var projectBusy by mutableStateOf(false)
    var projectLoaded by mutableStateOf(false)
    var activeSessions by mutableStateOf<Set<String>>(emptySet())

    var serverStatus by mutableStateOf(EmbeddedServer.status.value)
    var serverLog by mutableStateOf<List<String>>(emptyList())

    val isEmbedded: Boolean get() = settings.mode == ServerMode.EMBEDDED

    var sessionId by mutableStateOf(settings.sessionId)
    var sessionTitle by mutableStateOf(settings.sessionTitle)
    var sessionDirectory by mutableStateOf("")
    var sessionCost by mutableStateOf(0.0)
    var tokensIn by mutableStateOf(0L)
    var tokensOut by mutableStateOf(0L)
    var sessionOutcome by mutableStateOf<String?>(null)
    var messages by mutableStateOf<List<UiMessage>>(emptyList())
    var sessions by mutableStateOf<List<SessionInfo>>(emptyList())
    var running by mutableStateOf(false)
    var busy by mutableStateOf(false)

    var models by mutableStateOf<List<ModelChoice>>(emptyList())
    var agents by mutableStateOf<List<AgentChoice>>(emptyList())
    var selectedModelProvider by mutableStateOf(settings.modelProvider)
    var selectedModelId by mutableStateOf(settings.modelId)
    var selectedVariant by mutableStateOf(settings.modelVariant)
    var selectedAgent by mutableStateOf(settings.agent)

    var permissions by mutableStateOf<List<PermissionAsk>>(emptyList())
    var forms by mutableStateOf<List<FormSpec>>(emptyList())
    var input by mutableStateOf(settings.draft)

    private var connectionJob: Job? = null
    private var refreshJob: Job? = null
    private var cacheJob: Job? = null
    private var settingsJob: Job? = null
    private var embeddedKey: String? = null
    private val shellJobs = mutableMapOf<String, Job>()
    private val pendingShells = mutableMapOf<String, Job>()
    private val shellStatus = mutableMapOf<String, Pair<String, Int?>>()
    private val shellMeta = mutableMapOf<String, ShellMeta>()
    private val toolNames = mutableMapOf<String, String>()

    val statusLabel: String
        get() = when (status) {
            ConnState.CONNECTED -> if (running) "Работает" else "Подключено"
            ConnState.CONNECTING -> "Подключение"
            ConnState.RECONNECTING -> "Переподключение"
            ConnState.ERROR -> "Нет связи"
            ConnState.IDLE -> "Не подключено"
        }

    val sessionLabel: String
        get() = sessionTitle.takeIf { it.isNotBlank() }
            ?: sessionId?.take(26)
            ?: "Сессия не создана"

    val directoryLabel: String
        get() = sessionDirectory.takeIf { it.isNotBlank() }
            ?: location?.directory.orEmpty()

    val selectedModelChoice: ModelChoice?
        get() = models.firstOrNull {
            it.id == selectedModelId && it.providerID == selectedModelProvider
        }

    val modelLabel: String
        get() = selectedModelChoice?.name ?: selectedModelId ?: "модель"

    val agentLabel: String
        get() = agents.firstOrNull { it.id == selectedAgent }?.name
            ?: selectedAgent ?: "агент"

    val variantLabel: String?
        get() = selectedVariant?.takeIf { it.isNotBlank() }

    val usageLabel: String
        get() = if (tokensIn == 0L && tokensOut == 0L) "" else "$tokensIn → $tokensOut"

    init {
        val saved = settings.sessionId
        if (saved != null) {
            viewModelScope.launch {
                val cached = withContext(Dispatchers.IO) { cache.load(saved) }
                if (sessionId == saved && messages.isEmpty()) messages = cached
            }
        }
        viewModelScope.launch {
            EmbeddedServer.log.collect { serverLog = it }
        }
        viewModelScope.launch {
            EmbeddedServer.status.collect { onEmbeddedStatus(it) }
        }
        if (settings.mode == ServerMode.EXTERNAL) {
            if (settings.baseUrl.isNotBlank()) connect()
        }
    }

    private fun onEmbeddedStatus(status: EmbeddedStatus) {
        serverStatus = status
        if (settings.mode != ServerMode.EMBEDDED) return
        when (status.state) {
            EmbeddedState.RUNNING -> {
                val key = status.url + "|" + status.password
                if (key != embeddedKey) {
                    embeddedKey = key
                    baseUrl = status.url
                    password = status.password
                    connect()
                }
            }

            EmbeddedState.STARTING -> {
                embeddedKey = null
                connectionJob?.cancel()
                this.status = ConnState.CONNECTING
                statusDetail = "Запускаю встроенный OpenCode…"
            }

            EmbeddedState.ERROR -> {
                embeddedKey = null
                connectionJob?.cancel()
                this.status = ConnState.ERROR
                statusDetail = status.error.ifBlank { "Встроенный сервер не запустился" }
            }

            EmbeddedState.STOPPED -> {
                embeddedKey = null
                connectionJob?.cancel()
                this.status = ConnState.IDLE
                statusDetail = "Встроенный сервер остановлен"
            }
        }
    }

    fun setMode(mode: ServerMode) {
        if (settings.modeWasSet && settings.mode == mode) return
        settings.mode = mode
        if (mode == ServerMode.EMBEDDED) {
            connectionJob?.cancel()
            status = ConnState.CONNECTING
            statusDetail = "Запускаю встроенный OpenCode…"
            ServerService.start(getApplication())
        } else {
            ServerService.stop(getApplication())
            embeddedKey = null
            connectionJob?.cancel()
            baseUrl = settings.baseUrl
            password = settings.password
            status = ConnState.IDLE
            statusDetail = ""
            if (baseUrl.isNotBlank()) connect()
        }
    }

    fun startEmbedded() {
        if (settings.mode != ServerMode.EMBEDDED) {
            setMode(ServerMode.EMBEDDED)
        } else {
            ServerService.start(getApplication())
        }
    }

    fun stopEmbedded() {
        ServerService.stop(getApplication())
    }

    fun restartEmbedded() {
        if (settings.mode != ServerMode.EMBEDDED) {
            setMode(ServerMode.EMBEDDED)
        } else {
            ServerService.restart(getApplication())
        }
    }

    fun normalizeUrl(value: String): String {
        var url = value.trim()
        if (url.isEmpty()) return "http://127.0.0.1:4096"
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        return url.trimEnd('/')
    }

    fun onBaseUrlChange(value: String) {
        baseUrl = value
        queueSave()
    }

    fun onPasswordChange(value: String) {
        password = value
        queueSave()
    }

    fun onInputChange(value: String) {
        input = value
        queueSave()
    }

    fun selectTab(value: Int) {
        tab = value
        settings.tab = value
        if (value == 1 && status == ConnState.CONNECTED && !projectLoaded) refreshProject()
    }

    fun connect() {
        baseUrl = normalizeUrl(baseUrl)
        saveNow()
        connectionJob?.cancel()
        status = ConnState.CONNECTING
        statusDetail = ""
        connectionJob = viewModelScope.launch { connectionLoop() }
    }

    private suspend fun connectionLoop() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            try {
                status = if (attempt == 0) ConnState.CONNECTING else ConnState.RECONNECTING
                val info = api.info(baseUrl, password)
                serverInfo = info
                status = ConnState.CONNECTED
                statusDetail = ""
                attempt = 0
                api.events(baseUrl, password).collect { event -> handleEvent(event) }
                if (currentCoroutineContext().isActive) {
                    status = ConnState.RECONNECTING
                    statusDetail = "Поток событий закрыт сервером"
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                status = ConnState.ERROR
                statusDetail = error.message ?: "Нет соединения с сервером"
                running = false
            }
            if (!currentCoroutineContext().isActive) break
            attempt++
            delay((1000L * (1 shl attempt.coerceAtMost(4))).coerceAtMost(15_000L))
        }
    }

    fun refreshEverything() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { refreshAllSuspend() }
    }

    private suspend fun refreshAllSuspend() {
        var failed = 0

        runCatching { api.location(baseUrl, password) }
            .onSuccess { location = it }
            .onFailure { failed++ }

        runCatching { api.sessions(baseUrl, password) }
            .onSuccess { sessions = it.filter { session -> !session.archived } }
            .onFailure { failed++ }

        runCatching { api.agents(baseUrl, password) }
            .onSuccess { list ->
                val visible = list.filter { !it.hidden && it.mode != "subagent" }
                if (visible.isNotEmpty()) agents = visible
                if (selectedAgent == null) selectedAgent = visible.firstOrNull()?.id
            }

        runCatching { loadModelsForRefresh() }
            .onSuccess { list ->
                val enabled = list.filter { it.enabled }
                if (enabled.isNotEmpty()) models = enabled
                if (selectedModelId == null) {
                    enabled.firstOrNull()?.let {
                        selectedModelProvider = it.providerID
                        selectedModelId = it.id
                    }
                }
            }

        runCatching { api.activeSessions(baseUrl, password) }
            .onSuccess { activeSessions = it }

        val sid = sessionId
        if (sid != null) {
            var info = sessions.firstOrNull { it.id == sid }
            if (info == null) {
                info = runCatching { api.session(baseUrl, password, sid) }.getOrNull()
            }
            if (info != null) {
                applySessionInfo(info)
                refreshMessagesSuspend(sid)
                refreshPendingSuspend(sid)
                running = activeSessions.contains(sid)
            } else if (failed == 0) {
                sessionId = null
                sessionTitle = ""
                messages = emptyList()
                settings.saveSession(null, "")
                statusDetail = "Сохранённая сессия больше не существует на сервере"
            }
        }

        loadProjectSuspend()
        if (failed > 0) statusDetail = "Часть данных не загрузилась, проверь связь"
        saveNow()
    }

    /**
     * Настоящий сервер несколько секунд после старта отвечает 503
     * «Location plugins did not finish activating» на /api/model.
     * Во встроенном режиме ждём, а не показываем пустой список.
     */
    private suspend fun loadModelsForRefresh(): List<ModelChoice> {
        if (!isEmbedded) return api.models(baseUrl, password)
        val deadline = System.currentTimeMillis() + 30_000
        var last: Exception = IllegalStateException("Модели не загрузились")
        while (currentCoroutineContext().isActive) {
            try {
                return api.models(baseUrl, password)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                last = error
                if (System.currentTimeMillis() >= deadline) break
                delay(750)
            }
        }
        throw last
    }

    fun refreshModels() {
        viewModelScope.launch {
            runCatching { loadModelsForRefresh() }
                .onSuccess { list ->
                    val enabled = list.filter { it.enabled }
                    if (enabled.isEmpty()) return@onSuccess
                    models = enabled
                    val current = selectedModelChoice
                    if (current == null) {
                        enabled.firstOrNull()?.let {
                            selectedModelProvider = it.providerID
                            selectedModelId = it.id
                            selectedVariant = null
                        }
                    }
                }
        }
    }

    private suspend fun refreshMessagesSuspend(sid: String) {
        val loaded = api.messages(baseUrl, password, sid)
        if (sid != sessionId) return
        messages = loaded
        saveCache(sid, loaded)
    }

    private suspend fun saveCache(sid: String, list: List<UiMessage>) {
        withContext(Dispatchers.IO) { cache.save(sid, list) }
    }

    private suspend fun refreshPendingSuspend(sid: String) {
        runCatching { api.permissions(baseUrl, password, sid) }
            .onSuccess { permissions = it }
        runCatching { api.forms(baseUrl, password, sid) }
            .onSuccess { forms = it }
    }

    private suspend fun refreshSessionSuspend(sid: String) {
        runCatching { api.session(baseUrl, password, sid) }
            .onSuccess { applySessionInfo(it) }
        runCatching { refreshMessagesSuspend(sid) }
            .onFailure { statusDetail = "История: ${it.message}" }
        refreshPendingSuspend(sid)
        running = runCatching {
            api.activeSessions(baseUrl, password).contains(sid)
        }.getOrDefault(false)
        saveNow()
    }

    private suspend fun loadProjectSuspend() {
        vcsSummary = runCatching { api.vcs(baseUrl, password) }.getOrNull()
        vcsFiles = runCatching { api.vcsStatus(baseUrl, password) }.getOrDefault(emptyList())
        projectPath = "."
        projectFiles = runCatching { api.files(baseUrl, password, ".") }
            .getOrDefault(emptyList())
            .take(200)
        projectLoaded = true
    }

    fun refreshProject() {
        viewModelScope.launch { loadProjectSuspend() }
    }

    fun browseProject(path: String) {
        viewModelScope.launch {
            projectBusy = true
            runCatching { api.files(baseUrl, password, path) }
                .onSuccess {
                    projectPath = path
                    projectFiles = it.take(200)
                }
                .onFailure { statusDetail = "Каталог: ${it.message}" }
            projectBusy = false
        }
    }

    suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { api.readFile(baseUrl, password, path) }
    }

    suspend fun loadDiff(path: String): Result<VcsDiff?> = withContext(Dispatchers.IO) {
        runCatching {
            api.vcsDiff(baseUrl, password).firstOrNull { it.path == path }
        }
    }

    fun refreshSessions() {
        viewModelScope.launch {
            runCatching { api.sessions(baseUrl, password) }
                .onSuccess { sessions = it.filter { session -> !session.archived } }
        }
    }

    fun newSession() {
        if (status != ConnState.CONNECTED) {
            statusDetail = "Сначала подключись к серверу"
            return
        }
        if (busy) return
        busy = true
        viewModelScope.launch {
            runCatching {
                api.createSession(
                    baseUrl,
                    password,
                    newSessionTitle(),
                    selectedAgent,
                    selectedModelRef()
                )
            }.onSuccess { info ->
                applyNewSession(info)
                refreshSessions()
            }.onFailure {
                statusDetail = "Не удалось создать сессию: ${it.message}"
            }
            busy = false
        }
    }

    private fun applyNewSession(info: SessionInfo) {
        cancelShellPolls()
        sessionId = info.id
        sessionTitle = info.title
        sessionDirectory = info.directory
        sessionCost = info.cost
        tokensIn = info.tokensInput
        tokensOut = info.tokensOutput
        sessionOutcome = info.outcome
        messages = emptyList()
        permissions = emptyList()
        forms = emptyList()
        running = false
        statusDetail = ""
        settings.saveSession(info.id, info.title)
        viewModelScope.launch(Dispatchers.IO) { cache.save(info.id, emptyList()) }
        updateSessionInList(info)
        if (info.agent != null) selectedAgent = info.agent
        if (info.modelId != null && info.modelProvider != null) {
            selectedModelId = info.modelId
            selectedModelProvider = info.modelProvider
        }
        saveNow()
    }

    private fun newSessionTitle(): String =
        "Pocket " + SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())

    fun selectSession(id: String) {
        if (id == sessionId) return
        cancelShellPolls()
        sessionId = id
        val known = sessions.firstOrNull { it.id == id }
        sessionTitle = known?.title.orEmpty()
        sessionDirectory = known?.directory.orEmpty()
        sessionCost = known?.cost ?: 0.0
        tokensIn = known?.tokensInput ?: 0L
        tokensOut = known?.tokensOutput ?: 0L
        sessionOutcome = known?.outcome
        messages = emptyList()
        permissions = emptyList()
        forms = emptyList()
        running = false
        settings.saveSession(id, sessionTitle)
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { cache.load(id) }
            if (sessionId == id && messages.isEmpty()) messages = cached
        }
        viewModelScope.launch { refreshSessionSuspend(id) }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        if (status != ConnState.CONNECTED) {
            statusDetail = "Нет соединения с сервером. Проверь адрес и пароль."
            return
        }
        if (busy) return
        input = ""
        settings.draft = ""
        viewModelScope.launch {
            busy = true
            try {
                val sid = ensureSession(text) ?: return@launch
                val localID = "local_${UUID.randomUUID()}"
                messages = messages + UiMessage(
                    id = localID,
                    role = UiRole.USER,
                    blocks = listOf(UiBlock.Text(content = text))
                )
                running = true
                statusDetail = ""
                cacheSoon()
                runCatching { api.prompt(baseUrl, password, sid, text) }
                    .onFailure { error ->
                        messages = messages + UiMessage(
                            id = "error_${UUID.randomUUID()}",
                            role = UiRole.SYSTEM,
                            blocks = listOf(
                                UiBlock.Text(
                                    content = "Не удалось отправить задание: ${error.message}"
                                )
                            )
                        )
                        running = false
                        statusDetail = "Запрос: ${error.message}"
                    }
            } finally {
                busy = false
            }
        }
    }

    private suspend fun ensureSession(firstText: String): String? {
        sessionId?.let { return it }
        val info = runCatching {
            api.createSession(
                baseUrl,
                password,
                firstText.take(60),
                selectedAgent,
                selectedModelRef()
            )
        }.getOrElse {
            statusDetail = "Не удалось создать сессию: ${it.message}"
            return null
        }
        applyNewSession(info)
        return info.id
    }

    fun interrupt() {
        val sid = sessionId ?: return
        viewModelScope.launch {
            runCatching { api.interrupt(baseUrl, password, sid) }
                .onSuccess { statusDetail = "Остановка запрошена" }
                .onFailure { statusDetail = "Стоп: ${it.message}" }
        }
    }

    fun selectModel(choice: ModelChoice) {
        selectedModelProvider = choice.providerID
        selectedModelId = choice.id
        if (choice.variants.isEmpty()) {
            selectedVariant = null
        } else if (selectedVariant !in choice.variants) {
            selectedVariant = null
        }
        saveNow()
        applyModelToSession()
    }

    fun selectVariant(variant: String?) {
        selectedVariant = variant
        saveNow()
        applyModelToSession()
    }

    private fun applyModelToSession() {
        val sid = sessionId ?: return
        val model = selectedModelRef() ?: return
        viewModelScope.launch {
            runCatching { api.setModel(baseUrl, password, sid, model) }
                .onFailure { statusDetail = "Модель: ${it.message}" }
        }
    }

    fun selectAgent(id: String) {
        selectedAgent = id
        saveNow()
        val sid = sessionId ?: return
        viewModelScope.launch {
            runCatching { api.setAgent(baseUrl, password, sid, id) }
                .onFailure { statusDetail = "Агент: ${it.message}" }
        }
    }

    private fun selectedModelRef(): ModelRef? {
        val provider = selectedModelProvider ?: return null
        val id = selectedModelId ?: return null
        val choice = selectedModelChoice
        val variant = selectedVariant?.takeIf { choice?.variants?.contains(it) == true }
        return ModelRef(id = id, providerID = provider, variant = variant)
    }

    fun replyPermission(request: PermissionAsk, decision: String) {
        viewModelScope.launch {
            runCatching {
                api.replyPermission(
                    baseUrl, password, request.sessionID, request.id, decision
                )
            }.onSuccess {
                permissions = permissions.filterNot { it.id == request.id }
            }.onFailure {
                statusDetail = "Разрешение: ${it.message}"
            }
        }
    }

    fun replyForm(form: FormSpec, answer: JSONObject) {
        viewModelScope.launch {
            runCatching {
                api.replyForm(baseUrl, password, form.sessionID, form.id, answer)
            }.onSuccess {
                forms = forms.filterNot { it.id == form.id }
            }.onFailure {
                statusDetail = "Форма: ${it.message}"
            }
        }
    }

    fun cancelForm(form: FormSpec) {
        viewModelScope.launch {
            runCatching {
                api.cancelForm(baseUrl, password, form.sessionID, form.id)
            }.onSuccess {
                forms = forms.filterNot { it.id == form.id }
            }.onFailure {
                statusDetail = "Форма: ${it.message}"
            }
        }
    }

    fun forgetPassword() {
        password = ""
        settings.password = ""
        statusDetail = "Пароль удалён с устройства"
    }

    fun resetAll() {
        connectionJob?.cancel()
        refreshJob?.cancel()
        cacheJob?.cancel()
        settingsJob?.cancel()
        cancelShellPolls()
        settings.clear()
        cache.clearAll()
        baseUrl = "http://127.0.0.1:4096"
        password = ""
        sessionId = null
        sessionTitle = ""
        sessionDirectory = ""
        messages = emptyList()
        sessions = emptyList()
        permissions = emptyList()
        forms = emptyList()
        models = emptyList()
        agents = emptyList()
        selectedModelProvider = null
        selectedModelId = null
        selectedVariant = null
        selectedAgent = null
        running = false
        busy = false
        status = ConnState.IDLE
        statusDetail = ""
        tab = 0
        input = ""
        projectLoaded = false
        projectFiles = emptyList()
        vcsFiles = emptyList()
        vcsSummary = null
        serverInfo = null
        location = null
    }

    private fun queueSave() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            delay(450)
            saveNow()
        }
    }

    private fun saveNow() {
        // Во встроенном режиме URL и пароль выдаются заново при каждом
        // запуске сервера — хранить их бессмысленно и вредно.
        if (settings.mode == ServerMode.EXTERNAL) {
            settings.baseUrl = baseUrl
            settings.password = password
        }
        settings.tab = tab
        settings.saveSession(sessionId, sessionTitle)
        settings.draft = input
        settings.agent = selectedAgent
        settings.modelProvider = selectedModelProvider
        settings.modelId = selectedModelId
        settings.modelVariant = selectedVariant
    }

    private fun cacheSoon() {
        val sid = sessionId ?: return
        val snapshot = messages
        cacheJob?.cancel()
        cacheJob = viewModelScope.launch {
            delay(700)
            withContext(Dispatchers.IO) { cache.save(sid, snapshot) }
        }
    }

    private fun cacheNow() {
        val sid = sessionId ?: return
        val snapshot = messages
        viewModelScope.launch(Dispatchers.IO) { cache.save(sid, snapshot) }
    }

    private fun applySessionInfo(info: SessionInfo) {
        sessionTitle = info.title
        sessionDirectory = info.directory
        sessionCost = info.cost
        tokensIn = info.tokensInput
        tokensOut = info.tokensOutput
        sessionOutcome = info.outcome
        info.agent?.takeIf { it.isNotBlank() }?.let { selectedAgent = it }
        if (info.modelId != null && info.modelProvider != null) {
            selectedModelId = info.modelId
            selectedModelProvider = info.modelProvider
            val choice = models.firstOrNull {
                it.id == info.modelId && it.providerID == info.modelProvider
            }
            if (choice != null && selectedVariant !in choice.variants) {
                selectedVariant = null
            }
        }
        updateSessionInList(info)
        if (sessionId == info.id) settings.saveSession(info.id, info.title)
    }

    private fun updateSessionInList(info: SessionInfo) {
        val index = sessions.indexOfFirst { it.id == info.id }
        sessions = if (index >= 0) {
            sessions.toMutableList().also { it[index] = info }
        } else {
            listOf(info) + sessions
        }
    }

    private fun handleEvent(event: JSONObject) {
        val type = event.optString("type")
        val data = event.optJSONObject("data") ?: JSONObject()

        if (type == "server.connected") {
            refreshEverything()
            return
        }
        if (type == "session.created") {
            refreshSessions()
            return
        }
        if (type == "permission.asked" || type == "permission.replied") {
            refreshPendingIfCurrent(data)
            return
        }
        if (type == "form.created" || type == "form.replied" || type == "form.cancelled") {
            refreshPendingIfCurrent(data)
            return
        }
        if (type == "shell.created") {
            handleShellCreated(data)
            return
        }
        if (type == "shell.exited") {
            handleShellExited(data)
            return
        }
        if (type == "provider.updated" || type == "model.updated" ||
            type == "credential.created" || type == "credential.deleted"
        ) {
            refreshModels()
            return
        }

        val eventSession = data.optText("sessionID")
        if (eventSession != null && eventSession != sessionId) {
            if (type == "session.usage.updated") {
                updateUsageForSession(eventSession, data)
            }
            return
        }

        when (type) {
            "session.execution.started" -> {
                sessionId?.let { activeSessions = activeSessions + it }
                running = true
                statusDetail = ""
            }

            "session.execution.succeeded",
            "session.execution.interrupted" -> {
                sessionId?.let { activeSessions = activeSessions - it }
                finishExecution()
            }

            "session.execution.failed" -> {
                sessionId?.let { activeSessions = activeSessions - it }
                running = false
                statusDetail = ""
                finishExecution()
            }

            "session.step.started" -> {
                val aid = data.optString("assistantMessageID")
                if (aid.isNotBlank()) {
                    ensureAssistant(aid)
                    val agent = data.optText("agent")
                    val model = data.optJSONObject("model")?.optText("id")
                    if (agent != null || model != null) {
                        updateMessage(aid) {
                            it.copy(agent = agent ?: it.agent, model = model ?: it.model)
                        }
                    }
                }
            }

            "session.text.started" -> applyTextEvent(data, "text", started = true)
            "session.text.delta" -> applyTextEvent(data, "text")
            "session.text.ended" -> applyTextEvent(data, "text", ended = true)
            "session.reasoning.started" -> applyTextEvent(data, "reasoning", started = true)
            "session.reasoning.delta" -> applyTextEvent(data, "reasoning")
            "session.reasoning.ended" -> applyTextEvent(data, "reasoning", ended = true)

            "session.tool.input.started" -> handleToolInputStarted(data)
            "session.tool.input.delta" -> handleToolInputDelta(data)
            "session.tool.input.ended" -> handleToolInputEnded(data)
            "session.tool.called" -> handleToolCalled(data)
            "session.tool.progress" -> handleToolProgress(data)
            "session.tool.success" -> handleToolResult(data, success = true)
            "session.tool.failed" -> handleToolResult(data, success = false)

            "session.usage.updated" -> {
                sessionCost = data.optDouble("cost", sessionCost)
                val tokens = data.optJSONObject("tokens")
                if (tokens != null) {
                    tokensIn = tokens.optLong("input", tokensIn)
                    tokensOut = tokens.optLong("output", tokensOut)
                }
                sessionId?.let { updateUsageForSession(it, data) }
            }
        }
    }

    private fun refreshPendingIfCurrent(data: JSONObject) {
        val sid = data.optText("sessionID")
        if (sid == null || sid == sessionId) {
            val current = sessionId ?: return
            viewModelScope.launch { refreshPendingSuspend(current) }
        }
    }

    private fun updateUsageForSession(sid: String, data: JSONObject) {
        val index = sessions.indexOfFirst { it.id == sid }
        if (index < 0) return
        val old = sessions[index]
        val tokens = data.optJSONObject("tokens")
        val updated = old.copy(
            cost = data.optDouble("cost", old.cost),
            tokensInput = tokens?.optLong("input", old.tokensInput) ?: old.tokensInput,
            tokensOutput = tokens?.optLong("output", old.tokensOutput) ?: old.tokensOutput
        )
        sessions = sessions.toMutableList().also { it[index] = updated }
    }

    private fun finishExecution() {
        running = false
        cacheNow()
        viewModelScope.launch {
            delay(700)
            if (running) return@launch
            val sid = sessionId ?: return@launch
            runCatching { refreshMessagesSuspend(sid) }
            runCatching { api.session(baseUrl, password, sid) }
                .onSuccess { applySessionInfo(it) }
            runCatching { api.sessions(baseUrl, password) }
                .onSuccess { sessions = it.filter { session -> !session.archived } }
            if (sessionOutcome == "failed" && messages.none { it.error?.isNotBlank() == true }) {
                statusDetail = "Выполнение завершилось с ошибкой"
            }
            if (sessionOutcome == "interrupted" && messages.none { it.error?.isNotBlank() == true }) {
                statusDetail = "Выполнение прервано"
            }
            cacheNow()
        }
    }

    private fun ensureAssistant(id: String) {
        if (messages.any { it.id == id }) return
        messages = messages + UiMessage(
            id = id,
            role = UiRole.ASSISTANT,
            agent = selectedAgent,
            model = selectedModelId
        )
    }

    private fun updateMessage(id: String, transform: (UiMessage) -> UiMessage) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) return
        val copy = messages.toMutableList()
        copy[index] = transform(copy[index])
        messages = copy
    }

    private fun applyTextEvent(
        data: JSONObject,
        kind: String,
        started: Boolean = false,
        ended: Boolean = false
    ) {
        val aid = data.optString("assistantMessageID")
        if (aid.isBlank()) return
        val ordinal = data.optInt("ordinal", 0)
        val delta = when {
            ended -> data.optString("text")
            started -> ""
            else -> data.optString("delta")
        }
        ensureAssistant(aid)
        updateMessage(aid) { message ->
            val blocks = message.blocks.toMutableList()
            val index = blocks.indexOfFirst { block ->
                when (kind) {
                    "text" -> block is UiBlock.Text && block.ordinal == ordinal
                    else -> block is UiBlock.Reasoning && block.ordinal == ordinal
                }
            }
            if (index >= 0) {
                when (val block = blocks[index]) {
                    is UiBlock.Text -> blocks[index] = block.copy(
                        content = if (ended) delta else block.content + delta
                    )

                    is UiBlock.Reasoning -> blocks[index] = block.copy(
                        content = if (ended) delta else block.content + delta,
                        streaming = !ended
                    )

                    else -> Unit
                }
            } else {
                blocks += if (kind == "text") {
                    UiBlock.Text(ordinal, delta)
                } else {
                    UiBlock.Reasoning(ordinal, delta, !ended)
                }
            }
            message.copy(blocks = blocks)
        }
        cacheSoon()
    }

    private fun handleToolInputStarted(data: JSONObject) {
        val aid = data.optString("assistantMessageID")
        val callId = data.optString("id")
        val name = data.optString("name").ifBlank { "tool" }
        if (aid.isBlank() || callId.isBlank()) return
        toolNames[callId] = name
        ensureAssistant(aid)
        updateTool(aid, callId) {
            it.copy(name = name, status = "streaming")
        }
        cacheSoon()
    }

    private fun handleToolInputDelta(data: JSONObject) {
        val aid = data.optString("assistantMessageID")
        val callId = data.optString("id")
        if (aid.isBlank() || callId.isBlank()) return
        ensureAssistant(aid)
        val delta = data.optString("delta")
        updateTool(aid, callId) {
            it.copy(
                name = toolNames[callId] ?: it.name,
                input = it.input + delta,
                status = "streaming"
            )
        }
        cacheSoon()
    }

    private fun handleToolInputEnded(data: JSONObject) {
        val aid = data.optString("assistantMessageID")
        val callId = data.optString("id")
        if (aid.isBlank() || callId.isBlank()) return
        ensureAssistant(aid)
        updateTool(aid, callId) {
            it.copy(input = data.optString("text"), status = "streaming")
        }
        cacheSoon()
    }

    private fun handleToolCalled(data: JSONObject) {
        val aid = data.optString("assistantMessageID")
        val callId = data.optString("id")
        if (aid.isBlank() || callId.isBlank()) return
        ensureAssistant(aid)
        updateTool(aid, callId) {
            it.copy(
                name = toolNames[callId] ?: it.name,
                input = jsonText(data.opt("input")),
                status = "running"
            )
        }
        cacheSoon()
    }

    private fun handleToolProgress(data: JSONObject) {
        val aid = data.optString("assistantMessageID")
        val callId = data.optString("id")
        val shellID = data.optJSONObject("metadata")?.optText("shellID")
        if (shellID != null) {
            pendingShells.remove(shellID)?.cancel()
            messages = messages.filterNot { it.id == "shell_$shellID" }
            shellMeta[shellID] = ShellMeta("", sessionId.orEmpty())
            if (aid.isNotBlank() && callId.isNotBlank()) {
                updateTool(aid, callId) { it.copy(shellId = shellID, status = "running") }
                startShellPolling(shellID)
            }
        }
    }

    private fun handleToolResult(data: JSONObject, success: Boolean) {
        val aid = data.optString("assistantMessageID")
        val callId = data.optString("id")
        if (aid.isBlank() || callId.isBlank()) return
        val metadata = data.optJSONObject("metadata")
        val output = parseToolContent(data.optJSONArray("content"))
        val error = if (success) null else {
            data.optJSONObject("error")?.optString("message")
                ?: data.optString("message").takeIf { it.isNotBlank() }
                ?: "Инструмент завершился с ошибкой"
        }
        updateTool(aid, callId) {
            it.copy(
                name = toolNames[callId] ?: it.name,
                status = if (success) "completed" else "error",
                output = output.ifBlank { it.output },
                metadata = metadata?.toString(2) ?: it.metadata,
                exit = if (metadata?.has("exit") == true) metadata.optInt("exit") else it.exit,
                error = error
            )
        }
        cacheSoon()
    }

    private fun updateTool(
        assistantID: String,
        callID: String,
        transform: (UiBlock.Tool) -> UiBlock.Tool
    ) {
        updateMessage(assistantID) { message ->
            val blocks = message.blocks.toMutableList()
            val index = blocks.indexOfFirst {
                it is UiBlock.Tool && it.callId == callID
            }
            if (index >= 0) {
                blocks[index] = transform(blocks[index] as UiBlock.Tool)
            } else {
                blocks += transform(
                    UiBlock.Tool(callID, toolNames[callID] ?: "tool")
                )
            }
            message.copy(blocks = blocks)
        }
    }

    private fun handleShellCreated(data: JSONObject) {
        val info = data.optJSONObject("info") ?: return
        val shellID = info.optString("id")
        if (shellID.isBlank()) return
        val command = info.optString("command")
        val sid = info.optJSONObject("metadata")?.optString("sessionID").orEmpty()
        if (sid.isNotBlank() && sid != sessionId) return
        shellMeta[shellID] = ShellMeta(command, sid)
        shellStatus[shellID] = "running" to null

        var attached = false
        messages = messages.map { message ->
            if (message.blocks.any { it is UiBlock.Tool && it.shellId == shellID }) {
                attached = true
                message.copy(
                    blocks = message.blocks.map { block ->
                        if (block is UiBlock.Tool && block.shellId == shellID) {
                            block.copy(
                                status = "running",
                                input = if (block.input.isBlank()) {
                                    JSONObject().put("command", command).toString(2)
                                } else {
                                    block.input
                                }
                            )
                        } else {
                            block
                        }
                    }
                )
            } else {
                message
            }
        }

        if (!attached) {
            pendingShells[shellID]?.cancel()
            pendingShells[shellID] = viewModelScope.launch {
                delay(1500)
                val claimed = messages.any { message ->
                    message.blocks.any { it is UiBlock.Tool && it.shellId == shellID }
                }
                if (!claimed && messages.none { it.id == "shell_$shellID" }) {
                    val (state, exit) = shellStatus[shellID] ?: ("running" to null)
                    messages = messages + UiMessage(
                        id = "shell_$shellID",
                        role = UiRole.SHELL,
                        blocks = listOf(UiBlock.Shell(shellID, command, state, exit = exit))
                    )
                    cacheSoon()
                }
            }
        }
        startShellPolling(shellID)
        cacheSoon()
    }

    private fun handleShellExited(data: JSONObject) {
        val shellID = data.optString("id")
        if (shellID.isBlank()) return
        val exit = if (data.has("exit") && !data.isNull("exit")) data.optInt("exit") else null
        val status = data.optString("status", "exited")
        shellStatus[shellID] = status to exit
        updateShellState(shellID, status, exit)
        shellJobs.remove(shellID)?.cancel()
        viewModelScope.launch {
            runCatching { api.shellOutput(baseUrl, password, shellID) }
                .onSuccess { updateShellOutput(shellID, it) }
            cacheSoon()
        }
    }

    private fun updateShellState(shellID: String, status: String, exit: Int?) {
        messages = messages.map { message ->
            if (message.blocks.none {
                    (it is UiBlock.Tool && it.shellId == shellID) ||
                        (it is UiBlock.Shell && it.shellId == shellID)
                }
            ) {
                message
            } else {
                message.copy(
                    blocks = message.blocks.map { block ->
                        when {
                            block is UiBlock.Tool && block.shellId == shellID ->
                                block.copy(exit = exit ?: block.exit)

                            block is UiBlock.Shell && block.shellId == shellID ->
                                block.copy(status = status, exit = exit ?: block.exit)

                            else -> block
                        }
                    }
                )
            }
        }
        cacheSoon()
    }

    private fun updateShellOutput(shellID: String, output: String) {
        if (output.isEmpty()) return
        messages = messages.map { message ->
            if (message.blocks.none {
                    (it is UiBlock.Tool && it.shellId == shellID) ||
                        (it is UiBlock.Shell && it.shellId == shellID)
                }
            ) {
                message
            } else {
                message.copy(
                    blocks = message.blocks.map { block ->
                        when {
                            block is UiBlock.Tool && block.shellId == shellID ->
                                block.copy(output = output)

                            block is UiBlock.Shell && block.shellId == shellID ->
                                block.copy(output = output)

                            else -> block
                        }
                    }
                )
            }
        }
        cacheSoon()
    }

    private fun startShellPolling(shellID: String) {
        if (shellJobs[shellID]?.isActive == true) return
        shellJobs[shellID] = viewModelScope.launch {
            repeat(1800) {
                try {
                    val output = api.shellOutput(baseUrl, password, shellID)
                    updateShellOutput(shellID, output)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: ApiException) {
                    if (error.code == 404) return@launch
                } catch (_: Exception) {
                    // Временная ошибка — продолжаем опрос.
                }
                delay(650)
            }
        }
    }

    private fun cancelShellPolls() {
        shellJobs.values.forEach { it.cancel() }
        shellJobs.clear()
        pendingShells.values.forEach { it.cancel() }
        pendingShells.clear()
        shellStatus.clear()
    }

    override fun onCleared() {
        saveNow()
        sessionId?.let { sid -> runCatching { cache.save(sid, messages) } }
        connectionJob?.cancel()
        refreshJob?.cancel()
        cacheJob?.cancel()
        settingsJob?.cancel()
        cancelShellPolls()
        super.onCleared()
    }
}
