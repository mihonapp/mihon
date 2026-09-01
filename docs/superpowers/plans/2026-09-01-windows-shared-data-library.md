# Windows Shared Data Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the second runnable Windows vertical slice: a persistent SQLDelight-backed library, fail-closed local manga-directory import, transactional Android Mihon backup import, and real Material 3 library/detail/chapter screens.

**Architecture:** Add a JVM-only `:desktop-library-data` module whose public API is rooted at `mihon.desktop.library`; do not convert or depend on the existing Android `:data`, `:domain`, or `:source-local` modules. The new module owns pure Kotlin library models and repository ports, a SQLDelight 2.3.2 database opened with `JdbcSqliteDriver`, bounded gzip/ProtoBuf backup decoding, deterministic transactional merging, and staged Windows filesystem import. `:desktop-app` owns lifecycle, CLI hooks, and Compose Desktop presentation, while a real Android-side contract test encodes existing backup DTOs and proves that the desktop decoder imports their semantics.

**Tech Stack:** Kotlin 2.4.10, Java 17 bytecode/runtime image, Gradle 9.7.1, SQLDelight 2.3.2 with `app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver`, SQLite 3.38 dialect, kotlinx.serialization 1.11.0 ProtoBuf/JSON, Okio 3.18.1, kotlinx.coroutines 1.11.0, Compose Multiplatform 1.12.0 Material 3, JUnit 6.1.3, Kotest assertions 6.2.4.

## Global Constraints

- Keep `:desktop-library-data` JVM-only and independent of Android APIs and the existing Android `:data`, `:domain`, and `:source-local` modules; those modules are not converted in this slice.
- Root every new module package and every public model/port at `mihon.desktop.library`; desktop presentation remains under `mihon.desktop.ui.library` and only consumes those public APIs.
- Compile production bytecode for Java 17 and keep the packaged desktop runtime on Java 17 even when Gradle itself runs on a newer JVM.
- Use SQLDelight `2.3.2`, `JdbcSqliteDriver`, foreign keys enabled on every connection, one writer connection, and deterministic query ordering.
- Keep every source file focused on one responsibility; split models, ports, codecs, validators, merge policy, filesystem scanning, staging, runtime ownership, and UI state instead of creating a single manager class.
- Treat Android backup support in this slice as import-only. Android-compatible backup export and Windows-to-Android round-trip are Plan 7 deliverables.
- Match the existing Android backup wire contract exactly, including gzip framing, kotlinx.serialization ProtoBuf defaults, enum ordinals, and every listed `@ProtoNumber`; do not invent a desktop-only wire format.
- Decode and validate before starting the database transaction. Apply manga, chapters, categories/links, history, tracking, sources, supported preferences, and the success report in one all-or-nothing transaction.
- Enforce `BackupLimits(maxCompressedBytes=268_435_456, maxExpandedBytes=1_073_741_824, maxManga=100_000, maxChapters=2_000_000, maxCategories=10_000, maxTracks=1_000_000, maxPreferences=100_000, maxStringChars=1_048_576, maxNestingDepth=64)` before permanent mutation.
- The supported app-preference whitelist is exactly `pref_display_mode_library` (String), `library_sorting_mode` (String), `pref_library_columns_portrait_key` (Int), `pref_library_columns_landscape_key` (Int), `default_category` (Int), `library_update_categories` (StringSet), and `library_update_categories_exclude` (StringSet). Category-valued preferences are remapped through backup category ID -> category name -> desktop category ID before persistence.
- Source preferences are accepted only when `SupportedPreferencePolicy.sourceKeys[sourceKey]` contains the exact preference key; the built-in Plan 2 source-key map is empty until a Windows extension declares keys in Plan 4.
- Reject preference keys beginning with the exact Mihon private prefix `__PRIVATE_` as `PRIVATE`, and keys beginning with `__APP_STATE_` as `APP_STATE`; report every remaining non-whitelisted key as `UNKNOWN`. Never persist skipped values or credentials. Source preferences are archived only as unsupported report metadata in Plan 2 because the built-in source-key map is empty; their values are not activated.
- Manga identity is `(sourceId, url)`, chapter identity is `(mangaId, url)`, manga category references use Android backup category `order`, category-valued app preferences use Android backup category `id`, and tracker identity is `(mangaId, trackerId)` where `trackerId` is backup `syncId`.
- Merge non-regressively and deterministically: OR read/bookmark/favorite flags; take maximum progress/version/timestamps; union categories; never replace nonblank text with blank text; and use incoming metadata only when its `lastModifiedAt` is newer, with existing data winning ties.
- Local import accepts one manga directory containing chapter directories and/or files ending case-insensitively in `.cbz`, `.zip`, `.rar`, `.cbr`, `.7z`, `.cb7`, `.tar`, `.cbt`, or `.epub`. This slice registers archive/chapter metadata; page enumeration and decoding remain Plan 3.
- Fail closed on symbolic links, Windows reparse points, path traversal, non-regular files, root escape after normalization, duplicate normalized paths, unreadable entries, unsupported root contents, or staging/promotion failure. Do not follow links.
- Support non-ASCII names, Windows long paths, and paths longer than 260 characters through `java.nio.file.Path` without manual truncation or lossy normalization.
- The main desktop Library destination must render data observed from the real database, open a real detail view, and show the real chapter list; previews and tests may use in-memory fakes, but production wiring must not use fixtures.
- Preserve `spotlessCheck`, Android `testDebugUnitTest`, `verifySqlDelightMigration`, `assembleDebug`, the existing packaged-runtime Java 17 assertion, and the existing `--smoke-test` foundation verifier.
- Make one focused commit at the end of every task only after its named tests pass.

---

## File Structure

### Build and database module

- `settings.gradle.kts` — registers `:desktop-library-data` without changing existing Android module boundaries.
- `gradle/libs.versions.toml` — exposes SQLDelight JDBC driver and Okio/JVM aliases already pinned by the catalog.
- `desktop-library-data/build.gradle.kts` — JVM 17, serialization, SQLDelight schema generation/migration verification, and test dependencies.
- `desktop-library-data/src/main/sqldelight/mihon/desktop/library/db/Library.sq` — schema plus focused selects/upserts for manga, chapters, categories/links, history, tracking, source metadata, supported app/source preference snapshots, local assets, and import reports.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/db/DesktopLibraryDatabaseFactory.kt` — opens/creates the JDBC SQLite file, enables foreign keys, and owns the driver lifecycle.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/db/SqlDelightLibraryRepository.kt` — maps generated queries to the pure repository port and runs transactions.

### Public models and ports

- `desktop-library-data/src/main/kotlin/mihon/desktop/library/model/LibraryModels.kt` — immutable `LibraryManga`, `MangaDetails`, `LibraryChapter`, `LibraryCategory`, and import-source models.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/model/ImportModels.kt` — structured import status, counts, skip reasons, warnings, and report models.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/repository/LibraryRepository.kt` — observable read port and lookup functions used by desktop presentation.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/repository/LibraryMutationPort.kt` — the narrow transactional mutation surface consumed by importers.

### Android backup compatibility

- `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/AndroidBackupDtos.kt` — import-only serializable DTOs with exact Android ProtoNumber fields.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/AndroidBackupCodec.kt` — gzip detection, compressed/expanded byte bounds, JSON signature rejection, and ProtoBuf decoding.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/BackupLimits.kt` — immutable hard limits and counting helpers.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/AndroidBackupValidator.kt` — duplicate, reference, string, count, and semantic validation.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/SupportedPreferencePolicy.kt` — exact whitelist/classification and safe value normalization.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/BackupMergePolicy.kt` — deterministic non-regressive field merges.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/AndroidBackupImporter.kt` — decode/validate/transaction orchestration and report creation.
- `app/src/test/java/eu/kanade/tachiyomi/data/backup/DesktopBackupImportContractTest.kt` — existing Android DTO and ProtoBuf encoder to desktop decoder contract, extended after Task 5 to import into the real desktop database and compare stable semantics.

### Local Windows import

- `desktop-library-data/src/main/kotlin/mihon/desktop/library/local/LocalImportScanner.kt` — no-follow scan, supported chapter classification, normalized-path and reparse checks.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/local/LocalImportStager.kt` — copies into a private staging directory, verifies the manifest, atomically promotes, and cleans rollback/orphan state.
- `desktop-library-data/src/main/kotlin/mihon/desktop/library/local/LocalMangaImporter.kt` — converts a validated/promoted local manga into one transactional database import/report.

### Desktop runtime, CLI, and Material 3 UI

- `desktop-app/src/main/kotlin/mihon/desktop/DesktopRuntime.kt` — owns the open library store/import services and parsed CLI command for the process lifetime.
- `desktop-app/src/main/kotlin/mihon/desktop/Main.kt` — executes headless smoke/import/list commands or launches Compose, and always closes runtime resources.
- `desktop-app/src/main/kotlin/mihon/desktop/cli/DesktopCommand.kt` — parses mutually exclusive `--import-backup`, `--import-local`, `--list-library-json`, and existing smoke arguments.
- `desktop-app/src/main/kotlin/mihon/desktop/cli/DesktopCommandRunner.kt` — stable JSON Lines output and exit codes for verifier hooks.
- `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryPresenter.kt` — converts repository flows and selection into UI state.
- `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryScreen.kt` — Material 3 searchable database-backed grid/list and empty/error/loading states.
- `desktop-app/src/main/kotlin/mihon/desktop/ui/library/MangaDetailScreen.kt` — selected manga metadata and database-backed chapter list.
- `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryImportActions.kt` — file/directory chooser actions and visible import result dialog.
- `desktop-app/src/main/kotlin/mihon/desktop/ui/DesktopShell.kt` — routes Library to the real screen while preserving other foundation destinations.
- `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt` — remembers/closes the presenter and provides import chooser callbacks.

### Verification

- `scripts/verify-desktop-library.ps1` — runs foundation checks, data/UI tests, packaged CLI backup/local imports, reopen listing, and artifact assertions.
- `.github/workflows/build.yml` — changes the Windows job to the extended verifier while preserving Android jobs.
- `docs/superpowers/evidence/windows-shared-data-library.md` — records exact commands, fixture provenance, test counts, packaged paths, and manual UI observations after execution.

---

