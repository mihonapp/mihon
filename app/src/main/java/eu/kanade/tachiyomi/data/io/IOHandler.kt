@file:JvmName("IOHandler")
package eu.kanade.tachiyomi.data.io

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.ToastUtil
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
     * Returns temp file location
     *
     * @param context context of application
 * @throws IOException IO exception
     * @return location of temp file
     */
    @Throws(IOException::class)
    private fun getTempFilename(context: Context): String {
        // Get output directory.
        val outputDir = context.cacheDir

        // Create temporary file
        val outputFile = File.createTempFile("temp_cover", "0", outputDir)

        // Return path of temporary file
        return outputFile.absolutePath
    }

    /**
     * Download media to temp location and returns file path
     *
     * @param input input stream containing input file
     * @param context context of application
     * @throws IOException IO exception
     * @return location of temp file
     */
    @Throws(IOException::class)
    fun downloadMediaAndReturnPath(input: FileInputStream, context: Context): String {
        var tempFilename = ""
        var output: FileOutputStream? = null
        try {
            // Get temp file name.
            tempFilename = getTempFilename(context)

            output = FileOutputStream(tempFilename)
            // Copy input stream to temp location.
            input.copyTo(output)
        } catch (e: IOException) {
            // Show user something went wrong and print stackTrace.
            ToastUtil.showShort(context, R.string.notification_manga_update_failed)
            e.printStackTrace()
        } finally {
            // Close streams.
            input.close()
            output?.close()
        }

        // Return temp name.
        return tempFilename
    }