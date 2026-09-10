# Reader Image Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded, packaged Windows reader support for JPEG, PNG/APNG, GIF, WebP, AVIF, HEIF/HEIC, JPEG XL, BMP, and TIFF.

**Architecture:** Keep `PageDecoder` as the public boundary, add magic-based format detection and capability-aware routing, strengthen the in-process ImageIO backend, and use a pinned packaged ImageMagick worker for formats or animation features that ImageIO cannot safely decode. Every output remains governed by the existing reader memory budget and typed failures.

**Tech Stack:** Kotlin/JVM, coroutines, JDK ImageIO, TwelveMonkeys ImageIO 3.13.1, ImageMagick 7.1.2-31 portable Q8 x64, Compose Desktop, JUnit/Kotest, Gradle/JPackage.

## Global Constraints

- Target Windows 10 22H2 and Windows 11 x64.
- Do not modify the Android image pipeline.
- Detect formats from at most 64 leading bytes; never trust filename or HTTP type alone.
- Preserve the 16 MiB full-raster ceiling, 256 MiB core budget, and 96 MiB Compose bridge budget.
- A decoder crash, timeout, malformed response, or corrupt page must not terminate the reader session.
- MSI, EXE, and portable ZIP must carry the same pinned codec payload and require no system codec installation.
- Preserve all unrelated uncommitted work in the shared `main` checkout.

---

### Task 1: Magic-Based Format Detection

**Files:**
- Create: `reader-core/src/main/kotlin/mihon/reader/image/ReaderImageFormat.kt`
- Create: `reader-core/src/main/kotlin/mihon/reader/image/ImageFormatDetector.kt`
- Create: `reader-core/src/test/kotlin/mihon/reader/image/ImageFormatDetectorTest.kt`
- Modify: `reader-core/src/main/kotlin/mihon/reader/source/ImageEntryPolicy.kt`

**Interfaces:**
- Produces: `enum class ReaderImageFormat { JPEG, PNG, GIF, WEBP, AVIF, HEIF, JXL, BMP, TIFF, UNKNOWN }`
- Produces: `fun ImageFormatDetector.detect(header: ByteArray): ReaderImageFormat`
- Consumes: encoded page bytes supplied through `BoundedPageInput`.

- [ ] **Step 1: Write failing signature tests** covering JPEG SOI, PNG, GIF87a/89a, RIFF/WEBP, JXL codestream/container, BMP, little/big-endian TIFF, and ISO-BMFF `avif`, `avis`, `heic`, `heix`, `hevc`, `hevx`, `mif1`, and `msf1` brands. Include renamed files and random bytes.
- [ ] **Step 2: Run** `./gradlew.bat :reader-core:test --tests mihon.reader.image.ImageFormatDetectorTest --no-daemon --max-workers=1 --console=plain`; expect assertion failures because the detector does not exist.
- [ ] **Step 3: Implement** fixed-offset, bounds-checked comparisons and compatible-brand scanning limited to the supplied 64-byte header. Map `mif1`/`msf1` without an AV1 brand to HEIF.
- [ ] **Step 4: Extend** `ImageEntryPolicy` so `.avif`, `.heif`, `.heic`, `.jxl`, `.tif`, and `.tiff` are accepted while safety still relies on content probing at decode time.
- [ ] **Step 5: Re-run** the focused test and `:reader-core:spotlessCheck`; expect zero failures.

### Task 2: Strengthened In-Process ImageIO Backend

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `reader-core/build.gradle.kts`
- Modify: `reader-core/src/main/kotlin/mihon/reader/image/ImageIoPageDecoder.kt`
- Modify: `reader-core/src/main/kotlin/mihon/reader/image/ImageContracts.kt`
- Modify: `reader-core/src/test/kotlin/mihon/reader/image/ImageFixtures.kt`
- Modify: `reader-core/src/test/kotlin/mihon/reader/image/ImageIoPageDecoderTest.kt`

