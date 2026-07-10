# Decision Log

## 2026-07-10 — Base Yomori on Mihon

Yomori uses Mihon as its upstream base to retain a mature Android reader, downloader, library, and compatible extension architecture.

## 2026-07-10 — Remain source-agnostic

Yomori will not bundle, operate, maintain, or recommend content sources. Extension repositories, installed extensions, and matching scope remain user-controlled.

## 2026-07-10 — Use confidence plus ambiguity margin

Automatic CBL matching requires both a sufficient confidence score and a sufficient lead over the next candidate. Users may tune thresholds and manually override every result.

## 2026-07-10 — Keep user confirmation authoritative

A user-confirmed series or issue mapping cannot be silently replaced by later automatic matching.

## 2026-07-10 — Build without telemetry

Standard Yomori CI and release candidates are built without optional telemetry. GitHub Actions is the authoritative build environment.
