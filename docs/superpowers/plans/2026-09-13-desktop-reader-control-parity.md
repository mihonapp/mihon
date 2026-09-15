# Desktop Reader Control Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replicate Mihon's reader control hierarchy on Windows while adding native keyboard, wheel, pointer, and window interactions.

**Architecture:** Keep reader-core and persistence contracts unchanged. Extend the pure `ReaderInputMapper`, add a small pure overlay-visibility policy, split the oversized desktop chrome into Mihon-shaped Compose components, and keep `ReaderScreen` as the only session orchestration boundary.

**Tech Stack:** Kotlin 2.4.10, Compose Multiplatform 1.12.0, Material 3, JUnit Jupiter 6.1.3, Kotest assertions, Gradle 9.7.1, Java 23 build toolchain with Java 17 packaged runtime.

## Global Constraints

- Preserve the Android application and existing reader-core ABI.
- Preserve progress ordering, close-and-flush behavior, image ownership, cache invalidation, and chapter-transition semantics.
- Target Windows 10 22H2 and Windows 11 x64.
- Match Mihon's top bar, chapter navigator, bottom action bar, settings grouping, and 200 ms slide/150 ms fade timing.
- Translate touch-only behavior into keyboard, mouse, wheel, focus, and window semantics.
- Implement every behavior with a failing test first and serialize Gradle with `--max-workers=1`.

---

## File Structure

- Modify `desktop-app/src/main/kotlin/mihon/desktop/reader/input/ReaderInputMapper.kt`: pure keyboard, modifier, side-button, and page-action command mapping.
- Modify `desktop-app/src/test/kotlin/mihon/desktop/reader/input/ReaderInputMapperTest.kt`: input contract tests.
- Create `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderOverlayVisibility.kt`: pure chrome/cursor visibility reducer.
- Create `desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ReaderOverlayVisibilityTest.kt`: idle/reveal/control ownership tests.
- Modify `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderGesture.kt`: secondary-click and pointer-motion callbacks.
- Modify `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderScreen.kt`: wire new commands, staged overlays, and visibility state.
- Modify `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderChrome.kt`: retain public chrome entry point and compose Mihon-shaped components.
- Create `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderChapterNavigator.kt`: rounded navigator and stable page labels.
- Create `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderSettingsPanel.kt`: three-page reader settings dialog.
- Modify `desktop-app/src/main/kotlin/mihon/desktop/i18n/DesktopStrings.kt`: localized desktop reader labels.
- Modify reader Compose tests under `desktop-app/src/test/kotlin/mihon/desktop/ui/reader/`: chrome, settings, pointer, and staged Escape coverage.

### Task 1: Desktop input commands

**Interfaces:**

- Consumes: `ReaderInputContext`, `ReaderAction`, `ReadingMode`, `InputPoint`.
- Produces: `ReaderInputKey.SPACE`, `ReaderInputKey.F11`, `ReaderInputContext.shift`, `ReaderInputCommand.PageActions`, and reading-direction-aware `mapSideButton`.

- [x] **Step 1: Write failing input tests**

Add tests proving these exact mappings:

```kotlin
mapper.mapKey(ReaderInputKey.SPACE, ltr).action shouldBe
    ReaderInputCommand.Core(ReaderAction.Next)
mapper.mapKey(ReaderInputKey.SPACE, ltr.copy(shift = true)).action shouldBe
    ReaderInputCommand.Core(ReaderAction.Previous)
mapper.mapKey(ReaderInputKey.F11, ltr).action shouldBe ReaderInputCommand.Fullscreen
mapper.mapKey(ReaderInputKey.X, ltr).action shouldBe ReaderInputCommand.PageActions
mapper.mapSideButton(ReaderSideButton.BACK, rtl).action shouldBe
    ReaderInputCommand.Core(ReaderAction.Next)
```

Also assert that text/menu focus and Ctrl-modified keys remain unconsumed.

- [x] **Step 2: Run the focused test and observe RED**

Run:

```powershell
./gradlew.bat :desktop-app:test --tests '*ReaderInputMapperTest' --no-daemon --max-workers=1 --console=plain
```

Expected: compilation/test failure because `SPACE`, `F11`, `shift`, and `PageActions` do not exist and side buttons are not direction-aware.

- [x] **Step 3: Implement the minimal pure mapping**

Extend the enums/context and use one direction helper:

```kotlin
private fun directionAction(forward: Boolean, mode: ReadingMode): ReaderAction =
    when {
        !mode.isRightToLeft -> if (forward) ReaderAction.Next else ReaderAction.Previous
        forward -> ReaderAction.Previous
        else -> ReaderAction.Next
    }
```

Map Space, Shift+Space, F11, X, and side buttons without adding UI state to the mapper.

- [x] **Step 4: Run the focused test and observe GREEN**

Run the Step 2 command. Expected: `ReaderInputMapperTest` passes with zero failures.

- [x] **Step 5: Commit Task 1**

