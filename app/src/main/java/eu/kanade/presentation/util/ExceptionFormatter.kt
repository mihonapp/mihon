package eu.kanade.presentation.util

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.network.HttpException
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.source.model.SourceNotInstalledException

context(Context)
val Throwable.formattedMessage: String
    get() {
        when (this) {
            is NoResultsException -> return getString(R.string.no_results_found)
            is SourceNotInstalledException -> return getString(R.string.loader_not_implemented_error)
            is HttpException -> return "$message: ${getString(R.string.http_error_hint)}"
        }
        return when (val className = this::class.simpleName) {
            "Exception", "IOException" -> message ?: className
            else -> "$className: $message"
        }
    }
