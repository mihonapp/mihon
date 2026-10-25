# Hide and unhide individual chapters

Related request: https://github.com/mihonapp/mihon/issues/1916

Selecting chapters adds a Hide action to the chapter selection bar. When every
selected chapter is hidden, the action becomes Unhide. Mixed selections hide all
selected chapters.

Hidden chapters stay in their normal list position, dimmed. Long press still
selects them, and taps toggle selection while selection mode is active. They
cannot open the reader or start downloads. Hiding preserves reading progress,
bookmarks and completed downloads; queued downloads are canceled.

The reader skips hidden chapters regardless of the Skip filtered chapters
setting. Resume and download ahead skip them too, including when the history
anchor was subsequently hidden. Hidden chapters do not extend the range used
for missing-chapter warnings, and known hidden chapters within that range do
not become missing chapters. This handles an unwanted Chapter 99990000 above
Chapter 203 without removing the unwanted row from the list.

## Persistence

Database migration 20 adds `chapter.user_hidden`, defaulting existing chapters
to visible, and updates the library and updates views. Source metadata refreshes
preserve the flag. Replacement chapter URLs inherit it by recognized chapter
number, following existing progress and bookmark transfer behavior.

Backups use optional protobuf field 14. Older backups default to visible;
restoring into an existing library preserves a hidden flag from either copy.

## Validation

Regression coverage includes hidden outliers, known hidden chapter gaps, both
sort directions, all-hidden lists, selecting hidden rows, resume anchors and
protobuf compatibility. Download queue coverage checks current chapter state
when restoring a saved queue.

Run the repository checks with the configured JDK and Android SDK:

```sh
./gradlew spotlessCheck testDebugUnitTest verifySqlDelightMigration :app:assembleDebug --max-workers=2
```

Native review flow:

1. Open an entry with normal chapters and an unwanted high-numbered chapter.
2. Long press the unwanted chapter and tap the fifth action, Hide.
3. Confirm the dimmed row remains and its tap/download/swipe actions cannot
   open or download it. Confirm the missing-chapter warning ignores it.
4. Long press the dimmed row and tap Unhide. Confirm normal interaction returns.
5. Hide multiple chapters, then test a mixed selection and an all-hidden selection.
6. Verify Start/Resume and reader next/previous navigation skip hidden chapters,
   with Skip filtered chapters both enabled and disabled.
7. Repeat after refreshing, restarting and restoring a backup. Verify queued
   hidden chapters do not resume downloading, while completed downloads remain.
8. Check phone and tablet layouts, light and dark themes, and accessibility
   announcements for hidden rows.

Verified locally on 25 October 2026 using JDK 21 and Android SDK 37.2:

- 100 domain tests and 49 app tests passed, with no skips or failures.
- SQLDelight debug/release migration verification and repository formatting checks passed.
- Debug APK assembled successfully.
- Android 16 ARM64 emulator: phone and tablet layouts, light/dark mode,
  Hide/Unhide, disabled row taps, mixed/all-hidden selections, refresh, restart,
  and reader chapter 1 → 3 navigation with hidden chapter 2 passed.
- Reader skipping was checked with Skip filtered chapters enabled and disabled.
- Independent source review findings about existing download queues and reader
  gap warnings were repaired and re-reviewed with no further source findings.

Native verification used an offline local-source fixture. Physical devices,
remote extension network behavior and a full manual backup import were not
exercised; protobuf compatibility is covered by automated tests.

## Native screenshots

The offline local-source fixture uses Delete in the fourth slot because its
chapters are already available locally; Hide/Unhide is the fifth action.

![Select a chapter to hide](1916-hidden-chapters/selection-hide.png)

![Select the dimmed chapter to unhide it](1916-hidden-chapters/selection-unhide.png)

![Tablet chapter selection](1916-hidden-chapters/tablet-unhide.png)
