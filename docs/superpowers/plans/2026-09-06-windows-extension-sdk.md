# Windows Extension SDK, Isolated Host, and Online Sources Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use `subagent-driven-development` to execute this plan task-by-task. Every production change follows `test-driven-development`; every task receives a fresh spec review and code-quality review before the next task starts.

**Goal:** Deliver Phase 4 of the Mihon Windows Port: a secure and isolated Extension SDK, standalone JVM host process with Windows Job Object sandbox, local Named Pipe IPC protocol with brokered HTTP network engine, extension repository catalog management, Browse & Source screens, online manga details & library addition, and online chapter streaming in the desktop reader.

**Architecture:** 
- Add `:extension-sdk` as a pure Kotlin/JVM module defining source interfaces, data models, manifest schema, and `.mext` package packaging and validation.
- Add `:extension-host` as a standalone JVM entry point executing extensions in a separate worker process over local Windows Named Pipes.
- `desktop-app` owns the Windows sandbox process manager (Job Object memory/CPU limits), local Named Pipe server, brokered HTTP client (validating declared domains, rate limiting, and cookies), extension store repository, and Compose UI for Browse (Sources / Extensions tabs), Catalog, Online Details, and Reader streaming via `OnlineChapterSource`.

**Tech Stack:** Kotlin/JVM 2.4.10, Java 17, kotlinx-coroutines 1.11.0, kotlinx-serialization 1.11.0, Compose Multiplatform Desktop 1.12.0, OkHttp 5.5.0, JNA 5.19.1 (Windows Job Object APIs), JUnit 6, Kotest assertions.

**Authoritative references:** [approved Windows design](../specs/2026-08-31-windows-port-design.md), [Tachiyomi/Mihon source-api](../../source-api), [Windows Job Objects documentation](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects).

---

## Fixed scope and invariants

- [ ] Extensions execute exclusively in a separate host process. An extension never receives direct database access, main process memory handles, or raw filesystem access outside its private directory.
- [ ] Network access from extensions is strictly brokered. The extension host requests HTTP operations across the authenticated local IPC pipe; the main process enforces declared domain whitelists, rate limits, timeouts, and cookie jars.
- [ ] Extension package format is `.mext`: a ZIP container with `manifest.json`, compiled classes/JAR, `icon.png`, and a SHA-256 digest.
- [ ] Manifest validation strictly enforces: valid semantic versioning, positive source IDs, valid package name (matching `^[a-zA-Z0-9_.]+$`), non-empty name, and explicit declared network domains.
- [ ] Extension host process failure, crash, OOM, or timeout never crashes the main desktop application. The process manager detects abnormal exit, logs structured diagnostics, and cleanly restarts or displays typed recovery UI.
- [ ] IPC protocol uses Windows Named Pipes (`\\.\pipe\mihon-w-ext-{guid}`) with length-prefixed JSON-lines framing, monotonic request sequence IDs, cancellation support, and strict 30-second default call deadlines.
- [ ] Extensions require explicit user approval before installation: the permission UI must display package name, publisher, version, declared network domains, and untrusted signature warnings.
- [ ] Online chapters seamlessly integrate into `reader-core`: `OnlineChapterSource` loads page lists via IPC, streams image data through the memory-budgeted decoder pipeline, and renders across all 6 reading modes.
- [ ] Adding an online manga to the library creates or merges database records with `source = sourceId`, preserving compatibility with backup schemas.
- [ ] No regression on Android: `:app:testDebugUnitTest :app:assembleDebug` and `spotlessCheck` must pass cleanly throughout.

---

## Required public contracts

```kotlin
// extension-sdk/src/main/kotlin/mihon/extension/model/ExtensionManifest.kt
@Serializable
data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val libVersion: Double,
    val lang: String,
    val isNsfw: Boolean = false,
    val sources: List<SourceDescriptor>,
    val declaredDomains: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
)

@Serializable
data class SourceDescriptor(
    val id: Long,
    val name: String,
    val lang: String,
    val className: String,
    val supportsLatest: Boolean = true,
)
```

