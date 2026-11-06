# CLAUDE.md — Mekuri (personal Mihon fork)

Read `MAINTAINING.md` first. It has the full list of changes from upstream, the variants, signing, releases and upstream syncing.

## Facts
- Repo `vsrksaketh-dev/mekuri` (public fork). Remotes: `origin` = my repo, `upstream` = `mihonapp/mihon` (push disabled).
- Base: upstream `main` @ `70c73c7` (after tag v0.21.3). Package `com.vsrksaketh.mekuri`, app name Mekuri, versionName 1.0.0.
- macOS: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`, SDK at `~/Library/Android/sdk`.
- Build: `./gradlew assembleRelease -Pinclude-telemetry=false -Penable-updater=true`. Never use `-Pdist=ci|github`, which turns on Firebase.
- CI checks: `./gradlew spotlessCheck testDebugUnitTest verifySqlDelightMigration`. Run `./gradlew spotlessApply` before committing.
- Keystore lives outside the repo (`~/android-keys/`). Never commit keystores, `keystore.properties`, passwords or tokens.

## Rules
- Don't rename the Kotlin namespace/packages (`eu.kanade.tachiyomi`, `tachiyomi.*`, `mihon.*`). That's huge diffs and upstream conflicts.
- Keep upstream-file edits minimal and tagged `// Mekuri:`. Prefer new files for features.
- Never break extensions, extension repos, backup/restore compatibility with Mihon, trackers (keep OAuth schemes), or DB migrations.
- `versionCode` must track upstream's (migrations depend on it). Only change `versionName` for Mekuri releases; release tag = `v<versionName>`.
- One branch and one PR per feature. Ask the owner before anything destructive or anything that publishes outside this repo.
