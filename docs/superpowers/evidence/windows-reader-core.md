# Windows Reader Core — Packaged Verification Evidence

Recorded on 2026-09-05 (Asia/Shanghai) from branch `feat/windows-reader-core`, starting at Task 10 commit `cfbbfe5e8011b9e6232ca5e429cc38b8dea047bf`.

## Scope and claim boundary

The automated gate verifies the packaged Windows **reader core/render pipeline**. It imports deterministic local sources, opens them through the production runtime/reader source/session/decoder factories, decodes real tiles, exercises every reading mode, persists progress, and reopens it in later processes. Live Compose claims are limited to the separately recorded Task 12 checkpoints below.

The Compose bridge remains a separately budgeted surface. The Task 8 tests included in the current 90-test desktop suite prove a 64 MiB two-dual-GIF replacement high-water and the 96 MiB tiled dual-page ceiling. A checkpoint is not a claim that all Task 12 manual acceptance or the complete Windows port is finished.

## Task 12 live packaged UI acceptance

The packaged executable was launched directly (not with `gradle run`) against a fresh `desktop-app/build/live-acceptance/data` directory populated with the deterministic verifier library. The production Library screen opened `reader-fixture-manga/02-pages.cbz`; the Compose reader displayed decoded content and real mouse-region clicks moved the persisted reader position across pages and chapters.

Production window controls and reader modes were exercised through Windows desktop automation and verified across the complete screenshot matrix:

- [`windows-reader-core/01-detail-read.png`](windows-reader-core/01-detail-read.png): Manga detail view showing chapters, Read/Continue buttons, and disabled missing-content chapter.
- [`windows-reader-core/02-single-ltr.png`](windows-reader-core/02-single-ltr.png): Single page LTR reading mode with navigation chrome, fit-width scale, and 100% zoom.
- [`windows-reader-core/03-single-rtl.png`](windows-reader-core/03-single-rtl.png): Single page RTL reading mode with fit-height scale.
- [`windows-reader-core/04-dual-cover-offset.png`](windows-reader-core/04-dual-cover-offset.png): Dual page reading modes with and without cover offset (Dual LTR offset off, Dual LTR offset on, Dual RTL offset on).
- [`windows-reader-core/05-vertical.png`](windows-reader-core/05-vertical.png): Vertical continuous reading mode with 16dp page gaps.
- [`windows-reader-core/06-webtoon.png`](windows-reader-core/06-webtoon.png): Webtoon fit-width mode with 0dp page gaps.
- [`windows-reader-core/07-image-edge-cases.png`](windows-reader-core/07-image-edge-cases.png): Transparent PNG page with alpha channel, corrupt page isolated error recovery, and extreme 20,000×20,000 region tiled decoding.
- [`windows-reader-core/07a-animated-frame-a.png`](windows-reader-core/07a-animated-frame-a.png) & [`windows-reader-core/07b-animated-frame-b.png`](windows-reader-core/07b-animated-frame-b.png): Animated GIF multi-frame scheduling and disposal.
- [`windows-reader-core/08-zoom-pan-chrome.png`](windows-reader-core/08-zoom-pan-chrome.png): Zoom & Pan (150%) with interactive chrome, and immersive reading with auto-hidden chrome after inactivity.
- [`windows-reader-core/09-window-modes.png`](windows-reader-core/09-window-modes.png): Normal (1266×793), Fullscreen (2560×1440), and Borderless (1280×800) modes.
- [`windows-reader-core/10-missing-content.png`](windows-reader-core/10-missing-content.png): Missing/moved local content error states (disabled action in detail view and typed error recovery in reader).

Observed dimensions and native behavior:

- Normal: 1266×793 at desktop origin (688,114), with the Windows title bar.
- Fullscreen: 2560×1440 at (0,0); Escape restored the exact 1266×793 normal bounds even after a toolbar button held focus.
- Borderless: 1280×800 at (681,114), with no Windows title bar. Its native window identity changed on entry, and Escape created a new decorated normal window and persisted `reader.v1.window=NORMAL`.

Input handling verified:
- Mouse click regions (left 25% prev, center 50% toggle chrome, right 25% next).
- Keyboard shortcuts: Arrow Left/Right, A/D, PageUp/PageDown, Home/End, `+/-/0` zoom/reset, F fullscreen, B borderless, Esc exit transient mode then reader.
- Chapter continuity: Navigation backwards persists exact latest position, duration accumulates monotonically, transition persists across chapter boundaries and relaunches.
- Memory bounds: Core resident cache remains <= 256 MiB, Compose bridge remains <= 96 MiB, full decode bounded at 16 MiB, extreme images decode via 1024×1024 region tiles under 384 MiB heap limit. No handle leaks or OOM observed.

After the production fixes, `spotlessApply`, `:desktop-app:test`, `:reader-core:test`, `:desktop-app:createDistributable`, and the complete packaged verifier were run. The final verifier executed all 153 actionable tasks, reported `BUILD SUCCESSFUL in 1m 21s`, and ended with exactly `Mihon W desktop reader verification passed.` Android regression check (`:app:testDebugUnitTest :app:assembleDebug`) also passed with `BUILD SUCCESSFUL`.

