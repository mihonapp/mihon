package eu.kanade.presentation.track.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.util.system.openInBrowser
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TrackerOAuthExpiredDialog(
    tracker: Tracker,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    when (tracker) {
                        is Anilist -> {
                            context.openInBrowser(AnilistApi.authUrl(), true)
                        }
                        is Bangumi -> {
                            context.openInBrowser(BangumiApi.authUrl(), true)
                        }
                        is MyAnimeList -> {
                            context.openInBrowser(MyAnimeListApi.authUrl(), true)
                        }
                    }
                    onDismissRequest()
                },
            ) { Text(stringResource(MR.strings.login)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
            ) { Text(stringResource(MR.strings.action_cancel)) }
        },
        title = { Text(stringResource(MR.strings.tracker_oauth_dialog_title, tracker.name)) },
        text = {
            Text(
                stringResource(MR.strings.tracker_oauth_dialog_description, tracker.name),
            )
        },
    )
}
