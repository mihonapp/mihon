package mihon.domain.extension.model

import eu.kanade.tachiyomi.extension.model.Extension

/**
 * What the stores listed in one fetch.
 *
 * @property extensions every store's listing, including several listings of the same extension.
 * @property failedSigningKeys signing keys of the stores whose listing failed or came back empty. An
 * extension signed with one of them may be listed even when it's missing from [extensions].
 */
data class ExtensionStoreListings(
    val extensions: List<Extension.Available>,
    val failedSigningKeys: Set<String>,
)
