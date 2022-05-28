package eu.kanade.domain.chapter.repository

import eu.kanade.domain.chapter.model.ChapterUpdate

interface ChapterRepository {

    suspend fun update(chapterUpdate: ChapterUpdate)
}
