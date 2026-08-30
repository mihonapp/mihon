# Windows Foundation Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce the first runnable Windows x64 Mihon W vertical slice: a Compose Desktop application with deterministic installed/portable data roots, atomic desktop preferences, safe window placement restoration, Material 3 desktop navigation, a self-contained runtime image, and a Windows CI gate.

**Architecture:** Add a separate `desktop-app` JVM entry-point module, following the post-AGP-9 split between shared libraries and platform application modules. Keep this slice independent of Android-only modules; later plans migrate domain and data modules behind platform contracts. All startup state is injected through small Kotlin classes so it can be tested without launching a UI.

**Tech Stack:** Kotlin 2.4.10, Java 17, Gradle 9.7.1, Compose Multiplatform 1.12.0, Compose Material 3, JUnit Jupiter 6.1.3, Kotest assertions, PowerShell, GitHub Actions.

## Global Constraints

- Preserve the existing Android application and keep its tests and APK assembly buildable.
- Target Windows 10 22H2 and Windows 11 on x64.
- Retain Mihon's Material 3 identity while using desktop navigation and Windows window behavior.
- Installed data defaults to `%APPDATA%\MihonW`; portable data defaults to a `data` directory beside the executable.
- The portable edition must not write user state to `%APPDATA%`.
- Use Java 17 and the repository's existing Kotlin 2.4.10 and Gradle 9.7.1 baselines.
- Use the stable `org.jetbrains.compose` Gradle plugin version `1.12.0`.
- This plan is a foundation slice, not completion of the Windows port. The full completion criteria remain those in `docs/superpowers/specs/2026-08-31-windows-port-design.md`.

## Program Roadmap

This specification contains several independently reviewable systems, so implementation is split into the following plans. Each plan must finish with working software and retain all earlier gates:

1. `2026-08-31-windows-foundation-shell.md` — this plan; runnable shell, platform data roots, preferences, window state, runtime image, and CI.
2. `2026-09-01-windows-shared-data-library.md` — migrate platform-neutral domain/data code, SQLite driver, local source, real backup import, library/detail/chapter screens.
3. `2026-09-02-windows-reader.md` — reader core, bounded decoding, all reading modes, progress, cache, and independent reader window.
4. `2026-09-03-windows-extension-platform.md` — extension SDK, manifest, named-pipe protocol, AppContainer launcher, repository sidecar index, and one real migrated extension.
5. `2026-09-04-windows-downloads-background.md` — persistent downloads, task center, cache, scheduled updates, and Windows notifications.
6. `2026-09-05-windows-history-tracking.md` — history, categories, trackers, browser authentication, credential storage, offline queue, and conflict resolution.
7. `2026-09-06-windows-backup-settings-quality.md` — compatible export, round-trip fixtures, all remaining settings, diagnostics, accessibility, performance, and failure recovery.
8. `2026-09-07-windows-release.md` — installer, portable ZIP, file associations, updater, rollback, clean-machine matrix, and formal release workflow.

The dates in the filenames establish ordering, not guaranteed delivery dates. A later plan may refine file placement after earlier code establishes authoritative interfaces, but it may not narrow the approved product scope.

### Full-objective coverage matrix

- Cross-platform architecture, shared domain/data extraction, SQLDelight desktop access, and local library behavior: Plan 2.
- Material 3 desktop navigation and window behavior begin in Plan 1; feature-complete screens are delivered with their owning vertical slices in Plans 2–7.
- Reader core, image decoding, all reading modes, progress, and reader-window behavior: Plan 3.
- `source-api`, extension SDK, `extension-host`, Windows AppContainer isolation, repository compatibility, and a real online source: Plan 4.
- Persistent downloads, caching, task center, scheduled updates, network restrictions, and notifications: Plan 5.
- History, categories, tracker authentication, credential storage, offline writes, and conflicts: Plan 6.
- Android-compatible backup export/round-trip, settings parity, recovery paths, diagnostics, accessibility, and performance criteria: Plan 7.
- Installer, portable ZIP, GitHub Releases updater, rollback, clean Windows 10/11 verification, and release evidence: Plan 8.
- Full user-feature parity is evaluated only after Plans 1–8 all pass the completion criteria in the approved design specification.

## File Structure for This Plan

- `desktop-app/build.gradle.kts` — desktop dependencies, JVM target, run configuration, and development runtime-image metadata.
- `desktop-app/src/main/kotlin/mihon/desktop/Main.kt` — process entry point and smoke-test exit path.
- `desktop-app/src/main/kotlin/mihon/desktop/DesktopRuntime.kt` — deterministic startup assembly.
- `desktop-app/src/main/kotlin/mihon/desktop/navigation/DesktopDestination.kt` — stable primary desktop destinations.
- `desktop-app/src/main/kotlin/mihon/desktop/navigation/DesktopNavigator.kt` — testable navigation state.
- `desktop-app/src/main/kotlin/mihon/desktop/platform/AppDirectories.kt` — installed/portable directory resolution and creation.
- `desktop-app/src/main/kotlin/mihon/desktop/preferences/DesktopPreferenceStore.kt` — atomic file-backed desktop preferences.
- `desktop-app/src/main/kotlin/mihon/desktop/window/WindowPlacement.kt` — platform-neutral window-bound validation.
- `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt` — Compose application window and state wiring.
- `desktop-app/src/main/kotlin/mihon/desktop/ui/DesktopShell.kt` — navigation rail and desktop content shell.
- `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopTheme.kt` — system-aware Material 3 theme.
- `desktop-app/src/test/kotlin/...` — unit tests mirroring each non-UI responsibility.
- `scripts/verify-desktop-foundation.ps1` — one-command Windows verification.
- `.github/workflows/build.yml` — Windows foundation job alongside the existing Android job.

