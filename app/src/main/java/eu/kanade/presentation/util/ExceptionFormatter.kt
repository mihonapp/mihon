package eu.kanade.presentation.util

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.online.LicensedMangaChaptersException
import eu.kanade.tachiyomi.util.system.isOnline
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.source.model.SourceNotInstalledException
import java.net.UnknownHostException

context(Context)
val Throwable.formattedMessage: String
    get() {
        when (this) {
            is HttpException -> return getString(R.string.exception_http, code)
            is UnknownHostException -> {
                return if (!isOnline()) {
                    getString(R.string.exception_offline)
                } else {
                    getString(R.string.exception_unknown_host, message)
                }
            }

            is NoResultsException -> return getString(R.string.no_results_found)
            is SourceNotInstalledException -> return getString(R.string.loader_not_implemented_error)
            is LicensedMangaChaptersException -> return getString(R.string.licensed_manga_chapters_error)
        }
        return when (val className = this::class.simpleName) {
            "Exception", "IOException" -> message ?: className
            else -> "$className: $message"
        }
    }
