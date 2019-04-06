package exh

import eu.kanade.tachiyomi.source.model.FilterList

data class EXHSavedSearch(val name: String,
                          val query: String,
                          val filterList: FilterList)