---

### Task 1: Add the Desktop JVM Module and Stable Navigation Model

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `desktop-app/build.gradle.kts`
- Create: `desktop-app/src/test/kotlin/mihon/desktop/navigation/DesktopDestinationTest.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/navigation/DesktopDestination.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/navigation/DesktopNavigator.kt`

**Interfaces:**
- Consumes: repository version catalogs `libs` and `mihonx`.
- Produces: `enum class DesktopDestination`, `class DesktopNavigator`, Gradle tasks `:desktop-app:test`, `:desktop-app:run`, and `:desktop-app:createDistributable`.

- [ ] **Step 1: Register Compose Multiplatform 1.12.0 and the desktop module**

Add this entry under the existing `[versions]` table in `gradle/libs.versions.toml`:

```toml
compose-multiplatform = "1.12.0"
```

Add this entry under the existing `[plugins]` table in the same file:

```toml
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
```

Add this root plugin declaration to `build.gradle.kts`:

```kotlin
alias(libs.plugins.compose.multiplatform) apply false
```

Add this module include to `settings.gradle.kts` immediately after `include(":app")`:

```kotlin
include(":desktop-app")
```

Create `desktop-app/build.gradle.kts`:

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(mihonx.plugins.spotless)
}

kotlin {
    jvmToolchain(mihonx.versions.java.get().toInt())
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "mihon.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "MihonW"
            packageVersion = "0.1.0"
            description = "Mihon manga reader for Windows"
            vendor = "Mihon W"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.desktop", "java.logging", "java.prefs")

            windows {
                perUserInstall = true
                dirChooser = true
                menuGroup = "Mihon W"
                upgradeUuid = "07E02BEA-9179-4E54-A1AF-CFC185C91398"
            }
        }
    }
}
```

- [ ] **Step 2: Write the failing navigation contract test**

Create `desktop-app/src/test/kotlin/mihon/desktop/navigation/DesktopDestinationTest.kt`:

```kotlin
package mihon.desktop.navigation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DesktopDestinationTest {

    @Test
    fun `primary destinations preserve the approved desktop order`() {
        DesktopDestination.entries.map { it.label } shouldBe listOf(
            "Library",
            "Updates",
            "History",
            "Browse",
            "Downloads",
            "Settings",
            "About",
        )
    }

    @Test
    fun `navigator publishes a destination change once`() {
        val changes = mutableListOf<DesktopDestination>()
        val navigator = DesktopNavigator(DesktopDestination.Library, changes::add)

        navigator.navigate(DesktopDestination.Updates)
        navigator.navigate(DesktopDestination.Updates)

        navigator.current shouldBe DesktopDestination.Updates
        changes shouldBe listOf(DesktopDestination.Updates)
    }
}
```

- [ ] **Step 3: Run the test and verify the contract is absent**

Run:

```powershell
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.navigation.DesktopDestinationTest"
```

Expected: compilation fails with unresolved references to `DesktopDestination` and `DesktopNavigator`.

- [ ] **Step 4: Implement the minimal navigation model**

Create `desktop-app/src/main/kotlin/mihon/desktop/navigation/DesktopDestination.kt`:

```kotlin
package mihon.desktop.navigation

