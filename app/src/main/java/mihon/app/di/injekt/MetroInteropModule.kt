package mihon.app.di.injekt

import android.app.Application
import android.content.Context
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.repository.ExtensionStoreRepository
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.InsertTrack
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingleton

@AssistedInject
class MetroInteropModule(
    @Assisted private val application: Application,
    private val json: Json,
    private val protoBuf: ProtoBuf,
    private val xml: XML,
    private val networkHelper: NetworkHelper,
    private val javaScriptEngine: JavaScriptEngine,
    private val preferenceStore: PreferenceStore,
    private val basePreferences: BasePreferences,
    private val privacyPreferences: PrivacyPreferences,
    private val trackPreferences: TrackPreferences,
    private val sourcePreferences: SourcePreferences,
    private val uiPreferences: UiPreferences,
    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    private val extensionStoreRepository: ExtensionStoreRepository,
    private val downloadCache: DownloadCache,
    private val coverCache: CoverCache,
    private val addTracksProvider: () -> AddTracks,
    private val insertTracksProvider: () -> InsertTrack,
    private val updateExtensionStoreProvider: () -> UpdateExtensionStores,
    private val trustExtensionProvider: () -> TrustExtension,
): InjektModule {

    @AssistedFactory
    interface Factory {
        fun create(application: Application): MetroInteropModule
    }

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(application)
        addSingleton<Context>(application)
        addSingleton(json)
        addSingleton(protoBuf)
        addSingleton(xml)
        addSingleton(networkHelper)
        addSingleton(javaScriptEngine)
        addSingleton(preferenceStore)
        addSingleton(basePreferences)
        addSingleton(privacyPreferences)
        addSingleton(trackPreferences)
        addSingleton(sourcePreferences)
        addSingleton(uiPreferences)
        addSingleton(sourceManager)
        addSingleton(extensionManager)
        addSingleton(extensionStoreRepository)
        addSingleton(downloadCache)
        addSingleton(coverCache)
        addFactory(addTracksProvider)
        addFactory(insertTracksProvider)
        addFactory(updateExtensionStoreProvider)
        addFactory(trustExtensionProvider)
    }
}
