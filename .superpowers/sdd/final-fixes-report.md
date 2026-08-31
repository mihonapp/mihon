# Desktop foundation final fixes

## Scope

Implemented the final whole-branch review findings in the `windows-foundation`
worktree only:

- Compose packaging resolves the configured Java 17 Gradle toolchain and assigns
  its home through Compose's public `application.javaHome` DSL. Gradle itself
  was run with the requested JDK 21 `JAVA_HOME`.
- Preference loading now returns `DesktopPreferences()` when `Properties.load`
  cannot read or parse the file. The original file is moved to a uniquely named
  `preferences.properties.corrupt-<UUID>` sibling; if the move cannot happen,
  it stays in place for diagnosis.
- Blank `APPDATA` is treated as unavailable, so installed mode preserves the
  resolver's actionable `--data-dir=<path>` error. A supplied data directory
  still takes precedence.
- The desktop verification script reads the packaged runtime's `release` file
  and fails unless `JAVA_VERSION` is Java 17.

## TDD evidence

### RED

Command (with `JAVA_HOME=C:\Users\18734\.codex\toolchains\temurin-21\jdk-21.0.12.1+1`):

```powershell
.\gradlew.bat :desktop-app:test --tests 'mihon.desktop.preferences.DesktopPreferenceStoreTest' --tests 'mihon.desktop.DesktopRuntimeFactoryTest' --no-daemon
```

Result: expected failure, 7 tests run and 3 failed. The malformed `\\u12G4`
preference test threw `IllegalArgumentException`; blank `APPDATA` caused an
`InvalidPathException` even with an explicit data directory; installed mode
with blank `APPDATA` did not return the resolver's required error.

### GREEN

After the minimal recovery and blank-value handling changes, the same focused
command passed. The full desktop test suite also passed:

```powershell
.\gradlew.bat :desktop-app:test --no-daemon
```

Result: `BUILD SUCCESSFUL`.

## Packaging and verification evidence

The Compose 1.12 public DSL exposes `JvmApplication.setJavaHome(String)`, and
the build uses Gradle's public `JavaToolchainService.launcherFor` plus
`JavaLanguageVersion` to resolve the project's Java 17 toolchain. No machine
path is hard-coded.

The final packaging verification was run after stopping the Gradle daemon, with
the requested JDK 21 runtime:

```powershell
$env:JAVA_HOME = 'C:\Users\18734\.codex\toolchains\temurin-21\jdk-21.0.12.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --stop
.\scripts\verify-desktop-foundation.ps1
```

Result: `BUILD SUCCESSFUL`; the script ran `spotlessCheck`,
`:desktop-app:test`, and `:desktop-app:createDistributable`, then completed the
packaged launcher smoke test. It reported:

```text
Packaged runtime JAVA_VERSION=17.0.18
Mihon W desktop foundation verification passed.
```

The packaged metadata was inspected directly at:

```text
desktop-app\build\compose\binaries\main\app\MihonW\runtime\release
JAVA_VERSION="17.0.18"
```

`git diff --check` completed with exit code 0 before commit.

## Notes

The Gradle output retains pre-existing informational warnings about incubating
Gradle features and a deprecated Compose `material3` dependency accessor. They
are unrelated to this change and do not fail either required gate.

## Smoke timeout follow-up

The packaged smoke process no longer uses `Start-Process -Wait`. The verifier
starts it with `-PassThru`, calls `Process.WaitForExit(30 * 1000)`, and on
timeout calls `Kill()`, waits up to a further five seconds for that termination
to take effect, then throws a clear timeout error. The existing exit-code and
smoke data-root assertions run only after normal process completion.

### RED/GREEN guard

Before the change, a source-level regression guard failed with:

```text
Smoke verifier uses unbounded Start-Process -Wait
```

After the change, the same guard confirmed one `Start-Process` invocation with
`-PassThru`, no `-Wait`, a bounded `WaitForExit`, and `Kill()` on timeout. It
passed in both Windows PowerShell 5.1 and PowerShell 7.6.4. The verifier AST
also parsed successfully in both versions:

```text
parser passed: 5.1.26100.9278
parser passed: 7.6.4
```

### Final verifier run

With the approved JDK 21 Gradle runtime, the daemon was stopped and the full
verifier was run again:

