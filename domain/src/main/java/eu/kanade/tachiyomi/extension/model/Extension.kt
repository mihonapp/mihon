package eu.kanade.tachiyomi.extension.model

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.source.Source
import mihon.domain.extension.model.ContentWarning
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.domain.source.model.StubSource

sealed interface Extension {

    val name: String
    val pkgName: String
    val versionName: String
    val versionCode: Long
    val libVersion: Double?
    val lang: String?
    val contentWarning: ContentWarning

    /**
     * An extension whose apk is on the device, whether or not it ended up being loaded.
     */
    sealed interface Installed : Extension {
        val isShared: Boolean
    }

    /**
     * An extension that isn't on the device yet, as listed by an [ExtensionStore].
     */
    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val contentWarning: ContentWarning,
        val sources: List<Source>,
        val apkUrl: String,
        val iconUrl: String,
        val store: ExtensionStore,
    ) : Extension {

        data class Source(
            val id: Long,
            val lang: String,
            val name: String,
            val baseUrl: String,
        ) {
            fun toStubSource(): StubSource {
                return StubSource(
                    id = this.id,
                    lang = this.lang,
                    name = this.name,
                )
            }
        }
    }

    /**
     * An installed extension whose sources are registered and usable.
     */
    data class Loaded(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val contentWarning: ContentWarning,
        override val isShared: Boolean,
        val pkgFactory: String?,
        val sources: List<Source>,
        val icon: Drawable?,
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val store: ExtensionStore? = null,
    ) : Installed

    /**
     * An installed extension that was never loaded, so it provides no sources. [lang] is derived
     * from the sources and [libVersion] from metadata, so neither is always known here.
     */
    data class NotLoaded(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val isShared: Boolean,
        override val contentWarning: ContentWarning,
        override val libVersion: Double? = null,
        override val lang: String? = null,
        val reason: Reason,
    ) : Installed {

        sealed interface Reason {
            /** Signature isn't trusted yet. Resolvable by the user accepting it. */
            data class Untrusted(val signatureHash: String) : Reason

            /** Its [contentWarning] isn't one the user chose to load. */
            data object Filtered : Reason

            /** No signature to check against at all. */
            data object Unsigned : Reason

            /** Built against an extension lib this app version can't run. */
            data object UnsupportedLibVersion : Reason

            /** Required package metadata is missing. */
            data object Malformed : Reason

            /** Threw while its classes or sources were being instantiated. */
            data class Failed(val message: String, val stackTrace: String) : Reason
        }
    }
}
