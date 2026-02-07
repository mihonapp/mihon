# Testing Patterns

**Analysis Date:** 2026-02-05

## Test Framework

**Runner:**
- JUnit Jupiter 6.0.2
- Config: No explicit test config file (defaults used)
- Parallel execution: `@Execution(ExecutionMode.CONCURRENT)` on test classes

**Assertion Library:**
- Kotest 6.1.2 (`kotest-assertions-core`)
- Matcher style: `shouldBe`, `shouldNotBe`
- Examples:
  ```kotlin
  result shouldBe 4.0
  flag shouldBe 0b01011100
  flag shouldNotBe currentSort.flag
  ```

**Mocking Framework:**
- Mockk 1.14.9
- Functions: `mockk()`, `spyk()`, `verify()`, `slot()`

**Run Commands:**
```bash
./gradlew test                      # Run all tests
./gradlew :domain:test             # Test domain module
./gradlew :app:test                # Test app module
./gradlew test --tests "*ChapterRecognitionTest"  # Specific test
```

## Test File Organization

**Location:**
- Co-located with source code in `src/test/`
- Mirrors main package structure
- Examples:
  - `domain/src/test/java/tachiyomi/domain/chapter/service/ChapterRecognitionTest.kt`
  - `domain/src/test/java/tachiyomi/domain/manga/interactor/FetchIntervalTest.kt`
  - `app/src/test/java/mihon/core/migration/MigratorTest.kt`

**Naming:**
- `{ClassName}Test.kt` pattern
- Test class name matches class under test
- Located in same package as class under test

**Structure:**
```
src/test/java/
└── tachiyomi/domain/
    ├── chapter/
    │   └── service/
    │       ├── ChapterRecognitionTest.kt
    │       └── MissingChaptersTest.kt
    ├── library/
    │   └── model/
    │       └── LibraryFlagsTest.kt
    └── manga/
        └── interactor/
            └── FetchIntervalTest.kt
```

## Test Structure

**Suite Organization:**
```kotlin
@Execution(ExecutionMode.CONCURRENT)  // Enable parallel execution
class ChapterRecognitionTest {

    @Test
    fun `Basic Ch prefix`() {
        val mangaTitle = "Mokushiroku Alice"
        assertChapter(mangaTitle, "Mokushiroku Alice Vol.1 Ch.4: Misrepresentation", 4.0)
    }

    private fun assertChapter(mangaTitle: String, name: String, expected: Double) {
        ChapterRecognition.parseChapterNumber(mangaTitle, name) shouldBe expected
    }
}
```

**Patterns:**
- Test methods named with backticks: `fun \`Test name with spaces\`()`
- Describe behavior in test name
- Use private helper methods for common assertions
- No explicit `@BeforeEach` in simple test classes

**Setup/Teardown:**
- `@BeforeEach` for test initialization when needed
- `@BeforeAll`/`@AfterAll` for expensive setup (e.g., coroutine dispatchers)
- Example from `MigratorTest.kt`:
  ```kotlin
  companion object {
      @OptIn(DelicateCoroutinesApi::class)
      val mainThreadSurrogate = newSingleThreadContext("UI thread")

      @BeforeAll
      @JvmStatic
      fun setUp() {
          Dispatchers.setMain(mainThreadSurrogate)
      }

      @AfterAll
      @JvmStatic
      fun tearDown() {
          Dispatchers.resetMain()
          mainThreadSurrogate.close()
      }
  }
  ```

**Assertion Pattern:**
- Kotest matchers: `shouldBe`, `shouldNotBe`
- Direct assertions: `assertEquals()`, `assertFalse()`, `assertInstanceOf()`
- Custom helper functions for domain-specific assertions

## Mocking

**Framework:** Mockk

**Patterns:**
```kotlin
// From MigratorTest.kt
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify

lateinit var migrationJobFactory: MigrationJobFactory

@BeforeEach
fun initialize() {
    migrationJobFactory = spyk(MigrationJobFactory(migrationContext, CoroutineScope(Dispatchers.Main + Job())))
    migrationCompletedListener = spyk<MigrationCompletedListener>(block = {})
}

@Test
fun test() = runBlocking {
    val migrations = slot<List<Migration>>()
    execute.await()

    verify { migrationJobFactory.create(capture(migrations)) }
    assertEquals(1, migrations.captured.size)
    verify(exactly = 0) { migrationJobFactory.create(any()) }
}
```

**What to Mock:**
- Repository interfaces
- External dependencies
- Heavy services (network, database)
- Use `spyk()` for partial mocking of real objects

**What NOT to Mock:**
- Data classes (value objects)
- Simple utilities
- Pure functions (e.g., `ChapterRecognition.parseChapterNumber()`)

## Fixtures and Factories

**Test Data:**
- Inline creation in test methods
- Helper functions for complex objects
- Example from `FetchIntervalTest.kt`:
  ```kotlin
  private val testTime = ZonedDateTime.parse("2020-01-01T00:00:00Z")
  private var chapter = Chapter.create().copy(
      dateFetch = testTime.toEpochSecond() * 1000,
      dateUpload = testTime.toEpochSecond() * 1000,
  )

  private fun chapterWithTime(chapter: Chapter, duration: Duration): Chapter {
      val newTime = testTime.plus(duration.toJavaDuration()).toEpochSecond() * 1000
      return chapter.copy(dateFetch = newTime, dateUpload = newTime)
  }
  ```

**Location:**
- Test data created inline or in helper functions
- No centralized fixtures directory
- Factory methods in test classes for reusable objects

## Coverage

**Requirements:** None explicitly enforced

**View Coverage:**
- No coverage command documented
- Test count appears limited (focused testing approach)
- Coverage likely low; focus on critical business logic

**Test Areas:**
- Domain logic: Chapter recognition, fetch interval calculation, library flags
- Migration logic: Version migrations
- Notable gaps: UI/presentation layer (no Compose UI tests)

## Test Types

**Unit Tests:**
- Primary focus
- Test pure functions and business logic
- Examples: `ChapterRecognitionTest`, `FetchIntervalTest`, `LibraryFlagsTest`
- No Android dependencies (uses `testImplementation`, not `androidTestImplementation`)

**Integration Tests:**
- Limited or not identified
- No `androidTestImplementation` usage found

**E2E Tests:**
- Not present
- No UI automation tests detected

## Common Patterns

**Async Testing:**
```kotlin
// From MigratorTest.kt
@Test
fun initialVersion() = runBlocking {
    val strategy = migrationStrategyFactory.create(0, 1)
    val execute = strategy(listOf(migration))
    execute.await()

    eventually(2.seconds) { verify { migrationCompletedListener() } }
}
```

- Use `runBlocking` for suspend function tests
- Kotest's `eventually()` for async assertions with timeout
- Coroutine test utilities: `kotlinx.coroutines.test`

**Error Testing:**
- Not extensively demonstrated in sample tests
- Use standard exception handling patterns:
  ```kotlin
  @Test
  fun `handles invalid input`() {
      val result = someFunction(invalidInput)
      // Assert error handling (empty list, default value, etc.)
  }
  ```

**Parametric Testing:**
- Not using JUnit 5 `@ParameterizedTest`
- Individual test methods for each case instead
- Example: 20+ individual test methods in `ChapterRecognitionTest.kt`

**Test Organization by Feature:**
- Tests grouped by class under test
- Related test cases in same file
- Descriptive test names with backticks

**Coroutines Testing:**
- Set main dispatcher for tests needing UI context
- Use `@OptIn(DelicateCoroutinesApi::class)` for test utilities
- Reset dispatcher in `@AfterAll`

---

*Testing analysis: 2026-02-05*