```powershell
$env:JAVA_HOME = 'C:\Users\18734\.codex\toolchains\temurin-21\jdk-21.0.12.1+1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --stop
.\scripts\verify-desktop-foundation.ps1
```

Result: `BUILD SUCCESSFUL`; `spotlessCheck`, `:desktop-app:test`,
`:desktop-app:createDistributable`, Java 17 runtime metadata validation, and
the bounded packaged smoke test all passed. The output included
`Packaged runtime JAVA_VERSION=17.0.18` and
`Mihon W desktop foundation verification passed.`

## Desktop navigation layout follow-up

The `NavigationRail` inside `DesktopShell` was previously only constrained by
height inside a `Row`, allowing it to consume the full row width and leave the
following content `Box` without displayable width. It now explicitly uses
`Modifier.fillMaxHeight().width(80.dp)`, preserving a fixed desktop rail and
available space for the selected destination headline.

### Correction

The earlier source-text width guard was an exploratory, untracked check rather
than a Compose UI regression test. It must not be treated as proof of the
layout behavior. It is superseded by the committed Compose Desktop UI test
documented below.

### Build and package gates

With `JAVA_HOME` set to the approved JDK 21 runtime, this command passed:

```powershell
.\gradlew.bat :desktop-app:test :desktop-app:compileKotlin :desktop-app:spotlessCheck --no-daemon
```

Result: `BUILD SUCCESSFUL`.

After stopping the Gradle daemon, the full verifier was run again and passed:

```powershell
.\gradlew.bat --stop
.\scripts\verify-desktop-foundation.ps1
```

Result: `BUILD SUCCESSFUL`; `spotlessCheck`, `:desktop-app:test`,
`:desktop-app:createDistributable`, the packaged smoke test, and Java runtime
metadata validation passed (`Packaged runtime JAVA_VERSION=17.0.18`).

This change has not been claimed as visually verified here. A controller must
relaunch the packaged desktop app and inspect a fresh screenshot/click flow to
confirm the selected destination headline is visible.

## Compose Desktop layout regression test correction

`desktop-app` now declares Compose 1.12's supported
`compose.desktop.uiTestJUnit4` test dependency. The committed
`DesktopShellTest` uses `androidx.compose.ui.test.v2.runComposeUiTest` to
render `DesktopShell` inside a bounded `1280.dp x 800.dp` container. Stable
semantics tags identify the navigation rail and selected headline. The test
asserts that the headline has positive width and that its left bound is at or
to the right of the rail's right bound.

### Behavioral RED

With the real UI test in place, the rail's `width(80.dp)` modifier was
temporarily removed and the exact focused command was run:

```powershell
.\gradlew.bat :desktop-app:test --tests 'mihon.desktop.ui.DesktopShellTest' --no-daemon
```

Result: `BUILD FAILED`; `DesktopShellTest > selected headline has positive
bounds beside the navigation rail()` failed, with `1 test completed, 1 failed`.
This verifies the test observes the actual Compose layout regression rather
than source text.

### Behavioral GREEN

After restoring `width(80.dp)`, the same real UI test was rerun without using
the Gradle build cache:

```powershell
.\gradlew.bat :desktop-app:test --tests 'mihon.desktop.ui.DesktopShellTest' --rerun-tasks --no-daemon
```

Result: `BUILD SUCCESSFUL`; all 12 tasks executed and the single Compose UI
test passed.

### Full gates

```powershell
.\gradlew.bat :desktop-app:test :desktop-app:compileKotlin :desktop-app:spotlessCheck --no-daemon
```

Result: `BUILD SUCCESSFUL`. The JUnit XML results report 17 tests, 0 failures,
0 errors, and 0 skipped, including `DesktopShellTest`.

After stopping the daemon, the full verifier was rerun with the approved JDK
21 runtime:

```powershell
.\gradlew.bat --stop
.\scripts\verify-desktop-foundation.ps1
```

Result: `BUILD SUCCESSFUL`; formatting, tests, distributable creation, bounded
smoke verification, and Java 17 metadata validation passed. The verifier
reported `Packaged runtime JAVA_VERSION=17.0.18` and
`Mihon W desktop foundation verification passed.`
