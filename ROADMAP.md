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

## v1.3.0: bookshelf library ("Library D")
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

## v1.4.0: vault
- One vault, unlocked with fingerprint or the phone's PIN (androidx.biometric), shared by:
  - **Hidden shelves:** the last spine on the bookshelf is the Vault (dashed coral, lock icon). Releasing on it unlocks, and the hidden shelves appear just before it. Releasing on it again locks.
  - **Hidden sources:** Browse → ⋯ on a source → Move to vault. Hidden sources leave Sources, global search and Updates. A dashed "Vault · N hidden sources" row at the bottom unlocks; unlocked, they show at the top in coral.
- Vault sources are **always incognito**: never saved to history (built on Mihon's `incognitoExtensions`)
- Relocks when the app goes to the background
- While locked, hidden items never appear in search, Updates, History, notifications, widgets or the recent-apps preview (FLAG_SECURE)
- Hidden items are still included in backups

## v1.5.0: settings
- Search-first settings, quick switches (incognito, dark, Wi-Fi-only downloads, English only), 8 clear groups

## Rules for all of this
- New code goes in `app/src/main/java/mihon/feature/mekuri/...`; edits to upstream files are small and marked `// Mekuri:`
- Glass blur only on Android 12+ (RenderEffect); translucent solid colour below that. No blur on large scrolling areas.
- TalkBack labels and slider semantics on the shelf; respect the system "remove animations" setting
