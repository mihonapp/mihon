package eu.kanade.domain

import eu.kanade.data.history.HistoryRepositoryImpl
import eu.kanade.domain.history.interactor.DeleteHistoryTable
import eu.kanade.domain.history.interactor.GetHistory
import eu.kanade.domain.history.interactor.GetNextChapterForManga
import eu.kanade.domain.history.interactor.RemoveHistoryById
import eu.kanade.domain.history.interactor.RemoveHistoryByMangaId
import eu.kanade.domain.history.repository.HistoryRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class DomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<HistoryRepository> { HistoryRepositoryImpl(get()) }
        addFactory { DeleteHistoryTable(get()) }
        addFactory { GetHistory(get()) }
        addFactory { GetNextChapterForManga(get()) }
        addFactory { RemoveHistoryById(get()) }
        addFactory { RemoveHistoryByMangaId(get()) }
    }
}
