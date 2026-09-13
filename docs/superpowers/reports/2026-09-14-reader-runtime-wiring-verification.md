# Reader Runtime Wiring Verification

Date: 2026-09-14 (Asia/Shanghai)

## Provenance

- Branch: `codex/reader-runtime-wiring`
- Verified HEAD: `dfebd909481e4f44a8da1d477fe27f182ad2ef05`
- Merge base: `main@8e19e5b48875752850ba45072d215f1828fce364`
- Isolated worktree: `D:\my project\mihon-w\.worktrees\reader-runtime-wiring`

## Runtime coverage

- Mihon-equivalent four-image forward prefetch plus one direction-aware behind image.
- Actual single, dual, vertical, and Webtoon visibility reporting into the session pipeline.
- Final-five-page adjacent chapter enumeration and boundary first-page warmup.
- Shared bounded coordinator/cache for production display, prefetch, retry, and diagnostics.
- Core-selected animated frame playback with foreground and visibility cancellation.
- Large-image sampled fallback plus viewport region tiles with a one-tile margin and stale-region release.
- Desktop mouse wheel, keyboard, pinch/pan, secondary click, and back/forward side-button input wiring.
- Exclusive 7z decoder allowance handoff so four-page prefetch waits instead of failing the visible session.

## Automated verification

Command:

```powershell
.\gradlew.bat :reader-core:spotlessCheck :reader-core:test :desktop-app:spotlessCheck :desktop-app:test --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Result: `BUILD SUCCESSFUL in 2m 46s`.

- `reader-core`: 177 tests, 0 failures, 0 errors, 1 environment-gated skip.
- `desktop-app`: 510 tests, 0 failures, 0 errors, 6 environment-gated skips.
- Total: 687 tests discovered; 680 executed successfully; 7 skipped; 0 failed.
- Both module Spotless checks passed.

The preloading increase exposed a real 7z resource race during the full packaged reader scenario.
A new concurrent-open regression test first reproduced the failure. The source now serializes its
exclusive 128 MiB decoder allowance, and the original `PackagedReaderScenarioTest` followed by
`PackagedCodecPageDecoderTest` passed without cross-test exceptions.

## Distributable evidence

Command: `:desktop-app:createDistributable`

- Result: `BUILD SUCCESSFUL in 35s`.
- App image: `desktop-app\build\compose\binaries\main\app\MihonW`
- Launcher: `desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe`
- Launcher size: 446,976 bytes.
- App-image size: 258,092,263 bytes.
- Launcher SHA-256: `AAB9E39BE3CA879AAB812FE0DBA39BD9AA6DD28746B40BFCCAA10CAAE6FB2772`.
- Bundled runtime: Java 17.0.18 (`runtime\release`).
- Packaged launcher smoke: `MihonW.exe --smoke-test`, exit code `0`.

## Remaining gated checks

The seven skipped tests require opt-in live-network state or a separately supplied installed-package
fixture. They are unchanged permission/environment gates, not functional failures. The built app-image
launcher itself was exercised directly and returned zero.
