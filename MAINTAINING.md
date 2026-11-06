# Maintaining Mekuri

Mekuri is my personal fork of [Mihon](https://github.com/mihonapp/mihon). It installs as a separate app
(`com.vsrksaketh.mekuri`), so it can sit next to the official Mihon.

- Repo: https://github.com/vsrksaketh-dev/mekuri (public fork)
- Based on: `mihonapp/mihon` `main` @ `70c73c728b6350dd9f1447420e9cef54e8403b41` (2026-11-05),
  68 commits after the latest stable tag **v0.21.3** (`feadad5e1e2b`).

## What differs from upstream

| File | Change |
|---|---|
| `app/build.gradle.kts` | `applicationId = "com.vsrksaketh.mekuri"`, `versionName = "1.0.0"` (versionCode left at upstream's value) |
| `i18n/.../base/strings.xml` | `app_name` = Mekuri |
| `i18n/.../<22 locales>/strings.xml` | "Mihon" → "Mekuri" in `information_webview_required`, `untrusted_extension_message`, `obsolete_extension_message` |
| `app/src/main/res/drawable/ic_launcher_{foreground,background,monochrome}.xml` | Mekuri icon (manga page being turned, purple gradient) |
| `app/src/debug/res/drawable/ic_launcher_{foreground,background}.xml` | same icon, orange background for debug builds |
| `app/src/main/res/drawable/ic_mihon.xml` | Mekuri logo (About header, splash, notifications). File name kept on purpose |
| `app/src/main/res/values/colors.xml` | splash colour |
| `app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt` | update repo → `vsrksaketh-dev/mekuri` |
| `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt` | only a GitHub link to this repo; Mihon website/social/privacy links removed |
| `app/src/main/java/eu/kanade/presentation/more/MoreScreen.kt` | "Support us" (Mihon donations) hidden |
| `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt` | donation campaign dialog disabled |
| `app/src/main/java/mihon/feature/mekuri/**` | Mekuri-only code: `MekuriSetup` (one-time defaults), `autoscroll/`, `nav/FloatingNavPill`, `offline/OfflineTab`, `you/YouTab`, `shelf/` (BookshelfHome, CompactShelf, Shelf), `vault/` (MekuriVault, VaultScreen, VaultAuth, VaultRow, VaultPrivacy), `settings/MekuriSettingsHome` |
| `App.kt` | calls `MekuriSetup.run(...)` on startup |
| `HomeScreen.kt` | tabs Library · Updates · Offline · Browse · You; floating pill on phones (`MekuriPhoneHome`) |
| `ExtensionsTab.kt`, `MigrateSourceTab.kt` | tab titles Add-ons / Move |
| `LibraryTab.kt` | bookshelf home + compact shelf (unless Classic library); Back/Library button return to the shelf |
| `LibraryContent.kt` | `tabsOverride` hook used for the compact shelf |
| `LibraryPreferences.kt`, `SettingsLibraryScreen.kt` | `mekuri_classic_library` switch |
| `LibraryViewModel.kt`, `UpdatesViewModel.kt`, `HistoryViewModel.kt`, `SearchViewModel.kt`, `SourcesViewModel.kt`, `SourcesScreen.kt`, `SourcesTab.kt` | vault filtering (hidden while locked), Vault section/row, Move to vault |
| `GetIncognitoState.kt` | vault sources always incognito |
| `SettingsMainScreen.kt` | phones show `MekuriSettingsHome` |
| `LibraryUpdateNotifier.kt` | vault manga never appear in update notifications |
| `ReaderActivity.kt` | also `VaultScreenPrivacy()` |
| `.github/workflows/release.yml` | `generate_release_notes` so the in-app update dialog lists what changed |
| `LibraryPreferences.kt` | also `mekuri_last_shelf_id` (last-opened shelf by id) |
| `icons/material-symbols/.../rounded/lock.svg`, `lock_open.svg` | added icons |
| prefs `mekuri_vault_categories`, `mekuri_vault_sources` | what's in the vault (ids) |
| `i18n/.../base/strings.xml` | `mekuri.*` strings at the end |
| `ReaderActivity.kt`, `ReaderAppBars.kt`, `ReaderPreferences.kt` | auto-scroll wiring, speed pref `mekuri_auto_scroll_speed` |
| `ReaderViewModel.kt`, `ReaderSettingsViewModel.kt`, `ReadingModePage.kt`, `ReaderBottomBar.kt`, `SettingsReaderScreen.kt` | long strip only (mode always WEBTOON; pickers removed) |
| `TachiyomiColorScheme.kt`, `presentation-core/.../colors_tachiyomi.xml` (values + values-night) | Mekuri colours in the Default theme |
| `AppThemePreferenceWidget.kt` | theme picker shows only the Mekuri theme |
| `LocaleHelper.kt` | default source languages = English only |
| `.github/workflows/build.yml` | PR = debug-signed build; push to main = signed APK artifact; telemetry excluded |
| `.github/workflows/release.yml` | tag `v*` → signed APKs → GitHub Release in this repo |
| `.github/workflows/update_website.yml`, `.github/FUNDING.yml` | deleted (Mihon-only) |
| `.gitignore` | keystores, `signing.properties` |
| `README.md`, `.github/assets/logo.png`, `.idea/icon.svg` | fork notice at top, Mekuri logo |
| `MAINTAINING.md`, `CLAUDE.md`, `ROADMAP.md` | these docs |

Intentionally **unchanged**: Kotlin namespace (`eu.kanade.tachiyomi`), tracker OAuth schemes (`mihon://…-auth`,
`app.mihon://comick-auth`: they are registered with the trackers, and changing them breaks tracker login),
the extension-repo deep links (`tachiyomi://add-repo`, `mihon://extension-store`), the mihon.app docs/help links,
and the tracker User-Agent strings. Provider authorities use `${applicationId}`, so they changed automatically.

## Variants

| Build type | Package | Notes |
|---|---|---|
| `release` | `com.vsrksaketh.mekuri` | the one I install |
| `debug` | `com.vsrksaketh.mekuri.dev` | orange icon, debuggable |
| `foss` / `nightly` / `benchmark` | `.foss` / `.debug` / `.benchmark` | inherited from upstream, not used |

Build locally (macOS):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleRelease -Pinclude-telemetry=false -Penable-updater=true   # signed, -> app/build/outputs/apk/release/
./gradlew assembleDebug                                                     # debug
```

Use `app-universal-release.apk` if you're unsure which APK fits your phone. Never pass `-Pdist=ci` or `-Pdist=github`:
those switch on Firebase telemetry.

## Signing

- Release keystore: `~/android-keys/mekuri-release.jks`, alias `mekuri`. The passwords are in
  `~/android-keys/mekuri-release-passwords.txt`. **Back both up** (password manager or cloud drive). If the key is lost,
  updates can't be installed over the existing app.
- Local builds: `keystore.properties` in the repo root (git-ignored) holds `storeFile`, `storePassword`, `keyAlias` and `keyPassword`.
  Upstream's `app/build.gradle.kts` reads it.
- CI: when `MIHON_GITHUB_RELEASE=true`, Gradle reads the env vars `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. These GitHub secrets feed them:
  `SIGNING_KEY` (base64 of the .jks), `KEY_STORE_PASSWORD`, `KEY_PASSWORD`, `ALIAS`. `GRADLE_ENCRYPTION_KEY` encrypts the Gradle cache.
  To rotate a secret: `base64 -i ~/android-keys/mekuri-release.jks | tr -d '\n' | gh secret set SIGNING_KEY`.

## Cutting a release

1. Bump `versionName` in `app/build.gradle.kts` (e.g. `1.0.1`) and merge to `main`.
2. Tag it: `git tag v1.0.1 && git push origin v1.0.1`. The tag must equal `v` + versionName.
3. The **Release** workflow builds the signed APKs and publishes "Mekuri v1.0.1" with two APKs: `mekuri-v1.0.1.apk` (universal) and `mekuri-arm64-v8a-v1.0.1.apk`.
4. The app's update checker (More → About → Check for updates) reads `/releases/latest` and offers the APK for your phone's ABI.

Versioning: Mekuri uses its own `MAJOR.MINOR.PATCH`, and the in-app comparison is numeric per component. **Never lower
`versionCode`.** Keep it equal to upstream's, because upstream migrations are keyed to it. If I need to ship two builds on the same
upstream code, the same versionCode still installs as an update.

## Pulling in upstream updates

```bash
git fetch upstream --tags
git switch -c sync/upstream-v0.22.0 main
git merge v0.22.0          # or upstream/main
```

Expected conflict hotspots: `app/build.gradle.kts` (keep my applicationId and versionName, take upstream's versionCode),
`AboutScreen.kt`, `MoreScreen.kt`, `MainActivity.kt` (donation call), `.github/workflows/*.yml`, translated `strings.xml`
files (re-run the "Mihon"→"Mekuri" replacement for the three keys above), and icon drawables if upstream changed them.
Then build, install, run the phone checklist below, open a PR, wait for green CI, merge, and tag a new version.

## Phone checklist after any change

Installs next to Mihon · name and icon are Mekuri · add an extension repo, install an extension · search a source, open a manga ·
download a chapter · create a backup and restore it.

## Rules for feature work

- One branch per feature (`feature/<name>`), PR into `main`, CI green before merge.
- Put new code in new files where possible. Keep edits to upstream files small and mark them with a `// Mekuri:` comment.
- Never change: the backup format (protobuf), the extension API/loader, the DB schema without a migration, or anything that breaks
  restoring a Mihon backup.
