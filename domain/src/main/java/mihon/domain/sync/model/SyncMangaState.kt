package mihon.domain.sync.model

/**
 * What the sync tracks about an entry on this device.
 *
 * @property favoriteChangedAt when the entry last joined or left the library, in seconds.
 * @property changeCount moves whenever something the sync publishes for the entry changes.
 */
data class SyncMangaState(
    val favoriteChangedAt: Long,
    val changeCount: Long,
)
