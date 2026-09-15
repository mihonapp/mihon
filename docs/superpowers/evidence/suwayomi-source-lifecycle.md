# T1 source / installer lifecycle evidence

Working tree: `<repository>\.worktrees\suwayomi`, branch `codex/suwayomi-evolution`, baseline `a75a1d76f`.

## Implemented

- `DesktopSourceManager.kt`: loading remains serialized by the existing load mutex. After host registration and before publishing the loaded-package hint, replay persisted writable preferences for every source returned by the extension. Sources without saved values avoid an unnecessary IPC lookup. Host epoch changes clear cached remote definitions. Explicit missing-source recovery remains limited to one retry.
- `DesktopPreferenceStore.kt`: synchronized read-only prefix snapshot supports locating saved source preferences without mutating the settings file.
- `DesktopSourceManager.kt`: installer lifecycle hooks unload matching active hosts and invalidate local state. Direct APK probing discovers runtime source IDs; failed/replaced probes invalidate cached ownership so the old installed file remains reloadable.
- `TachiyomiExtensionConverter.kt`: process every `classes*.dex`, retain precompiled class entries, preserve assets, validate entry class and API range 1.3–1.6, validate converted package. Runtime identity replacement keeps original IDs/languages and the original factory entry class for future loads.
- `DesktopExtensionInstaller.kt`: stage and validate all bytes before replacing any installed directory; unload host handles; move old installation to backup; publish staged directory and metadata; restore backup on failure. Same-path reinstall works. Updates preserve disabled state. Successful replacement/uninstall retries locked cleanup on the next installer startup using an allowlisted cleanup queue.
- Direct APK installs without repository source metadata require an active extension host for identity discovery. Missing host causes an explicit error instead of publishing synthetic IDs into the installed list.

## Red evidence and verification

Root serialized Gradle runs confirmed the expected failures before production changes: missing asset, source preference replay assertion, same-path installer `NoSuchFileException`, unsupported API 99 acceptance, and synthetic rather than runtime direct APK source identity. Two initial expression-bodied tests were not discovered by JUnit because their inferred return type was non-Unit; both were explicitly corrected to `Unit` before the confirmed red run.

Focused classes for green verification:

- `mihon.desktop.extension.DesktopSourceManagerTest`
- `mihon.desktop.extension.DesktopSourceManagerSourcePreferenceIpcTest`
- `mihon.desktop.extension.DesktopExtensionInstallerTest`
- `mihon.desktop.extension.TachiyomiExtensionCompatibilityTest`

Preference regression also covers eight concurrent initialization requests, no duplicate replay after an additional ensure, and replay after invalidation/reload. Existing SourceManager tests cover host registry loss and exactly-one operation retry. Converter asset test uses actual ZIP roundtrip; direct identity test probes a fake host and asserts the installed manifest ID and original entry class.

## Additional verification

Shared-wrapper run session 17950 completed 36 tests: 35 passed, with only the newly added standard versionName API boundary failing. That boundary has now been implemented; final green run session 64698 succeeded (BUILD SUCCESSFUL in 49s), with all 36 focused tests passing.

A dex-writer test-only dependency generates valid primary and secondary DEX classes; the converter test asserts both translated JARs contain their expected classes. Standard APK versionName now determines the manifest API version and is checked against 1.3 through 1.6 as well as explicit metadata.

The installer lifecycle mutex serializes source initialization against install/uninstall/enable changes, preventing a source-miss retry from reopening the old JAR during replacement.

## Remaining evidence limits

- Final focused verification passed: 36 tests, zero failures/errors, shared-wrapper session 64698. Production/test compilation passed.
- Generated valid multidex translation is tested. OS-level locked-file rollback still needs representative Windows runtime evidence; same-path reinstall and existing signature/update rejection are covered.
- Installer process kill between directory publication and metadata publication is not a journaled transaction; this is distinct from ordinary caught update failures and next-start cleanup retry.
