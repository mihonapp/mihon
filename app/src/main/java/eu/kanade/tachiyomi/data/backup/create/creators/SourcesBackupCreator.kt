package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupSource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SourcesBackupCreator(
    private val sourceManager: SourceManager = Injekt.get(),
) {

    fun backupSources(mangas: List<Manga>): List<BackupSource> {
        return mangas
            .asSequence()
            .map(Manga::source)
            .distinct()
            .map(sourceManager::getOrStub)
            .map(BackupSource::copyFrom)
            .toList()
    }
}
