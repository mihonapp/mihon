package eu.kanade.domain.extension.interactor

import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.preference.getAndSet

class TrustExtension(
    private val preferences: SourcePreferences,
) {

    fun isTrusted(pkgInfo: PackageInfo, signatureHash: String): Boolean {
        val key = "${pkgInfo.packageName}:${PackageInfoCompat.getLongVersionCode(pkgInfo)}:$signatureHash"
        return key in preferences.trustedExtensions().get()
    }

    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
        preferences.trustedExtensions().getAndSet { exts ->
            // Remove previously trusted versions
            val removed = exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()

            removed.also {
                it += "$pkgName:$versionCode:$signatureHash"
            }
        }
    }

    fun revokeAll() {
        preferences.trustedExtensions().delete()
    }
}
