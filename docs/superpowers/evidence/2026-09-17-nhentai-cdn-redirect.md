# NHentai image CDN redirect repair

Branch: `codex/fix-download-host-recovery`. Verified installed version: 0.2.11.

## Reproduction and root cause

The installed 0.2.10 build was revision `45f72772d1629b74eb76556324f945e7a47fe651`,
not an older installation. The saved Chinese NHentai.xxx task (source ID
`560822675588930101`, chapter ID 12) contained 48 images, all failed.
Both the queue and the isolated host stderr reported `DOMAIN_DENIED` for
`i4.nhentaimg.com` under `eu.kanade.tachiyomi.extension.all.nhentaixxx`.

The queue contains `i4.nhentai.xxx` URLs. A live HTTP request to the saved first
image confirmed a 301 redirect to `i4.nhentaimg.com` with the same asset path.
The installed 1.6.11 converted package has no declared-domain metadata. The
existing runtime page registration allows the original host; the broker then
correctly applies its destination check to the unknown migrated host and fails.

## Change and boundaries

The broker recognizes this image-service migration only for the authenticated
NHentai.xxx extension, GET/HEAD, HTTPS on port 443, matching numbered image shard,
and identical asset path/query. The original request still needs authorization.
The destination permission applies only to that redirect; it is not added to
runtime or global host permissions. Further redirects still undergo checks, and
cross-origin authentication headers and cookies are stripped as before.

## Source verification

The new redirect regression initially failed with expected 200 versus actual
403. After the change it passes along with tests rejecting unrelated extensions,
unrelated/forged hosts, local addresses, HTTP downgrade, nonstandard ports,
different shards, altered asset path/query, direct CDN access, and later redirects.

A separate live smoke test copied the user's queue/preferences to a temporary
directory and used the installed real NHentai.xxx package and executable host.
The updated desktop broker downloaded all 48 saved pages:

```text
DOWNLOAD_SMOKE status=COMPLETED ready=48 bytes=13563047 error=null
```

This first smoke run uses checkout desktop classes. Installed artifact and real
profile verification must be recorded separately after packaging.

The focused suite completed 57 discovered tests, zero failures/errors, and one
opt-in live test skipped (the live test was executed separately above). It covers
all download tests, CDN redirects, broker permissions/session isolation/network
policy, and the isolated-extension workflow. `:desktop-app:spotlessCheck` passes.

The manual `InstalledProfileDownloadRetry` test helper refuses checkout desktop
classes and takes the profile lock. It retries a selected original queue entry,
registers its offline assets in the database, and opens it using a reader with
no online source or network supplied. It is explicitly invoked, never part of
the default automated test suite.

## Installed artifact and original profile verification

`:desktop-app:packageMsi` and `verifyCleanDistribution` passed. The MSI is
`desktop-app/build/compose/binaries/main/msi/mihondesk-0.2.11.msi`, SHA-256
`D4A2506DB5CD7695AB2DCC8A8FE707934BE77F2C6D1788DD3EEB066DABF41DC3`.
Windows Installer completed the normal upgrade with exit code 0. Before retrying
the task, the original preferences, queue, and database hashes still matched
their pre-install backups.

The installed application at `C:\Users\18734\AppData\Local\mihondesk` reports
version 0.2.11, revision `6854210df544db20bb70d0aaed7c39871f33cb01`, dirty=false.
Its desktop JAR matches the packaged JAR, SHA-256
`715E8D73ED40B00E1B51A896EDBF8041B6100D8DCF2EC938E37D7C69E28B82BD`.

The manual helper loaded the installed application JAR and used the installed
EXE as its isolated extension host. It retried chapter 12 in the original user
profile, with its real download directory and database mutation port:

```text
INSTALLED_RETRY status=COMPLETED ready=48 bytes=13563047 error=null
OFFLINE_READER page=1 decoded=true
OFFLINE_READER page=24 decoded=true
OFFLINE_READER page=48 decoded=true
```

The first manual invocation completed the download, but its separate reader
check lacked the packaged launcher's Skiko native-library option. Repeating the
helper with `-Dskiko.library.path=<installed>/app` from `mihondesk.cfg` verified
all three offline pages successfully, exit code 0. No production reader change
was needed. The original queue now records chapter 12 as COMPLETED, 48/48 READY,
with no error; the existing chapter 9 remains COMPLETED, 145/145 READY.

Local execution logs and profile backups are under the ignored
`build/cdn-redirect-evidence/` directory. This evidence covers the reported
NHentai task and scoped redirect compatibility; it does not establish that
every extension or future CDN migration works.
