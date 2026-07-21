# Yomori Repository Instructions

## Authority

This file is the repository policy layer for Yomori. Higher-level host, application, and explicit user instructions take precedence.

Do not interpret this file as standing authorization to commit, push, open or merge a pull request, delete branches, publish artifacts, alter signing, or perform a release. Those actions require explicit authorization.

## Required read order

Before planning or changing the repository, read:

1. `AGENTS.md`.
2. `PROJECT_CONTEXT.md`.
3. Relevant documents under `docs/`.
4. The affected source, tests, database schema, build configuration, and workflows.
5. Current Git and GitHub evidence relevant to the task.

`PROJECT_CONTEXT.md` is the canonical status and decision ledger for merged repository state.

An open branch or pull request may contain newer work. Distinguish between:

* planned;
* implemented locally;
* pushed;
* validated by GitHub Actions;
* physically device-tested;
* merged into `main`;
* publicly released.

Update `PROJECT_CONTEXT.md` in the same change when merged implementation changes a documented decision, milestone, migration, invariant, or release blocker.

## Repository identity

* Product: **Yomori**.
* Canonical repository: `Kamui2040/Yomori`.
* Stable branch: `main`.
* Upstream: `mihonapp/mihon`.
* License: Apache-2.0.
* Production application ID: `io.github.kamui2040.yomori`.
* Development application ID: `io.github.kamui2040.yomori.debug`.
* Yomori is an independent fork and must not imply Mihon endorsement.

Yomori imports Comic Book Lover (`.cbl`) reading orders, resolves entries through explicitly selected compatible extensions, and provides continuous cross-series reading.

## Start-of-task checks

Before modifying files:

* Verify the canonical checkout and repository root containing `.git`.
* Verify `origin`, the Mihon upstream remote, current branch, upstream tracking, and fetch state.
* Fetch current remote state before relying on branch comparisons.
* Inspect `git status`, tracked changes, staged changes, and untracked files.
* Preserve unrelated user or agent changes.
* Inspect relevant open pull requests, review state, and GitHub Actions checks.
* Read repository-specific task documents and referenced architecture decisions.
* Confirm whether the request is planning, implementation, review, validation, or publication.
* Keep planning and research no-code unless implementation is requested.

After an error, inspect actual repository and build state before retrying. Do not blindly repeat failed commands.

## Git and change safety

* Keep `main` stable and buildable.
* Implementation belongs on a focused branch named `agent/<description>`.
* Implementation should ultimately be reviewed through a narrow pull request.
* Do not create or switch branches, commit, push, open a pull request, merge, rebase shared work, delete a branch, or modify remote state unless the task explicitly authorizes it.
* Keep changes narrow, independently reviewable, and independently revertible.
* Avoid drive-by formatting, unrelated cleanup, generated-file churn, and unnecessary dependency changes.
* Preserve existing user changes and unexpected files unless the task explicitly includes them.
* Never merge while required checks are failing.
* Never merge a draft or approval-gated pull request without explicit authorization.

## Source neutrality

Yomori must not:

* bundle comic or manga content sources;
* host or operate extension repositories;
* recommend sources or repositories;
* select a source without an explicit user action;
* automatically install an extension;
* automatically trust an extension or signing identity;
* silently change a user’s effective source set or priority;
* market itself as endorsed by Mihon.

Extensions remain separate user-installed Mihon/Tachiyomi-compatible APKs. Extension repository and source choices remain user-controlled.

Explicitly assigning a user-configured reading-list category may visibly prefill that category’s ordered source defaults. The resulting effective source set must remain visible and editable before save or search. This does not authorize hidden selection, recommendation, installation, trust, or querying.

## Extension security and compatibility

Treat extensions as untrusted executable code.

Preserve:

* signature verification;
* explicit trust decisions;
* visible network activity;
* bounded concurrency and timeouts;
* user-controlled source scope;
* actionable extension failure reporting;
* extension-facing binary compatibility.

Do not rename or incompatibly change `eu.kanade.tachiyomi.source` or related extension-facing packages and model contracts as part of Yomori rebranding.

Track upstream source API, loader, trust, and signature changes. Compatibility-sensitive changes require focused tests and explicit review.

Never commit source credentials, repository credentials, tokens, signing keys, private CBL files, private library data, or private test data.

Standard builds remain telemetry-free and local-first.

## CBL import and parser safety

CBL import must:

* preserve exact `<Book>` order;
* preserve original known and unknown metadata;
* preserve data needed for later repair and rematching;
* reject DTD and entity declarations;
* reject malformed structure;
* reject entries missing required `Series` or `Number` attributes;
* reject oversized documents;
* reject excessive entry counts;
* read local picker input with a maximum 16 MiB boundary;
* support UTF-8, UTF-16LE, and UTF-16BE;
* perform validation before persistence;
* insert lists and owned records transactionally;
* maintain safe deletion cascades;
* prevent progress from referencing missing entries.

Before a reading list is saved, its visible effective source set must contain at least one currently installed online source. Direct source selection or explicit assignment of a user-configured category may populate that set, but the resulting ordered sources must be visible and editable before save.

