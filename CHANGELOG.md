# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Option to disable reader zoom out (@Splintorien)
- Source name and tracker urls to app generated `ComicInfo.xml` file (@Shamicen)
- Option to migrate in Duplicate entry dialog (@sirlag)
- Upcoming screen to visualize expected update dates (@sirlag)
- Crash screen error message to the top of the crash log generated from that screen (@FooIbar)
- Support for 7Zip and RAR5 archives (@FooIbar, @null2264)
- Extra configuration options to e-ink page flashes (@sirlag)
- 8-bit+ AVIF image support (@WerctFourth)
- Smart update dialog message when no predicted released date exists (@Animeboynz)
- Save global search "Has result" choice (@AntsyLich)
- Option to copy reader panel to clipboard (@Animeboynz)
- Copy Tracker URL option to tracker sheet (@mm12)
- A button to exclude all scanlators in exclude scanlators dialog (@AntsyLich)
- Open in browser option to reader menu (@mm12)

### Changed
- Read archive files from memory instead of extracting files to internal storage (@FooIbar)
- Try to get resource from Extension before checking in the app (@beer-psi) 
- Default user agent (@AntsyLich)
- Wait for sources to be initialized before performing source related tasks (@jobobby04)
- Duplicate entry dialog UI (@sirlag)
- Extension trust system (@AntsyLich)
- Make category backup/restore not dependant on library backup (@AntsyLich)
- Kitsu domain to `kitsu.app` (@MajorTanya)

### Improvement
- Long strip reader performance (@FooIbar, @wwww-wwww)
- Performance when looking up specific files (@raxod502)
- Chapter number parsing (@Naputt1)
- Error message on restoring if backup decoding fails (@vetleledaal)

### Fixed
- Creating `ComicInfo.xml` file for local source (@FooIbar)
- Chapter download indicator (@ivaniskandar)
- Crash when trying to load some corrupt image (@az4521)
- Issues with shizuku in a multi user setup (@Redjard)
- Occasional black bar when scrolling in long strip reader (@FooIbar)
- Extension being marked as not installed instead of untrusted after updating with private installer (@AntsyLich)
- Extension update counter not updating due to extension being marked as untrusted (@AntsyLich)
- `Key "extension-XXX-YYY" was already used` crash (@AntsyLich)
- Navigation layout tap zones shifting after zooming out in webtoon readers (@FooIbar)
- Some extension not loading due to missing classes (@AwkwardPeak7)
- Theme colors in accordance to upstream changes (@CrepeTF, @AntsyLich)
- Crash when requesting folder access on non-conforming devices (@mainrs)
- Bugged color for Date/Scanlator in chapter list for read chapters (@ivaniskandar)
- Categories having same `order` after restoring backup (@Cologler)
- Filter by "Tracking" temporarily stuck after signing out of tracker (@AntsyLich)
- JXL image downloading and loading (@FooIbar)
- Crash when using `%` in category name (@Animeboynz, @FooIbar)
- Library is backed up while being disabled (@AntsyLich)
- Crash on list with 0 item but only sticky header (@cuong-tran)
- Crash when trying to clear cookies of some source (@FooIbar)
- MAL search results not showing start dates (@MajorTanya)
- Android SDK 35 API collision (@AntsyLich)

## [v0.16.5] - 2024-04-09
### Added
- Setting to install custom color profiles to get true colors (@wwww-wwww)

### Changed
- Permanently enable 32-bit color mode (@wwww-wwww)

### Fixed
- Fix wrong dates in Updates and History tab due to time zone issues (@sirlag)
- Fix app infinitely retries tracker update instead of failing after 3 tries (@MajorTanya)
- Fix crash on Pixel devices
- Fix crash when opening some heif/heic images (@az4521)
- Fix crash in track date selection dialog (@ivaniskandar)
- Fix dates for saved images on Samsung devices (@MajorTanya)
- Fix colors getting distorted when opening CMYK jpeg images (@wwww-wwww)

## [v0.16.4] - 2024-02-26
### Fixed
- Circumvent MAL block (@AntsyLich)

## [v0.16.3] - 2024-01-30
### Added
- Copy extension debug info when clicking logo or name in the extension details screen (@MajorTanya)

### Changed
- Rename extension update error file to `mihon_update_errors.txt` (@m-jishnu)
- Hide display cutoff setting in reader settings sheet if fullscreen is off (@Riztard)

### Fixed
- Fix bottom sheet display issues on non-Tablet UI (@theolm)
- Fix crash when switching screen while a list is scrolling (@theolm)
- Fix newly installed extensions not being recognized by Mihon (@AwkwardPeak7)
- Fix error handling when refreshing MAL OAuth token (@AntsyLich)

## [v0.16.2] - 2024-01-28
### Added
- Scanlator filter is now part of Backup (@jobobby04)

### Changed
- Rename crash log filename to `mihon_crash_logs.txt` (@MajorTanya)

### Fixed
- "Flash screen on page change" Making the screen goes blank (@AntsyLich)
- App icon scaling (@AntsyLich)
- Updating extension not reflecting correctly (@AntsyLich)
- Inconsistent button height with some languages in "Data and storage" (@theolm)
- Fix chapter not being marked as read in some cases with Enhanced Trackers (@Secozzi)
- And various tracker related fixes (@AntsyLich, @Secozzi)

## [v0.16.1] - 2024-01-18
### Fixed
- App Icon not filled (@AntsyLich)
- MangaUpdates default score being set to -1.0 (@AntsyLich)

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
