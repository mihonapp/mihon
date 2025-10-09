package eu.kanade.tachiyomi.data.export

import android.content.Context
import android.net.Uri
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
            val contentResolver = context.contentResolver
            val getFavorites = Injekt.get<GetFavorites>()
            val coverCache = Injekt.get<CoverCache>()

            try {
                val pfd = contentResolver.openFileDescriptor(uri, "r") ?: return@withContext onRestoreFailure()
                ArchiveReader(pfd).use { archiveReader ->
                    var protoData: ByteArray? = null

                    archiveReader.useEntries { entries ->
                        entries.forEach { entry ->
                            if (entry.name == "manga_urls.proto") {
                                archiveReader.getInputStream(entry.name)?.use { stream ->
                                    protoData = stream.readBytes()
                                }
                            }
                        }
                    }

                    if (protoData == null) {
                        onRestoreFailure()
                        return@withContext
                    }

                    val backupCovers = ProtoBuf.decodeFromByteArray<BackupCovers>(protoData)
                    val mangas = getFavorites.await()
                    val expectedFiles = backupCovers.covers.associateBy { it.filename }

                    ArchiveReader(contentResolver.openFileDescriptor(uri, "r")!!).use { imageReader ->
                        imageReader.useEntries { entries ->
                            entries.forEach { entry ->
                                val backupCover = expectedFiles[entry.name] ?: return@forEach
                                val matchingManga = mangas.find {
                                    it.url == backupCover.mangaUrl && it.source == backupCover.sourceId
                                } ?: return@forEach

                                imageReader.getInputStream(entry.name)?.use { imageStream ->
                                    coverCache.setCustomCoverToCache(matchingManga, imageStream)
                                }
                            }
                        }
                    }

                    onRestoreComplete()
                }
            } catch (e: Exception) {
                Log.e("CustomCoverRestorer", "Restore failed", e)
                onRestoreFailure()
            }
        }
    }
}
