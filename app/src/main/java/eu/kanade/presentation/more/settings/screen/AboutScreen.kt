package eu.kanade.presentation.more.settings.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.bluelinelabs.conductor.Router
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.LinkIcon
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.more.LogoHeader
import eu.kanade.presentation.more.about.LicensesScreen
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.LocalRouter
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateResult
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.ui.more.NewUpdateDialogController
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AboutScreen : Screen {

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val handleBack = LocalBackPress.current
        val navigator = LocalNavigator.currentOrThrow
        val router = LocalRouter.currentOrThrow

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(R.string.pref_category_about),
                    navigateUp = if (handleBack != null) handleBack::invoke else null,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            ScrollbarLazyColumn(
                contentPadding = contentPadding,
            ) {
                item {
                    LogoHeader()
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(R.string.version),
                        subtitle = getVersionName(withBuildDate = true),
                        onPreferenceClick = {
                            val deviceInfo = CrashLogUtil(context).getDebugInfo()
                            context.copyToClipboard("Debug information", deviceInfo)
                        },
                    )
                }

                if (BuildConfig.INCLUDE_UPDATER) {
                    item {
                        TextPreferenceWidget(
                            title = stringResource(R.string.check_for_updates),
                            onPreferenceClick = {
                                scope.launch {
                                    checkVersion(context, router)
                                }
                            },
                        )
                    }
                }
                if (!BuildConfig.DEBUG) {
                    item {
                        TextPreferenceWidget(
                            title = stringResource(R.string.whats_new),
                            onPreferenceClick = { uriHandler.openUri(RELEASE_URL) },
                        )
                    }
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(R.string.help_translate),
                        onPreferenceClick = { uriHandler.openUri("https://tachiyomi.org/help/contribution/#translation") },
                    )
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(R.string.licenses),
                        onPreferenceClick = { navigator.push(LicensesScreen()) },
                    )
                }

                item {
                    TextPreferenceWidget(
                        title = stringResource(R.string.privacy_policy),
                        onPreferenceClick = { uriHandler.openUri("https://tachiyomi.org/privacy") },
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        LinkIcon(
                            label = stringResource(R.string.website),
                            painter = rememberVectorPainter(Icons.Outlined.Public),
                            url = "https://tachiyomi.org",
                        )
                        LinkIcon(
                            label = "Discord",
                            painter = painterResource(R.drawable.ic_discord_24dp),
                            url = "https://discord.gg/tachiyomi",
                        )
                        LinkIcon(
                            label = "Twitter",
                            painter = painterResource(R.drawable.ic_twitter_24dp),
                            url = "https://twitter.com/tachiyomiorg",
                        )
                        LinkIcon(
                            label = "Facebook",
                            painter = painterResource(R.drawable.ic_facebook_24dp),
                            url = "https://facebook.com/tachiyomiorg",
                        )
                        LinkIcon(
                            label = "Reddit",
                            painter = painterResource(R.drawable.ic_reddit_24dp),
                            url = "https://www.reddit.com/r/Tachiyomi",
                        )
                        LinkIcon(
                            label = "GitHub",
                            painter = painterResource(R.drawable.ic_github_24dp),
                            url = "https://github.com/tachiyomiorg",
                        )
                    }
                }
            }
        }
    }

    /**
     * Checks version and shows a user prompt if an update is available.
     */
    private suspend fun checkVersion(context: Context, router: Router) {
        val updateChecker = AppUpdateChecker()
        withUIContext {
            context.toast(R.string.update_check_look_for_updates)
            try {
                when (val result = withIOContext { updateChecker.checkForUpdate(context, isUserPrompt = true) }) {
                    is AppUpdateResult.NewUpdate -> {
                        NewUpdateDialogController(result).showDialog(router)
                    }
                    is AppUpdateResult.NoNewUpdate -> {
                        context.toast(R.string.update_check_no_new_updates)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                context.toast(e.message)
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    companion object {
        fun getVersionName(withBuildDate: Boolean): String {
            return when {
                BuildConfig.DEBUG -> {
                    "Debug ${BuildConfig.COMMIT_SHA}".let {
                        if (withBuildDate) {
                            "$it (${getFormattedBuildTime()})"
                        } else {
                            it
                        }
                    }
                }
                BuildConfig.PREVIEW -> {
                    "Preview r${BuildConfig.COMMIT_COUNT}".let {
                        if (withBuildDate) {
                            "$it (${BuildConfig.COMMIT_SHA}, ${getFormattedBuildTime()})"
                        } else {
                            "$it (${BuildConfig.COMMIT_SHA})"
                        }
                    }
                }
                else -> {
                    "Stable ${BuildConfig.VERSION_NAME}".let {
                        if (withBuildDate) {
                            "$it (${getFormattedBuildTime()})"
                        } else {
                            it
                        }
                    }
                }
            }
        }

        private fun getFormattedBuildTime(): String {
            return try {
                val inputDf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US)
                inputDf.timeZone = TimeZone.getTimeZone("UTC")
                val buildTime = inputDf.parse(BuildConfig.BUILD_TIME)

                val outputDf = DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                    Locale.getDefault(),
                )
                outputDf.timeZone = TimeZone.getDefault()

                buildTime!!.toDateTimestampString(UiPreferences.dateFormat(Injekt.get<UiPreferences>().dateFormat().get()))
            } catch (e: Exception) {
                BuildConfig.BUILD_TIME
            }
        }
    }
}
