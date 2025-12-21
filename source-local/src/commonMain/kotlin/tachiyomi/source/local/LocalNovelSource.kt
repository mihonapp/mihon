package tachiyomi.source.local

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Page

expect class LocalNovelSource : CatalogueSource, NovelSource, UnmeteredSource
