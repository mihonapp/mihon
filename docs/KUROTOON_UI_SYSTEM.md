# KuroToon UI System

Status: design foundation. This document defines visual rules and screen behavior; it is not evidence that every screen has already been implemented.

## 1. Brand principles

- **Mascot fidelity:** Kuro-chan's approved original artwork is canonical. Do not redraw, replace, or approximate her official launcher icon.
- **Cosmic, not noisy:** deep-space surfaces, soft purple/blue light, rare rose accents, and gold used sparingly for rewards or premium-looking details.
- **Reader first:** no visual effect may slow page turning, obscure panels, or make reading controls difficult to reach.
- **Consistent surfaces:** shared spacing, corner radii, type scale, cards, empty states, and loading/error states across all tabs.
- **Accessible by default:** readable contrast, scalable text, reduced motion, and explicit sound/haptic controls.

## 2. Initial color tokens

| Token | Value | Use |
|---|---|---|
| Space | #090711 | Primary dark background |
| Surface | #171326 | Elevated cards and panels |
| Purple | #8B5CF6 | Primary brand accent |
| Violet glow | #B69CFF | Soft highlights |
| Cosmic blue | #5B8CFF | Secondary action/information |
| Rose | #FF4F8B | Small emphasis, not every button |
| Gold | #F5C86A | Coins, milestones, earned rewards |
| Primary text | #F7F3FF | Main text on dark surfaces |
| Secondary text | #B9B2CB | Supporting text |

The Android resource file contains these tokens. Existing UI components must be audited before claiming that every screen consumes them.

## 3. Navigation

Keep five primary destinations: **Home · Browse · Shop · Library · Me**. Preserve clear labels with icons and selected states. Repositories belong under **Me → Settings → Sources → Repositories**, not in the bottom bar. If the existing Mihon navigation architecture requires staged migration, avoid breaking established reader/library entry points while transitioning.

## 4. Screen design requirements

### Home
- Compact branded greeting/header and visible search entry.
- Continue-reading card with last-read chapter and clear resume action.
- Featured/trending rows using actual source covers and loading placeholders.
- Avoid fabricated ratings, chapter counts, or availability.
- First-run state should guide the user to add a source and find a title.

### Browse
- Search results should show source/provenance where useful.
- Sorting: Latest, Popular, Rating, Title, Year when the underlying data supports each field.
- Beginner-friendly filters first; advanced filters can expand.
- Empty, loading, network failure, and no-source states must be distinct.

### Shop
- Cosmetic catalog only at first: stickers, avatars, frames, banners, themes.
- Label earned/free/paid status honestly. Do not imply purchases or currency systems exist before they do.
- No pay-to-read gates, deceptive scarcity, or pressure timers.
- Kuro-chan cosmetics must use approved artwork and consistent asset variants.

### Library
- Make reading, unread updates, bookmarks, favourites, and history easy to distinguish.
- Preserve existing library data and categories during UI migration.
- Support empty state, filter/sort, and bulk management without hiding core actions.

### Me
- Compact profile header, avatar, stats, reading goals, achievements, and sticker showcase.
- Settings entry must be easy to find.
- Keep privacy, backups, reader settings, appearance, and source management organized in clear groups.
- Do not expose sensitive account/source credentials in profile summaries.

### Reader
- Preserve core page rendering, gestures, progress, and orientation behavior.
- Reader controls should be reachable but disappear when not needed.
- Atmospheric audio and haptics are optional, adjustable, and off or conservative by default.
- AI dubbing must be separately controlled, show language/voice settings, and respect network, battery, and privacy choices.

### Repository manager
- Path: Me → Settings → Sources → Repositories.
- Distinguish repository configuration from individual extensions and manga sources.
- Show truthful sync/health status, last checked time, update status, and errors.
- Repository ingestion must not execute arbitrary extension code. Any future runtime must use explicit capabilities and a constrained request/response contract.

## 5. Shared interaction states

Every data-driven screen needs loading, populated, empty, offline/error, and retry states. Actions must provide visible feedback. Avoid placeholder buttons that look functional but do nothing. Respect Android back navigation and preserve reading progress.

## 6. Asset checklist

Before final branding release, integrate and verify the approved files for:
- Launcher adaptive icon (foreground/background and legacy densities as required).
- Splash/launch artwork where supported.
- Kuro-chan avatar and approved sticker/pose/expression sheets.
- Empty-state artwork and any promotional banner art.

Do not generate replacement character art if the original source files are not available in the repository. Request/retrieve the actual approved asset instead.

## 7. Acceptance gate before APK sharing

- [ ] App installs separately from Mihon using the KuroToon application ID.
- [ ] Launcher label and approved icon are correct.
- [ ] Five navigation destinations open and preserve state.
- [ ] Open a source, browse titles, open a series, and read a chapter.
- [ ] Library, history, bookmarks, and backups do not regress.
- [ ] All new controls have working actions or are clearly marked as planned.
- [ ] Build workflow, formatting, unit tests, and migration checks pass.
- [ ] Install and smoke-test on a real Android device before calling the APK device-verified.
