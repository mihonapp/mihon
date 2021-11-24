package eu.kanade.tachiyomi.ui.setting.database

import android.os.Bundle
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ClearDatabasePresenter : BasePresenter<ClearDatabaseController>() {

    private val db = Injekt.get<DatabaseHelper>()

    private val sourceManager = Injekt.get<SourceManager>()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        getDatabaseSourcesObservable()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeLatestCache(ClearDatabaseController::setItems)
    }

    fun clearDatabaseForSourceIds(sources: List<Long>) {
        db.deleteMangasNotInLibraryBySourceIds(sources).executeAsBlocking()
        db.deleteHistoryNoLastRead().executeAsBlocking()
    }

    private fun getDatabaseSourcesObservable(): Observable<List<ClearDatabaseSourceItem>> {
        return db.getSourceIdsWithNonLibraryManga().asRxObservable()
            .map { sourceCounts ->
                sourceCounts.map {
                    val sourceObj = sourceManager.getOrStub(it.source)
                    ClearDatabaseSourceItem(sourceObj, it.count)
                }.sortedBy { it.source.name }
            }
    }
}
