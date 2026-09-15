# T9 continuous reader soak

This evidence covers the headless reader pipeline (decoding, session paging/mode changes, cross-chapter transitions, persistence, cache and memory budgets). It does not measure Compose frame rendering or UI latency.

## Internal opt-in

Existing guarded `--verify-reader=<fixture>` entry only:

- `MIHON_W_READER_VERIFY=1`
- `MIHON_W_READER_SOAK_SECONDS=1800` (absent means no soak; accepted range 1–3600)
- `MIHON_W_READER_SOAK_OUTPUT=<new absolute JSONL file>`

`ReaderSoak.kt` streams elapsed monotonic time, PID, completed cycles, decoded tile counts, Java heap used/committed, and reader core reserved/cache/limit bytes. Java heap uses `Runtime.totalMemory/freeMemory` from java.base because the packaged compact Java 17 runtime excludes java.management. Core high-water remains sampled by the existing independent verifier poller.

Each cycle evicts unpinned cache entries before decoding the deterministic standalone/directory/CBZ/CBT/CB7/CBR/EPUB matrix. This includes two distinct animated GIF frames, an extreme-image region, transparency checks, and corrupt-image isolation. It then opens the six reading modes, selects pages, crosses a chapter boundary, flushes and reopens progress. No restart occurs between cycles. No deliberate idle period replaces decoding work.

## Provenance

Controlled appimage copy: `.superpowers/sdd/reader-soak-20260915-01/MihonW`. Existing packaged Java 17, native dependencies and launcher are retained; project JARs were refreshed from this worktree's builds to include the internal soak entry. This is a controlled appimage experiment, not final installer acceptance. Hashes and hardware are retained beside the run.

Fixture: `desktop-app/build/verification/suwayomi-reader-prerelease-20260915-01/fixture/reader-fixture-manifest.json` (validated by the verifier before reading). User profiles are not touched.

Hardware: Intel Core i9-13980HX, 24 cores / 32 logical processors; 33,954,439,168 bytes RAM; Windows 11 build 26220. The application keeps the existing packaged JVM settings (no test-specific heap sizing).

## Preflight history

- Initial 2-second test correctly completed one full matrix cycle; assertion requiring two cycles was too strict for the decode duration. Corrected to require at least one whole decoded cycle and at least the initial matrix's tile count. `64164` passed in 1m14s through the shared Gradle wrapper.
- EXE preflights exposed missing java.management; this was an instrumentation dependency, fixed by using java.base heap counters. Failed preflights and their distinct PIDs are excluded from the 30-minute measurement.

The continuous run and plateau analysis are recorded below after completion.

## Root run 01 invalidated

The new actual app-image preflight (`reader-soak-smoke-final-01`) exited0 with two complete cycles, 76 decoded tiles in9.22s. The subsequent long run (`reader-soak-1800-final-01`, launcher21396/JVM53948) exposed repeated uncaught `selected index 7 is outside 1 pages` errors. It was stopped at1323.15s/274cycles/10412decoded tiles. This is **not a passed soak**, despite continued sampling and bounded observed memory. Raw stderr,214process samples and invalidation marker are retained. Independent asynchronous ReaderSession actions could execute Next before an earlier no-op SelectPage; the stale selection then addressed a one-page chapter. A deterministic reversed-dispatcher regression and action-ordering fix are being added. The verification launcher now rejects uncaught asynchronous stderr even if an EXE were to return0.

## Action-ordering correction

`DefaultReaderSession.dispatch` now enqueues actions in a single session-owned channel. A single consumer applies them under the existing session lock in submission order. Chapter-bound navigation/selection/viewport actions carry the chapter ID observed by the caller and are dropped if the session has since opened a different chapter. Close/cancel cancels the queue. This fixes a real caller-visible race rather than delaying or weakening the verifier. Two deterministic tests reproduce both reverse scheduling and an explicit chapter switch before queued selection; both failed before the fix and passed afterwards together with existing session tests (72832,27s).
