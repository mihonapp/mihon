# KuroToon Product Roadmap

This is the working feature inventory for KuroToon. It separates existing foundations from proposed work so documentation does not imply that unfinished features are already available. Statuses must be updated only after code and tests support the claim.

## Product guardrails

- Android-first application; preserve core reading reliability and user data during UI changes.
- Five bottom destinations only: **Home, Browse, Shop, Library, Me**.
- Repository controls live at **Me → Settings → Sources → Repositories**.
- KuroToon does not host reading content.
- Use the approved Kuro-chan artwork; do not invent replacement mascot assets.
- Avoid deceptive purchases, pay-to-read gates, fake availability, fake ratings, or fake repository health.
- Repository ingestion must never execute arbitrary third-party extension code.
- A feature is not complete until its error/empty/loading states and tests are addressed.

## A. App identity, release, and updates

- [ ] Verify application ID `app.kurotoon`, launcher label, and approved launcher artwork.
- [ ] Keep the upstream namespace required by the inherited codebase where necessary; do not confuse internal package names with user-facing branding.
- [ ] Ensure version name and version code are updated consistently.
- [ ] Configure a reproducible CI build and retain build artifacts.
- [ ] Publish signed APKs through official GitHub Releases after successful build and smoke testing.
- [ ] Add an update feed with current version, latest compatible version, release notes, release URL, checksum, and minimum supported version.
- [ ] Add a user-visible **Update available** prompt and explicit Update/Later actions.
- [ ] Download updates to app-private storage and verify integrity before handing off to Android's package installer.
- [ ] Handle cancellation, insufficient storage, network failure, incompatible APK, and installation failure gracefully.
- [ ] Never claim installation succeeded until Android reports the outcome; do not assume silent installation.
- [ ] Preserve the same signing identity and application ID across upgrades.
- [ ] Add a release checklist: CI green, artifact exists, signature checked, install/upgrade tested, rollback/recovery guidance documented.

## B. Navigation and shared design system

- [ ] Five destinations: Home, Browse, Shop, Library, Me.
- [ ] Consistent cosmic colors, spacing, typography, cards, icon sizes, and selected states.
- [ ] Deep-space background; purple/blue glow; restrained rose accents; gold reserved for rewards.
- [ ] Loading, empty, offline, permission-denied, and retry states for every data-driven screen.
- [ ] Accessibility: contrast, scalable text, touch target sizes, TalkBack labels, reduced motion.
- [ ] Respect Android back behavior and preserve tab/reader state.
- [ ] Keep animations subtle and avoid blocking reader performance.

## C. Home

- [ ] Branded header and clear search entry.
- [ ] Continue-reading card with title, chapter, progress, and resume action.
- [ ] Recent activity.
- [ ] Featured/trending rows based on real provider data.
- [ ] First-run onboarding for adding/enabling sources and finding a title.
- [ ] Source/network failure and no-source empty states.
- [ ] No fabricated popularity, ratings, chapter counts, or recommendations.

## D. Browse and unified discovery

- [ ] Search across enabled sources with source/provenance visible.
- [ ] Filters for category/type: manga, manhwa, manhua, webtoon, doujin, comics, and novels when supported.
- [ ] Language, genre/tag, status, year, and demographic filters only when metadata supports them.
- [ ] Sort by latest, popularity, rating, title, and year only when the source provides reliable fields.
- [ ] Source selection and source-specific result indicators.
- [ ] Search history and user-controlled clearing.
- [ ] Handle duplicate titles without silently merging unrelated series.
- [ ] Normalize series using stable IDs and carefully scored metadata matches; preserve alternate titles, source links, language, and provenance.
- [ ] Identify chapter variants, mini-chapters, translations, and releases without dropping unique chapters.
- [ ] Offer series/source/artist views where provider metadata supports them.
- [ ] Deduplicate safely with manual correction/override options; never let AI normalization silently delete source records.

## E. Shop and cosmetic inventory

- [ ] Cosmetic-only catalog initially: themes, stickers, avatars, frames, banners, and approved Kuro-chan variants.
- [ ] Clear sections for featured, themes, stickers, avatars, frames, and owned items.
- [ ] Explicit labels for free, earned, and paid items.
- [ ] Preview cosmetics before applying.
- [ ] Persistent inventory/equipped state and restore after app restart.
- [ ] Empty/loading/offline states.
- [ ] Do not expose fake purchase buttons, fake prices, timers, scarcity, or unavailable payment flows.
- [ ] Keep reading access independent of shop participation.
- [ ] Add a reward economy only after its rules, storage, anti-abuse boundaries, and user disclosures are specified.

## F. Library

- [ ] Preserve existing library data, categories, reading progress, and bookmarks.
- [ ] Clear sections or filters for all, unread, updates, favourites, and categories.
- [ ] Reading history with last-read chapter and resume action.
- [ ] Sorting and filtering.
- [ ] Bulk selection and management without hiding core actions.
- [ ] Refresh/update status and actionable error messages.
- [ ] Backup/restore compatibility tests before changing stored models.

## G. Me, profile, settings, and achievements

