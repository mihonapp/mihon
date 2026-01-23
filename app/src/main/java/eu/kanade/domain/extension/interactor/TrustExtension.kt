package eu.kanade.domain.extension.interactor

import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import logcat.LogPriority
import logcat.logcat
import mihon.domain.extensionrepo.repository.ExtensionRepoRepository
import tachiyomi.core.common.preference.getAndSet

class TrustExtension(
    private val extensionRepoRepository: ExtensionRepoRepository,
    private val preferences: SourcePreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Cached trusted fingerprints using StateFlow - automatically updates when repos change
    private val trustedFingerprints: StateFlow<Set<String>> = extensionRepoRepository.subscribeAll()
        .map { repos -> 
            val fingerprints = repos.map { it.signingKeyFingerprint }.toHashSet()
            logcat(LogPriority.DEBUG) { "TrustExtension: Cached ${fingerprints.size} trusted fingerprints" }
            fingerprints
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,  // Start immediately to cache fingerprints at startup
            initialValue = emptySet()
        )

    /**
     * Preload trusted fingerprints into cache. Call this ONCE before loading many extensions.
     * This ensures the StateFlow has received its first emission from the database.
     */
    suspend fun preloadTrustedFingerprints() {
        // Wait for the first non-empty emission if possible, or just trigger a subscription
        if (trustedFingerprints.value.isEmpty()) {
            logcat(LogPriority.DEBUG) { "TrustExtension: Preloading - waiting for first emission" }
            // Force a fresh load if StateFlow is empty
            extensionRepoRepository.subscribeAll().first()
        }
        logcat(LogPriority.DEBUG) { "TrustExtension: Preload complete, ${trustedFingerprints.value.size} fingerprints cached" }
    }

    suspend fun isTrusted(pkgInfo: PackageInfo, fingerprints: List<String>): Boolean {
        val cached = trustedFingerprints.value
        val key = "${pkgInfo.packageName}:${PackageInfoCompat.getLongVersionCode(pkgInfo)}:${fingerprints.last()}"
        return cached.any { fingerprints.contains(it) } || key in preferences.trustedExtensions().get()
    }

    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
        preferences.trustedExtensions().getAndSet { exts ->
            // Remove previously trusted versions
            val removed = exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()

            removed.also { it += "$pkgName:$versionCode:$signatureHash" }
        }
    }

    fun revokeAll() {
        preferences.trustedExtensions().delete()
    }
}
