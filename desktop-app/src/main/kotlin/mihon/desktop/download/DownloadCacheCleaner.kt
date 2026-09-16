package mihon.desktop.download

import mihon.desktop.library.model.LibraryChapter
import mihon.desktop.library.model.MangaRecord
import mihon.desktop.library.repository.LibraryRepository
import java.nio.file.Files
import java.nio.file.Path

data class CleanReport(
    val deletedChaptersCount: Int,
    val freedBytes: Long,
)

class DownloadCacheCleaner(
    private val repository: LibraryRepository,
    private val diskProvider: DownloadDiskProvider,
    private val coordinatedDelete: ((MangaRecord, LibraryChapter) -> Boolean)? = null,
) {
    fun calculateDownloadSize(): Long = diskProvider.downloadRoots.sumOf(Companion::calculateDownloadSize)

    fun deleteReadChapters(): CleanReport =
        Companion.deleteReadChapters(repository, diskProvider, coordinatedDelete)

    fun clearImageDiskCache(cacheDir: Path): Long = Companion.clearImageDiskCache(cacheDir)

    companion object {

        fun calculateDownloadSize(downloadsDir: Path): Long {
            if (!Files.exists(downloadsDir)) return 0L
            return try {
                Files.walk(downloadsDir).use { stream ->
                    stream.filter { Files.isRegularFile(it) }
                        .mapToLong {
                            try {
                                Files.size(it)
                            } catch (_: Exception) {
                                0L
                            }
                        }
                        .sum()
                }
            } catch (_: Exception) {
                0L
            }
        }

        fun getDirectorySize(dir: Path): Long {
            if (!Files.exists(dir)) return 0L
            return try {
                Files.walk(dir).use { stream ->
                    stream.filter { Files.isRegularFile(it) }
                        .mapToLong {
                            try {
                                Files.size(it)
                            } catch (_: Exception) {
                                0L
                            }
                        }
                        .sum()
                }
            } catch (_: Exception) {
                0L
            }
        }

        fun deleteReadChapters(
            repository: LibraryRepository,
            diskProvider: DownloadDiskProvider,
            coordinatedDelete: ((MangaRecord, LibraryChapter) -> Boolean)? = null,
        ): CleanReport {
            val allManga = repository.allMangaSnapshot()
            var deletedCount = 0
            var totalFreed = 0L

            for (manga in allManga) {
                val chapters = repository.chapterSnapshot(manga.id)
                for (ch in chapters) {
                    if (ch.read) {
                        val chapterDir = diskProvider.findChapterDir(manga.sourceId, manga.title, ch.name)
                        if (chapterDir != null && Files.exists(chapterDir)) {
                            val size = getDirectorySize(chapterDir)
                            val success = coordinatedDelete?.invoke(manga, ch)
                                ?: diskProvider.deleteChapter(manga.sourceId, manga.title, ch.name)
                            if (success) {
                                deletedCount++
                                totalFreed += size
                            }
                        }
                    }
                }
            }

            return CleanReport(
                deletedChaptersCount = deletedCount,
                freedBytes = totalFreed,
            )
        }

        fun clearImageDiskCache(cacheDir: Path): Long {
            if (!Files.exists(cacheDir)) return 0L
            var freed = 0L
            try {
                Files.walk(cacheDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach { path ->
                        if (path != cacheDir) {
                            try {
                                if (Files.isRegularFile(path)) {
                                    freed += Files.size(path)
                                }
                                Files.deleteIfExists(path)
                            } catch (_: Exception) {
                            }
                        }
                    }
            } catch (_: Exception) {
            }
            return freed
        }
    }
}
