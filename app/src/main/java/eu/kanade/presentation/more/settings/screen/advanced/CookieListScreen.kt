package eu.kanade.presentation.more.settings.screen.advanced

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.TimeFormat
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.lang.toTimestampString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import mihon.app.di.appGraph
import mihon.domain.network.Cookie
import mihon.domain.network.CookieRepository
import mihon.domain.network.SameSite
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Delete
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class CookieListScreen(private val host: String) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = assistedMetroViewModel<CookieListViewModel, CookieListViewModel.Factory> { create(host) }
        val state by model.state.collectAsState()
        val dateTimeFormatter = rememberDateTimeFormatter()

        when (val s = state) {
            is CookieListViewModel.State.Loading -> LoadingScreen()
            is CookieListViewModel.State.Ready -> {
                s.dialog?.let { dialog ->
                    CookieDialog(
                        dialog = dialog,
                        dateTimeFormatter = dateTimeFormatter,
                        onDismiss = { model.setDialog(null) },
                        onConfirm = { cookie ->
                            model.addOrUpdate(dialog, cookie)
                            model.setDialog(null)
                        },
                    )
                }

                Scaffold(
                    topBar = {
                        SearchToolbar(
                            titleContent = { Text(host) },
                            navigateUp = navigator::pop,
                            actions = {
                                AppBarActions(
                                    actions = listOf(
                                        AppBar.Action(
                                            title = stringResource(MR.strings.action_add_cookie),
                                            icon = MaterialSymbols.Rounded.Add,
                                            onClick = {
                                                model.setDialog(CookieListViewModel.Dialog())
                                            },
                                        ),
                                    ),
                                )
                            },
                            searchQuery = s.query,
                            onChangeSearchQuery = model::onSearchQueryChange,
                        )
                    },
                ) { contentPadding ->
                    if (s.cookies.isEmpty()) {
                        EmptyScreen(stringResource(MR.strings.no_cookies_indexed))
                    } else if (s.filteredCookies.isEmpty()) {
                        EmptyScreen(stringResource(MR.strings.no_results_found))
                    } else {
                        ScrollbarLazyColumn(
                            contentPadding = contentPadding,
                        ) {
                            items(
                                items = s.filteredCookies,
                                key = { it.identity.key },
                            ) {
                                CookieItem(
                                    cookie = it,
                                    dateTimeFormatter = dateTimeFormatter,
                                    onClick = {
                                        model.setDialog(CookieListViewModel.Dialog.from(it))
                                    },
                                    onClickDelete = { model.delete(it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CookieDialog(
        dialog: CookieListViewModel.Dialog,
        dateTimeFormatter: DateTimeFormatter,
        onDismiss: () -> Unit,
        onConfirm: (Cookie) -> Unit,
    ) {
        val nameState = rememberTextFieldState(dialog.name)
        val valueState = rememberTextFieldState(dialog.value)
        val pathState = rememberTextFieldState(dialog.path)

        var hostOnly by remember { mutableStateOf(dialog.hostOnly) }
        var secure by remember { mutableStateOf(dialog.secure) }
        var httpOnly by remember { mutableStateOf(dialog.httpOnly) }
        var sameSite by remember { mutableStateOf(dialog.sameSite) }
        var expiresAt by remember { mutableStateOf(dialog.expiresAt) }

        var showExpiryPicker by remember { mutableStateOf(false) }

        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(
                        if (dialog.existing == null) {
                            MR.strings.action_add_cookie
                        } else {
                            MR.strings.action_edit_cookie
                        },
                    ),
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        state = nameState,
                        label = { Text(stringResource(MR.strings.cookie_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (dialog.existing == null) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                },
                            ),
                        enabled = dialog.existing == null,
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    OutlinedTextField(
                        state = valueState,
                        label = { Text(stringResource(MR.strings.cookie_value)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (dialog.existing != null) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                },
                            ),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    OutlinedTextField(
                        state = pathState,
                        label = { Text(stringResource(MR.strings.cookie_path)) },
                        modifier = Modifier.fillMaxWidth(),
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(MR.strings.cookie_expires),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { showExpiryPicker = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    expiresAt?.toTimestampString(dateTimeFormatter)
                                        ?: stringResource(MR.strings.cookie_expires_session),
                                )
                            }
                            if (expiresAt != null) {
                                IconButton(onClick = { expiresAt = null }) {
                                    Icon(
                                        imageVector = MaterialSymbols.Rounded.Close,
                                        contentDescription = stringResource(MR.strings.action_clear),
                                    )
                                }
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ToggleChip(
                            label = stringResource(MR.strings.cookie_host_only),
                            selected = hostOnly,
                            onClick = { hostOnly = !hostOnly },
                        )
                        ToggleChip(
                            label = stringResource(MR.strings.cookie_secure),
                            selected = secure,
                            onClick = { secure = !secure },
                        )
                        ToggleChip(
                            label = stringResource(MR.strings.cookie_http_only),
                            selected = httpOnly,
                            onClick = { httpOnly = !httpOnly },
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(MR.strings.cookie_same_site),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SameSite.entries.forEach {
                                ToggleChip(
                                    label = it.label(),
                                    selected = sameSite == it,
                                    onClick = {
                                        if (sameSite != SameSite.None && it == SameSite.None) {
                                            secure = true
                                        }
                                        sameSite = it
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm(
                            Cookie(
                                name = nameState.text.toString(),
                                value = valueState.text.toString(),
                                path = pathState.text.toString(),
                                hostOnly = hostOnly,
                                expiresAt = expiresAt,
                                secure = secure,
                                httpOnly = httpOnly,
                                sameSite = sameSite,
                            ),
                        )
                    },
                    enabled = nameState.text.isNotBlank(),
                ) {
                    Text(stringResource(MR.strings.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )

        if (showExpiryPicker) {
            ExpiryPickerDialog(
                initial = expiresAt,
                onDismiss = { showExpiryPicker = false },
                onConfirm = {
                    expiresAt = it
                    showExpiryPicker = false
                },
            )
        }
    }

    @Composable
    private fun ExpiryPickerDialog(
        initial: Instant?,
        onDismiss: () -> Unit,
        onConfirm: (Instant) -> Unit,
    ) {
        val context = LocalContext.current
        val locale = LocalLocale.current.platformLocale
        val uiPreferences = context.appGraph.uiPreferences
        val timeFormat by uiPreferences.timeFormat.collectAsState()
        val is24HourFormat = remember(timeFormat, locale) {
            when (timeFormat) {
                TimeFormat.FOLLOW_LANGUAGE -> {
                    val pattern = DateFormat.getBestDateTimePattern(locale, "j")
                    pattern.contains('H') || pattern.contains('k')
                }

                TimeFormat.FOLLOW_SYSTEM -> DateFormat.is24HourFormat(context)
                TimeFormat.HOURS_24 -> true
                TimeFormat.HOURS_12 -> false
            }
        }

        val timeZone = remember { TimeZone.currentSystemDefault() }
        val initialDateTime = remember(initial) {
            (initial ?: (Clock.System.now() + 1.days)).toLocalDateTime(timeZone)
        }
        val initialDateMillis = remember(initialDateTime) {
            LocalDateTime(initialDateTime.date, LocalTime(0, 0))
                .toInstant(TimeZone.UTC)
                .toEpochMilliseconds()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
        val timePickerState = rememberTimePickerState(
            initialHour = initialDateTime.hour,
            initialMinute = initialDateTime.minute,
            is24Hour = is24HourFormat,
        )

        var selectedTab by remember { mutableIntStateOf(0) }

        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        val dateMillis = datePickerState.selectedDateMillis ?: return@TextButton
                        val date = Instant.fromEpochMilliseconds(dateMillis).toLocalDateTime(TimeZone.UTC).date
                        onConfirm(
                            LocalDateTime(
                                date = date,
                                time = LocalTime(timePickerState.hour, timePickerState.minute),
                            ).toInstant(timeZone),
                        )
                    },
                    enabled = datePickerState.selectedDateMillis != null,
                ) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(MR.strings.date)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(MR.strings.time)) },
                    )
                }

                if (selectedTab == 0) {
                    DatePicker(
                        state = datePickerState,
                        title = null,
                        headline = null,
                    )
                } else {
                    TimePicker(
                        state = timePickerState,
                        modifier = Modifier.padding(vertical = MaterialTheme.padding.medium),
                    )
                }
            }
        }
    }

    @Composable
    private fun ToggleChip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
        )
    }

    @Composable
    private fun SameSite.label(): String = stringResource(
        when (this) {
            SameSite.Unspecified -> MR.strings.cookie_same_site_unspecified
            SameSite.None -> MR.strings.cookie_same_site_none
            SameSite.Lax -> MR.strings.cookie_same_site_lax
            SameSite.Strict -> MR.strings.cookie_same_site_strict
        },
    )

    @Composable
    private fun CookieItem(
        cookie: Cookie,
        dateTimeFormatter: DateTimeFormatter,
        onClick: () -> Unit,
        onClickDelete: () -> Unit,
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
            onClick = onClick,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "${cookie.name} = ${cookie.value}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val expires = cookie.expiresAt?.toTimestampString(dateTimeFormatter)
                        ?: stringResource(MR.strings.cookie_expires_session)
                    Text(
                        text = "${stringResource(MR.strings.cookie_expires)} = $expires",
                        modifier = Modifier.secondaryItemAlpha(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = "${stringResource(MR.strings.cookie_path)} = ${cookie.path}",
                        modifier = Modifier.secondaryItemAlpha(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(MaterialTheme.padding.extraSmall))
                    CookieAttributes(cookie)
                }

                IconButton(
                    onClick = onClickDelete,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Delete,
                        contentDescription = stringResource(MR.strings.action_delete),
                    )
                }
            }
        }
    }

    @Composable
    private fun CookieAttributes(cookie: Cookie) {
        val tags = buildList {
            if (cookie.hostOnly) add(stringResource(MR.strings.cookie_host_only))
            if (cookie.secure) add(stringResource(MR.strings.cookie_secure))
            if (cookie.httpOnly) add(stringResource(MR.strings.cookie_http_only))
            if (cookie.sameSite != SameSite.Unspecified) {
                add("${stringResource(MR.strings.cookie_same_site)}=${cookie.sameSite.label()}")
            }
        }

        val style = MaterialTheme.typography.labelSmall
        // line height + Pill's 1.dp vertical padding on both sides
        val pillHeight = with(LocalDensity.current) { style.lineHeight.toDp() } + 2.dp

        FlowRow(
            modifier = Modifier
                .offset(x = (-4).dp) // cancel Pill's start padding
                .heightIn(min = pillHeight),
            maxLines = 1,
        ) {
            tags.forEach { tag ->
                Pill(
                    text = tag,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = style,
                )
            }
        }
    }

    @Composable
    private fun rememberDateTimeFormatter(): DateTimeFormatter {
        val context = LocalContext.current
        val locale = LocalLocale.current
        val uiPreferences = context.appGraph.uiPreferences
        val dateFormat by uiPreferences.dateFormat.collectAsState()
        val timeFormat by uiPreferences.timeFormat.collectAsState()
        val is24HourSystem = DateFormat.is24HourFormat(context)
        return remember(dateFormat, timeFormat, locale, is24HourSystem) {
            UiPreferences.dateTimeFormat(
                dateFormat = dateFormat,
                timeFormat = timeFormat,
                locale = locale.platformLocale,
                is24HourSystem = is24HourSystem,
            )
        }
    }
}

@AssistedInject
class CookieListViewModel(
    @Assisted private val host: String,
    private val cookieRepository: CookieRepository,
) : ViewModel() {

    val state: StateFlow<State>
        field = MutableStateFlow<State>(State.Loading)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(host: String): CookieListViewModel
    }

    init {
        viewModelScope.launch {
            fetchCookies()
        }
    }

    private suspend fun fetchCookies() {
        cookieRepository.getCookiesForHost(host).let { cookies ->
            state.update {
                when (it) {
                    is State.Ready -> it.copy(cookies = cookies)
                    is State.Loading -> State.Ready(cookies = cookies)
                }
            }
        }
    }

    fun onSearchQueryChange(query: String?) = state.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(query = query)
    }

    fun setDialog(dialog: Dialog?) = state.update { state ->
        if (state !is State.Ready) return@update state
        state.copy(dialog = dialog)
    }

    fun delete(cookie: Cookie) {
        viewModelScope.launch {
            cookieRepository.deleteCookie(host, cookie)
            fetchCookies()
        }
    }

    fun addOrUpdate(dialog: Dialog, cookie: Cookie) {
        if (cookie.name.isBlank()) return

        val updated = cookie.copy(path = cookie.path.normalizePath())
        val existing = dialog.existing

        viewModelScope.launch {
            if (existing != null && (existing.identity != updated.identity || (existing.secure && !updated.secure))) {
                cookieRepository.deleteCookie(host, existing)
            }

            cookieRepository.addOrUpdateCookie(host, updated)
            fetchCookies()
        }
    }

    private fun String.normalizePath(): String {
        val trimmed = this.trim()
        if (trimmed.isBlank() || trimmed == "/") return "/"
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    data class Dialog(
        val name: String = "",
        val value: String = "",
        val path: String = "",
        val hostOnly: Boolean = false,
        val secure: Boolean = false,
        val httpOnly: Boolean = false,
        val sameSite: SameSite = SameSite.Unspecified,
        val expiresAt: Instant? = null,
        val existing: Cookie? = null,
    ) {
        companion object {
            fun from(cookie: Cookie) = Dialog(
                name = cookie.name,
                value = cookie.value,
                path = cookie.path,
                hostOnly = cookie.hostOnly,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                sameSite = cookie.sameSite,
                expiresAt = cookie.expiresAt,
                existing = cookie,
            )
        }
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Ready(
            val cookies: List<Cookie> = emptyList(),
            val query: String? = null,
            val dialog: Dialog? = null,
        ) : State {
            val filteredCookies: List<Cookie>
                get() = if (query.isNullOrBlank()) {
                    cookies
                } else {
                    cookies.filter { it.name.contains(query, ignoreCase = true) }
                }
        }
    }
}
