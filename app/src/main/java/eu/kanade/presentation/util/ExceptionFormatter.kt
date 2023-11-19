package eu.kanade.presentation.util

import android.content.Context
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.online.LicensedMangaChaptersException
import eu.kanade.tachiyomi.util.system.isOnline
import tachiyomi.core.i18n.stringResource
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.source.model.SourceNotInstalledException
import tachiyomi.i18n.MR
import java.net.UnknownHostException

context(Context)
val Throwable.formattedMessage: String
    get() {
        when (this) {
            is HttpException -> return stringResource(MR.strings.exception_http, code)
            is UnknownHostException -> {
                return if (!isOnline()) {
                    stringResource(MR.strings.exception_offline)
                } else {
                    stringResource(MR.strings.exception_unknown_host, message ?: "")
                }
            }

            is NoResultsException -> return stringResource(MR.strings.no_results_found)
            is SourceNotInstalledException -> return stringResource(MR.strings.loader_not_implemented_error)
            is LicensedMangaChaptersException -> return stringResource(MR.strings.licensed_manga_chapters_error)
        }
        return when (val className = this::class.simpleName) {
            "Exception", "IOException" -> message ?: className
            else -> "$className: $message"
        }
    }
