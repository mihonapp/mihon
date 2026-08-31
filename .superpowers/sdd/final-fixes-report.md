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
