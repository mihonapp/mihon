package eu.kanade.presentation.crash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.CrashLogUtil
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.InfoScreen
import tachiyomi.presentation.core.util.ThemePreviews

@Composable
fun CrashScreen(
    exception: Throwable?,
    onRestartClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    InfoScreen(
        icon = Icons.Outlined.BugReport,
        headingText = stringResource(R.string.crash_screen_title),
        subtitleText = stringResource(R.string.crash_screen_description, stringResource(R.string.app_name)),
        acceptText = stringResource(R.string.pref_dump_crash_logs),
        onAcceptClick = {
            scope.launch {
                CrashLogUtil(context).dumpLogs()
            }
        },
        rejectText = stringResource(R.string.crash_screen_restart_application),
        onRejectClick = onRestartClick,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = MaterialTheme.padding.small)
                .clip(MaterialTheme.shapes.small)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = exception.toString(),
                modifier = Modifier
                    .padding(all = MaterialTheme.padding.small),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun CrashScreenPreview() {
    TachiyomiTheme {
        CrashScreen(exception = RuntimeException("Dummy")) {}
    }
}
