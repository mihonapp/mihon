# T3 real extension compatibility evidence

**Current result:** all 7 real APKs convert and load, exposing 84 runtime sources. The final combined run (`73059`) passed 18 tests, 0 failures, 0 skips, in 32 seconds through the shared Gradle mutex wrapper. This proves conversion/loading and the specified offline contracts; it is not full live-site or image-reading verification. Later sections retain the actual red-to-green failure history.

Worktree: `D:\my project\mihon-w\.worktrees\suwayomi`, branch `codex/suwayomi-evolution`. No user extension installation or data was modified. APK downloads and converted artifacts remain in ignored `.superpowers/sdd/t3-samples`.

## Provenance

All seven APKs were downloaded from Keiyoushi official GitHub release assets; downloaded SHA-256 matched the published `release-assets.json` digest. API below is the binary APK manifest version prefix. Current source files fetched from extensions-source/main are research aids, not proof of the binary revision.

| Package | Version | API | SHA-256 |
|---|---|---|---|
| [eu.kanade.tachiyomi.extension.all.mangadex](https://github.com/keiyoushi/extensions/releases/download/f303b9c/tachiyomi-all.mangadex-v1.6.0.apk) | 1.6.0 | 1.6 | `35d220b64162cb9409da47af81fbb09ae96f170ed77eaa0ffb440add65f92f35` |
| [eu.kanade.tachiyomi.extension.all.mangafire](https://github.com/keiyoushi/extensions/releases/download/6ca40f6-0/tachiyomi-all.mangafire-v1.6.34.apk) | 1.6.34 | 1.6 | `6cfabb4ca49dbcad33688c89876b18ae61d62842f373abbc9139568711145b0b` |
| [eu.kanade.tachiyomi.extension.all.mangaplus](https://github.com/keiyoushi/extensions/releases/download/1fbc35e/tachiyomi-all.mangaplus-v1.6.66.apk) | 1.6.66 | 1.6 | `e9511110525f81f30139704bda07b0a910e5100e42326570c5c9870a7529f94b` |
| [eu.kanade.tachiyomi.extension.all.nhentaixxx](https://github.com/keiyoushi/extensions/releases/download/6ca40f6-0/tachiyomi-all.nhentaixxx-v1.6.11.apk) | 1.6.11 | 1.6 | `0f0c2f62902d76558525260e8a7a3ce33ac1d836f6e29b5e011ab34f6c99fd41` |
| [eu.kanade.tachiyomi.extension.en.ezmanga](https://github.com/keiyoushi/extensions/releases/download/6ca40f6-0/tachiyomi-en.ezmanga-v1.4.62.apk) | 1.4.62 | 1.4 | `146a27759226edc8af91bab855e9e262b1091627d9c39dc35fb5d89df481f5b8` |
| [eu.kanade.tachiyomi.extension.en.readcomiconline](https://github.com/keiyoushi/extensions/releases/download/6ca40f6-1/tachiyomi-en.readcomiconline-v1.4.44.apk) | 1.4.44 | 1.4 | `f90d661f8afcd144650c293ea4ea77f1ad635d121b3fe4713300bc2724a7d15c` |
| [eu.kanade.tachiyomi.extension.zh.bilimanga](https://github.com/keiyoushi/extensions/releases/download/6ca40f6-2/tachiyomi-zh.bilimanga-v1.6.14.apk) | 1.6.14 | 1.6 | `6447bbc28f8b5dfd5fcbef291f1c3a318e7ff4409424151e21f3bdf148f702d5` |

## Changes

- `AxmlManifestParser.kt`: correct binary attribute offsets/stride, typed strings/floats, extended UTF-8/UTF-16 string lengths, malformed chunk errors. Actual APKs previously all parsed version 1.0.0; fixing layout exposed typed API floats previously interpreted as integer bits.
- `TachiyomiExtensionConverter.kt`: cache key includes original APK SHA-256, converter and compatibility versions, and repository metadata. Validated cache reuse; changed inputs invalidate. Atomic replacement preserves existing conversion on failure. Generated DEX JARs now compute JVM stack frames from APK and host class hierarchy; missing dependency is an explicit early conversion error.
- `android.content.ContextWrapper` and `Application`: correct inheritance and profile context delegation. Intent values and explicit ActivityNotFoundException for unsupported Android activity launches.
- `app.cash.quickjs.QuickJs`: tested evaluate/create/close ABI subset backed by Mozilla Rhino 1.8.1 (MPL-2.0, Java 11+, compatible with packaged Java 17). Safe globals, Java access disabled, 2-second instruction deadline, input size limit. Primitive results only; callers can JSON.stringify objects. Native QuickJS bytecode/bindings are not implemented.

## Validation history

- Initial real APK red: all seven rejected because version parsed as 1.0.0.
- Second real red: layout corrected, libVersion TYPE_FLOAT decoded as integer bits.
- Third real red: six missing JVM stackmap frames; NHentai missing ContextWrapper.
- Cache and real binary manifest regressions passed in subsequent runs (12 tests), actual APK load remained red while additional ABI gaps were repaired.
- QuickJs ABI red: ClassNotFoundException before implementation.

## Coverage interpretation

Matrix categories: ordinary HttpSource (EZManga/BiliManga), SourceFactory (MangaDex/MangaPlus), custom headers/Cookie (NHentaiXXX), request-affecting preferences (MangaDex/ReadComicOnline), JavaScript extraction (ReadComicOnline), image decryption (MangaPlus), WebView-dependent APK (MangaFire). Category membership does not mean all associated behavior passed. Actual outcomes are reported separately below.

MangaFire uses WebView as part of an automatic shape CAPTCHA solver. That branch is not exercised. T4 agent verifies the generic Android WebView bridge using a local page; this is not claimed as a real MangaFire end-to-end pass.

Live station availability and full reading chains have not yet been verified by this test; offline conversion/load and API contracts are separate evidence.

## R8 constructor recovery

The raw DEX (EZManga example) allocates `Lz;` with `new-instance v0`, then invokes `java.lang.Object.<init>(v0)`. Dex2jar otherwise emits `new Object`, destroying the allocation type. `DexConstructorNormalizer` restores the exact DEX allocation class before translation, only for adjacent meaningful instructions, no-argument Object construction, a direct Object superclass, and no conflicting declared constructor. It synthesizes the missing no-argument constructor and rewrites that constructor invocation. Unsupported/ambiguous forms fail explicitly. `DexConstructorCompatibilityTest` covers original allocation preservation and rejects invalid JVM Object-to-subtype static assignment before package publication.

At converter version 4, early validation exposed the same underlying optimizer form in five APKs; previous successful load was insufficient because the affected helper classes were lazy. Version 5 adds the DEX-based recovery.

## Verified converter v5 outcomes (2026-09-15)

Shared-wrapper run: `:desktop-app:test --tests *RealExtensionSampleTest --tests *DexConstructorCompatibilityTest`, BUILD SUCCESSFUL in 41s. APK inventory is an opt-in diagnostic matrix; passing the matrix means at least one real APK loaded, not every APK supported.

- NHentaiXXX: 4 real runtime source IDs; offline popular request and empty HTML parsing succeeded. Origin/referer and broker extension/source identity captured.
- BiliManga: 1 source; real filter and preference DTOs succeeded; offline popular succeeded twice. Changing POPULAR_MANGA_DISPLAY through the real IPC preference API changed `/top/weekvisit/1.html` to `/top/monthvisit/1.html`; requests retained `Cookie: night=1`, `Accept-Language: zh`, Origin and Referer. No source-site traffic was made.
- EZManga: 1 source; request reached broker at `/api/v1/series?page=1&perPage=20&sort=popular` with JSON Accept and source Referer. Intentional HTML fixture then failed JSON decoding; this is not a site failure or full parser pass.
- MangaPlus: 9 sources and real preferences loaded; emitted rankingV2 request, but its optimized protobuf decoder still failed JVM verification. Image decryption path remains unverified.
- ReadComicOnline: 1 source and preferences loaded; request execution exposed Enum constructor elimination, addressed in v6 and pending re-run. QuickJs ABI/runtime contract separately passed 2 tests, including deadline, isolation and Java denial.
- MangaDex: still failed inherited constructor initialization in v5; v6 adds restoration from direct inheritance facts.
- MangaFire: not executed; conversion reported missing ConsoleMessage.MessageLevel. Added value/enum ABI, awaiting conversion re-run.

The constructor normalizer was subsequently extended using the same original DEX facts: straight-line argument preparation that neither overwrites nor exposes the allocation register, direct-parent constructors with forwarded arguments (including eliminated Enum constructors), and a constructor `this` call that bypasses an eliminated direct-parent Object constructor. Branches, allocation escapes, conflicting constructors, and unresolved cross-DEX parents remain explicit unsupported cases. JVM stack frames are recomputed after this transformation.

## Converter v7 result

`47133`: shared wrapper, BUILD SUCCESSFUL in 45s. Six APKs now load: MangaDex (61 sources), MangaPlus (9), NHentaiXXX (4), EZManga (1), ReadComicOnline (1), BiliManga (1). MangaDex filters and preferences enumerate. The previous MangaPlus protobuf and ReadComicOnline Enum JVM verification failures are fixed; intentionally generic HTML fixture responses then produce expected protobuf/JSON parse errors. This verifies request execution, not real provider responses. MangaFire conversion still identified missing ViewGroup.LayoutParams; the value ABI has since been added without executing its CAPTCHA branch.

The retained opt-in regression now requires at least six APKs loaded and a real BiliManga preference-induced request-path change. Normal hermetic runs always execute the seven binary-manifest fixtures, cache invalidation and DEX constructor tests; APK runtime matrix requires `MIHON_W_REAL_APK_DIR` pointing at the downloaded inventory.

## Integration and maintenance

No Runtime, UI, process manager or IPC contract edits are required for T3. Existing installer calls use the improved converter automatically. Bump `CONVERTER_VERSION` for conversion/normalization changes and `COMPAT_VERSION` for host compatibility changes requiring regenerated frames. APK-to-MEXT caching also keys repository metadata, preserving changed domain declarations. Failed conversions retain the previous target file but report failure rather than silently load it. Original APKs are not embedded into MEXTs; existing installations without an original APK require re-import for reconversion.

New host dependency: `org.mozilla:rhino:1.8.1` ([upstream release and license](https://github.com/mozilla/rhino/releases)). Distributions must retain its MPL notice along with normal bundled dependency notices.

## Final sample matrix

| Real APK category | Sample | Actual verified behavior | Boundary |
|---|---|---|---|
| SourceFactory / filters | MangaDex 1.6.0 | 61 sources; actual filter and preference models | Live API content unverified; AppInfo header ABI added after final combo, follow-up below |
| WebView-dependent source | MangaFire 1.6.34 | 7 sources; conversion and host loading including WebView-related types | CAPTCHA branch deliberately not executed; T4 local-page bridge test is separate |
| Image decryption / protobuf | MangaPlus 1.6.66 | 9 sources; preference defaults; ranking request with origin/referer/session header; decoder runs | Fixture HTML is intentionally not valid protobuf; image decoding/decryption not claimed |
| Custom headers | NHentaiXXX 1.6.11 | 4 sources; offline popular parsing; actual Origin and Referer captured | Live content unverified |
| Ordinary HttpSource | EZManga 1.4.62 | 1 source; real JSON API request with Accept/Referer captured | Fixture HTML is intentionally not JSON |
| JavaScript / preferences | ReadComicOnline 1.4.44 | 1 source; mirror/quality/server preferences; JSON request reaches broker; QuickJs evaluate ABI separately tested | Full remote config/image script pipeline unverified |
| Cookie / request-affecting preference | BiliManga 1.6.14 | 1 source; filter/pref DTOs; two successful offline popular calls; week→month request path change; Cookie night=1 and language header | Live content unverified |

Raw reproducible runtime outcomes: `.superpowers/sdd/t3-samples/load-results.txt`; inventory/provenance: `inventory.json`; retained MEXTs: `converted/`. These local artifacts are ignored rather than committed. Sanitized summary above avoids publishing temporary generated session tokens.

Final test groups: TachiyomiExtensionCompatibilityTest (11), RealBinaryManifestTest (1, seven actual binary manifests), DexConstructorCompatibilityTest (2), RealExtensionSampleTest (1, seven APKs), QuickJsCompatibilityTest (2), ContextWrapperCompatibilityTest (1). No tests skipped in the opt-in run. The matrix now asserts every supplied inventory APK loads, plus the actual BiliManga preference-induced path change.

Final changed-file inventory (relative to repository):

- `desktop-app/src/main/kotlin/mihon/desktop/extension/compat/{AxmlManifestParser,TachiyomiExtensionConverter,DexConstructorNormalizer}.kt`
- `desktop-app/src/test/kotlin/mihon/desktop/extension/{RealExtensionSampleTest,RealBinaryManifestTest,DexConstructorCompatibilityTest,TachiyomiExtensionCompatibilityTest}.kt`
- `desktop-app/src/test/resources/real-manifests/` (seven original binary manifests plus provenance README)
- `extension-host/build.gradle.kts` (Rhino dependency)
- `extension-host/src/main/kotlin/app/cash/quickjs/QuickJs.kt`
- `extension-host/src/main/kotlin/android/app/{Application,Activity}.kt`
- `extension-host/src/main/kotlin/android/content/{Context,ContextWrapper,Intent}.kt`, `android/content/res/Resources.kt`
- `extension-host/src/main/kotlin/android/os/{Build,Bundle}.kt`, `android/util/DisplayMetrics.kt`, `android/view/View.kt` (LayoutParams addition), `android/widget/EditText.kt`, `android/webkit/ConsoleMessage.kt`
- `extension-host/src/main/kotlin/androidx/preference/Preferences.kt` (dialog metadata, toggle summaries, EditText listener ABI)
- `extension-host/src/main/kotlin/eu/kanade/tachiyomi/AppInfo.kt` (object instance ABI required by MangaDex; optional mihon.version/mihon.versionCode properties)
- `extension-host/src/test/kotlin/app/cash/quickjs/QuickJsCompatibilityTest.kt`, `extension-host/src/test/kotlin/android/content/ContextWrapperCompatibilityTest.kt`

Handler/Looper/AndroidSchedulers and WebView implementation are T4-owned work and are not counted as this subtask's new implementation. Root fixed failure-path classloader closure and preference default DTO handling based on the real sample observations.

## Final AppInfo follow-up

`2022`: shared wrapper `:desktop-app:test --tests *RealExtensionSampleTest`, BUILD SUCCESSFUL in 29s after correcting AppInfo to the actual Kotlin object INSTANCE/virtual-method ABI. All seven inventory APKs must load and BiliManga preference path change must pass. The exact final MangaDex request outcome is recorded below.

- eu.kanade.tachiyomi.extension.all.mangadex | OFFLINE_POPULAR | false | Unexpected JSON token at offset 0: Expected start of the object '{', but had '<' instead at path: $
