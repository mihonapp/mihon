# Desktop Reader Control Parity Evidence

## Scope

- Branch: `codex/desktop-reader-control-parity`
- Implementation commit: `b04cbe839`
- Isolated worktree: `D:\my project\mihon-w\.worktrees\desktop-reader-control-parity`
- Build JDK: Amazon Corretto 23.0.2

## Automated verification

Command:

```powershell
./gradlew.bat :reader-core:test :desktop-app:test --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Result: `BUILD SUCCESSFUL in 1m 5s`.

- Test suites: 143
- Tests: 673
- Failures: 0
- Errors: 0
- Skipped: 7

Focused Compose regression also passed for reader chrome, settings, actions, chapter transitions, and localization.

`git diff --check` completed without whitespace errors before the implementation commit.

## Formatting gate

`reader-core:spotlessCheck` and `desktop-app:spotlessCheck` were executed. The gate is not green at the branch baseline: Spotless reports pre-existing formatting violations in ten files outside this reader slice, including `OnlineChapterSource.kt`, `DesktopImageLoader.kt`, `SourcePreferencesScreen.kt`, and `MangaBottomActionMenu.kt`. Validation-only formatting changes to those files were removed so this branch does not absorb unrelated rewrites.

## Distributable and runtime smoke

Command:

```powershell
./gradlew.bat :desktop-app:createDistributable --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Result: `BUILD SUCCESSFUL in 21s`.

- Executable: `desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe`
- Size: 446,976 bytes
- SHA-256: `AAB9E39BE3CA879AAB812FE0DBA39BD9AA6DD28746B40BFCCAA10CAAE6FB2772`
- Packaged smoke: `MihonW.exe --smoke-test`
- Smoke exit code: 0

## Remaining manual limitation

Compose structure, semantics, persistence, input mapping, packaged startup, and automated interaction paths are covered. Physical multi-monitor DPI behavior and subjective animation smoothness still require a manual Windows UI pass; they are not claimed by the headless test suite.
