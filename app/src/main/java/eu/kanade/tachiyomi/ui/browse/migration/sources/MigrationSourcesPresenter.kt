package eu.kanade.tachiyomi.ui.browse.migration.sources

import android.os.Bundle
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.util.lang.combineLatest
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.Collator
import java.util.Collections
import java.util.Locale

class MigrationSourcesPresenter(
    private val sourceManager: SourceManager = Injekt.get(),
    private val db: DatabaseHelper = Injekt.get()
) : BasePresenter<MigrationSourcesController>() {

    private val preferences: PreferencesHelper by injectLazy()

    private val sortRelay = BehaviorRelay.create(Unit)

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        db.getFavoriteMangas()
            .asRxObservable()
            .combineLatest(sortRelay.observeOn(Schedulers.io())) { sources, _ -> sources }
            .observeOn(AndroidSchedulers.mainThread())
            .map { findSourcesWithManga(it) }
            .subscribeLatestCache(MigrationSourcesController::setSources)
    }

    private fun findSourcesWithManga(library: List<Manga>): List<SourceItem> {
        val header = SelectionHeader()
        return library
            .groupBy { it.source }
            .filterKeys { it != LocalSource.ID }
            .map {
                val source = sourceManager.getOrStub(it.key)
                SourceItem(source, it.value.size, header)
            }
            .sortedWith(sortFn())
            .toList()
    }

    fun sortFn(): java.util.Comparator<SourceItem> {
        val sort by lazy {
            preferences.migrationSortingMode().get()
        }
        val direction by lazy {
            preferences.migrationSortingDirection().get()
        }

        val locale = Locale.getDefault()
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }
        val sortFn: (SourceItem, SourceItem) -> Int = { a, b ->
            when (sort) {
                MigrationSourcesController.SortSetting.ALPHABETICAL -> collator.compare(a.source.name.lowercase(locale), b.source.name.lowercase(locale))
                MigrationSourcesController.SortSetting.TOTAL -> a.mangaCount.compareTo(b.mangaCount)
            }
        }

        return when (direction) {
            MigrationSourcesController.DirectionSetting.ASCENDING -> Comparator(sortFn)
            MigrationSourcesController.DirectionSetting.DESCENDING -> Collections.reverseOrder(sortFn)
        }
    }

    fun requestSortUpdate() {
        sortRelay.call(Unit)
    }
}
