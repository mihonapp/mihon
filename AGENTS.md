# Yomori Repository Instructions

## Read order

Before changing the repository, read:

1. `AGENTS.md`
2. `PROJECT_CONTEXT.md`
3. Any relevant document under `docs/`
4. The affected source and tests

`PROJECT_CONTEXT.md` is the canonical statement of current project scope and decisions. Update it when an implementation changes the documented state.

## Project identity

- Product name: **Yomori**
- Canonical repository: `Kamui2040/Yomori`
- Upstream: `mihonapp/mihon`
- License: Apache-2.0
- Yomori is an independent fork and must not imply endorsement by Mihon.

## Product constraints

- Do not bundle, operate, recommend, or maintain comic or manga content sources.
- Preserve support for user-installed Mihon/Tachiyomi-compatible extensions.
- Preserve extension-facing binary API namespaces, including `eu.kanade.tachiyomi.source` and related model contracts, unless a documented compatibility migration exists.
- Keep extension repositories and extension selection user-controlled.
- Retain signature verification and explicit trust controls for third-party extensions.
- Prefer local-first storage and processing. Do not add telemetry to standard Yomori builds.
- User-confirmed CBL matches must never be silently replaced by automatic matching.
- Preserve original CBL metadata so broken mappings can be repaired later.

## Development workflow

- Keep `main` stable and buildable.
- Use focused branches named `agent/<description>`.
- Open a pull request for implementation work; do not commit feature work directly to `main`.
- Keep pull requests narrow enough to review and revert independently.
- Run the relevant Gradle checks before merging. GitHub Actions is the authoritative build environment.
- Do not merge when required checks are failing.
- Prefer deterministic scripts and tests over manual instructions.

## Validation baseline

For Android or shared-code changes, run the relevant subset of:

```sh
./gradlew spotlessCheck
./gradlew testDebugUnitTest
./gradlew verifySqlDelightMigration
./gradlew assembleRelease
```

Add focused unit tests for parsers, normalization, confidence scoring, matching thresholds, persistence rules, and migration behavior.

## Upstream maintenance

- Keep Yomori-specific functionality isolated where practical.
- Avoid unnecessary refactors of upstream Mihon code.
- Record significant upstream divergences in `PROJECT_CONTEXT.md`.
- Resolve upstream changes in focused synchronization pull requests.
- Never overwrite Yomori-specific product constraints during an upstream sync.

## Security and privacy

- Treat extensions as untrusted executable code.
- Never auto-install or auto-trust extensions.
- Do not commit credentials, signing keys, tokens, source credentials, or private test data.
- Network searches must be initiated by explicit user actions or clearly disclosed automated operations.
- Limit multi-source matching requests to user-selected extensions and apply bounded concurrency.

## Release policy

- Pull requests and development branches produce unsigned/development artifacts through GitHub Actions.
- Public releases require a Yomori application ID, Yomori branding, release signing configuration, and release notes.
- Do not publish a release using Mihon branding, Mihon signing identity, or Mihon update endpoints.
