# Reader Image Compatibility Checkpoint — 2026-09-10

## Implemented

- Magic-byte detection for JPEG, PNG/APNG, GIF, WebP, AVIF, HEIF/HEIC, JPEG XL, BMP, and TIFF.
- Composite decoding through bounded ImageIO, pure-Java APNG composition, and a packaged ImageMagick worker.
- TwelveMonkeys 3.13.1 providers for the in-process fallback matrix.
- Auto-orientation and sRGB normalization for JPEG/TIFF and packaged modern formats.
- Process timeout, process-tree cleanup, bounded stdout/stderr, exact BGRA payload validation, and typed failures.
- Pinned ImageMagick 7.1.2-31 Q8 x64 download with SHA-256 verification and JPackage/portable staging.
- Pinned real HEIC fixture from libheif commit `08075aebcc0d9bf7d35f900c36114b1b6e90ed7d`.

Implementation commits:

- `2819cbf4b` — magic-based format detection
- `ad0cd744a` — packaged modern image pipeline
- `575856e71` — packaged codec inventory gate

## Verified

The following command passed with zero failures before the inventory-only follow-up test:

```powershell
./gradlew.bat :reader-core:test :desktop-app:test :reader-core:spotlessCheck :desktop-app:spotlessCheck --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

The real-codec matrix passed AVIF, JPEG XL, still/animated WebP, APNG, HEIC, EXIF orientation, CMYK JPEG, renamed extensions, and HEIC region sampling. `reader-core` reported 170 tests, 0 failures, 0 errors, and 1 skipped. The separate timeout/recovery and codec-inventory gates also passed.

All three Windows artifacts were built from the current mixed working tree. The unpacked portable application was launched with PID 57976 and remained alive after five seconds; that verifier-owned process was then stopped.

| Artifact | SHA-256 |
| --- | --- |
| `desktop-app/build/compose/binaries/main/exe/MihonW-0.1.3.exe` | `b1064592fbcb66a17f4d30a8c11f8af93067f6f5feb7faa953c07305173e6777` |
| `desktop-app/build/compose/binaries/main/msi/MihonW-0.1.3.msi` | `156ad9dc4ea0eb7ecbb28918a1e00b1840c4c20e5b799f7cbdb081651def1fdd` |
| `desktop-app/build/compose/binaries/main/portable/MihonW-0.1.3-windows-x64-portable.zip` | `3f0b7efbe6fc284fd50ed16ff9349dbb5e5467c63161cf45f9078492ca503474` |

The portable ZIP contains `codec/magick.exe`, `LICENSE.txt`, and `THIRD-PARTY-NOTICES.txt`. WiX decompilation confirmed the same three payload entries in the MSI.

## Continue From Here

This checkpoint is not the completion gate. Remaining work:

1. Add APNG partial-frame disposal/blend fixtures, malformed chunk/CRC tests, and cancellation coverage.
2. Add animated AVIF/HEIF sequence fixtures where the pinned codec can decode them and record limitations otherwise.
3. Extend the packaged CLI reader scenario across standalone, directory, CBZ, EPUB, corrupt-then-valid, and large-region cases using the new formats.
4. Run the constrained-heap extreme gate and the complete clean-machine reader verifier.
5. Rebuild, install, and interactively verify the final MSI/EXE/portable artifacts after the shared working tree is consolidated.