```powershell
git add -- desktop-app/src/main/kotlin/mihon/desktop/reader/input/ReaderInputMapper.kt desktop-app/src/test/kotlin/mihon/desktop/reader/input/ReaderInputMapperTest.kt
git commit -m "feat(reader): add desktop navigation shortcuts"
```

### Task 2: Pointer and overlay visibility policy

**Interfaces:**

- Consumes: monotonic timestamps plus pointer/control events from `ReaderScreen`.
- Produces: `ReaderOverlayVisibilityState`, `ReaderOverlayEvent`, and `reduceReaderOverlayVisibility(state, event)`.

- [x] **Step 1: Write the failing pure policy tests**

Cover these transitions:

```kotlin
reduceReaderOverlayVisibility(hidden, ReaderOverlayEvent.PointerAtEdge).chromeVisible shouldBe true
reduceReaderOverlayVisibility(visible, ReaderOverlayEvent.ReadingInput).cursorVisible shouldBe true
reduceReaderOverlayVisibility(activeControl, ReaderOverlayEvent.IdleTimeout).chromeVisible shouldBe true
reduceReaderOverlayVisibility(pageIdle, ReaderOverlayEvent.IdleTimeout).cursorVisible shouldBe false
```

Assert that pointer movement restores the cursor and that a control-owned pointer prevents chrome hiding.

- [x] **Step 2: Run the policy test and observe RED**

Run:

```powershell
./gradlew.bat :desktop-app:test --tests '*ReaderOverlayVisibilityTest' --no-daemon --max-workers=1 --console=plain
```

Expected: compilation failure because the policy types do not exist.

- [x] **Step 3: Implement the pure reducer**

Create immutable state/event types. The reducer must not start timers; `ReaderScreen` owns delays and dispatches `IdleTimeout`.

- [x] **Step 4: Run the policy test and observe GREEN**

Run the Step 2 command. Expected: all overlay-policy tests pass.

- [x] **Step 5: Add failing gesture wiring tests**

Extend `ReaderGestureTest`/`ReaderScreenActionsTest` to assert:

- secondary click opens `reader-page-actions-dialog`;
- moving into `reader-top-reveal` or `reader-bottom-reveal` makes chrome visible;
- Escape dismisses page actions/settings/chapter drawer before invoking window escape.

- [x] **Step 6: Run gesture tests and observe RED**

Run:

```powershell
./gradlew.bat :desktop-app:test --tests '*ReaderGestureTest' --tests '*ReaderScreenActionsTest' --no-daemon --max-workers=1 --console=plain
```

Expected: failures because secondary-click, bottom reveal, and staged overlay dismissal are not wired.

- [x] **Step 7: Wire pointer callbacks and staged Escape**

Add `onSecondaryClick`, `onPointerMove`, and edge callbacks at the Compose input boundary. Keep page selection and page-action resolution in `ReaderScreen`.

- [x] **Step 8: Run Task 2 tests and observe GREEN**

Run both Step 2 and Step 6 commands. Expected: zero failures.

- [x] **Step 9: Commit Task 2**

```powershell
git add -- desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderOverlayVisibility.kt desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderGesture.kt desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderScreen.kt desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ReaderOverlayVisibilityTest.kt desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ReaderGestureTest.kt desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ReaderScreenActionsTest.kt
git commit -m "feat(reader): add desktop pointer visibility behavior"
```

### Task 3: Mihon-shaped chrome and navigator

**Interfaces:**

- Consumes: existing `ReaderChrome` callback contract and `ReaderState`.
- Produces: `ReaderChapterNavigator` plus top/bottom/overflow chrome semantics tags.

- [x] **Step 1: Write failing Compose structure tests**

Add `ReaderChromeParityTest` assertions for:

```kotlin
onNodeWithTag("reader-top-bar").assertIsDisplayed()
onNodeWithTag("reader-chapter-navigator").assertIsDisplayed()
onNodeWithTag("reader-bottom-bar").assertIsDisplayed()
onNodeWithTag("reader-overflow").performClick()
onNodeWithTag("reader-fullscreen").assertIsDisplayed()
onNodeWithTag("reader-borderless").assertIsDisplayed()
onNodeWithTag("reader-shortcuts-btn").assertIsDisplayed()
```

Assert that fullscreen, borderless, shortcuts, zoom, and page actions are absent from the persistent top/bottom rows. Assert stable current/total page labels and RTL slider semantics.

- [x] **Step 2: Run the chrome test and observe RED**

Run:

```powershell
./gradlew.bat :desktop-app:test --tests '*ReaderChromeParityTest' --no-daemon --max-workers=1 --console=plain
```

Expected: failures because the current chrome has no Mihon component tags and exposes desktop actions persistently.

- [x] **Step 3: Extract and implement the navigator**

Create `ReaderChapterNavigator.kt` with a rounded 24 dp slider capsule, filled chapter buttons, stable page-label width, and explicit layout direction derived from `state.mode.isRightToLeft`.

- [x] **Step 4: Rebuild chrome around Mihon's hierarchy**

