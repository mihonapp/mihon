# Mihon Desktop Reader Control Parity Design

**Date:** 2026-09-13  
**Status:** Approved for planning  
**Scope:** Continue the existing Mihon W reader by replicating Mihon's reader controls while translating touch-first behavior into native desktop interaction.

## Objective

Bring the Windows reader's visible control layer into close structural and visual parity with Android Mihon without copying mobile-only interaction assumptions. This slice covers reader chrome, chapter navigation, settings presentation, pointer behavior, and keyboard/mouse input. It does not replace the existing reader session, decoding, cache, chapter-transition, progress, or page-action contracts.

## Reference Contract

The Android implementation remains the visual and behavioral source of truth for:

- `ReaderAppBars` animation timing and surface treatment;
- `ReaderTopBar` title, chapter subtitle, bookmark, and overflow actions;
- `ChapterNavigator` page labels, slider direction, and chapter-boundary actions;
- `ReaderBottomBar` reading-mode, orientation/layout, crop, and settings actions;
- `ReaderSettingsDialog` reading-mode, general, and color-filter organization.

Desktop adaptations are intentional where Android relies on touch, device rotation, Android system bars, haptics, or share intents.

## Control Layout

### Top app bar

The top bar uses Mihon's elevated translucent surface, title/subtitle hierarchy, back action, bookmark action, and overflow action grouping. It enters from the top over 200 ms with a 150 ms fade and exits using the inverse animation.

Desktop-only commands such as borderless mode, shortcut help, fullscreen, and page actions live in the overflow menu instead of occupying persistent toolbar width. This preserves Mihon's visual density and prevents the reader from looking like a generic desktop toolbar.

### Chapter navigator

Paged modes use a centered horizontal navigator based on Mihon's rounded floating slider:

- stable-width current-page and total-page labels;
- reading-direction-aware slider direction;
- filled previous/next chapter buttons outside the slider capsule;
- direct slider seeking without dispatching intermediate chapter changes.

Vertical and Webtoon modes keep normal wheel scrolling as the primary desktop operation. Their chapter navigator remains horizontal so it does not compete with the vertical scrollbar or consume reading width.

### Bottom action bar

The bottom bar uses four primary icon actions matching Mihon: reading mode, layout/direction, crop borders, and settings. Layout/direction replaces Android's physical orientation picker and exposes only states meaningful on desktop. Zoom controls remain accessible through the overflow menu and keyboard/mouse input rather than permanently expanding the bottom bar.

### Reader settings

Settings use three Mihon-aligned pages:

1. Reading mode: reading direction, scale, dual-page cover offset, Webtoon width/padding, and crop behavior.
2. General: click regions, wheel behavior, chapter-transition skip rules, and desktop window/input options.
3. Color filter: background color and image filter controls with the reader visible behind the dialog where supported.

The dialog has a bounded but resizable desktop width, scrollable pages, predictable focus order, keyboard activation, and Escape dismissal. Existing persisted setting keys remain compatible.

## Desktop Input Contract

- Plain wheel scrolls Vertical/Webtoon content and flips pages in paged modes when page-navigation behavior is selected.
- `Ctrl+wheel` zooms around the pointer position.
- Double primary click toggles fitted and configured zoom.
- Secondary click opens the current page's actions.
- Mouse back/forward side buttons select previous/next according to visual reading direction.
- `Space` and `Page Down` advance; `Shift+Space` and `Page Up` go back.
- Arrow keys and `A`/`D` retain direction-aware navigation.
- `Home` and `End` seek to chapter boundaries.
- `F11` toggles fullscreen; the existing `F` shortcut remains accepted for compatibility.
- Escape first closes menus/dialogs, then leaves fullscreen or borderless mode, then closes the reader after best-effort progress flush.

Input is ignored when a text field, menu, or dialog owns focus. Repeated wheel events remain rate-limited in paged modes.

## Pointer and Visibility Behavior

Reader controls appear when the pointer reaches the top or bottom reveal zones or when the center click action requests chrome. After reading input settles, controls hide using the existing delay. While controls are hidden and the pointer is over page content, the cursor hides after an idle delay and immediately returns on pointer movement.

Moving the pointer into an active control, dialog, drawer, or page-action surface cancels auto-hide. A press that began while controls were hidden cannot accidentally activate a newly revealed control.

## Architecture

- `ReaderInputMapper` remains the platform-neutral desktop command mapper and gains explicit modifiers and desktop mouse commands.
- `ReaderScreen` remains the orchestration boundary for session actions, chapter transitions, close/flush, and overlay state.
- `ReaderChrome` is decomposed into top bar, chapter navigator, bottom bar, and overflow components. Each component receives state and callbacks only; it does not own reader-session behavior.
- Reader settings UI is separated from chrome and edits a local draft before atomically saving through `DesktopReaderSettingsStore`.
- Existing `ReaderPageActionHandler`, `ReaderChapterDrawer`, `ReaderGesturePolicy`, and reader-core contracts are reused.

No reader-core ABI, database schema, progress ordering, extension-host protocol, or image ownership rule changes in this slice.

## Error and Boundary Handling

- Disabled chapter buttons remain visible and non-interactive when no adjacent chapter exists.
- Empty or single-page chapters avoid invalid slider ranges.
- Page actions are disabled until a current page exists.
- Settings dismissal discards an unsaved draft; Save applies all changes together.
- Input mapping rejects non-finite wheel/zoom data and does not consume unrelated shortcuts.
- Reader close continues even if final progress persistence fails, matching the existing best-effort navigation rule.

## Verification Strategy

Implementation follows red-green-refactor:

1. Add failing unit tests for Space/Shift+Space, F11, side-button direction, secondary-click actions, modifier focus guards, and pointer visibility state.
2. Add failing Compose tests for Mihon-shaped chrome structure, overflow placement, page labels, RTL slider semantics, three settings pages, reveal zones, and staged Escape behavior.
3. Implement the minimum production changes required for each failing test.
4. Run focused reader input/UI suites, all `desktop-app` tests, `reader-core` tests, Spotless, and `git diff --check` with serialized Gradle execution.
5. Build and launch the current distributable for an interactive mouse/keyboard smoke test. Packaging artifacts are rebuilt only after the slice passes source and runtime checks.

## Acceptance Criteria

- The visible chrome follows Mihon's top bar, floating navigator, and four-action bottom bar hierarchy.
- Persistent desktop-only actions no longer overcrowd the top or bottom bars.
- Paged, RTL, dual-page, Vertical, and Webtoon modes retain correct page/chapter direction.
- Mouse, wheel, keyboard, focus, reveal, and staged Escape contracts behave as specified.
- Existing progress, retry, chapter transition, image ownership, and page-action tests remain green.
- The packaged desktop reader can complete open, navigate, zoom, open page actions, change settings, enter/leave fullscreen, and close without losing progress.

## Deferred Reader Parity

This slice does not claim the whole reader is finished. Later slices may cover screenshot-baseline tuning across display scales, color-filter sliders beyond the current filter model, optional navigation-region teaching overlays, performance telemetry, and broad source-by-source interactive acceptance.
