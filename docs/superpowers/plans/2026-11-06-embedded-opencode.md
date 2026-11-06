# Встроенный OpenCode в OpenCode Pocket — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Приложение само запускает настоящий OpenCode v2 внутри APK, управляет сессиями, провайдерами, скилами, настройками и файлами телефона без Termux.

**Architecture:** Бинарник OpenCode лежит в `jniLibs/arm64-v8a/libocserver.so` и запускается из `nativeLibraryDir` как дочерний процесс приложения с приватным `HOME`/XDG и рабочим каталогом `/sdcard/OpenCode`. Foreground-сервис держит процесс и уведомление, `EmbeddedServer` отдаёт состояние/порт/пароль/лог через `StateFlow`, `PocketViewModel` автоматически подключает существующий Compose UI. Внешний сервер остаётся режимом отладки.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Jetpack Compose (BOM 2024.12.01), Android SDK 35, OkHttp 4.12, org.json, JUnit 4, OpenCode v2.1.4-beta.4b83402a (arm64 Android ELF).

**Spec:** `docs/superpowers/specs/2026-11-06-embedded-opencode-design.md`

## Global Constraints

- `minSdk 26`, `targetSdk 35`, `compileSdk 35`, JDK 17. Only `arm64-v8a`.
- Package `com.pocket.opencode`. Binary is `libocserver.so`, 170 MB, version `2.1.4-beta.4b83402a`; never commit it.
- Private home: `files/opencode/home` (`data`, `config`, `cache` inside). Logs: `files/opencode/logs/server.log`.
- Workspace: `/sdcard/OpenCode`; fallback `files/opencode/workspace` until all-files access is granted.
- Server binds `127.0.0.1`; preferred port `4096`; Basic auth `opencode:<password>`; password is parsed from stdout line `server password <value>`.
- Daily use must not require Termux, ADB or root. MCP is explicitly out of scope for this iteration.
- Keep the dark violet UI. Do not delete user data during migration; back up before replacing.
- `versionName = "0.2.0"`, `versionCode = 2` at the final task.

## Review Focus

- Server process exits before printing a password: UI must show a real error and the log, not spin on «Подключение».
- Port `4096` is occupied by another process: app must pick a free port and connect to that exact port.
- Server stdout is chunked/reordered: password and listening lines must still be parsed without hanging.
- All-files access is missing or revoked: workspace falls back to app storage, and the user sees how to grant it.
- Migration archive has Termux paths, missing DB, or a live WAL: import must preview problems and never destroy the existing home.
- Malformed or duplicate message IDs from the server: transcript must render without a Compose crash.

---

