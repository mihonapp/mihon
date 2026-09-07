package mihon.desktop.download

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
) {
    fun calculateDownloadSize(): Long = Companion.calculateDownloadSize(diskProvider.downloadsDir)

    fun deleteReadChapters(): CleanReport = Companion.deleteReadChapters(repository, diskProvider)

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
        ): CleanReport {
            val allManga = repository.allMangaSnapshot()
            var deletedCount = 0
            var totalFreed = 0L

            for (manga in allManga) {
                val chapters = repository.chapterSnapshot(manga.id)
                for (ch in chapters) {
                    if (ch.read) {
                        val chapterDir = diskProvider.getChapterDir(manga.sourceId, manga.title, ch.name)
                        if (Files.exists(chapterDir)) {
                            val size = getDirectorySize(chapterDir)
                            val success = diskProvider.deleteChapter(manga.sourceId, manga.title, ch.name)
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
