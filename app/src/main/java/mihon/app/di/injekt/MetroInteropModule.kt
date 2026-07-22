package mihon.app.di.injekt

import android.app.Application
import android.content.Context
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.core.security.PrivacyPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.domain.extension.interactor.GetExtensionStoreCountAsFlow
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.repository.ExtensionStoreRepository
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.ResetCategoryFlags
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.ResetViewerFlags
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
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
    private val uiPreferences: UiPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val storagePreferences: StoragePreferences,
    private val backupPreferences: BackupPreferences,
    private val readerPreferences: ReaderPreferences,
    private val securityPreferences: SecurityPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val networkPreferences: NetworkPreferences,

    private val sourceManager: SourceManager,
    private val extensionManager: ExtensionManager,
    private val storageManager: StorageManager,
    private val trackerManager: TrackerManager,

    private val downloadCache: DownloadCache,
    private val chapterCache: ChapterCache,
    private val coverCache: CoverCache,

    private val extensionStoreRepository: ExtensionStoreRepository,

    private val addTracksProvider: () -> AddTracks,
    private val insertTracksProvider: () -> InsertTrack,
    private val updateExtensionStoreProvider: () -> UpdateExtensionStores,
    private val resetViewerFlagsProvider: () -> ResetViewerFlags,
    private val getExtensionStoreCountAsFlowProvider: () -> GetExtensionStoreCountAsFlow,
    private val getFavoritesProvider: () -> GetFavorites,
    private val getCategoriesProvider: () -> GetCategories,
    private val resetCategoryFlagsProvider: () -> ResetCategoryFlags,
    private val getMangaProvider: () -> GetManga,
    private val refreshTracksProvider: () -> RefreshTracks,
) : InjektModule {

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
        addSingleton(uiPreferences)
        addSingleton(libraryPreferences)
        addSingleton(storagePreferences)
        addSingleton(backupPreferences)
        addSingleton(readerPreferences)
        addSingleton(securityPreferences)
        addSingleton(downloadPreferences)
        addSingleton(networkPreferences)

        addSingleton(sourceManager)
        addSingleton(extensionManager)
        addSingleton(storageManager)
        addSingleton(trackerManager)

        addSingleton(downloadCache)
        addSingleton(chapterCache)
        addSingleton(coverCache)

        addSingleton(extensionStoreRepository)

        addFactory<AddTracks>(addTracksProvider)
        addFactory<InsertTrack>(insertTracksProvider)
        addFactory<UpdateExtensionStores>(updateExtensionStoreProvider)
        addFactory<ResetViewerFlags>(resetViewerFlagsProvider)
        addFactory<GetExtensionStoreCountAsFlow>(getExtensionStoreCountAsFlowProvider)
        addFactory<GetFavorites>(getFavoritesProvider)
        addFactory<GetCategories>(getCategoriesProvider)
        addFactory<ResetCategoryFlags>(resetCategoryFlagsProvider)
        addFactory<GetManga>(getMangaProvider)
        addFactory<RefreshTracks>(refreshTracksProvider)
    }
}