- [ ] Profile header and selectable avatar/cosmetics.
- [ ] Reading statistics based on actual local data.
- [ ] Optional reading goals and milestones.
- [ ] Achievements with clear unlock criteria.
- [ ] Settings organized into Appearance, Reader, Sources, Backups, Notifications, Privacy, and About/Updates.
- [ ] Source management path: Me → Settings → Sources → Installed / Discover / Repositories / Updates.
- [ ] Privacy and data controls; never expose credentials in profile or logs.
- [ ] No social/account feature implied until its backend and privacy model exist.

## H. Repositories and extensions

- [ ] Separate repository configuration, installed extensions, and enabled manga sources.
- [ ] Add repository by supported deep link, repository URL, or manual entry.
- [ ] Support multiple curated catalogs/categories while keeping repository provenance visible.
- [ ] Search/filter by source name, content type, language, and status.
- [ ] Show repository URL, enabled state, health, last checked, sync result, extension count, available updates, and useful errors.
- [ ] Validate repository metadata/schema before accepting it.
- [ ] Deduplicate repository entries by canonical identity without erasing mirrors or distinct catalogs.
- [ ] Install/validate/update/remove extension packages through a controlled pipeline.
- [ ] Explicit lifecycle states: validating, downloading, installing, installed, enabled, failed, incompatible, dormant, removed.
- [ ] Never execute repository-supplied code during repository ingestion.
- [ ] Future extension runtime must be isolated from DOM/app state, LocalStorage/IndexedDB, authentication/session data, internal UI, and unrestricted native filesystem/Capacitor APIs.
- [ ] Use structured capabilities such as SEARCH, FETCH_METADATA, FETCH_CHAPTERS, FETCH_PAGES, FETCH_IMAGES, ARTIST_METADATA, and TAGS.
- [ ] Audit permissions and network behavior; show source-specific failures without crashing the app.
- [ ] Keep MangaDex as an independent, disableable provider rather than conflating it with the extension catalog.

## I. Reader reliability and customization

- [ ] Preserve page rendering, gestures, reading direction, zoom, orientation, chapter navigation, and progress.
- [ ] Keep controls reachable and hide them when appropriate.
- [ ] Reader themes, page spacing, fit modes, background, and gesture settings.
- [ ] Optional haptics with intensity/off control.
- [ ] Optional atmospheric audio with volume, playback, source, and off controls.
- [ ] Respect battery, data, offline, accessibility, and reduced-motion preferences.
- [ ] Regression tests for navigation between chapters and returning to the same position.
- [ ] Reader errors must not lose progress.

## J. Music discovery and atmospheric engine

- [ ] Discover audio metadata in relation to a series/chapter where available and permitted.
- [ ] Do not extract or redistribute protected audio without authorization; prefer lawful providers and user-provided sources.
- [ ] Show track/source attribution and availability clearly.
- [ ] Optional context-based ambience, separate from ordinary reading audio.
- [ ] Audio focus, pause/resume, volume, mute, and battery-conscious lifecycle.
- [ ] Avoid generic AI image generation as a substitute for the atmospheric engine; prioritize responsive reader ambience and user controls.

## K. AI-assisted dubbing and context features

- [ ] Optional bubble-by-bubble dialogue dubbing with language and voice selection.
- [ ] Preserve bubble ordering and panel context; do not obscure source text or page art.
- [ ] Explicit consent and clear disclosure before sending page/dialogue content to a remote service.
- [ ] Network, privacy, cost, battery, and offline controls.
- [ ] Fallback to ordinary reading whenever AI is unavailable.
- [ ] Wider series/season context tabs where metadata supports them, without forcing the user out of the reader.
- [ ] AI-assisted metadata normalization must be reviewable and must not silently overwrite original provider data.

## L. Mascot and visual assets

- [ ] Integrate approved original Kuro-chan launcher art.
- [ ] Verify adaptive icon foreground/background and legacy densities.
- [ ] Approved avatar, sticker, expression, and pose assets.
- [ ] Empty-state and promotional artwork.
- [ ] Keep assets consistent with the canonical character design; no replacement mascot generated from scratch.

## M. Testing and release gates

- [ ] Formatting and static checks pass.
- [ ] Unit tests pass.
- [ ] SQLDelight migration verification passes.
- [ ] Release APK artifact exists and is retrievable.
- [ ] Install from a clean state on a real Android device.
- [ ] Upgrade from a previous signed KuroToon build without losing library/progress.
- [ ] Verify sources, search, series, chapters, reader, library, history, bookmarks, backups, and navigation.
- [ ] Test offline, slow network, failed source, missing metadata, and no repository cases.
- [ ] Verify update cancellation, checksum failure, incompatible version, and installer cancellation.
- [ ] Mark APK device-tested only after a real device smoke test is completed.

## Status vocabulary

- **Existing foundation:** inherited functionality believed to exist; still requires regression testing after changes.
- **In progress:** implementation has started but is not complete.
- **Planned:** documented direction only; not yet implemented.
- **Verified:** implementation and relevant tests/build checks passed.
- **Device-tested:** installed and smoke-tested on a real Android device.

Do not mark an item verified solely because its README or design document exists.
