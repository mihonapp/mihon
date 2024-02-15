package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.util.lang.toRelativeSting
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun relativeDateText(
    dateEpochMillis: Long,
): String {
    return relativeDateText(
        localDate = LocalDate.ofInstant(Instant.ofEpochMilli(dateEpochMillis), ZoneId.systemDefault()).takeIf { dateEpochMillis > 0L },
    )
}

@Composable
fun relativeDateText(
    localDate: LocalDate?,
): String {
    val context = LocalContext.current

    val preferences = remember { Injekt.get<UiPreferences>() }
    val relativeTime = remember { preferences.relativeTime().get() }
    val dateFormat = remember { UiPreferences.dateFormat(preferences.dateFormat().get()) }

    return localDate?.toRelativeSting(
            context = context,
            relative = relativeTime,
            dateFormat = dateFormat
        )
        ?: stringResource(MR.strings.not_applicable)
}
