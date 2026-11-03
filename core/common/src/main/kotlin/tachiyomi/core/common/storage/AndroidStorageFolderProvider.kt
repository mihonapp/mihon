package tachiyomi.core.common.storage

import android.content.Context
import android.os.Environment
import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.profile.ProfileManager
import tachiyomi.i18n.MR
import java.io.File

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidStorageFolderProvider(
    private val context: Context,
) : FolderProvider {

    override fun directory(): File {
        val root = File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                context.stringResource(MR.strings.app_name),
        )
        if (ProfileManager.isDefault(context)) return root
        // Downloads, backups and local sources of other profiles live in their own folder
        return File(root, "profiles" + File.separator + ProfileManager.activeId(context)).also { it.mkdirs() }
    }

    override fun path(): String {
        return directory().toUri().toString()
    }
}