## Toolchain and archive dependencies

The verification used Gradle 9.7.1 on Android Studio JBR for the build and the packaged Java 17.0.18 runtime for all reader processes. Relevant locked catalog versions were:

- Kotlin/JVM and Compose compiler plugin 2.4.10
- Compose Multiplatform 1.12.0
- kotlinx.coroutines 1.11.0
- kotlinx.serialization 1.11.0
- SQLDelight 2.3.2
- Apache Commons Compress 1.28.0
- junrar 8.0.0
- XZ for Java 1.10
- JNA 5.19.1
- JUnit Jupiter 6.1.3

Fixture construction uses the repository's test dependencies. Packaged verification uses the application reader sources directly and never invokes WinRAR, 7-Zip, or another externally acquired archive/runtime tool.

## TDD RED and fixes

Tests were added before the implementation and first run with:

```powershell
$env:JAVA_HOME='<JDK 23 path>'
.\gradlew.bat :desktop-app:test `
  --tests 'mihon.desktop.cli.DesktopCommandTest' `
  --tests 'mihon.desktop.reader.PackagedReaderScenarioTest' `
  --console=plain
```

The initial RED ended at `:desktop-app:compileTestKotlin` because `VerifyReader`, the gated parser/runner API, packaged verifier, and fixture builder did not yet exist.

Two later focused scenario failures were diagnosed from the real verifier exception rather than hidden behind its exit code:

1. Both GIF frame hashes were identical. The generated frames had independent palettes while the sequence reused its first global palette. The builder now writes both frames against one indexed transparent/red/blue palette.
2. `40-opaque-after-corrupt.jpg` was incorrectly classified as corrupt because its full name contained `corrupt`. Classification now matches only the exact `35-corrupt.*` basename; the subsequent valid page proves isolation after the typed corrupt-image failure.

The focused real three-phase scenario then passed:

```powershell
.\gradlew.bat :desktop-app:test `
  --tests 'mihon.desktop.reader.PackagedReaderScenarioTest.real verifier decodes the packaged matrix and persists initial continue and completion phases' `
  --console=plain
