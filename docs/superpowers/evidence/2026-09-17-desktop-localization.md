# Desktop Chinese localization pass

Branch: `codex/fix-download-host-recovery`. Target version: 0.2.13.

## Findings and changes

The app already selected Simplified Chinese correctly, but many later desktop
features bypassed DesktopStrings with literal English labels and messages.
The affected surfaces included reader actions/shortcuts/chapter transitions,
chapter settings and translation-group filters, duplicate detection, download
progress, reading duration, extension trust, app lock settings and its lock
screen, and the upcoming calendar. Native file chooser titles also bypassed the
app language. Calendar dates followed the OS locale instead of the selected
application language.

App-owned missing labels now use a typed UiText catalog with English, Simplified
Chinese, and Traditional Chinese values, resolved from the active DesktopStrings
instance. Existing string properties are reused for common actions. Dynamic
templates preserve values without translating source titles, chapter names,
translation-group names, or diagnostic payloads. Dialog handlers capture the
selected language; reader image-saving handlers are refreshed when it changes.
Calendar month/day labels follow the selected language, with natural Chinese
date ordering. Existing Chinese terminology is made consistent for extensions,
reader modes, translation groups, and Cookie/site settings.

Visual inspection also found that the upcoming page inherited a black foreground
outside the shell Surface. Its own Surface now supplies matching theme colors,
so its newly translated title and empty-state heading are readable in AMOLED.

## Verification

DesktopLocalizationUiTest renders the actual desktop UI and checks an already
open reader menu while switching English -> Simplified -> Traditional -> English;
source titles remain unchanged. Other cases cover image actions, chapter/group
filter dialogs, and calendar messages/dates. The calendar test verifies a white
heading on AMOLED. Optional desktop-width render evidence is saved under
`build/localization-evidence/`.

The initial full desktop run executed 652 tests (14 skipped) and found six
stale test assumptions: diagnostic/about assertions used the old `Mihon W`
product name, and three source tests assumed MangaDex was automatically bundled.
Production already uses `mihondesk` and only registers the local source by
default. Assertions now use the current name, and tests that exercise MangaDex
explicitly register it as a fixture. Source registration policy, diagnostics,
and redaction behavior are unchanged. Original XML results are retained under
`build/localization-evidence/full-test-results/`.

After these fixture corrections and the calendar contrast fix, the relevant UI,
localization, security, and diagnostic packages passed 300 tests with no failures
or skips. `:desktop-app:spotlessCheck` and `git diff --check` also passed. The full
run's unaffected cases are retained separately; this is not a claim that the
initial full run passed. Source renders of the reader menu, chapter settings,
image actions, and calendar were saved and reviewed at desktop width.

Installed-package evidence will be appended after validation.