```kotlin
// extension-sdk/src/main/kotlin/mihon/extension/source/WindowsSource.kt
interface WindowsSource {
    val id: Long
    val name: String
    val lang: String
    val supportsLatest: Boolean
}

interface WindowsCatalogueSource : WindowsSource {
    suspend fun getPopularManga(page: Int): MangasPage
    suspend fun getLatestUpdates(page: Int): MangasPage
    suspend fun searchManga(page: Int, query: String, filters: FilterList): MangasPage
    suspend fun getMangaDetails(manga: SManga): SManga
    suspend fun getChapterList(manga: SManga): List<SChapter>
    suspend fun getPageList(chapter: SChapter): List<Page>
    fun getFilterList(): FilterList = FilterList()
}
```

```kotlin
// extension-sdk/src/main/kotlin/mihon/extension/ipc/IpcMessage.kt
@Serializable
sealed interface IpcMessage {
    val requestId: Long
}

@Serializable
data class IpcRequest(
    override val requestId: Long,
    val command: String,
    val payloadJson: String = "",
) : IpcMessage

@Serializable
data class IpcResponse(
    override val requestId: Long,
    val success: Boolean,
    val payloadJson: String = "",
    val error: String? = null,
) : IpcMessage
```

---

## Tasks

### Task 1: Establish `:extension-sdk` Contracts and Models
- Add `include(":extension-sdk")` in `settings.gradle.kts`.
- Implement data contracts: `ExtensionManifest`, `WindowsSource`, `WindowsCatalogueSource`, `WindowsHttpSource`, `SManga`, `SChapter`, `Page`, `MangasPage`, `FilterList`, `Filter`.
- Implement `ExtensionPackageValidator` for `.mext` verification: package structure, manifest parsing, domain checks, path traversal rejection.
- Unit tests: Manifest serialization, validator tests, invalid packages, path traversal rejection.

### Task 2: Implement IPC Protocol and Framing
- Implement length-prefixed JSON framing over Windows Named Pipes and generic ByteStreams.
- Define protocol commands: `Ping`, `LoadExtension`, `GetSources`, `GetPopularManga`, `GetLatestUpdates`, `SearchManga`, `GetMangaDetails`, `GetChapterList`, `GetPageList`, `GetFilterList`.
- Implement bi-directional request handling and callback for `BrokerHttpRequest` / `BrokerHttpResponse`.
- Unit tests: Request/response correlation, concurrency, timeouts, cancellation, serialization errors.

### Task 3: Build `:extension-host` Runtime
- Add `include(":extension-host")` in `settings.gradle.kts`.
- Implement `MainKt` entry point, command-line parsing, and pipe client connection.
- Implement `ExtensionClassLoader` to load `.mext` JARs isolated from host internals.
- Implement `BrokeredHttpInterceptor` bridging extension HTTP requests back to main app over IPC.
- Unit tests: Dynamic loading, source execution, error trapping, brokered HTTP callbacks.

### Task 4: Build Windows Sandbox and Process Manager
- Implement `WindowsExtensionProcessManager` in `desktop-app`.
- Process execution management: JRE discovery, arguments, pipe server.
- Attach Windows Job Object using JNA (limits: memory ceiling, kill on parent close).
- Liveness detection: Heartbeat ping, timeout watchdog, auto-restart on unexpected exit.
- Unit tests: Process launch, IPC handshake, timeout handling, crash recovery, clean shutdown.

### Task 5: Extension Store Repository and Catalog Service
- Implement repository index fetching (`index.min.json` / `repo.json`).
- Parse available extensions, versions, library versions, language tags, and `.mext` URLs.
- Persistence of custom extension repository URLs in `DesktopPreferenceStore`.
- Unit tests: Multi-repo aggregation, version comparison, legacy and v2 store schemas.

