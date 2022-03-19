package eu.kanade.tachiyomi.util.system

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.kanade.tachiyomi.R

fun Uri.toShareIntent(context: Context, type: String = "image/*", message: String? = null): Intent {
    val uri = this

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        if (message != null) putExtra(Intent.EXTRA_TEXT, message)
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        setType(type)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }

    return Intent.createChooser(shareIntent, context.getString(R.string.action_share)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
}
