# Upstream Synchronization

Yomori is based on `mihonapp/mihon` and should stay close enough to upstream that security fixes, Android compatibility changes, and extension-API updates can be adopted without large rewrites.

## Remotes for a local checkout

```sh
git remote -v
git remote add upstream https://github.com/mihonapp/mihon.git
git fetch --all --prune
```

Expected remotes:

- `origin`: `https://github.com/Kamui2040/Yomori.git`
- `upstream`: `https://github.com/mihonapp/mihon.git`

## Synchronization workflow

1. Start from a clean, current Yomori `main`.
2. Fetch `upstream/main`.
3. Create `agent/sync-mihon-YYYY-MM-DD`.
4. Merge upstream into the synchronization branch without discarding Yomori changes.
5. Resolve conflicts using `AGENTS.md` and `PROJECT_CONTEXT.md` as product constraints.
6. Review extension loader, source API, database migrations, build tooling, updater, telemetry, branding, and reader changes explicitly.
7. Run the full CI baseline.
8. Open a focused pull request that lists upstream commits, conflicts, and retained Yomori divergences.

## Protected Yomori decisions

An upstream sync must not silently restore:

- Mihon product name or release links
- Mihon application identity once Yomori identity work lands
- Telemetry in standard builds
- Automatic source installation or trust
- Bundled or recommended content sources
- Replacement of user-confirmed CBL matches

## Compatibility review

For each upstream update, inspect changes involving:

- `source-api`
- Extension loading and metadata versions
- Extension signing and trust
- Database schema and migrations
- Reader chapter navigation
- Backup and restore
- Android SDK and Gradle requirements

Record material divergence or new compatibility risks in `PROJECT_CONTEXT.md`.
