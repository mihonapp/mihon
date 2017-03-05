package eu.kanade.tachiyomi.data.glide

import com.bumptech.glide.Priority
import com.bumptech.glide.load.data.DataFetcher
import java.io.File
import java.io.IOException
import java.io.InputStream

open class FileFetcher(private val file: File) : DataFetcher<InputStream> {

    private var data: InputStream? = null

    override fun loadData(priority: Priority): InputStream {
        data = file.inputStream()
        return data!!
    }

    override fun cleanup() {
        data?.let { data ->
            try {
                data.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }

    override fun cancel() {
        // Do nothing.
    }

    override fun getId(): String {
        return file.toString()
    }
}