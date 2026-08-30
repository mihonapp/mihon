# Mihon Windows Port Design

**Date:** 2026-08-31

**Status:** Approved design

## Objective

Port Mihon to a production-quality Windows desktop application while preserving the existing Android application and its ability to follow upstream. The Windows application must eventually provide a desktop counterpart for every major Android user feature, including library management, online sources, extensions, downloads, the reader, history, trackers, backup and restore, scheduled updates, themes, settings, and application updates.

The first formally complete release targets Windows 10 22H2 and Windows 11 on x64. It ships both an installer and a portable ZIP, obtains application updates from GitHub Releases, and can exchange compatible backups with Android Mihon. ARM64 is not a first-release deliverable, but platform boundaries must avoid preventing a later ARM64 build.

## Confirmed Product Decisions

- The end state is full user-feature parity, not a local-reader-only or permanently reduced edition.
- Existing Android extension APKs do not need to execute unchanged on Windows.
- Existing extension repositories and source behavior remain compatible through Windows-specific packages, a compatibility SDK, and assisted recompilation where technically safe.
- The interface retains Mihon's Material 3 identity while adopting desktop layouts and Windows window/input behavior.
- Windows 10 22H2 and Windows 11 x64 are supported.
- Distribution includes an installer, a portable ZIP, and GitHub Release based automatic updates.
- Backups are importable from and exportable to Android Mihon with maximum practical round-trip fidelity.
- Account-based real-time phone/desktop synchronization is outside this port. Backup exchange remains the cross-device migration mechanism.

## Current-State Constraints

The repository is an Android-native Gradle project. It contains roughly 900 Kotlin files, and hundreds directly import Android APIs. Only the internationalization module currently has meaningful Kotlin Multiplatform source sets. The application depends on Android-specific activities, services, WorkManager, notifications, package installation, WebView, preferences, storage APIs, SQLite integration, and custom reader views. Consequently, the Windows port cannot be produced by changing a build target or repackaging the APK.

The design keeps the Android application buildable while extracting reusable logic behind platform contracts. This reduces long-term divergence from Mihon upstream and avoids maintaining two independent implementations of domain behavior.

## Architecture

### Repository and module strategy

The existing Android modules remain in place. A `desktop-app` module supplies the Windows entry point, Compose Desktop application, desktop navigation, and window lifecycle. Reusable modules move incrementally to Kotlin Multiplatform with `commonMain`, `androidMain`, and `jvmMain` source sets.

The intended module boundaries are:

- `desktop-app`: Windows application entry point, dependency assembly, windows, navigation, and desktop screens.
- `core:common`: platform-neutral primitives and contracts for clocks, dispatchers, files, networking state, clipboard, notifications, scheduling, credentials, and application directories.
- `platform-windows`: Windows implementations for those contracts, including Credential Manager, file associations, proxy discovery, notifications, scheduled tasks, and window persistence.
- `domain`: platform-neutral manga, chapter, category, library, history, download, tracking, and update models and use cases.
- `data`: SQLDelight schema, repositories, migrations, backup codecs, and preference storage. Windows uses a JVM SQLite driver.
- `reader-core`: page models, chapter transitions, prefetch policy, cache policy, reading direction, viewport state, and progress calculation independent of a concrete renderer.
- `source-api`: a cross-platform source protocol that does not expose Android `Context`, `Drawable`, `Intent`, or package-manager types.
- `extension-sdk`: the Windows extension API, manifest schema, compatibility helpers, packaging task, and migration diagnostics.
- `extension-host`: a separate JVM process that loads Windows extension packages and exposes a narrow local IPC protocol.
- `extension-sandbox-windows`: a small native launcher that starts the host inside a Windows AppContainer with a Job Object, explicit resource limits, and access only to the extension's private directory.
- `packaging`: installer, portable archive, update manifest, icons, file associations, and release assembly.

Files are migrated only when a vertical feature slice needs them. Platform abstractions must express real platform capabilities rather than mirror Android classes under new names.

### Process boundaries and extension format

The main application owns the database, library state, downloads, credentials, and user interface. Extensions never receive a database handle and cannot mutate library state directly.

Windows extensions execute in one or more separate host processes. A native Windows launcher creates an AppContainer profile without direct network capability, grants access only to a per-extension private directory, and attaches CPU, memory, process-count, and termination limits through a Job Object. The main application communicates with the container over an authenticated local named-pipe protocol and brokers permitted HTTP operations. Requests and responses are typed and versioned. The protocol covers source metadata, preferences, popular/latest lists, search, manga details, chapter lists, page lists, login state, and limited cookie/network operations. Calls support cancellation, deadlines, structured error codes, and protocol capability negotiation.

A Windows extension artifact uses a dedicated package format containing:

- a manifest with package ID, source IDs, version, minimum API version, languages, content rating, declared domains, and requested capabilities;
- JVM bytecode and declared dependencies;
- icon and localization resources;
- publisher identity, SHA-256 digests, and an optional signature.

