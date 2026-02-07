package eu.kanade.presentation.more.settings.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.ui.webview.TrackerWebViewLoginActivity
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTrackingScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_tracking

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri("https://mihon.app/docs/guides/tracking") }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(MR.strings.tracking_guide),
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val trackPreferences = remember { Injekt.get<TrackPreferences>() }
        val trackerManager = remember { Injekt.get<TrackerManager>() }
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val autoTrackStatePref = trackPreferences.autoUpdateTrackOnMarkRead()

        var dialog by remember { mutableStateOf<Any?>(null) }
        dialog?.run {
            when (this) {
                is LoginDialog -> {
                    TrackingLoginDialog(
                        tracker = tracker,
                        uNameStringRes = uNameStringRes,
                        onDismissRequest = { dialog = null },
                    )
                }
                is LogoutDialog -> {
                    TrackingLogoutDialog(
                        tracker = tracker,
                        onDismissRequest = { dialog = null },
                    )
                }
                is NovelTrackerLoginDialog -> {
                    NovelTrackerLoginDialogContent(
                        tracker = tracker,
                        trackerName = trackerName,
                        onDismissRequest = { dialog = null },
                    )
                }
                is NovelUpdatesListMappingDialog -> {
                    NovelUpdatesListMappingDialogContent(
                        trackerManager = trackerManager,
                        trackPreferences = trackPreferences,
                        onDismissRequest = { dialog = null },
                    )
                }
            }
        }

        val enhancedTrackers = trackerManager.trackers
            .filter { it is EnhancedTracker }
            .partition { service ->
                val acceptedSources = (service as EnhancedTracker).getAcceptedSources()
                sourceManager.getCatalogueSources().any { it::class.qualifiedName in acceptedSources }
            }
        var enhancedTrackerInfo = stringResource(MR.strings.enhanced_tracking_info)
        if (enhancedTrackers.second.isNotEmpty()) {
            val missingSourcesInfo = stringResource(
                MR.strings.enhanced_services_not_installed,
                enhancedTrackers.second.joinToString { it.name },
            )
            enhancedTrackerInfo += "\n\n$missingSourcesInfo"
        }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = trackPreferences.autoUpdateTrack(),
                title = stringResource(MR.strings.pref_auto_update_manga_sync),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = trackPreferences.autoUpdateTrackOnMarkRead(),
                entries = AutoTrackState.entries
                    .associateWith { stringResource(it.titleRes) }
                    .toPersistentMap(),
                title = stringResource(MR.strings.pref_auto_update_manga_on_mark_read),
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = trackPreferences.minChaptersBeforeTrackingManga(),
                title = "Minimum chapters before tracking (Manga)",
                subtitle = "Number of chapters that must be read before auto-tracking starts for manga (0 = always track)",
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = trackPreferences.minChaptersBeforeTrackingNovel(),
                title = "Minimum chapters before tracking (Novels)",
                subtitle = "Number of chapters that must be read before auto-tracking starts for novels (0 = always track)",
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.services),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.myAnimeList,
                        login = { context.openInBrowser(MyAnimeListApi.authUrl(), forceDefaultBrowser = true) },
                        logout = { dialog = LogoutDialog(trackerManager.myAnimeList) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.aniList,
                        login = { context.openInBrowser(AnilistApi.authUrl(), forceDefaultBrowser = true) },
                        logout = { dialog = LogoutDialog(trackerManager.aniList) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.kitsu,
                        login = { dialog = LoginDialog(trackerManager.kitsu, MR.strings.email) },
                        logout = { dialog = LogoutDialog(trackerManager.kitsu) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.mangaUpdates,
                        login = { dialog = LoginDialog(trackerManager.mangaUpdates, MR.strings.username) },
                        logout = { dialog = LogoutDialog(trackerManager.mangaUpdates) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.shikimori,
                        login = { context.openInBrowser(ShikimoriApi.authUrl(), forceDefaultBrowser = true) },
                        logout = { dialog = LogoutDialog(trackerManager.shikimori) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.bangumi,
                        login = { context.openInBrowser(BangumiApi.authUrl(), forceDefaultBrowser = true) },
                        logout = { dialog = LogoutDialog(trackerManager.bangumi) },
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.tracking_info)),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Novel Trackers",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.novelUpdates,
                        login = {
                            // Use WebView-based login for NovelUpdates
                            val intent = TrackerWebViewLoginActivity.newIntent(
                                context,
                                trackerId = 10L,
                                trackerName = "NovelUpdates",
                                loginUrl = "https://www.novelupdates.com/login/",
                            )
                            context.startActivity(intent)
                        },
                        logout = { dialog = LogoutDialog(trackerManager.novelUpdates) },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = trackPreferences.novelUpdatesMarkChaptersAsRead(),
                        title = "Mark chapters as read on NovelUpdates",
                        subtitle = "Automatically mark chapters as read when you read them in the app",
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = trackPreferences.novelUpdatesSyncReadingList(),
                        title = "Sync reading list",
                        subtitle = "Keep reading list status in sync with NovelUpdates",
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = trackPreferences.novelUpdatesUseCustomListMapping(),
                        title = "Custom list mapping",
                        subtitle = "Map statuses to custom NovelUpdates lists",
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "Configure list mapping",
                        subtitle = "Choose which list each status maps to",
                        onClick = { dialog = NovelUpdatesListMappingDialog },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.novelList,
                        login = {
                            // Use WebView-based login for NovelList
                            val intent = TrackerWebViewLoginActivity.newIntent(
                                context,
                                trackerId = 11L,
                                trackerName = "NovelList",
                                loginUrl = "https://www.novellist.co/sign-in",
                            )
                            context.startActivity(intent)
                        },
                        logout = { dialog = LogoutDialog(trackerManager.novelList) },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = trackPreferences.novelListMarkChaptersAsRead(),
                        title = "Mark chapters as read on NovelList",
                        subtitle = "Automatically mark chapters as read when you read them in the app",
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = trackPreferences.novelListSyncReadingList(),
                        title = "Sync reading list",
                        subtitle = "Keep reading list status in sync with NovelList",
                    ),
                    Preference.PreferenceItem.InfoPreference(
                        "Login via WebView. Cookies will be automatically extracted after successful login.",
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.enhanced_services),
                preferenceItems = (
                    enhancedTrackers.first
                        .map { service ->
                            Preference.PreferenceItem.TrackerPreference(
                                tracker = service,
                                login = { (service as EnhancedTracker).loginNoop() },
                                logout = service::logout,
                            )
                        } + listOf(Preference.PreferenceItem.InfoPreference(enhancedTrackerInfo))
                    ).toImmutableList(),
            ),
        )
    }

    @Composable
    private fun TrackingLoginDialog(
        tracker: Tracker,
        uNameStringRes: StringResource,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var username by remember { mutableStateOf(TextFieldValue(tracker.getUsername())) }
        var password by remember { mutableStateOf(TextFieldValue(tracker.getPassword())) }
        var processing by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(MR.strings.login_title, tracker.name),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_close),
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.Username + ContentType.EmailAddress },
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(text = stringResource(uNameStringRes)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        isError = inputError && !processing,
                    )

                    var hidePassword by remember { mutableStateOf(true) }
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.Password },
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(text = stringResource(MR.strings.password)) },
                        trailingIcon = {
                            IconButton(onClick = { hidePassword = !hidePassword }) {
                                Icon(
                                    imageVector = if (hidePassword) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                        visualTransformation = if (hidePassword) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        singleLine = true,
                        isError = inputError && !processing,
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !processing && username.text.isNotBlank() && password.text.isNotBlank(),
                    onClick = {
                        scope.launchIO {
                            processing = true
                            val result = checkLogin(
                                context = context,
                                tracker = tracker,
                                username = username.text,
                                password = password.text,
                            )
                            inputError = !result
                            if (result) onDismissRequest()
                            processing = false
                        }
                    },
                ) {
                    val id = if (processing) MR.strings.logging_in else MR.strings.login
                    Text(text = stringResource(id))
                }
            },
        )
    }

    private suspend fun checkLogin(
        context: Context,
        tracker: Tracker,
        username: String,
        password: String,
    ): Boolean {
        return try {
            tracker.login(username, password)
            withUIContext { context.toast(MR.strings.login_success) }
            true
        } catch (e: Throwable) {
            tracker.logout()
            withUIContext { context.toast(e.message.toString()) }
            false
        }
    }

    @Composable
    private fun TrackingLogoutDialog(
        tracker: Tracker,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = stringResource(MR.strings.logout_title, tracker.name),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismissRequest,
                    ) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            tracker.logout()
                            onDismissRequest()
                            context.toast(MR.strings.logout_success)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.logout))
                    }
                }
            },
        )
    }

    @Composable
    private fun NovelTrackerLoginDialogContent(
        tracker: Tracker,
        trackerName: String,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var token by remember { mutableStateOf(TextFieldValue(tracker.getPassword())) }
        var processing by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf(false) }

        val instructions = when (trackerName) {
            "NovelList" -> {
                // Use a raw string split across multiple source lines to keep line lengths short
                """
                1. Login to novellist.co in browser
                2. Open DevTools (F12) → Application → Cookies
                3. Find 'novellist' cookie
                4. If it starts with 'base64-', decode it and extract 'access_token'
                5. Paste the token below
                """.trimIndent()
            }
            "NovelUpdates" -> {
                """
                1. Login to novelupdates.com in browser
                2. Open DevTools (F12) → Application → Cookies
                3. Copy all cookie values
                4. Paste below as: cookie_name=value; cookie_name2=value2
                """.trimIndent()
            }
            else -> "Enter your authentication token"
        }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Login to $trackerName",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_close),
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = instructions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    var hideToken by remember { mutableStateOf(true) }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Token / Cookies") },
                        trailingIcon = {
                            IconButton(onClick = { hideToken = !hideToken }) {
                                Icon(
                                    imageVector = if (hideToken) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                        visualTransformation = if (hideToken) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        singleLine = false,
                        maxLines = 3,
                        isError = inputError && !processing,
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !processing && token.text.isNotBlank(),
                    onClick = {
                        scope.launchIO {
                            processing = true
                            val result = runCatching {
                                tracker.login("", token.text.trim())
                            }
                            inputError = result.isFailure
                            processing = false
                            if (result.isSuccess) {
                                withUIContext {
                                    onDismissRequest()
                                    context.toast(MR.strings.login_success)
                                }
                            } else {
                                withUIContext {
                                    context.toast(result.exceptionOrNull()?.message ?: "Login failed")
                                }
                            }
                        }
                    },
                ) {
                    if (processing) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp))
                    } else {
                        Text(text = stringResource(MR.strings.login))
                    }
                }
            },
        )
    }

    @Composable
    private fun NovelUpdatesListMappingDialogContent(
        trackerManager: TrackerManager,
        trackPreferences: TrackPreferences,
        onDismissRequest: () -> Unit,
    ) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        // Load cached lists
        var availableLists by remember {
            val cached = trackPreferences.novelUpdatesCachedLists().get()
            val lists = try {
                if (cached.isNotEmpty() && cached != "[]") {
                    kotlinx.serialization.json.Json.decodeFromString<List<List<String>>>(cached)
                        .map { Pair(it[0], it[1]) }
                } else {
                    emptyList()
                }
            } catch (_: Exception) { emptyList() }
            val defaultLists = listOf(
                Pair("0", "Reading List"),
                Pair("1", "Completed"),
                Pair("2", "Plan to Read"),
                Pair("3", "On Hold"),
                Pair("4", "Dropped"),
            )
            mutableStateOf(if (lists.isEmpty()) defaultLists else lists)
        }

        // Current mappings (status ID -> list ID)
        var mappings by remember {
            val json = trackPreferences.novelUpdatesCustomListMapping().get()
            val map = try {
                if (json.isNotEmpty() && json != "{}") {
                    kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(json)
                } else {
                    mapOf("1" to "0", "2" to "1", "3" to "3", "4" to "4", "5" to "2")
                }
            } catch (_: Exception) {
                mapOf("1" to "0", "2" to "1", "3" to "3", "4" to "4", "5" to "2")
            }
            mutableStateOf(map)
        }

        var isLoading by remember { mutableStateOf(false) }

        val statuses = listOf(
            "1" to "Reading",
            "2" to "Completed",
            "3" to "On Hold",
            "4" to "Dropped",
            "5" to "Plan to Read",
        )

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text("List Mapping") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Refresh button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${availableLists.size} lists loaded",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launchIO {
                                    isLoading = true
                                    val lists = trackerManager.novelUpdates.getAvailableReadingLists()
                                    if (lists.isNotEmpty()) {
                                        availableLists = lists
                                        val cached = kotlinx.serialization.json.Json.encodeToString(
                                            lists.map { listOf(it.first, it.second) },
                                        )
                                        trackPreferences.novelUpdatesCachedLists().set(cached)
                                        trackPreferences.novelUpdatesLastListRefresh()
                                            .set(System.currentTimeMillis())
                                    }
                                    withUIContext {
                                        isLoading = false
                                        if (lists.isEmpty()) context.toast("Failed to fetch lists")
                                    }
                                }
                            },
                            enabled = !isLoading,
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.height(16.dp))
                            } else {
                                Text("Refresh")
                            }
                        }
                    }

                    HorizontalDivider()

                    // Status mappings
                    statuses.forEach { (statusId, statusName) ->
                        var expanded by remember { mutableStateOf(false) }
                        val selectedListId = mappings[statusId] ?: "0"
                        val selectedName = availableLists.find { it.first == selectedListId }?.second
                            ?: "List #$selectedListId"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = statusName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(0.4f),
                            )
                            Box(modifier = Modifier.weight(0.6f)) {
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        selectedName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    availableLists.forEach { (listId, listName) ->
                                        DropdownMenuItem(
                                            text = { Text(listName) },
                                            onClick = {
                                                mappings = mappings.toMutableMap().apply {
                                                    put(statusId, listId)
                                                }
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        trackPreferences.novelUpdatesCustomListMapping().set(
                            kotlinx.serialization.json.Json.encodeToString(mappings),
                        )
                        onDismissRequest()
                    },
                ) {
                    Text(stringResource(MR.strings.action_save))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissRequest) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

private data class LoginDialog(
    val tracker: Tracker,
    val uNameStringRes: StringResource,
)

private data class LogoutDialog(
    val tracker: Tracker,
)

private data class NovelTrackerLoginDialog(
    val tracker: Tracker,
    val trackerName: String,
)

private data object NovelUpdatesListMappingDialog
