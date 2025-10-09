package eu.kanade.tachiyomi.data.export

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import eu.kanade.tachiyomi.data.backup.models.BackupCovers
import eu.kanade.tachiyomi.data.cache.CoverCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.core.archive.ArchiveReader
import tachiyomi.domain.manga.interactor.GetFavorites
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object CustomCoverRestorer {

    suspend fun restoreFromZip(
        context: Context,
        uri: Uri,
        onRestoreComplete: () -> Unit,
        onRestoreFailure: () -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext
                ArchiveReader(pfd).use { archiveReader ->
                    var protoData: ByteArray? = null
                    val imageMap = mutableMapOf<String, ByteArray>()

                    archiveReader.useEntries { entries ->
                        entries.forEach { entry ->
                            val entryName = entry.name
                            val entryStream = archiveReader.getInputStream(entryName) ?: return@forEach
                            val data = entryStream.readBytes()

                            if (entryName == "manga_urls.proto") {
                                protoData = data
                                Log.d("CustomCoverRestorer", "Proto found")
                            } else {
                                imageMap[entryName] = data
                                Log.d("CustomCoverRestorer", "Image found: $entryName")
                            }
                        }
                    }

                    if (protoData != null) {
                        val backupCovers = ProtoBuf.decodeFromByteArray<BackupCovers>(protoData)
                        val mangas = Injekt.get<GetFavorites>().await()
                        val coverCache = Injekt.get<CoverCache>()

                        backupCovers().forEach { backupCover ->
                            val matchingManga = mangas.find {
                                it.url == backupCover.mangaUrl && it.source == backupCover.sourceId
                            }

                            if (matchingManga != null) {
                                val imageName = backupCover.filename
                                val coverData = imageMap[imageName]
                                Log.d("CustomCoverRestorer", "Restoring image: ${backupCover.filename}")

                                if (coverData != null) {
                                    val coverInputStream = coverData.inputStream()
                                    coverCache.setCustomCoverToCache(matchingManga, coverInputStream)
                                }
                            }
                        }
                        onRestoreComplete()
                    } else {
                        onRestoreFailure()
                    }
                }
            } catch (e: Exception) {
                onRestoreFailure()
            }
        }
    }
}
