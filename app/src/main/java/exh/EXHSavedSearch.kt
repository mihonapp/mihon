package exh

import eu.kanade.tachiyomi.source.model.Filter

data class EXHSavedSearch(val name: String,
                          val query: String,
                          val filterList: List<Filter<*>>)