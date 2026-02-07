# Coding Conventions

**Analysis Date:** 2026-02-05

## Naming Patterns

**Files:**
- PascalCase for classes: `LibrarySort.kt`, `ChapterRecognition.kt`
- Use descriptive names matching primary class/interface
- Test files: `{ClassName}Test.kt` (e.g., `ChapterRecognitionTest.kt`)
- Compose screens: `{Feature}Screen.kt` (e.g., `BrowseSourceScreen.kt`)
- Interactors: `{Verb}{Entity}.kt` (e.g., `GetChaptersByMangaId.kt`, `SetMangaViewerFlags.kt`)

**Functions:**
- camelCase for functions: `parseChapterNumber()`, `calculateInterval()`
- Interactor use cases use verb-noun pattern: `getAvailableScanlators()`, `syncChaptersWithSource()`
- Composable functions: PascalCase: `DownloadsBadge()`, `LanguageBadge()`
- Suspend functions marked with `suspend` keyword

**Variables:**
- camelCase: `mangaTitle`, `chapterName`, `uploadDates`
- Immutable vals favored over mutable vars
- Constants: UPPER_SNAKE_CASE: `MAX_INTERVAL`, `GRACE_PERIOD`

**Types:**
- PascalCase: `ChapterUpdate`, `Manga`, `LibrarySort`
- Sealed classes for state: `TriState`
- Data classes for models: `data class Manga()`, `data class Chapter()`
- Immutable models with `@Immutable` annotation for Compose

## Code Style

**Formatting:**
- Tool: Spotless with KtLint 1.8.0
- Config: `buildSrc/src/main/kotlin/mihon.code.lint.gradle.kts`
- Apply with: `./gradlew spotlessApply`
- Check with: `./gradlew spotlessCheck`
- Key settings:
  - Trim trailing whitespace
  - End files with newline
  - Kotlin files: `**/*.kt`, `**/*.kts`
  - Excludes `**/build/**/*.kt`

**Linting:**
- Android Lint: `./gradlew lint`
- Lint config in `app/build.gradle.kts`:
  - `abortOnError = false`
  - `checkReleaseBuilds = false`

**Kotlin Compiler Opt-ins:**
- Experimental Compose APIs: `ExperimentalMaterial3Api`, `ExperimentalFoundationApi`
- Coroutines: `ExperimentalCoroutinesApi`, `FlowPreview`
- Serialization: `ExperimentalSerializationApi`
- Configured in `app/build.gradle.kts` under `kotlin.compilerOptions.freeCompilerArgs`

## Import Organization

**Order:**
1. Android/AndroidX imports
2. Third-party library imports (Compose, Voyager, etc.)
3. Project/domain imports (tachiyomi.domain.*)
4. Local project imports (eu.kanade.*)
5. Kotlin stdlib extensions (implicitly)

**Path Aliases:**
- No explicit path aliases configured
- Gradle project dependencies used: `implementation(projects.domain)`, `implementation(projects.core.common)`

**Import Style:**
- Individual imports preferred over star imports
- Grouped logically by library/source
- Example from `LibraryBadges.kt`:
  ```kotlin
  import androidx.compose.foundation.layout.Column
  import androidx.compose.material.icons.Icons
  import eu.kanade.presentation.theme.TachiyomiPreviewTheme
  import tachiyomi.presentation.core.components.Badge
  ```

## Error Handling

**Patterns:**
- Try-catch with logging using `logcat()` extension function
- Return default/empty values on error in interactors
- Example from `GetChaptersByMangaId.kt`:
  ```kotlin
  suspend fun await(mangaId: Long, applyScanlatorFilter: Boolean = false): List<Chapter> {
      return try {
          chapterRepository.getChapterByMangaId(mangaId, applyScanlatorFilter)
      } catch (e: Exception) {
          logcat(LogPriority.ERROR, e)
          emptyList()
      }
  }
  ```

**Sealed Classes for Errors:**
- Custom exceptions in domain: `NoChaptersException`, `SaveExtensionRepoException`
- Repository interfaces throw domain-specific exceptions

