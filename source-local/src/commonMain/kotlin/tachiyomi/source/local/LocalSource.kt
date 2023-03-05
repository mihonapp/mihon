package tachiyomi.source.local

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.UnmeteredSource

expect class LocalSource : CatalogueSource, UnmeteredSource
