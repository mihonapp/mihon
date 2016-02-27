package eu.kanade.tachiyomi.data.io

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Returns temp file location.
 *
 * @param context context of application.
 * @throws IOException IO exception.
 * @return location of temp file.
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
 * Download media to temp location and returns file path.
 *
 * @param input input stream containing input file.
 * @param context context of application.
 * @throws IOException IO exception.
 * @return location of temp file.
 */
@Throws(IOException::class)
fun downloadMediaAndReturnPath(input: FileInputStream, context: Context): String {
    var output: FileOutputStream? = null
    try {
        // Get temp file name.
        val tempFilename = getTempFilename(context)

        output = FileOutputStream(tempFilename)

        // Copy input stream to temp location.
        input.copyTo(output)

        return tempFilename
    } finally {
        // Close streams.
        input.close()
        output?.close()
    }

}