**Interfaces:**
- Extends: `ImageMetadata` with `format`, `hasAlpha`, and `orientationApplied` defaults that preserve source compatibility.
- Produces: a deterministic ImageIO provider selector that prefers TwelveMonkeys for JPEG/TIFF/BMP/WebP and retains the proven GIF compositor.

- [ ] **Step 1: Add failing tests** for grayscale JPEG, progressive JPEG, CMYK/YCCK JPEG fixture, alpha PNG, TIFF, still WebP, provider fallback, and a provider that lies about region support.
- [ ] **Step 2: Run** the focused `ImageIoPageDecoderTest`; expect missing-provider or wrong-color failures.
- [ ] **Step 3: Add** pinned TwelveMonkeys `imageio-core`, `imageio-metadata`, `imageio-jpeg`, `imageio-tiff`, `imageio-bmp`, and `imageio-webp` dependencies at 3.13.1.
- [ ] **Step 4: Implement** provider selection, normalized premultiplied sRGB output, capability probing, and typed mapping for provider exceptions. Keep reservation-before-allocation behavior unchanged.
- [ ] **Step 5: Re-run** focused tests, all `reader-core` tests, and `:reader-core:spotlessCheck`; expect zero failures and no leaked memory reservations.

### Task 3: Packaged Codec Worker and Composite Routing

**Files:**
- Create: `reader-core/src/main/kotlin/mihon/reader/image/CompositePageDecoder.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/reader/codec/CodecWorkerProtocol.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/reader/codec/PackagedCodecPageDecoder.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/reader/codec/CodecWorkerProtocolTest.kt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/reader/codec/PackagedCodecPageDecoderTest.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/reader/DesktopReaderFactory.kt`

**Interfaces:**
- Produces: `CompositePageDecoder(formatDetector, imageIoDecoder, packagedCodecDecoder)`.
- Produces: length-prefixed metadata and BGRA response validation with exact checked arithmetic.
- Produces: `PackagedCodecPageDecoder` that invokes only the application-relative codec executable.

- [ ] **Step 1: Add failing routing tests** asserting JPEG/PNG/GIF/TIFF use ImageIO when capable and APNG/animated WebP/AVIF/HEIF/JXL use the packaged backend. Assert UNKNOWN fails before process launch.
- [ ] **Step 2: Add failing protocol tests** for truncated headers, negative/overflowing sizes, wrong stride, excess payload, timeout, nonzero exit, cancellation, and stderr truncation.
- [ ] **Step 3: Run** both focused test classes; expect missing-type failures.
- [ ] **Step 4: Implement** the composite router and process-backed decoder using `ProcessBuilder(List<String>)`, private temporary directories, bounded stdout/stderr, five-second metadata and thirty-second decode deadlines, cancellation-triggered process termination, and strict response validation before memory reservation.
- [ ] **Step 5: Wire** the composite decoder in `DesktopReaderFactory` without changing `PageLoadCoordinator` callers.
- [ ] **Step 6: Re-run** focused tests, `:reader-core:test`, `:desktop-app:test`, and both Spotless checks; expect zero failures.

### Task 4: Full Format and Animation Matrix