Existing extension repositories remain usable as catalog inputs. A sidecar Windows index associates the existing package/source identity with a Windows artifact. Pure Kotlin extensions without Android API dependencies can be rebuilt automatically by the compatibility toolchain. Extensions that use Android-only behavior require a compatibility adapter or a Windows implementation. The application reports unsupported extensions explicitly; it never claims that an arbitrary APK can be losslessly converted.

The host process is disposable. A timeout, crash, excessive memory use, or protocol violation terminates the affected host without terminating the main application. Extension cookies and storage are partitioned by extension identity.

### Data and storage

The installed edition uses `%APPDATA%\MihonW` for application data. The portable edition uses a `data` directory beside the executable and must not write user state to `%APPDATA%` unless the user explicitly chooses an external location.

Storage is divided into:

- a SQLDelight database for library metadata, chapters, categories, history, tracking state, download state, and extension preferences;
- a user-configurable media root for downloaded and local manga;
- disposable image, HTTP, extension, and thumbnail caches;
- Windows Credential Manager entries for OAuth tokens, login credentials, and sensitive cookies;
- rotating, privacy-filtered diagnostic logs.

All file handling supports non-ASCII names, Windows long paths, removable drives, and loss of an external media root. Completed downloads are written to temporary files and atomically renamed after integrity checks.

Database schema changes are versioned. Before a migration, the application creates a consistent snapshot. A failed migration restores the prior database and starts a read-only recovery mode instead of continuing with partially migrated data.

### Backup compatibility

The backup codec retains Mihon's existing backup models and field semantics wherever they are platform neutral. Import supports Android Mihon backups containing library entries, categories, chapters, read and bookmark state, history, tracking, source metadata, and supported preferences.

Import is transactional: decode and validation occur against temporary state, references and versions are checked, and permanent data changes occur only after validation succeeds. Export produces a backup Android Mihon can read. Windows-only values live in optional, ignorable extension fields so older clients do not reject the whole backup.

Round-trip tests use real backup fixtures and compare semantic data, not only successful parsing. Platform-only settings that cannot transfer are listed in the import/export report rather than silently discarded.

### Networking, downloads, tracking, and background work

The main application owns the HTTP stack and supplies controlled network operations to extension hosts. It honors Windows proxy settings and supports user overrides, custom DNS where feasible, cookies, authentication, rate limits, cancellation, and TLS diagnostics.

Downloads use a persistent queue and a state machine stored in the database. The queue resumes after application or system restart, retains completed pages, retries only missing work, detects duplicates, supports concurrency and bandwidth limits, and reports disk-space failures without corrupting chapters.

Scheduled library updates use a platform scheduler while the application is running and a Windows scheduled-task integration for configured background launches. If the operating system prevents a scheduled run, the next launch performs a bounded catch-up run.

Tracking integrations retain their domain interfaces. Interactive authentication opens the system browser and uses a loopback callback where supported. Secrets are stored in Credential Manager. Tracking writes enter a persistent local queue so offline changes can be retried in order. Conflicts expose local and remote values and require an explicit resolution when automatic reconciliation is unsafe.

### Reader architecture

`reader-core` owns chapter loading, page order, transitions, progress, prefetch windows, retries, and cache eviction. The Windows renderer uses Compose Desktop and Skia-compatible image decoders, with tiled or region decoding for images too large to hold at full resolution.

The Windows reader supports single page, dual page, left-to-right, right-to-left, vertical continuous, and Webtoon modes. It also supports cover offsets, original size, fit width, fit height, smooth zoom, pan, chapter continuity, fullscreen, borderless, and normal windows.

Input mappings cover mouse buttons, wheel, touchpad gestures, keyboard navigation, configurable click regions, and touch. Page decoding is bounded so memory use does not grow with the total chapter length.

## Desktop Interface

The application combines Mihon Material 3 styling with Windows desktop information density and behavior.

The main window has a fixed left navigation rail for Library, Updates, History, Browse, and Downloads, with Settings and About at the bottom. The content area displays grids, lists, search results, and task views. On wide windows a details pane displays the selected manga without forcing a route change. Narrow windows collapse to two or one content columns but do not revert to a mobile bottom bar.

The title bar integrates application tools while preserving dragging, minimize, maximize, snap layouts, and the Windows system menu. Window bounds, monitor placement, and display scale are restored safely. Library, reader, details, and settings views can open in separate windows where useful.

Desktop interactions include:

- `Ctrl+K` global search, `Ctrl+,` settings, `F5` refresh, and `Ctrl+W` close;
- `Ctrl` and `Shift` range selection in grids and lists;
- context menus for item operations;
- drag-and-drop import and direct opening of supported local files;
- hover actions without hiding the equivalent keyboard or touch action;
- a task center for downloads, updates, transfer speed, queue state, and errors;
- complete focus indicators, keyboard traversal, and accessibility semantics.

Themes include light, dark, system-following, and user-selected Mihon palettes. The default follows the Windows theme and accent color while retaining Mihon branding.

## Error Handling, Recovery, and Security