### Task 1: Test scaffolding and pure server helpers

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/test/java/com/pocket/opencode/ServerPathsTest.kt`
- Create: `app/src/test/java/com/pocket/opencode/ServerOutputTest.kt`
- Create: `app/src/test/java/com/pocket/opencode/LogRingTest.kt`
- Create: `app/src/test/java/com/pocket/opencode/PortSelectionTest.kt`
- Create: `app/src/main/java/com/pocket/opencode/ServerPaths.kt`
- Create: `app/src/main/java/com/pocket/opencode/ServerOutput.kt`
- Create: `app/src/main/java/com/pocket/opencode/LogRing.kt`
- Create: `scripts/remote.sh`

**Interfaces:**
- Produces:
  - `enum class ServerMode { EMBEDDED, EXTERNAL }`
  - `enum class EmbeddedState { STOPPED, STARTING, RUNNING, ERROR }`
  - `data class ServerPaths(root: File, home: File, data: File, config: File, cache: File, logs: File, tmp: File, workspace: File)` with `database: File` and `companion object { fun under(root: File, workspace: File): ServerPaths; fun forContext(context: Context, workspace: File): ServerPaths }`
  - `object ServerOutput { fun password(line: String): String?; fun listeningUrl(line: String): String? }`
  - `class LogRing(capacity: Int = 400) { fun append(line: String); fun snapshot(): List<String>; fun clear() }`
  - `object PortSelection { fun choose(preferred: Int = 4096, span: Int = 32): Int }`
  - `scripts/remote.sh sync|test <class>|build`

- [ ] **Step 1: Add test dependencies**

In `app/build.gradle.kts` add to `dependencies`:

```kotlin
testImplementation("junit:junit:4.13.2")
testImplementation("org.json:json:20240303")
```

and inside `android { ... }`:

```kotlin
testOptions { unitTests.isReturnDefaultValues = true }
```

- [ ] **Step 2: Write failing tests**

`ServerPathsTest.kt`:

```kotlin
class ServerPathsTest {
    @Test fun `builds private xdg layout`() {
        val p = ServerPaths.under(File("/data/app/files/opencode"), File("/sdcard/OpenCode"))
        assertEquals(File("/data/app/files/opencode/home"), p.home)
        assertEquals(File("/data/app/files/opencode/home/data"), p.data)
        assertEquals(File("/data/app/files/opencode/home/config"), p.config)
        assertEquals(File("/data/app/files/opencode/home/cache"), p.cache)
        assertEquals(File("/data/app/files/opencode/logs"), p.logs)
        assertEquals(File("/data/app/files/opencode/home/cache/tmp"), p.tmp)
        assertEquals(File("/sdcard/OpenCode"), p.workspace)
        assertEquals(File("/data/app/files/opencode/home/data/opencode/opencode.db"), p.database)
    }
}
```

`ServerOutputTest.kt`:

```kotlin
class ServerOutputTest {
    @Test fun `parses plain password line`() {
        assertEquals("abc123", ServerOutput.password("server password abc123"))
    }
    @Test fun `parses password with surrounding output`() {
        assertEquals("abc123", ServerOutput.password("OUT server password abc123"))
    }
    @Test fun `ignores non password lines`() {
        assertNull(ServerOutput.password("server listening on http://127.0.0.1:4096"))
    }
    @Test fun `parses listening url`() {
        assertEquals("http://127.0.0.1:4096", ServerOutput.listeningUrl("server listening on http://127.0.0.1:4096"))
    }
}
```

`LogRingTest.kt`:

```kotlin
class LogRingTest {
    @Test fun `keeps insertion order`() {
        val ring = LogRing(3); ring.append("a"); ring.append("b"); ring.append("c")
        assertEquals(listOf("a", "b", "c"), ring.snapshot())
    }
    @Test fun `drops oldest lines past capacity`() {
        val ring = LogRing(3); listOf("a", "b", "c", "d").forEach(ring::append)
        assertEquals(listOf("b", "c", "d"), ring.snapshot())
    }
    @Test fun `clear empties the ring`() {
        val ring = LogRing(2); ring.append("a"); ring.clear()
        assertTrue(ring.snapshot().isEmpty())
    }
}
```

`PortSelectionTest.kt`:

```kotlin
class PortSelectionTest {
    @Test fun `returns preferred port when free`() {
        val free = ServerSocket(0).use { it.localPort }
        assertEquals(free, PortSelection.choose(free))
    }
    @Test fun `skips a busy port`() {
        ServerSocket(0).use { busy ->
            val picked = PortSelection.choose(busy.localPort, span = 8)
            assertNotEquals(busy.localPort, picked)
            assertTrue(picked > 0)
        }
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run remote unit tests for the four classes; expected failure is unresolved references for `ServerPaths`, `ServerOutput`, `LogRing`, `PortSelection`.

- [ ] **Step 4: Implement the helpers**

`ServerPaths.kt`:

```kotlin
package com.pocket.opencode

import android.content.Context
import java.io.File

enum class ServerMode { EMBEDDED, EXTERNAL }
enum class EmbeddedState { STOPPED, STARTING, RUNNING, ERROR }

data class ServerPaths(
    val root: File,
    val home: File,
    val data: File,
    val config: File,
    val cache: File,
    val logs: File,
    val tmp: File,
    val workspace: File,
) {
    val database: File get() = File(data, "opencode/opencode.db")

    companion object {
        fun under(root: File, workspace: File): ServerPaths {
            val home = File(root, "home")
            val cache = File(home, "cache")
            return ServerPaths(
                root = root,
                home = home,
                data = File(home, "data"),
                config = File(home, "config"),
                cache = cache,
                logs = File(root, "logs"),
                tmp = File(cache, "tmp"),
                workspace = workspace,
            )
        }
        fun forContext(context: Context, workspace: File): ServerPaths =
            under(File(context.filesDir, "opencode"), workspace)
    }
}
```

`ServerOutput.kt` uses `Regex("""server password\s+(\S+)""")` and `Regex("""server listening on (http://\S+)""")`. `LogRing.kt` uses an `ArrayDeque<String>` under `synchronized`. `PortSelection.kt` tries `ServerSocket()` on `127.0.0.1` for `preferred until preferred + span`, returning the first successful port, then `0` to let the OS choose.

- [ ] **Step 5: Add remote build helpers**

`scripts/remote.sh` must read `POCKET_HOST` and `POCKET_PASS`, use `sshpass`, and expose:
- `sync` — tar the repo excluding `app/src/main/jniLibs`, copy to `/root/pocket-src.tar.gz`, run `/root/build_pocket.sh` on the host (the script copies `/root/ocserver/libocserver.so` into jniLibs).
- `test <Class>` — ssh `cd /root/pocket && ./gradlew testDebugUnitTest --tests "com.pocket.opencode.<Class>" --no-daemon --console=plain`.
- `build` — ssh `bash /root/build_pocket.sh`, then scp `app/build/outputs/apk/debug/app-debug.apk` to `~/tmp/pocket.apk`.

- [ ] **Step 6: Run tests to verify they pass**

`bash scripts/remote.sh sync && bash scripts/remote.sh test ServerPathsTest` and the same for the other three classes. Expected: `BUILD SUCCESSFUL`, zero failures.

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/pocket/opencode/ServerPaths.kt app/src/main/java/com/pocket/opencode/ServerOutput.kt app/src/main/java/com/pocket/opencode/LogRing.kt app/src/main/java/com/pocket/opencode/PortSelection.kt app/src/test scripts/remote.sh
git commit -m "feat: add embedded server paths, output parsing and log ring"
```

---

### Task 2: EmbeddedServer process manager

**Files:**
- Create: `app/src/main/java/com/pocket/opencode/EmbeddedServer.kt`
- Create: `app/src/test/java/com/pocket/opencode/ServerEnvTest.kt`
- Modify: `app/src/main/java/com/pocket/opencode/ServerPaths.kt` (add env builder if not separate)

**Interfaces:**
- Consumes: `ServerPaths`, `ServerOutput`, `LogRing`, `PortSelection`.
- Produces:
  - `data class EmbeddedStatus(val state: EmbeddedState = STOPPED, val port: Int = 0, val password: String = "", val url: String = "", val error: String = "", val version: String = "")`
  - `object EmbeddedServer { val status: StateFlow<EmbeddedStatus>; val log: StateFlow<List<String>>; fun start(context: Context, workspace: File); fun stop(); fun restart(context: Context, workspace: File) }`
  - `object ServerEnv { fun build(paths: ServerPaths): Map<String, String> }`

- [ ] **Step 1: Write the failing env test**

```kotlin
class ServerEnvTest {
    @Test fun `builds isolated xdg environment`() {
        val p = ServerPaths.under(File("/app/files/opencode"), File("/sdcard/OpenCode"))
        val env = ServerEnv.build(p)
        assertEquals(p.home.absolutePath, env["HOME"])
        assertEquals(p.data.absolutePath, env["XDG_DATA_HOME"])
        assertEquals(p.config.absolutePath, env["XDG_CONFIG_HOME"])
        assertEquals(p.cache.absolutePath, env["XDG_CACHE_HOME"])
        assertEquals(p.tmp.absolutePath, env["TMPDIR"])
        assertEquals("/system/bin:/system/xbin", env["PATH"])
        assertEquals("C.UTF-8", env["LANG"])
    }
}
```

- [ ] **Step 2: Run it and verify it fails**

Expected: unresolved `ServerEnv`.

- [ ] **Step 3: Implement `ServerEnv` and `EmbeddedServer`**

`ServerEnv.build` returns exactly the keys asserted above. `EmbeddedServer.start`:

1. If state is `STARTING`/`RUNNING`, return.
2. Set `STARTING`, clear log.
3. Create directories from `ServerPaths` and `workspace.mkdirs()`.
4. Resolve `File(context.applicationInfo.nativeLibraryDir, "libocserver.so")`; if missing, set `ERROR` with the path.
5. Pick port with `PortSelection.choose()`.
6. `ProcessBuilder(bin.absolutePath, "serve", "--hostname", "127.0.0.1", "--port", port.toString())`, directory = workspace, redirectErrorStream = true, env = `ServerEnv.build(paths)`.
7. Reader thread appends every line to `LogRing`, parses password with `ServerOutput.password`, and persists to `files/opencode/logs/server.log` (append; rotate at 2 MB by renaming to `server.log.1`).
8. Poll `GET /api/info` with Basic auth every 500 ms for 30 s; on `200`, read `version` from JSON, set `RUNNING`, url `http://127.0.0.1:<port>`.
9. If the process exits or timeout, set `ERROR` with exit code and last 20 log lines.
10. `stop()` destroys the process, waits 3 s, then `destroyForcibly()`, sets `STOPPED`.

`status` and `log` are `MutableStateFlow` exposed as `StateFlow`. All state mutation happens on a single-thread executor to avoid races.

- [ ] **Step 4: Run env test**

Expected: PASS.

- [ ] **Step 5: Device smoke test**

Build and install. Add a temporary button in Settings that calls `EmbeddedServer.start(...)` and prints `status`/`log`; expected log contains `server listening on` and `server password`, state becomes `RUNNING`, and `GET /api/info` returns 200 via `adb`/`su curl`. Remove the temporary button after the check.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pocket/opencode/EmbeddedServer.kt app/src/test/java/com/pocket/opencode/ServerEnvTest.kt
git commit -m "feat: run bundled OpenCode server as a child process"
```

---

### Task 3: Foreground service and manifest

**Files:**
- Create: `app/src/main/java/com/pocket/opencode/ServerService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/pocket/opencode/SettingsStore.kt` (workspace/mode fields used by the service)
- Modify: `app/src/main/java/com/pocket/opencode/MainActivity.kt` (start service and request notification permission)

**Interfaces:**
- Consumes: `EmbeddedServer`, `SettingsStore`, `ServerPaths`.
- Produces:
  - `class ServerService : Service()` with `companion object { fun start(context: Context); fun stop(context: Context); fun restart(context: Context) }`
  - Notification channel `opencode_server`, id `4096`, action `ServerService.ACTION_STOP`.

- [ ] **Step 1: Add manifest declarations**

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>

<service
    android:name=".ServerService"
    android:exported="false"
    android:stopWithTask="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="embedded OpenCode agent server"/>
</service>
```

- [ ] **Step 2: Implement the service**

`onStartCommand`:
- `ACTION_START`/`ACTION_RESTART`: `startForeground(4096, buildNotification("Запускаю OpenCode…"))`, collect `EmbeddedServer.status`, update notification to `OpenCode работает · :<port>` or `Ошибка: <error>`, call `EmbeddedServer.start/restart` with `SettingsStore.workspacePath`.
- `ACTION_STOP`: `EmbeddedServer.stop()`, `stopForeground(STOP_FOREGROUND_REMOVE)`, `stopSelf()`.
- Return `START_STICKY`.
- `onDestroy`: stop the server only when the service was explicitly stopped; if killed by the OS, let `START_STICKY` restart it.

Notification: channel importance `LOW`, small icon `R.drawable.ic_launcher_foreground` or existing launcher, content intent opens `MainActivity`, action «Стоп» sends `ACTION_STOP` via `PendingIntent.getService`.

- [ ] **Step 3: Wire startup**

In `MainActivity.onCreate` after `enableEdgeToEdge`:
- On API 33+, `requestPermissions(arrayOf(POST_NOTIFICATIONS), 1)`.
- If `SettingsStore(this).mode == EMBEDDED`, call `ServerService.start(this)`.
- Keep `setContent { PocketApp(vm) }`.

- [ ] **Step 4: Verify on device**

Manual checklist:
1. Launch app → notification «OpenCode работает».
2. Swipe app from recents → notification stays, `curl` to the port still returns 200.
3. Tap «Стоп» → notification disappears, port closed, app status becomes «Не подключено».
4. Relaunch → service starts again, port may change.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/pocket/opencode/ServerService.kt app/src/main/java/com/pocket/opencode/MainActivity.kt
git commit -m "feat: keep embedded server alive with a foreground service"
```

---

### Task 4: Mode resolution and automatic connection

**Files:**
- Modify: `app/src/main/java/com/pocket/opencode/SettingsStore.kt`
- Modify: `app/src/main/java/com/pocket/opencode/PocketViewModel.kt`
- Create: `app/src/main/java/com/pocket/opencode/ConnectionMode.kt`
- Create: `app/src/test/java/com/pocket/opencode/ConnectionModeTest.kt`
- Create: `app/src/test/java/com/pocket/opencode/TabMigrationTest.kt`

**Interfaces:**
- Consumes: `EmbeddedServer.status`, `ServerService`.
- Produces:
  - `SettingsStore.mode: ServerMode` (persist `"embedded"` / `"external"`), `modeWasSet: Boolean`, `workspacePath: String`, `serverPort: Int`
  - `object ConnectionModeResolver { fun resolve(hasSavedMode: Boolean, savedMode: String?, hasExternalUrl: Boolean): ServerMode }`
  - `object TabMigration { fun map(oldTab: Int, schema: Int): Int }` (old schema `< 2` maps `2 → 4`)
  - `PocketViewModel`: `serverStatus: EmbeddedStatus`, `serverLog: List<String>`, `fun setMode(mode)`, `fun startEmbedded()`, `fun stopEmbedded()`, `fun restartEmbedded()`

- [ ] **Step 1: Write failing tests**

```kotlin
class ConnectionModeTest {
    @Test fun `fresh install defaults to embedded`() =
        assertEquals(ServerMode.EMBEDDED, ConnectionModeResolver.resolve(false, null, false))
    @Test fun `existing external server survives upgrade`() =
        assertEquals(ServerMode.EXTERNAL, ConnectionModeResolver.resolve(false, null, true))
    @Test fun `saved mode wins`() =
        assertEquals(ServerMode.EMBEDDED, ConnectionModeResolver.resolve(true, "embedded", true))
}
class TabMigrationTest {
    @Test fun `old settings tab becomes new settings tab`() = assertEquals(4, TabMigration.map(2, 1))
    @Test fun `current schema is untouched`() = assertEquals(2, TabMigration.map(2, 2))
}
```

- [ ] **Step 2: Run tests and verify they fail**

- [ ] **Step 3: Implement settings and resolver**

`SettingsStore`:
- `mode` getter returns `ConnectionModeResolver.resolve(prefs.contains("mode"), prefs.getString("mode", null), prefs.contains("base_url"))`; setter writes `mode`.
- `workspacePath` default `/sdcard/OpenCode`.
- `serverPort` default `4096`.
- `tab` getter applies `TabMigration.map(raw, prefs.getInt("tab_schema", 1))` and writes `tab_schema=2` on first read.

- [ ] **Step 4: Integrate with ViewModel**

In `init`:
- If `settings.mode == EMBEDDED`, collect `EmbeddedServer.status`:
  - `RUNNING` → if `baseUrl != status.url` set `baseUrl = status.url`, `password = status.password`, call `connect()`.
  - `ERROR` → `status = ConnState.ERROR`, `statusDetail = status.error`.
  - `STARTING` → `status = ConnState.CONNECTING`, `statusDetail = "Запускаю встроенный OpenCode…"`.
- If `EXTERNAL` and `settings.baseUrl.isNotBlank()`, keep current `connect()`.
- Collect `EmbeddedServer.log` into `serverLog`.

Add SSE handling for `provider.updated`, `model.updated`, `credential.created`, `credential.deleted`, `skill.created`, `skill.updated`, `skill.deleted`, `server.connected` → refresh models/skills/providers as appropriate.

- [ ] **Step 5: Make connection robust when models are not ready**

In the embedded path, after `/api/info` succeeds, retry `/api/model` up to 30 s (the real server returns 503 `Location plugins did not finish activating` for a few seconds). Only after that call `refreshEverything()`.

- [ ] **Step 6: Run tests**

`ConnectionModeTest` and `TabMigrationTest` pass.

- [ ] **Step 7: Device verification**

Fresh install with cleared data → app starts embedded server, status becomes «Подключено», chat loads models without manual URL/password. Upgrade case: install over the current external setup → mode stays external until changed.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/pocket/opencode/SettingsStore.kt app/src/main/java/com/pocket/opencode/PocketViewModel.kt app/src/main/java/com/pocket/opencode/ConnectionMode.kt app/src/test/java/com/pocket/opencode/ConnectionModeTest.kt app/src/test/java/com/pocket/opencode/TabMigrationTest.kt
git commit -m "feat: resolve connection mode and auto-connect to embedded server"
```

---

### Task 5: Server, workspace and onboarding UI

**Files:**
- Create: `app/src/main/java/com/pocket/opencode/Workspace.kt`
- Create: `app/src/main/java/com/pocket/opencode/ServerScreen.kt`
- Modify: `app/src/main/java/com/pocket/opencode/SettingsScreen.kt`
- Modify: `app/src/main/java/com/pocket/opencode/MainActivity.kt` (5 tabs)
- Modify: `app/src/main/java/com/pocket/opencode/UiTheme.kt` (icons only if needed)
- Create: `app/src/test/java/com/pocket/opencode/WorkspaceTest.kt`

**Interfaces:**
- Consumes: `PocketViewModel`, `EmbeddedServer`, `ServerService`.
- Produces:
  - `object Workspace { fun defaultPath(): String; fun hasAllFilesAccess(context: Context): Boolean; fun ensure(dir: File): Boolean; fun openAllFilesSettings(context: Context) }`
  - `@Composable fun ServerPanel(vm: PocketViewModel)` embedded in the «Связь» tab.
  - 5 tabs: 0 Чат, 1 Проект, 2 Модели, 3 Скилы, 4 Связь.

- [ ] **Step 1: Write the failing workspace test**

```kotlin
class WorkspaceTest {
    @Test fun `default workspace is user visible`() =
        assertEquals("/sdcard/OpenCode", Workspace.defaultPath())
    @Test fun `ensure creates nested directory`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "oc-test-" + System.nanoTime() + "/nested")
        assertTrue(Workspace.ensure(dir)); assertTrue(dir.isDirectory)
    }
}
```

- [ ] **Step 2: Implement `Workspace` and run tests**

`hasAllFilesAccess` uses `Environment.isExternalStorageManager()` on API 30+, `true` below 30. `openAllFilesSettings` starts `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` with `package:<packageName>`; fallback to `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`.

- [ ] **Step 3: Build the «Связь» screen**

Add these panels to `SettingsScreen` (above the existing URL/password block):
1. **Сервер**: mode switch «Встроенный / Внешний», status pill, port and version, buttons «Запустить», «Остановить», «Перезапустить», last 100 log lines in a mono block with «Копировать».
2. **Рабочий каталог**: path field, «Проверить доступ», warning when all-files access is missing with button «Открыть настройки доступа».
3. **Аккаунт и квота**: text «Лимит бесплатных моделей — на стороне аккаунта OpenCode» + button «Добавить свой ключ» → tab 2.
4. Keep external URL/password fields visible only when mode is `EXTERNAL`.

When mode changes, call `vm.setMode`, which stops the old service/connection and starts the new one.

- [ ] **Step 4: Add tabs**

`PocketTabs` gets 5 `NavigationBarItem`s. Labels: «Чат», «Проект», «Модели», «Скилы», «Связь». Use existing Material icons (`Send`, `List`, `Build`/`Star`, `Settings`); a missing icon may be replaced by text.

- [ ] **Step 5: Onboarding for fresh install**

If `mode == EMBEDDED` and `!Workspace.hasAllFilesAccess(context)`, show a dismissible card at the top of Chat: explain that `/sdcard/OpenCode` requires «Доступ ко всем файлам», with a button to open settings and a secondary «Продолжить во внутренней папке». After returning from settings, re-check access; if granted, restart the server with the new workspace.

- [ ] **Step 6: Device verification**

- Fresh install: card appears, grant access, server restarts with workspace `/sdcard/OpenCode`.
- Start/stop/restart buttons change the notification and status.
- Log panel shows `server listening on` and `server password`.
- Rotate the screen while on «Связь»: mode, workspace, and log survive.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/pocket/opencode/Workspace.kt app/src/main/java/com/pocket/opencode/ServerScreen.kt app/src/main/java/com/pocket/opencode/SettingsScreen.kt app/src/main/java/com/pocket/opencode/MainActivity.kt app/src/test/java/com/pocket/opencode/WorkspaceTest.kt
git commit -m "feat: manage embedded server, workspace and all-files access from UI"
```

---

### Task 6: Providers and model selection

**Files:**
- Create: `app/src/main/java/com/pocket/opencode/ProviderModels.kt`
- Create: `app/src/main/java/com/pocket/opencode/ProvidersScreen.kt`
- Modify: `app/src/main/java/com/pocket/opencode/OpenCodeApi.kt`
- Modify: `app/src/main/java/com/pocket/opencode/PocketViewModel.kt`
- Create: `app/src/test/java/com/pocket/opencode/CredentialPayloadTest.kt`
- Create: `app/src/test/java/com/pocket/opencode/ProviderCatalogTest.kt`

**Interfaces:**
- Consumes: existing API/Basic auth, `ModelChoice`.
- Produces:
  - DTOs `IntegrationMethod(type, id, label, names)`, `IntegrationConnection(type, id, label, method)`, `IntegrationInfo(id, name, methods, connections)`, `CredentialInfo(id, integrationID, label, active)`
  - `OpenCodeApi.integrations(base, password): List<IntegrationInfo>`, `credentials(...): List<CredentialInfo>`, `addKeyCredential(base, password, integrationID, key, label = "default"): CredentialInfo`, `deleteCredential(base, password, id)`
  - `object CredentialPayload { fun key(integrationID: String, key: String, label: String = "default"): JSONObject }`
  - `object ProviderCatalog { fun ordered(list: List<IntegrationInfo>): List<IntegrationInfo>; fun matches(item: IntegrationInfo, query: String): Boolean }`
  - ViewModel: `integrations`, `credentials`, `providerBusy`, `providerError`, `loadProviders()`, `addProviderKey(id, key)`, `removeProvider(id/credentialId)`

- [ ] **Step 1: Write failing tests**

```kotlin
class CredentialPayloadTest {
    @Test fun `key credential matches server schema`() {
        val o = CredentialPayload.key("anthropic", "sk-test")
        assertEquals("anthropic", o.getString("integrationID"))
        assertEquals("default", o.getString("label"))
        assertEquals("key", o.getJSONObject("value").getString("type"))
        assertEquals("sk-test", o.getJSONObject("value").getString("key"))
    }
}
class ProviderCatalogTest {
    private fun p(id: String, connected: Boolean = false) =
        IntegrationInfo(id, id, emptyList(), if (connected) listOf(IntegrationConnection("credential", "c", null, "key")) else emptyList())
    @Test fun `connected providers come first`() {
        val out = ProviderCatalog.ordered(listOf(p("zzz"), p("openai", true), p("anthropic")))
        assertEquals(listOf("openai", "anthropic", "zzz"), out.map { it.id })
    }
    @Test fun `search matches name and id`() {
        assertTrue(ProviderCatalog.matches(p("openrouter"), "open")); assertFalse(ProviderCatalog.matches(p("openrouter"), "zzz"))
    }
}
```

- [ ] **Step 2: Implement API and DTOs**

Parse `GET /api/integration` (`data` array) and `GET /api/credential` (`data` array). `POST /api/credential` body is exactly `CredentialPayload.key(...)`; success is `200` with `data` object. `DELETE /api/credential/<id>` returns `204`.

- [ ] **Step 3: Implement ViewModel state**

`loadProviders()` fetches integrations + credentials + models in parallel (three `runCatching` blocks). `addProviderKey` posts the key, reloads models, and shows a toast/status «Ключ добавлен». `removeProvider` deletes the credential and reloads. Handle `provider.updated`/`credential.*` SSE by reloading providers and models.

- [ ] **Step 4: Build the «Модели» screen**

Sections:
- **Подключённые провайдеры**: each integration with `connections.isNotEmpty()`, shows key label and «Удалить».
- **Добавить провайдера**: search field, curated first (`anthropic`, `openai`, `openrouter`, `google`, `deepseek`, `xai`, `groq`, `mistral`), then all others. Tapping a provider with a `key` method opens a password-masked key dialog with «Проверить и сохранить». Providers without a key method show «Требуется вход через браузер — позже».
- **Модели**: searchable list grouped by provider; tap selects the default model for new sessions (`vm.setModel`), current model marked.

- [ ] **Step 5: Run tests and device verification**

Unit tests pass. On device: add a test Anthropic key, see provider turn connected, model list gains Claude models, delete the key, list returns to free models. Never log the full key.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pocket/opencode/ProviderModels.kt app/src/main/java/com/pocket/opencode/ProvidersScreen.kt app/src/main/java/com/pocket/opencode/OpenCodeApi.kt app/src/main/java/com/pocket/opencode/PocketViewModel.kt app/src/test/java/com/pocket/opencode/CredentialPayloadTest.kt app/src/test/java/com/pocket/opencode/ProviderCatalogTest.kt
git commit -m "feat: manage provider keys and models in the app"
```

---

### Task 7: Skills screen and OpenCode config editor

**Files:**
- Create: `app/src/main/java/com/pocket/opencode/SkillsScreen.kt`
- Create: `app/src/main/java/com/pocket/opencode/SkillWriter.kt`
- Create: `app/src/main/java/com/pocket/opencode/ConfigStore.kt`
- Modify: `app/src/main/java/com/pocket/opencode/OpenCodeApi.kt` (skills + reload)
- Modify: `app/src/main/java/com/pocket/opencode/PocketViewModel.kt`
- Create: `app/src/test/java/com/pocket/opencode/SkillWriterTest.kt`
- Create: `app/src/test/java/com/pocket/opencode/ConfigStoreTest.kt`

**Interfaces:**
- Consumes: `ServerPaths`, `SettingsStore`, `PocketViewModel`.
- Produces:
  - `data class SkillInfo(id, name, description, path, content)`; `OpenCodeApi.skills(...)`, `OpenCodeApi.reload(...)`
  - `object SkillWriter { fun render(name: String, description: String, body: String): String; fun safeName(name: String): String; fun isWritable(path: String, configHome: File, workspace: File): Boolean }`
  - `object ConfigStore { fun validate(text: String): String?; fun read(file: File): String; fun write(file: File, text: String) }`
  - ViewModel: `skills`, `skillBusy`, `loadSkills()`, `saveSkill(name, description, body, project: Boolean)`, `deleteSkill(path: String)`

- [ ] **Step 1: Write failing tests**

```kotlin
class SkillWriterTest {
    @Test fun `renders frontmatter`() {
        assertEquals("---\nname: my-skill\ndescription: Does things\n---\n\n# Body\n", SkillWriter.render("My Skill", "Does things", "# Body\n"))
    }
    @Test fun `safe name lowercases and hyphenates`() =
        assertEquals("my-skill", SkillWriter.safeName(" My Skill! "))
    @Test fun `only config and project skills are writable`() {
        val config = File("/app/home/config"); val ws = File("/sdcard/OpenCode")
        assertTrue(SkillWriter.isWritable("/app/home/config/opencode/skills/a/SKILL.md", config, ws))
        assertTrue(SkillWriter.isWritable("/sdcard/OpenCode/.opencode/skills/a/SKILL.md", config, ws))
        assertFalse(SkillWriter.isWritable("/builtin/opencode.md", config, ws))
    }
}
class ConfigStoreTest {
    @Test fun `valid object accepted`() = assertNull(ConfigStore.validate("""{"model":"x"}"""))
    @Test fun `broken json rejected`() = assertNotNull(ConfigStore.validate("{"))
    @Test fun `non object rejected`() = assertNotNull(ConfigStore.validate("[]"))
}
```

- [ ] **Step 2: Implement and run tests**

`SkillWriter.render` trims the name/description, ensures one blank line after frontmatter, and ends with one newline. `ConfigStore.write` writes to `config.json.tmp`, validates, then renames over `config.json` and keeps `config.json.bak`.

- [ ] **Step 3: Implement skills API and ViewModel**

`GET /api/skill` returns `data: [{id,name,description,path,content}]`. `POST /api/location/reload` returns `204`; call it after every write/delete. `SkillWriter.isWritable` decides whether the editor is read-only (builtin) or editable.

- [ ] **Step 4: Build the «Скилы» screen**

List with search and source badge (`builtin`, `global`, `project`). Tap opens a full-screen editor with name, description, body, «Сохранить», «Удалить» (only writable). «Новый скил» creates `config/opencode/skills/<safeName>/SKILL.md` and reloads. Builtin skills open read-only with a copy button.

- [ ] **Step 5: Add config editor to «Связь»**

A collapsible «Конфиг OpenCode» panel with a multiline JSON field, «Сохранить», «Сбросить», validation error text, and «Перезагрузить сервер» after save. Path: `ServerPaths.forContext(...).config/opencode/config.json`.

- [ ] **Step 6: Device verification**

- Create a skill, see it in the list from `/api/skill`, open a new chat and confirm the skill is available to the agent.
- Edit and delete a skill; builtins cannot be edited.
- Save valid/invalid JSON in the config editor; invalid text is rejected without touching the file.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/pocket/opencode/SkillsScreen.kt app/src/main/java/com/pocket/opencode/SkillWriter.kt app/src/main/java/com/pocket/opencode/ConfigStore.kt app/src/main/java/com/pocket/opencode/OpenCodeApi.kt app/src/main/java/com/pocket/opencode/PocketViewModel.kt app/src/test/java/com/pocket/opencode/SkillWriterTest.kt app/src/test/java/com/pocket/opencode/ConfigStoreTest.kt
git commit -m "feat: manage skills and OpenCode config from the app"
```

---

### Task 8: Migration from Termux

**Files:**
- Create: `app/src/main/java/com/pocket/opencode/Migration.kt`
- Create: `app/src/main/java/com/pocket/opencode/MigrationScreen.kt` (embedded panel in «Связь»)
- Create: `app/src/test/java/com/pocket/opencode/DirectoryRewriterTest.kt`
- Create: `app/src/test/java/com/pocket/opencode/ArchiveLayoutTest.kt`
- Modify: `app/src/main/java/com/pocket/opencode/SettingsScreen.kt`

**Interfaces:**
- Consumes: `ServerPaths`, `EmbeddedServer`, `ServerService`, `/system/bin/tar`, Android `SQLiteDatabase`.
- Produces:
  - `object DirectoryRewriter { fun rewrite(value: String, oldRoot: String, newRoot: String): String }`
  - `object ArchiveLayout { fun destinationFor(entry: String, paths: ServerPaths): File? }`
  - `data class MigrationPreview(sessions: Int, skills: Int, credentials: Int, problems: List<String>)`
  - `data class MigrationResult(sessions: Int, skills: Int, credentials: Int, backupDir: File, warnings: List<String>)`
  - `object MigrationManager { fun preview(archive: File, paths: ServerPaths): MigrationPreview; fun import(context: Context, archive: File, paths: ServerPaths, workspace: File, oldRoot: String = "/data/data/com.termux/files/home"): MigrationResult }`

- [ ] **Step 1: Write failing tests**

```kotlin
class DirectoryRewriterTest {
    @Test fun `rewrites home root`() =
        assertEquals("/sdcard/OpenCode", DirectoryRewriter.rewrite("/data/data/com.termux/files/home", "/data/data/com.termux/files/home", "/sdcard/OpenCode"))
    @Test fun `rewrites nested project`() =
        assertEquals("/sdcard/OpenCode/proj", DirectoryRewriter.rewrite("/data/data/com.termux/files/home/proj", "/data/data/com.termux/files/home", "/sdcard/OpenCode"))
    @Test fun `does not touch similar prefix`() =
        assertEquals("/data/data/com.termux/files/home2", DirectoryRewriter.rewrite("/data/data/com.termux/files/home2", "/data/data/com.termux/files/home", "/sdcard/OpenCode"))
    @Test fun `leaves unrelated absolute path`() =
        assertEquals("/tmp/x", DirectoryRewriter.rewrite("/tmp/x", "/data/data/com.termux/files/home", "/sdcard/OpenCode"))
}
class ArchiveLayoutTest {
    private val paths = ServerPaths.under(File("/app/files/opencode"), File("/sdcard/OpenCode"))
    @Test fun `maps data directory`() =
        assertEquals(File("/app/files/opencode/home/data/opencode/opencode.db"), ArchiveLayout.destinationFor(".local/share/opencode/opencode.db", paths))
    @Test fun `maps config directory`() =
        assertEquals(File("/app/files/opencode/home/config/opencode/skills/a/SKILL.md"), ArchiveLayout.destinationFor(".config/opencode/skills/a/SKILL.md", paths))
    @Test fun `rejects unrelated entry`() =
        assertNull(ArchiveLayout.destinationFor("notes.txt", paths))
}
```

- [ ] **Step 2: Implement and run tests**

`DirectoryRewriter.rewrite` matches the root with a following `/` or end-of-string, rewrites to `newRoot + remainder`, normalizes duplicate slashes and trims trailing slash. `ArchiveLayout.destinationFor` maps `.local/share/opencode/...` to `paths.data/opencode/...` and `.config/opencode/...` to `paths.config/opencode/...`; everything else returns null.

- [ ] **Step 3: Implement preview and import**

`preview` extracts the archive to `cache/migration/preview-<ts>` using:

```kotlin
ProcessBuilder("/system/bin/tar", "-xzf", archive.absolutePath, "-C", temp.absolutePath)
```

Then locate the DB, open it read-only with `SQLiteDatabase`, count `session_v2` and `credential`, count `SKILL.md` files, and return problems for a missing DB or tar failure. Delete the temp dir.

`import`:
1. `EmbeddedServer.stop()`; wait up to 10 s for `STOPPED`.
2. Backup the existing `paths.home` to `paths.root/backup-<timestamp>` (skip logs).
3. Extract to `cache/migration/import-<ts>`.
4. Copy config and skills with `copyRecursively(overwrite = true)`; keep `.bak` of an existing `config.json`.
5. Copy `opencode.db`, `opencode.db-wal`, `opencode.db-shm` into `paths.data/opencode/`.
6. Open the DB read-write with `SQLiteDatabase.openDatabase(...)` and run parameterized updates:
   - `UPDATE session_v2 SET directory=? WHERE directory=? OR directory LIKE ?` (`oldRoot`, `oldRoot + "/%"`)
   - the same for `project.worktree` and `project_directory.directory`
   - `UPDATE session_message SET data=replace(data, ?, ?) WHERE data LIKE ?` (`oldRoot`, `newRoot`, `%oldRoot%`)
7. Close the DB, return counts and warnings for paths that could not be mapped.
8. The UI calls `ServerService.restart` and shows the restored counts.

- [ ] **Step 4: Build the migration panel**

In «Связь» add «Перенос из Termux»:
- Instructions with the exact Termux command:
  ```bash
  mkdir -p /sdcard/OpenCode/import
  tar czf /sdcard/OpenCode/import/opencode-backup.tar.gz -C $HOME .local/share/opencode .config/opencode
  ```
- Path field default `/sdcard/OpenCode/import/opencode-backup.tar.gz`, «Проверить архив» → preview counts/warnings, «Импортировать» with confirmation «Текущие сессии будут сохранены как резервная копия».
- Progress text while the server is stopped, success/error summary, «Открыть резервную копию».

- [ ] **Step 5: Device verification**

Create a real backup from Termux, import it, verify:
- session count matches,
- old sessions open and show history,
- skills list includes Termux skills,
- credentials from Termux appear in «Модели»,
- a backup directory exists with the previous home.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pocket/opencode/Migration.kt app/src/main/java/com/pocket/opencode/MigrationScreen.kt app/src/main/java/com/pocket/opencode/SettingsScreen.kt app/src/test/java/com/pocket/opencode/DirectoryRewriterTest.kt app/src/test/java/com/pocket/opencode/ArchiveLayoutTest.kt
git commit -m "feat: import Termux sessions, skills and credentials"
```

---

### Task 9: Fix mock-test UI defects and state resilience

**Files:**
- Modify: `app/src/main/java/com/pocket/opencode/Dialogs.kt`
- Modify: `app/src/main/java/com/pocket/opencode/ChatScreen.kt`
- Modify: `app/src/main/java/com/pocket/opencode/Models.kt`
- Modify: `app/src/main/java/com/pocket/opencode/PocketViewModel.kt`
- Create: `app/src/test/java/com/pocket/opencode/MessageDedupTest.kt`

**Interfaces:**
- Consumes: existing transcript/form code.
- Produces: no new public API; behavior fixes.

Already applied before this plan was written (verify, keep):
- `parseServerMessages` keeps the last message per duplicate ID.
- `ChatScreen` uses `itemsIndexed` with `"${id}#$index"` keys.
- Form external field is rendered once.
- The custom field shows only manually entered text, not the selected preset.
- Form dialog uses `Modifier.imePadding()` + `DialogProperties(decorFitsSystemWindows = false)`.

- [ ] **Step 1: Add a regression test for dedup**

```kotlin
class MessageDedupTest {
    @Test fun `last message wins for duplicate ids`() {
        val arr = JSONArray()
            .put(JSONObject().put("id","msg_1").put("type","user").put("text","old"))
            .put(JSONObject().put("id","msg_1").put("type","user").put("text","new"))
        val out = parseServerMessages(arr)
        assertEquals(1, out.size)
        assertEquals("new", (out[0].blocks.single() as UiBlock.Text).content)
    }
}
```

- [ ] **Step 2: Run the test**

Expected: FAIL if dedup is missing; PASS with the current fix.

- [ ] **Step 3: Verify the form fixes with the mock server**

Start `~/tmp/mock-oc.py` on `127.0.0.1:4098`, point the app at it (external mode), and run a full turn. Checklist:
- Permission allow and reject both render correctly.
- Form shows «Документация» once.
- «Своё значение» is empty while `Staging` is selected.
- Type into the custom field, open/close the IME, then tap «Ответить» and «Отмена»; both must work without restarting the app.
- The server log records exactly one `POST .../form/<id>/reply`.

- [ ] **Step 4: Fix the remaining IME issue if it still reproduces**

If taps still do not land, replace `AlertDialog` in `FormDialog` with a plain `Dialog` + `Surface` that has `.imePadding()` and buttons in a fixed footer outside the scrollable column. Keep the same field editor.

- [ ] **Step 5: Rotation and process-death check**

- Rotate on Chat, Project, Models, Skills, Settings: selected tab, draft, session, model and scroll position survive.
- Kill the app process with `am force-stop` while a turn is running; reopen: the cached transcript is shown and the session reconnects. The server continues if the service was not stopped.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pocket/opencode/Dialogs.kt app/src/main/java/com/pocket/opencode/ChatScreen.kt app/src/main/java/com/pocket/opencode/Models.kt app/src/main/java/com/pocket/opencode/PocketViewModel.kt app/src/test/java/com/pocket/opencode/MessageDedupTest.kt
git commit -m "fix: form rendering, IME taps and duplicate transcript keys"
```

---

### Task 10: Final cleanup, build and acceptance

**Files:**
- Delete: `app/src/main/java/com/pocket/opencode/SpikeProbe.kt`
- Modify: `app/src/main/java/com/pocket/opencode/MainActivity.kt` (remove `SpikeProbe.start`)
- Modify: `app/build.gradle.kts` (`versionCode 2`, `versionName "0.2.0"`, remove spike comments)
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-11-06-embedded-opencode-design.md` (status «реализовано»)

**Interfaces:**
- Consumes: all previous tasks.
- Produces: installable `app-debug.apk` with embedded server and no spike code.

- [ ] **Step 1: Remove spike artifacts**

Delete `SpikeProbe.kt`, remove the `SpikeProbe.start(applicationContext)` call, and remove `files/probe-home` on the test device. Keep `libocserver.so` in `jniLibs` (it is required at runtime and excluded from git).

- [ ] **Step 2: Run all unit tests**

`bash scripts/remote.sh sync && bash scripts/remote.sh test '*'` (or run each test class). Expected: all tests pass, no ignored failures.

- [ ] **Step 3: Build the APK**

```bash
bash scripts/remote.sh build
```

Expected: `BUILD SUCCESSFUL`, APK about 75–90 MB. Verify `unzip -l app-debug.apk | grep libocserver` shows the binary in `lib/arm64-v8a`.

- [ ] **Step 4: Install and run the acceptance checklist**

Install from `/data/local/tmp` after freeing space:
```bash
su -c 'cp ~/tmp/pocket.apk /data/local/tmp/pocket.apk'
su -c 'pm install -r -d /data/local/tmp/pocket.apk'
su -c 'am force-stop com.pocket.opencode; am start -n com.pocket.opencode/.MainActivity'
```

Checklist:
1. Fresh install: embedded server starts, notification appears, status «Подключено», model list loads (free models or own provider).
2. Chat turn streams reasoning, text, tools, shell output, and permission dialogs.
3. Form tests from Task 9 pass.
4. Providers: add/remove a key; model list updates without restart.
5. Skills: create/edit/delete/reload works.
6. Workspace: `/sdcard/OpenCode` is created and the agent can read/write project files there.
7. Migration from Termux works and the backup exists.
8. Swipe from recents: notification and server survive; «Стоп» stops both.
9. Rotate and force-stop: state restores, transcript is cached, reconnect works.
10. Disable network: UI shows a clear error, does not hang, and recovers when the network returns.

- [ ] **Step 5: Update README**

Document: embedded architecture, APK size, arm64 only, permissions (`MANAGE_EXTERNAL_STORAGE`, notifications), first-run flow, provider keys, skills, workspace, migration command, backup/export, known limitation (MCP deferred, free-model quota is account-wide).

- [ ] **Step 6: Commit and tag**

```bash
git add -A
git commit -m "feat: ship embedded OpenCode server in the Android app"
git tag v0.2.0-embedded
```

---

## Self-Review

- **Spec coverage:** embedded server (Tasks 1–3), auto-connect/mode (Task 4), private home + workspace + all-files access (Tasks 2, 5), providers/keys (Task 6), skills/config (Task 7), logs/lifecycle/notification (Tasks 3, 5), migration (Task 8), UI fixes/state (Task 9), full access and acceptance (Tasks 5, 10). MCP is explicitly deferred in the spec and absent from tasks.
- **Type consistency:** `ServerPaths`, `EmbeddedStatus`, `ServerMode`, `IntegrationInfo`, `SkillInfo` names are used consistently across tasks. Settings fields (`mode`, `workspacePath`, `serverPort`) are defined in Task 4 and consumed by Tasks 3/5.
- **Review Focus tests:** process-exit error (Task 2 step 5), busy port (Task 1 `PortSelectionTest`), chunked output (Task 1 `ServerOutputTest`), missing all-files access (Task 5 onboarding), migration problems (Task 8 preview), duplicate IDs (Task 9 `MessageDedupTest`).
- **Proportion:** implementation steps name signatures and exact values; code blocks are limited to tests and small parsers.
- **Open risk:** the real model cannot be exercised until the account quota resets or the user adds a provider key. The acceptance checklist accepts either free models or a user key.
