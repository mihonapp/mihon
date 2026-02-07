# External Integrations

**Analysis Date:** 2026-02-05

## APIs & External Services

**Analytics & Crash Reporting:**
- Firebase Analytics & Crashlytics (BOM 34.8.0) - Usage analytics and crash reporting
  - SDK: `com.google.firebase:firebase-analytics`, `com.google.firebase:firebase-crashlytics`
  - Auth: `google-services.json` (not in repo, added at build time)
  - Build flag: `-Pinclude-telemetry` (disabled by default)
  - Implementation: `telemetry/src/firebase/kotlin/mihon/telemetry/`
  - User opt-in via onboarding flow

**Manga Sources (Extension System):**
- Dynamic extension loading - Third-party manga source plugins
  - Interface: `source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/Source.kt`
  - Runtime loading via reflection
  - No pre-defined external APIs - extensions implement standard interfaces

## Data Storage

**Databases:**
- SQLDelight (SQLite-based) - Local database for manga, chapters, history
  - Connection: Internal Android storage
  - Client: SQLDelight 2.2.1 with AndroidX SQLite 2.6.2
  - Schemas: `data/src/main/sqldelight/tachiyomi/` (`.sq` files)
  - Migrations: `data/src/main/sqldelight/tachiyomi/migrations/` (`.sqm` files)

**File Storage:**
- Local Android filesystem using `Unifile` wrapper
  - Archive formats: CBZ via `libarchive`
  - Image caching: DiskLRU cache
  - Downloads: App-specific external storage directories

**Caching:**
- DiskLRU 2.0.2 - Disk-based LRU cache for images and pages
- In-memory: Coil image loading cache (3.3.0)

## Authentication & Identity

**Auth Provider:**
- None (local application) - No user accounts or authentication
  - Biometric: AndroidX Biometric for local app lock (fingerprint/pin)
  - Implementation: `androidx.biometric:biometric-ktx`

## Monitoring & Observability

**Error Tracking:**
- Firebase Crashlytics (optional, telemetry builds only)
  - User opt-in via privacy preferences
  - Preference: `privacyPreferences.crashlytics()`

**Logs:**
- Square Logcat 0.4 - Android logcat wrapper
- Debug logging via `logcat` tag filtering

## CI/CD & Deployment

**Hosting:**
- GitHub (code repository)
- Google Play Store (distribution, implied)
- F-Droid (FOSS variant distribution, implied)

**CI Pipeline:**
- GitHub Actions (`.github/workflows/`)
- Build matrix for different ABIs and variants

## Environment Configuration

**Required env vars:**
- None required for base build
- Optional `-P` flags for telemetry: `-Pinclude-telemetry`

**Secrets location:**
- `google-services.json` - Firebase config (not in repo, must be added locally for telemetry builds)
- Signing configs - Local keystore files (not in repo)

## Webhooks & Callbacks

**Incoming:**
- None (app does not expose webhooks)

**Outgoing:**
- HTTP requests to manga source websites via OkHttp 5.3.2
  - No webhooks - periodic polling for chapter updates
  - User-agent: Configurable per source

**Extension System:**
- Dynamic plugin loading (no webhooks)
- Extensions implement `Source` interface from `source-api` module
- No pre-defined external service integrations for sources

## Third-Party Libraries with External Dependencies

**JavaScript Engine:**
- QuickJS Android (com.github.zhanghai.quickjs-java:quickjs-android:547f5b1597)
  - Purpose: Execute JavaScript for bypassing Cloudflare/anti-scraping on manga sites

**Security:**
- Conscrypt Android 2.5.3
  - Purpose: TLS 1.3 support for Android devices < Android 10

**String Similarity:**
- string-similarity-kotlin 0.1.0
  - Purpose: Fuzzy matching for manga title search

**Material Design:**
- MaterialKolor 5.0.0-alpha05
  - Purpose: Dynamic color theme generation from manga cover colors

---

*Integration audit: 2026-02-05*