Errors use structured categories and actionable messages. Network failures distinguish offline state, timeout, TLS failure, proxy failure, rate limiting, expired authentication, and source-parser changes. Extension errors name the extension and operation. Damaged images can be retried, skipped, or opened as the original file. Download retries preserve valid pages.

Extension installation displays publisher, digest, requested capabilities, and network domains. Repository trust is explicit and fingerprint-based. Packages are verified before activation. An unsigned package requires a separate warning and explicit user confirmation. Host calls have time, memory, and CPU limits. AppContainer filesystem ACLs and the brokered-network model enforce the narrow IPC surface rather than relying on JVM convention alone. The IPC surface grants no arbitrary database or filesystem access. Sensitive state is isolated by extension.

Updates download into a temporary location and verify the release manifest, signature policy, and hashes before activation. The installer can return to the previous application version after a failed update. The portable updater replaces application files only after the main process exits. Application rollback never deletes user data, and database migration is not treated as an incidental side effect of file replacement.

Logs omit credentials, cookies, manga content, and complete sensitive query strings. Crash reports stay local unless the user exports them. A diagnostic bundle contains redacted logs, application and OS versions, installed extension metadata, and database integrity results.

## Implementation Slices

The port is delivered through testable vertical slices while preserving the full objective:

1. Multiplatform foundation and a bootable Windows shell with database, preferences, platform directories, theme, and navigation.
2. Local library, local-source import, compatible backup import, library/detail/chapter screens, and persistence.
3. Reader core and all Windows reading modes with bounded decoding and progress persistence.
4. Extension SDK, isolated host, repository catalog, one migrated real extension, browse/search/details/chapters/pages, and permission UI.
5. Persistent downloads, task center, caching, scheduled library updates, and Windows notifications.
6. History, categories, all trackers, authentication, offline tracking queue, and conflict handling.
7. Remaining settings, full backup export and round-trip compatibility, diagnostics, accessibility, and performance work.
8. Installer, portable ZIP, file associations, GitHub update flow, rollback, clean-machine verification, and release documentation.

An intermediate slice is evidence of progress, not completion of the Windows-port objective.

## Testing Strategy

### Automated layers

- Domain unit tests cover filters, sorting, progress, download transitions, retries, permissions, and Windows path behavior.
- Database tests cover clean creation, every supported migration, rollback, concurrent access, and corruption recovery.
- Backup contract tests perform Android-to-Windows-to-Android semantic round trips with real fixtures.
- Extension contract tests cover search, popular/latest, details, chapters, pages, authentication, cookies, rate limits, cancellation, and timeouts against deterministic mock servers.
- Reader tests cover normal, transparent, animated, corrupt, and extreme-dimension images; dual-page offsets; RTL; Webtoon; zoom; and page eviction.
- Compose UI tests cover navigation, search, selection, context menus, drag-and-drop, task center, settings, focus order, and window restoration.
- Screenshot baselines protect key desktop layouts and reader rendering at representative display scales.
- Android unit tests and APK assembly remain regression gates while shared modules are extracted.

### End-to-end and distribution tests

An end-to-end scenario installs the application, imports a real backup, installs a Windows extension, searches for a manga, adds it to the library, downloads and reads a chapter, updates tracking progress, exports a backup, updates the application, and uninstalls while respecting the user's data-retention choice.

Clean virtual machines running Windows 10 22H2 and Windows 11 verify the installer and portable ZIP, upgrade and rollback, file associations, proxy behavior, firewall prompts, non-ASCII paths, long paths, and removable media loss.

## Completion Criteria

The Windows port is complete only when all of the following are proven against current builds:

- Every major Android user feature has a working Windows counterpart; no visible feature is a nonfunctional placeholder.
- Installer and portable x64 distributions run independently on clean Windows 10 22H2 and Windows 11 systems.
- Real Mihon backup fixtures survive Android-to-Windows-to-Android round trips without silent loss of transferable library, category, chapter, history, tracking, or supported preference data.
- At least one real Windows extension passes repository installation, search, details, library addition, download, and reading end to end, and the SDK/compatibility tooling can be applied to additional sources.
- A library containing 10,000 manga remains usable for filtering, searching, and scrolling.
- Cold startup on the release reference machine is no more than five seconds, idle memory is targeted below 500 MB, and long-chapter reading demonstrates bounded memory rather than growth proportional to page count.
- Extension crash, offline operation, full disk, database migration failure, damaged page, expired tracker login, and failed application update each have a tested recovery path.
- A real installer upgrade and uninstall have been performed on a clean system, and the portable edition has been verified not to leak user state into the installed-edition directory.
- Shared changes leave the Android tests and APK build passing.

## Non-Goals

- Running arbitrary Android extension APKs unchanged through an embedded Android runtime.
- Account-based real-time synchronization between Windows and Android.
- Windows ARM64 binaries in the first complete release.
- Replacing Mihon's visual identity with a pure WinUI clone.
- Bundling or hosting copyrighted manga content.
