# Changelog

All notable changes to this project will be documented in this file.

The format is a modified version of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
- `Added` - for new features.
- `Changed ` - for changes in existing functionality.
- `Improved` - for enhancement or optimization in existing functionality.
- `Removed` - for now removed features.
- `Fixed` - for any bug fixes.
- `Other` - for technical stuff.

## [Unreleased]
### Added
- Add more Kaomoji for empty/error screens ([@ianfhunter](https://github.com/ianfhunter/)) ([#1909](https://github.com/mihonapp/mihon/pull/1909))
- Add user manga notes ([@imkunet](https://github.com/imkunet), [@AntsyLich](https://github.com/AntsyLich)) ([#428](https://github.com/mihonapp/mihon/pull/428))
  - Fix user notes not restoring when manga doesn't exist in DB ([@AntsyLich](https://github.com/AntsyLich)) ([#1945](https://github.com/mihonapp/mihon/pull/1945))

### Fixes
- Fix Bangumi search results including novels ([@MajorTanya](https://github.com/MajorTanya)) ([#1885](https://github.com/mihonapp/mihon/pull/1885))
- Fix next chapter button occasionally jumping to the last page of the current chapter ([@perokhe](https://github.com/perokhe)) ([#1920](https://github.com/mihonapp/mihon/pull/1920))
  - Fix page number not appearing when opening chapter ([@perokhe](https://github.com/perokhe)) ([#1936](https://github.com/mihonapp/mihon/pull/1936))
- Fix backup sharing from notifications not working when app is in background ([@JaymanR](https://github.com/JaymanR))([#1929](https://github.com/mihonapp/mihon/pull/1929))
- Fix mark existing duplicate read chapters as read option not working in some cases ([@AntsyLich](https://github.com/AntsyLich)) ([#1944](https://github.com/mihonapp/mihon/pull/1944))

## [v0.18.0] - 2025-03-20
### Added
- Add option to always decode long strip images with SSIV ([@AntsyLich](https://github.com/AntsyLich)) ([`c5655e8`](https://github.com/mihonapp/mihon/commit/c5655e8803bc32d0931657f0b7bc6afeab70feaf))
  - Change option label ([@AntsyLich](https://github.com/AntsyLich)) ([#1835](https://github.com/mihonapp/mihon/pull/1835))
- Added option to enable incognito per extension ([@sdaqo](https://github.com/sdaqo), [@AntsyLich](https://github.com/AntsyLich)) ([#157](https://github.com/mihonapp/mihon/pull/157))
- Add button to favorite manga from history screen ([@Animeboynz](https://github.com/Animeboynz)) ([#1733](https://github.com/mihonapp/mihon/pull/1733))
- Add Monochrome theme (made with e-ink displays in mind) ([@MajorTanya](https://github.com/MajorTanya)) ([#1752](https://github.com/mihonapp/mihon/pull/1752))
- Support for private tracking with AniList and Bangumi ([@NarwhalHorns](https://github.com/NarwhalHorns)) ([#1736](https://github.com/mihonapp/mihon/pull/1736))
  - Add private tracking support for Kitsu ([@MajorTanya](https://github.com/MajorTanya)) ([#1774](https://github.com/mihonapp/mihon/pull/1774))
- Add option to export minimal library information to a CSV file ([@Animeboynz](https://github.com/Animeboynz), [@AntsyLich](https://github.com/AntsyLich)) ([#1161](https://github.com/mihonapp/mihon/pull/1161))
- Add back support for drag-and-drop category reordering ([@cuong-tran](https://github.com/cuong-tran)) ([#1427](https://github.com/mihonapp/mihon/pull/1427))
- Add option to mark duplicate read chapters as read after library update or while reading ([@AntsyLich](https://github.com/AntsyLich)) ([#1785](https://github.com/mihonapp/mihon/pull/1785), [#1791](https://github.com/mihonapp/mihon/pull/1791), [#1870](https://github.com/mihonapp/mihon/pull/1870))
- Display staff information on Anilist tracker search results ([@NarwhalHorns](https://github.com/NarwhalHorns)) ([#1810](https://github.com/mihonapp/mihon/pull/1810))
- Add `id:` prefix search to library to search by internal DB ID ([@MajorTanya](https://github.com/MajorTanya)) ([#1856](https://github.com/mihonapp/mihon/pull/1856))
- Add back option to disable unread chapter badge in library ([@AntsyLich](https://github.com/AntsyLich)) ([#1871](https://github.com/mihonapp/mihon/pull/1871))

### Changed
- Sliders UI ([@AntsyLich](https://github.com/AntsyLich)) ([#1840](https://github.com/mihonapp/mihon/pull/1840))
- Apply "Downloaded only" filter to all entries regardless of favourite status ([@NGB-Was-Taken](https://github.com/NGB-Was-Taken)) ([#1603](https://github.com/mihonapp/mihon/pull/1603))
- Ignore hidden files/folders for Local Source chapter list ([@BrutuZ](https://github.com/BrutuZ)) ([#1763](https://github.com/mihonapp/mihon/pull/1763))
- Migrate to newer Bangumi API ([@MajorTanya](https://github.com/MajorTanya)) ([#1748](https://github.com/mihonapp/mihon/pull/1748))
  - Now showing manga starting dates in search
  - Reduced request load by 2-4x in certain situations
- Bump default user agent ([@AntsyLich](https://github.com/AntsyLich)) ([#1833](https://github.com/mihonapp/mihon/pull/1833))
- Changed the label of chapter swipe settings and renamed the group to "Behavior" ([@AntsyLich](https://github.com/AntsyLich)) ([#1870](https://github.com/mihonapp/mihon/pull/1870))

### Fixed
- Fix MAL `main_picture` nullability breaking search if a result doesn't have a cover set ([@MajorTanya](https://github.com/MajorTanya)) ([#1618](https://github.com/mihonapp/mihon/pull/1618))
- Fix Bangumi and MAL tracking 401 errors due to Mihon sending expired credentials ([@MajorTanya](https://github.com/MajorTanya)) ([#1681](https://github.com/mihonapp/mihon/pull/1681), [#1682](https://github.com/mihonapp/mihon/pull/1682))
- Fix certain Infinix, Xiaomi devices being unable to use any "Open link in browser" actions, including tracker setup ([@MajorTanya](https://github.com/MajorTanya)) ([#1684](https://github.com/mihonapp/mihon/pull/1684)) ([#1776](https://github.com/mihonapp/mihon/pull/1776))
- Fix App's preferences referencing deleted categories ([@cuong-tran](https://github.com/cuong-tran)) ([#1734](https://github.com/mihonapp/mihon/pull/1734))
- Fix backup/restore of category related preferences ([@cuong-tran](https://github.com/cuong-tran)) ([#1726](https://github.com/mihonapp/mihon/pull/1726))
- Fix WebView sending app's package name in `X-Requested-With` header, which led to sources blocking access ([@AwkwardPeak7](https://github.com/AwkwardPeak7)) ([#1812](https://github.com/mihonapp/mihon/pull/1812))
- Fix an issue where tracker reading progress is changed to a lower value ([@Animeboynz](https://github.com/Animeboynz)) ([#1795](https://github.com/mihonapp/mihon/pull/1795))
- Attempt to fix crash when migrating or removing entries from library ([@FlaminSarge](https://github.com/FlaminSarge)) ([#1828](https://github.com/mihonapp/mihon/pull/1828))

### Removed
- Remove alphabetical category sort option ([@AntsyLich](https://github.com/AntsyLich)) ([#1781](https://github.com/mihonapp/mihon/pull/1781))

### Other
- Add zoned "Current time" to debug info and include year & timezone in logcat output ([@MajorTanya](https://github.com/MajorTanya)) ([#1672](https://github.com/mihonapp/mihon/pull/1672))
- Add application package ID to debug info ([@MajorTanya](https://github.com/MajorTanya)) ([#1847](https://github.com/mihonapp/mihon/pull/1847))

## [v0.17.1] - 2024-12-06
### Changed
- Bump default user agent ([@AntsyLich](https://github.com/AntsyLich)) ([`76dcf90`](https://github.com/mihonapp/mihon/commit/76dcf903403d565056f44c66d965c1ea8affffc3))

### Improved
- Bangumi search now shows the score and summary of a search result ([@MajorTanya](https://github.com/MajorTanya)) ([#1396](https://github.com/mihonapp/mihon/pull/1396))
- Extension repo URLs are now auto-formatted ([@AntsyLich](https://github.com/AntsyLich), [@MajorTanya](https://github.com/MajorTanya)) ([`22d8aad`](https://github.com/mihonapp/mihon/commit/22d8aad598bea8f00f2831779e45a6645392ca0f))

### Fixed
- Fix "currentTab was used multiple times" ([@AntsyLich](https://github.com/AntsyLich)) ([`371c143`](https://github.com/mihonapp/mihon/commit/371c1432e218f6dcf129f05405dceb2cd351c647))
- Fix a rare crash when invoking "Mark previous as read" action ([@AntsyLich](https://github.com/AntsyLich)) ([`f508d10`](https://github.com/mihonapp/mihon/commit/f508d10ad13560d7316df8642bc93fe66c05b9a8))
- Fix long strip images not loading in some old devices ([@AntsyLich](https://github.com/AntsyLich)) ([`06efc3b`](https://github.com/mihonapp/mihon/commit/06efc3b25c5af51f42448af27a269ee459d9093d))
  - Switch to hardware bitmap in reader only if device can handle it ([@AntsyLich](https://github.com/AntsyLich)) ([`e6d96bd`](https://github.com/mihonapp/mihon/commit/e6d96bd348ea5d18a005d6465222ad5f5123103e))
  - Add option to lower the threshold for hardware bitmaps ([@AntsyLich](https://github.com/AntsyLich)) ([`dcddac5`](https://github.com/mihonapp/mihon/commit/dcddac5daaff3ec89c8507c35dc13d345ffdb6d7))
    - Improve hardware bitmap threshold option ([@AntsyLich](https://github.com/AntsyLich)) ([`d6dfd24`](https://github.com/mihonapp/mihon/commit/d6dfd24548eaa05a8c3e478068fe2e08f2ee4473))
  - Always use software bitmap on certain devices ([@MajorTanya](https://github.com/MajorTanya)) ([#1543](https://github.com/mihonapp/mihon/pull/1543))
- Fix crash after removing last category while it's active in library ([@cuong-tran](https://github.com/cuong-tran)) ([#1450](https://github.com/mihonapp/mihon/pull/1450))
- Fix reader transition color scheme in auto background mode ([@cuong-tran](https://github.com/cuong-tran)) ([#1487](https://github.com/mihonapp/mihon/pull/1487))
- Fix app update error notification disappearing ([@cuong-tran](https://github.com/cuong-tran)) ([#1476](https://github.com/mihonapp/mihon/pull/1476))
- Fix browser not opening in some cases in Honor devices ([@AntsyLich](https://github.com/AntsyLich), [@MajorTanya](https://github.com/MajorTanya)) ([#1520](https://github.com/mihonapp/mihon/pull/1520))

## [v0.17.0] - 2024-10-26
### Added
- Option to disable reader zoom out ([@Splintorien](https://github.com/Splintorien)) ([#302](https://github.com/mihonapp/mihon/pull/302))
- Source name and tracker urls to app generated `ComicInfo.xml` file ([@Shamicen](https://github.com/Shamicen)) ([#459](https://github.com/mihonapp/mihon/pull/459))
- Option to migrate in Duplicate entry dialog ([@sirlag](https://github.com/sirlag)) ([#492](https://github.com/mihonapp/mihon/pull/492))
- Upcoming screen to visualize expected update dates ([@sirlag](https://github.com/sirlag)) ([#420](https://github.com/mihonapp/mihon/pull/420))
  - Only show upcoming updates in the future ([@sirlag](https://github.com/sirlag)) ([#606](https://github.com/mihonapp/mihon/pull/606))
  - Add Quantity Badge to Upcoming Screen ([@Animeboynz](https://github.com/Animeboynz), [@AntsyLich](https://github.com/AntsyLich)) ([#1250](https://github.com/mihonapp/mihon/pull/1250))
- Crash screen error message to the top of the crash log generated from that screen ([@FooIbar](https://github.com/FooIbar)) ([#742](https://github.com/mihonapp/mihon/pull/742))
- Support for 7Zip and RAR5 archives ([@FooIbar](https://github.com/FooIbar)) ([#949](https://github.com/mihonapp/mihon/pull/949))
- Extra configuration options to e-ink page flashes ([@sirlag](https://github.com/sirlag)) ([#625](https://github.com/mihonapp/mihon/pull/625))
- 8-bit+ AVIF image support ([@WerctFourth](https://github.com/WerctFourth)) ([#971](https://github.com/mihonapp/mihon/pull/971))
- Smart update dialog message when no predicted released date exists ([@Animeboynz](https://github.com/Animeboynz)) ([#977](https://github.com/mihonapp/mihon/pull/977))
- Option to copy reader panel to clipboard ([@Animeboynz](https://github.com/Animeboynz)) ([#1003](https://github.com/mihonapp/mihon/pull/1003))
- Copy Tracker URL option to tracker sheet ([@mm12](https://github.com/mm12)) ([#1101](https://github.com/mihonapp/mihon/pull/1101))
- A button to exclude all scanlators in exclude scanlators dialog ([@AntsyLich](https://github.com/AntsyLich)) ([`84b2164`](https://github.com/mihonapp/mihon/commit/84b2164787a795f3fd757c325cbfb6ef660ac3a3))
- Open in browser option to reader menu ([@mm12](https://github.com/mm12)) ([#1110](https://github.com/mihonapp/mihon/pull/1110))
  - Reorder reader menu overflow items ([@AntsyLich](https://github.com/AntsyLich)) ([`788235f`](https://github.com/mihonapp/mihon/commit/788235feeca241228eac0561339dd07b5ea0b77d))
- Option to skip downloading duplicate read chapters ([@shabnix](https://github.com/shabnix)) ([#1125](https://github.com/mihonapp/mihon/pull/1125))
- Add confirmation dialog when adding repo via URI ([@Animeboynz](https://github.com/Animeboynz)) ([#1158](https://github.com/mihonapp/mihon/pull/1158))
- Add "show entry" action to download notifications ([@mm12](https://github.com/mm12), [@AntsyLich](https://github.com/AntsyLich)) ([#1159](https://github.com/mihonapp/mihon/pull/1159))
- Option to update trackers when chapter marked as read ([@Animeboynz](https://github.com/Animeboynz), [@AntsyLich](https://github.com/AntsyLich)) ([#1177](https://github.com/mihonapp/mihon/pull/1177), [#1365](https://github.com/mihonapp/mihon/pull/1365), [#1374](https://github.com/mihonapp/mihon/pull/1374))
- Toast to restart app when User-Agent is changed ([@NGB-Was-Taken](https://github.com/NGB-Was-Taken)) ([#1204](https://github.com/mihonapp/mihon/pull/1204))
- Added more profile compilation status (p) ([`c8bb78d`](https://github.com/mihonapp/mihon/commit/c8bb78d91afc2824baaca999f0095559c49d1306))
- Add option to opt out of Analytics and Crashlytics ([@Animeboynz](https://github.com/Animeboynz)) ([#1237](https://github.com/mihonapp/mihon/pull/1237))
  - Rework Firebase setup ([@AntsyLich](https://github.com/AntsyLich)) ([`15e3f28`](https://github.com/mihonapp/mihon/commit/15e3f28aa36bec3c31f212c572ab57ce960cc862))
- Added random library sort ([@jackhamilton](https://github.com/jackhamilton)) ([#1317](https://github.com/mihonapp/mihon/pull/1317))
  - Make sure random library sort is at the bottom ([@AntsyLich](https://github.com/AntsyLich)) ([`2e2c8d3`](https://github.com/mihonapp/mihon/commit/2e2c8d36c1e23bf274c7c19f1242e14b0c7afbc1))
- Confirmation dialog when removing privately installed extensions ([@Animeboynz](https://github.com/Animeboynz), [@AntsyLich](https://github.com/AntsyLich)) ([#1320](https://github.com/mihonapp/mihon/pull/1320))
- Option to backup non-library read entries ([@Animeboynz](https://github.com/Animeboynz), [@jobobby04](https://github.com/jobobby04), [@AntsyLich](https://github.com/AntsyLich)) ([#1324](https://github.com/mihonapp/mihon/pull/1324))

### Changed
- Read archive files from memory instead of temporarily extracting to internal storage ([@FooIbar](https://github.com/FooIbar)) ([#326](https://github.com/mihonapp/mihon/pull/326))
  - Fix dual page split ([@FooIbar](https://github.com/FooIbar)) ([#485](https://github.com/mihonapp/mihon/pull/485))
- Bump default user agent ([@AntsyLich](https://github.com/AntsyLich)) ([`8160b47`](https://github.com/mihonapp/mihon/commit/8160b47ff5fbbd9b32caeb462b5be881fabd3449))
- Wait for sources to be initialized before performing source related tasks ([@jobobby04](https://github.com/jobobby04)) ([`a08e03f`](https://github.com/mihonapp/mihon/commit/a08e03f5cbf3f4e6be1de35f97ef8ebb26a1210e))
- Duplicate entry dialog UI ([@sirlag](https://github.com/sirlag)) ([#492](https://github.com/mihonapp/mihon/pull/492))
- Extension trust system
  - Store extension repo details from `repo.json` in database ([@sirlag](https://github.com/sirlag)) ([#506](https://github.com/mihonapp/mihon/pull/506))
    - Fix extension repo migration not triggering ([@AntsyLich](https://github.com/AntsyLich)) ([`9672ea8`](https://github.com/mihonapp/mihon/commit/9672ea8b1b06f464800e310c96e060ead182f7ca))
    - Refactor the ExtensionRepoService to use DTOs ([@MajorTanya](https://github.com/MajorTanya)) ([#573](https://github.com/mihonapp/mihon/pull/573))
    - Fix extension repo name is used to construct URL instead of baseUrl ([@MajorTanya](https://github.com/MajorTanya)) ([#572](https://github.com/mihonapp/mihon/pull/572))
    - Fix crash with `TypeReference` issue when creating extension repo ([@AntsyLich](https://github.com/AntsyLich)) ([#574](https://github.com/mihonapp/mihon/pull/574), [`e020ae5`](https://github.com/mihonapp/mihon/commit/e020ae5ed558e80742ef0ad8bfa0f69af0959d5a))
      - Fix mishap in [`e020ae5`](https://github.com/mihonapp/mihon/commit/e020ae5ed558e80742ef0ad8bfa0f69af0959d5a) ([@AntsyLich](https://github.com/AntsyLich)) ([`6965e59`](https://github.com/mihonapp/mihon/commit/6965e59a643c67a2bf81b3c69ec70268e5da5797))
    - Backup and Restore ([@Animeboynz](https://github.com/Animeboynz)) ([#1057](https://github.com/mihonapp/mihon/pull/1057))
  - Trust extension by repo ([@AntsyLich](https://github.com/AntsyLich)) ([#570](https://github.com/mihonapp/mihon/pull/570))
- From M2 ripple to M3 ([@FooIbar](https://github.com/FooIbar)) ([#675](https://github.com/mihonapp/mihon/pull/675))
- Increased continue reading button size ([@AntsyLich](https://github.com/AntsyLich), [@Animeboynz](https://github.com/Animeboynz)) ([`e17f70f`](https://github.com/mihonapp/mihon/commit/e17f70f7226ea031fc1f962c9dfea3e404ba53ad))
- Global search "Has result" choice is now sticky ([@AntsyLich](https://github.com/AntsyLich)) ([`5a61ca5`](https://github.com/mihonapp/mihon/commit/5a61ca5535fe0d9e8e7bcb9e665ba2f9cb0cf649))
- Make category backup/restore not dependant on library backup ([@AntsyLich](https://github.com/AntsyLich)) ([`56fb4f6`](https://github.com/mihonapp/mihon/commit/56fb4f62a152e87a71892aa68c78cac51a2c8596))
- Rename backup restore error log file ([@AntsyLich](https://github.com/AntsyLich)) ([`2858ef8`](https://github.com/mihonapp/mihon/commit/2858ef835fec8d7278b1d0cad1b5664104d1e4b0))
- Keyboard type in add extension repo dialog ([@xbjfk](https://github.com/xbjfk)) ([#764](https://github.com/mihonapp/mihon/pull/764))
- Adjust collapse/open animation on manga description ([@AntsyLich](https://github.com/AntsyLich), [@ivaniskandar](https://github.com/ivaniskandar)) ([`1c16fc7`](https://github.com/mihonapp/mihon/commit/1c16fc79c2ac4c4be30308fed84ffb371dab5902))
- Kitsu domain to `kitsu.app` ([@MajorTanya](https://github.com/MajorTanya)) ([#1106](https://github.com/mihonapp/mihon/pull/1106))
- Respect privacy settings in extension update notification ([@Animeboynz](https://github.com/Animeboynz)) ([#1156](https://github.com/mihonapp/mihon/pull/1156))
- Hide keyboard when a Tracker SearchResultItem is clicked ([@Animeboynz](https://github.com/Animeboynz)) ([#1168](https://github.com/mihonapp/mihon/pull/1168))
- Enable 'Split Tall Images' by default ([@Smol-Ame](https://github.com/Smol-Ame)) ([#1185](https://github.com/mihonapp/mihon/pull/1185))
- Ignore "intent://" urls on webview ([@bapeey](https://github.com/bapeey)) ([#1193](https://github.com/mihonapp/mihon/pull/1193))
- Make reader chapter navigator slightly wider on small screens (p) ([#1202](https://github.com/mihonapp/mihon/pull/1202))
- Re-enable fetching chapters list for entries with licenced status ([@Animeboynz](https://github.com/Animeboynz)) ([#1230](https://github.com/mihonapp/mihon/pull/1230))
- Change casing for Extention Repos String ([@Animeboynz](https://github.com/Animeboynz)) ([#1248](https://github.com/mihonapp/mihon/pull/1248))
- Retain remote last chapter read if it's higher than the local one for EnhancedTracker ([@brewkunz](https://github.com/brewkunz)) ([#1301](https://github.com/mihonapp/mihon/pull/1301))
- Adjust expandable fab animation (p) ([`eb6092b`](https://github.com/mihonapp/mihon/commit/eb6092bd0cfa09694985a8bafdd8bbf2815190a1))
- "Invalidate downloads index" to "Reindex downloads" ([@AntsyLich](https://github.com/AntsyLich)) ([`d2afbfe`](https://github.com/mihonapp/mihon/commit/d2afbfe4ede283076aae40633c79c3f90b4390e7))

### Improved
- Reader performance
  - Avoid unnecessary copying when processing reader image ([@FooIbar](https://github.com/FooIbar)) ([#691](https://github.com/mihonapp/mihon/pull/691))
  - Significantly improve performance when loading extremely long images in long strip mode ([@FooIbar](https://github.com/FooIbar)) ([#692](https://github.com/mihonapp/mihon/pull/692))
  - Use `Bitmap.Config.HARDWARE` if possible to improve image loading speed ([@wwww-wwww](https://github.com/wwww-wwww)) ([#687](https://github.com/mihonapp/mihon/pull/687))
  - Improve preloading in long strip mode ([@FooIbar](https://github.com/FooIbar)) ([#1076](https://github.com/mihonapp/mihon/pull/1076))
- Performance when looking up specific files ([@raxod502](https://github.com/raxod502)) ([#728](https://github.com/mihonapp/mihon/pull/728))
- Chapter number parsing ([@Naputt1](https://github.com/Naputt1)) ([`6a80305`](https://github.com/mihonapp/mihon/commit/6a80305d6c572da6c08c0c69f5c25ff26ecf7383))
- Error message on restoring if backup decoding fails ([@vetleledaal](https://github.com/vetleledaal)) ([#1056](https://github.com/mihonapp/mihon/pull/1056))

### Removed
- Legacy download folder names no longer supported ([@AntsyLich](https://github.com/AntsyLich)) ([`e55e5f6`](https://github.com/mihonapp/mihon/commit/e55e5f6f64f872475d370d6ce0c186e2601776e4))
- Remove legacy broken source and history backup ([@AntsyLich](https://github.com/AntsyLich)) ([`518abf0`](https://github.com/mihonapp/mihon/commit/518abf032ccb9bb45d197927be2a5faca4167d29))
- Remove more unnecessary permissions from Firebase dependency ([@AntsyLich](https://github.com/AntsyLich)) ([`02af9b1`](https://github.com/mihonapp/mihon/commit/02af9b1acf9f590d29560bc3fc90d206e8e6e1af))
  - Fix mishap in `02af9b1` ([@AntsyLich](https://github.com/AntsyLich)) ([`f22767d`](https://github.com/mihonapp/mihon/commit/f22767d863a0fa001f93f24092cd5ade87350502))

### Fixed
- Extracting `ComicInfo.xml` from local source archives ([@FooIbar](https://github.com/FooIbar)) ([#325](https://github.com/mihonapp/mihon/pull/325))
- Chapter download indicator ([@ivaniskandar](https://github.com/ivaniskandar)) ([`d8b9a9f`](https://github.com/mihonapp/mihon/commit/d8b9a9f593911569ff2bceb49b4f020978d0d2e1))
- Issues with shizuku in a multi user setup ([@Redjard](https://github.com/Redjard)) ([#494](https://github.com/mihonapp/mihon/pull/494))
- Fix reader page image not being decoded until it's visible ([@FooIbar](https://github.com/FooIbar)) ([#563](https://github.com/mihonapp/mihon/pull/563))
- Reader chapter progress slider visuals ([@FooIbar](https://github.com/FooIbar)) ([#674](https://github.com/mihonapp/mihon/pull/674))
- Extension being marked as not installed instead of untrusted after updating with private installer ([@AntsyLich](https://github.com/AntsyLich)) ([`2114514`](https://github.com/mihonapp/mihon/commit/21145144cdf550aa775047603e06e261951ebc42))
- Extension update counter not updating due to extension being marked as untrusted ([@AntsyLich](https://github.com/AntsyLich)) ([`2114514`](https://github.com/mihonapp/mihon/commit/21145144cdf550aa775047603e06e261951ebc42))
- `Key "extension-XXX-YYY" was already used` crash ([@AntsyLich](https://github.com/AntsyLich)) ([`2114514`](https://github.com/mihonapp/mihon/commit/21145144cdf550aa775047603e06e261951ebc42))
- Navigation layout tap zones shifting after zooming out in webtoon readers ([@FooIbar](https://github.com/FooIbar)) ([#767](https://github.com/mihonapp/mihon/pull/767))
- Some extension not loading due to missing classes ([@AwkwardPeak7](https://github.com/AwkwardPeak7)) ([#783](https://github.com/mihonapp/mihon/pull/783))
- Theme colors in accordance to upstream changes ([@CrepeTF](https://github.com/CrepeTF), [@AntsyLich](https://github.com/AntsyLich)) ([#766](https://github.com/mihonapp/mihon/pull/766), [#963](https://github.com/mihonapp/mihon/pull/963), [#976](https://github.com/mihonapp/mihon/pull/976), [9a34ace](https://github.com/mihonapp/mihon/commit/9a34ace09c66274e6c2b3f9446058a0fa99d4bd0))
- Crash when requesting folder access on non-conforming devices ([@mainrs](https://github.com/mainrs)) ([#726](https://github.com/mihonapp/mihon/pull/726))
- Fix unexpected skips in strong skipping mode ([@FooIbar](https://github.com/FooIbar)) ([#940](https://github.com/mihonapp/mihon/pull/940))
- Bugged color for Date/Scanlator in chapter list for read chapters ([@ivaniskandar](https://github.com/ivaniskandar)) ([`15d9992`](https://github.com/mihonapp/mihon/commit/15d999229fcce865001d5fa77d0163e6e80e38db))
- Categories having same `order` after restoring backup ([@Cologler](https://github.com/Cologler)) ([`119bcbf`](https://github.com/mihonapp/mihon/commit/119bcbf8ed2415664922ea77fadf0da1165d1732))
- Filter by "Tracking" temporarily stuck after signing out of tracker ([@AntsyLich](https://github.com/AntsyLich)) ([#987](https://github.com/mihonapp/mihon/pull/987))
  - Fix login prompts despite being logged in to trackers in Manga screen ([@AntsyLich](https://github.com/AntsyLich)) ([`cbcd8bd`](https://github.com/mihonapp/mihon/commit/cbcd8bd6682023f728568f2b44da26124618aed7))
- JXL image downloading and loading ([@FooIbar](https://github.com/FooIbar)) ([#993](https://github.com/mihonapp/mihon/pull/993))
- Crash when using `%` in category name ([@Animeboynz](https://github.com/Animeboynz), [@FooIbar](https://github.com/FooIbar)) ([#1030](https://github.com/mihonapp/mihon/pull/1030))
- Fix item disappearing when fast scrolling ([@cuong-tran](https://github.com/cuong-tran)) ([#1035](https://github.com/mihonapp/mihon/pull/1035))
- Library is backed up while being disabled ([@AntsyLich](https://github.com/AntsyLich)) ([`56fb4f6`](https://github.com/mihonapp/mihon/commit/56fb4f62a152e87a71892aa68c78cac51a2c8596))
- Crash on list with only sticky header ([@cuong-tran](https://github.com/cuong-tran)) ([#1083](https://github.com/mihonapp/mihon/pull/1083))
- Crash when trying to clear cookies of some source ([@FooIbar](https://github.com/FooIbar)) ([#1084](https://github.com/mihonapp/mihon/pull/1084))
- MAL search results not showing start dates ([@MajorTanya](https://github.com/MajorTanya)) ([#1098](https://github.com/mihonapp/mihon/pull/1098))
- Android SDK 35 API collision ([@AntsyLich](https://github.com/AntsyLich)) ([`fdb9617`](https://github.com/mihonapp/mihon/commit/fdb96179c6373eb0a8e7d6daea671a315d5ce5f0))
- Manga next update calculation when considering custom fetch interval ([@cuong-tran](https://github.com/cuong-tran)) ([#1206](https://github.com/mihonapp/mihon/pull/1206))
- WheelPicker Manual Input ([@Animeboynz](https://github.com/Animeboynz)) ([#1209](https://github.com/mihonapp/mihon/pull/1209))
- EnhancedTracker not auto binding when adding manga to library ([@brewkunz](https://github.com/brewkunz)) ([#1298](https://github.com/mihonapp/mihon/pull/1298))
- Step count in settings slider ([@abdurisaq](https://github.com/abdurisaq)) ([#1356](https://github.com/mihonapp/mihon/pull/1356))
- Freezing in some screens due to blocking call ([@cuong-tran](https://github.com/cuong-tran)) ([#1364](https://github.com/mihonapp/mihon/pull/1364))
- Crash when removing non-existent tracked entry from tracker ([@cuong-tran](https://github.com/cuong-tran)) ([#1380](https://github.com/mihonapp/mihon/pull/1380))

### Other
- Code cleanup
  - Minor refactor of theming when expressions ([@MajorTanya](https://github.com/MajorTanya)) ([#396](https://github.com/mihonapp/mihon/pull/396))
  - Inside `WorkerInfoScreen` ([@AntsyLich](https://github.com/AntsyLich)) ([`5aec8f8`](https://github.com/mihonapp/mihon/commit/5aec8f8018236a38106483da08f9cbc28261ac9b))
  - Inside `ChapterDownloadIndicator`, `MangaChapterListItem` ([@AntsyLich](https://github.com/AntsyLich)) ([`b7e091d`](https://github.com/mihonapp/mihon/commit/b7e091d5d039e00cababc7daf555280df6cf9c03))
  - MangaCoverFetcher ([@ivaniskandar](https://github.com/ivaniskandar)) ([`1365695`](https://github.com/mihonapp/mihon/commit/13656959ae0606736f6ca9eb62699dc23e467c2f))
- Cleanup `LibraryScreenModel` `LibraryMap.applySort` and some more ([@AntsyLich](https://github.com/AntsyLich)) ([`2beb89d`](https://github.com/mihonapp/mihon/commit/2beb89d53163a6d288f8acdebe0f5d26fea8ab3e))
- Address `overridePendingTransition` deprecation ([@MajorTanya](https://github.com/MajorTanya)) ([#410](https://github.com/mihonapp/mihon/pull/410))
- Prioritize extension classes and files over app ([@beer-psi](https://github.com/beer-psi)) ([#433](https://github.com/mihonapp/mihon/pull/433))
- Use compose pager implementation ([@ivaniskandar](https://github.com/ivaniskandar)) ([`84984ef`](https://github.com/mihonapp/mihon/commit/84984ef7e1d7242924120cd2f171cb9dd75bc916))
- Switch to coil3 from coil2 ([@ivaniskandar](https://github.com/ivaniskandar)) ([`f72b6e4`](https://github.com/mihonapp/mihon/commit/f72b6e4d7c1f2f93d705402e4d80c94160bef54d))
  - Fix GIF not playing ([@jobobby04](https://github.com/jobobby04)) ([`59bedb3`](https://github.com/mihonapp/mihon/commit/59bedb33ff59ad5db1df2e93567a2266fb63eacc))
- Accommodate db for sync support ([@kaiserbh](https://github.com/kaiserbh)) ([#450](https://github.com/mihonapp/mihon/pull/450))
- Fix webtoon last visible item position calculation ([@FooIbar](https://github.com/FooIbar)) ([#562](https://github.com/mihonapp/mihon/pull/562))
- Migrate from `com.google.accompanist:accompanist-webview` to `io.github.kevinnzou:compose-webview` ([@sirlag](https://github.com/sirlag)) ([#569](https://github.com/mihonapp/mihon/pull/569))
- Rewrite migrations ([@ghostbear](https://github.com/ghostbear)) ([#577](https://github.com/mihonapp/mihon/pull/577))
  - Further improve migration ([@ghostbear](https://github.com/ghostbear)) ([#588](https://github.com/mihonapp/mihon/pull/588))
  - Fix migrations not running ([@ghostbear](https://github.com/ghostbear)) ([#604](https://github.com/mihonapp/mihon/pull/604))
  - Fix MigratorTest after updating to Kotlin 2 ([@cuong-tran](https://github.com/cuong-tran)) ([#896](https://github.com/mihonapp/mihon/pull/896))
  - Add MigratorTest to build script ([@cuong-tran](https://github.com/cuong-tran)) ([#896](https://github.com/mihonapp/mihon/pull/896))
  - Fix UI freeze after migration ([@AntsyLich](https://github.com/AntsyLich)) ([`3f1d28c`](https://github.com/mihonapp/mihon/commit/3f1d28c3833e6b868152149ed02b3fb8c54eccef))
  - Fix some migrations never running ([@MajorTanya](https://github.com/MajorTanya), [@AntsyLich](https://github.com/AntsyLich)) ([#1030](https://github.com/mihonapp/mihon/pull/1030))
- Add ProGuard rule to keep `mihon` namespace classes ([@MajorTanya](https://github.com/MajorTanya)) ([#605](https://github.com/mihonapp/mihon/pull/605))
- Use gradle plugins to share build configuration instead of subprojects ([@AntsyLich](https://github.com/AntsyLich)) ([`e448e40`](https://github.com/mihonapp/mihon/commit/e448e40406e8d9916120a278e42829a6f1b25a7a))
- Remove dependency on compose material 2 components ([@AntsyLich](https://github.com/AntsyLich)) ([`fb94230`](https://github.com/mihonapp/mihon/commit/fb9423028eb017c110cb805f2d0601e5b02e50f9))
- Upload PR build artifacts to GitHub ([@FooIbar](https://github.com/FooIbar)) ([#941](https://github.com/mihonapp/mihon/pull/941))
- Refactor archive support with libarchive ([@FooIbar](https://github.com/FooIbar)) ([#949](https://github.com/mihonapp/mihon/pull/949))
  - Add safeguard to prevent ArchiveInputStream from being closed twice ([@null2264](https://github.com/null2264)) ([#967](https://github.com/mihonapp/mihon/pull/967))
  - Move archive related code to :core:archive ([@AntsyLich](https://github.com/AntsyLich)) ([`bd7b354`](https://github.com/mihonapp/mihon/commit/bd7b35419861df6d426d6ec0a188391910d0f615))
- Replace detekt with ktlint via spotless ([@AntsyLich](https://github.com/AntsyLich)) ([#1130](https://github.com/mihonapp/mihon/pull/1130), [#1136](https://github.com/mihonapp/mihon/pull/1136), [#1138](https://github.com/mihonapp/mihon/pull/1138))
  - Refrain from running spotless on weblate files ([@AntsyLich](https://github.com/AntsyLich)) ([`32d2c2a`](https://github.com/mihonapp/mihon/commit/32d2c2ac1bc224cbda2f09a4023d7d120ea0e954))
- Use feature flags in compose compiler plugin ([@AntsyLich](https://github.com/AntsyLich)) ([`8f9a325`](https://github.com/mihonapp/mihon/commit/8f9a325895bb7b94c2ec92dd969094fc30b3b5e2))
- PagerPageHolder: lazy init loading indicator ([@AntsyLich](https://github.com/AntsyLich), [@ivaniskandar](https://github.com/ivaniskandar)) ([`a45eb5e`](https://github.com/mihonapp/mihon/commit/a45eb5e5288159dbbbbb5f92140ce0dd32a8f3ab))
- Collect MangaScreen state with lifecycle ([@AntsyLich](https://github.com/AntsyLich), [@ivaniskandar](https://github.com/ivaniskandar)) ([`03eb756`](https://github.com/mihonapp/mihon/commit/03eb756ecba0692d88d3a76254afc4c157fa225b))
- Add stable marker to Manga data class ([@AntsyLich](https://github.com/AntsyLich), [@ivaniskandar](https://github.com/ivaniskandar)) ([`03eb756`](https://github.com/mihonapp/mihon/commit/03eb756ecba0692d88d3a76254afc4c157fa225b))
- Use DTOs to parse tracking API responses ([@MajorTanya](https://github.com/MajorTanya)) ([#1103](https://github.com/mihonapp/mihon/pull/1103))
  - Fix Kitsu ratingTwenty being typed as String ([@MajorTanya](https://github.com/MajorTanya)) ([#1191](https://github.com/mihonapp/mihon/pull/1191))
  - Fix Kitsu `synopsis` nullability ([@MajorTanya](https://github.com/MajorTanya)) ([#1233](https://github.com/mihonapp/mihon/pull/1233))
  - Fix AniList `ALSearchItem.status` nullibility ([@Secozzi](https://github.com/Secozzi)) ([#1297](https://github.com/mihonapp/mihon/pull/1297))
- Migrate some classpaths to gradle plugins ([@AntsyLich](https://github.com/AntsyLich)) ([`fc1c804`](https://github.com/mihonapp/mihon/commit/fc1c804bfda1d76c0399bbb6214e75b3def951cc))
- Add crashlytics to standard builds ([@AntsyLich](https://github.com/AntsyLich)) ([`3c611b9`](https://github.com/mihonapp/mihon/commit/3c611b95fb79e5ac972019b76c7b24f46a3087fd))
- Switch to stable compose ([@AntsyLich](https://github.com/AntsyLich)) ([`2baffa6`](https://github.com/mihonapp/mihon/commit/2baffa62cade1abd978d5fd03151b47fc87fd31e))
- Switch from inorichi injekt to kohesive Injekt ([@AntsyLich](https://github.com/AntsyLich)) ([#1205](https://github.com/mihonapp/mihon/pull/1205))
  - Use custom injekt register with inorichi patch ([@AntsyLich](https://github.com/AntsyLich)) ([`83fd474`](https://github.com/mihonapp/mihon/commit/83fd4746eda1b99f35292b0c2211e606a421b3eb))
- Use TextFieldState in BasicTextField where applicable (p) ([#1201](https://github.com/mihonapp/mihon/pull/1201))
- Bump NDK version ([@AntsyLich](https://github.com/AntsyLich)) ([#1203](https://github.com/mihonapp/mihon/pull/1203))
- Move firebase permission removal to standard flavor ([@AntsyLich](https://github.com/AntsyLich)) ([`be671b4`](https://github.com/mihonapp/mihon/commit/be671b42cefd70180644e01bb065a18cb7701bf9))
- Adjust distinct checker in WidgetManager and run on default dispatcher (p) ([`9b8ab6a`](https://github.com/mihonapp/mihon/commit/9b8ab6acc25a5f99c9c5eebf9cc250975931c57c))
- Update resources exclusion rules (p) ([`481cfed`](https://github.com/mihonapp/mihon/commit/481cfedf08576cecfbb35616837bd8f627d8f959))
- Bump compile sdk to 35 (p) ([`37419cd`](https://github.com/mihonapp/mihon/commit/37419cdc26c2b5c4f8583fc2ba439b08fab42856))
- ChapterNavigator: dispatch page change only when needed (p) ([`f84d9a0`](https://github.com/mihonapp/mihon/commit/f84d9a08b4af768b1e9920c43cc445c86f5427fc))
- Remove usage of deprecated accompanist SystemUiController ([@AntsyLich](https://github.com/AntsyLich)) ([`2ba3f06`](https://github.com/mihonapp/mihon/commit/2ba3f0612c08c7021fed2f6d96cd538da2f34a13))
- Run PR check when base strings are changed ([@AntsyLich](https://github.com/AntsyLich)) ([`4051f18`](https://github.com/mihonapp/mihon/commit/4051f180a2e36e8a2cde6c55f0bea7952fdc4704))
  - Fix PR build check ([@AntsyLich](https://github.com/AntsyLich)) ([`9503082`](https://github.com/mihonapp/mihon/commit/9503082d44b5bd868ee1bfc42741dc978d1d9047))
- Cleanup .gitignore files ([@AntsyLich](https://github.com/AntsyLich)) ([`afa5002`](https://github.com/mihonapp/mihon/commit/afa50029882655af8d5eea40aed7644fce4564d8))
- Pass uncaught exception to default handler in GlobalExceptionHandler (so it's reported to crashlytics) ([@AntsyLich](https://github.com/AntsyLich)) ([`f3a2f56`](https://github.com/mihonapp/mihon/commit/f3a2f566c8a09ab862758ae69b43da2a2cd8f1db))

## [v0.16.5] - 2024-04-09
### Added
- Relative date for up to a week in the future ([@sirlag](https://github.com/sirlag)) ([#415](https://github.com/mihonapp/mihon/pull/415))
- Advance setting to install custom color profiles ([@wwww-wwww](https://github.com/wwww-wwww)) ([#523](https://github.com/mihonapp/mihon/pull/523))

### Changed
- Permanently enable 32-bit color mode ([@wwww-wwww](https://github.com/wwww-wwww)) ([#523](https://github.com/mihonapp/mihon/pull/523))

### Fixed
- Wrong dates in Updates and History tab due to time zone issues ([@sirlag](https://github.com/sirlag)) ([#402](https://github.com/mihonapp/mihon/pull/402))
  - Fix extra date header introduced by parent PR ([@sirlag](https://github.com/sirlag)) ([#415](https://github.com/mihonapp/mihon/pull/415))
  - Fix build time in about screen displayed in UTC ([@AntsyLich](https://github.com/AntsyLich)) ([`aed53d3`](https://github.com/mihonapp/mihon/commit/aed53d3bdc85ce0e899fbb90b9f9cad0f1b86480))
- App infinitely retries tracker update instead of failing after 3 tries ([@MajorTanya](https://github.com/MajorTanya)) ([#411](https://github.com/mihonapp/mihon/pull/411))
- Crash on Pixel devices (was introduced due to compose update) ([@AntsyLich](https://github.com/AntsyLich)) ([`ab06720`](https://github.com/mihonapp/mihon/commit/ab067209661eceefc04c65f6bdbfcaa8a1264651))
- Crash when opening some heif/heic images ([@az4521](https://github.com/az4521)) ([#466](https://github.com/mihonapp/mihon/pull/466))
- Crash when putting app in background while track date selection dialog is open ([@ivaniskandar](https://github.com/ivaniskandar)) ([`c348fac`](https://github.com/mihonapp/mihon/commit/c348fac78fac479fb123bd617c01c78b9ca851d5))
- Dates for saved images not following the specification (fixes date issue mainly on Samsung devices) ([@MajorTanya](https://github.com/MajorTanya)) ([#552](https://github.com/mihonapp/mihon/pull/552))
- Colors getting distorted when opening CMYK jpeg images ([@wwww-wwww](https://github.com/wwww-wwww)) ([#523](https://github.com/mihonapp/mihon/pull/523))

## [v0.16.4] - 2024-02-27
### Changed
- Don't include custom user agent for MAL (circumvents MAL block) ([@AntsyLich](https://github.com/AntsyLich)) ([`085ad8d`](https://github.com/mihonapp/mihon/commit/085ad8d44637c375a8ed24aba3a6f75f5b0cc9ee))

## [v0.16.3] - 2024-01-30
### Added
- Copy extension debug info when clicking logo or name in the extension details screen ([@MajorTanya](https://github.com/MajorTanya)) ([#271](https://github.com/mihonapp/mihon/pull/271))

### Changed
- Hide display cutoff setting in reader settings sheet if fullscreen is disabled ([@Riztard](https://github.com/Riztard)) ([#241](https://github.com/mihonapp/mihon/pull/241))
- Library update error filename to `mihon_update_errors.txt` from `tachiyomi_update_errors.txt` ([@mjishnu](https://github.com/mjishnu)) ([#253](https://github.com/mihonapp/mihon/pull/253))

### Fixed
- Bottom sheet UI issues on non-tablet devices ([@theolm](https://github.com/theolm)) ([#182](https://github.com/mihonapp/mihon/pull/182))
- Crash when switching screen while a list is scrolling ([@theolm](https://github.com/theolm)) ([#272](https://github.com/mihonapp/mihon/pull/272))
- Newly installed extensions not being recognized by Mihon ([@AwkwardPeak7](https://github.com/AwkwardPeak7)) ([#275](https://github.com/mihonapp/mihon/pull/275))
- Failing to refresh MAL token being inferred as token expiration ([@AntsyLich](https://github.com/AntsyLich)) ([`0f4de03`](https://github.com/mihonapp/mihon/commit/0f4de03d7a77b52490dc9a95e96a308b93b26e4f))

### Other
- Add `detekt` (kotlin code analyzer) to the project ([@theolm](https://github.com/theolm)) ([#216](https://github.com/mihonapp/mihon/pull/216))

## [v0.16.2] - 2024-01-28
### Changed
- Backup now contains scanlator filter of a series ([@jobobby04](https://github.com/jobobby04)) ([#166](https://github.com/mihonapp/mihon/pull/166))
- App icon scaling ([@AntsyLich](https://github.com/AntsyLich)) ([`26815c7`](https://github.com/mihonapp/mihon/commit/26815c7356111394665467c1e81255ac9ee33c1a))
- Tracker OAuth client to Mihon's (fixes login issue for Shikimori tracker) ([@AntsyLich](https://github.com/AntsyLich)) ([`e3f33e2`](https://github.com/mihonapp/mihon/commit/e3f33e24f5e928ac8a85d1f500fd42d4715fc6b5))
- Tracker user agents ([@AntsyLich](https://github.com/AntsyLich), [@kitsumed](https://github.com/kitsumed)) ([`e3f33e2`](https://github.com/mihonapp/mihon/commit/e3f33e24f5e928ac8a85d1f500fd42d4715fc6b5))
- Crash log filename to `mihon_crash_logs.txt` from `tachiyomi_crash_logs.txt` ([@MajorTanya](https://github.com/MajorTanya)) ([#234](https://github.com/mihonapp/mihon/pull/234))
- Don't try to refresh MAL token after refresh token expires ([@AntsyLich](https://github.com/AntsyLich)) ([`32188f9`](https://github.com/mihonapp/mihon/commit/32188f9f65009a18250674ef1bd6e57d351c1fba))

### Fixed
- "Flash screen on page change" making the screen full black ([@AntsyLich](https://github.com/AntsyLich)) ([`38d6ab8`](https://github.com/mihonapp/mihon/commit/38d6ab80ce868707829dbc81de4170afe3c2f2a5))
- Faulty MangaUpdates score in database ([@AntsyLich](https://github.com/AntsyLich)) ([`a024218`](https://github.com/mihonapp/mihon/commit/a024218410953a389b8af4880fa7ae6cc30124a2))
- Updating extension not reflecting correctly ([@AntsyLich](https://github.com/AntsyLich)) ([`cb06898`](https://github.com/mihonapp/mihon/commit/cb068984303f811692531bf6f14902ae118d8ac7))
- Inconsistent button height in "Data and storage" for some languages ([@theolm](https://github.com/theolm)) ([#202](https://github.com/mihonapp/mihon/pull/202))
- Chapter not being marked as read locally when refreshing Enhanced Trackers ([@Secozzi](https://github.com/Secozzi)) ([#219](https://github.com/mihonapp/mihon/pull/219))

### Other
- Make `last_modified_at` field in database be `0` on insert ([@kaiserbh](https://github.com/kaiserbh)) ([#113](https://github.com/mihonapp/mihon/pull/113))
- Remove usage of `.not()` where possible in code ([@AntsyLich](https://github.com/AntsyLich)) ([`3940740`](https://github.com/mihonapp/mihon/commit/39407407f282dbb7fa972b12053c26b3e3bd66d8))
- Use type-safe project accessors ([@theolm](https://github.com/theolm)) ([#194](https://github.com/mihonapp/mihon/pull/194))
- Legacy tracker model properties now has the same type as the domain ones ([@AntsyLich](https://github.com/AntsyLich)) ([#245](https://github.com/mihonapp/mihon/pull/245))

## [v0.16.1] - 2024-01-18
### Changed
- Branding to Mihon (for references we missed) ([@AntsyLich](https://github.com/AntsyLich)) ([`6539406`](https://github.com/mihonapp/mihon/commit/653940613d661eb371aab3b3c3a8181e4e308c43))
- Preview builds are now called Beta builds ([@AntsyLich](https://github.com/AntsyLich)) ([`3c3a1cd`](https://github.com/mihonapp/mihon/commit/3c3a1cd448ab1f653ddd12b2afe0cba38968d1b9))

### Fixed
- App icon not following the [specification](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive) ([@AntsyLich](https://github.com/AntsyLich)) ([`1849715`](https://github.com/mihonapp/mihon/commit/18497154183356bb0d469b27827f9f7d6b7a3130))
- MangaUpdates default score being set to -1.0 ([@AntsyLich](https://github.com/AntsyLich)) ([`99fd273`](https://github.com/mihonapp/mihon/commit/99fd2731f5d9d374700e89fa67d4d5bf611bbafa))

## [v0.16.0] - 2024-01-16
### Changed
- Branding to Mihon ([@AntsyLich](https://github.com/AntsyLich))
- Minimum supported Android version to 8 ([@AntsyLich](https://github.com/AntsyLich)) ([`dfb3091`](https://github.com/mihonapp/mihon/commit/dfb3091e380dda3e9bfb64bf5c9a685cf3a03d0e))

[unreleased]: https://github.com/mihonapp/mihon/compare/v0.18.0...main
[v0.18.0]: https://github.com/mihonapp/mihon/compare/v0.17.1...v0.18.0
[v0.17.1]: https://github.com/mihonapp/mihon/compare/v0.17.0...v0.17.1
[v0.17.0]: https://github.com/mihonapp/mihon/compare/v0.16.5...v0.17.0
[v0.16.5]: https://github.com/mihonapp/mihon/compare/v0.16.4...v0.16.5
[v0.16.4]: https://github.com/mihonapp/mihon/compare/v0.16.3...v0.16.4
[v0.16.3]: https://github.com/mihonapp/mihon/compare/v0.16.2...v0.16.3
[v0.16.2]: https://github.com/mihonapp/mihon/compare/v0.16.1...v0.16.2
[v0.16.1]: https://github.com/mihonapp/mihon/compare/v0.16.0...v0.16.1
[v0.16.0]: https://github.com/mihonapp/mihon/compare/a9c7cbf...v0.16.0
