# Panel-to-Panel Reader

## Overview

Add a new reading mode, similar to GlobalComix's / comiXology's Guided View,
that navigates through a manga page one panel at a time: tapping/swiping
zooms and pans to the next panel in reading order instead of immediately
flipping to the next page.

Unlike comiXology, sources here never supply publisher-authored panel
coordinates — pages arrive as flat images only. Panel boundaries must
therefore be detected on-device, from the decoded page bitmap, the first
time a page is viewed in this mode, and cached for reuse.

## Goals

- A new `ReadingMode.PANEL_BY_PANEL` entry, selectable the same way as
  Webtoon/LTR/RTL/Vertical today.
- On-device panel boundary detection with no bundled ML model and no OpenCV
  dependency.
- Guided navigation (tap zones + swipe) that pans/zooms panel-to-panel,
  falling through to the normal page flip at the start/end of a page.
- Wide/spread panels get multiple navigation stops (dialogue regions, then a
  full reveal) instead of one giant zoomed-out stop.
- Respects the manga's actual reading direction (RTL vs LTR) for both panel
  order and sub-stop order.
- Manual escape hatch: pinch-to-zoom drops into free pan/zoom; a following
  tap re-syncs to the nearest panel and resumes guided navigation.
- Detected panels are cached per page so re-reading a chapter doesn't
  re-run detection.

## Non-goals

- `WEBTOON` / `CONTINUOUS_VERTICAL` modes are out of scope for v1 — this
  mode is built on `PagerViewer`, not the continuous-scroll webtoon viewer.
- No cross-file page stitching: a "spread" is only recognized when it
  already exists as one wide image file (the same case the existing
  dual-page-split feature detects via `ImageUtil.isWideImage`). Two
  separate page files are never joined into one canvas.
- No manual panel-editing UI (correcting bad detections) in v1.
- No publisher/source-provided panel metadata support (none of our sources
  provide it).

## Architecture

The dual-page-split feature already contains the core mechanism this
feature needs: `ReaderPageImageView` (a `SubsamplingScaleImageView`
subclass) is driven via `animateScaleAndCenter(scale, PointF)` to pan
between the left/right halves of a wide image before the containing
`PagerViewer` flips to the next `ViewPager` page — see `canPanRight()` /
`panRight()` in `ReaderPageImageView.kt` and the check in
`PagerViewer.moveRight()` / `moveLeft()` (`PagerViewer.kt:333-356`).

Panel-by-panel generalizes this exact pattern from "2 static halves" to "N
detected panel stops," rather than introducing a new viewer/gesture stack:

- New `PanelByPanelViewer`, a `PagerViewer` subclass alongside
  `L2RPagerViewer` / `R2LPagerViewer`, reusing all existing `ViewPager`,
  chapter-transition, and preload infrastructure.
- `PagerPageHolder` / `ReaderPageImageView`'s pan logic generalizes from
  the hardcoded 2-half case to an ordered list of panel stops.
- A new `PanelDetector` component computes panel boundaries from the
  decoded page bitmap.

## Components

### Panel detection (boundary finding)

Connected-component analysis, the technique the open-source Kumiko
manga-panel-extraction project uses, run in pure Kotlin over the decoded
bitmap (downsampled first for speed):

1. Downscale the page bitmap to a fixed max dimension for detection
   (full-resolution image is still used for display).
2. Binarize (threshold to ink/background).
3. Flood-fill / union-find over ink pixels to find connected components.
4. Each component above a minimum size threshold becomes a candidate
   panel; its bounding box (in original-image coordinates) is the panel
   rect.

This handles the cases discussed during design:

- **Gutter-separated panels** → naturally separate components; no need for
  the gutter to be blank on all sides.
- **Panels sharing a drawn border, no gutter** → the border line blocks the
  flood fill, so they still separate correctly without a trained model or
  OpenCV.
- **True bleeding/overlapping art, no border at all** → the flood fill
  merges them into a single, larger component/panel. Safe fallback: never
  a wrong cut through the art.

Detection runs off the UI thread the first time a page is decoded for this
mode, reusing the bitmap already decoded for display (no extra image
fetch). It has a size/time budget; if it's exceeded, abort and fall back to
treating the page as a single panel rather than jank the UI.

### Wide/spread panel sub-stops

A candidate panel whose component is very wide (using the same wide-image
threshold as the existing dual-page-split `ImageUtil.isWideImage` check)
gets multiple navigation stops instead of one:

1. Run on-device OCR (ML Kit Text Recognition) over the panel region to get
   text-block bounding boxes.
2. Cluster nearby text blocks into 2-4 "dialogue regions," ordered
   according to the manga's reading direction.
3. Emit one stop per region (zoomed/centered on it), then a final stop
   showing the whole panel.
4. If OCR finds no text, or the ML Kit model isn't available/fails, skip
   straight to 2-3 evenly-spaced geometric stops across the panel, then the
   full reveal — navigation is never blocked by OCR being unavailable.

### Reading order

Panels are ordered row-major, top-to-bottom; within a row, left-to-right or
right-to-left according to the manga's actual reading direction (the same
direction driving `ReadingMode.RIGHT_TO_LEFT` / `LEFT_TO_RIGHT`). Sub-stops
within a wide panel are ordered the same way. This is a property of the
manga's configured reading direction, not a hardcoded default.

### Data model & caching

One new SQLDelight table, one row per page:

```
reader_panel_cache(
  manga_id, chapter_id, page_index,
  image_hash,     -- fingerprint of the decoded bitmap; invalidates the row
                  -- if a source ever serves a different image at the same URL
  panels_json,    -- ordered List<Panel{ rect, subStops: List<Rect> }>
  detected_at
)
```

Detection results are read from cache on every view after the first;
`image_hash` guards against stale cached panels if source-served image
bytes change without the page URL changing.

### Navigation integration

- Tap zones and swipe reuse the existing `NavigationRegion` handling in
  `PagerViewer` — the same integration point `moveRight()` / `moveLeft()`
  already use for dual-page-split panning (`config.navigateToPan`), just
  fed panel stops instead of static halves.
- `canPanToNext()` / `panToNext()` (generalized from today's
  `canPanRight()` / `panRight()`) walk the ordered panel/sub-stop list,
  driving `animateScaleAndCenter()` on `ReaderPageImageView`.
- Reaching the last stop on a page and advancing flips to the next
  `ViewPager` page (landing on its first stop); reaching the first stop and
  going back flips to the previous page's last stop — same page-flip
  integration the existing pan mechanism already has.
- Pinch-to-zoom escapes to free pan/zoom, native to the underlying
  `SubsamplingScaleImageView`. A following tap re-syncs to the nearest
  panel stop and resumes guided navigation.

### Fallback & error handling

- No panels detected, or detection fails/times out → single synthetic
  panel covering the whole page; navigation behavior stays consistent
  (one page = one stop, equivalent to a normal page turn).
- OCR unavailable/fails on a wide panel → fixed geometric subdivision
  instead of content-aware stops.
- Detection budget exceeded → abort, single-panel fallback.

## Open risks

- Connected-component analysis performance on very large/high-res pages —
  mitigated by downsampling before detection, but needs profiling on
  low-end devices.
- ML Kit Text Recognition adds a new dependency (on-device model,
  downloaded via Google Play Services or bundled unbundled model) —
  acceptable per this design but worth confirming APK size impact.
- Heuristic detection will sometimes produce wrong panel counts on unusual
  layouts (diagonal panels, insets); the merge-on-uncertainty fallback
  keeps failures safe (bigger panel) rather than wrong (bad cut), but
  won't be perfect. No manual correction UI in v1.
