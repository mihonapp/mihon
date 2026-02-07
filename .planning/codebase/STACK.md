# Technology Stack

**Analysis Date:** 2026-02-05

## Languages

**Primary:**
- Kotlin 2.3.0 - Android application modules and all business logic

**Secondary:**
- SQL - SQLDelight database schemas in `.sq` files (`data/src/main/sqldelight/`)

## Runtime

**Environment:**
- Android 8.0+ (API 26-36) - Target SDK 36, Min SDK 26

**Package Manager:**
- Gradle 8.x with Kotlin DSL
- Lockfile: `gradle/libs.versions.toml`, `gradle/compose.versions.toml`, `gradle/androidx.versions.toml`, `gradle/kotlinx.versions.toml`

## Frameworks

**Core:**
- Jetpack Compose BOM 2026.01.01 - Modern declarative UI framework
- AndroidX Lifecycle 2.10.0 - ViewModel and lifecycle-aware components
- AndroidX WorkManager 2.11.1 - Background job scheduling
- AndroidX Biometric 1.2.0-alpha05 - Fingerprint/biometric authentication
- AndroidX Paging 3.4.0 - Data pagination for large datasets

**Testing:**
- JUnit Jupiter 6.0.2 - Test runner
- Kotest 6.1.2 - Assertions library
- Mockk 1.14.9 - Mocking framework
- AndroidX Benchmark 1.4.1 - Performance benchmarking

**Build/Dev:**
- Spotless 8.2.1 - Code formatting with KtLint 1.8.0
- Injekt 91edab2317 - Lightweight dependency injection
- SQLDelight 2.2.1 - Type-safe database access code generator

## Key Dependencies

**Critical:**
- OkHttp 5.3.2 - HTTP client for network requests (with Brotli, DNS-over-HTTPS, logging)
- Coil 3.3.0 - Image loading library for Compose
- SQLDelight 2.2.1 - Database ORM and query generator
- Kotlin Coroutines 1.10.2 - Asynchronous programming
- RxJava 1.3.8 - Legacy async support (being phased out)

**Infrastructure:**
- Conscrypt 2.5.3 - TLS 1.3 support for Android < 10
- QuickJS Android - JavaScript engine for web scraping
- Jsoup 1.22.1 - HTML parsing for manga sources
- libarchive 1.1.6 - CBZ/Comic archive format handling
- SQLite 2.6.2 (AndroidX) - Database framework

**UI:**
- Voyager 1.1.0-beta03 - Navigation library for Compose
- Material 3 Components - Material Design 3 UI kit
- Material Motion Compose 2.0.1 - Motion/transitions
- Subsampling Scale Image View - Large image viewing (manga pages)
- Shizuku 13.1.5 - System-level API access (ADB-like)
- AboutLibraries 13.2.1 - OSS licenses display

**Internationalization:**
- Moko Resources 0.25.2 - Multiplatform resource management

## Configuration

**Environment:**
- Build variants configured via Gradle project properties
- Key build properties (enabled via `-P` flags):
  - `include-telemetry` - Enable Firebase analytics/crashlytics
  - `enable-updater` - Enable in-app updater
  - `disable-code-shrink` - Disable ProGuard/R8
  - `include-dependency-info` - Include dependency metadata in APK

**Build:**
- `build.gradle.kts` - Root project configuration
- `app/build.gradle.kts` - Main app module with build variants (debug, release, foss, preview, benchmark)
- `buildSrc/src/main/kotlin/mihon/buildlogic/` - Convention plugins and build config
- Kotlin compiler options in each module's `build.gradle.kts`

## Platform Requirements

**Development:**
- JDK 17
- Android Gradle Plugin 8.13.2
- 4GB JVM heap configured (`org.gradle.jvmargs=-Xmx4g`)

**Production:**
- Android 8.0 (API 26) or higher
- Architecture splits: armeabi-v7a, arm64-v8a, x86, x86_64
- Google Play Services (optional, only for telemetry builds)

---

*Stack analysis: 2026-02-05*
