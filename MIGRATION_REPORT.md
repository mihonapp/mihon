# Migration Report: kotlinx.collections.immutable 0.5.0

**Project:** mihon
**Modules migrated:** `:app`, `:presentation-core`, `:presentation-widget` (all three depend on the library; only `:app` had a call site needing rewrite)
**Date:** 2026-05-14
**kotlinx.collections.immutable version:** 0.4.0 → 0.5.0-beta01
**Kotlin version:** 2.3.21 (unchanged by this migration)
**Status:** Completed successfully

---

## Pre-Migration State

### Library Usage

| Module | Build file location | Version |
|--------|---------------------|---------|
| `:app` | `app/build.gradle.kts:202` (`implementation(libs.kotlinx.collections.immutable)`) | 0.4.0 |
| `:presentation-core` | `presentation-core/build.gradle.kts:43` | 0.4.0 |
| `:presentation-widget` | `presentation-widget/build.gradle.kts:21` | 0.4.0 |
| version catalog | `gradle/libs.versions.toml:46` | 0.4.0 |

### Baseline Build

- **Command:** `./gradlew :app:compileDebugKotlin --continue`
- **Result:** success in 47s. 8 pre-existing `@DelicateCoroutinesApi`-style warnings unrelated to the migration:
  - `core/common/.../WebViewInterceptor.kt:59`
  - `app/.../DownloadManager.kt:224, :251`
  - `app/.../Downloader.kt:114`
  - `app/.../LibraryUpdateNotifier.kt:212`
  - `app/.../NotificationReceiver.kt:200, :226`
  - `app/.../HttpPageLoader.kt:134`

  These persist in the post-migration build as well — they are baseline noise, not migration-induced.

### Pre-existing `@Suppress("DEPRECATION")` annotations

Twelve files contain `@Suppress("DEPRECATION")` annotations (e.g. `ReaderActivity.kt`, `WebViewActivity.kt`, `ImageUtil.kt`). None of them suppress kotlinx.collections.immutable deprecations — they cover Android-framework deprecations (e.g. legacy WebView API, deprecated `Configuration.locale`, etc.). None became redundant after the migration; none required removal.

---

## Migration Steps

### Phase 3: Version Bump

- **File:** `gradle/libs.versions.toml`
- **Change:** `kotlinx-collections-immutable = "0.4.0"` → `"0.5.0-beta01"` (line 46)

### Phase 4: Compiler-Driven Renames

Total call sites renamed: **1**

| File | Line | Receiver type | Before | After |
|------|------|---------------|--------|-------|
| `app/src/main/java/mihon/feature/migration/list/MigrationListScreenModel.kt` | 330 | `PersistentList<MigratingManga>` | `items.toPersistentList().remove(item)` | `items.toPersistentList().removing(item)` |

The compiler emitted exactly one `kotlinx.collections.immutable` deprecation warning after the version bump:

```
w: …/MigrationListScreenModel.kt:330:72 'fun remove(element: MigratingManga): PersistentList<MigratingManga>' is deprecated. Use removing() instead.
```

The rename was the obvious one from `rename-map.md` (`PersistentCollection.remove(element)` → `removing(element)`). One recompile after the edit produced zero `kotlinx.collections.immutable` warnings.

### Phase 5: Compiler-Blind Passes

#### Operator-syntax indexed assignment (`list[i] = v`)

None rewritten. The grep produced 28 candidate matches across 6 files (`UpdatesScreenModel.kt`, `MangaScreenModel.kt`, `SearchScreenModel.kt`, `CategoryDialogs.kt`, `SettingsMainScreen.kt`, plus `LibraryScreenModel.kt`). All were on non-persistent receivers:

| Pattern | Files | Receiver type | Rewrite? |
|---|---|---|---|
| `list[i] = …` inside `.mutate { list -> }` | `UpdatesScreenModel.kt:207` | `PersistentList.Builder` (a `MutableList`) | No — `MutableList[i] = v` is in-place, not deprecated |
| `it[k] = …` inside `.mutate { it -> }` | `SearchScreenModel.kt:200` | `PersistentMap.Builder` (a `MutableMap`) | No — same reason |
| `selectedPositions[i] = …` | `UpdatesScreenModel.kt:344-410`, `MangaScreenModel.kt:980-1043` | `IntArray` field | No — primitive array |
| `mutableList[i] = …` | `CategoryDialogs.kt:269` | `MutableList<…>` | No |
| `arr[i] = …` | `SettingsMainScreen.kt:67` | `FloatArray(3)` | No |

