# Architecture

**Analysis Date:** 2024-06-23

## Pattern Overview

**Overall:** Clean Architecture with MVVM pattern

**Key Characteristics:**
- Layered architecture with clear separation of concerns
- Domain-Driven Design principles
- Repository pattern for data access abstraction
- Dependency injection using Injekt
- Reactive programming with Kotlin Flow and Coroutines
- Navigation using Voyager in Jetpack Compose UI

## Layers

**Domain Layer:**
- Purpose: Business logic, use cases, and domain models
- Location: `domain/src/main/java/`
- Contains: Interactors, repositories interfaces, models
- Depends on: Core utilities and data models
- Used by: Presentation layer

**Data Layer:**
- Purpose: Repository implementations, data sources, and database access
- Location: `data/src/main/java/`
- Contains: Repository implementations, SQLDelight database access, caching
- Depends on: Domain interfaces (implements them), external data sources
- Used by: Domain layer

**Presentation Layer:**
- Purpose: UI screens, user interaction, and state management
- Location: `app/src/main/java/eu/kanade/presentation/`, `app/src/main/java/eu/kanade/tachiyomi/ui/`
- Contains: Compose screens, view models (ScreenModels), UI components
- Depends on: Domain layer (use cases), Presentation Core
- Used by: User interface

**Core/Infrastructure:**
- Purpose: Shared utilities, dependencies, and cross-cutting concerns
- Location: `core-common/`, `core-metadata/`, `core/archive/`, etc.
- Contains: DI setup, preferences, network helpers, themes
- Depends on: Platform-specific implementations
- Used by: All layers

## Data Flow

**User Interaction:**
1. User interacts with Compose UI screen
2. UI triggers action (click, navigation, etc.)
3. Composable calls ScreenModel or use case directly
4. Use case executes business logic through repositories
5. Repository fetches/updates data from data sources
6. Data flows back through layers to update UI

**Data Loading:**
1. UI triggers data loading (e.g., library refresh)
2. Use case calls repository method
3. Repository queries database via SQLDelight
4. Results returned as Kotlin Flow
5. UI collects Flow and recomposes

**Navigation:**
1. Screen uses Voyager navigator
2. Navigator pushes/pops screens
3. Screens implement Screen interface for lifecycle
4. State managed via remember/compose state

## Key Abstractions

**Use Cases (Interactors):**
- Purpose: Encapsulate business logic
- Pattern: Command pattern with suspend functions
- Examples: `GetManga`, `GetLibraryManga`, `DownloadChapter`
- Location: `domain/src/main/java/**/interactor/`

**Repositories:**
- Purpose: Abstract data access from use cases
- Pattern: Repository pattern with Flow responses
- Examples: `MangaRepository`, `ChapterRepository`, `SourceRepository`
- Location: Domain interfaces in `domain/src/main/java/**/repository/`
- Implementation in `data/src/main/java/**/RepositoryImpl.kt`

**Screen Models:**
- Purpose: Manage UI state and handle actions
- Pattern: ViewModel-like using Compose state
- Location: `app/src/main/java/eu/kanade/presentation/**/*ScreenModel.kt`
- Usage: Collect from use cases and manage UI state

**Sources:**
- Purpose: Abstract data sources for manga content
- Pattern: Strategy pattern with different implementations
- Examples: `CatalogueSource`, `SourceManager`
- Location: `source-api/` for interfaces, `source-local/` for local files

## Entry Points

**Application Entry:**
- Location: `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`
- Purpose: Initializes app, sets up navigation, handles deep links
- Features: Splash screen, dual-screen support, intent handling

**Navigation Entry:**
- Location: `app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt`
- Purpose: Root navigation with bottom tabs
- Tabs: Library, Browse, Updates, History, More

**Dependency Injection:**
- Location: `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt`
- Purpose: Configure Injekt modules for DI
- Features: Database, repositories, services, utilities

## Error Handling

**Strategy:** Centralized error handling with try-catch and logging

**Patterns:**
- Use cases: Wrap in try-catch, log errors, return null/Result
- Repository: Handle database/network errors, return Flow with errors
- UI: Show error states via Compose state management
- Logging: Logcat with priority levels in core utilities

## Cross-Cutting Concerns

**Logging:**
- Framework: Logcat with custom priority levels
- Patterns: `logcat(LogPriority.ERROR, e)` in domain logic

**Preferences:**
- Framework: Custom preference system wrapping Android SharedPreferences
- Location: `core/common/src/main/java/tachiyomi/core/common/preference/`
- Usage: `preferences.setting().collectAsState()` in Composables

**Authentication:**
- Pattern: Custom auth system for tracking services
- Location: `app/src/main/java/eu/kanade/tachiyomi/data/track/`
- Implementation: Individual provider implementations

---

*Architecture analysis: 2024-06-23*
