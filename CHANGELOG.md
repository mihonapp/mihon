# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Option to disable reader zoom out (@Splintorien) (#302)
- Source name and tracker urls to app generated `ComicInfo.xml` file (@Shamicen) (#459)
- Option to migrate in Duplicate entry dialog (@sirlag) (#492)
- Upcoming screen to visualize expected update dates (@sirlag) (#420)
- Crash screen error message to the top of the crash log generated from that screen (@FooIbar) (#742)
- Support for 7Zip and RAR5 archives (@FooIbar, @null2264) (#949, #967)
- Extra configuration options to e-ink page flashes (@sirlag) (#625)
- 8-bit+ AVIF image support (@WerctFourth) (#971)
- Smart update dialog message when no predicted released date exists (@Animeboynz) (#977)
- Save global search "Has result" choice (@AntsyLich) (5a61ca5)
- Option to copy reader panel to clipboard (@Animeboynz) (#1003)
- Copy Tracker URL option to tracker sheet (@mm12) (#1101)
- A button to exclude all scanlators in exclude scanlators dialog (@AntsyLich) (84b2164)
- Open in browser option to reader menu (@mm12) (#1110)

### Changed
- Read archive files from memory instead of extracting files to internal storage (@FooIbar) (#326)
- Try to get resource from Extension before checking in the app (@beer-psi) (#433)
- Default user agent (@AntsyLich) (8160b47)
- Wait for sources to be initialized before performing source related tasks (@jobobby04) (a08e03f)
- Duplicate entry dialog UI (@sirlag) (#492)
- Extension trust system (@AntsyLich) (#570)
- Make category backup/restore not dependant on library backup (@AntsyLich) (56fb4f6)
- Kitsu domain to `kitsu.app` (@MajorTanya) (#1106)

### Improvement
- Long strip reader performance (@FooIbar, @wwww-wwww) (#687)
- Performance when looking up specific files (@raxod502) (#728)
- Chapter number parsing (@Naputt1) (6a80305)
- Error message on restoring if backup decoding fails (@vetleledaal) (#1056)

### Fixed
- Creating `ComicInfo.xml` file for local source (@FooIbar) (#325)
- Chapter download indicator (@ivaniskandar) (d8b9a9f)
- Issues with shizuku in a multi user setup (@Redjard) (#494)
- Occasional black bar when scrolling in long strip reader (@FooIbar) (#563)
- Extension being marked as not installed instead of untrusted after updating with private installer (@AntsyLich) (2114514)
- Extension update counter not updating due to extension being marked as untrusted (@AntsyLich) (2114514)
- `Key "extension-XXX-YYY" was already used` crash (@AntsyLich) (2114514)
- Navigation layout tap zones shifting after zooming out in webtoon readers (@FooIbar) (#767)
- Some extension not loading due to missing classes (@AwkwardPeak7) (#783)
- Theme colors in accordance to upstream changes (@CrepeTF, @AntsyLich) (#766, #963, #976)
- Crash when requesting folder access on non-conforming devices (@mainrs) (#726)
- Bugged color for Date/Scanlator in chapter list for read chapters (@ivaniskandar) (15d9992)
- Categories having same `order` after restoring backup (@Cologler) (119bcbf)
- Filter by "Tracking" temporarily stuck after signing out of tracker (@AntsyLich) (#987)
- JXL image downloading and loading (@FooIbar) (#993)
- Crash when using `%` in category name (@Animeboynz, @FooIbar) (#1030)
- Library is backed up while being disabled (@AntsyLich) (56fb4f6)
- Crash on list with 0 item but only sticky header (@cuong-tran) (#1083)
- Crash when trying to clear cookies of some source (@FooIbar) (#1084)
- MAL search results not showing start dates (@MajorTanya) (#1098)
- Android SDK 35 API collision (@AntsyLich) (fdb9617)

## [v0.16.5] - 2024-04-09
### Added
- Setting to install custom color profiles to get true colors (@wwww-wwww) (#523)

### Changed
- Permanently enable 32-bit color mode (@wwww-wwww) (#523)

### Fixed
- Fix wrong dates in Updates and History tab due to time zone issues (@sirlag) (#402, #415)
- Fix app infinitely retries tracker update instead of failing after 3 tries (@MajorTanya) (#411)
- Fix crash on Pixel devices (ab06720)
- Fix crash when opening some heif/heic images (@az4521) (#466)
- Fix crash in track date selection dialog (@ivaniskandar) (c348fac)
- Fix dates for saved images on Samsung devices (@MajorTanya) (#552)
- Fix colors getting distorted when opening CMYK jpeg images (@wwww-wwww) (#523)

## [v0.16.4] - 2024-02-26
### Fixed
- Circumvent MAL block (@AntsyLich) (085ad8d)

## [v0.16.3] - 2024-01-30
### Added
- Copy extension debug info when clicking logo or name in the extension details screen (@MajorTanya) (#271)

### Changed
- Rename extension update error file to `mihon_update_errors.txt` (@mjishnu) (#253)
- Hide display cutoff setting in reader settings sheet if fullscreen is off (@Riztard) (#241)

### Fixed
- Fix bottom sheet display issues on non-Tablet UI (@theolm) (#182)
- Fix crash when switching screen while a list is scrolling (@theolm) (#272)
- Fix newly installed extensions not being recognized by Mihon (@AwkwardPeak7) (#275)
- Fix error handling when refreshing MAL OAuth token (@AntsyLich) (0f4de03)

## [v0.16.2] - 2024-01-28
### Added
- Scanlator filter is now part of Backup (@jobobby04) (#166)

### Changed
- Rename crash log filename to `mihon_crash_logs.txt` (@MajorTanya) (#234)

### Fixed
- "Flash screen on page change" Making the screen goes blank (@AntsyLich) (38d6ab8)
- App icon scaling (@AntsyLich) (26815c7)
- Updating extension not reflecting correctly (@AntsyLich) (cb06898)
- Inconsistent button height with some languages in "Data and storage" (@theolm) (#202)
- Fix chapter not being marked as read in some cases with Enhanced Trackers (@Secozzi) (#219) 
- And various tracker related fixes (@AntsyLich, @kitsumed @Secozzi) (a024218, e3f33e2, 32188f9)

## [v0.16.1] - 2024-01-18
### Fixed
- App Icon not filled (@AntsyLich) (1849715)
- MangaUpdates default score being set to -1.0 (@AntsyLich) (99fd273)

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
