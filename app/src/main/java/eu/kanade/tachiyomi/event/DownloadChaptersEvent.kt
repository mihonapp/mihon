package eu.kanade.tachiyomi.event

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga

class DownloadChaptersEvent(val manga: Manga, val chapters: List<Chapter>)