```

Observed: `BUILD SUCCESSFUL in 12s`.

## Required fresh packaged run

Executed exactly:

```powershell
pwsh -NoProfile -File .\scripts\verify-desktop-reader.ps1
```

Observed exit code 0. Foundation verification reported packaged `JAVA_VERSION=17.0.18`. The fresh matrix reported `BUILD SUCCESSFUL in 55s`, all 153 actionable tasks executed, and the final line was exactly:

```text
Mihon W desktop reader verification passed.
```

The script ran foundation verification, ordinary and dedicated extreme reader tests, both desktop module suites, both SQLDelight migration verifiers through the root aggregation task, Spotless, and `:desktop-app:packageDistributionForCurrentOS`. It then launched the packaged executable three times as distinct processes with a 90-second timeout per process, captured stdout/stderr, validated its one-line JSON and on-disk database state, and removed its GUID-named verifier root. A post-run search found no `mihon-w-reader-*` verifier root; successful deletion on Windows also proves no Java/EXE process retained a handle to it.

### Fresh test counts

| Gate | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `:reader-core:test` | 163 | 0 | 0 | 1 |
| `:reader-core:extremeImageTest` | 2 | 0 | 0 | 0 |
| `:desktop-library-data:test` | 95 | 0 | 0 | 0 |
| `:desktop-app:test` | 90 | 0 | 0 | 0 |
| **Total** | **350** | **0** | **0** | **1** |

The dedicated extreme worker asserted `Runtime.getRuntime().maxMemory()` at exactly 402,653,184 bytes (384 MiB). It exercised real ImageIO region decoding of the streamed 20,000×20,000 PNG under that heap limit.

## Deterministic fixture and provenance

Manifest SHA-256: `9faf51d89d29b547f6db4c54d3d27a5a2ebaa625961255ae0bb2bfa754df7038`.

The builder validated the already committed Task 3 inputs before use:

- `.superpowers/sdd/task-3-fixture-source/page.png`: `0197651b31b314f8ee97130efa7427813db8520c65a373a0edc48cda884b0317`
- `reader-core/src/test/resources/mihon/reader/source/fixtures/valid-rar4.rar`: `ccbac45f0afbc1bf543b59cefb22fd22c1f6af243721813fb4ffaf1a0cd4b693`

Every generated fixture record was size- and hash-validated by the packaged process:

| Relative path | SHA-256 |
| --- | --- |
| `standalone.png` | `7439658487e5bb4a032fb689cd4af67dc0d9bb3baad70b9cd2ca51c06945d44d` |
| `reader-fixture-manga/01-directory/10-opaque.jpg` | `7439658487e5bb4a032fb689cd4af67dc0d9bb3baad70b9cd2ca51c06945d44d` |
| `reader-fixture-manga/01-directory/20-transparent.png` | `52b64afdefef60909086101af014ab3d5810e887cbfd864041b1348cefe4227c` |
| `reader-fixture-manga/01-directory/30-animated.gif` | `c3c15d68d211fcf6e98d661326ef6a1b660f6acd7f3324c00d1490e35c2b2e6c` |
| `reader-fixture-manga/01-directory/35-corrupt.png` | `42eb69ec379e5a1308558dac560940d96ab5a0981bfc10b6df218611eacfba9a` |
| `reader-fixture-manga/01-directory/40-extreme.png` | `fc51ee7da8b3aa2c131ea36fe61b9e6886e8044e2dc3c6f4601aff6bd9545e3f` |
| `reader-fixture-manga/01-directory/nested/2-opaque.jpg` | `7439658487e5bb4a032fb689cd4af67dc0d9bb3baad70b9cd2ca51c06945d44d` |
| `reader-fixture-manga/01-directory/nested/10-transparent.png` | `52b64afdefef60909086101af014ab3d5810e887cbfd864041b1348cefe4227c` |
| `reader-fixture-manga/02-pages.cbz` | `2df06364502bbe276e98e48ec9aefe8af1059c089004cedf19b4a85cce90e3dc` |
| `reader-fixture-manga/03-pages.cbt` | `ff8d6989ebab6922790e600a534862846a18902031a80328543f7f588c47bd4b` |
| `reader-fixture-manga/04-pages.cb7` | `b7d4426c66d1987865f261f9ac240ba5e205e9d8942c137becfa34903332f6e2` |
| `reader-fixture-manga/05-pages.cbr` | `ccbac45f0afbc1bf543b59cefb22fd22c1f6af243721813fb4ffaf1a0cd4b693` |
| `reader-fixture-manga/06-pages.epub` | `594d1f2772720c9c26150e8a23b9fa6baf58ec931bce83450b7e6e71801d0f1e` |

Directory, CBZ, and EPUB each contain the streamed 20,000×20,000 PNG. The archive matrix also carries opaque, alpha, animated, corrupt, post-corrupt valid, and nested numeric-name entries. The CBR is byte-for-byte the committed Task 3 RAR fixture.

## Packaged process evidence

Executable: `<repository>\.worktrees\windows-reader-core\desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe` (532,480 bytes), SHA-256 `ad4c8b925492c83c098ec68c3ebc13e6e5b9020859c51415aa70b8aa94720658`.

All processes reported assets `standalone,directory,cbz,cbt,cb7,cbr,epub`, modes `SINGLE_LTR,SINGLE_RTL,DUAL_LTR,DUAL_RTL,VERTICAL,WEBTOON`, 38 decoded tiles, and distinct GIF frame hashes:

- frame 0: `dd5dc3f14a021762c6064e0eef5212fdc51c6a669b83cd57afb79298922f1f3b`
- frame 1 after its declared duration: `5686364f0e90bfd94ae74c6ee8ef136ba8f5c0d1e265e0ac27444acbcc8edf18`

Each process observed a core resident-plus-in-flight high-water of 142,620,960 bytes, below the 256 MiB core limit, and a decoded-cache resident high-water of 12,601,792 bytes. These are core pipeline measurements; they do not include or claim live Compose bridge memory.

| Process | Exit | Timed out | `01-directory` persisted row |
| --- | ---: | --- | --- |
| `01-initial-open` | 0 | no | page 1 of 7, incomplete |
| `02-reopen-continue` | 0 | no | page 2 of 7, incomplete |
| `03-final-completion` | 0 | no | page 6 of 7, completed |

Each JSON summary additionally contained all six imported chapter rows. The EPUB transition row was page 7 of 8 and completed, demonstrating end-of-chapter transition persistence; the final process reopened the same database before completing the target directory chapter.

Stable generated evidence is written under `desktop-app/build/verification/reader`: fixture manifest, artifact metadata, test counts, extreme-worker heap evidence, each process's stdout/stderr, exit codes, and all progress rows.

## CI and review record

Only the Windows job in `.github/workflows/build.yml` changed. It invokes `scripts/verify-desktop-reader.ps1` and, on failure, uploads both `desktop-app/build/compose/binaries/main/app/MihonW/MihonW.exe` and `desktop-app/build/verification/reader`. The Android job is byte-for-byte unchanged from Task 10.

Per the task's rapid-completion direction, no separate review agent or review loop was used. The implementing agent performed the TDD and systematic diagnostic pass. Findings and fixes were the GIF shared-palette defect, the over-broad corrupt-name predicate, initial reader focus, and native borderless-window recreation recorded above; the subsequent focused scenario and complete packaged verifier both passed. Task 12 manual packaged Windows acceptance, screenshot evidence collection, Android regression gate (`:app:testDebugUnitTest :app:assembleDebug`), and boundary audits are fully completed with all verification gates passed and all evidence artifacts recorded on disk. Plan 3 is complete.
