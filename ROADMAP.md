# Mekuri roadmap

The agreed redesign. Visual reference: the "Mekuri redesign" canvas (owner's claude.ai artifacts).
Look: soft glass layered over minimal, flat, rounded shapes. Ink-dark base, lavender primary, coral accent, mint "downloaded" colour.
Motion level 4 of 5. Every release is installable on its own.

## v1.1.0: quick wins (done)
- Auto-scroll in the long strip reader: play/pause, 1–10 speed, holds still while a finger is down
- Long strip only (stored per-manga modes untouched, so backups stay compatible)
- English-only sources by default
- Mekuri theme (Default theme recoloured). Light / Dark / System and Pure black stay; the other themes are hidden.

## v1.2.0: navigation (done)
- Floating, icons-only nav pill: Library · Updates · Offline · Browse · You
- **Offline** tab: the download queue at the top (only while active), then downloaded manga grouped by series
- **You** hub: tiles for History, Settings, Stats, Backup and data, Categories and About, plus the Incognito and Downloaded-only switches (Vault tile comes in v1.4; a swipeable History row comes later)
- Browse tabs relabelled: Sources · Add-ons (extensions) · Move (migrate)
- Tablets keep the side navigation rail (with the same 5 tabs)

## v1.3.0: bookshelf library ("Library D") (done; not yet: first-run ghost hint, pinning a spine colour, ⋯ category menu on the shelf)
- The home screen is a full-screen bookshelf, one spine per category
  - Spine **width** grows with the title count: min 48 dp, max about 108 dp
  - Spine colour comes from the last-read cover in that category (Palette API); the user can pin a colour
- **Hold and slide**
  - The spine under the finger lifts, its name and count show above, and the glow takes its colour
  - Haptic tick on each spine; near either edge of the shelf it glides sideways
  - Release opens the category; dragging up off the shelf, then releasing, cancels
  - The shelf is inset from the screen edges so it doesn't fight the system back gesture
- **Inside a category**
  - A compact overview shelf at the top: tap, slide, or swipe the covers to change category
  - A ⋯ menu for the category's actions; Back returns to the shelf, centred on that category
  - Spines are tap-or-slide only (no long-press); long-press on covers still multi-selects, as in Mihon
- Mihon's features stay: pull-to-refresh, sort, filters, search, multi-select
- First-run hint: a ghost finger slides across the shelf once
- Settings switch **Classic library** returns to Mihon's own library screen (safety net; also keeps upstream merges easy)

## v1.4.0: vault (done; still to do: hiding vault manga from library-update notifications and widgets, FLAG_SECURE in the reader, a ⋯ menu to move a shelf into the vault from the shelf itself)
- One vault, unlocked with fingerprint or the phone's PIN (androidx.biometric), shared by:
  - **Hidden shelves:** the last spine on the bookshelf is the Vault (dashed coral, lock icon). Releasing on it unlocks, and the hidden shelves appear just before it. Releasing on it again locks.
  - **Hidden sources:** Browse → ⋯ on a source → Move to vault. Hidden sources leave Sources, global search and Updates. A dashed "Vault · N hidden sources" row at the bottom unlocks; unlocked, they show at the top in coral.
- Vault sources are **always incognito**: never saved to history (built on Mihon's `incognitoExtensions`)
- Relocks when the app goes to the background
- While locked, hidden items never appear in search, Updates, History, notifications, widgets or the recent-apps preview (FLAG_SECURE)
- Hidden items are still included in backups

## v1.5.0: settings (done)
- Search-first settings home on phones (opens Mihon's full-text settings search), quick switches (Incognito, Dark mode, Pure black, Wi-Fi-only downloads), 8 tiles (Look, Reading, Library, Downloads, Sources, Privacy, Trackers, Backup) opening Mihon's pages, then Vault / Advanced / About. Tablets keep the two-pane list.

## Rules for all of this
- New code goes in `app/src/main/java/mihon/feature/mekuri/...`; edits to upstream files are small and marked `// Mekuri:`
- Glass blur only on Android 12+ (RenderEffect); translucent solid colour below that. No blur on large scrolling areas.
- TalkBack labels and slider semantics on the shelf; respect the system "remove animations" setting

## v1.6.0: bookshelf, part 2 (done)
- ⋯ next to the shelf name on the bookshelf: rename, spine colour (8 colours or "from cover"), move left/right, move to/out of the vault
- Smart shelves at the start of the shelf (sparkle spines): Recently read (14 days), New this week, Unread, Downloaded. Each opens a cover grid; vault manga are left out while locked. Settings → Library → Smart shelves turns them off.
- First-run demo: a ghost finger slides across a few spines once

## v1.7.0: reader (done)
- Auto-scroll speed remembered per manga; volume keys change the speed while auto-scrolling (never the media volume); "Stop at chapter end" chip; "≈ N min left in chapter" estimate
- Updater: version comparison fixed (first differing number decides), with tests

## v1.8.0: look and home (done)
- Real frosted glass (Haze 2.0.1) on the reader's top, auto-scroll and bottom bars, blurring the pages behind them (Android 12+; a translucent tint below). Not on the nav pill: Mihon's lists stop above it, so there is nothing behind it to blur. That needs every list to scroll under the pill, which is a bigger change.
- "Continue reading" home-screen widget (cover, title, chapter; opens the manga; hides vault manga; blank when the app lock is on)
- Reading streak card at the top of Statistics (days in a row, best run, chapters in the last 7 days)

## v1.9.0: feedback round (done)
- Bookshelf: drag to move along the shelf (with momentum), tap a spine to open it. No more release-to-open. All spines the same height; width = number of titles.
- Smart shelves: Recently read, New this week and Unread removed; only Downloaded remains (Settings → Library → Smart shelves turns it off)
- "New chapters" marker: a coral bar on a spine when a manga on that shelf got chapters in an update since you last read it; it clears once you read them
- Nav icons: bell for Updates, globe-with-magnifier for Browse
- Auto-scroll up to 10× (0.25× … 10×), eases in when it starts; coral hairline shows chapter progress while reading
- Library card styles: "Mekuri cards" (rounded covers, unread count, progress bar) and "Wide cards" (one row each: cover, chapters read, unread, progress, source, resume). In the shelf's Filter → Display.
- Statistics: streak card and section titles centred

## v1.9.1: shelf fixes (done)
- Bookshelf: every spine (first three, the vault) can reach the middle and be highlighted; drags and flings settle the nearest spine into the middle; clipping at the screen edge (vault spine no longer cut off)
- Orange marker only for shelves with chapters fetched in an update in the last 7 days and still unread
- Inside a shelf: run a finger across the small shelf (title follows) and let go to switch; tap still works
- Tapping Updates again opens History (Offline's reselect opens the download queue)
