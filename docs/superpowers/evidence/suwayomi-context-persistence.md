# Suwayomi T1 host Context persistence

Scope: `D:\my project\mihon-w\.worktrees\suwayomi`, `codex/suwayomi-evolution`.

## Behavior

- Extension construction resolves an Application bound to its working directory before invoking a SourceFactory. The existing distinct factory initialization and atomic source registration remain intact.
- Application stores private files, cache and typed preferences under the extension work directory. Preference names are URL-safe base64 encoded.
- SharedPreferences persist via a temporary file, fsync and atomic replacement (replacement fallback when atomic move is unavailable). commit returns disk success; apply updates and persists synchronously. Null values remove entries, clear precedes editor puts, editor reuse does not repeat clear, and sets/maps use defensive copies.
- ExtensionExecutionContext binds package, source and Application identity across coroutine dispatch and RxJava scheduling. Injekt returns the actual Application so captured Context references retain their identity on worker threads.
- AssetManager opens/lists bundled assets inside the extension directory. UTF-8 length-prefixed preference strings support large JSON values.
- Unloading cancels only that extension request scopes, clears source/context/preference models and closes its loader. Reload also cancels old request scopes before swapping the registry.

## Files

- extension-host/src/main/kotlin/android/app/Application.kt
- extension-host/src/main/kotlin/android/content/Context.kt
- extension-host/src/main/kotlin/android/content/res/AssetManager.kt
- extension-host/src/main/kotlin/android/content/FileSharedPreferences.kt
- extension-host/src/main/kotlin/mihon/extension/host/ExtensionExecutionContext.kt
- extension-host/src/main/kotlin/mihon/extension/host/ExtensionHostEngine.kt
- extension-host/src/test/kotlin/mihon/extension/host/ExtensionContextPersistenceTest.kt
- extension-host/src/test/kotlin/mihon/extension/host/ExtensionHostEngineTest.kt

## Verification

Root owns all serial Gradle execution. Initial ContextPersistenceTest red: 3/3 failed as expected for missing private storage/persistence and set isolation. The original three are green. Second red run confirmed assets and large-string failures, then those passed. Third red run: 14 tests, exactly three failures (Rx identity, unload command, active request cancellation). Final verification: `.superpowers/sdd/run-gradle.ps1 -GradleArguments @(':extension-host:test')` passed (34 tests, 10 suites, 0 failures/errors). ContextPersistenceTest: 6/6; ExtensionHostEngineTest: 8/8. This includes the large-string, assets, Rx identity, unload route and active request cancellation regressions.

## Limitations being tracked

- apply is synchronous, so large writes can briefly block callers.
- Corrupt preference files fail explicitly rather than silently dropping user settings.
- Threads created directly by extension code outside coroutine/Rx schedulers do not automatically inherit identity; captured Context/client references remain valid.
- Cross-process simultaneous writers are not supported; desktop host owns extension execution.


