# Windows Persistent Downloads, Task Center, Library Updates, and Notifications Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use `subagent-driven-development` to execute this plan task-by-task. Every production change follows `test-driven-development`; every task receives a fresh spec review and code-quality review before the next task starts.

**Goal:** Deliver Phase 5 of the Mihon Windows Port: persistent download queueing with restart recovery, atomic chapter writing and page-level resume, task center UI for queue monitoring and speed metrics, scheduled background library updates with new chapter discovery, offline reading of downloaded chapters via reader-core, and resilient Windows desktop notifications.

**Architecture:**
- `desktop-app/src/main/kotlin/mihon/desktop/download`: Core download models, atomic JSON/file queue store (`downloads.json`), disk provider with disk space verification and atomic directory rename / CBZ creation, and coroutine-based `DesktopDownloader` managing concurrency, page retries, and speed calculation.
- `desktop-app/src/main/kotlin/mihon/desktop/updates`: Periodic and manual library update service checking online sources for new chapters, inserting them atomically into `SqlDelightLibraryRepository`, and triggering notifications.
- `desktop-app/src/main/kotlin/mihon/desktop/notification`: Resilient Windows notification service supporting SystemTray / Windows Toast notifications with graceful fallback when headless or disabled.
- `desktop-app/src/main/kotlin/mihon/desktop/ui/tasks`: `DownloadsScreen` (Task Center) displaying queue items, progress, active speed, pause/resume/cancel controls.
- `desktop-app/src/main/kotlin/mihon/desktop/ui/updates`: `UpdatesScreen` displaying recently discovered chapters with direct read navigation.
- Shell and navigation wiring: connecting `DesktopDestination.Downloads` and `DesktopDestination.Updates`, plus "Download" actions on manga detail chapter lists.

**Tech Stack:** Kotlin/JVM 2.4.10, Java 17/21, kotlinx-coroutines 1.11.0, kotlinx-serialization 1.11.0, Compose Multiplatform Desktop 1.12.0, OkHttp 5.5.0, SQLDelight 2.0.2, JUnit 5/6, Kotest.

---

## Fixed scope and invariants

- [x] Downloads are persistent across application restarts. In-flight downloads are saved to `downloads.json` and restored on startup in a paused/queued state.
- [x] Incomplete downloads use a temporary directory suffix (`_tmp`). When all pages are downloaded and verified, the folder is atomically renamed to the final chapter directory.
- [x] Resuming or retrying a download preserves already completed pages and only fetches missing or failed pages.
- [x] Disk space is validated before and during downloads; if free space is below threshold (e.g. 50 MB), downloading halts with an explicit error without corrupting existing chapters.
- [x] Downloaded chapters register as local chapter assets in the database so `reader-core` can immediately read them offline without network access.
- [x] Scheduled library updates check favorited manga for new chapters using the extension process manager, insert newly discovered chapters into the database, and trigger notifications.
- [x] Desktop notifications are robust and never crash the application if Windows notifications or SystemTray are unavailable.
- [x] Task Center UI provides clear visibility into active downloads, transfer speeds, queue order, and pause/resume/retry/cancel actions.
- [x] No regression on Android: `:app:testDebugUnitTest :app:assembleDebug` and `spotlessCheck` must pass cleanly throughout.

---

## Execution Tasks

### Task 1: Download Data Models & Persistent Queue Store
- Implement `DownloadModels.kt`: `DesktopDownload`, `DownloadPage`, `DownloadStatus`, `PageStatus`.
- Implement `DownloadStore.kt`: atomic JSON persistence (`downloads.json`) in AppDirectories, saving queue state and restoring on startup.
- Unit tests: serialize, deserialize, save, restore, crash-recovery state reset (`DOWNLOADING` -> `QUEUED`).

### Task 2: Download Disk Provider & Atomic Finalizer
- Implement `DownloadDiskProvider.kt`:
  - Path resolution: `{downloadRoot}/{sourceId}/{mangaTitle}/{chapterName}`.
  - Temporary directory: `{chapterName}_tmp`.
  - Disk space checking (`File.usableSpace`) with configurable threshold.
  - Page file naming: `001.jpg`, `002.png`, etc.
  - Atomic finalizer: verifies all page files exist and are non-empty, creates ComicInfo.xml metadata, atomically renames `_tmp` to target chapter directory.
  - Database asset registration: registers chapter in `local_chapter_asset` table.
- Unit tests: temp directory creation, page saving, disk space check, atomic rename, corrupt/incomplete rejection.

### Task 3: Desktop Downloader Engine
- Implement `DesktopDownloader.kt`:
  - Concurrency management: parallel downloads and parallel pages.
  - Page download pipeline using `DesktopNetworkHelper` / `OnlineChapterSource` / extension IPC.
  - Speed calculation: exponential moving average of downloaded bytes per second.
  - State flows: `queueState: StateFlow<List<DesktopDownload>>`, `isRunning: StateFlow<Boolean>`, `speedBytesPerSec: StateFlow<Double>`.
  - Methods: `enqueue(manga, chapters)`, `start()`, `pause()`, `resume()`, `cancel(chapterId)`, `retry(chapterId)`, `clearCompleted()`.
