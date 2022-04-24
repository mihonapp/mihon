package eu.kanade.domain

import eu.kanade.data.history.HistoryRepositoryImpl
import eu.kanade.data.source.SourceRepositoryImpl
import eu.kanade.domain.history.interactor.DeleteHistoryTable
import eu.kanade.domain.history.interactor.GetHistory
import eu.kanade.domain.history.interactor.GetNextChapterForManga
import eu.kanade.domain.history.interactor.RemoveHistoryById
import eu.kanade.domain.history.interactor.RemoveHistoryByMangaId
import eu.kanade.domain.history.repository.HistoryRepository
import eu.kanade.domain.source.interactor.DisableSource
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.domain.source.repository.SourceRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class DomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addFactory { GetNextChapterForManga(get()) }

        addSingletonFactory<HistoryRepository> { HistoryRepositoryImpl(get()) }
        addFactory { DeleteHistoryTable(get()) }
        addFactory { GetHistory(get()) }
        addFactory { RemoveHistoryById(get()) }
        addFactory { RemoveHistoryByMangaId(get()) }

        addSingletonFactory<SourceRepository> { SourceRepositoryImpl(get()) }
        addFactory { GetEnabledSources(get(), get()) }
        addFactory { DisableSource(get()) }
        addFactory { ToggleSourcePin(get()) }
    }
}
