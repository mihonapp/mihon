package eu.kanade.tachiyomi.event

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.source.base.Source

class ReaderEvent(val source: Source, val manga: Manga, val chapter: Chapter)
