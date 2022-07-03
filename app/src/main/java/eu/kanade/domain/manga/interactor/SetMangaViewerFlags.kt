package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.MangaUpdate
import eu.kanade.domain.manga.repository.MangaRepository
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType

class SetMangaViewerFlags(
    private val mangaRepository: MangaRepository,
) {

    suspend fun awaitSetMangaReadingMode(id: Long, flag: Long) {
        mangaRepository.update(
            MangaUpdate(
                id = id,
                viewerFlags = flag.setFlag(flag, ReadingModeType.MASK.toLong()),
            ),
        )
    }

    suspend fun awaitSetOrientationType(id: Long, flag: Long) {
        mangaRepository.update(
            MangaUpdate(
                id = id,
                viewerFlags = flag.setFlag(flag, OrientationType.MASK.toLong()),
            ),
        )
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }
}