enum class DesktopDestination(val label: String, val shortLabel: String) {
    Library("Library", "L"),
    Updates("Updates", "U"),
    History("History", "H"),
    Browse("Browse", "B"),
    Downloads("Downloads", "D"),
    Settings("Settings", "S"),
    About("About", "A"),
}
```

Create `desktop-app/src/main/kotlin/mihon/desktop/navigation/DesktopNavigator.kt`:

```kotlin
package mihon.desktop.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class DesktopNavigator(
    initialDestination: DesktopDestination,
    private val onDestinationChanged: (DesktopDestination) -> Unit,
) {
    var current: DesktopDestination by mutableStateOf(initialDestination)
        private set

    fun navigate(destination: DesktopDestination) {
        if (destination == current) return
        current = destination
        onDestinationChanged(destination)
    }
}
```

- [ ] **Step 5: Run the desktop test and Android configuration checks**

Run:

```powershell
.\gradlew.bat :desktop-app:test :app:tasks --quiet
```

Expected: `DesktopDestinationTest` passes and the Android application module still configures successfully.

- [ ] **Step 6: Commit the module boundary**

```powershell
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml desktop-app
git commit -m "build: add Windows desktop application module"
```

---

### Task 2: Resolve Installed and Portable Application Directories

**Files:**
- Create: `desktop-app/src/test/kotlin/mihon/desktop/platform/AppDirectoryResolverTest.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/platform/AppDirectories.kt`

**Interfaces:**
- Consumes: `java.nio.file.Path` and the caller-provided `%APPDATA%` value/executable directory.
- Produces: `DistributionMode`, `AppDirectories`, `AppDirectoryResolver.resolve(mode, explicitRoot)`, and `AppDirectories.create()`.

- [ ] **Step 1: Write failing installed, portable, override, and creation tests**

Create `desktop-app/src/test/kotlin/mihon/desktop/platform/AppDirectoryResolverTest.kt`:

```kotlin
package mihon.desktop.platform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AppDirectoryResolverTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `installed mode roots state in APPDATA`() {
        val appData = tempDir.resolve("Roaming")
        val resolver = AppDirectoryResolver(appData, tempDir.resolve("bin"))

        resolver.resolve(DistributionMode.Installed).root shouldBe appData.resolve("MihonW")
    }

    @Test
    fun `portable mode roots state beside executable`() {
        val executableDir = tempDir.resolve("MihonW")
        val resolver = AppDirectoryResolver(tempDir.resolve("Roaming"), executableDir)

        resolver.resolve(DistributionMode.Portable).root shouldBe executableDir.resolve("data")
    }

    @Test
    fun `explicit data root wins in either mode`() {
        val explicit = tempDir.resolve("chosen")
        val resolver = AppDirectoryResolver(tempDir.resolve("Roaming"), tempDir.resolve("bin"))

        resolver.resolve(DistributionMode.Portable, explicit).root shouldBe explicit
    }

    @Test
    fun `create builds every required directory`() {
        val directories = AppDirectoryResolver(tempDir.resolve("Roaming"), tempDir.resolve("bin"))
            .resolve(DistributionMode.Installed)
            .create()

        listOf(
            directories.root,
            directories.cache,
            directories.logs,
            directories.extensions,
            directories.database,
        ).all(Files::isDirectory) shouldBe true
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```powershell
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.platform.AppDirectoryResolverTest"
```

Expected: compilation fails because `AppDirectoryResolver`, `DistributionMode`, and `AppDirectories` do not exist.

- [ ] **Step 3: Implement deterministic directory resolution**

Create `desktop-app/src/main/kotlin/mihon/desktop/platform/AppDirectories.kt`:

```kotlin
package mihon.desktop.platform

import java.nio.file.Files
import java.nio.file.Path

enum class DistributionMode {
    Installed,
    Portable,
}

data class AppDirectories(
    val root: Path,
    val cache: Path = root.resolve("cache"),
    val logs: Path = root.resolve("logs"),
    val extensions: Path = root.resolve("extensions"),
    val database: Path = root.resolve("database"),
) {
    fun create(): AppDirectories = apply {
        listOf(root, cache, logs, extensions, database).forEach(Files::createDirectories)
    }
}

class AppDirectoryResolver(
    private val appDataDirectory: Path?,
    private val executableDirectory: Path,
) {
    fun resolve(
        mode: DistributionMode,
        explicitRoot: Path? = null,
    ): AppDirectories {
        val root = explicitRoot ?: when (mode) {
            DistributionMode.Installed -> requireNotNull(appDataDirectory) {
                "APPDATA is unavailable; pass --data-dir=<path> to select a writable data directory"
            }.resolve("MihonW")
            DistributionMode.Portable -> executableDirectory.resolve("data")
        }
        return AppDirectories(root.toAbsolutePath().normalize())
    }
}
```

- [ ] **Step 4: Run the targeted and full desktop tests**

```powershell
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.platform.AppDirectoryResolverTest"
.\gradlew.bat :desktop-app:test
```

Expected: all four directory tests and all earlier desktop tests pass.

- [ ] **Step 5: Commit the directory policy**

```powershell
git add desktop-app/src/main/kotlin/mihon/desktop/platform desktop-app/src/test/kotlin/mihon/desktop/platform
git commit -m "feat: resolve Windows application data directories"
```

---

### Task 3: Add Atomic File-Backed Desktop Preferences

**Files:**
- Create: `desktop-app/src/test/kotlin/mihon/desktop/preferences/DesktopPreferenceStoreTest.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/preferences/DesktopPreferenceStore.kt`

**Interfaces:**
- Consumes: a preference-file `Path` and `DesktopDestination`.
- Produces: `ThemeMode`, `DesktopPreferences`, `DesktopPreferenceStore.load()`, and `DesktopPreferenceStore.save(preferences)`.

- [ ] **Step 1: Write failing default, round-trip, and corrupt-value tests**

Create `desktop-app/src/test/kotlin/mihon/desktop/preferences/DesktopPreferenceStoreTest.kt`:

```kotlin
package mihon.desktop.preferences

import io.kotest.matchers.shouldBe
import mihon.desktop.navigation.DesktopDestination
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopPreferenceStoreTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `missing file returns safe defaults`() {
        DesktopPreferenceStore(tempDir.resolve("preferences.properties")).load() shouldBe DesktopPreferences()
    }

    @Test
    fun `saved theme and destination survive reload`() {
        val file = tempDir.resolve("preferences.properties")
        val store = DesktopPreferenceStore(file)
        val expected = DesktopPreferences(ThemeMode.Dark, DesktopDestination.Browse)

        store.save(expected)

        DesktopPreferenceStore(file).load() shouldBe expected
        Files.exists(file.resolveSibling("preferences.properties.tmp")) shouldBe false
    }

    @Test
    fun `unknown enum values fall back independently`() {
        val file = tempDir.resolve("preferences.properties")
        Files.writeString(file, "theme=NEON\ndestination=UNKNOWN\n")

        DesktopPreferenceStore(file).load() shouldBe DesktopPreferences()
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

```powershell
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.preferences.DesktopPreferenceStoreTest"
```

Expected: compilation fails because the preference types do not exist.

- [ ] **Step 3: Implement atomic properties persistence**

Create `desktop-app/src/main/kotlin/mihon/desktop/preferences/DesktopPreferenceStore.kt`:

```kotlin
package mihon.desktop.preferences

import mihon.desktop.navigation.DesktopDestination
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class DesktopPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val lastDestination: DesktopDestination = DesktopDestination.Library,
)

