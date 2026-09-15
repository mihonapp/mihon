# Windows extension isolation evidence — 2026-09-15

Scope: T10, actual native Windows host. Synthetic local fixture packages only; no real user library or production source was used. No commit was created.

## Implementation and Runtime wiring

`WindowsExtensionProcessManager` preserves its public API. Windows launches are fail-closed AppContainer launches; there is no Windows stdio/unrestricted fallback. Each loaded package is routed to a separate private manager, work directory, AppContainer SID, pair of named pipes, and Job Object. Source IDs route to the owning package; unloading one package closes only that host. An empty control host preserves existing start/ping/epoch lifecycle behavior.

Parent constructs `WindowsExtensionProcessManager(directory, onBrokerHttp = network::executeBrokeredRequest, networkHelper = network, onWebView = webViewManager::handleExtensionRequest)`. HTTP callbacks retain package/source checks and large-response file transfer. WebView callbacks require an exact loaded package and non-null registered source and forward the typed response. Child managers inherit these callbacks.

`WindowsAppContainerLauncher` creates a zero-capability AppContainer, stages a private runtime copy (avoids modifying administrator-owned Program Files), grants that SID runtime/classpath read+execute and its work directory modify, and starts suspended. It verifies TokenIsAppContainer, assigns the Job Object, prepares IPC, then resumes. The environment exposes private temp/profile paths and the nonce, not the parent environment. The Job Object sets a default one-process limit, job memory quota, and kill-on-close; `WindowsJobObject(activeProcessLimit = 32)` is available for the separate Chromium process tree.

Two one-way pipes avoid synchronous duplex blocking. The parent creates pipes under `\\.\pipe\Sessions\<session>\AppContainerNamedObjects\<SID>\...`; child uses `\\.\pipe\LOCAL\...`. `GetAppContainerNamedObjectPath` returned the relative AppContainerNamedObjects/SID namespace on this machine, so ProcessIdToSessionId supplies the session prefix. DACL grants SYSTEM, parent user and exact child SID; remote pipe clients are rejected, instances are exclusive. Each client PID must match the suspended child PID, then both sides verify a fresh nonce before IpcSession traffic.

## Verified native behavior

Before implementation, the real JVM extension host token probe was red (TokenIsAppContainer false). Native setup then reproduced and fixed administrator-owned runtime ACL denial and incorrect pipe namespace/file-not-found failures. The completed native run before the final memory-quota addition passed WindowsExtensionIsolationTest 4/4 and WindowsExtensionProcessManagerTest 3 executed / 5 environment skips:

- actual extension host starts with TokenIsAppContainer and completes PING over authenticated named pipes;
- child writes its own work directory; reading a sibling secret or writing outside fails;
- direct loopback socket fails while the parent can connect to the same listening endpoint;
- closing its Job Object/launcher leaves no live child PID;
- two actual synthetic mext packages load in separate hosts, return source results, cannot overwrite the sibling work file, and unloading one leaves the other usable;
- local named pipe handshake accepts the expected PID and nonce.

The final command adds an OS memory-quota probe: 256 MiB Job quota, Java heap 32 MiB, JVM direct-memory allowance 1 GiB, request a 512 MiB direct allocation. The allocation was refused in the real child; the final green results are below. This validates allocation refusal, not an automatic terminate-on-quota policy.

Reproduce from the Suwayomi worktree:

```powershell
.superpowers/sdd/run-gradle.ps1 -GradleArguments @(':desktop-app:test','--tests','mihon.desktop.extension.WindowsExtensionIsolationTest','--tests','mihon.desktop.extension.WindowsExtensionProcessManagerTest','--tests','mihon.desktop.extension.SuwayomiWorkflowIntegrationTest')
```

## Cleanup and limitations

Normal close terminates the job, closes process/pipe/log handles, removes only this SID's grants, deletes its profile, and releases/deletes its own staged runtime cache after the final lease. Crashing the parent can leave staged cache directories even though kill-on-close terminates children. No external production mext or packaged installed EXE was tested here; packaged-process tests are explicitly skipped when their environment paths are missing.

Two early prototype runtime caches remain:

- `C:\Users\18734\AppData\Local\Temp\mihonw-sandbox-runtime-18305044064793164876`
- `C:\Users\18734\AppData\Local\Temp\mihonw-sandbox-runtime-4972787963644455935`

