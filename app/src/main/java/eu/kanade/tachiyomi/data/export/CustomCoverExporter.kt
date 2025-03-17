package eu.kanade.tachiyomi.data.export

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.models.BackupCover
import eu.kanade.tachiyomi.data.backup.models.BackupCovers
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.core.archive.ZipWriter
import tachiyomi.domain.manga.interactor.GetFavorites
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object CustomCoverExporter {

    suspend fun exportToZip(
        context: Context,
        uri: Uri,
        onExportComplete: () -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val customCovers = Injekt.get<CoverCache>().getAllCustomCovers()
                val mangas = Injekt.get<GetFavorites>().await()
                val outputFile = UniFile.fromUri(context, uri)

                if (outputFile != null) {
                    ZipWriter(context, outputFile).use { zipWriter ->

                        val mangaUrlMappings = mangas.mapNotNull { manga ->
                            val expectedFileName = DiskUtil.hashKeyForDisk(manga.id.toString())
                            val file = customCovers.find { it.name == expectedFileName }

                            if (file?.exists() == true) {
                                BackupCover(manga.id, manga.url, manga.source)
                            } else {
                                null
                            }
                        }

                        val protoByteArray = ProtoBuf.encodeToByteArray(BackupCovers(mangaUrlMappings))
                        val protoFile = File(context.cacheDir, "manga_urls.proto")
                        protoFile.writeBytes(protoByteArray)

                        val protoUniFile = UniFile.fromFile(protoFile)!!
                        zipWriter.write(protoUniFile)
                        protoFile.delete()

                        mangas.forEach { manga ->
                            val expectedFileName = DiskUtil.hashKeyForDisk(manga.id.toString())
                            val file = customCovers.find { it.name == expectedFileName }

                            if (file?.exists() == true) {
                                val coverUniFile = UniFile.fromFile(file)!!
                                coverUniFile.renameTo("${manga.id}.jpg")
                                zipWriter.write(coverUniFile)
                            }
                        }
                    }
                }

                onExportComplete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