**Files:**
- Create: `desktop-app/src/test/resources/reader-images/README.md`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/reader/codec/ReaderImageCompatibilityMatrixTest.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/DecodedReaderPage.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/ui/reader/AnimatedPage.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/i18n/DesktopStrings.kt`

**Interfaces:**
- Consumes: `PageDecoder` metadata and frame APIs without format-specific UI branches.
- Produces: the same animation visibility and foreground semantics for GIF, APNG, animated WebP, AVIF sequences, and HEIF sequences when the codec reports multiple frames.

- [ ] **Step 1: Add failing matrix tests** using redistributable or generated fixtures for every required still format, alpha, orientation, ICC/CMYK, renamed extension, corrupt/truncated input, and every supported animated format with frame pixels and durations asserted.
- [ ] **Step 2: Run** `ReaderImageCompatibilityMatrixTest`; expect failures for formats not yet decoded by the packaged backend.
- [ ] **Step 3: Implement** worker command construction for metadata JSON and frame/region BGRA output, coalescing animated frames and applying auto-orientation and sRGB conversion exactly once.
- [ ] **Step 4: Add** localized unsupported/corrupt/limit/timeout messages and ensure retry recreates the worker-backed request.
- [ ] **Step 5: Re-run** matrix, reader UI tests, full desktop tests, and Spotless; expect every matrix row to pass.

### Task 5: Reproducible Codec Packaging

**Files:**
- Create: `gradle/reader-codec.gradle.kts`
- Create: `desktop-app/src/main/resources/codec/THIRD-PARTY-NOTICES.txt`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/reader/codec/PackagedCodecInventoryTest.kt`
- Modify: `desktop-app/build.gradle.kts`
- Modify: `scripts/verify-desktop-reader.ps1`

**Interfaces:**
- Produces: Gradle tasks `downloadReaderCodec`, `verifyReaderCodec`, and `stageReaderCodec`.
- Pins: `ImageMagick-7.1.2-31-portable-Q8-x64.7z` with SHA-256 `4eb7914050902c52bf388bae188fdbdb04154ca89d105b2f6200a29cb774241b`.

- [ ] **Step 1: Add a failing inventory test** that requires application-relative `codec/magick.exe`, delegates for HEIC, JXL, PNG, TIFF, and WEBP, the license/notice file, and the pinned inventory hash.
- [ ] **Step 2: Run** the inventory test against a staged distribution; expect missing-codec failure.
- [ ] **Step 3: Implement** download-to-Gradle-cache, SHA-256 verification before extraction, selected-file staging, generated inventory, JPackage resource inclusion, and portable-ZIP inclusion. Never execute an unverified download.
- [ ] **Step 4: Run** `verifyReaderCodec`, createDistributable, MSI/EXE packaging, and portable ZIP packaging; expect all codec files in each artifact.
- [ ] **Step 5: Run** the packaged application with codec-related `PATH` entries removed and execute the complete fixture matrix through the packaged runtime.

### Task 6: Bounded-Memory and Failure Recovery Gates

**Files:**
- Modify: `reader-core/src/test/kotlin/mihon/reader/image/ImageIoPageDecoderTest.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/reader/codec/PackagedCodecPageDecoderTest.kt`
- Modify: `scripts/verify-desktop-reader.ps1`
- Modify: `docs/superpowers/evidence/windows-reader-core.md`

**Interfaces:**
- Verifies: no compatibility backend bypasses `ReaderLimits`, `BoundedReaderMemoryBudget`, cancellation, or single-page failure isolation.

- [ ] **Step 1: Add failing recovery tests** for a 20,000×20,000 supported image, decompression bomb metadata, full-decode refusal, worker allocation excess, worker hang, worker crash, cancellation, and successful decode after worker replacement.
- [ ] **Step 2: Run** focused tests and confirm each new assertion fails for the intended missing guard.
- [ ] **Step 3: Add** only the guards and cleanup needed to pass: checked dimensions/stride/payload, reservation before read, process-tree termination, bounded temporary files, and fresh-worker retry.
- [ ] **Step 4: Run** `:reader-core:extremeImageTest` with its constrained heap, all reader/desktop tests, both Spotless checks, and `git diff --check`.
- [ ] **Step 5: Package** MSI, EXE, and portable ZIP; launch the packaged app; run the reader scenario across the full matrix; record exact commands, artifact hashes, test counts, memory high-water, and any unsupported animation limitation in the evidence document.

## Completion Gate

The work is complete only when every format-matrix row passes from the packaged application, all focused and full tests pass, constrained-heap verification passes, formatting passes, all three Windows distributions contain the verified codec payload, and an actual packaged launch reads representative local, CBZ, EPUB, and online pages without a system codec dependency.
