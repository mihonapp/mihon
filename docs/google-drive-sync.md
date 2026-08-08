# Cross-device sync via Google Drive

The library, categories, chapters, reading progress, history and tracking are merged between
devices through a single snapshot file stored in the **application data folder** of your Google
Drive — a hidden, per-app folder. The app cannot see any other file in your Drive.

App settings and source settings are deliberately **not** synced: they describe a device (storage
location, reader layout, screen size) rather than the library.

Everything is configured from inside the app. Nothing is set at build time.

## Setup

Google does not let any app reach a Drive without an OAuth client, so you have to create one —
once, for all your devices.

### 1. Create the OAuth client (once, ~2 minutes)

1. Open the [Google Cloud console](https://console.cloud.google.com/) and create a project.
2. **APIs & Services → Library** → enable **Google Drive API**.
3. **APIs & Services → OAuth consent screen**:
   - user type *External*, fill in the app name and your email;
   - add the scope `https://www.googleapis.com/auth/drive.appdata`;
   - add your own Google account under **Test users** — while the app stays in testing mode no
     Google verification is needed.
4. **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - application type **Desktop app**;
   - copy the **client ID** and the **client secret**.

Application type *Desktop app* is what makes a single client usable everywhere: unlike an *Android*
client it is not bound to a package name or to a signing certificate, so the same two values work
on every phone and every build you install.

The client secret of a desktop client is not a real secret — Google documents it as
non-confidential for installed apps, since it necessarily ships inside them. It only identifies
your project.

### 2. Enter them in the app (per device)

**More → Settings → Data and storage → Sync**

1. Paste the **OAuth client ID** and the **OAuth client secret**.
2. Tap **Connect Google Drive**. The consent screen opens in your browser; approve it and the tab
   tells you to go back to the app.
3. Set **Automatic sync frequency** if you want background syncs, or use **Sync now**.

Repeat on each device with the same two values and the same Google account.

## How a sync runs

1. A snapshot of the local library is built with the existing backup creator (`BackupCreator`).
2. The remote snapshot is downloaded, if there is one.
3. Both are merged record by record (`SyncMerger`), settling conflicts with the `lastModifiedAt`
   timestamps the app already maintains. Nothing is dropped: an entry added on device A and an
   entry added on device B both survive.
4. The merged snapshot is uploaded back.
5. The merged snapshot is applied to the local database with the existing backup restorer.

Because a device merges rather than overwrites, a phone that has been offline for weeks cannot
wipe what happened elsewhere. Un-marking a chapter as read is also preserved, since read state is
taken as a whole from whichever side was modified last.

The snapshot is a regular `.tachibk` file, so it can be downloaded from Drive and restored by hand.

## How sign-in works

Google accepts two redirect styles for an installed app: a custom scheme tied to the package name
and signing certificate, or a **loopback address**. This app uses the loopback form.

While you are on the consent screen, `LoopbackRedirectServer` listens on `127.0.0.1` on an
ephemeral port and Google redirects your browser there with the authorization code. The listener
is open only for the duration of the sign-in (5 minutes at most), accepts a single connection, and
is closed whatever happens. The exchange uses PKCE.

That is what removes the package name, the SHA-1 fingerprint and the rebuild from the setup.

**Disconnect Google Drive** clears the stored tokens but keeps the client ID and secret, since
those belong to your Cloud project rather than to the session. The remote snapshot is left in
place, so reconnecting picks up where you left off.

## Installing next to upstream Mihon

The application ID is `app.mihon.sync` and the app name is *Mihon Sync*, so this build installs
side by side with the official `app.mihon` without touching its data. The two apps share nothing:
migrating means exporting a backup from one and restoring it in the other.

Note the `debug` build type appends `.dev` to the application ID — irrelevant to sync now that the
OAuth client is not bound to a package name, but it does mean a debug and a release build are two
separate installs with separate libraries.