### Task 1: Create the JVM Data Module and Complete SQLDelight Schema

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `desktop-library-data/build.gradle.kts`
- Create: `desktop-library-data/src/main/sqldelight/mihon/desktop/library/db/Library.sq`
- Create: `desktop-library-data/src/test/kotlin/mihon/desktop/library/db/LibrarySchemaTest.kt`

**Interfaces:**
- Consumes: version catalog values `mihonx.versions.java=17`, `libs.versions.sqldelight=2.3.2`, and the root Spotless convention.
- Produces: generated `mihon.desktop.library.db.DesktopLibraryDatabase`, `DesktopLibraryDatabase.Schema`, and generated `libraryQueries` methods used by Tasks 2, 5, and 6.

- [x] **Step 1: Write the failing module/schema test**

Create `LibrarySchemaTest.kt` with a temporary `JdbcSqliteDriver`, create the schema, query `sqlite_master`, and assert this exact table set:

```kotlin
package mihon.desktop.library.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainAll
import org.junit.jupiter.api.Test

class LibrarySchemaTest {
    @Test
    fun `schema creates every Plan 2 table`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.use {
            DesktopLibraryDatabase.Schema.create(driver)
            val names = driver.executeQuery(
                identifier = null,
                sql = "SELECT name FROM sqlite_master WHERE type='table'",
                mapper = { cursor ->
                    val result = mutableListOf<String>()
                    while (cursor.next().value) result += cursor.getString(0)!!
                    app.cash.sqldelight.db.QueryResult.Value(result)
                },
                parameters = 0,
            ).value
            names.shouldContainAll(
                "manga", "chapter", "category", "manga_category", "history", "tracking",
                "source_metadata", "preference_snapshot", "source_preference_snapshot",
                "local_manga_entry", "local_chapter_asset", "import_report", "import_report_item",
            )
        }
    }
}
```

- [x] **Step 2: Run the test to verify the module is absent**

Run: `./gradlew :desktop-library-data:test --tests mihon.desktop.library.db.LibrarySchemaTest`

Expected: FAIL because project `:desktop-library-data` is not present.

- [x] **Step 3: Register dependencies and configure the module**

Add `include(":desktop-library-data")` beside `include(":desktop-app")`. Add catalog alias `sqldelight-jdbcDriver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }`. Create `desktop-library-data/build.gradle.kts` with these exact behaviors:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(mihonx.plugins.spotless)
}

kotlin { jvmToolchain(mihonx.versions.java.get().toInt()) }

dependencies {
    implementation(libs.sqldelight.jdbcDriver)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okio)
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

sqldelight {
    databases {
        create("DesktopLibraryDatabase") {
            packageName.set("mihon.desktop.library.db")
            dialect(libs.sqldelight.sqliteDialect338)
            verifyMigrations.set(true)
        }
    }
}
```

- [x] **Step 4: Add the complete initial schema and deterministic queries**

Create `Library.sq`. Use `INTEGER AS Boolean` for flags, `TEXT` for JSON/list snapshots, foreign keys with explicit delete actions, and the following tables/constraints:

```sql
PRAGMA foreign_keys = ON;

CREATE TABLE manga (
  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  source_id INTEGER NOT NULL,
  url TEXT NOT NULL,
  title TEXT NOT NULL,
  artist TEXT,
  author TEXT,
  description TEXT,
  genre_json TEXT NOT NULL DEFAULT '[]',
  status INTEGER NOT NULL DEFAULT 0,
  thumbnail_url TEXT,
  favorite INTEGER AS Boolean NOT NULL DEFAULT 1,
  date_added INTEGER NOT NULL DEFAULT 0,
  viewer_flags INTEGER NOT NULL DEFAULT 0,
  chapter_flags INTEGER NOT NULL DEFAULT 0,
  update_strategy TEXT NOT NULL DEFAULT 'ALWAYS_UPDATE',
  last_modified_at INTEGER NOT NULL DEFAULT 0,
  favorite_modified_at INTEGER,
  excluded_scanlators_json TEXT NOT NULL DEFAULT '[]',
  version INTEGER NOT NULL DEFAULT 0,
  notes TEXT NOT NULL DEFAULT '',
  initialized INTEGER AS Boolean NOT NULL DEFAULT 0,
  memo_json TEXT NOT NULL DEFAULT '{}',
  UNIQUE(source_id, url)
);

CREATE TABLE chapter (
  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  manga_id INTEGER NOT NULL REFERENCES manga(id) ON DELETE CASCADE,
  url TEXT NOT NULL,
  name TEXT NOT NULL,
  scanlator TEXT,
  read INTEGER AS Boolean NOT NULL DEFAULT 0,
  bookmark INTEGER AS Boolean NOT NULL DEFAULT 0,
  last_page_read INTEGER NOT NULL DEFAULT 0,
  date_fetch INTEGER NOT NULL DEFAULT 0,
  date_upload INTEGER NOT NULL DEFAULT 0,
  chapter_number REAL NOT NULL DEFAULT 0,
  source_order INTEGER NOT NULL DEFAULT 0,
  last_modified_at INTEGER NOT NULL DEFAULT 0,
  version INTEGER NOT NULL DEFAULT 0,
  memo_json TEXT NOT NULL DEFAULT '{}',
  UNIQUE(manga_id, url)
);

