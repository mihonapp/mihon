# Multi-source library titles

Search in Browse, then add a result to the library. Adding from the result's detail screen or the source search grid starts a search for that title on other installed HTTP sources. Only enabled languages and enabled sources are searched. The original result supplies the library entry, cover, metadata, categories, and tracking.

The **Linked sources** action opens the normal global search results layout, prefilled with the library title. It searches all enabled installed HTTP sources except the original source. Tap a cover in a source section to select that version; a check badge marks the pending selection. Tap it again to unlink it, or tap another title in that source to replace it. Each source contributes at most one selected title. The source header opens its full, paginated results with the same selection behavior. Long press opens a candidate's details for inspection. **Apply** saves all selected sources and scanlator choices, refreshes chapters, and returns to the title. **Clear** deselects source links in the current editing session; Apply commits that cleared selection. Back without Apply discards pending changes. The original source and library entry remain intact. The helper text has an opaque surface background as part of the fixed search header. The title's overflow menu reopens this search to add or change sources.

After tapping a source result, Mihon inspects that title's chapter scanlators. If multiple groups are present, a **Select scanlators** dialog requires one or more selections before adding the link to the pending selections. Apply commits those selections and refreshes chapters. Each scanlator row shows its chapter count and the minimum/maximum recognized chapter numbers, when available; the span does not imply gap-free coverage. Unnamed releases appear as **Unknown scanlator**. Canceling leaves any previous link untouched; a failed inspection offers Retry. A title with only one scanlator links directly. To change a linked source's scanlator choices, unlink and select it again.

Scanlator choices are saved per source link and applied to both fresh chapter lists and retained chapter origins when a source request fails. Newly appearing unselected groups are excluded. Existing links without choices retain their previous behavior, and completed downloads or protected reading progress remain intact.

Confirmed sources contribute to one chapter list. Recognized chapter numbers, including decimals, identify equivalent chapters. Unknown numbers, specials, extras, and explicit volume/season labels remain source-specific. Matching does not infer numbering offsets or prove that similarly numbered editions have identical content; those editions should not be linked without checking their numbering.

Chapter refresh collects available versions without assigning download providers. Unqueued chapters show no source label. Adding a chapter to the download queue commits its provider and spreads that requested batch according to existing queue load. Queued and downloaded chapters retain their assignment across refreshes. Streaming may use a representative origin for its requests without creating a download assignment. Existing source and page concurrency limits still apply. Downloading remains controlled by manual actions and the existing automatic download preferences; adding a title does not independently enable automatic downloads.

Each logical chapter keeps its local ID, progress, bookmark, and storage name. Source routing resolves the actual remote chapter URL and extension metadata for network requests. Download directories remain under the actual source and the shared library title.

## Automatic source recovery

Manual downloads and download ahead use the same queue and recovery policy. A failed chapter tries the remaining installed providers confirmed for that chapter, at most once per provider in a download attempt. The complete chapter origin list is retained; attempted providers are queue state persisted across restarts. Resuming an errored download resets its attempts so a repaired source can be tried again.

Connection failures and HTTP 403, 408, 429, and server errors put a source on a 60-second cooldown. New batches favor other installed providers even if the failing source has a shorter queue. Already queued chapters check source availability before making a request and reroute when a healthy alternative exists. A successful download clears the cooldown early. Chapters with no alternate installed provider still get a bounded attempt instead of being silently discarded.

Restoring the queue automatically selects an installed alternative if the selected extension is missing. If no provider is installed, its persisted queue record is kept and restoration is retried when installed sources change. Partially read chapters retain their current version, because page positions differ between editions. Detected directory and free-space failures do not trigger source fallback. When all providers fail, the chapter remains errored for a later retry; recovery never links unconfirmed titles or retries indefinitely.

Refreshes query confirmed sources with bounded concurrency and timeouts. A failed source retains its known chapters; an entirely failed refresh leaves the existing database untouched. Manual refreshes show partial failures, and library updates include them in their error report. Read, bookmarked, partially read, queued, and downloaded chapters are protected when lists change.

## Persistence

- Manga memo keys `mihon.linkedSources` and `mihon.storageSources` store active links (including optional per-source scanlator selections) and source directories used previously. Remote metadata updates preserve these keys atomically; they are excluded from extension requests.
- Chapter memo key `mihon.chapterOrigins` stores available alternatives with each provider's original chapter metadata. `mihon.assignedChapterSource` records a provider only once the chapter is queued; its origin is then first for routing. Older queued/downloaded chapters keep their existing route when their assignment is adopted. These records are included in existing manga/chapter backups. Backup source lists also include previously used sources.
- Migration 20 rebuilds `updatesView` to expose chapter memo data so Updates download indicators resolve the correct provider while covers continue to use the original source.
- Removing a source link does not delete its completed downloads. Download counts, title renames, and deletion include previously used source directories.

## Validation

Run `:app:compileDebugKotlin`, `:domain:testDebugUnitTest`, and Spotless checks for app, domain, and data. Merger and planner tests cover overlap, missing chapters, decimals, ambiguous chapters, preserved identities, metadata round trips, arbitrary selected batches, queue load, partial progress, unavailable sources, bounded recovery attempts, retained origins, missing extensions, and cooldown expiry.

Device acceptance checks still need real extensions: add through both Browse paths, confirm two overlapping sources, download an arbitrary subset concurrently, read across source boundaries, fail one source, restart a queued download, and round-trip a backup. Also verify an existing single-source title and cancellation of source selection.

## Returning to official Mihon

In **Settings → Data and storage → Create backup**, enable **Compatible with official Mihon**. This export splits each linked title into ordinary library entries, one per confirmed source, and replaces internal chapter identities with that source's real chapter URLs and metadata. Read status and bookmarks transfer to every available equivalent chapter. Partial-page progress and reading history stay with the currently routed edition; page positions are not copied between different editions. Categories, tracking, notes, and reader settings are retained. This does not change the current library.

Normal and automatic backups still retain the complete multi-source setup. Keep one before returning to official Mihon: compatible exports do not retain links or per-link scanlator selections. Official Mihon will treat the exported sources as independent titles; review its scanlator filters after refreshing. Install the relevant extensions before restoring. Downloaded image files are not included, and the fork's download folders may not match official chapter names and URL hashes; redownload those chapters in official Mihon.

If a protected chapter retains an origin whose source has since been unlinked, compatibility export reports an error rather than dropping its progress or guessing the remote title URL. Relink that title to provide its source URL, or keep a normal backup. Missing chapter origins likewise require a source refresh before exporting.

Restore the compatible backup through official Mihon's backup screen. This is a backup migration, not support for opening the fork's newer live database in an older app or bypassing Android signing requirements. Exporting and restoring use the existing protobuf format; ordinary single-source backups and older queued backup-worker options continue to work.