- Unit tests: enqueue, download all pages, pause/resume, error handling, retry missing pages only, speed metrics.

### Task 4: Offline Reading for Downloaded Chapters
- Connect downloaded chapters with `reader-core`:
  - When `readerFactory.createSession().open(chapterId)` is called, check if downloaded local asset exists.
  - If downloaded asset exists, use local file reader rather than remote network stream.
  - Test: download chapter, simulate network offline, open in reader, verify pages render correctly.

### Task 5: Library Update Service & Scheduler
- Implement `LibraryUpdateModels.kt`: update jobs and result summaries.
- Implement `DesktopLibraryUpdateService.kt`:
  - Query all favorited manga in `SqlDelightLibraryRepository`.
  - Fetch latest chapter list for each manga via `DesktopExtensionProcessManager` / `OnlineMangaSyncService`.
  - Compare with existing chapters in DB, insert newly discovered chapters with `date_fetch = currentTimeMillis()`.
  - Return `LibraryUpdateResult` containing newly added chapters count and affected manga.
- Implement `DesktopUpdateScheduler.kt`:
  - Background periodic coroutine scheduler with interval support (manual, hourly, daily).
- Unit tests: new chapter detection, DB update, error handling when a source fails.

### Task 6: Desktop Notifications Service
- Implement `DesktopNotificationService.kt`:
  - Interface: `notifyDownloadComplete(mangaTitle, chapterName)`, `notifyDownloadError(mangaTitle, chapterName, error)`, `notifyLibraryUpdate(newChaptersCount, mangaCount)`.
  - Implementation: uses `SystemTray` / PowerShell Windows Toast with safe fallback when headless or disabled.
- Unit tests: notification invocation, error resilience, fallback behavior.

### Task 7: Task Center (Downloads Screen) UI
- Implement `DownloadsScreen.kt`:
  - Header: Active queue count, total speed (e.g. `2.4 MB/s`), Pause All, Resume All, Clear Completed buttons.
  - Download cards list:
    - Manga title, chapter name, source.
    - Status chip (Queued, Downloading, Paused, Completed, Error).
    - Linear progress bar and page progress text (e.g. `14 / 28 pages - 50%`).
    - Individual item controls: Pause/Resume, Retry, Cancel.
- Compose UI tests: queue rendering, progress updates, button clicks trigger downloader methods.

### Task 8: Updates Screen UI & Shell Navigation
- Implement `UpdatesScreen.kt`:
  - Header with "Check for Updates" button, last checked timestamp, and progress indicator.
  - List of recently fetched chapters grouped by date and manga.
  - "Read" action button on each chapter row to launch chapter in reader.
- Wire into `DesktopShell.kt`:
  - Connect `DesktopDestination.Downloads` to `DownloadsScreen`.
  - Connect `DesktopDestination.Updates` to `UpdatesScreen`.
  - Connect `DesktopDestination.Browse` to `BrowseScreen`.
- Connect "Download Chapter" button on `MangaDetailScreen` and `OnlineMangaDetailScreen`.
- Compose UI tests: UpdatesScreen rendering, shell navigation to Downloads and Updates.

### Task 9: End-to-End Download & Update Pipeline Verification
- Implement `DownloadAndReadOfflinePipelineTest.kt`:
  - Enqueue online manga chapter for download.
  - Downloader runs, saves all pages, renames `_tmp` folder, updates DB.
  - Kill / simulate network offline.
  - Reader opens chapter from downloaded storage and reads pages offline.
  - Run library update service, verify new chapters discovered and notified.
- Create verification script: `scripts/verify-desktop-downloads.ps1`.
- Create evidence document: `docs/superpowers/evidence/windows-downloads-taskcenter.md`.
- Android regression gate: `:app:testDebugUnitTest :app:assembleDebug` and `spotlessCheck`.

---

## Completion gate

Phase 5 is complete only when all of the following are simultaneously true:

- [x] All tasks above are implemented and verified.
- [x] Download queue is persistent and safely resumes after simulated restart.
- [x] Incomplete downloads use temporary folders and atomically finalize without page loss or corruption.
- [x] Downloaded chapters can be read offline in the desktop reader.
- [x] Scheduled and manual library updates correctly discover new chapters and update the database.
- [x] Desktop notifications dispatch without errors or application crashes.
- [x] Task Center (Downloads) and Updates screens are fully integrated into `DesktopShell`.
- [x] Android regression gate (`:app:testDebugUnitTest :app:assembleDebug`) passes.
- [x] Spotless check passes across all modules.
- [x] Working tree is clean after the final documentation commit; no branch is pushed unless explicitly requested.
