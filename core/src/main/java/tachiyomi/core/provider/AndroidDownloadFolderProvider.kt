package tachiyomi.core.provider

import android.content.Context
import android.os.Environment
import androidx.core.net.toUri
import tachiyomi.core.i18n.localize
import tachiyomi.i18n.MR
import java.io.File

class AndroidDownloadFolderProvider(
    val context: Context,
) : FolderProvider {

    override fun directory(): File {
        return File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                context.localize(MR.strings.app_name),
            "downloads",
        )
    }

    override fun path(): String {
        return directory().toUri().toString()
    }
}