CREATE TABLE category (
  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL COLLATE NOCASE UNIQUE,
  sort_order INTEGER NOT NULL DEFAULT 0,
  flags INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE manga_category (
  manga_id INTEGER NOT NULL REFERENCES manga(id) ON DELETE CASCADE,
  category_id INTEGER NOT NULL REFERENCES category(id) ON DELETE CASCADE,
  PRIMARY KEY(manga_id, category_id)
);

CREATE TABLE history (
  chapter_id INTEGER NOT NULL PRIMARY KEY REFERENCES chapter(id) ON DELETE CASCADE,
  last_read INTEGER NOT NULL,
  read_duration INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE tracking (
  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  manga_id INTEGER NOT NULL REFERENCES manga(id) ON DELETE CASCADE,
  tracker_id INTEGER NOT NULL,
  remote_id INTEGER NOT NULL,
  library_id INTEGER NOT NULL DEFAULT 0,
  title TEXT NOT NULL DEFAULT '',
  last_chapter_read REAL NOT NULL DEFAULT 0,
  total_chapters INTEGER NOT NULL DEFAULT 0,
  score REAL NOT NULL DEFAULT 0,
  status INTEGER NOT NULL DEFAULT 0,
  started_reading_date INTEGER NOT NULL DEFAULT 0,
  finished_reading_date INTEGER NOT NULL DEFAULT 0,
  private INTEGER AS Boolean NOT NULL DEFAULT 0,
  tracking_url TEXT NOT NULL DEFAULT '',
  UNIQUE(manga_id, tracker_id)
);

CREATE TABLE source_metadata (
  source_id INTEGER NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  imported_at INTEGER NOT NULL
);

CREATE TABLE preference_snapshot (
  key TEXT NOT NULL PRIMARY KEY,
  value_type TEXT NOT NULL,
  value_json TEXT NOT NULL,
  imported_at INTEGER NOT NULL
);

CREATE TABLE source_preference_snapshot (
  source_key TEXT NOT NULL,
  key TEXT NOT NULL,
  value_type TEXT NOT NULL,
  value_json TEXT NOT NULL,
  imported_at INTEGER NOT NULL,
  PRIMARY KEY(source_key, key)
);

CREATE TABLE local_manga_entry (
  manga_id INTEGER NOT NULL PRIMARY KEY REFERENCES manga(id) ON DELETE CASCADE,
  storage_path TEXT NOT NULL UNIQUE,
  manifest_sha256 TEXT NOT NULL,
  imported_at INTEGER NOT NULL
);

CREATE TABLE local_chapter_asset (
  chapter_id INTEGER NOT NULL PRIMARY KEY REFERENCES chapter(id) ON DELETE CASCADE,
  relative_path TEXT NOT NULL,
  asset_kind TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  modified_at INTEGER NOT NULL,
  UNIQUE(relative_path)
);

CREATE TABLE import_report (
  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  import_type TEXT NOT NULL,
  source_path TEXT NOT NULL,
  status TEXT NOT NULL,
  started_at INTEGER NOT NULL,
  finished_at INTEGER NOT NULL,
  manga_inserted INTEGER NOT NULL DEFAULT 0,
  manga_merged INTEGER NOT NULL DEFAULT 0,
  chapters_inserted INTEGER NOT NULL DEFAULT 0,
  chapters_merged INTEGER NOT NULL DEFAULT 0,
  categories_linked INTEGER NOT NULL DEFAULT 0,
  preferences_imported INTEGER NOT NULL DEFAULT 0,
  preferences_skipped INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE import_report_item (
  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  report_id INTEGER NOT NULL REFERENCES import_report(id) ON DELETE CASCADE,
  item_type TEXT NOT NULL,
  item_key TEXT NOT NULL,
  outcome TEXT NOT NULL,
  reason TEXT,
  message TEXT NOT NULL
);

CREATE INDEX chapter_manga_order ON chapter(manga_id, source_order DESC, chapter_number DESC, id DESC);
CREATE INDEX history_last_read ON history(last_read DESC);
CREATE INDEX manga_title ON manga(title COLLATE NOCASE);

selectLibrary:
SELECT manga.*, COUNT(chapter.id) AS chapter_count,
       COALESCE(SUM(CASE WHEN chapter.read = 0 THEN 1 ELSE 0 END), 0) AS unread_count
FROM manga LEFT JOIN chapter ON chapter.manga_id = manga.id
WHERE manga.favorite = 1
GROUP BY manga.id
ORDER BY manga.title COLLATE NOCASE, manga.id;

selectMangaById:
SELECT * FROM manga WHERE id = ?;

selectMangaByIdentity:
SELECT * FROM manga WHERE source_id = ? AND url = ?;

selectChaptersForManga:
SELECT * FROM chapter WHERE manga_id = ?
ORDER BY source_order DESC, chapter_number DESC, name COLLATE NOCASE, id DESC;

selectChapterByIdentity:
SELECT * FROM chapter WHERE manga_id = ? AND url = ?;

selectCategoriesForManga:
SELECT category.* FROM category
JOIN manga_category ON manga_category.category_id = category.id
WHERE manga_category.manga_id = ?
ORDER BY category.sort_order, category.name COLLATE NOCASE;

selectLatestImportReport:
SELECT * FROM import_report ORDER BY id DESC LIMIT 1;
```

Add these named mutations. Continue each shown manga/chapter value list with every column in the same schema order; the corresponding update assigns every mutable column and ends in `WHERE id = :id`. The implementation must contain the complete column lists below, not wildcard/generated reflection. No `INSERT OR REPLACE` is permitted because it can break foreign keys.

```sql
insertManga:
INSERT INTO manga(
  source_id, url, title, artist, author, description, genre_json, status, thumbnail_url,
  favorite, date_added, viewer_flags, chapter_flags, update_strategy, last_modified_at,
  favorite_modified_at, excluded_scanlators_json, version, notes, initialized, memo_json
) VALUES (
  :source_id, :url, :title, :artist, :author, :description, :genre_json, :status, :thumbnail_url,
  :favorite, :date_added, :viewer_flags, :chapter_flags, :update_strategy, :last_modified_at,
  :favorite_modified_at, :excluded_scanlators_json, :version, :notes, :initialized, :memo_json
);

updateManga:
UPDATE manga SET source_id=:source_id, url=:url, title=:title, artist=:artist, author=:author,
  description=:description, genre_json=:genre_json, status=:status, thumbnail_url=:thumbnail_url,
  favorite=:favorite, date_added=:date_added, viewer_flags=:viewer_flags, chapter_flags=:chapter_flags,
  update_strategy=:update_strategy, last_modified_at=:last_modified_at,
  favorite_modified_at=:favorite_modified_at, excluded_scanlators_json=:excluded_scanlators_json,
  version=:version, notes=:notes, initialized=:initialized, memo_json=:memo_json
WHERE id=:id;

insertChapter:
INSERT INTO chapter(
  manga_id, url, name, scanlator, read, bookmark, last_page_read, date_fetch, date_upload,
  chapter_number, source_order, last_modified_at, version, memo_json
) VALUES (
  :manga_id, :url, :name, :scanlator, :read, :bookmark, :last_page_read, :date_fetch, :date_upload,
  :chapter_number, :source_order, :last_modified_at, :version, :memo_json
);

updateChapter:
UPDATE chapter SET manga_id=:manga_id, url=:url, name=:name, scanlator=:scanlator, read=:read,
  bookmark=:bookmark, last_page_read=:last_page_read, date_fetch=:date_fetch, date_upload=:date_upload,
  chapter_number=:chapter_number, source_order=:source_order, last_modified_at=:last_modified_at,
  version=:version, memo_json=:memo_json
WHERE id=:id;

insertCategory:
INSERT INTO category(name, sort_order, flags) VALUES (:name, :sort_order, :flags)
ON CONFLICT(name) DO UPDATE SET
  sort_order=MAX(category.sort_order, excluded.sort_order),
  flags=(category.flags | excluded.flags);

selectCategoryByName:
SELECT * FROM category WHERE name = ? COLLATE NOCASE;

linkMangaCategory:
INSERT INTO manga_category(manga_id, category_id) VALUES (?, ?)
ON CONFLICT(manga_id, category_id) DO NOTHING;

upsertHistory:
INSERT INTO history(chapter_id, last_read, read_duration) VALUES (:chapter_id, :last_read, :read_duration)
ON CONFLICT(chapter_id) DO UPDATE SET
  last_read=MAX(history.last_read, excluded.last_read),
  read_duration=MAX(history.read_duration, excluded.read_duration);

selectHistoryByChapter:
SELECT * FROM history WHERE chapter_id = ?;

insertTracking:
INSERT INTO tracking(
  manga_id, tracker_id, remote_id, library_id, title, last_chapter_read, total_chapters,
  score, status, started_reading_date, finished_reading_date, private, tracking_url
) VALUES (
  :manga_id, :tracker_id, :remote_id, :library_id, :title, :last_chapter_read, :total_chapters,
  :score, :status, :started_reading_date, :finished_reading_date, :private, :tracking_url
);

selectTrackingByIdentity:
SELECT * FROM tracking WHERE manga_id=? AND tracker_id=? AND remote_id=?;

updateTracking:
UPDATE tracking SET library_id=:library_id, title=:title, last_chapter_read=:last_chapter_read,
  total_chapters=:total_chapters, score=:score, status=:status,
  started_reading_date=:started_reading_date, finished_reading_date=:finished_reading_date,
  private=:private, tracking_url=:tracking_url
WHERE id=:id;

upsertSourceMetadata:
INSERT INTO source_metadata(source_id, name, imported_at) VALUES (?, ?, ?)
ON CONFLICT(source_id) DO UPDATE SET name=excluded.name, imported_at=excluded.imported_at;

upsertPreferenceSnapshot:
INSERT INTO preference_snapshot(key, value_type, value_json, imported_at) VALUES (?, ?, ?, ?)
ON CONFLICT(key) DO UPDATE SET value_type=excluded.value_type,
  value_json=excluded.value_json, imported_at=excluded.imported_at;

upsertSourcePreferenceSnapshot:
INSERT INTO source_preference_snapshot(source_key, key, value_type, value_json, imported_at)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT(source_key, key) DO UPDATE SET value_type=excluded.value_type,
  value_json=excluded.value_json, imported_at=excluded.imported_at;

insertLocalMangaEntry:
INSERT INTO local_manga_entry(manga_id, storage_path, manifest_sha256, imported_at) VALUES (?, ?, ?, ?)
ON CONFLICT(manga_id) DO UPDATE SET storage_path=excluded.storage_path,
  manifest_sha256=excluded.manifest_sha256, imported_at=excluded.imported_at;

selectLocalMangaByManifest:
SELECT * FROM local_manga_entry WHERE manifest_sha256 = ?;

insertLocalChapterAsset:
INSERT INTO local_chapter_asset(chapter_id, relative_path, asset_kind, size_bytes, modified_at)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT(chapter_id) DO UPDATE SET relative_path=excluded.relative_path,
  asset_kind=excluded.asset_kind, size_bytes=excluded.size_bytes, modified_at=excluded.modified_at;

insertImportReport:
INSERT INTO import_report(
  import_type, source_path, status, started_at, finished_at, manga_inserted, manga_merged,
  chapters_inserted, chapters_merged, categories_linked, preferences_imported, preferences_skipped
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

insertImportReportItem:
INSERT INTO import_report_item(report_id, item_type, item_key, outcome, reason, message)
VALUES (?, ?, ?, ?, ?, ?);

lastInsertRowId:
SELECT last_insert_rowid();
```

- [x] **Step 5: Run schema generation and the focused test**

Run: `./gradlew :desktop-library-data:generateSqlDelightInterface :desktop-library-data:test --tests mihon.desktop.library.db.LibrarySchemaTest`

Expected: PASS; generated schema contains all 13 Plan 2 tables.

- [x] **Step 6: Commit the module boundary**

```bash
git add settings.gradle.kts gradle/libs.versions.toml desktop-library-data
git commit -m "feat: add desktop library database schema"
```

### Task 2: Add Pure Models, Repository Ports, Persistence, and Lifecycle

**Files:**
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/model/LibraryModels.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/model/ImportModels.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/repository/LibraryRepository.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/repository/LibraryMutationPort.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/db/DesktopLibraryDatabaseFactory.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/db/SqlDelightLibraryRepository.kt`
- Test: `desktop-library-data/src/test/kotlin/mihon/desktop/library/db/SqlDelightLibraryRepositoryTest.kt`

**Interfaces:**
- Consumes: `DesktopLibraryDatabase`, `libraryQueries`, and Java `Path` from Task 1.
- Produces: `LibraryRepository`, `LibraryMutationPort`, `SqlDelightLibraryRepository`, and `DesktopLibraryDatabaseFactory.open(Path): SqlDelightLibraryRepository` used by all later tasks.

- [x] **Step 1: Write persistence, foreign-key, ordering, and rollback tests**

Add tests that open a temporary file, insert a manga and two chapters through `LibraryMutationPort`, close and reopen, and assert the same title/unread counts and deterministic chapter order. Add a foreign-key test that inserting a chapter for manga ID `999` throws. Add `repository.transaction { insert manga; error("forced rollback") }`, reopen, and assert the manga is absent.

```kotlin
@Test
fun `committed library survives reopen and rollback never leaks rows`() {
    val file = tempDir.resolve("library.db")
    DesktopLibraryDatabaseFactory.open(file).use { first ->
        first.transaction {
            val id = insertManga(MangaRecord(sourceId = 1, url = "/a", title = "漫画 A"))
            insertChapter(ChapterRecord(mangaId = id, url = "/2", name = "第 2 话", sourceOrder = 2))
            insertChapter(ChapterRecord(mangaId = id, url = "/1", name = "第 1 话", sourceOrder = 1, read = true))
        }
        shouldThrow<IllegalStateException> {
            first.transaction {
                insertManga(MangaRecord(sourceId = 1, url = "/rolled-back", title = "不可见"))
                error("forced rollback")
            }
        }
    }
    DesktopLibraryDatabaseFactory.open(file).use { reopened ->
        reopened.librarySnapshot().single().run {
            title shouldBe "漫画 A"
            chapterCount shouldBe 2
            unreadCount shouldBe 1
        }
        reopened.chapterSnapshot(reopened.librarySnapshot().single().id).map { it.name } shouldBe
            listOf("第 2 话", "第 1 话")
        reopened.findManga(1, "/rolled-back") shouldBe null
    }
}
```

- [x] **Step 2: Run the focused test to verify the ports are missing**

Run: `./gradlew :desktop-library-data:test --tests mihon.desktop.library.db.SqlDelightLibraryRepositoryTest`

Expected: FAIL on unresolved `DesktopLibraryDatabaseFactory`, `MangaRecord`, and repository methods.

- [x] **Step 3: Define immutable public models and ports**

Define these exact public signatures; model fields that mirror backup fields remain lossless, while UI projection stays compact:

```kotlin
package mihon.desktop.library.repository

interface LibraryRepository {
    fun observeLibrary(): kotlinx.coroutines.flow.Flow<List<mihon.desktop.library.model.LibraryManga>>
    fun observeManga(id: Long): kotlinx.coroutines.flow.Flow<mihon.desktop.library.model.MangaDetails?>
    fun observeChapters(mangaId: Long): kotlinx.coroutines.flow.Flow<List<mihon.desktop.library.model.LibraryChapter>>
    fun librarySnapshot(): List<mihon.desktop.library.model.LibraryManga>
    fun mangaSnapshot(id: Long): mihon.desktop.library.model.MangaDetails?
    fun chapterSnapshot(mangaId: Long): List<mihon.desktop.library.model.LibraryChapter>
    fun latestImportReport(): mihon.desktop.library.model.ImportReport?
}

interface LibraryMutationPort {
    fun <T> transaction(block: LibraryMutationPort.() -> T): T
    fun findManga(sourceId: Long, url: String): MangaRecord?
    fun insertManga(value: MangaRecord): Long
    fun updateManga(value: MangaRecord)
    fun findChapter(mangaId: Long, url: String): ChapterRecord?
    fun insertChapter(value: ChapterRecord): Long
    fun updateChapter(value: ChapterRecord)
    fun upsertCategory(value: CategoryRecord): Long
    fun linkCategory(mangaId: Long, categoryId: Long)
    fun upsertHistory(value: HistoryRecord)
    fun findTracking(mangaId: Long, trackerId: Long): TrackingRecord?
    fun insertTracking(value: TrackingRecord)
    fun updateTracking(value: TrackingRecord)
    fun upsertSource(value: SourceRecord)
    fun upsertPreference(value: PreferenceSnapshotRecord)
    fun upsertSourcePreference(value: SourcePreferenceSnapshotRecord)
    fun insertLocalManga(value: LocalMangaRecord)
    fun insertLocalChapter(value: LocalChapterRecord)
    fun insertReport(value: ImportReportRecord): Long
    fun insertReportItem(reportId: Long, value: ImportReportItemRecord)
}
```

In `LibraryModels.kt`, define `LibraryManga(id, sourceId, url, title, thumbnailUrl, chapterCount, unreadCount)`, `MangaDetails` with every `manga` column plus `categories`, `LibraryChapter` with every `chapter` column, and the internal mutation records named above. In `ImportModels.kt`, define `ImportType { ANDROID_BACKUP, LOCAL_DIRECTORY }`, `ImportStatus { SUCCEEDED, REJECTED, FAILED }`, `PreferenceSkipReason { PRIVATE, APP_STATE, UNKNOWN, UNSUPPORTED_TYPE }`, `ImportCounts`, `ImportReportItem`, and `ImportReport`.

- [x] **Step 4: Implement the JDBC factory and SQLDelight repository**

`DesktopLibraryDatabaseFactory.open(path)` must create the parent directory, open `jdbc:sqlite:${path.toAbsolutePath()}`, execute `PRAGMA foreign_keys=ON`, call `Schema.create` only when `PRAGMA user_version` is zero, then set `PRAGMA user_version=${DesktopLibraryDatabase.Schema.version}`, and return one `SqlDelightLibraryRepository` that owns/closes the driver. Implement query-to-model mappings in private functions, SQLDelight `asFlow().mapToList(Dispatchers.IO)`, `transactionWithResult`, and `lastInsertRowId` retrieval through the named SQLDelight query.

```kotlin
object DesktopLibraryDatabaseFactory {
    fun open(path: Path): SqlDelightLibraryRepository {
        Files.createDirectories(path.toAbsolutePath().parent)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${path.toAbsolutePath()}")
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        val version = driver.executeQuery(null, "PRAGMA user_version", { c ->
            c.next().value
            app.cash.sqldelight.db.QueryResult.Value(c.getLong(0) ?: 0L)
        }, 0).value
        if (version == 0L) {
            DesktopLibraryDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA user_version=${DesktopLibraryDatabase.Schema.version}", 0)
        }
        return SqlDelightLibraryRepository(driver, DesktopLibraryDatabase(driver))
    }
}
```

- [x] **Step 5: Run persistence tests and all module tests**

Run: `./gradlew :desktop-library-data:test`

Expected: PASS, including reopen persistence, foreign-key rejection, deterministic order, and forced transaction rollback.

- [x] **Step 6: Commit the repository contract**

```bash
git add desktop-library-data/src/main
git add desktop-library-data/src/test/kotlin/mihon/desktop/library/db
git commit -m "feat: persist desktop library data"
```

### Task 3: Implement the Exact Bounded Android Backup Codec

**Files:**
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/AndroidBackupDtos.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/BackupLimits.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/AndroidBackupCodec.kt`
- Test: `desktop-library-data/src/test/kotlin/mihon/desktop/library/backup/AndroidBackupCodecTest.kt`

**Interfaces:**
- Consumes: kotlinx.serialization `ProtoBuf`, Okio gzip, and the Global Constraints limits.
- Produces: `AndroidBackupCodec.decode(path: Path, limits: BackupLimits = BackupLimits.DEFAULT): AndroidBackup` and the import-only DTO graph consumed by Tasks 4 and 5.

- [x] **Step 1: Write corrupt, raw/gzip, JSON-signature, and expansion-limit tests**

Cover: valid raw ProtoBuf; the same bytes gzip-compressed; truncated gzip; random bytes; leading `{}`, `{"`, and `{\n`; compressed input one byte above `maxCompressedBytes`; and a gzip bomb that expands one byte above `maxExpandedBytes`. Assert stable exceptions `BackupDecodeException.Kind.COMPRESSED_LIMIT`, `EXPANDED_LIMIT`, `LEGACY_JSON`, `CORRUPT_GZIP`, or `INVALID_PROTOBUF`, never `OutOfMemoryError`.

- [x] **Step 2: Run the codec test to verify it fails**

Run: `./gradlew :desktop-library-data:test --tests mihon.desktop.library.backup.AndroidBackupCodecTest`

Expected: FAIL because `AndroidBackupCodec` and the DTOs do not exist.

- [x] **Step 3: Define the exact wire DTOs**

Use `@Serializable` and these exact field numbers/names/defaults; preserve deprecated fields because old/current Android backups can emit them:

```kotlin
@Serializable
data class AndroidBackup(
    @ProtoNumber(1) val backupManga: List<AndroidBackupManga>,
    @ProtoNumber(2) val backupCategories: List<AndroidBackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<AndroidBackupSource> = emptyList(),
    @ProtoNumber(104) val backupPreferences: List<AndroidBackupPreference> = emptyList(),
    @ProtoNumber(105) val backupSourcePreferences: List<AndroidBackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) val backupExtensionStores: List<AndroidBackupExtensionStore> = emptyList(),
)

@Serializable
data class AndroidBackupManga(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val artist: String? = null,
    @ProtoNumber(5) val author: String? = null,
    @ProtoNumber(6) val description: String? = null,
    @ProtoNumber(7) val genre: List<String> = emptyList(),
    @ProtoNumber(8) val status: Int = 0,
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(13) val dateAdded: Long = 0,
    @ProtoNumber(14) val viewer: Int = 0,
    @ProtoNumber(16) val chapters: List<AndroidBackupChapter> = emptyList(),
    @ProtoNumber(17) val categories: List<Long> = emptyList(),
    @ProtoNumber(18) val tracking: List<AndroidBackupTracking> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
    @ProtoNumber(101) val chapterFlags: Int = 0,
    @ProtoNumber(103) val viewerFlags: Int? = null,
    @ProtoNumber(104) val history: List<AndroidBackupHistory> = emptyList(),
    @ProtoNumber(105) val updateStrategy: AndroidUpdateStrategy = AndroidUpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(106) val lastModifiedAt: Long = 0,
    @ProtoNumber(107) val favoriteModifiedAt: Long? = null,
    @ProtoNumber(108) val excludedScanlators: List<String> = emptyList(),
    @ProtoNumber(109) val version: Long = 0,
    @ProtoNumber(110) val notes: String = "",
    @ProtoNumber(111) val initialized: Boolean = false,
    @ProtoNumber(112) val memo: ByteArray = byteArrayOf(123, 125),
)

@Serializable enum class AndroidUpdateStrategy { ALWAYS_UPDATE, ONLY_FETCH_ONCE }

@Serializable
data class AndroidBackupChapter(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val scanlator: String? = null,
    @ProtoNumber(4) val read: Boolean = false,
    @ProtoNumber(5) val bookmark: Boolean = false,
    @ProtoNumber(6) val lastPageRead: Long = 0,
    @ProtoNumber(7) val dateFetch: Long = 0,
    @ProtoNumber(8) val dateUpload: Long = 0,
    @ProtoNumber(9) val chapterNumber: Float = 0F,
    @ProtoNumber(10) val sourceOrder: Long = 0,
    @ProtoNumber(11) val lastModifiedAt: Long = 0,
    @ProtoNumber(12) val version: Long = 0,
    @ProtoNumber(13) val memo: ByteArray = byteArrayOf(123, 125),
)
```

Add `AndroidBackupCategory(1 name, 2 order, 3 id, 100 flags)`, `AndroidBackupHistory(1 url, 2 lastRead, 3 readDuration)`, `AndroidBackupSource(1 name, 2 sourceId)`, and tracking fields exactly `1 syncId`, `2 libraryId`, `3 mediaIdInt`, `4 trackingUrl`, `5 title`, `6 lastChapterRead`, `7 totalChapters`, `8 score`, `9 status`, `10 startedReadingDate`, `11 finishedReadingDate`, `12 private`, `100 mediaId`. Add preference wrapper fields exactly `AndroidBackupPreference(1 key, 2 value)` and `AndroidBackupSourcePreferences(1 sourceKey, 2 prefs)`. Mirror the existing sealed `PreferenceValue` subclasses (`Int`, `Long`, `Float`, `String`, `Boolean`, `StringSet`) with the same serial class names by applying `@SerialName` equal to the Android fully qualified serializer names. Add extension-store fields `1 indexUrl`, `2 name`, `3 badgeLabel`, `4 contactWebsite`, `5 signingKey`, `6 contactDiscord`, `7 isLegacy`, `8 extensionListUrl`; decoding stores but Plan 2 reports these entries as unknown rather than installing extensions.

- [x] **Step 4: Implement streaming bounded gzip/ProtoBuf decoding**

Define `BackupLimits` exactly as the Global Constraints default. `AndroidBackupCodec` must check `Files.size` before opening, read through an Okio `ForwardingSource` that increments expanded bytes and throws immediately above the bound, detect gzip from unsigned bytes `0x1f,0x8b`, reject the three JSON prefixes before ProtoBuf, and wrap serialization/EOF/gzip errors in typed `BackupDecodeException` without swallowing limit exceptions.

```kotlin
class AndroidBackupCodec(private val protoBuf: ProtoBuf = ProtoBuf) {
    fun decode(path: Path, limits: BackupLimits = BackupLimits.DEFAULT): AndroidBackup {
        val compressed = Files.size(path)
        if (compressed > limits.maxCompressedBytes) throw BackupDecodeException.compressed(compressed)
        Files.newInputStream(path).source().buffer().use { source ->
            val magic = source.peek().run { require(2); byteArrayOf(readByte(), readByte()) }
            val payload = if (magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte()) source.gzip() else source
            val bounded = ExpandedLimitSource(payload, limits.maxExpandedBytes).buffer()
            val bytes = bounded.use { it.readByteArray() }
            rejectLegacyJson(bytes)
            return try {
                protoBuf.decodeFromByteArray(AndroidBackup.serializer(), bytes)
            } catch (error: SerializationException) {
                throw BackupDecodeException.invalidProto(error)
            }
        }
    }
}
```

- [x] **Step 5: Run codec tests and format checks**

Run: `./gradlew :desktop-library-data:test --tests mihon.desktop.library.backup.AndroidBackupCodecTest spotlessCheck`

Expected: PASS for raw, gzip, corrupt, JSON, compressed-limit, and expanded-limit cases.

- [x] **Step 6: Commit the wire codec**

```bash
git add desktop-library-data/src/main/kotlin/mihon/desktop/library/backup desktop-library-data/src/test/kotlin/mihon/desktop/library/backup/AndroidBackupCodecTest.kt
git commit -m "feat: decode bounded Android backups on desktop"
```

### Task 4: Prove Wire Compatibility with the Existing Android Encoder

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/test/java/eu/kanade/tachiyomi/data/backup/DesktopBackupImportContractTest.kt`

**Interfaces:**
- Consumes: the existing Android classes `Backup`, `BackupManga`, `BackupChapter`, `BackupCategory`, `BackupHistory`, `BackupTracking`, `BackupPreference`, `BackupSourcePreferences`, and `ProtoBuf.encodeToByteArray`; consumes Task 3 `AndroidBackupCodec`.
- Produces: a real cross-module wire compatibility gate whose input bytes are created only by the existing Android DTO serializer, never by the new desktop DTOs. Task 5 extends the same test through the real importer/database.

- [x] **Step 1: Add the failing Android-to-desktop semantic contract test**

Add `testImplementation(project(":desktop-library-data"))` to `app/build.gradle.kts`. In the test, construct existing Android backup DTOs with Unicode and non-default fields, encode with the same `ProtoBuf` singleton used by `BackupCreator`, gzip with Okio, write `.tachibk`, decode using `AndroidBackupCodec`, and compare a stable semantic projection:

```kotlin
@Test
fun `current Android encoder produces bytes understood by desktop`() {
    val android = Backup(
        backupManga = listOf(
            BackupManga(source = 42, url = "/作品/一", title = "作品 一").apply {
                author = "作者"
                favorite = true
                lastModifiedAt = 900
                version = 7
                categories = listOf(2) // BackupManga stores BackupCategory.order, not id.
                chapters = listOf(
                    BackupChapter("/章节/一", "第 1 话", read = true, bookmark = true, lastPageRead = 12, version = 3),
                )
                history = listOf(BackupHistory("/章节/一", lastRead = 800, readDuration = 1234))
                tracking = listOf(BackupTracking(syncId = 1, libraryId = 2, mediaId = 3, title = "远程作品", lastChapterRead = 1F))
            },
        ),
        backupCategories = listOf(BackupCategory("收藏", order = 2, id = 10, flags = 4)),
        backupSources = listOf(BackupSource("真实来源", 42)),
        backupPreferences = listOf(
            BackupPreference("pref_display_mode_library", StringPreferenceValue("COMPACT_GRID")),
            BackupPreference("__PRIVATE_auth_token", StringPreferenceValue("must-not-appear-in-report")),
            BackupPreference("__APP_STATE_last_version_code", IntPreferenceValue(1)),
            BackupPreference("unrecognized_plan2_key", StringPreferenceValue("unknown")),
        ),
    )
    val path = tempDir.resolve("android-encoder.tachibk")
    path.sink().gzip().buffer().use { sink ->
        sink.write(ProtoBuf.encodeToByteArray(Backup.serializer(), android))
    }

    val desktop = AndroidBackupCodec().decode(path)
    desktop.toSemanticProjection() shouldBe android.toSemanticProjection()
}
```

Define both projections inside the test, using primitives only. Include source/url/title/author/favorite/version, chapter URL/name/read/bookmark/page/version, category ID/name/order/flags, the manga's category-order references, history URL/time/duration, and tracking IDs/progress/title. This comparison must not compare DTO class equality.

- [x] **Step 2: Run the cross-contract test to expose any field mismatch**

Run: `./gradlew :app:testDebugUnitTest --tests eu.kanade.tachiyomi.data.backup.DesktopBackupImportContractTest`

Expected before correction: FAIL if serial names, enum encoding, defaults, or any ProtoNumber differs from the existing Android DTOs.

- [x] **Step 3: Correct only the desktop wire mirror until semantics match**

Compare every desktop annotation against `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/*.kt`. Keep the existing Android DTOs and encoder untouched. Add a second contract case for `mediaIdInt` fallback, `viewer` fallback when `viewer_flags` is absent, all six preference value subclasses, and empty/default optional fields.

- [x] **Step 4: Run both contract and desktop codec suites**

Run: `./gradlew :app:testDebugUnitTest --tests eu.kanade.tachiyomi.data.backup.DesktopBackupImportContractTest :desktop-library-data:test --tests mihon.desktop.library.backup.AndroidBackupCodecTest`

Expected: PASS; test output demonstrates bytes originate from the existing Android serializer and desktop semantics match.

- [x] **Step 5: Commit the compatibility gate**

```bash
git add app/build.gradle.kts app/src/test/java/eu/kanade/tachiyomi/data/backup/DesktopBackupImportContractTest.kt desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/AndroidBackupDtos.kt
git commit -m "test: lock Android desktop backup compatibility"
```

### Task 5: Validate and Transactionally Merge Android Backups

**Files:**
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/AndroidBackupValidator.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/SupportedPreferencePolicy.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/BackupMergePolicy.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/backup/AndroidBackupImporter.kt`
- Test: `desktop-library-data/src/test/kotlin/mihon/desktop/library/backup/AndroidBackupValidatorTest.kt`
- Test: `desktop-library-data/src/test/kotlin/mihon/desktop/library/backup/AndroidBackupImporterTest.kt`
- Modify: `app/src/test/java/eu/kanade/tachiyomi/data/backup/DesktopBackupImportContractTest.kt`

**Interfaces:**
- Consumes: Task 2 repository/mutation records and Task 3 codec/DTOs.
- Produces: `AndroidBackupImporter.import(path: Path, nowMillis: Long): ImportReport`, exact preference classification, deterministic merge behavior used by CLI/UI, and an Android-encoder-to-real-desktop-database compatibility gate.

- [x] **Step 1: Write validator tests for limits, duplicates, references, and JSON memo bounds**

Create table-driven cases that reject: manga count over limit; total chapter/track/preference counts over limits; one string over `maxStringChars`; duplicate `(source,url)` manga; duplicate chapter URL within one manga; duplicate category IDs, names, or orders; manga category order absent from backup categories; history URL absent from that manga's chapters; duplicate `syncId` tracking rows within one manga; malformed `memo` bytes that are not a JSON object; and nesting deeper than 64. Assert `BackupValidationException` includes a stable path such as `backupManga[0].history[0].url` and does not mutate a repository.

- [x] **Step 2: Write importer tests for one transaction and non-regressive merge rules**

Seed an existing manga/chapter/history/tracker with later progress but older metadata, import an overlapping backup, and assert:

```kotlin
merged.favorite shouldBe true
merged.title shouldBe "newer incoming title"
mergedChapter.read shouldBe true
mergedChapter.bookmark shouldBe true
mergedChapter.lastPageRead shouldBe 50
mergedHistory.lastRead shouldBe 2_000
mergedTracking.lastChapterRead shouldBe 12.0
mergedCategories.map { it.name }.toSet() shouldBe setOf("Existing", "Imported")
```

Add the reverse case where incoming `lastModifiedAt` is older or equal and assert existing nonblank metadata wins. Inject an `ImportCheckpoint` that throws after tracking but before report insertion; assert manga, chapters, links, history, tracking, preferences, source metadata, and report tables all remain byte-for-byte unchanged.

- [x] **Step 3: Run validator/importer tests to verify they fail**

Run: `./gradlew :desktop-library-data:test --tests 'mihon.desktop.library.backup.AndroidBackup*Test'`

Expected: FAIL on missing validator, policy, merge policy, and importer.

- [x] **Step 4: Implement exact validation and preference policy**

Implement `AndroidBackupValidator.validate(backup, limits): ValidatedAndroidBackup` as a single pass with cumulative counters and canonical identity sets. Parse `memo` as JSON only after byte/string bounds; require a JSON object. Define:

```kotlin
data class SupportedPreferencePolicy(
    val appKeys: Set<String> = DEFAULT_APP_KEYS,
    val sourceKeys: Map<String, Set<String>> = emptyMap(),
) {
    fun classifyApp(key: String, value: AndroidPreferenceValue): PreferenceDecision
    fun classifySource(sourceKey: String, key: String, value: AndroidPreferenceValue): PreferenceDecision

    companion object {
        val DEFAULT_APP_KEYS = setOf(
            "pref_display_mode_library", "library_sorting_mode",
            "pref_library_columns_portrait_key", "pref_library_columns_landscape_key",
            "default_category", "library_update_categories", "library_update_categories_exclude",
        )
    }
}

sealed interface PreferenceDecision {
    data class Import(val type: String, val canonicalJson: String) : PreferenceDecision
    data class Skip(val reason: PreferenceSkipReason) : PreferenceDecision
}
```

Classification order is exact `__PRIVATE_` prefix, exact `__APP_STATE_` prefix, exact whitelist with the required value type, then unknown. Allow only the six known scalar/set subclasses; serialize string sets sorted lexicographically. An unknown preference subclass or a whitelisted key with the wrong value type is `UNSUPPORTED_TYPE`. Remap `default_category` and the two category-set preferences through backup category ID -> name -> target database ID inside the transaction. Never put skipped values into report messages; report only scope/key/reason to avoid leaking secrets.

- [x] **Step 5: Implement deterministic merge policy**

Create pure functions `mergeManga(existing, incoming)`, `mergeChapter`, `mergeHistory`, and `mergeTracking`. Use incoming text/metadata only when incoming `lastModifiedAt > existing.lastModifiedAt`, selecting existing on ties; for every selected string use incoming only when nonblank. Always use `existing.favorite || incoming.favorite`, `read ||`, `bookmark ||`, maximum page/progress/version/date-fetch/date-upload/last-read/read-duration/total-chapters, earliest nonzero start date, latest finish date, set-union genres/excluded scanlators sorted by Unicode code point, and existing tracker score/status/title/URL unless incoming tracker progress is strictly greater.

- [x] **Step 6: Implement the all-or-nothing importer and report**

Use this public signature and keep decode/validation outside the transaction:

```kotlin
class AndroidBackupImporter(
    private val codec: AndroidBackupCodec,
    private val validator: AndroidBackupValidator,
    private val mutations: LibraryMutationPort,
    private val preferences: SupportedPreferencePolicy = SupportedPreferencePolicy(),
    private val checkpoint: ImportCheckpoint = ImportCheckpoint.NONE,
) {
    fun import(path: Path, nowMillis: Long): ImportReport {
        val validated = validator.validate(codec.decode(path))
        return mutations.transaction {
            val accumulator = ImportAccumulator(path, nowMillis)
            mergeCategoriesSourcesMangaChildrenAndPreferences(validated, accumulator)
            checkpoint.beforeReport()
            val report = accumulator.success(nowMillis)
            val reportId = insertReport(report.toRecord())
            report.items.forEach { insertReportItem(reportId, it.toRecord()) }
            report.copy(id = reportId)
        }
    }
}
```

Resolve category backup orders to database IDs before manga links; separately build backup category ID -> name -> database ID for the three category-valued preferences. Resolve history URLs only to chapters within the current manga. Identify tracking rows by `(mangaId, syncId)`; effective remote media ID is `mediaIdInt.toLong()` when nonzero, otherwise `mediaId`, and is merged as data rather than identity. Store extension-store entries only as `ImportReportItem(outcome="SKIPPED", reason="UNKNOWN", message="Extension store installation is outside Plan 2")`; do not persist trust/signing material. A decode or validation rejection returns/throws a typed failure without opening a write transaction or inserting a report. A successful report and all content commit together.

- [x] **Step 7: Extend the Android cross-contract through the real importer and run all gates**

In `DesktopBackupImportContractTest`, keep the Android-generated gzip bytes from Task 4, open a temporary desktop database with `DesktopLibraryDatabaseFactory.open`, pass the path to `AndroidBackupImporter`, close/reopen the database, and compare a stable database projection against the original Android DTO projection. The database projection must include manga/source/url/title/author/favorite/version, chapter URL/name/read/bookmark/page/version, category name/order/flags/link, history time/duration, tracking IDs/progress/title, source metadata, the imported whitelisted preference, and skip report reasons for private/app-state/unknown preferences. No desktop DTO encoder may appear in this test.

Run: `./gradlew :desktop-library-data:test :app:testDebugUnitTest --tests eu.kanade.tachiyomi.data.backup.DesktopBackupImportContractTest`

Expected: PASS for validation paths, rollback, non-regressive merges, preference skip reporting, reopen persistence, Android wire compatibility, and Android-generated bytes imported into the real desktop database.

- [x] **Step 8: Commit backup import**

```bash
git add desktop-library-data/src/main/kotlin/mihon/desktop/library/backup
git add desktop-library-data/src/test/kotlin/mihon/desktop/library/backup
git commit -m "feat: import Android backups transactionally"
```

### Task 6: Add Fail-Closed Staged Local Manga Import

**Files:**
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/local/LocalImportScanner.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/local/LocalImportStager.kt`
- Create: `desktop-library-data/src/main/kotlin/mihon/desktop/library/local/LocalMangaImporter.kt`
- Test: `desktop-library-data/src/test/kotlin/mihon/desktop/library/local/LocalImportScannerTest.kt`
- Test: `desktop-library-data/src/test/kotlin/mihon/desktop/library/local/LocalMangaImporterTest.kt`

**Interfaces:**
- Consumes: Task 2 mutation port and report models.
- Produces: `LocalMangaImporter.import(sourceDirectory, localLibraryRoot, nowMillis): ImportReport`, chapter-directory/archive metadata, and a promoted immutable local media path.

- [x] **Step 1: Write scanner tests for supported formats, Unicode, and long paths**

Build a source directory named `漫画/作者/作品` with chapter directories and one file for each case-insensitive extension `.cbz`, `.zip`, `.rar`, `.cbr`, `.7z`, `.cb7`, `.tar`, `.cbt`, `.epub`. Create a nested directory path longer than 260 characters without exceeding per-segment Windows limits. Assert all names and relative paths survive unchanged, directory chapters classify as `DIRECTORY`, archives as `ARCHIVE`, and ordering is normalized relative-path Unicode ordinal order.

- [x] **Step 2: Write fail-closed and rollback tests**

Cover symbolic link, junction/reparse point where supported, `..` escape in a synthetic scan candidate, absolute child path, duplicate paths after case-folding, unreadable/non-regular entry, unsupported top-level regular file, and a symlink introduced between scan and copy. Each must throw `LocalImportRejected` before final promotion/database mutation. Inject failures after staging copy, after promotion, and before report insertion; assert staging is empty, final directory is absent, and database snapshots/reports are unchanged. Add startup orphan cleanup for `.staging/*` and promoted directories without a `local_manga_entry` row.

- [x] **Step 3: Run local import tests to verify they fail**

Run: `./gradlew :desktop-library-data:test --tests 'mihon.desktop.library.local.*Test'`

Expected: FAIL because scanner/stager/importer do not exist.

- [x] **Step 4: Implement no-follow scanner and manifest**

Define:

```kotlin
enum class LocalChapterKind { DIRECTORY, ARCHIVE }
data class LocalChapterCandidate(val name: String, val relativePath: Path, val kind: LocalChapterKind, val sizeBytes: Long, val modifiedAt: Long)
data class LocalImportManifest(val title: String, val sourceRoot: Path, val chapters: List<LocalChapterCandidate>, val sha256: String)

class LocalImportScanner {
    fun scan(sourceDirectory: Path): LocalImportManifest
}
```

Resolve the root with `toAbsolutePath().normalize()` but do not call `toRealPath()` through a link. For root and every walked entry, reject `Files.isSymbolicLink`, reject `DosFileAttributes.isOther` or the `reparsePoint` bit when exposed, read attributes with `LinkOption.NOFOLLOW_LINKS`, require `candidate.normalize().startsWith(root)`, and never use `FOLLOW_LINKS`. Treat immediate child directories as chapter directories and immediate child supported archives as archive chapters. A chapter directory may contain regular files/directories that are copied as opaque page assets, but every descendant receives the same no-follow/root-escape checks. Reject an empty manga directory. Compute SHA-256 from sorted tuples `(relative UTF-8 path, kind, size, modifiedAt)`.

- [x] **Step 5: Implement staging, atomic promotion, and rollback**

`LocalImportStager.stage(manifest, localLibraryRoot)` creates only beneath `<root>/.staging/<uuid>`, copies with `NOFOLLOW_LINKS`, re-reads source attributes before and after every copy, and verifies the staged manifest. `promote` moves to `<root>/manga/<uuid>` with `ATOMIC_MOVE`; if the filesystem reports atomic move unsupported, fail and delete staging instead of silently weakening guarantees. Return `StagedLocalManga(stagingPath, finalPath, manifest)` implementing `AutoCloseable`; closing before `markCommitted()` deletes staging/final paths. Use explicit validated paths for recursive cleanup and refuse cleanup if the target is not below `.staging` or `manga`.

- [x] **Step 6: Implement local database registration**

Use a reserved local source ID constant `LOCAL_SOURCE_ID = 0L` and stable manga URL `local:<manifest.sha256>`. Inside `mutations.transaction`, promote, insert/merge the manga, insert one chapter per candidate with URL `local:<forward-slash-relative-path>`, insert `local_manga_entry` and `local_chapter_asset`, then insert report/items. On any exception the transaction rolls back and `StagedLocalManga.close()` removes promoted files. On success call `markCommitted()` after transaction return. Re-importing the same manifest must merge idempotently and must not duplicate chapters.

- [x] **Step 7: Run all scanner/importer tests**

Run: `./gradlew :desktop-library-data:test --tests 'mihon.desktop.library.local.*Test'`

Expected: PASS for all nine archive types, chapter directories, Unicode/long paths, link/reparse/traversal rejection, idempotence, and staging/database rollback.

- [x] **Step 8: Commit local import**

```bash
git add desktop-library-data/src/main/kotlin/mihon/desktop/library/local desktop-library-data/src/test/kotlin/mihon/desktop/library/local
git commit -m "feat: import local manga directories safely"
```

### Task 7: Own the Database Runtime and Add Headless CLI Hooks

**Files:**
- Modify: `desktop-app/build.gradle.kts`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/DesktopRuntime.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/Main.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/cli/DesktopCommand.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/cli/DesktopCommandRunner.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/DesktopRuntimeFactoryTest.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/cli/DesktopCommandTest.kt`

**Interfaces:**
- Consumes: `DesktopLibraryDatabaseFactory`, `AndroidBackupImporter`, and `LocalMangaImporter` from Tasks 2, 5, and 6.
- Produces: one `DesktopRuntime : AutoCloseable` with repository/importers and `DesktopCommandRunner.run(command): Int`; UI Tasks 8–9 consume the same runtime.

- [x] **Step 1: Write CLI parsing and lifecycle tests**

Test exact commands: no command, existing `--smoke-test`, `--import-backup=C:\\备份\\a.tachibk`, `--import-local=C:\\漫画\\作品`, and `--list-library-json`. Reject more than one headless command with exit-code-ready `CommandLineException`. Assert `--data-dir` and `--portable` continue working. Use a fake close callback to prove command success and exceptions both close the library exactly once.

- [x] **Step 2: Run focused desktop tests to verify missing commands**

Run: `./gradlew :desktop-app:test --tests 'mihon.desktop.*Runtime*Test' --tests 'mihon.desktop.cli.*Test'`

Expected: FAIL because library runtime/commands are not wired.

- [x] **Step 3: Add the data module dependency and exact command model**

Add `implementation(project(":desktop-library-data"))`. Define:

```kotlin
sealed interface DesktopCommand {
    data object LaunchUi : DesktopCommand
    data object FoundationSmoke : DesktopCommand
    data class ImportBackup(val path: Path) : DesktopCommand
    data class ImportLocal(val path: Path) : DesktopCommand
    data object ListLibraryJson : DesktopCommand
}

object DesktopCommandParser {
    fun parse(args: Array<String>): DesktopCommand
}
```

`--data-dir` and `--portable` configure runtime and do not count as commands. Reject missing/blank paths. Do not accept separate next-argument path forms; the equals form avoids ambiguous Windows quoting.

- [x] **Step 4: Extend runtime ownership**

Replace the `smokeTest: Boolean` field with `command: DesktopCommand`. Add `library: SqlDelightLibraryRepository`, `backupImporter`, and `localImporter`. Open `${directories.root}/library/library.db`; use `${directories.root}/media/local` for local imports. Make `DesktopRuntime.close()` idempotently close the repository. If construction after database open fails, close before rethrowing.

- [x] **Step 5: Implement stable JSON Lines command output**

`DesktopCommandRunner` writes UTF-8 one-line JSON with `Json { encodeDefaults = true; explicitNulls = true }`. Success shapes are exactly `{"command":"import-backup","status":"SUCCEEDED","reportId":N,...}`, `{"command":"import-local",...}`, and `{"command":"list-library","items":[...]}`. Typed decode/validation/local rejection returns exit code `2` and a redacted JSON error containing category/path but no preference value; unexpected errors return `1`. Foundation smoke keeps the exact prefix `MIHON_DESKTOP_SMOKE_OK` so the current verifier remains valid.

- [x] **Step 6: Close resources in every main path**

Wrap runtime in `use`. Headless commands return without creating AWT/Compose. For UI launch, move closing into `application(exitProcessOnExit = false)` completion so the repository remains open while windows exist and closes after `exitApplication()`.

- [x] **Step 7: Run desktop lifecycle/CLI and packaged smoke tests**

Run: `./gradlew :desktop-app:test :desktop-app:createDistributable`

Then run: `desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe --smoke-test --data-dir=$env:TEMP\mihon-plan2-smoke`

Expected: tests PASS; executable exits 0 and prints `MIHON_DESKTOP_SMOKE_OK` while creating `library\library.db` under the explicit root.

- [x] **Step 8: Commit runtime and CLI ownership**

```bash
git add desktop-app/build.gradle.kts desktop-app/src/main/kotlin/mihon/desktop desktop-app/src/test/kotlin/mihon/desktop
git commit -m "feat: wire desktop library runtime and commands"
```

### Task 8: Render the Real Database-Backed Library Screen

**Files:**
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryPresenter.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/DesktopShell.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryPresenterTest.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryScreenTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/ui/DesktopShellTest.kt`

**Interfaces:**
- Consumes: `LibraryRepository.observeLibrary()` and the `DesktopRuntime.library` instance from Task 7.
- Produces: `LibraryPresenter.state: StateFlow<LibraryUiState>`, `LibraryScreen(state, onQueryChange, onMangaSelected, onImportBackup, onImportLocal)`, and selection handed to Task 9.

- [x] **Step 1: Write presenter tests against a controlled repository flow**

Assert loading starts true, emitted database rows become visible, query matching is case-insensitive across title/author, Unicode titles survive, selected manga ID is retained only while present, and repository exceptions become a retryable error without fixture fallback. Use `MutableStateFlow`; do not open SQLite in this unit test.

- [x] **Step 2: Write Compose UI tests for real state rendering**

Render `LibraryScreen` with `LibraryUiState(items=listOf(LibraryManga(...)))`; assert tags `library-screen`, `library-search`, `library-item-<id>`, unread count, empty state, and import actions. Click a manga and assert the exact ID callback. In `DesktopShellTest`, select Library and assert `library-screen` exists instead of the old headline placeholder.

- [x] **Step 3: Run focused UI tests to verify they fail**

Run: `./gradlew :desktop-app:test --tests 'mihon.desktop.ui.library.*Test' --tests mihon.desktop.ui.DesktopShellTest`

Expected: FAIL because the real Library route does not exist.

- [x] **Step 4: Implement the presenter and immutable UI state**

Define:

```kotlin
data class LibraryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val items: List<LibraryManga> = emptyList(),
    val selectedMangaId: Long? = null,
    val errorMessage: String? = null,
)

class LibraryPresenter(repository: LibraryRepository, scope: CoroutineScope) : AutoCloseable {
    val state: StateFlow<LibraryUiState>
    fun setQuery(value: String)
    fun selectManga(id: Long?)
    fun retry()
    override fun close()
}
```

Collect only `repository.observeLibrary()` on `Dispatchers.IO`; combine with query/selection and publish on the supplied scope. Filtering is deterministic by the database-provided order. `close()` cancels only the presenter's child job, not the application scope or database.

- [x] **Step 5: Implement the Material 3 desktop library layout**

Use a top `SearchBar`-equivalent `OutlinedTextField`, primary `FilledTonalButton` actions “Import Android backup” and “Import local manga”, and `LazyVerticalGrid` with minimum 180 dp cards. Each card shows title, source ID, chapter count, and unread badge; use stable keys. Loading uses `CircularProgressIndicator`, errors use text plus Retry, and empty states distinguish empty database from no search matches. Give every clickable item focus semantics and the test tags from Step 2.

- [x] **Step 6: Route production Library to the repository**

Create one `LibraryPresenter` with `remember(runtime.library)` in `MihonDesktopApp`, dispose it with `DisposableEffect`, and pass state/callbacks to `DesktopShell`. Change only `DesktopDestination.Library` content to `LibraryScreen`; keep Updates, History, Browse, Downloads, Settings, and About foundation headlines unchanged. Do not seed rows in production.

- [x] **Step 7: Run presenter/UI tests and the full desktop suite**

Run: `./gradlew :desktop-app:test`

Expected: PASS; Library destination displays supplied repository rows and no fixture is referenced from production source.

- [x] **Step 8: Commit the library screen**

```bash
git add desktop-app/src/main/kotlin/mihon/desktop/ui desktop-app/src/test/kotlin/mihon/desktop/ui
git commit -m "feat: show persisted manga in desktop library"
```

### Task 9: Add Real Manga Details, Chapter List, and Import Actions

**Files:**
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryPresenter.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/MangaDetailScreen.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryImportActions.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/library/LibraryScreen.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/MangaDetailScreenTest.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/ui/library/LibraryImportActionsTest.kt`

**Interfaces:**
- Consumes: `observeManga`, `observeChapters`, `AndroidBackupImporter`, and `LocalMangaImporter`.
- Produces: selected real `MangaDetails`/`LibraryChapter` state, responsive detail pane, chooser-driven imports, and visible structured result dialogs.

- [x] **Step 1: Write detail/chapter Compose tests**

Assert selected real data renders title, author, description, categories, notes, and chapter rows with read/bookmark/page-progress semantics. Assert chapter order exactly follows repository order. On width at least 1,100 dp, detail uses adjacent pane; on narrower width, selecting pushes a detail surface with a Back action. A missing/deleted selected manga returns to Library without crashing. Do not assert reader navigation because reader work is Plan 3.

- [x] **Step 2: Write import action tests with fakes**

Inject chooser functions returning a backup path/local directory or null. Assert cancel performs no import; success shows counts/report ID; typed rejection shows its actionable category; exception messages do not reveal skipped preference values; imports run off the UI dispatcher; and successful completion is observed through repository flow rather than manually appending a UI fixture.

- [x] **Step 3: Run focused tests to verify missing detail/actions**

Run: `./gradlew :desktop-app:test --tests 'mihon.desktop.ui.library.MangaDetailScreenTest' --tests 'mihon.desktop.ui.library.LibraryImportActionsTest'`

Expected: FAIL because detail and import action components do not exist.

- [x] **Step 4: Extend presenter with selected database flows**

When `selectedMangaId` changes, use `flatMapLatest` over `repository.observeManga(id)` and `observeChapters(id)`. Define `MangaDetailUiState(manga: MangaDetails?, chapters: List<LibraryChapter>, loading: Boolean, errorMessage: String?)`. Never cache detached DTOs after selection changes. If manga becomes null, clear selection.

- [x] **Step 5: Implement responsive Material 3 details and chapter list**

Render metadata in a scrollable header and chapters in `LazyColumn` with stable chapter IDs. Show readable labels for read, bookmark, and `lastPageRead`; disable “Read” actions with explanatory tooltip/text “Reader arrives in Plan 3” rather than routing to a blank page. Wide layout uses `Row` with library `weight(0.55f)` and detail `weight(0.45f)` plus divider; narrow layout shows one surface at a time.

- [x] **Step 6: Implement native chooser imports and result dialog**

Use AWT `FileDialog` for `.tachibk` backup selection and `JFileChooser(DIRECTORIES_ONLY)` for local manga directories. Convert selected values to `Path`; execute import with `withContext(Dispatchers.IO)`. Expose `LibraryImportController.importBackup(path)` and `importLocal(path)` returning `ImportActionState`. The UI dialog lists inserted/merged/skipped counts and skip categories, not raw skipped values. On success, leave the repository flow to refresh the screen.

- [x] **Step 7: Run all desktop UI and data tests**

Run: `./gradlew :desktop-app:test :desktop-library-data:test`

Expected: PASS for wide/narrow details, real chapter rendering, chooser cancel/success/error, and data importer suites.

- [x] **Step 8: Commit details and import actions**

```bash
git add desktop-app/src/main/kotlin/mihon/desktop/ui/library desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt desktop-app/src/test/kotlin/mihon/desktop/ui/library
git commit -m "feat: add desktop library details and imports"
```

### Task 10: Add Packaged End-to-End Import and Reopen Verification

**Files:**
- Create: `desktop-library-data/src/test/kotlin/mihon/desktop/library/integration/LibraryImportIntegrationTest.kt`
- Create: `app/src/test/java/eu/kanade/tachiyomi/data/backup/DesktopBackupFixtureWriterTest.kt`
- Modify: `app/build.gradle.kts`
- Create: `scripts/verify-desktop-library.ps1`

**Interfaces:**
- Consumes: packaged CLI commands from Task 7, real importers from Tasks 5–6, and existing `scripts/verify-desktop-foundation.ps1`.
- Produces: repeatable packaged-process evidence that backup/local imports persist across process reopen and that the Java 17 runtime/foundation checks remain intact.

- [x] **Step 1: Write a file-backed integration test**

Create a temporary database plus two backups: first inserts two manga; second overlaps one with older metadata but newer read progress and includes one new manga. Import both, close/reopen, and assert three manga, non-regressed progress, union categories, source metadata, preference report reasons, and latest report counts. Add a forced importer checkpoint failure and assert reopen sees exactly the pre-failure state.

- [x] **Step 2: Run the integration test before verifier work**

Run: `./gradlew :desktop-library-data:test --tests mihon.desktop.library.integration.LibraryImportIntegrationTest`

Expected: PASS; failures here are data defects and must be fixed before scripting packaged verification.

- [x] **Step 3: Create a deterministic Android-wire fixture generator**

Create `DesktopBackupFixtureWriterTest` in the Android `:app` test source set so the generated bytes necessarily use the existing Android `Backup` DTO graph and `ProtoBuf.encodeToByteArray(Backup.serializer(), value)`. Read output only from required system property `mihon.plan2.fixtureDir`, require the normalized directory to be beneath `app/build`, create `android-generated.tachibk` with Okio gzip, and create `android-generated.tachibk.sha256` from `MessageDigest.getInstance("SHA-256")`. The payload contains title `跨平台备份`, category `Android 收藏` whose `id` differs from its `order`, chapter `第 1 话`, source ID `42`, one history row, one tracker, whitelisted preference `pref_display_mode_library`, category-ID preferences `default_category` and `library_update_categories`, private preference `__PRIVATE_auth_token`, app-state preference `__APP_STATE_last_version_code`, and unknown preference `unrecognized_plan2_key`.

In `app/build.gradle.kts`, forward the optional Gradle property only to unit-test JVMs:

```kotlin
tasks.withType<Test>().configureEach {
    providers.gradleProperty("mihonPlan2FixtureDir").orNull?.let { output ->
        systemProperty("mihon.plan2.fixtureDir", output)
    }
}
```

The fixture test must fail with a clear missing-property message when invoked directly without that property only if the writer test itself is selected; all normal Android unit tests remain independent of fixture output. Generate it with:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests eu.kanade.tachiyomi.data.backup.DesktopBackupFixtureWriterTest `
  -PmihonPlan2FixtureDir=app/build/plan2-fixtures
```

Expected: both fixture and lowercase 64-hex-character SHA-256 file exist under `app/build/plan2-fixtures`; no desktop DTO encoder appears in the writer test imports.

- [x] **Step 4: Implement the extended PowerShell verifier**

`verify-desktop-library.ps1` must:

1. call `verify-desktop-foundation.ps1` unchanged;
2. run `:desktop-library-data:test`, the Android cross-contract test, `:desktop-app:test`, `verifySqlDelightMigration`, and `:desktop-app:createDistributable`;
3. run the exact Android fixture-writer test/property command from Step 3 and verify its SHA-256 before import;
4. create a Unicode/long-path local manga directory containing one chapter directory and `.cbz`, `.rar`, `.7z`, and `.epub` chapter files;
5. run packaged `MihonW.exe --import-backup=<fixture> --data-dir=<temp-root>` and require exit 0/`SUCCEEDED`;
6. run packaged `MihonW.exe --import-local=<directory> --data-dir=<same-root>` and require exit 0/`SUCCEEDED`;
7. run packaged `MihonW.exe --list-library-json --data-dir=<same-root>` in a new process and assert `跨平台备份`, the local Unicode title, and nonempty chapters;
8. inspect the packaged `runtime/release` and retain the exact Java 17 assertion;
9. delete only its GUID-named temp root in `finally` after validating the resolved path is beneath `[System.IO.Path]::GetTempPath()`.

Use `Start-Process -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput/-RedirectStandardError` with a 30-second timeout per packaged command. Print `Mihon W desktop library verification passed.` only after every assertion succeeds.

- [x] **Step 5: Run the extended verifier locally**

Run: `pwsh -NoProfile -File .\scripts\verify-desktop-library.ps1`

Expected: exit 0; final line `Mihon W desktop library verification passed.`; foundation smoke and Java 17 checks remain in the output.

- [x] **Step 6: Commit integration verification**

```bash
git add desktop-library-data/src/test/kotlin/mihon/desktop/library/integration app/src/test/java/eu/kanade/tachiyomi/data/backup/DesktopBackupFixtureWriterTest.kt app/build.gradle.kts scripts/verify-desktop-library.ps1
git commit -m "test: verify packaged desktop library imports"
```

### Task 11: Extend CI, Preserve Android Gates, and Record Completion Evidence

**Files:**
- Modify: `.github/workflows/build.yml`
- Create: `docs/superpowers/evidence/windows-shared-data-library.md`
- Modify: `docs/superpowers/plans/2026-09-01-windows-shared-data-library.md`

**Interfaces:**
- Consumes: all Tasks 1–10 and the existing Android/foundation CI jobs.
- Produces: one Windows Plan 2 CI gate, retained Android regression gates, and auditable completion evidence.

- [x] **Step 1: Change only the Windows verifier invocation/artifact name**

Rename the Windows job display name to `Build & Test Windows Library`, run `./scripts/verify-desktop-library.ps1`, and upload the runtime as `mihon-w-library-${{ github.sha }}`. Keep checkout/setup action SHAs, Windows runner, JDK setup, Gradle setup, and the entire Android `build` job unchanged.

- [x] **Step 2: Run the complete local regression matrix**

Run:

```powershell
.\gradlew.bat spotlessCheck `
  :desktop-library-data:test `
  :desktop-app:test `
  :app:testDebugUnitTest `
  verifySqlDelightMigration `
  :app:assembleDebug `
  :desktop-app:createDistributable
pwsh -NoProfile -File .\scripts\verify-desktop-library.ps1
```

Expected: every Gradle task succeeds; Android debug APK is created; desktop runtime is created; cross-contract test passes; verifier prints both foundation and library success lines.

- [x] **Step 3: Perform the manual real-UI acceptance run**

Launch packaged `MihonW.exe` with a clean explicit data root, import the Android-generated `.tachibk` from the Library action, import the Unicode local directory, close the application, relaunch with the same root, and verify: both manga remain; search finds each; selecting each opens real metadata; chapters are listed in deterministic order; read/bookmark/progress labels match imported data; the import report distinguishes imported, private, app-state, and unknown preferences without showing values; no reader action appears functional in this slice.

- [x] **Step 4: Record exact completion evidence**

Create `windows-shared-data-library.md` with these filled fields from the actual run: branch/commit, command transcript summaries and counts, Android fixture generator class and SHA-256, database path, packaged executable path, Java runtime version, Android APK path, imported manga/chapter/category/history/tracking/preference counts, rollback test names, local Unicode/long-path fixture paths, manual UI observations, and remaining Plan 3/Plan 7 boundaries. Every entry must point to an actual output/file; omit any claim not observed.

- [x] **Step 5: Mark this plan's checkboxes only from evidence**

Change a checkbox to `[x]` only when its command/expected outcome is present in the evidence document. Leave failed or unrun steps unchecked and state the exact failure in evidence. This prevents a green-looking plan from replacing verification.

- [x] **Step 6: Commit CI and evidence**

```bash
git add .github/workflows/build.yml docs/superpowers/evidence/windows-shared-data-library.md docs/superpowers/plans/2026-09-01-windows-shared-data-library.md
git commit -m "ci: verify Windows shared data library"
```

---

## Plan Completion Evidence

Implementation is complete only when all of the following evidence exists in `docs/superpowers/evidence/windows-shared-data-library.md` and is reproducible from a clean checkout:

- `:desktop-library-data:test` proves schema creation, reopen persistence, foreign keys, forced rollback, corrupt/raw/gzip/JSON/limit handling, duplicate/reference validation, preference classifications, non-regressive merge behavior, all-or-nothing import, Unicode/long paths, local symlink/reparse/traversal rejection, and staging rollback.
- `:app:testDebugUnitTest --tests eu.kanade.tachiyomi.data.backup.DesktopBackupImportContractTest` proves the existing Android DTO/ProtoBuf encoder produced the bytes consumed by the desktop decoder and that stable semantic projections match.
- Packaged CLI output proves a generated Android backup and a staged local manga import succeed, and a second packaged process proves both persist after reopen.
- Compose UI test output plus the manual packaged run proves the main Library destination observes the real database, opens real details, and renders the real chapter list without fixture-backed production state.
- `spotlessCheck`, Android `testDebugUnitTest`, `verifySqlDelightMigration`, `:app:assembleDebug`, the existing foundation verifier, the packaged Java 17 assertion, and the extended Windows verifier all pass.
- The evidence explicitly states that page decoding/reader behavior remains Plan 3 and Android-compatible backup export/round-trip remains Plan 7; neither is represented as complete by this slice.

## References

- Approved architecture: `docs/superpowers/specs/2026-08-31-windows-port-design.md`
- Foundation slice: `docs/superpowers/plans/2026-08-31-windows-foundation-shell.md`
- Existing Android wire DTOs: `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/`
- Existing Android gzip encoder: `app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt`
- Existing Android decoder behavior: `app/src/main/java/eu/kanade/tachiyomi/data/backup/BackupDecoder.kt`