class DesktopPreferenceStore(private val file: Path) {

    fun load(): DesktopPreferences {
        if (!Files.exists(file)) return DesktopPreferences()

        val properties = Properties().apply {
            Files.newInputStream(file).use { input -> load(input) }
        }
        return DesktopPreferences(
            themeMode = enumValueOrDefault(properties.getProperty("theme"), ThemeMode.System),
            lastDestination = enumValueOrDefault(
                properties.getProperty("destination"),
                DesktopDestination.Library,
            ),
        )
    }

    fun save(preferences: DesktopPreferences) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        val properties = Properties().apply {
            setProperty("theme", preferences.themeMode.name)
            setProperty("destination", preferences.lastDestination.name)
        }
        Files.newOutputStream(temporary).use { properties.store(it, "Mihon W desktop preferences") }
        try {
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file, REPLACE_EXISTING)
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
        return enumValues<T>().firstOrNull { it.name == value } ?: default
    }
}
```

- [ ] **Step 4: Run tests and format checks**

```powershell
.\gradlew.bat :desktop-app:test spotlessCheck
```

Expected: desktop tests and repository formatting checks pass.

- [ ] **Step 5: Commit preferences persistence**

```powershell
git add desktop-app/src/main/kotlin/mihon/desktop/preferences desktop-app/src/test/kotlin/mihon/desktop/preferences
git commit -m "feat: persist Windows desktop preferences"
```

---

### Task 4: Validate and Persist Window Placement

**Files:**
- Create: `desktop-app/src/test/kotlin/mihon/desktop/window/WindowPlacementTest.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/window/WindowPlacement.kt`
- Modify: `desktop-app/src/test/kotlin/mihon/desktop/preferences/DesktopPreferenceStoreTest.kt`
- Modify: `desktop-app/src/main/kotlin/mihon/desktop/preferences/DesktopPreferenceStore.kt`

**Interfaces:**
- Consumes: `ScreenBounds` and persisted integer fields.
- Produces: `WindowPlacement.sanitize(screenBounds)`, `DesktopPreferences.windowPlacement`, and complete preference round trips.

- [ ] **Step 1: Write failing placement sanitization tests**

Create `desktop-app/src/test/kotlin/mihon/desktop/window/WindowPlacementTest.kt`:

```kotlin
package mihon.desktop.window

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WindowPlacementTest {

    private val screen = ScreenBounds(x = 0, y = 0, width = 1920, height = 1080)

    @Test
    fun `valid placement is preserved`() {
        val placement = WindowPlacement(120, 80, 1280, 800, maximized = false)

        placement.sanitize(screen) shouldBe placement
    }

    @Test
    fun `undersized and offscreen placement is centered and enlarged`() {
        WindowPlacement(5000, 5000, 200, 100, maximized = false).sanitize(screen) shouldBe
            WindowPlacement(510, 240, 900, 600, maximized = false)
    }

    @Test
    fun `placement is limited to available screen size`() {
        WindowPlacement(0, 0, 4000, 3000, maximized = true).sanitize(screen) shouldBe
            WindowPlacement(0, 0, 1920, 1080, maximized = true)
    }
}
```

Extend the round-trip test in `DesktopPreferenceStoreTest.kt` so `expected` is:

```kotlin
val expected = DesktopPreferences(
    themeMode = ThemeMode.Dark,
    lastDestination = DesktopDestination.Browse,
    windowPlacement = WindowPlacement(120, 80, 1280, 800, maximized = true),
)
```

Add this import:

```kotlin
import mihon.desktop.window.WindowPlacement
```

- [ ] **Step 2: Run both tests and verify the new types/properties are absent**

```powershell
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.window.WindowPlacementTest" --tests "mihon.desktop.preferences.DesktopPreferenceStoreTest"
```

Expected: compilation fails for `ScreenBounds`, `WindowPlacement`, and `DesktopPreferences.windowPlacement`.

- [ ] **Step 3: Implement pure window-bound validation**

Create `desktop-app/src/main/kotlin/mihon/desktop/window/WindowPlacement.kt`:

```kotlin
package mihon.desktop.window

import kotlin.math.max

