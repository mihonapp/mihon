# Casting the reader to a TV

Mihon Casting can show what you are reading on a TV (or any other screen) while your phone or
tablet works as a smart remote. The reader on the phone stays the source of truth: every page turn,
scroll, auto-scroll step or remote command happens on the phone and the TV mirrors it with its own
orientation, size and background.

## Ways to cast

| Target | How it connects | Best for |
|---|---|---|
| **External display** | Android's secondary-display (Presentation) API. Works with the system *Cast screen* / *Smart View* / *Screen mirroring* session (Chromecast, Google TV, Android TV, Miracast) and with wired HDMI / USB-C adapters. | Lowest latency, no network setup. |
| **Web receiver** | The app runs a tiny HTTP server on the phone. Open the shown address (for example `http://192.168.1.23:8765`) in the TV's web browser, a console, a laptop plugged into the TV, or any other device on the same Wi-Fi. | TVs without screen casting support, browsers, sharing with a second device. |

### External display

1. Start a screen cast from your phone's Quick Settings (Cast / Smart View / Screen mirroring) or
   plug the phone into the TV.
2. Open a chapter in the reader and tap the **Cast** icon in the top bar.
3. Pick the display under *External display*. Mihon takes over the TV and opens the remote.

Some phones (for example Pixels) only offer screen casting to Chromecast-type devices; Miracast
(Smart View) is available on most Samsung, Xiaomi and other devices. The TV keeps showing Mihon's
pages, not a mirror of the phone, so the phone screen can be dimmed or show the remote.

### Web receiver

1. Open a chapter, tap the **Cast** icon and press **Start web receiver**.
2. Open the address on the TV (or copy it and send it to any device on the same network).
3. The receiver page follows the phone instantly. Arrow keys / clicks on the receiver page turn
   pages too, if the device has a keyboard or a pointer.

The port and the image quality (original bytes or an optimized JPEG for older TV browsers) can be
changed under *Settings → Reader → Casting*.

## Remote and presentation settings

The **Cast remote** button in the bottom bar opens the remote sheet:

* **Touchpad** – swipe to scroll (webtoon / continuous modes) or pan an oversized page; tap the
  sides to turn pages; tap the center to start or pause auto-scroll. Horizontal swipes turn pages in
  paged modes.
* **Previous / next page and chapter** buttons, **play / pause** for auto-scroll.
* **Cast orientation** – landscape, portrait (for vertically mounted screens) and flipped variants.
* **Cast page size** – fit screen / width / height / original size plus a zoom slider (paged modes),
  or the strip width (continuous modes). These only affect the cast target, not the phone.
* **Cast background** – black, gray or white.
* **Auto-scroll** – speed (px/s) and scroll rate (smooth or stepped) for continuous modes, or the
  page interval (seconds per page) for paged modes. Auto-scroll also works without casting.

Casting stops when you leave the reader, when the external display disconnects, or when you press
**Stop casting**. A notification with previous / next / stop actions is shown while casting.
