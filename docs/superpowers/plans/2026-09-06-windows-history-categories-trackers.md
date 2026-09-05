# Windows History, Categories, Trackers, and Offline Queue Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use `subagent-driven-development` to execute this plan task-by-task. Every production change follows `test-driven-development`; every task receives a fresh spec review and code-quality review before the next task starts.

**Goal:** Deliver Phase 6 of the Mihon Windows Port: reading history tracking with relative date grouping and resume reading, category management and library category filtering, tracking services for all 9+ supported trackers, offline tracking update queueing with auto-flush, and conflict resolution.

**Architecture:**
- `desktop-library-data`: Database queries in `Library.sq` for category CRUD, history details join & deletion, and tracking queries.
- `desktop-app/src/main/kotlin/mihon/desktop/history`: `HistoryModels.kt` and `DesktopHistoryService.kt` for relative date grouping (Today, Yesterday, Past 7 Days, Older).
- `desktop-app/src/main/kotlin/mihon/desktop/category`: `CategoryModels.kt` and category management logic.
- `desktop-app/src/main/kotlin/mihon/desktop/track`: Tracker interfaces, tracker implementations (MyAnimeList, AniList, Kitsu, Shikimori, Bangumi, Komga, MangaUpdates, Kavita, Suwayomi), persistent `OfflineTrackingQueue.kt`, and `TrackingConflictResolver.kt`.
- `desktop-app/src/main/kotlin/mihon/desktop/ui`:
  - `HistoryScreen.kt` for `DesktopDestination.History`.
  - Category tabs/chips and management in `LibraryScreen.kt`.
  - `TrackingDialog.kt` in manga details.

**Tech Stack:** Kotlin/JVM 2.4.10, Java 17/21, kotlinx-coroutines 1.11.0, kotlinx-serialization 1.11.0, Compose Multiplatform Desktop 1.12.0, SQLDelight 2.0.2, JUnit 5/6, Kotest.

---

## Fixed scope and invariants

- [x] Categories can be created, renamed, reordered, and deleted. Deleting a category does not delete its manga.
- [x] Manga can be assigned to multiple categories. Library screen provides category tabs/chips to filter manga by category ("All" default).
- [x] History records every read chapter with last read timestamp and cumulative read duration.
- [x] History view groups items by relative time (Today, Yesterday, Earlier this week, Earlier) and supports search, single-item deletion, and "Clear all history".
- [x] "Resume" action in History opens the chapter directly in the reader.
- [x] Tracker support for all core Mihon trackers (MAL, AniList, Kitsu, Shikimori, Bangumi, Komga, MangaUpdates, Kavita, Suwayomi).
- [x] Offline tracking changes enter `tracking-queue.json` and automatically flush when back online or on manual sync.
- [x] Tracking conflicts between local and remote are detected and resolvable via `LOCAL_WINS`, `REMOTE_WINS`, or `PROMPT`.
- [x] Full regression checks: `verify-desktop-trackers.ps1`, `spotlessCheck`, and `:app:testDebugUnitTest :app:assembleDebug` must pass.

---

## Execution Tasks

### Task 1: Database Schema Queries & Repository Extensions
- Update `desktop-library-data/src/main/sqldelight/mihon/desktop/library/db/Library.sq`:
  - Categories: `selectAllCategories`, `deleteCategory`, `updateCategoryName`, `updateCategoryOrder`, `clearCategoriesForManga`.
  - History: `selectHistoryWithDetails`, `deleteHistoryByChapterId`, `clearAllHistory`.
  - Tracking: `selectTrackingForManga`, `deleteTracking`.
- Update `LibraryRepository.kt`, `LibraryMutationPort.kt`, and `SqlDelightLibraryRepository.kt`.
- Unit tests in `SqlDelightLibraryRepositoryTest.kt`.

### Task 2: History Models, Service & Compose HistoryScreen
- Implement `HistoryModels.kt` (relative time grouping, `HistoryGroup`).
- Implement `DesktopHistoryService.kt` (history search, resume navigation, delete, clear all).
- Implement `HistoryScreen.kt` Compose UI with test tags (`history-search`, `history-item-resume`, `history-item-delete`, `history-clear-all`).
- UI unit tests: `HistoryScreenTest.kt`.

### Task 3: Category Management & Library Screen Filtering
- Implement `CategoryModels.kt`.
- Update `LibraryPresenter.kt` to observe categories and filter by selected category ID.
- Update `LibraryScreen.kt` with category chips row and category management dialog (`CategoryDialog.kt`).
- UI unit tests: `LibraryCategoryFilterTest.kt`.

### Task 4: Tracker Interfaces, Multi-Tracker Services & Conflict Handling
- Implement `DesktopTracker.kt` and `TrackerModels.kt`.
- Implement `DesktopTrackerManager.kt` supporting all 9+ trackers.
- Implement `OfflineTrackingQueue.kt` (`tracking-queue.json` atomic store, retry, exponential backoff).
- Implement `TrackingConflictResolver.kt` (detect discrepancy, apply strategy).
- Implement `TrackingDialog.kt` Compose UI for manga details.
- Unit tests for Tracker Manager, Offline Queue, and Conflict Resolver.

### Task 5: Desktop Shell Integration, Verification Script & Evidence
- Wire `DesktopDestination.History` to `HistoryScreen` in `DesktopShell.kt`.
- Wire `DesktopRuntime.kt` with `DesktopTrackerManager` and `OfflineTrackingQueue`.
- Create `scripts/verify-desktop-trackers.ps1`.
- Run verification, create evidence document `docs/superpowers/evidence/windows-history-categories-trackers.md`.
- Ensure clean Android build (`:app:testDebugUnitTest :app:assembleDebug`) and `spotlessCheck`.
- Commit on `feat/windows-history-categories-trackers`.
