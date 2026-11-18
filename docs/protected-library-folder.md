# Protected library folder

A hidden, PIN-protected folder inside the Library. Entries moved into it disappear from every regular view of the
app until the folder is opened with its 4-digit PIN.

This document covers what the project looks like, how the feature was fitted into it, the decisions that were
made, and what the feature does not protect against.

## How the project is organised

| Module | Role |
| --- | --- |
| `app` | Screens, view models, workers, notifications, backup and restore |
| `domain` | Models, repository interfaces, interactors, preference classes |
| `data` | SQLDelight schema, migrations and repository implementations |
| `core/common` | Preference store, shared utilities |
| `i18n` | String resources (moko-resources); only `base/` is edited by hand, other locales come from Weblate |
| `presentation-widget` | Home-screen widget showing recent updates |

- Dependency injection is Metro (`@Inject`, `@ContributesBinding`, `AppGraph`). View models are obtained with
  `metroViewModel` / `assistedMetroViewModel`.
- Navigation is Voyager. `HomeScreen` hosts the five tabs; other screens are pushed on the navigator created in
  `MainActivity`.
- "In the library" means `manga.user_favorite_at IS NOT NULL`. Categories are the `manga_category` join table.
- The Library, Updates and History screens read the SQL views `libraryView`, `updatesView` and `historyView`.
- The existing app lock lives in `SecureActivityDelegate` (lock on background, `FLAG_SECURE`) and
  `AuthenticatorUtil` (biometric prompt).
- Preference keys built with `Preference.appStateKey()` are never written to backups.

## How the feature works

### Storage

- Membership is a table, `protected_manga(manga_id)`, added by migration `20.sqm`. It references `manga` with
  `ON DELETE CASCADE`. Existing tables and categories are untouched; an entry keeps its categories while it is in
  the folder and has them again when it is taken out.
- The PIN is stored as a PBKDF2-HMAC-SHA256 hash (120,000 iterations, 256 bits) with a random 16-byte salt, in
  `ProtectedFolderPreferences`. Every key of that class is an app-state key.

### Hiding

Protected entries are excluded in SQL, so they are hidden whether the folder is locked or open. The open folder is
the only place that lists them.

| Where | How |
| --- | --- |
| Library tabs and counts, Stats | `library` query on `libraryView` |
| Updates tab, widget | `updatesView` |
| History tab, resume button | `historyView`, `getLatestHistory` |
| Total read time in Stats | `getReadDuration` |
| Backups, storage export, default chapter flags | `getFavorites`, `getReadMangaNotInLibrary` |
| Source migration lists and counts | `getFavoriteBySourceId`, `getSourceIdWithFavoriteCount` |
| Duplicate-entry dialog | `getDuplicateLibraryManga` |
| Upcoming | `getUpcomingManga` |
| Clear database | `getSourceIdsWithNonLibraryManga`, `deleteNonLibraryManga` |
| Browse and global search results | shown as not in the library (`BrowseSourceViewModel`, `SearchViewModel`) |
| Download queue screen | filtered in `DownloadQueueViewModel` |
| New-chapter, update-progress, update-error and download notifications | `LibraryUpdateWorker`, `DownloadNotifier` |

Protected entries are still checked for new chapters and auto-downloaded during library updates. They produce no
notification, no Updates entry and no badge count.

### Opening and locking

- Entry point: a long-press on the Library title. It is one constant, `ProtectedFolderEntry` in
  `ProtectedFolderEntryGesture.kt`; `TitleDoubleTap` is the other supported value. The gesture has no ripple and no
  accessibility action.
- `ProtectedFolderScreen` shows the PIN pad, then the folder. First use asks for a new PIN twice.
- `ProtectedFolderSession` holds the unlocked state in memory. The folder locks when:
  - the app goes to the background,
  - the folder screen leaves the navigation stack (back to the Library, another tab, a deep link),
  - nothing has been touched for the idle timeout (default 1 minute; paused while the reader is in front),
  - the process dies.
- Opening a protected entry's details or reader from the folder keeps it open. When it locks, those screens close.
- While the folder is open the window is marked `FLAG_SECURE`, so it is blank in the recent-apps view and cannot be
  screenshotted. This can be turned off in the folder's options.

### PIN rules

- Exactly four ASCII digits; setting a PIN needs a matching second entry.
- Five wrong PINs in a row lock entry for 30 seconds. Each further wrong PIN doubles the lockout, up to about 34
  hours. The right PIN resets the count. The count and the lockout survive an app restart.
- Changing and removing the PIN (Settings > Security and privacy > Protected folder) both ask for the current PIN
  and share the same lockout.
- Removing the PIN returns every protected entry to the regular library.
- Biometric unlock is optional and off by default. It accepts a biometric only, never the device PIN or pattern,
  and falls back to the folder PIN.

