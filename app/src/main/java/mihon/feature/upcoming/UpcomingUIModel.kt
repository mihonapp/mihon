package mihon.feature.upcoming

import kotlinx.datetime.LocalDate
import tachiyomi.domain.manga.model.Manga

sealed interface UpcomingUIModel {
    data class Header(val date: LocalDate, val mangaCount: Int) : UpcomingUIModel
    data class Item(val manga: Manga) : UpcomingUIModel
}
