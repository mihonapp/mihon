# Mihon W Desktop Library Adaptive Layout Design

**Date:** 2026-09-14  
**Status:** Approved for planning  
**Scope:** Repair the library's broken comfortable-grid proportions and make all library display modes respond predictably to desktop window width.

## Objective

Make the library readable and efficient on Windows at normal, narrow, and ultrawide window sizes. The current comfortable grid assigns a roughly 180 dp column to a horizontal card containing an 84 dp cover, a 52 dp unread rail, padding, and text. The remaining text width is too small, so titles and metadata wrap into vertical strips. The replacement must preserve the existing library data and actions while adapting layout rather than shrinking content below its usable size.

## Approaches Considered

1. Keep horizontal cards and increase their minimum width. This is the smallest change, but it produces very few columns and wastes vertical space on large libraries.
2. Use vertically composed desktop cards with a fixed readable width range and adaptive column count. This keeps covers scannable, supports many items, and scales cleanly across desktop widths. **Selected.**
3. Use a dense table as the default desktop view. This fits metadata well but weakens cover recognition and duplicates the existing list mode.

## Layout Contract

### Page structure

- Keep the page title and primary import/update actions in a compact top row.
- Keep display choice, card-size control, filtering, sorting, and multi-select in a responsive toolbar that can wrap into a second row instead of clipping.
- Keep categories in a horizontally scrollable row with category management anchored at the end when space permits.
- Use a compact search field above the content area; it remains full width of the library pane.
- Retain the existing batch action bar as an overlay with sufficient bottom content padding.

### Adaptive grid

- Derive the column count from available content width, selected card size, and spacing.
- Treat the selected size as a target rather than allowing the grid to create unusably narrow cards.
- Comfortable cards have a readable minimum width and a bounded maximum width. Wider windows add columns before stretching cards excessively.
- Compact and cover-only modes use smaller independent minimum widths but follow the same bounded adaptive behavior.
- Narrow windows reduce the column count. No mode may compress title or metadata into character-wide columns.
- Grid content fills from the leading edge and maintains consistent horizontal and vertical gaps.

### Comfortable card

- Replace the horizontal cover/text/unread-rail composition with a vertical card.
- Use a portrait cover region with a stable manga-cover aspect ratio and cropped image fit.
- Place unread count as a compact overlay badge on the cover; omit it when the count is zero.
- Put the title below the cover with two lines maximum and ellipsis.
- Put author when available, otherwise source, plus chapter count in a quiet metadata line below the title.
- Show the selection checkbox over the cover so selection mode does not change card geometry.
- Use a subtle hover/focus/selection surface treatment appropriate for mouse and keyboard interaction.

### Other modes

- Compact grid remains cover-led with a bottom title gradient and unread badge, but receives bounded adaptive sizing.
- Cover-only remains the densest mode and receives bounded adaptive sizing without adding metadata.
- List mode remains a full-width row view and is unaffected by the grid-size control.

## Sizing Model

Grid sizing will be implemented by a small pure layout function so width behavior can be tested without screenshot-only assertions. Inputs are available width, requested card width, spacing, and mode bounds. Outputs are a positive column count and effective cell width.

The function must:

- return one column for widths below one usable card plus page padding;
- increase columns monotonically as available width grows;
- never return a cell narrower than the mode's readable minimum when more than one column is used;
- avoid stretching cells past the mode's preferred maximum by adding another column when possible;
- tolerate zero, negative, and non-finite measurement inputs with a safe one-column result.

The Compose grid will use the computed fixed column count. This avoids relying on `GridCells.Adaptive` behavior alone and makes the desktop contract explicit.

## Interaction and Accessibility

- The whole card remains a single click target and retains the existing stable test tag.
- Keyboard focus must be visible; Enter/Space activation continues through button semantics.
- Selection checkboxes retain their existing test tags and do not cause layout shifts.
- Hover feedback must not be the only indication of selection or unread state.
- Text uses ellipsis rather than per-character wrapping, and card metadata has appropriate content color contrast.

## Architecture

- `LibraryScreen` remains the screen and action wiring boundary.
- A pure library-grid sizing helper owns width-to-column calculations and mode-specific bounds.
- `LibraryContent` observes available width and passes the computed fixed column count to each grid mode.
- Each card composable owns only its visual composition and callbacks; no presenter, repository, database, or import behavior changes.
- Existing display-mode and grid-size preferences remain compatible.

## Error and Boundary Handling

- Loading, error, empty-library, and empty-search states keep filling the content region.
- Missing covers continue to use the existing cover fallback.
- Very long titles, authors, source labels, and large unread counts stay bounded and cannot resize the card.
- Toolbars wrap or compact when horizontal room is limited; controls must remain reachable rather than drawing outside the window.
- Selection and batch actions continue to operate on the same manga IDs.

## Verification Strategy

Implementation follows red-green-refactor:

1. Add failing unit tests for narrow, standard, wide, ultrawide, invalid-width, and monotonic column-count cases.
2. Add failing Compose tests proving comfortable cards keep horizontal text semantics, retain stable tags/actions, and do not expose the old unread rail geometry.
3. Implement the pure sizing model and vertical comfortable card, then make the toolbar responsive.
4. Run focused library tests, the full `desktop-app` test suite, Spotless, and `git diff --check` using Corretto 23 and one Gradle worker.
5. Launch the desktop application with representative library data and capture the library at narrow, standard, and ultrawide widths to verify actual card proportions, wrapping, hover/focus, and scrolling.

## Acceptance Criteria

- The screenshot's vertical-strip title and metadata failure cannot occur at supported window widths.
- Resizing the window changes column count without requiring a restart or manual mode change.
- Comfortable cards are cover-led, readable, and visually balanced on a desktop monitor.
- Standard and ultrawide windows use available space by adding sensible columns instead of leaving most of the content region empty.
- Narrow windows reduce columns and keep every control and card usable.
- Compact, cover-only, and list modes retain their distinct density and existing actions.
- Existing library filtering, sorting, categories, imports, selection, updates, and navigation behavior remains intact.

## Out of Scope

This slice does not change library persistence, automatic update behavior, category semantics, manga detail presentation, reader behavior, or online-library membership rules.
