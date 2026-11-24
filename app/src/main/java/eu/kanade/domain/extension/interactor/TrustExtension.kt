package eu.kanade.domain.extension.interactor

import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import dev.zacsweers.metro.Inject
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.preference.getAndSet

@Inject
class TrustExtension(
    private val repository: ExtensionStoreRepository,
    private val preferences: SourcePreferences,
) {

    /**
     * Trusted when the user trusted this exact apk, or when it's signed with a store's key and isn't obsolete.
     */
    fun isTrusted(pkgInfo: PackageInfo, fingerprints: List<String>, stores: List<ExtensionStore>): Boolean {
        val key = "${pkgInfo.packageName}:${PackageInfoCompat.getLongVersionCode(pkgInfo)}:${fingerprints.last()}"
        if (key in preferences.trustedExtensions.get()) return true
        if (isObsolete(pkgInfo.packageName, fingerprints)) return false

        val trustedFingerprints = stores.mapTo(HashSet()) { it.signingKey }
        return trustedFingerprints.any { fingerprints.contains(it) }
    }

    /**
     * Whether the extension was last seen signed with a store's key but listed by no such store.
     */
    fun isObsolete(pkgName: String, fingerprints: List<String>): Boolean {
        return obsoleteKey(pkgName, fingerprints) in preferences.obsoleteExtensions.get()
    }

    fun trust(pkgName: String, versionCode: Long, signatureHash: String) = synchronized(lock) {
        preferences.trustedExtensions.getAndSet { exts ->
            // Remove previously trusted versions
            val removed = exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()

            removed.also { it += "$pkgName:$versionCode:$signatureHash" }
        }
    }

    fun revokeAll() = synchronized(lock) {
        preferences.trustedExtensions.delete()
    }

    /**
     * Marks the [obsolete] extensions and unmarks the [listed] ones. An extension that becomes obsolete
     * also loses the trust the user gave it, so it has to be trusted again.
     *
     * @return whether anything changed.
     */
    fun updateObsolete(
        obsolete: Collection<Extension.Installed>,
        listed: Collection<Extension.Installed>,
    ): Boolean = synchronized(lock) {
        val current = preferences.obsoleteExtensions.get()
        val updated = current -
            listed.mapTo(HashSet()) { obsoleteKey(it.pkgName, it.signatures) } +
            obsolete.map { obsoleteKey(it.pkgName, it.signatures) }
        if (updated == current) return false

        val newlyObsolete = (updated - current).mapTo(HashSet()) { it.substringBeforeLast(':') }
        if (newlyObsolete.isNotEmpty()) {
            preferences.trustedExtensions.getAndSet { exts ->
                exts.filterNotTo(HashSet()) { it.substringBefore(':') in newlyObsolete }
            }
        }
        preferences.obsoleteExtensions.set(updated)
        return true
    }

    fun forgetObsolete(pkgName: String) = synchronized(lock) {
        preferences.obsoleteExtensions.getAndSet { exts ->
            exts.filterNotTo(HashSet()) { it.substringBeforeLast(':') == pkgName }
        }
    }

    /**
     * Emits whenever what counts as trusted changes, either because a store was added or removed or
     * because an extension was trusted or had its trust revoked. Both sources replay their current
     * value, which is dropped.
     */
    fun changes(): Flow<Unit> {
        return merge(
            // Stores are rewritten whenever their index is refreshed, so only their keys matter here
            repository.getAllAsFlow()
                .map { stores -> stores.mapTo(HashSet()) { it.signingKey } }
                .distinctUntilChanged()
                .drop(1),
            preferences.trustedExtensions.changes()
                .distinctUntilChanged()
                .drop(1),
        )
            .map {}
    }

    private companion object {
        // Every injection gets its own instance, while the preferences are shared
        val lock = Any()

        fun obsoleteKey(pkgName: String, fingerprints: List<String>) = "$pkgName:${fingerprints.last()}"
    }
}
