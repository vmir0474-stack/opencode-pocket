# Skill Installer and In-App Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the built-in agent find, download, install and update skills on request, and let the app update itself from GitHub releases without opening a browser.

**Architecture:** A Kotlin installer resolves sources (GitHub repo, skills.sh id, raw `SKILL.md`), safely extracts archives with commons-compress, validates frontmatter, writes OpenCode's native `config/opencode/skills/<name>/SKILL.md` layout, records provenance in `.pocket-skill.json`, and reloads the server. The same code is exposed as debug-bridge actions and a `pocket-skill` CLI wrapper in PATH, so the agent uses one documented command instead of guessing. Updates use the GitHub Releases API, verify the asset's `sha256` digest, and hand the APK to `PackageInstaller`.

**Tech Stack:** Kotlin, OkHttp 4.12, commons-compress 1.27.1, org.json, Jetpack Compose, Android `PackageInstaller`.

**Spec:** `docs/superpowers/plans/2026-11-09-skill-installer-and-updater.md` (this document is the design and the plan; the user approved the native-installer approach).

## Global Constraints

- `minSdk 26`, `targetSdk 28`, package `com.pocket.opencode`, arm64-v8a only.
- No new runtime dependency (no Node, no Python, no `gh`).
- Skills must land exactly where OpenCode discovers them: `<config>/opencode/skills/<name>/SKILL.md` (global) or `<workspace>/.opencode/skills/<name>/SKILL.md` (project). Never invent a parallel directory.
- A skill is untrusted text. Never execute scripts during install. Show source and description before enabling unless the user already said "install".
- Rootless installs always show Android's confirmation dialog. Never claim silent updates.
- All network failures return a clear message; never crash the server or the bridge.
- Archive extraction: reject absolute paths, `..`, symlinks and devices; cap 500 files, 20 MB total, 5 MB per file.
- Do not overwrite an existing skill unless `overwrite` is explicitly set.

## Review Focus

- A repo with several `SKILL.md` files: the installer must pick the requested one, not all of them.
- A malicious archive with `../../` or a symlink: nothing may be written outside the temp directory.
- A raw `SKILL.md` with missing or broken frontmatter: reject with a readable error, leave no half-installed directory.
- GitHub API rate limit / offline: `pocket-skill search` and the update check must explain the failure, not hang.
- Android without "install unknown apps": the update button must open the right settings screen instead of failing silently.

---

### Task 1: Source parsing

**Files:**
- Create: `app/src/main/java/com/pocket/opencode/SkillSource.kt`
- Test: `app/src/test/java/com/pocket/opencode/SkillSourceTest.kt`

**Interfaces:**
- Produces: `sealed class SkillSource` with `Repo(owner, repo, ref, skill)`, `Raw(url)`, `SkillsSh(owner, repo, skill)`; `SkillSourceParser.parse(input: String): SkillSource?`; `SkillSource.label: String`.

- [ ] Write tests for `owner/repo`, `owner/repo@skill`, `owner/repo/skill`, GitHub tree/blob URLs, `raw.githubusercontent.com`, `skills.sh/...`, and rejects empty/`javascript:` input.
- [ ] Run tests, see them fail.
- [ ] Implement parser; trim, strip trailing `/`, reject non-http schemes, keep path segments URL-decoded.
- [ ] Run tests, commit.

### Task 2: Safe archive selection

**Files:**
- Create: `app/src/main/java/com/pocket/opencode/SkillArchive.kt`
- Test: `app/src/test/java/com/pocket/opencode/SkillArchiveTest.kt`

**Interfaces:**
- Produces: `data class SkillEntry(val path: String, val directory: Boolean, val size: Long)`; `SkillArchive.safePath(path: String): Boolean`; `SkillArchive.skillDirs(entries: List<SkillEntry>, skill: String?): List<String>`; `SkillArchive.extract(tar: InputStream, dest: File, selected: Set<String>, limits: Limits): Int`.

- [ ] Tests: safe paths reject `..`, absolute, backslash; `skillDirs` finds dirs containing `SKILL.md`, filters by dir name or frontmatter name; extraction ignores unselected entries and symlinks; limits enforced.
- [ ] Run tests, see them fail.
- [ ] Implement with `TarArchiveInputStream` + `GzipCompressorInputStream`.
- [ ] Run tests, commit.

### Task 3: Installer and provenance

**Files:**
- Create: `app/src/main/java/com/pocket/opencode/SkillInstaller.kt`
- Test: `app/src/test/java/com/pocket/opencode/SkillInstallerTest.kt`

**Interfaces:**
- Produces: `SkillInstaller.search(query)`, `install(source, paths, project, overwrite)`, `update(paths, name, all)`, `remove(paths, name)`, `list(paths)`; `SkillCandidate(id, name, source, installs)`; `InstalledSkill(name, path, source, files)`.
- Consumes: `SkillSourceParser`, `SkillArchive`, `SkillWriter.safeName`, `ServerPaths`.

