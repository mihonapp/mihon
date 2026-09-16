# Reader toolbar contrast repair

Branch: `codex/fix-download-host-recovery`. Target installed version: 0.2.12.

The user's profile enables the default dark theme and AMOLED black surfaces.
ReaderChrome used `surface.copy(alpha = 0.96f)` for both toolbar Surfaces without
an explicit contentColor. That translucent color does not match a color-scheme
surface role, so Material falls back to the inherited LocalContentColor. The
reader destination has no parent Surface to establish that foreground, leaving
title text, action icons and page labels black. Explicitly colored chapter text
and text buttons were still readable, matching the supplied screenshot.

Both toolbar Surfaces now pair their background with the active theme's
`onSurface`. Descendant titles, icons, and page labels inherit the matching
foreground. Page rendering, filters, toolbar visibility, and theme preferences
are unchanged.

ReaderChromeContrastTest renders the actual ReaderScreen without an enclosing
Surface for light, dark, and AMOLED themes. Before the fix, all three cases
reported the inherited black title instead of the theme foreground. After the
fix, text layout foreground assertions and rendered icon-pixel checks pass.
The three-dot icon assertion accounts for its small filled area at 1x density.

The complete reader UI package passed 99 tests, zero failures/errors/skips, and
`:desktop-app:spotlessCheck` passed. Desktop-width screenshots (1024 x 720) were
inspected for AMOLED and light mode. Local render artifacts and execution logs
are under the ignored `build/reader-contrast-evidence/` directory.

Installed artifact verification will be appended after packaging and upgrade.
