# Codebase Structure

**Analysis Date:** 2024-06-23

## Directory Layout

```
mihon-ds/
├── app/                     # Main Android application module
│   └── src/main/java/
│       └── eu/kanade/
│           ├── presentation/     # Modern Compose UI (Mihon fork additions)
│           │   ├── browse/        # Source browsing, extensions, search
│           │   ├── library/       # Library UI and components
│           │   ├── manga/         # Manga details and reader
│           │   ├── more/          # Settings and more screens
│           │   ├── reader/        # Reader UI components
│           │   ├── track/         # Tracking service UI
│           │   ├── updates/       # Updates management
│           │   └── components/    # Shared UI components
│           ├── tachiyomi/         # Legacy Tachiyomi UI (being phased out)
│           │   ├── ui/           # Legacy Compose screens
│           │   ├── data/          # Legacy data layer implementation
│           │   └── di/            # Dependency injection setup
│           └── core/              # Core utilities for Mihon
├── domain/                   # Business logic and models
│   └── src/main/java/
│       ├── tachiyomi/           # Legacy domain logic
│       └── mihon/               # Mihon-specific domain logic
├── data/                     # Repository implementations and database
│   └── src/main/java/
│       ├── tachiyomi/           # Legacy data implementations
│       └── mihon/               # Mihon-specific data implementations
│   └── src/main/sqldelight/     # Database schemas and migrations
│       └── tachiyomi/
│           ├── data/            # Table schemas
│           └── migrations/      # Database migration scripts
├── core-common/             # Shared utilities and preferences
├── core-metadata/           # Metadata handling (ComicInfo, etc.)
├── core-archive/            # Archive format handling (CBZ, etc.)
├── presentation-core/       # Shared presentation components and theme
├── presentation-widget/     # App widgets
├── source-api/              # Extension source interfaces
├── source-local/            # Local file source implementation
├── i18n/                    # Internationalization with Moko Resources
├── telemetry/               # Analytics and crash reporting
└── buildSrc/                # Build logic and version catalogs
```

## Directory Purposes

**app/src/main/java/eu/kanade/presentation/**:
- Purpose: Modern Mihon Compose UI screens
- Contains: Feature-specific screens, shared components
- Key files: `BrowseSourceScreen.kt`, `LibraryScreen.kt`, `MangaScreen.kt`
- Naming: `*Screen.kt` for screens, `*Components.kt` for reusable components

**app/src/main/java/eu/kanade/tachiyomi/ui/**:
- Purpose: Legacy Tachiyomi Compose UI (being migrated)
- Contains: Existing screens before Mihon fork
- Migration: Being gradually replaced by presentation/ components

**app/src/main/java/eu/kanade/tachiyomi/data/**:
- Purpose: Legacy data implementation
- Contains: Repository implementations, data access
- Migration: Being replaced by domain/ and data/ separation

**domain/src/main/java/**:
- Purpose: Business logic and use cases
- Contains: Interactors, repository interfaces, domain models
- Key patterns: Interactor classes for business logic, repository interfaces

**data/src/main/java/**:
- Purpose: Repository implementations and data sources
- Contains: Concrete repository implementations, database access
- Key patterns: `*RepositoryImpl.kt` files implementing domain interfaces

**data/src/main/sqldelight/**:
- Purpose: Database schemas and migrations
- Contains: `.sq` files for table definitions
- Generated: Kotlin code from `.sq` files for database access

**core-common/src/main/java/**:
- Purpose: Shared utilities and preferences
- Contains: Network helpers, storage utilities, custom preferences
- Key files: `NetworkHelper.kt`, `AndroidStorageFolderProvider.kt`

**presentation-core/src/main/java/**:
- Purpose: Shared UI components and theme
- Contains: Theme, components, utilities for presentation layer
- Key files: `theme.kt`, `components/` directory

**source-api/src/**:
- Purpose: Extension source interfaces
- Contains: Common interfaces for source implementations
- Pattern: Defines contracts for manga sources

**i18n/src/**:
- Purpose: Internationalization resources
- Contains: Moko Resources for string localization
- Pattern: Language-specific directories in `moko-resources/`

## Key File Locations

**Entry Points:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`: Main activity entry point
- `app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt`: Root navigation screen
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt`: DI module setup

**Core Logic:**
- `domain/src/main/java/tachiyomi/domain/manga/interactor/GetManga.kt`: Manga business logic
- `data/src/main/java/tachiyomi/data/manga/MangaRepositoryImpl.kt`: Data access implementation
- `data/src/main/sqldelight/tachiyomi/data/mangas.sq`: Manga database schema

**UI Screens:**
- `app/src/main/java/eu/kanade/presentation/library/LibraryScreen.kt`: Library UI
- `app/src/main/java/eu/kanade/presentation/manga/MangaScreen.kt`: Manga details
- `app/src/main/java/eu/kanade/presentation/reader/reader/ReaderScreen.kt`: Reader UI

## Naming Conventions

**Files:**
- Screens: `*Screen.kt` (e.g., `BrowseSourceScreen.kt`)
- ViewModels/ScreenModels: `*ScreenModel.kt`
- Components: `*Component.kt` (e.g., `ActionButton.kt`)
- Use Cases: `*Interactor.kt` (e.g., `GetManga.kt`)
- Repositories: `*Repository.kt` (interface), `*RepositoryImpl.kt` (implementation)
- Database: `*.sq` (schemas)

**Packages:**
- Domain: `domain.{feature}.{type}` (e.g., `domain.manga.interactor`)
- Data: `data.{feature}` (e.g., `data.manga`)
- Presentation: `presentation.{feature}` (e.g., `presentation.browse`)
- UI: `ui.{feature}` (e.g., `ui.browse`)

**Classes:**
- Use Cases: PascalCase (e.g., `GetManga`)
- Repositories: PascalCase (e.g., `MangaRepository`)
- Screens: PascalCase (e.g., `LibraryScreen`)

## Where to Add New Code

**New Feature:**
- Domain logic: `domain/src/main/java/tachiyomi/domain/{feature}/`
- Data implementation: `data/src/main/java/tachiyomi/data/{feature}/`
- UI screens: `app/src/main/java/eu/kanade/presentation/{feature}/`
- Database schema: `data/src/main/sqldelight/tachiyomi/data/{feature}.sq`

**New Component:**
- UI component: `app/src/main/java/eu/kanade/presentation/components/`
- Presentation utility: `presentation-core/src/main/java/tachiyomi/presentation/core/util/`

**New Use Case:**
- Location: `domain/src/main/java/tachiyomi/domain/{feature}/interactor/`
- Pattern: Class name describes action (e.g., `DownloadChapter`)

**New Repository:**
- Interface: `domain/src/main/java/tachiyomi/domain/{feature}/repository/`
- Implementation: `data/src/main/java/tachiyomi/data/{feature}/`

## Special Directories

**dualscreen/**:
- Purpose: Dual-screen mode support for foldable devices
- Location: `app/src/main/java/eu/kanade/tachiyomi/ui/main/DualScreenPresentation.kt`
- Generated: No, manually implemented

**migration/**:
- Purpose: Database migrations and data transformations
- Location: `core/common/src/main/java/mihon/core/migration/`
- Generated: No, manually implemented migrations

**extension/**:
- Purpose: Extension management and source handling
- Location: `app/src/main/java/eu/kanade/tachiyomi/extension/`
- Generated: No, runtime loaded extensions

---

*Structure analysis: 2024-06-23*