- [ ] Tests: destination path is `config/opencode/skills` or `workspace/.opencode/skills`; existing skill refused without overwrite; provenance JSON round-trips; raw URL derives name from frontmatter; frontmatter missing name/description rejected.
- [ ] Run tests, see them fail.
- [ ] Implement with an injectable `SkillFetcher` (OkHttp in production, fake in tests).
- [ ] Run tests, commit.

### Task 4: Agent-facing bridge and CLI

**Files:**
- Modify: `app/src/main/java/com/pocket/opencode/DebugBridge.kt` (allowed actions)
- Modify: `app/src/main/java/com/pocket/opencode/PocketViewModel.kt` (`debugAction`)
- Modify: `app/src/main/java/com/pocket/opencode/Toolchain.kt` (write `pocket-skill` wrapper)
- Modify: `app/src/main/java/com/pocket/opencode/Environment.kt` (instructions + probe)
- Test: `app/src/test/java/com/pocket/opencode/PocketSkillCliTest.kt`

**Interfaces:**
- Produces: actions `skill-search`, `skill-install`, `skill-update`, `skill-remove`, `skill-list`; CLI `pocket-skill search|install|update|remove|list`.

- [ ] Tests: wrapper script contains token path, port loop, all subcommands; instructions mention `pocket-skill` and the confirmation rule.
- [ ] Run tests, see them fail.
- [ ] Implement actions; use `runBlocking` with a 60 s timeout; reload and `loadSkills()` after a successful install/update/remove.
- [ ] Implement wrapper in `Toolchain.ensure` with mode 0755.
- [ ] Run tests, commit.

### Task 5: Skills UI

**Files:**
- Modify: `app/src/main/java/com/pocket/opencode/SkillsScreen.kt`
- Modify: `app/src/main/java/com/pocket/opencode/PocketViewModel.kt`

**Interfaces:**
- Produces: `PocketViewModel.installSkill(source, project, overwrite)`, `updateAllSkills()`.

- [ ] Add "Установить по ссылке" dialog and "Обновить все" action.
- [ ] Show source/status errors in the existing status lines.
- [ ] Build, commit.

### Task 6: Update check

**Files:**
- Create: `app/src/main/java/com/pocket/opencode/UpdateChecker.kt`
- Test: `app/src/test/java/com/pocket/opencode/UpdateCheckerTest.kt`

**Interfaces:**
- Produces: `data class UpdateInfo(tag, versionName, versionCode, apkUrl, size, sha256, notes)`; `UpdateChecker.latest(currentName, currentCode): UpdateInfo?`; `compareVersions(a, b): Int`.

- [ ] Tests: semver compare (`0.10.0` > `0.9.9`), prerelease/draft skipped, asset selection, `digest` parsed, `version.json` preferred over tag.
- [ ] Run tests, see them fail.
- [ ] Implement with OkHttp and a 15 s timeout; `User-Agent` required by GitHub.
- [ ] Run tests, commit.

### Task 7: APK download and install

**Files:**
- Create: `app/src/main/java/com/pocket/opencode/UpdateInstaller.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`REQUEST_INSTALL_PACKAGES`)
- Test: `app/src/test/java/com/pocket/opencode/UpdateInstallerTest.kt`

**Interfaces:**
- Produces: `UpdateInstaller.canInstall()`, `requestPermissionIntent()`, `download(info, onProgress): File`, `install(file)`.

- [ ] Tests: SHA-256 verification rejects a wrong digest; file name sanitization.
- [ ] Run tests, see them fail.
- [ ] Implement `PackageInstaller` session and `STATUS_PENDING_USER_ACTION` handling.
- [ ] Run tests, commit.

### Task 8: Update UI and startup check

**Files:**
- Modify: `app/src/main/java/com/pocket/opencode/PocketViewModel.kt`
- Modify: `app/src/main/java/com/pocket/opencode/SettingsScreen.kt`
- Modify: `app/src/main/java/com/pocket/opencode/MainActivity.kt`
- Create: `app/src/main/java/com/pocket/opencode/UpdateStore.kt`

**Interfaces:**
- Produces: `updateInfo`, `updateBusy`, `updateProgress`, `updateError`, `updateStatus`; `checkForUpdates(manual)`, `downloadAndInstallUpdate()`, `dismissUpdate()`.

- [ ] Add Settings panel and startup dialog.
- [ ] Throttle automatic checks to once per 12 h; manual check always runs.
- [ ] Build, commit.

### Task 9: Release plumbing and verification

**Files:**
- Modify: `scripts/remote.sh` (or add `scripts/version-json.sh`)
- Modify: `README.md`
- Modify: `app/build.gradle.kts` (`versionCode 20`, `versionName "0.8.0"`)

- [ ] Generate `version.json` (`versionCode`, `versionName`) and attach it to every release.
- [ ] Run all unit tests.
- [ ] Build release, install over 0.7.0, verify data preserved.
- [ ] On device: ask the agent to install `anthropics/skills@pdf`, confirm it appears in `/api/skill`; verify the update panel reports "up to date" for 0.8.0.
- [ ] Commit, tag, publish with Russian notes and the APK.