Persist list-specific source order as the list’s priority. Retain unavailable selected or inherited source IDs visibly for later repair. Never silently discard, replace, recommend, or add a source.

## Matching and search rules

Resolve series first, then issues.

Query only installed online sources in the reading list’s visible effective source set. That set may contain directly selected sources or sources visibly inherited through an explicitly assigned user-configured category. Never query a hidden, merely available, recommended, or unconfirmed source. A search must follow an explicit user action or another clearly disclosed operation already authorized by the product flow.

Preference order, highest to lowest:

1. Entry-specific confirmed match or source override.
2. Series-specific confirmed mapping or source preference.
3. Reading-list source order.
4. Assigned-category default source order.
5. Global source preference.

List-specific source order overrides category defaults. Preserve unavailable overrides, mappings, direct selections, and inherited source IDs visibly. Do not bypass a higher-priority unavailable choice silently with a lower-priority source.

Within one operation:

* group equivalent series searches;
* avoid duplicate series searches;
* fetch each source/series issue list at most once;
* bound persisted candidate results;
* do not add search candidates to the ordinary library;
* preserve the current documented shared request limit and timeout unless the task explicitly changes them with tests and documentation.

The currently documented bounds are:

* shared concurrency limit: three extension requests;
* per-extension-request timeout: 30 seconds.

## Normalization and scoring

Preserve original imported metadata independently from normalized comparison values.

Title normalization must remain locale-independent and retain:

* full comparison keys;
* edition-free comparison keys;
* extracted year evidence;
* extracted volume evidence.

Issue normalization must preserve meaningful distinctions, including:

* annuals;
* specials;
* Free Comic Book Day issues;
* one-shots;
* issue zero;
* negative issues;
* decimals;
* fractions;
* suffixes;
* opaque identifiers.

Do not guess that distinct issue forms are equivalent without an explicit normalization rule and tests.

Automatic acceptance must satisfy every current safety gate documented in `PROJECT_CONTEXT.md` and the scoring architecture, including:

* the current automatic confidence threshold;
* the current minimum title-similarity threshold;
* equivalent issue identity;
* no conflicting user-confirmed entry or series mapping;
* membership in the reading list’s visible effective source set.

Among candidates that satisfy every automatic safety gate, choose deterministically by:

1. highest final confidence;
2. effective source priority;
3. stable candidate identity or persisted rank.

Do not use raw extension return order as the primary decision rule. Several qualifying candidates do not by themselves require review, and an exact score tie is not automatically ambiguous when the documented deterministic tie-breakers can resolve it safely.

Candidates that fail a safety gate, fall below the documented thresholds, or conflict with confirmed identity remain unresolved or require review as documented. Missing optional evidence is neutral. Conflicting evidence is penalized. External identifiers, source preference, history, or other evidence must not bypass title or issue safety gates.

Persist the confidence, runner-up evidence, decision reason, component breakdown, conflicts, source, language, remote identity, effective priority, and matcher version needed for review.

Exact thresholds and scoring weights belong in `PROJECT_CONTEXT.md` and the scoring architecture documents rather than being duplicated here.

## Confirmation, rejection, and skip protection

User-confirmed entry matches and user-confirmed series mappings are authoritative.

Automatic operations must never silently:

* replace a user-confirmed entry match;
* replace or bypass a user-confirmed series mapping;
* clear an explicit skip;
* restore a rejected candidate;
* discard an entry override;
* fall back from an unavailable confirmed or overridden source.

Rejected candidates persist independently from candidate refreshes and remain excluded from automatic matching until explicitly restored or confirmed.

Only an explicit user confirmation may replace a confirmed mapping, clear a skip through selection of a match, or select a previously rejected candidate.

Opening or browsing manual review must not initiate extension searches or network requests.

## Reading-list navigation and repair

Continuous reading follows persisted CBL order across series and selected sources.

Ordinary manga-scoped reader behavior must remain unchanged unless a task explicitly changes it.

Unresolved, ambiguous, unavailable, removed, rematch-required, incomplete, or skipped entries must stop visibly and offer the documented explicit action, such as Review, Skip, or Stop. Never silently omit or advance past such an entry.

A skip advances only the explicitly selected persisted entry. It must not remove original metadata, candidates, mappings, or repair information.

Chapter read state remains shared. Reading-list position and completion remain list-specific.

Failures affect only the relevant entry. Preserve imported metadata, mappings, confirmation state, and unaffected progress.

Repair and rematching should operate only on broken, unresolved, or explicitly selected entries. Do not broadly rematch confirmed healthy entries.

Adding resolved series to the normal library or categories remains optional and user-controlled.

## Upstream maintenance

* Keep Yomori-specific behavior isolated where practical.
* Avoid unnecessary refactors of inherited Mihon code.
* Track the current upstream baseline and relevant upstream source API or loader changes.
* Perform upstream synchronization through focused work that is separately reviewable.
* Preserve Yomori application identity, source neutrality, privacy, CBL behavior, extension compatibility, signing separation, and disabled release automation.
* Do not resolve conflicts by blindly accepting either side.
* Preserve licences, attribution, copyright notices, and required modified-file notices.
* Record significant long-lived divergences in `PROJECT_CONTEXT.md` or the relevant architecture document.

