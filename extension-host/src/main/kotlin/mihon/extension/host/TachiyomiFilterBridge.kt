package mihon.extension.host

import mihon.extension.compat.TachiyomiCatalogueSourceAdapter
import mihon.extension.source.model.MangasPage
import mihon.extension.source.model.SManga
import eu.kanade.tachiyomi.source.model.Filter as TFilter
import eu.kanade.tachiyomi.source.model.FilterList as TFilterList
import eu.kanade.tachiyomi.source.model.SManga as TSManga
import mihon.extension.source.model.Filter as SFilter
import mihon.extension.source.model.FilterList as SFilterList

/**
 * Filter bridge for Android/Tachiyomi extensions.
 *
 * The compat adapter maps most catalogue operations, but its filter mapping intentionally stays
 * small. Android extensions commonly group checkbox/tri-state filters (for example MangaDex
 * content ratings), so the host maps those recursively here before serializing them over IPC.
 */
internal fun TachiyomiCatalogueSourceAdapter.getIpcFilterList(): SFilterList {
    return SFilterList(delegate.getFilterList().mapNotNull { it.toSdkFilter() })
}

internal suspend fun TachiyomiCatalogueSourceAdapter.searchMangaWithFilters(
    page: Int,
    query: String,
    filters: SFilterList,
): MangasPage {
    val tachiFilters = delegate.getFilterList().applySdkState(filters)
    val result = delegate.getSearchManga(page, query, tachiFilters)
    return MangasPage(
        mangas = result.mangas.map { it.toSdkManga() },
        hasNextPage = result.hasNextPage,
    )
}

private fun TFilter<*>.toSdkFilter(): SFilter<*>? = when (this) {
    is TFilter.Header -> SFilter.Header(name)
    is TFilter.Separator -> SFilter.Separator(name)
    is TFilter.Text -> SFilter.Text(name, state)
    is TFilter.CheckBox -> SFilter.CheckBox(name, state)
    is TFilter.Select<*> -> SFilter.Select(name, values.map { it?.toString() ?: "" }.toTypedArray(), state)
    is TFilter.TriState -> SFilter.TriState(name, state)
    is TFilter.Group<*> -> SFilter.Group(
        name = name,
        state = state.mapNotNull { child -> (child as? TFilter<*>)?.toSdkFilter() },
    )
    is TFilter.Sort -> SFilter.Sort(
        name = name,
        values = values,
        state = state?.let { SFilter.Sort.Selection(it.index, it.ascending) },
    )
}

private fun TFilterList.applySdkState(filters: SFilterList): TFilterList {
    val incomingByName = filters.filters.associateBy { it.name }
    for (target in this) {
        val incoming = incomingByName[target.name] ?: continue
        target.applySdkState(incoming)
    }
    return this
}

private fun TFilter<*>.applySdkState(incoming: SFilter<*>) {
    when {
        this is TFilter.Text && incoming is SFilter.Text -> state = incoming.state
        this is TFilter.CheckBox && incoming is SFilter.CheckBox -> state = incoming.state
        this is TFilter.Select<*> && incoming is SFilter.Select<*> -> state = incoming.state
        this is TFilter.TriState && incoming is SFilter.TriState -> state = incoming.state
        this is TFilter.Sort && incoming is SFilter.Sort -> {
            val selection = incoming.state
            state = selection?.let { TFilter.Sort.Selection(it.index, it.ascending) }
        }
        this is TFilter.Group<*> && incoming is SFilter.Group<*> -> {
            val incomingChildren = incoming.state.filterIsInstance<SFilter<*>>().associateBy { it.name }
            for (child in state) {
                if (child is TFilter<*>) {
                    incomingChildren[child.name]?.let { child.applySdkState(it) }
                }
            }
        }
    }
}

private fun TSManga.toSdkManga(): SManga = SManga(
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genre = getGenres() ?: emptyList(),
    status = status,
    thumbnailUrl = thumbnail_url,
    initialized = initialized,
)