data class ScreenBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class WindowPlacement(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val maximized: Boolean,
) {
    fun sanitize(screen: ScreenBounds): WindowPlacement {
        val safeWidth = width.coerceIn(MIN_WIDTH.coerceAtMost(screen.width), screen.width)
        val safeHeight = height.coerceIn(MIN_HEIGHT.coerceAtMost(screen.height), screen.height)
        val intersectsScreen = x < screen.x + screen.width &&
            y < screen.y + screen.height &&
            x + safeWidth > screen.x &&
            y + safeHeight > screen.y
        val safeX = if (intersectsScreen) x.coerceIn(screen.x, screen.x + screen.width - safeWidth) else {
            screen.x + max(0, (screen.width - safeWidth) / 2)
        }
        val safeY = if (intersectsScreen) y.coerceIn(screen.y, screen.y + screen.height - safeHeight) else {
            screen.y + max(0, (screen.height - safeHeight) / 2)
        }
        return copy(x = safeX, y = safeY, width = safeWidth, height = safeHeight)
    }

    private companion object {
        const val MIN_WIDTH = 900
        const val MIN_HEIGHT = 600
    }
}
```

- [ ] **Step 4: Replace preference persistence with window-aware persistence**

Replace `desktop-app/src/main/kotlin/mihon/desktop/preferences/DesktopPreferenceStore.kt` with:

```kotlin
package mihon.desktop.preferences

import mihon.desktop.navigation.DesktopDestination
import mihon.desktop.window.WindowPlacement
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class DesktopPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val lastDestination: DesktopDestination = DesktopDestination.Library,
    val windowPlacement: WindowPlacement? = null,
)

class DesktopPreferenceStore(private val file: Path) {

    fun load(): DesktopPreferences {
        if (!Files.exists(file)) return DesktopPreferences()

        val properties = Properties().apply {
            Files.newInputStream(file).use { input -> load(input) }
        }
        return DesktopPreferences(
            themeMode = enumValueOrDefault(properties.getProperty("theme"), ThemeMode.System),
            lastDestination = enumValueOrDefault(
                properties.getProperty("destination"),
                DesktopDestination.Library,
            ),
            windowPlacement = properties.readWindowPlacement(),
        )
    }

    fun save(preferences: DesktopPreferences) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        val properties = Properties().apply {
            setProperty("theme", preferences.themeMode.name)
            setProperty("destination", preferences.lastDestination.name)
            preferences.windowPlacement?.let { placement ->
                setProperty("window.x", placement.x.toString())
                setProperty("window.y", placement.y.toString())
                setProperty("window.width", placement.width.toString())
                setProperty("window.height", placement.height.toString())
                setProperty("window.maximized", placement.maximized.toString())
            }
        }
        Files.newOutputStream(temporary).use { properties.store(it, "Mihon W desktop preferences") }
        try {
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file, REPLACE_EXISTING)
        }
    }

    private fun Properties.readWindowPlacement(): WindowPlacement? {
        val x = getProperty("window.x")?.toIntOrNull() ?: return null
        val y = getProperty("window.y")?.toIntOrNull() ?: return null
        val width = getProperty("window.width")?.toIntOrNull() ?: return null
        val height = getProperty("window.height")?.toIntOrNull() ?: return null
        val maximized = getProperty("window.maximized")?.toBooleanStrictOrNull() ?: false
        return WindowPlacement(x, y, width, height, maximized)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
        return enumValues<T>().firstOrNull { it.name == value } ?: default
    }
}
```

- [ ] **Step 5: Run placement and persistence tests**

```powershell
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.window.WindowPlacementTest" --tests "mihon.desktop.preferences.DesktopPreferenceStoreTest"
```

Expected: all placement and persistence tests pass.

- [ ] **Step 6: Commit window placement support**

```powershell
git add desktop-app/src/main/kotlin/mihon/desktop/window desktop-app/src/test/kotlin/mihon/desktop/window desktop-app/src/main/kotlin/mihon/desktop/preferences desktop-app/src/test/kotlin/mihon/desktop/preferences
git commit -m "feat: restore safe Windows window placement"
```

---

### Task 5: Assemble a Testable Desktop Runtime and Smoke Entry Point

**Files:**
- Create: `desktop-app/src/test/kotlin/mihon/desktop/DesktopRuntimeFactoryTest.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/DesktopRuntime.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/Main.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt` as the temporary bootable window replaced in Task 6.

**Interfaces:**
- Consumes: process arguments, environment, and executable directory.
- Produces: `DesktopRuntime`, `DesktopRuntimeFactory.create(args, environment, executableDirectory)`, `--portable`, `--data-dir=<path>`, and `--smoke-test`.

- [ ] **Step 1: Write failing runtime assembly tests**

Create `desktop-app/src/test/kotlin/mihon/desktop/DesktopRuntimeFactoryTest.kt`:

```kotlin
package mihon.desktop

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopRuntimeFactoryTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `portable runtime never uses APPDATA`() {
        val executableDir = tempDir.resolve("portable")

        val runtime = DesktopRuntimeFactory.create(
            args = arrayOf("--portable"),
            environment = mapOf("APPDATA" to tempDir.resolve("Roaming").toString()),
            executableDirectory = executableDir,
        )

        runtime.directories.root shouldBe executableDir.resolve("data").toAbsolutePath().normalize()
        Files.isDirectory(runtime.directories.cache) shouldBe true
    }

    @Test
    fun `explicit data directory is honored for smoke tests`() {
        val chosen = tempDir.resolve("smoke-data")

        val runtime = DesktopRuntimeFactory.create(
            args = arrayOf("--smoke-test", "--data-dir=$chosen"),
            environment = emptyMap(),
            executableDirectory = tempDir.resolve("bin"),
        )

        runtime.smokeTest shouldBe true
        runtime.directories.root shouldBe chosen.toAbsolutePath().normalize()
    }
}
```

- [ ] **Step 2: Run the test and verify runtime assembly is absent**

```powershell
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.DesktopRuntimeFactoryTest"
```

Expected: compilation fails because `DesktopRuntimeFactory` does not exist.

- [ ] **Step 3: Implement explicit startup assembly**

Create `desktop-app/src/main/kotlin/mihon/desktop/DesktopRuntime.kt`:

```kotlin
package mihon.desktop