**Logging Framework:**
- Library: `com.squareup.logcat:logcat`
- Usage: inline extension `Any.logcat()`
- Priority levels: `LogPriority.DEBUG`, `LogPriority.ERROR`
- File: `core/common/src/main/kotlin/tachiyomi/core/common/util/system/LogcatExtensions.kt`

## Logging

**Framework:** `logcat` (square/logcat)

**Patterns:**
- Use inline extension function: `logcat(priority = LogPriority.ERROR) { "message" }`
- Include throwable for errors: `logcat(LogPriority.ERROR, e) { "Failed to load" }`
- Context-aware logging via object/class receivers

**When to Log:**
- Exception catch blocks (ERROR level)
- Important state changes (DEBUG/INFO level)
- Not for routine operations

**Example:**
```kotlin
// From GetChaptersByMangaId.kt
import tachiyomi.core.common.util.system.logcat

try {
    // operation
} catch (e: Exception) {
    logcat(LogPriority.ERROR, e)
    emptyList()
}
```

## Comments

**When to Comment:**
- Complex regex patterns: Documented with `-R> = regex conversion` (e.g., `ChapterRecognition.kt`)
- Public APIs: KDoc for public functions
- Algorithm explanation: Comment blocks for complex logic
- "Why" not "what": Comments explain reasoning, not obvious code

**JSDoc/TSDoc:**
- KDoc for public APIs
- Parameter descriptions for complex functions
- Example from `ChapterRecognition.kt`:
  ```kotlin
  /**
   * All cases with Ch.xx
   * Mokushiroku Alice Vol.1 Ch. 4: Misrepresentation -R> 4
   */
  private val basic = Regex("""(?<=ch\.) *$NUMBER_PATTERN""")
  ```

**Block Comments:**
- Used for regex explanations
- Test case descriptions: `fun \`Basic Ch prefix\` { }`

## Function Design

**Size:**
- Keep functions focused and concise
- Complex logic split into private helper functions
- Interactors typically 20-50 lines

**Parameters:**
- Data classes for related parameters
- Builder pattern not typically used
- Default values for optional parameters
- Example: `parseChapterNumber(mangaTitle: String, chapterName: String, chapterNumber: Double? = null)`

**Return Values:**
- Suspended functions return direct values: `suspend fun await(): List<Chapter>`
- Use Flow for streams: `Flow<List<Manga>>`
- Sealed classes for multiple return types
- Nullable types for optional returns: `Double?`

## Module Design

**Exports:**
- Public classes exported by default
- Internal visibility for module-private: `internal fun`
- Public APIs in domain layer use `public` (default)

**Barrel Files:**
- Not extensively used
- Direct imports preferred: `import tachiyomi.domain.chapter.model.Chapter`
- Some utility files provide extension functions: `CoroutinesExtensions.kt`, `BooleanExtensions.kt`

**Domain Layer Patterns:**
- Interactors (use cases): `domain/.../interactor/{Verb}{Entity}.kt`
- Models: `domain/.../model/{Entity}.kt`
- Repository interfaces: `domain/.../repository/{Entity}Repository.kt`
- Services/Preferences: `domain/.../service/{Feature}Preferences.kt`

**Data Layer Patterns:**
- Repository implementations
- SQLDelight database schemas in `data/src/main/sqldelight/`
- Mappers: `.toDomain{Model}()` extension functions

**Presentation Layer:**
- Screens: `presentation/.../{Feature}Screen.kt`
- Components: `presentation/.../components/{Component}.kt`
- ViewModels: `ui/.../{Feature}ViewModel.kt`

**Visibility:**
- `internal` for implementation details
- `public` for APIs exposed across modules
- Composables use `internal` when not part of public API
- Example from `LibraryBadges.kt`:
  ```kotlin
  @Composable
  internal fun DownloadsBadge(count: Long) { }
  ```

---

*Convention analysis: 2026-02-05*