Use `AnimatedVisibility` with `slideInVertically/slideOutVertically(tween(200))` and `fadeIn/fadeOut(tween(150))`. Keep only back/title/bookmark/overflow in the top bar and reading mode/layout/crop/settings in the bottom bar.

- [x] **Step 5: Run chrome and existing reader UI tests**

Run:

```powershell
./gradlew.bat :desktop-app:test --tests '*ReaderChromeParityTest' --tests '*ReaderScreenTest' --tests '*ReaderScreenActionsTest' --tests '*ReaderChapterTransitionTest' --no-daemon --max-workers=1 --console=plain
```

Expected: all selected tests pass.

- [x] **Step 6: Commit Task 3**

```powershell
git add -- desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderChrome.kt desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderChapterNavigator.kt desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ReaderChromeParityTest.kt desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ReaderScreenTest.kt desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ReaderScreenActionsTest.kt
git commit -m "feat(reader): replicate Mihon reader chrome"
```

### Task 4: Three-page desktop reader settings

**Interfaces:**

- Consumes: `DesktopReaderSettings`, `DesktopReaderSettingsStore`, existing setting callbacks.
- Produces: `ReaderSettingsPanel(settings, onSave, onDismiss)` with Reading mode, General, and Color filter pages.

- [x] **Step 1: Write failing settings UI tests**

Add tests that open settings and assert tags for three tabs, switch pages, edit a draft, cancel without persistence, then save all changes atomically.

```kotlin
onNodeWithTag("reader-settings-tab-reading").assertIsDisplayed()
onNodeWithTag("reader-settings-tab-general").performClick()
onNodeWithTag("reader-setting-wheel").assertIsDisplayed()
onNodeWithTag("reader-settings-tab-filter").performClick()
onNodeWithTag("reader-setting-filter").assertIsDisplayed()
```

- [x] **Step 2: Run the settings test and observe RED**

Run:

```powershell
./gradlew.bat :desktop-app:test --tests '*DesktopReaderSettingsTransitionTest' --tests '*ReaderScreenTest' --no-daemon --max-workers=1 --console=plain
```

Expected: failures because the current dialog is a single scrolling page.

- [x] **Step 3: Implement the tabbed settings panel**

Move the existing draft controls into the three sections without changing persisted keys. Use a bounded desktop dialog width, vertical scrolling per page, and explicit Save/Reset/Cancel actions.

- [x] **Step 4: Add localized labels**

Add English, Simplified Chinese, and Traditional Chinese strings for the three tab titles, overflow actions, layout selector, and pointer shortcuts.

- [x] **Step 5: Run settings and i18n tests**

Run:

```powershell
./gradlew.bat :desktop-app:test --tests '*DesktopReaderSettingsTransitionTest' --tests '*ReaderScreenTest' --tests '*DesktopStringsTest' --no-daemon --max-workers=1 --console=plain
```

Expected: zero failures.

- [x] **Step 6: Commit Task 4**

```powershell
git add -- desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderSettingsPanel.kt desktop-app/src/main/kotlin/mihon/desktop/ui/reader/ReaderChrome.kt desktop-app/src/main/kotlin/mihon/desktop/i18n/DesktopStrings.kt desktop-app/src/test/kotlin/mihon/desktop/ui/reader/DesktopReaderSettingsTransitionTest.kt desktop-app/src/test/kotlin/mihon/desktop/ui/reader/ReaderScreenTest.kt desktop-app/src/test/kotlin/mihon/desktop/i18n/DesktopStringsTest.kt
git commit -m "feat(reader): replicate Mihon reader settings"
```

### Task 5: Full regression, runtime smoke, and delivery evidence

**Interfaces:**

- Consumes: completed Tasks 1-4.
- Produces: verified current distributable and an evidence report for this slice.

- [x] **Step 1: Run formatting and focused reader regression**

```powershell
$env:JAVA_HOME='<JDK 23 path>'
./gradlew.bat :reader-core:spotlessCheck :reader-core:test :desktop-app:spotlessCheck :desktop-app:test --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Expected: `BUILD SUCCESSFUL`, zero test failures.

- [x] **Step 2: Check patch integrity**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only intended evidence/plan changes remain.

- [x] **Step 3: Build the distributable**

```powershell
./gradlew.bat :desktop-app:createDistributable --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Expected: `desktop-app/build/compose/binaries/main/app/MihonW/MihonW.exe` exists and the build exits 0.

- [x] **Step 4: Launch packaged smoke test**

```powershell
& 'desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe' --smoke-test
```

Expected: exit code 0 with a successful smoke-test response.

- [x] **Step 5: Record evidence and commit**

Create `docs/superpowers/evidence/2026-09-13-desktop-reader-control-parity.md` with exact commands, test counts, skipped tests, artifact path, executable hash, and any remaining manual UI limitations.

```powershell
git add -- docs/superpowers/plans/2026-09-13-desktop-reader-control-parity.md docs/superpowers/evidence/2026-09-13-desktop-reader-control-parity.md
git commit -m "docs(reader): record desktop control parity evidence"
```