### Task 6: Secure Extension Installer and Verification
- Implement `DesktopExtensionInstaller`: Download `.mext`, compute SHA-256, verify against catalog hash.
- Unpack package into isolated extension directory.
- Permission analysis: Extract declared network domains and capabilities for user approval.
- Unit tests: Hash mismatch rejection, corrupted ZIP rejection, domain extraction, clean uninstall.

### Task 7: Brokered HTTP Network Engine
- Implement `DesktopNetworkHelper` and HTTP broker in `desktop-app`.
- Validate requested URLs against the extension's declared domains.
- Enforce standard User-Agent, referer headers, cookies, and rate limits.
- Unit tests: Domain whitelist enforcement, header forwarding, error propagation, cancellation.

### Task 8: Build Browse Screen & Extensions Management UI
- Compose Desktop UI for `Browse` destination in `DesktopShell`.
- Two-tab layout: **Sources** and **Extensions**.
- Extensions tab: List available, installed, and updateable extensions.
- Security confirmation modal: Display package name, publisher, requested domains, and untrusted warning before install.
- Repository management modal: Add, edit, remove repository URLs.
- Compose UI tests: Tab switching, search filtering, install button states, dialog interaction.

### Task 9: Build Source Catalog & Search UI
- `BrowseSourceScreen`:
  - Source header with mode selector (Popular / Latest).
  - Search input bar with debounce.
  - Filter drawer supporting Text, Checkbox, and Select filters.
  - Manga grid with thumbnail caching, title, and in-library badge.
  - Pagination controls (Previous / Next / page indicator).
- Compose UI tests: Mode switching, filter application, pagination events, error retry.

### Task 10: Online Manga Details & Library Insertion
- `OnlineMangaDetailScreen`:
  - Display cover, title, author, description, tags, and status.
  - "Add to Library" action: atomically creates/updates manga in `SqlDelightLibraryRepository` and links to online source.
  - Chapter list: Chapter numbers, names, scanlator, upload date.
  - Integration tests: Add to library, duplicate check, detail refresh, chapter sync.

### Task 11: Online Chapter Reading Integration
- Implement `OnlineChapterSource` adapting extension `PageList` to `reader-core`.
- Stream page images through HTTP broker into `ImageIoPageDecoder`.
- Support all 6 reading modes (`SINGLE_LTR`, `SINGLE_RTL`, `DUAL_LTR`, `DUAL_RTL`, `VERTICAL`, `WEBTOON`) seamlessly.
- Preserve reading progress in database when reading online chapters.
- Automated tests: Page loading, image caching, connection timeout fallback, progress write.

### Task 12: End-to-End Packaged Verification and Acceptance
- Provide real/reference extension package (`sample-manga-extension.mext`) and mock server.
- Run complete packaged verification across multiple processes:
  1. Repository install & permission confirmation.
  2. Browse popular, search with filter, view manga details.
  3. Read online chapter in reader, verify image decode, persist progress.
  4. Kill host process and verify graceful recovery.
- Create evidence document: `docs/superpowers/evidence/windows-extension-sdk.md`.
- Android regression gate (`:app:testDebugUnitTest :app:assembleDebug`) and `spotlessCheck`.

---

## Completion gate

Phase 4 is complete only when all of the following are simultaneously true:

- [ ] All checkboxes above are checked with matching evidence.
- [ ] `:extension-sdk`, `:extension-host`, `:desktop-library-data`, `:reader-core`, and `:desktop-app` unit tests pass cleanly.
- [ ] Brokered HTTP engine correctly restricts network requests to declared extension domains.
- [ ] Windows sandbox Job Object correctly enforces memory and process lifecycle bounds.
- [ ] Online chapter reading works smoothly across all 6 reading modes with progress persistence.
- [ ] Adding online manga to library updates local database and library views without errors.
- [ ] Host process crash recovery is verified without UI hang or application exit.
- [ ] Android regression suite (`:app:testDebugUnitTest :app:assembleDebug`) passes.
- [ ] Spotless check passes across all modules.
- [ ] Working tree is clean after the final documentation commit; no branch is pushed unless explicitly requested.
