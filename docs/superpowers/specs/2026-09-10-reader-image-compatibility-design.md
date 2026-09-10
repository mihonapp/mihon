# MihonW Reader Image Compatibility Design

**Date:** 2026-09-10

**Status:** Approved for implementation

## Objective

Extend the Windows reader from the JDK ImageIO baseline to a complete manga-oriented image matrix without weakening its bounded-memory, cancellation, corrupt-page isolation, or Windows packaging guarantees.

The required read matrix is JPEG (RGB, grayscale, CMYK, progressive, embedded ICC and EXIF orientation), PNG, APNG, GIF, WebP (still and animated), AVIF, HEIF/HEIC, JPEG XL, BMP, and TIFF. File extensions and HTTP content types are hints only; actual dispatch uses bounded magic-byte inspection.

## Architecture

`PageDecoder` remains the reader-facing contract. A new composite decoder probes the encoded stream once, identifies its format, then selects a capability-bearing backend. The existing ImageIO decoder remains the bounded tiled implementation for formats with reliable `ImageReadParam` region support. TwelveMonkeys providers strengthen JPEG, TIFF, BMP, PNG, and WebP handling, including CMYK/ICC cases.

Formats or animation features not reliably handled by ImageIO are sent to a packaged Windows x64 codec worker. The worker is a child process with a binary stdin/stdout protocol and no network access. It returns metadata or premultiplied RGBA for one frame and one requested region. The desktop process starts it through the existing Windows process controls and places it in a Job Object with a memory limit, single-process limit, kill-on-close behavior, and per-request deadline. A worker failure becomes a typed page failure and never terminates the reader session.

The initial worker implementation may reuse a pinned redistributable codec distribution rather than adding codec logic to the Kotlin process. Its license manifest, hashes, architecture, and exact codec versions are part of the packaged artifact and verification report. The application must not depend on a system-installed codec.

## Format Detection and Capabilities

`ReaderImageFormat` represents JPEG, PNG, GIF, WEBP, AVIF, HEIF, JXL, BMP, TIFF, and UNKNOWN. `ImageFormatDetector` reads at most 64 bytes and recognizes signatures and ISO BMFF brands. Detection preserves the complete input for subsequent probing.

Each backend reports width, height, frame count, frame durations, orientation-normalized dimensions, alpha presence, and region-decode support. Orientation is applied exactly once before layout and page-size reporting. Animated formats expose already-composited logical frames, including disposal and blend behavior; durations are clamped to the existing safe animation range.

## Memory and Large Images

No backend may allocate outside the existing `BoundedReaderMemoryBudget` accounting. A full decoded raster remains limited to 16 MiB. Larger still images must use source-region decoding or a worker-generated region at the requested sample size. A large image without safe region support fails with `RegionUnavailable`; it never silently attempts a full decode.

Worker response headers include exact width, height, stride, and payload length. Kotlin validates all arithmetic before reserving memory or reading pixels. Oversized, truncated, mismatched, or late responses terminate and replace the worker. Temporary encoded files, if needed by a codec, live in a per-request private directory and are removed on success, failure, cancellation, and application exit.

Animated pages retain only the displayed frame and at most one replacement frame. Off-screen or background animations pause through the existing coordinator. APNG, animated WebP, and GIF use identical scheduling semantics.

## Color and Metadata

All output presented to Compose is 8-bit premultiplied sRGB BGRA. Embedded ICC profiles are honored where the selected codec exposes them. CMYK and YCCK JPEG are converted to sRGB. EXIF orientation is normalized during decode, and the public metadata reports the post-orientation dimensions. Unsupported auxiliary metadata is ignored rather than treated as image corruption.

## Error Handling

Unsupported formats map to `ReaderFailure.UnsupportedImage`. Malformed or truncated supported images map to `ReaderFailure.CorruptImage`. Dimension, pixel, encoded-input, decoded-output, and worker-response excesses map to `ReaderFailure.LimitExceeded`. Unsafe or unavailable region decoding maps to `ReaderFailure.RegionUnavailable`.

The reader UI preserves other pages when one page fails. It displays a localized error with retry, exposes the detected format and backend in debug diagnostics, and never renders a blank page indefinitely. Retrying discards the failed backend instance and re-probes the original encoded page.

## Packaging

Both MSI/EXE and portable distributions contain the same Windows x64 codec payload. Packaging verifies expected files and SHA-256 hashes before producing an artifact. Clean-machine verification runs with an empty `PATH` segment for codec tools to prove the application uses only its packaged runtime.

The Android application remains unchanged. New dependencies and runtime flags are scoped to `reader-core` tests and `desktop-app` packaging.

## Verification

Automated fixtures cover every required format plus RGB, grayscale, CMYK, progressive, alpha, ICC, EXIF orientation, animation disposal/blend, renamed extensions, wrong content types, truncation, malformed headers, oversized dimensions, long images, cancellation, worker crash, worker timeout, and memory-pressure recovery.

Every implementation change follows a red-green test cycle. Focused decoder tests run before the full `reader-core` and `desktop-app` suites. `spotlessCheck`, the constrained-heap extreme-image test, MSI/EXE/portable packaging, packaged-runtime format probes, and a launched reader smoke scenario are completion gates.

## Non-Goals

- Image encoding or conversion as a general-purpose user feature.
- Raw camera formats, SVG, PDF, PSD, or video containers in reader pages.
- Depending on Windows Store codecs or software installed elsewhere on the machine.
- Relaxing archive, input-size, raster-size, or memory-budget limits to accommodate a format.