#### Method / callable references

None rewritten. The grep produced 19 candidates across 8 files (`newList::add`, `list::addAll`, `newItems::addAll`, `crashlyticsPref::set`, `preference::set`, `mutableExcludedScanlators::clear`, etc.). Every match was on `MutableList`, mihon's own `Preference<T>` type, or a `Builder` inside `.mutate { }`. Zero callable references on a `PersistentList` / `PersistentMap` / `PersistentSet` / `PersistentCollection`.

#### Java callers

No Java callers. The grep against `**/*.java` returned zero matches involving immutable collections. (mihon has only 8 `.java` files total; none import or use the library.)

### Phase 6: Interface Implementers

No third-party implementers in this codebase. The grep `:\s*(class|object|abstract\s+class|interface)\b.*\bPersistent(List|Map|Set|Collection)<` returned no matches — mihon is a consumer of `kotlinx.collections.immutable`, not a library that implements its interfaces.

### Phase 7: `@Suppress("DEPRECATION")` Cleanup

No redundant suppressions. The post-rename recompile emitted no `'@Suppress("DEPRECATION")' annotation has no effect` warnings; existing `@Suppress("DEPRECATION")` annotations in the codebase cover unrelated Android-framework deprecations and remain necessary.

### Phase 8: Verification

- **Compile command:** `./gradlew :app:compileDebugKotlin --continue` → success in 3s (incremental).
- **Remaining `kotlinx.collections.immutable` warnings:** 0.
- **Tests:** `./gradlew :app:testDebugUnitTest` → success in 28s. 6 tests in `MigratorTest` (`withinRangeMigration`, `largeMigration`, `sameVersion`, `smallMigration`, `noMigrations`, `initialVersion`) PASSED. (These `MigratorTest` tests are about mihon's app-data version migration — incidentally named — and are unrelated to the kotlinx migration; they're the only unit tests `:app` registers.)

---

## Errors Encountered

None.

---

## Non-Trivial Decisions

- **`.mutate { }` Builder calls were deliberately not migrated.** mihon uses `.mutate { … }` extensively (5 files: `LibraryScreenModel.kt`, `MigrateMangaScreenModel.kt`, `UpdatesScreenModel.kt`, `SearchScreenModel.kt`, `DebugInfoScreen.kt`). Inside the `mutate` lambda, the receiver is a `PersistentXxx.Builder` (a `MutableCollection`), not a `PersistentXxx`. The Kotlin compiler does not flag any of these calls because `MutableCollection.add/remove/set` etc. are not deprecated. **Trusting the compiler was the correct discipline here** — a naive textual find-replace of `.add(` / `.remove(` / `.set(` / `[i] = ` would have broken every one of these `mutate` blocks.

- **Operator-syntax indexed assignment was checked but produced no rewrites.** The two candidates inside `.mutate { }` (`UpdatesScreenModel.kt:207`, `SearchScreenModel.kt:200`) are on `Builder` receivers and stay as in-place mutation, not `replacingAt`.

## Files Changed

### Gradle Files
- `gradle/libs.versions.toml` — version bump 0.4.0 → 0.5.0-beta01 (line 46).

### Kotlin Sources
- `app/src/main/java/mihon/feature/migration/list/MigrationListScreenModel.kt` — renamed 1 `PersistentList.remove(element)` call to `removing(element)` (line 330).

### Java Sources
- None.

### Created
- `MIGRATION_REPORT.md` — this file.

### Not Modified (deliberately)
- All `.mutate { }` blocks (`LibraryScreenModel.kt`, `MigrateMangaScreenModel.kt`, `UpdatesScreenModel.kt`, `SearchScreenModel.kt`, `DebugInfoScreen.kt`) — Builder receivers, not deprecated.
- All `*::add` / `*::addAll` / `*::set` / `*::clear` callable references — `MutableList` / `Preference` / `Builder` receivers, not deprecated.
- All `selectedPositions[i] = `, `arr[i] = `, `mutableList[i] = ` — primitive arrays or `MutableList`, not `PersistentList`.
