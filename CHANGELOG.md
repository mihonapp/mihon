# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Option to disable reader zoom out ([@Splintorien](https://github.com/Splintorien)) ([#302](https://github.com/mihonapp/mihon/pull/302))
- Source name and tracker urls to app generated `ComicInfo.xml` file ([@Shamicen](https://github.com/Shamicen)) ([#459](https://github.com/mihonapp/mihon/pull/459))
- Option to migrate in Duplicate entry dialog ([@sirlag](https://github.com/sirlag)) ([#492](https://github.com/mihonapp/mihon/pull/492))
- Upcoming screen to visualize expected update dates ([@sirlag](https://github.com/sirlag)) ([#420](https://github.com/mihonapp/mihon/pull/420))
- Crash screen error message to the top of the crash log generated from that screen ([@FooIbar](https://github.com/FooIbar)) ([#742](https://github.com/mihonapp/mihon/pull/742))
- Support for 7Zip and RAR5 archives ([@FooIbar](https://github.com/FooIbar), [@null2264](https://github.com/null2264)) ([#949](https://github.com/mihonapp/mihon/pull/949), [#967](https://github.com/mihonapp/mihon/pull/967))
- Extra configuration options to e-ink page flashes ([@sirlag](https://github.com/sirlag)) ([#625](https://github.com/mihonapp/mihon/pull/625))
- 8-bit+ AVIF image support ([@WerctFourth](https://github.com/WerctFourth)) ([#971](https://github.com/mihonapp/mihon/pull/971))
- Smart update dialog message when no predicted released date exists ([@Animeboynz](https://github.com/Animeboynz)) ([#977](https://github.com/mihonapp/mihon/pull/977))
- Save global search "Has result" choice ([@AntsyLich](https://github.com/AntsyLich)) ([`5a61ca5`](https://github.com/mihonapp/mihon/commit/5a61ca5535fe0d9e8e7bcb9e665ba2f9cb0cf649))
- Option to copy reader panel to clipboard ([@Animeboynz](https://github.com/Animeboynz)) ([#1003](https://github.com/mihonapp/mihon/pull/1003))
- Copy Tracker URL option to tracker sheet ([@mm12](https://github.com/mm12)) ([#1101](https://github.com/mihonapp/mihon/pull/1101))
- A button to exclude all scanlators in exclude scanlators dialog ([@AntsyLich](https://github.com/AntsyLich)) ([`84b2164`](https://github.com/mihonapp/mihon/commit/84b2164787a795f3fd757c325cbfb6ef660ac3a3))
- Open in browser option to reader menu ([@mm12](https://github.com/mm12)) ([#1110](https://github.com/mihonapp/mihon/pull/1110))
- Option to skip downloading duplicate read chapters ([@shabnix](https://github.com/shabnix)) ([#1125](https://github.com/mihonapp/mihon/pull/1125))

### Changed
- Read archive files from memory instead of extracting files to internal storage ([@FooIbar](https://github.com/FooIbar)) ([#326](https://github.com/mihonapp/mihon/pull/326))
- Try to get resource from Extension before checking in the app ([@beer-psi](https://github.com/beer-psi)) ([#433](https://github.com/mihonapp/mihon/pull/433))
- Default user agent ([@AntsyLich](https://github.com/AntsyLich)) ([`8160b47`](https://github.com/mihonapp/mihon/commit/8160b47ff5fbbd9b32caeb462b5be881fabd3449))
- Wait for sources to be initialized before performing source related tasks ([@jobobby04](https://github.com/jobobby04)) ([`a08e03f`](https://github.com/mihonapp/mihon/commit/a08e03f5cbf3f4e6be1de35f97ef8ebb26a1210e))
- Duplicate entry dialog UI ([@sirlag](https://github.com/sirlag)) ([#492](https://github.com/mihonapp/mihon/pull/492))
- Extension trust system ([@AntsyLich](https://github.com/AntsyLich), [@Animeboynz](https://github.com/Animeboynz) ([#570](https://github.com/mihonapp/mihon/pull/570), [#1057](https://github.com/mihonapp/mihon/pull/1057))
- Make category backup/restore not dependant on library backup ([@AntsyLich](https://github.com/AntsyLich)) ([`56fb4f6`](https://github.com/mihonapp/mihon/commit/56fb4f62a152e87a71892aa68c78cac51a2c8596))
- Kitsu domain to `kitsu.app` ([@MajorTanya](https://github.com/MajorTanya)) ([#1106](https://github.com/mihonapp/mihon/pull/1106))

### Improvement
- Long strip reader performance ([@FooIbar](https://github.com/FooIbar), [@wwww-wwww](https://github.com/wwww-wwww)) ([#687](https://github.com/mihonapp/mihon/pull/687))
- Performance when looking up specific files ([@raxod502](https://github.com/raxod502)) ([#728](https://github.com/mihonapp/mihon/pull/728))
- Chapter number parsing ([@Naputt1](https://github.com/Naputt1)) ([`6a80305`](https://github.com/mihonapp/mihon/commit/6a80305d6c572da6c08c0c69f5c25ff26ecf7383))
- Error message on restoring if backup decoding fails ([@vetleledaal](https://github.com/vetleledaal)) ([#1056](https://github.com/mihonapp/mihon/pull/1056))

### Fixed
- Creating `ComicInfo.xml` file for local source ([@FooIbar](https://github.com/FooIbar)) ([#325](https://github.com/mihonapp/mihon/pull/325))
- Chapter download indicator ([@ivaniskandar](https://github.com/ivaniskandar)) ([`d8b9a9f`](https://github.com/mihonapp/mihon/commit/d8b9a9f593911569ff2bceb49b4f020978d0d2e1))
- Issues with shizuku in a multi user setup ([@Redjard](https://github.com/Redjard)) ([#494](https://github.com/mihonapp/mihon/pull/494))
- Occasional black bar when scrolling in long strip reader ([@FooIbar](https://github.com/FooIbar)) ([#563](https://github.com/mihonapp/mihon/pull/563))
- Extension being marked as not installed instead of untrusted after updating with private installer ([@AntsyLich](https://github.com/AntsyLich)) ([`2114514`](https://github.com/mihonapp/mihon/commit/21145144cdf550aa775047603e06e261951ebc42))
- Extension update counter not updating due to extension being marked as untrusted ([@AntsyLich](https://github.com/AntsyLich)) ([`2114514`](https://github.com/mihonapp/mihon/commit/21145144cdf550aa775047603e06e261951ebc42))
- `Key "extension-XXX-YYY" was already used` crash ([@AntsyLich](https://github.com/AntsyLich)) ([`2114514`](https://github.com/mihonapp/mihon/commit/21145144cdf550aa775047603e06e261951ebc42))
- Navigation layout tap zones shifting after zooming out in webtoon readers ([@FooIbar](https://github.com/FooIbar)) ([#767](https://github.com/mihonapp/mihon/pull/767))
- Some extension not loading due to missing classes ([@AwkwardPeak7](https://github.com/AwkwardPeak7)) ([#783](https://github.com/mihonapp/mihon/pull/783))
- Theme colors in accordance to upstream changes ([@CrepeTF](https://github.com/CrepeTF), [@AntsyLich](https://github.com/AntsyLich)) ([#766](https://github.com/mihonapp/mihon/pull/766), [#963](https://github.com/mihonapp/mihon/pull/963), [#976](https://github.com/mihonapp/mihon/pull/976))
- Crash when requesting folder access on non-conforming devices ([@mainrs](https://github.com/mainrs)) ([#726](https://github.com/mihonapp/mihon/pull/726))
- Bugged color for Date/Scanlator in chapter list for read chapters ([@ivaniskandar](https://github.com/ivaniskandar)) ([`15d9992`](https://github.com/mihonapp/mihon/commit/15d999229fcce865001d5fa77d0163e6e80e38db))
- Categories having same `order` after restoring backup ([@Cologler](https://github.com/Cologler)) ([`119bcbf`](https://github.com/mihonapp/mihon/commit/119bcbf8ed2415664922ea77fadf0da1165d1732))
- Filter by "Tracking" temporarily stuck after signing out of tracker ([@AntsyLich](https://github.com/AntsyLich)) ([#987](https://github.com/mihonapp/mihon/pull/987))
- JXL image downloading and loading ([@FooIbar](https://github.com/FooIbar)) ([#993](https://github.com/mihonapp/mihon/pull/993))
- Crash when using `%` in category name ([@Animeboynz](https://github.com/Animeboynz), [@FooIbar](https://github.com/FooIbar)) ([#1030](https://github.com/mihonapp/mihon/pull/1030))
- Library is backed up while being disabled ([@AntsyLich](https://github.com/AntsyLich)) ([`56fb4f6`](https://github.com/mihonapp/mihon/commit/56fb4f62a152e87a71892aa68c78cac51a2c8596))
- Crash on list with 0 item but only sticky header ([@cuong-tran](https://github.com/cuong-tran)) ([#1083](https://github.com/mihonapp/mihon/pull/1083))
- Crash when trying to clear cookies of some source ([@FooIbar](https://github.com/FooIbar)) ([#1084](https://github.com/mihonapp/mihon/pull/1084))
- MAL search results not showing start dates ([@MajorTanya](https://github.com/MajorTanya)) ([#1098](https://github.com/mihonapp/mihon/pull/1098))
- Android SDK 35 API collision ([@AntsyLich](https://github.com/AntsyLich)) ([`fdb9617`](https://github.com/mihonapp/mihon/commit/fdb96179c6373eb0a8e7d6daea671a315d5ce5f0))

## [v0.16.5] - 2024-04-09
### Added
- Setting to install custom color profiles to get true colors ([@wwww-wwww](https://github.com/wwww-wwww)) ([#523](https://github.com/mihonapp/mihon/pull/523))

### Changed
- Permanently enable 32-bit color mode ([@wwww-wwww](https://github.com/wwww-wwww)) ([#523](https://github.com/mihonapp/mihon/pull/523))

### Fixed
- Fix wrong dates in Updates and History tab due to time zone issues ([@sirlag](https://github.com/sirlag)) ([#402](https://github.com/mihonapp/mihon/pull/402), [#415](https://github.com/mihonapp/mihon/pull/415))
- Fix app infinitely retries tracker update instead of failing after 3 tries ([@MajorTanya](https://github.com/MajorTanya)) ([#411](https://github.com/mihonapp/mihon/pull/411))
- Fix crash on Pixel devices ([`ab06720`](https://github.com/mihonapp/mihon/commit/ab067209661eceefc04c65f6bdbfcaa8a1264651))
- Fix crash when opening some heif/heic images ([@az4521](https://github.com/az4521)) ([#466](https://github.com/mihonapp/mihon/pull/466))
- Fix crash in track date selection dialog ([@ivaniskandar](https://github.com/ivaniskandar)) ([`c348fac`](https://github.com/mihonapp/mihon/commit/c348fac78fac479fb123bd617c01c78b9ca851d5))
- Fix dates for saved images on Samsung devices ([@MajorTanya](https://github.com/MajorTanya)) ([#552](https://github.com/mihonapp/mihon/pull/552))
- Fix colors getting distorted when opening CMYK jpeg images ([@wwww-wwww](https://github.com/wwww-wwww)) ([#523](https://github.com/mihonapp/mihon/pull/523))

## [v0.16.4] - 2024-02-26
### Fixed
- Circumvent MAL block ([@AntsyLich](https://github.com/AntsyLich)) ([`085ad8d`](https://github.com/mihonapp/mihon/commit/085ad8d44637c375a8ed24aba3a6f75f5b0cc9ee))

## [v0.16.3] - 2024-01-30
### Added
- Copy extension debug info when clicking logo or name in the extension details screen ([@MajorTanya](https://github.com/MajorTanya)) ([#271](https://github.com/mihonapp/mihon/pull/271))

### Changed
- Rename extension update error file to `mihon_update_errors.txt` ([@mjishnu](https://github.com/mjishnu)) ([#253](https://github.com/mihonapp/mihon/pull/253))
- Hide display cutoff setting in reader settings sheet if fullscreen is off ([@Riztard](https://github.com/Riztard)) ([#241](https://github.com/mihonapp/mihon/pull/241))

### Fixed
- Fix bottom sheet display issues on non-Tablet UI ([@theolm](https://github.com/theolm)) ([#182](https://github.com/mihonapp/mihon/pull/182))
- Fix crash when switching screen while a list is scrolling ([@theolm](https://github.com/theolm)) ([#272](https://github.com/mihonapp/mihon/pull/272))
- Fix newly installed extensions not being recognized by Mihon ([@AwkwardPeak7](https://github.com/AwkwardPeak7)) ([#275](https://github.com/mihonapp/mihon/pull/275))
- Fix error handling when refreshing MAL OAuth token ([@AntsyLich](https://github.com/AntsyLich)) ([`0f4de03`](https://github.com/mihonapp/mihon/commit/0f4de03d7a77b52490dc9a95e96a308b93b26e4f))

## [v0.16.2] - 2024-01-28
### Added
- Scanlator filter is now part of Backup ([@jobobby04](https://github.com/jobobby04)) ([#166](https://github.com/mihonapp/mihon/pull/166))

### Changed
- Rename crash log filename to `mihon_crash_logs.txt` ([@MajorTanya](https://github.com/MajorTanya)) ([#234](https://github.com/mihonapp/mihon/pull/234))

### Fixed
- "Flash screen on page change" Making the screen goes blank ([@AntsyLich](https://github.com/AntsyLich)) ([`38d6ab8`](https://github.com/mihonapp/mihon/commit/38d6ab80ce868707829dbc81de4170afe3c2f2a5))
- App icon scaling ([@AntsyLich](https://github.com/AntsyLich)) ([`26815c7`](https://github.com/mihonapp/mihon/commit/26815c7356111394665467c1e81255ac9ee33c1a))
- Updating extension not reflecting correctly ([@AntsyLich](https://github.com/AntsyLich)) ([`cb06898`](https://github.com/mihonapp/mihon/commit/cb068984303f811692531bf6f14902ae118d8ac7))
- Inconsistent button height with some languages in "Data and storage" ([@theolm](https://github.com/theolm)) ([#202](https://github.com/mihonapp/mihon/pull/202))
- Fix chapter not being marked as read in some cases with Enhanced Trackers ([@Secozzi](https://github.com/Secozzi)) ([#219](https://github.com/mihonapp/mihon/pull/219)) 
- And various tracker related fixes ([@AntsyLich](https://github.com/AntsyLich), [@kitsumed](https://github.com/kitsumed), [@Secozzi](https://github.com/Secozzi)) ([`a024218`](https://github.com/mihonapp/mihon/commit/a024218410953a389b8af4880fa7ae6cc30124a2), [`e3f33e2`](https://github.com/mihonapp/mihon/commit/e3f33e24f5e928ac8a85d1f500fd42d4715fc6b5), [`32188f9`](https://github.com/mihonapp/mihon/commit/32188f9f65009a18250674ef1bd6e57d351c1fba))

## [v0.16.1] - 2024-01-18
### Fixed
- App Icon not filled ([@AntsyLich](https://github.com/AntsyLich)) ([`1849715`](https://github.com/mihonapp/mihon/commit/18497154183356bb0d469b27827f9f7d6b7a3130))
- MangaUpdates default score being set to -1.0 ([@AntsyLich](https://github.com/AntsyLich)) ([`99fd273`](https://github.com/mihonapp/mihon/commit/99fd2731f5d9d374700e89fa67d4d5bf611bbafa))

## [v0.16.0] - 2024-01-16

"The end of 立ち読み (Tachiyomi) is the beginning of みほん (Mihon)"
Credit to LinkCable, the icon designer, for this poetic quote.

What's New?
Well, nothing, except you now you need Android 8+ to install the app.

[unreleased]: https://github.com/mihonapp/mihon/compare/v0.16.5...HEAD
[v0.16.5]: https://github.com/mihonapp/mihon/compare/v0.16.4...v0.16.5
[v0.16.4]: https://github.com/mihonapp/mihon/compare/v0.16.3...v0.16.4
[v0.16.3]: https://github.com/mihonapp/mihon/compare/v0.16.2...v0.16.3
[v0.16.2]: https://github.com/mihonapp/mihon/compare/v0.16.1...v0.16.2
[v0.16.1]: https://github.com/mihonapp/mihon/compare/v0.16.0...v0.16.1
[v0.16.0]: https://github.com/mihonapp/mihon/releases/tag/v0.16.0
