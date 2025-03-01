package eu.kanade.tachiyomi.data.export

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.manga.interactor.GetFavorites
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File
import android.util.Log
import eu.kanade.tachiyomi.data.backup.models.BackupCover
import eu.kanade.tachiyomi.data.backup.models.BackupCovers

object CustomCoverExporter {

    suspend fun exportToZip(
        context: Context,
        uri: Uri,
        onExportComplete: () -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val customCovers = Injekt.get<CoverCache>().getAllCustomCovers()
                val mangas = Injekt.get<GetFavorites>().await()

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOutputStream ->
                        val mangaUrlMappings = mangas.mapNotNull { manga ->
                            val expectedFileName = DiskUtil.hashKeyForDisk(manga.id.toString())
                            val file = customCovers.find { it.name == expectedFileName }

                            if (file?.exists() == true) {
                                BackupCover(manga.id, manga.url)
                            } else {
                                null
                            }
                        }

                        val protoByteArray = ProtoBuf.encodeToByteArray(BackupCovers(mangaUrlMappings))
                        val protoFile = File(context.cacheDir, "manga_urls.proto")
                        protoFile.writeBytes(protoByteArray)

                        val protoZipEntry = ZipEntry("manga_urls.proto")
                        zipOutputStream.putNextEntry(protoZipEntry)
                        protoFile.inputStream().use { protoInput ->
                            protoInput.copyTo(zipOutputStream)
                        }
                        zipOutputStream.closeEntry()

                        mangas.forEach { manga ->
                            val expectedFileName = DiskUtil.hashKeyForDisk(manga.id.toString())
                            val file = customCovers.find { it.name == expectedFileName }

                            if (file?.exists() == true) {
                                FileInputStream(file).use { input ->
                                    val zipEntry = ZipEntry("${manga.id}.jpg")
                                    zipOutputStream.putNextEntry(zipEntry)
                                    input.copyTo(zipOutputStream)
                                    zipOutputStream.closeEntry()
                                }
                            }
                        }

                        protoFile.delete()
                    }
                }

                onExportComplete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
