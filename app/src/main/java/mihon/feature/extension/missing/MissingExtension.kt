package mihon.feature.extension.missing

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.extension.model.Extension

/**
 * An extension the account uses somewhere but this device does not have.
 *
 * [entryCount] is how many entries in *this* library are stranded without it, which is what decides
 * whether installing it is urgent or merely tidy: zero means another device has the extension but
 * nothing here needs it yet.
 */
@Immutable
data class MissingExtension(
    val extension: Extension.Available,
    val entryCount: Int,
    val sourceNames: List<String>,
) {
    val isNeeded: Boolean get() = entryCount > 0
}