A PowerShell cleanup resolving and checking these exact own TEMP paths before `Remove-Item -LiteralPath ... -Recurse -Force` was rejected by automatic approval with `blocked by policy` and no further reason. It was not retried through another tool or alternate deletion mechanism. Read-only checks found no running Java executable from these caches and no remaining MihonW.Extension profile directories at that point. Subsequent normal launch/close cleanup concerns only resources created by those subsequent launches.

## Official API references

- [Implementing an AppContainer](https://learn.microsoft.com/en-us/windows/win32/secauthz/implementing-an-appcontainer)
- [GetAppContainerNamedObjectPath](https://learn.microsoft.com/en-us/windows/win32/api/securityappcontainer/nf-securityappcontainer-getappcontainernamedobjectpath)
- [GetNamedPipeClientProcessId](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-getnamedpipeclientprocessid)
- [CreateNamedPipe](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-createnamedpipea)

## Final native quota result

The combined run completed WindowsExtensionIsolationTest **5 tests / 0 failures / 0 skips** in 42.896 s, including the 256 MiB Job quota / 512 MiB direct-allocation rejection. WindowsExtensionProcessManagerTest completed 8 tests / 0 failures / 5 environment skips in 52.131 s. Its overall Gradle task was red solely because the new workflow test exposed a download error. Final joint run after those changes: **WindowsExtensionIsolationTest 6/6, 0 skips, 45.534 s; Workflow 1/1, 33.811 s; extension-host 45 tests, 0 failures/errors, 2 environment skips. BUILD SUCCESSFUL in 2m 1s.** Both desktop test suites had empty stderr. Wrong PID and wrong nonce were rejected while clients remained connected, and the native process-handle shutdown race no longer appeared.

Known JDK compatibility boundary: direct third-party calls to JDK 17 `java.io.File.createTempFile` may still fail on its volume-name-limit query in AppContainer. The host's image transfer and SharedPreferences calls now use NIO temporary files. No whole-volume permission was added and no third-party bytecode rewrite is claimed. Production extensions with additional filesystem/native/child-process requirements need explicit compatibility validation.

## CPU hard cap follow-up (2026-09-15)

`WindowsAppContainerLauncher(cpuRatePercent = 50)` passes an explicit per-extension-host cap to `WindowsJobObject`. The generic Job constructor defaults to `null`, so the existing Chromium call (2 GiB, 32 processes) does not silently inherit this extension policy. Existing memory, active-process and kill-on-close limits remain configured.

The native structure uses two DWORDs (8 bytes), information class 15, `ENABLE | HARD_CAP` (5), and percentage times 100. Configuration occurs before the suspended child resumes. Invalid rates fail validation; a requested cap on a non-Windows platform fails explicitly; native configuration failure closes the Job handle and reports the Win32 error with no unbounded fallback. This limits the job's share of processor cycles per scheduling interval, not a fixed number of CPU cores. See [Microsoft's CPU rate structure contract](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_cpu_rate_control_information).

Focused verification: `./.superpowers/sdd/run-gradle.ps1 -GradleArguments @(':desktop-app:test','--tests','mihon.desktop.extension.WindowsJobCpuLimitTest')`. The test queries the actual Windows kernel Job configuration for uncapped, 25%, and 50% jobs, and checks rejection of -1, 0, and 101. It creates no busy-loop process. Initial runs 29157 and 39198 stopped in unrelated `extension-host:compileKotlin` import errors before reaching this test; native verification remains pending until the integration build records its result. No package was rebuilt for this follow-up.

Final native CPU verification in root integration run 95124: **WindowsJobCpuLimitTest 2/2 passed, 0 skips**. Kernel queries returned `(ControlFlags,CpuRate)` of `(0,0)`, `(5,2500)`, `(5,5000)` for uncapped, 25%, and 50%; invalid rates were rejected. The desktop suite had one unrelated optional-soak JSON serialization failure, now fixed separately; it does not invalidate these native results. XML was retained under `desktop-app/build/verification/integration-20260915-01/desktop-app/` before rerun. CPU cap is implemented and natively verified; this is not a CPU-throughput benchmark.
