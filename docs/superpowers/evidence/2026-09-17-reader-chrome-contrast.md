# Reader toolbar contrast repair

Branch: `codex/fix-download-host-recovery`. Verified installed version: 0.2.12.

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

## Installed verification

`:desktop-app:packageMsi` and `verifyCleanDistribution` passed. The MSI at
`desktop-app/build/compose/binaries/main/msi/mihondesk-0.2.12.msi` has SHA-256
`EDF02B3EABB62080CD8297BAE406F8B236D74DDA1F3B953D7B5D7E08539A3FDF`.
Windows Installer returned 0 for the upgrade. Before restarting the application,
the profile preferences, download queue, and database hashes all matched their
pre-upgrade backups.

The installed desktop JAR at `C:\Users\18734\AppData\Local\mihondesk\app`
matches the packaged JAR, SHA-256
`340C4E84BCB832E6110295B7C07D3B8B5F1416FEE91D098B33FFA1998A3ACBFA`.
Embedded build info reports 0.2.12, revision
`234e30052179be409bf5a7c1c91a1dccd144cb80`, dirty=false.

The three contrast tests were repeated with installed JARs first on the test
runtime classpath and the native-library path from the packaged launcher. Each
test asserted that ReaderScreen was loaded from the installation directory;
all three passed. Their installed AMOLED, dark, and light renderings are under
`build/reader-contrast-evidence/installed/`. The installed AMOLED rendering was
also inspected visually. The temporary Gradle init script skips included builds
and only adjusts the desktop test runtime. This check renders the actual
installed reader UI with synthetic pages; it does not modify the user's library.

The installed EXE was restarted after verification. Local installer/test logs
and pre-upgrade profile backups remain under `build/reader-contrast-evidence/`.
