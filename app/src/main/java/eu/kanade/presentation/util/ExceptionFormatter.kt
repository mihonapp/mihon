package eu.kanade.presentation.util

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.network.HttpException
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.source.model.SourceNotInstalledException
import java.io.IOException

context(Context)
val Throwable.formattedMessage: String
    get() = when {
        this is NoResultsException -> getString(R.string.no_results_found)
        this is SourceNotInstalledException -> getString(R.string.loader_not_implemented_error)
        this is HttpException -> "$message: ${getString(R.string.http_error_hint)}"
        this is IOException || this is Exception -> message ?: this::class.simpleName.orEmpty()
        this::class.simpleName != null -> "${this::class.simpleName}: $message"
        else -> message.orEmpty()
    }
