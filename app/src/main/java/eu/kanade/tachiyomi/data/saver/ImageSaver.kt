package eu.kanade.tachiyomi.data.saver

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.contentValuesOf
import androidx.core.net.toUri
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.logcat
import logcat.LogPriority
import okio.IOException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

class ImageSaver(
    val context: Context,
) {

    @SuppressLint("InlinedApi")
    fun save(image: Image): Uri {
        val data = image.data

        val type = ImageUtil.findImageType(data) ?: throw Exception("Not an image")
        val filename = DiskUtil.buildValidFilename("${image.name}.${type.extension}")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || image.location !is Location.Pictures) {
            return save(data(), image.location.directory(context), filename)
        }

        val pictureDir =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val folderRelativePath = "${Environment.DIRECTORY_PICTURES}/${context.getString(R.string.app_name)}/"
        val imageLocation = (image.location as Location.Pictures).relativePath

        val contentValues = contentValuesOf(
            MediaStore.Images.Media.DISPLAY_NAME to image.name,
            MediaStore.Images.Media.MIME_TYPE to type.mime,
            MediaStore.Images.Media.RELATIVE_PATH to folderRelativePath + imageLocation,
        )

        val picture = findUriOrDefault(folderRelativePath, "$imageLocation$filename") {
            context.contentResolver.insert(
                pictureDir,
                contentValues,
            ) ?: throw IOException(context.getString(R.string.error_saving_picture))
        }

        try {
            data().use { input ->
                @Suppress("BlockingMethodInNonBlockingContext")
                context.contentResolver.openOutputStream(picture, "w").use { output ->
                    input.copyTo(output!!)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            throw IOException(context.getString(R.string.error_saving_picture))
        }

        DiskUtil.scanMedia(context, picture)

        return picture
    }

    private fun save(inputStream: InputStream, directory: File, filename: String): Uri {
        directory.mkdirs()

        val destFile = File(directory, filename)

        inputStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        DiskUtil.scanMedia(context, destFile.toUri())

        return destFile.getUriCompat(context)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findUriOrDefault(relativePath: String, imagePath: String, default: () -> Uri): Uri {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )

        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(relativePath, imagePath),
            null,
        ).use { cursor ->
            if (cursor != null && cursor.count >= 1) {
                cursor.moveToFirst().let {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))

                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        }
        return default()
    }
}

sealed class Image(
    open val name: String,
    open val location: Location,
) {
    data class Cover(
        val bitmap: Bitmap,
        override val name: String,
        override val location: Location,
    ) : Image(name, location)

    data class Page(
        val inputStream: () -> InputStream,
        override val name: String,
        override val location: Location,
    ) : Image(name, location)

    val data: () -> InputStream
        get() {
            return when (this) {
                is Cover -> {
                    {
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                        ByteArrayInputStream(baos.toByteArray())
                    }
                }
                is Page -> inputStream
            }
        }
}

sealed class Location {
    data class Pictures private constructor(val relativePath: String) : Location() {
        companion object {
            fun create(relativePath: String = ""): Pictures {
                return Pictures(relativePath)
            }
        }
    }

    object Cache : Location()

    fun directory(context: Context): File {
        return when (this) {
            Cache -> context.cacheImageDir
            is Pictures -> {
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    context.getString(R.string.app_name),
                )
                if (relativePath.isNotEmpty()) {
                    return File(
                        file,
                        relativePath,
                    )
                }
                file
            }
        }
    }
}
