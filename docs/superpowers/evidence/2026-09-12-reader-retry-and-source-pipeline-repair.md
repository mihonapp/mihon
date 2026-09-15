# Reader retry and page download pipeline repair — 2026-09-12

## Context & Root Cause Analysis

Following user feedback on page download failures and unrecoverable reader errors ("页面下载失败，请检查网络或图源登录状态后重试。"):

1. **Stale/Corrupt Cache Retention on Retry**:
   - When a page failed to download or decode, corrupted bytes or HTML error pages written to disk cache were retained and repeatedly read.
   - The reader session's `retry` method failed to invalidate content in the underlying chapter source and did not reset session failure state to allow re-opening and re-fetching.

2. **Source Lifecycle Mismatch**:
   - During reader layout and frame decoding, new `OnlineChapterSource` instances were created without synchronizing page lists or cache eviction hooks.
   - Added `invalidate(pageId)` to `OnlineChapterSource` with cache locking to safely evict failed/corrupt page cache files.
   - Enforced SHA-256 digested cache filenames with image validation (`validateImageResponse`) and atomic writes (`.tmp` to `.img`).

3. **Reader Session Recovery**:
   - In `DefaultReaderSession`, if `retry(pageId)` is called while in `ReaderLoadState.Failed`, the chapter is re-opened via `openLocked` and scrolled back to the target retry page, successfully re-fetching the image from the remote source.

## Verification

1. **Regression & Unit Tests**:
   - `DefaultReaderSessionTest`: verified `retry reopens a chapter after visible page decoding failed`.
   - `OnlineChapterSourceTest`: verified `corrupt cached response is evicted and fetched again` and `force retry removes cached bytes before loading`.
   - `DesktopOnlineReaderIntegrationTest`: passed end-to-end.
   - Full test suite passed across all modules (313 tasks, zero failures).

2. **Live Installed Source Probing**:
   - Probed installed extensions with `scripts/probe-installed-sources.py` using the updated executable `%LOCALAPPDATA%\MihonW\MihonW.exe`:
     - **Everia.club** (Source `7698513740234984368`): Chapter `2026/09/09/niko-kawago-...` (Chapter 567) returned 87 pages. Page 0 fetched as real 168,062-byte WebP image.
     - **J-Novel** (Source `2482510125735992610`): Chapter 1 returned 33 pages. Page 0 fetched as real 588,130-byte WebP image.
     - **BiliManga (嗶哩漫畫)** (Source `7289707411592168382`): Chapter 111 returned 44 pages. Page 0 fetched as real 215,413-byte AVIF image.

3. **Packaging & Delivery**:
   - Generated `MihonW-0.1.3.msi` and `MihonW-0.1.3-windows-x64-portable.zip`.
   - Backed up installed application to `%LOCALAPPDATA%\MihonW-repair-backup-20260912-012734`.
   - Deployed updated application files to `%LOCALAPPDATA%\MihonW`, verified 292 files with 0 SHA-256 hash mismatches.