## Validation

Use repository wrappers. Do not use a globally installed Gradle.

On Windows PowerShell, use:

```powershell
.\gradlew.bat <task>
```

On Linux or macOS, use:

```sh
./gradlew <task>
```

Select the relevant subset rather than running unrelated expensive tasks blindly.

The normal validation set includes:

* formatting or style checks;
* focused tests for the changed behavior;
* `testDebugUnitTest`;
* `verifySqlDelightMigration` when SQLDelight schema, migrations, queries, repositories, or persistence behavior are affected;
* the current development or preview APK assembly task defined by the repository and GitHub Actions;
* `git diff --check`.

The current development assembly task is `assemblePreview`. Do not substitute production `assembleRelease` unless production release work and signing are explicitly in scope.

Relevant commands on Windows commonly include:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat testDebugUnitTest
.\gradlew.bat verifySqlDelightMigration
.\gradlew.bat assemblePreview
git diff --check
```

Run only applicable tasks, but do not omit a required migration or compatibility check merely to save time.

GitHub Actions is the authoritative APK and build-validation environment. A local pass does not override a failing required check.

Before reporting completion:

* inspect the complete changed-file list;
* review the full diff;
* verify expected file scope;
* check for accidental generated files, credentials, binaries, or private data;
* verify syntax, quoting, encoding, EOL handling, permissions, and idempotence where applicable;
* run `git diff --check`;
* confirm documentation and tests match the final behavior.

Do not paste large diffs or logs into reports unless explicitly requested.

## Required test coverage

Add or update focused tests where applicable for:

* parser safety, encoding, limits, and exact order;
* title and issue normalization;
* annual, special, FCBD, one-shot, zero, negative, decimal, fraction, suffix, and opaque issue handling;
* scoring thresholds and safety gates;
* strongest-candidate selection and deterministic exact ties;
* effective source-priority and stable-identity tie-breakers;
* runner-up evidence retention;
* direct and category-inherited source scope;
* category persistence, assignment, source defaults, and list overrides;
* unavailable inherited source visibility;
* source-preference hierarchy;
* unavailable overrides and mappings;
* confirmation protection;
* rejection persistence and restoration;
* skip protection;
* bounded search orchestration and duplicate-fetch prevention;
* transactions, cascades, progress integrity, and SQLDelight migrations;
* missing or failing extensions;
* manual-review behavior without network access;
* repair and rematching scope;
* cross-series navigation;
* unresolved Review, Skip, and Stop behavior;
* list-specific progress versus shared chapter read state;
* ordinary-reader regression behavior.

## Documentation discipline

Keep stable policy in `AGENTS.md`.

Keep current project status, completed milestones, active migrations, defaults, confirmed decisions, known blockers, next implementation stages, numeric thresholds, and scoring weights in `PROJECT_CONTEXT.md`.

Keep detailed architecture and feature behavior in focused documents under `docs/`.

Do not copy temporary PR state, build numbers, active branch names, or pending QA checklists into `AGENTS.md`.

When code and documentation disagree, determine whether the code is incomplete, the documentation is stale, or a branch is not yet merged. Correct the appropriate layer instead of silently choosing one.

## Development artifacts and release policy

Development APKs use:

* application ID `io.github.kamui2040.yomori.debug`;
* the reproducible public development certificate;
* development or preview build configuration;
* telemetry-free behavior.

The public development certificate must never be used for production releases.

Inherited public release automation remains disabled until release readiness is explicitly established.

Before a public release, verify at minimum:

* original Yomori visual branding;
* removal or replacement of inherited Mihon update, support, and download endpoints;
* protected production signing and documented key custody;
* representative compatible-extension QA;
* attribution and modified-file notices;
* privacy and telemetry review;
* release notes and update behavior;
* explicit release approval.

Never publish using Mihon branding, Mihon signing identity, Mihon update endpoints, the public development certificate, or incomplete release requirements.

Physical-device installation and representative device QA are performed by the user unless the user explicitly reports completed results. Agents may prepare artifacts, checksums, test plans, and QA checklists but must not claim physical-device validation they did not perform.

## Blocking failures

Stop and correct the work if it would introduce any of the following:

* bundled, recommended, hidden, or auto-trusted sources;
* querying a source outside the visible effective source set;
* extension API namespace breakage;
* unsafe XML processing;
* unbounded input or extension requests;
* silent replacement of confirmed mappings;
* silent source fallback;
* silent skipping or removal of reading-list entries;
* loss of original CBL metadata;
* inconsistent or dangling progress;
* search candidates flooding the normal library;
* upstream synchronization that removes Yomori constraints;
* credentials, signing keys, private CBL data, or private test data in the repository;
* development signing used for production;
* inherited Mihon branding or release endpoints in a public Yomori release;
* publication or merge without the required validation and explicit authorization.
