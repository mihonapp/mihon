# Codebase Concerns

**Analysis Date:** 2026-02-05

## Tech Debt

**Legacy RxJava Usage:**
- **Issue:** Multiple files still use RxJava despite migration to coroutines
- **Files:** `[source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt]`
- **Impact:** API surface is bloated with deprecated methods, increasing maintenance burden
- **Fix approach:** Create migration plan to remove RxJava APIs, update extension sources to use coroutines-only APIs

**InMemoryPreferenceStore Incomplete Implementation:**
- **Issue:** `InMemoryPreferenceStore.kt` has TODO for unimplemented methods
- **Files:** `[core/common/src/main/kotlin/tachiyomi/core/common/preference/InMemoryPreferenceStore.kt]`
- **Impact:** In-memory preferences fall back to default values instead of working
- **Fix approach:** Implement missing methods or document intentional limitations

**Large File Complexity:**
- **Issue:** Several files exceed 800 lines, making them difficult to maintain
- **Files:**
  - `[app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt]` (1220 lines)
  - `[app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt]` (1130 lines)
  - `[app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt]` (988 lines)
- **Impact:** Reduced readability, harder to test, increased cognitive load
- **Fix approach:** Break into smaller, focused components following single responsibility principle

**Local Source Legacy Support:**
- **Issue:** Local source contains TODO for removing legacy support
- **Files:** `[source-local/src/androidMain/kotlin/tachiyomi/source/local/LocalSource.kt]`
- **Impact:** Technical debt from maintaining deprecated formats
- **Fix approach:** Schedule removal after sufficient migration period

## Known Bugs

**Dual Screen UI Inconsistencies:**
- **Issue:** Navigation chevrons not properly hidden in webtoon modes
- **Files:** `[app/src/main/java/eu/kanade/tachiyomi/ui/main/CompanionDashboardScreen.kt]`
- **Symptoms:** UI elements overlap in dual-screen mode
- **Trigger:** Occurs when switching between reader modes on foldable devices
- **Workaround:** Manual screen rotation or mode switching

**Migration Custom Intervals Disabled:**
- **Issue:** Custom library update intervals feature is disabled
- **Files:** `[app/src/main/java/eu/kanade/presentation/library/LibrarySettingsDialog.kt]`
- **Symptoms:** Users cannot set custom update intervals
- **Trigger:** When accessing library settings
- **Workaround:** None, feature is unavailable

## Security Considerations

**WebView Security:**
- **Risk:** WebView allows JavaScript and can access local files
- **Files:** `[app/src/main/java/eu/kanade/presentation/webview/WebViewScreenContent.kt]`
- **Current mitigation:** Debug checks disable some features in release builds
- **Recommendations:** Implement Content Security Policy, disable JavaScript by default, add sandboxing

**Extension Permissions:**
- **Risk:** Extensions have broad access to device capabilities
- **Files:** `[app/src/main/java/eu/kanade/tachiyomi/extension/installer/ShizukuInstaller.kt]`
- **Current mitigation:** Uses Shizuku for system-level access
- **Recommendations:** Implement permission granularities, runtime permission requests

## Performance Bottlenecks

**Image Loading Pipeline:**
- **Problem:** Synchronous image operations in main thread
- **Files:** `[core/common/src/main/kotlin/tachiyomi/core/common/util/system/ImageUtil.kt]`
- **Cause:** Image processing blocks UI thread
- **Improvement path:** Use coroutines for all image operations, implement background processing

**Large Screen Model Collections:**
- **Problem:** MangaScreenModel loads all chapters at once
- **Files:** `[app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt]`
- **Cause:** No pagination for chapter list
- **Improvement path:** Implement lazy loading with pagination for large chapter lists

## Fragile Areas

**WebView Bridge:**
- **Files:** `[app/src/main/java/eu/kanade/presentation/webview/WebViewScreenContent.kt]`
- **Why fragile:** Direct communication between WebView and Compose
- **Safe modification:** Update communication protocol to use interfaces/abstractions
- **Test coverage:** Limited automated testing for WebView interactions

**Extension Loading System:**
- **Files:** `[app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt]`
- **Why fragile:** Dynamic loading of external packages
- **Safe modification:** Add safety checks and graceful degradation
- **Test coverage:** Mock-based testing only, no real extension integration tests

## Scaling Limits

**Memory Usage:**
- **Current capacity:** Limited by in-memory preference store implementation
- **Limit:** Large manga libraries cause performance degradation
- **Scaling path:** Optimize data structures, implement lazy loading

**Database Query Performance:**
- **Current capacity:** Single-threaded SQLDelight operations
- **Limit:** Large chapter/manga tables slow down queries
- **Scaling path:** Implement database indexing, optimize queries, consider room migration

## Dependencies at Risk

**Moko Resources:**
- **Risk:** Known workaround for Moko Resources issue #337
- **Impact:** Internationalization may break on certain configurations
- **Files:** `[core/common/src/main/kotlin/tachiyomi/core/common/i18n/Localize.kt]`
- **Migration plan:** Monitor for Moko Resources updates, implement alternative if needed

## Missing Critical Features

**Custom Library Update Intervals:**
- **Problem:** Feature disabled due to incomplete implementation
- **Blocks:** User control over library refresh frequency
- **Files:** `[app/src/main/java/eu/kanade/presentation/library/LibrarySettingsDialog.kt]`

**Full In-Memory Preference Implementation:**
- **Problem:** Preference store missing critical functionality
- **Blocks:** Testing features that don't persist to disk
- **Files:** `[core/common/src/main/kotlin/tachiyomi/core/common/preference/InMemoryPreferenceStore.kt]`

## Test Coverage Gaps

**UI Testing:**
- **What's not tested:** Most Compose screens lack automated tests
- **Files:** Most files in `app/src/main/java/eu/kanade/presentation/`
- **Risk:** UI changes can break functionality without detection
- **Priority:** High - UI is user-facing and frequently modified

**Integration Testing:**
- **What's not tested:** End-to-end flows like download->read->track
- **Files:** Test files are limited to domain layer
- **Risk:** Integration issues between layers go undetected
- **Priority:** Medium - Critical for user experience

---

*Concerns audit: 2026-02-05*