### Forgot PIN

"Forgot PIN?" is on the PIN pad only and stays enabled during a lockout. After typing `DELETE` in the confirmation
dialog:

1. Every protected entry is deleted from the database in a single statement. Chapters, history, tracker links and
   category assignments go with it through foreign keys. If this fails, nothing is deleted, the PIN is unchanged
   and an error is shown.
2. The PIN hash, salt, failed-attempt count and lockout are cleared.
3. Downloaded chapters and cached covers of those entries are deleted. Files cannot be rolled back, so this runs
   last; a failure here is reported, but the entries stay deleted.
4. The PIN setup flow starts and the folder is empty.

The entry rows are deleted outright rather than only removed from the library, so they cannot come back through
the "read entries" part of a later backup.

### Moving entries

- In the Library's selection mode, the overflow menu has "Move to protected folder", with a confirmation. The
  action is shown only once a PIN has been set.
- In the open folder, selecting entries offers "Remove from protected folder", with a confirmation.

## Backups

- Protected entries are **not** included in backups by default.
- "Include in manual backups" in the folder's options adds them to backups the user creates by hand. Each one
  carries a `protected` flag, so restoring puts it back in the folder instead of the visible library.
- Automatic backups never include protected entries, whatever that option says.
- The PIN hash, salt and attempt counters are never backed up, and restore ignores app-state keys found in a backup
  file, so an edited backup cannot replace the PIN.
- Restoring a backup with protected entries onto an install without a PIN leaves them hidden until a PIN is set
  through the entry gesture.
- Other apps that read this backup format ignore the `protected` flag and will show those entries.

## Where the code is

| Area | Files |
| --- | --- |
| Schema and queries | `data/src/main/sqldelight/tachiyomi/data/protected_manga.sq`, `migrations/20.sqm`, `manga.sq`, `history.sq`, `view/*.sq` |
| Repository | `mihon.domain.protectedfolder.repository.ProtectedMangaRepository`, `mihon.data.protectedfolder.ProtectedMangaRepositoryImpl` |
| Interactors | `GetProtectedLibraryManga`, `GetProtectedMangaIds`, `SetMangaProtected` in `mihon.domain.protectedfolder.interactor` |
| PIN | `PinHasher`, `PinLockout`, `ProtectedFolderPin`, `ProtectedFolderPreferences` in `mihon.domain.protectedfolder.service` |
| Session, wipe, UI | `app/src/main/java/mihon/feature/protectedfolder/` |
| Library integration | `LibraryToolbar`, `LibraryTab`, `LibraryViewModel`, `LibraryBottomActionMenu` |
| Locking hooks | `App.onStop`, `BaseActivity.onUserInteraction`, `MainActivity`, `ReaderActivity`, `SecureActivityDelegate` |
| Settings | `SettingsSecurityScreen` |
| Backup and restore | `BackupCreator`, `MangaBackupCreator`, `BackupManga`, `MangaRestorer`, `RestoreRepositoryImpl`, `PreferenceRestorer` |
| Tests | `domain/src/test/.../protectedfolder/service/`, `data/src/test/.../ProtectedMangaQueriesTest.kt`, `app/src/test/.../WipeProtectedFolderTest.kt` |

The `data` module had no unit tests. Its new tests run the real queries against an in-memory SQLite database
through SQLDelight's JDBC driver (`app.cash.sqldelight:sqlite-driver`), added as a test-only dependency because
the app's own driver only runs on a device.

## Limitations

- A 4-digit PIN protects against casual snooping on an unlocked device. It does not protect against someone with
  root access or file-system access to the app's private storage: the database is not encrypted, and a hash of a
  4-digit PIN can be brute-forced offline in minutes.
- Anyone who can open the PIN pad can wipe the folder through "Forgot PIN?". That is the intended recovery path.
- Downloaded chapters of protected entries stay in the normal download folder, under the entry's title, where a
  file manager can see them. Cached covers are in app-private storage.
- Opening a protected entry's page by finding it in its source shows its real state (in library, read chapters).
  Only the marker in browse and search result lists is hidden.
- Adding a protected entry to the library again from its source keeps it in the folder, so it appears nowhere
  until the folder is opened.
- An entry removed from the library while protected stays protected, so its history does not surface. It is no
  longer listed in the folder either; it is removed for good by a wipe, and its history shows again if the PIN is
  removed.
- Once a PIN exists, the "Move to protected folder" action and the "Protected folder" settings section are
  visible, which tells an observer the feature is in use. Before a PIN is set nothing is visible.
- Until a PIN is set, the entry gesture opens the PIN setup screen for whoever performs it.
- Tracker services still receive reading progress for protected entries.
- The metadata refresh worker skips protected entries.
- The lockout uses the device clock. Moving the clock forward ends a lockout early.
