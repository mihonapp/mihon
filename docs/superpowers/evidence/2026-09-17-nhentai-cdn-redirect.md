# NHentai image CDN redirect repair

Branch: `codex/fix-download-host-recovery`. Intended installed version: 0.2.11.

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
