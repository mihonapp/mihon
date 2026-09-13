# Reader Runtime Wiring Design

## Objective

Connect the reader capabilities that already exist in `reader-core` and the Compose desktop layer to the real Mihon W reader runtime. The finished reader must match Mihon's page and chapter preloading behavior while improving desktop responsiveness, cancellation, memory safety, animated-image lifecycle, large-image quality, and diagnostics.

## Current gap

The production reader opens pages through `DesktopReaderFactory.loadFrame()`. That path creates a fresh chapter source for each frame and talks directly to `WeightedTileCache`, so it bypasses the existing `PageLoadCoordinator`. Consequently, its tested visible-load priority, single-flight behavior, prefetch window, direction-change cancellation, error memoization, and retry invalidation do not govern the actual reader.

Several connected contracts are also present without complete production wiring:

- `ReaderAction.SetVisiblePages` exists, but paged and continuous Compose viewers do not report the complete visible set to the session.
- `AnimationCoordinator` and `AnimatedPage` exist, but `DecodedReaderPage` advances animation frames with its own timer and the production call site hard-codes `foreground = true`.
- `TilePlanner` is tested, but zoomed large pages are displayed as a single downsampled bitmap instead of viewport tiles.
- `ReaderState.cacheMetrics` and the debug chrome exist, but the state is never populated from the shared cache.

## Loading architecture

Each `DesktopReaderFactory.createSession()` call will create a session-owned `PageLoadCoordinator` and a desktop content adapter. The adapter owns the active `ChapterSource` through the session lifetime and exposes full-frame, animation-frame, and region-tile loads to Compose. All visible and background loads therefore share one single-flight/cache path.

`DefaultReaderSession` remains the authority for chapter identity, navigation, progress, foreground state, and close ordering. It will call a narrow content-pipeline contract to open/close chapters, report position and visible pages, retry a page, and publish anonymous cache metrics. Source ownership stays singular: the pipeline closes its current source exactly once, and the session closes the pipeline before its scope.

## Preloading policy

Mihon's online loader preloads four following images at adjacent priority. Mihon also requests the next chapter when the reader enters the last five pages. Mihon W will preserve both behaviors:

- preload the next four page images in logical reading order;
- begin next-chapter page-list warmup within the final five pages;
- at the chapter transition boundary, preload the next chapter's first visible unit at background priority;
- never let preloading delay a visible request;
- deduplicate prefetch and visible requests through the coordinator and disk cache.

Desktop improvements are intentionally additive:

- retain one page behind the current position for quick reversal;
- use the most recent navigation direction and cancel obsolete work immediately on reversal;
- treat a dual-page spread as a visible unit while limiting forward image prefetch to four images;
- suspend new background decode work while the window is not foreground/content-visible;
- keep all decoded output inside the existing 256 MiB core budget and 96 MiB Compose bridge budget.

The adjacent-chapter warmer has a separate cancellable job and may hold only metadata plus the first visible unit. It is cancelled on chapter change, direction reversal away from the boundary, session close, or superseding warmup. Failures are silent because warmup must never turn a readable current page into an error.

## Visible pages and animation

Paged mode reports every page in the current spread. Continuous and Webtoon modes report only items intersecting the viewport. That set drives pinning and prevents redundant prefetch.

Animation timing moves to `AnimationCoordinator`. The coordinator publishes the selected `FrameId`; `AnimatedPage` only loads and swaps that requested frame. Animation pauses when the application loses foreground, the reader is hidden, or the page leaves the visible set. At most the current and replacement animation frames may be retained.

## Large-image tiling

Normal pages continue to use the full-page path. A page that would exceed the display pixel limit at the requested zoom uses `TilePlanner` to request the visible image-space rectangle plus its one-tile margin. Compose draws the returned tiles at their image coordinates. A viewport or zoom change cancels stale region loads; the previous visible tiles remain until their replacements are ready.

Prefetch stays full-page and downsampled where necessary. It does not eagerly create zoom-detail tiles.

## Metrics and error handling

The pipeline publishes `CacheMetrics` after open, visible load, prefetch reconciliation, retry, and close. `ReaderState.cacheMetrics` feeds the existing debug-only chrome without paths, titles, URLs, or page names.

Visible failures retain the existing typed reader errors and retry UI. Prefetch and next-chapter warmup failures are non-fatal. Explicit retry invalidates both decoded tiles and online disk bytes before issuing a fresh visible request.

## Verification

Implementation is test-driven. Coverage must prove:

- four pages ahead and one behind in all reading modes;
- visible requests overtake queued prefetch and duplicate network reads collapse;
- reversal, chapter transition, hidden window, and close cancel obsolete work;
- next-chapter metadata warms inside the final five pages and its first unit at the boundary;
- dual and continuous viewers report their real visible sets;
- animation pauses/resumes and retains at most two frames;
- large zoomed pages use region tiles while normal pages stay full-frame;
- cache metrics reach reader state;
- corrupt online cache retry remains a fresh fetch;
- full reader-core and desktop suites, formatting, distributable creation, packaged EXE smoke, and merge back to `main` pass.

