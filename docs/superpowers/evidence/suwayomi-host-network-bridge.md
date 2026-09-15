# Suwayomi host network bridge evidence

Worktree: `D:\my project\mihon-w\.worktrees\suwayomi`
Date: 2026-09-15

## Delivered

- `NetworkHelper.client` resolves and caches an exclusive OkHttp client per `(extension package, source ID)` instead of binding the global Injekt singleton to whichever source accessed it first. A client captured during extension construction keeps that package identity even without a source ID.
- Engine initialization installs its `BrokeredHttpClient` before creating the compatibility NetworkHelper.
- `BrokerTransport` is the first application interceptor. It reads the final `RealCall.client` (including additions made by extension `newBuilder()`), then runs the remaining application interceptors followed by the network interceptors on a broker-terminating Chain. It never enters OkHttp's socket connection path. Both request modification and response/image transformations remain active.
- OkHttp 5.5's Chain option-copy methods return wrapped Chains, so `withDns` and similar calls cannot re-enter the real transport. Internal API use is isolated to RealCall.client with `@OptIn(OkHttpInternalApi::class)`.
- Requests retain HTTP method, binary POST bytes, repeated headers, package and source identity. Responses retain binary bytes, repeated headers and final redirected URL.
- While the suspend IPC callback runs, the synchronous interceptor checks Call cancellation every 10 ms and cancels the callback coroutine. Response bodies are standard closeable in-memory OkHttp bodies and can be transformed by extension image interceptors.
- BrokeredHttpClient also supplies execution context to native SDK requests, materializes broker file responses, and lets native get/post decode either inline or file-backed base64 text. Filenames must match `broker-UUID.bin`; symlink files/directories are rejected; reads are bounded at 64 MiB and files are deleted after consumption.

## Tests

Red: the first `BrokerTransportTest` run failed at compilation because BrokerTransport did not exist. The first runnable assertion then detected case normalization in OkHttp's header multimap; the contract now compares header names case-insensitively.

Green command (shared repository Gradle mutex):

```powershell
& .superpowers/sdd/run-gradle.ps1 -GradleArguments @(':extension-host:test','--tests','mihon.extension.host.BrokerTransportTest','--tests','mihon.extension.compat.SourceImagePipelineTest','--tests','eu.kanade.tachiyomi.network.NetworkHelperCompatibilityTest')
```

Results: BrokerTransportTest 5/5 pass; SourceImagePipelineTest 2/2 pass; NetworkHelperCompatibilityTest 2 pass, 2 skipped because their external APK prerequisites are absent. No failed tests.

Contracts cover derived application/network interceptor ordering without real DNS, binary POST, repeated request/response headers, final URL, cancellation of a suspended callback, file response validation/deletion, image response transformation and close, and distinct package/source client caching.

## Explicit transport boundary

The parent process owns connection establishment, cookies, session headers, redirects and proxy policy. Host `Chain.connection()` returns null because a parent connection cannot be represented as a live host socket. Extension interceptors receive and can transform requests/responses, but host socket/TLS/connection-pool customizations do not move to the parent. Chain timeout values are visible to downstream interceptors; broker transport timeout policy remains parent-owned. This is not a claim of emulating a real host network connection or of validating every third-party APK.

No commits were created. Parent-owned IPC schema and DesktopNetworkHelper were not modified by this subtask.
