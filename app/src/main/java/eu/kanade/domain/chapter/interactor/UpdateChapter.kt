package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.chapter.model.ChapterUpdate
import eu.kanade.domain.chapter.repository.ChapterRepository

class UpdateChapter(
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(chapterUpdate: ChapterUpdate) {
        chapterRepository.update(chapterUpdate)
    }
}