import mihon.desktop.platform.AppDirectories
import mihon.desktop.platform.AppDirectoryResolver
import mihon.desktop.platform.DistributionMode
import mihon.desktop.preferences.DesktopPreferenceStore
import java.nio.file.Path

data class DesktopRuntime(
    val directories: AppDirectories,
    val preferences: DesktopPreferenceStore,
    val smokeTest: Boolean,
)

object DesktopRuntimeFactory {
    fun create(
        args: Array<String>,
        environment: Map<String, String>,
        executableDirectory: Path,
    ): DesktopRuntime {
        val explicitRoot = args.firstOrNull { it.startsWith("--data-dir=") }
            ?.substringAfter('=')
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
        val mode = if ("--portable" in args) DistributionMode.Portable else DistributionMode.Installed
        val appData = environment["APPDATA"]?.let(Path::of)
        val directories = AppDirectoryResolver(appData, executableDirectory)
            .resolve(mode, explicitRoot)
            .create()
        return DesktopRuntime(
            directories = directories,
            preferences = DesktopPreferenceStore(directories.root.resolve("preferences.properties")),
            smokeTest = "--smoke-test" in args,
        )
    }
}
```

- [ ] **Step 4: Add the process entry point**

Create `desktop-app/src/main/kotlin/mihon/desktop/Main.kt`:

```kotlin
package mihon.desktop

import androidx.compose.ui.window.application
import mihon.desktop.ui.MihonDesktopApp
import java.nio.file.Path

fun main(args: Array<String>) {
    val command = ProcessHandle.current().info().command().orElse(null)
    val executableDirectory = command?.let(Path::of)?.parent
        ?: Path.of(System.getProperty("user.dir"))
    val runtime = DesktopRuntimeFactory.create(args, System.getenv(), executableDirectory)

    if (runtime.smokeTest) {
        println("MIHON_DESKTOP_SMOKE_OK ${runtime.directories.root}")
        return
    }

    application {
        MihonDesktopApp(runtime)
    }
}
```

At this point `Main.kt` intentionally fails to compile because `MihonDesktopApp` is introduced in Task 6. To keep Task 5 independently green, also create this temporary file:

`desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt`

```kotlin
package mihon.desktop.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import mihon.desktop.DesktopRuntime

@Composable
fun ApplicationScope.MihonDesktopApp(runtime: DesktopRuntime) {
    Window(onCloseRequest = ::exitApplication, title = "Mihon W") {
        Text("Mihon W data: ${runtime.directories.root}")
    }
}
```

- [ ] **Step 5: Run runtime tests and the Gradle smoke entry point**

```powershell
$smokeRoot = Join-Path $env:TEMP 'mihon-w-foundation-smoke'
.\gradlew.bat :desktop-app:test --tests "mihon.desktop.DesktopRuntimeFactoryTest"
.\gradlew.bat :desktop-app:run --args="--smoke-test --data-dir=$smokeRoot"
```

Expected: the test passes and the run task prints `MIHON_DESKTOP_SMOKE_OK` followed by the explicit temporary path, then exits without opening a window.

- [ ] **Step 6: Commit runtime assembly**

```powershell
git add desktop-app/src/main/kotlin/mihon/desktop desktop-app/src/test/kotlin/mihon/desktop/DesktopRuntimeFactoryTest.kt
git commit -m "feat: bootstrap the Mihon Windows runtime"
```

---

### Task 6: Build the Material 3 Desktop Shell and Wire Window State

**Files:**
- Replace: `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/DesktopShell.kt`
- Create: `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopTheme.kt`

**Interfaces:**
- Consumes: `DesktopRuntime`, `DesktopPreferences`, `DesktopNavigator`, and saved `WindowPlacement`.
- Produces: a 1280×800 Material 3 desktop window, primary navigation rail, persisted destination, theme selection, and safe bounds capture on close.

- [ ] **Step 1: Create the Material 3 theme**

Create `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopTheme.kt`:

```kotlin
package mihon.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import mihon.desktop.preferences.ThemeMode

@Composable
fun MihonDesktopTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
```

- [ ] **Step 2: Create the navigation-rail shell**

Create `desktop-app/src/main/kotlin/mihon/desktop/ui/DesktopShell.kt`:

```kotlin
package mihon.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mihon.desktop.navigation.DesktopDestination

@Composable
fun DesktopShell(
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
) {
    val primary = DesktopDestination.entries.take(5)
    val secondary = DesktopDestination.entries.drop(5)
    Surface(modifier = Modifier.fillMaxSize()) {
        Row {
            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        primary.forEach { destination ->
                            DestinationItem(destination, selected, onDestinationSelected)
                        }
                    }
                    Column {
                        HorizontalDivider()
                        secondary.forEach { destination ->
                            DestinationItem(destination, selected, onDestinationSelected)
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(text = selected.label, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Composable
private fun DestinationItem(
    destination: DesktopDestination,
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
) {
    NavigationRailItem(
        selected = destination == selected,
        onClick = { onDestinationSelected(destination) },
        icon = { Text(destination.shortLabel) },
        label = { Text(destination.label) },
        alwaysShowLabel = false,
    )
}
```

- [ ] **Step 3: Replace the temporary window with stateful desktop wiring**

Replace `desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt` with:

```kotlin
package mihon.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement as ComposeWindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import mihon.desktop.DesktopRuntime
import mihon.desktop.navigation.DesktopNavigator
import mihon.desktop.window.ScreenBounds
import mihon.desktop.window.WindowPlacement
import java.awt.Frame
import java.awt.Toolkit

@Composable
fun ApplicationScope.MihonDesktopApp(runtime: DesktopRuntime) {
    var preferences by remember { mutableStateOf(runtime.preferences.load()) }
    val screenSize = remember { Toolkit.getDefaultToolkit().screenSize }
    val screen = remember { ScreenBounds(0, 0, screenSize.width, screenSize.height) }
    val savedPlacement = remember(preferences.windowPlacement) {
        (preferences.windowPlacement ?: WindowPlacement(0, 0, 1280, 800, false)).sanitize(screen)
    }
    val navigator = remember {
        DesktopNavigator(preferences.lastDestination) { destination ->
            preferences = preferences.copy(lastDestination = destination)
            runtime.preferences.save(preferences)
        }
    }
    val windowState = rememberWindowState(
        placement = if (savedPlacement.maximized) {
            ComposeWindowPlacement.Maximized
        } else {
            ComposeWindowPlacement.Floating
        },
        position = WindowPosition(savedPlacement.x.dp, savedPlacement.y.dp),
        width = savedPlacement.width.dp,
        height = savedPlacement.height.dp,
    )
    var composeWindow: ComposeWindow? by remember { mutableStateOf(null) }

    Window(
        onCloseRequest = {
            composeWindow?.let { window ->
                preferences = preferences.copy(
                    windowPlacement = WindowPlacement(
                        x = window.x,
                        y = window.y,
                        width = window.width,
                        height = window.height,
                        maximized = window.extendedState and Frame.MAXIMIZED_BOTH != 0,
                    ).sanitize(screen),
                )
                runtime.preferences.save(preferences)
            }
            exitApplication()
        },
        state = windowState,
        title = "Mihon W",
    ) {
        SideEffect { composeWindow = window }
        MihonDesktopTheme(preferences.themeMode) {
            DesktopShell(
                selected = navigator.current,
                onDestinationSelected = navigator::navigate,
            )
        }
    }
}
```

- [ ] **Step 4: Compile, test, and launch the real window**

Run:

```powershell
.\gradlew.bat :desktop-app:test :desktop-app:compileKotlin
.\gradlew.bat :desktop-app:run
```

Expected: all tests pass. A resizable `Mihon W` window opens with a left navigation rail, destinations update when clicked, and closing/reopening restores the destination and safe window bounds under `%APPDATA%\MihonW`.

- [ ] **Step 5: Exercise portable mode without touching `%APPDATA%`**

Run:

```powershell
$portableRoot = Join-Path $env:TEMP 'mihon-w-portable-ui'
.\gradlew.bat :desktop-app:run --args="--portable --data-dir=$portableRoot"
```

Expected: the UI opens and creates `preferences.properties` only under the explicit temporary root. No new preference file appears under `%APPDATA%\MihonW` during this run.

- [ ] **Step 6: Commit the desktop shell**

```powershell
git add desktop-app/src/main/kotlin/mihon/desktop/ui
git commit -m "feat: add the Mihon Windows desktop shell"
```

---

### Task 7: Add a Repeatable Windows Verification Script

**Files:**
- Create: `scripts/verify-desktop-foundation.ps1`
- Create: `docs/windows-development.md`

**Interfaces:**
- Consumes: repository Gradle wrapper and Windows JDK 17.
- Produces: a noninteractive foundation verification command and a packaged runtime smoke result.

- [ ] **Step 1: Create the verification script**

Create `scripts/verify-desktop-foundation.ps1`:

```powershell
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    & .\gradlew.bat spotlessCheck :desktop-app:test :desktop-app:createDistributable
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle desktop foundation verification failed with exit code $LASTEXITCODE"
    }

    $launcher = Join-Path $repoRoot 'desktop-app\build\compose\binaries\main\app\MihonW\MihonW.exe'
    if (-not (Test-Path -LiteralPath $launcher)) {
        throw "Packaged launcher was not created at $launcher"
    }

    $smokeRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('mihon-w-smoke-' + [guid]::NewGuid().ToString('N'))
    $process = Start-Process -FilePath $launcher -ArgumentList @('--smoke-test', "--data-dir=$smokeRoot") -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Packaged launcher smoke test failed with exit code $($process.ExitCode)"
    }
    if (-not (Test-Path -LiteralPath (Join-Path $smokeRoot 'cache'))) {
        throw "Packaged launcher did not initialize the explicit smoke-test data root"
    }

    Write-Host 'Mihon W desktop foundation verification passed.'
} finally {
    Pop-Location
}
```

- [ ] **Step 2: Document exact developer commands**

Create `docs/windows-development.md`:

````markdown
# Mihon W Windows Development

## Requirements

- Windows 10 22H2 or Windows 11 x64
- JDK 17 selected by Gradle
- PowerShell 7 or Windows PowerShell 5.1

## Run from source

```powershell
.\gradlew.bat :desktop-app:run
```

Use an isolated portable data directory during development:

```powershell
$dataRoot = Join-Path $env:TEMP 'mihon-w-dev-data'
.\gradlew.bat :desktop-app:run --args="--portable --data-dir=$dataRoot"
```

## Verify the foundation slice

```powershell
.\scripts\verify-desktop-foundation.ps1
```

The verifier checks formatting, desktop unit tests, the self-contained application image, packaged startup, and explicit data-root initialization.
````

- [ ] **Step 3: Run the verifier from a clean Gradle invocation**

```powershell
.\gradlew.bat --stop
.\scripts\verify-desktop-foundation.ps1
```

Expected: the final line is `Mihon W desktop foundation verification passed.` and `git status --short` contains only the two new documentation/script files before commit.

- [ ] **Step 4: Commit the verifier and developer documentation**

```powershell
git add scripts/verify-desktop-foundation.ps1 docs/windows-development.md
git commit -m "test: verify the Windows desktop foundation"
```

---

### Task 8: Add the Windows CI Gate Without Weakening Android CI

**Files:**
- Modify: `.github/workflows/build.yml`

**Interfaces:**
- Consumes: `scripts/verify-desktop-foundation.ps1` and the existing pinned setup actions.
- Produces: the `desktop-windows` CI job and an uploaded self-contained runtime image.

- [ ] **Step 1: Add the Windows job**

Append this job under `jobs:` in `.github/workflows/build.yml`, alongside the existing Android `build` job:

```yaml
  desktop-windows:
    name: Build & Test Windows Foundation
    runs-on: windows-2025

    steps:
      - name: Checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1

      - name: Set up JDK
        uses: actions/setup-java@dd06d9cba3e5552c54d9f8ea23572deb30010f7c # v6.0.0
        with:
          java-version-file: .github/.java-version
          distribution: temurin

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb # v6.3.0

      - name: Verify Windows desktop foundation
        shell: pwsh
        run: .\scripts\verify-desktop-foundation.ps1

      - name: Upload Windows runtime image
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: mihon-w-foundation-${{ github.sha }}
          path: desktop-app/build/compose/binaries/main/app/MihonW
```

- [ ] **Step 2: Validate the workflow structure locally**

Run:

```powershell
$workflow = Get-Content -LiteralPath '.github\workflows\build.yml' -Raw
if ($workflow -notmatch 'desktop-windows:' -or $workflow -notmatch 'verify-desktop-foundation.ps1') {
    throw 'Windows CI job is missing required keys'
}
.\scripts\verify-desktop-foundation.ps1
```

Expected: the structural assertion returns no error and the local verifier passes. The existing Android job remains present and unchanged.

- [ ] **Step 3: Run the complete local regression gate available on Windows**

```powershell
.\gradlew.bat spotlessCheck :desktop-app:test testDebugUnitTest verifySqlDelightMigration
```

Expected: formatting, desktop tests, Android JVM unit tests, and SQLDelight migration verification pass.

- [ ] **Step 4: Commit the CI gate**

```powershell
git add .github/workflows/build.yml
git commit -m "ci: verify the Windows desktop foundation"
```

## Plan Completion Evidence

Before declaring this plan complete, collect and retain all of the following evidence:

- `git status --short --branch` shows only intentional commits and no uncommitted implementation files.
- `desktop-app/build/test-results/test` reports all desktop tests passing.
- `spotlessCheck`, `testDebugUnitTest`, and `verifySqlDelightMigration` pass on the implementation commit.
- `desktop-app/build/compose/binaries/main/app/MihonW/MihonW.exe --smoke-test --data-dir=<temporary path>` exits successfully and creates only the explicit data root.
- A real UI launch shows the navigation rail and proves destination/window restoration after close and reopen.
- The GitHub Actions Windows job passes and uploads the self-contained runtime image while the existing Android job remains green.

## References

- Compose Multiplatform plugin `1.12.0`: https://plugins.gradle.org/plugin/org.jetbrains.compose/1.12.0
- Compose Desktop native distributions and `createDistributable`: https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html
- Approved architecture: `docs/superpowers/specs/2026-08-31-windows-port-design.md`
