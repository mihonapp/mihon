package eu.kanade.tachiyomi.ui.browse.extension.details

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.widget.doAfterTextChanged
import androidx.preference.CheckBoxPreference
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import androidx.preference.getOnBindEditTextListener
import androidx.preference.setOnStateChangeListener
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.CheckboxPreferenceWidget
import eu.kanade.presentation.more.settings.widget.ListPreferenceDialog
import eu.kanade.presentation.more.settings.widget.MultiSelectListPreferenceDialog
import eu.kanade.presentation.more.settings.widget.PreferenceDialogMessage
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.preference.SharedPreferencesDataStore
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.sourcePreferences
import eu.kanade.tachiyomi.util.view.setIncognito
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class SourcePreferencesScreen(val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        // The preferences are made anew after a configuration change, so an open dialog is restored by key
        var savedDialogKey by rememberSaveable { mutableStateOf<String?>(null) }

        val screen by produceState<ExtensionPreferenceScreen?>(initialValue = null) {
            val source = context.appGraph.sourceManager.getOrStub(sourceId)
            // Reading a value loads the file, which shouldn't happen on the main thread
            val sourcePreferences = (source as? ConfigurableSource)?.let {
                withContext(Dispatchers.IO) { it.sourcePreferences().apply { all } }
            }
            value = ExtensionPreferenceScreen(context, source, sourcePreferences, savedDialogKey)
        }

        val preferenceScreen = screen
        if (preferenceScreen == null) {
            LoadingScreen()
            return
        }

        DisposableEffect(preferenceScreen) {
            onDispose { preferenceScreen.dispose() }
        }
        SideEffect { savedDialogKey = preferenceScreen.dialogPreference?.key }

        Scaffold(
            topBar = {
                AppBar(
                    title = preferenceScreen.title,
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            val items = remember(preferenceScreen.version) { preferenceScreen.items() }
            ScrollbarLazyColumn(contentPadding = contentPadding) {
                items(items) { PreferenceRow(it) }
            }
        }

        preferenceScreen.dialogPreference?.let {
            PreferenceDialog(
                preference = it,
                onDismissRequest = { preferenceScreen.dialogPreference = null },
            )
        }
    }
}

/**
 * The screen an extension fills in [ConfigurableSource.setupPreferenceScreen]. The [androidx.preference] objects stay
 * the model, so extensions keep the exact behaviour of the library, and are only drawn with Compose.
 */
@Stable
@SuppressLint("RestrictedApi")
private class ExtensionPreferenceScreen(
    context: Context,
    source: Source,
    sourcePreferences: SharedPreferences?,
    dialogKey: String?,
) {
    val title = source.toString()

    // Bumped on every change the library would rebind a preference's view for
    var version by mutableIntStateOf(0)
        private set

    var dialogPreference by mutableStateOf<DialogPreference?>(null)

    private val screen: PreferenceScreen

    init {
        val preferenceManager = PreferenceManager(context)
        if (sourcePreferences != null) {
            preferenceManager.preferenceDataStore = SharedPreferencesDataStore(sourcePreferences)
        }
        preferenceManager.setOnDisplayPreferenceDialogListener { dialogPreference = it as? DialogPreference }

        screen = preferenceManager.createPreferenceScreen(context)
        (source as? ConfigurableSource)?.setupPreferenceScreen(screen)
        preferenceManager.setPreferences(screen)
        screen.onAttached()
        observe(screen)
        dialogPreference = dialogKey?.let { screen.findPreference<Preference>(it) as? DialogPreference }
    }

    private fun observe(preference: Preference) {
        preference.setOnStateChangeListener {
            // A group changing may mean preferences were added to it
            if (it is PreferenceGroup) observe(it)
            version++
        }
        if (preference is PreferenceGroup) {
            repeat(preference.preferenceCount) { observe(preference.getPreference(it)) }
        }
    }

    fun items(): List<PreferenceItem> = buildList { addVisible(screen) }

    private fun MutableList<PreferenceItem>.addVisible(group: PreferenceGroup) {
        repeat(group.preferenceCount) {
            val preference = group.getPreference(it)
            if (!preference.isVisible) return@repeat
            add(PreferenceItem.of(preference))
            if (preference is PreferenceGroup) addVisible(preference)
        }
    }

    fun dispose() {
        screen.onDetached()
    }
}

/**
 * What a preference shows, read once per change so the list redraws when an extension changes a preference.
 */
private data class PreferenceItem(
    val preference: Preference,
    val title: String?,
    val summary: String?,
    val enabled: Boolean,
    val checked: Boolean?,
) {
    companion object {
        fun of(preference: Preference): PreferenceItem {
            val summary = when {
                preference !is TwoStatePreference -> preference.summary
                preference.isChecked && !preference.summaryOn.isNullOrEmpty() -> preference.summaryOn
                !preference.isChecked && !preference.summaryOff.isNullOrEmpty() -> preference.summaryOff
                else -> preference.summary
            }
            return PreferenceItem(
                preference = preference,
                title = preference.title?.toString(),
                summary = summary?.toString(),
                enabled = preference.isEnabled,
                checked = (preference as? TwoStatePreference)?.isChecked,
            )
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun PreferenceRow(item: PreferenceItem) {
    val preference = item.preference
    val onClick = if (preference.isSelectable) preference::performClick else null
    when {
        preference is PreferenceGroup -> PreferenceGroupHeader(title = item.title.orEmpty())
        preference is CheckBoxPreference -> CheckboxPreferenceWidget(
            title = item.title.orEmpty(),
            subtitle = item.summary,
            checked = item.checked == true,
            enabled = item.enabled,
            onCheckedChanged = { onClick?.invoke() },
        )
        item.checked != null -> SwitchPreferenceWidget(
            title = item.title.orEmpty(),
            subtitle = item.summary,
            checked = item.checked,
            enabled = item.enabled,
            onCheckedChanged = { onClick?.invoke() },
        )
        else -> TextPreferenceWidget(
            title = item.title,
            subtitle = item.summary,
            enabled = item.enabled,
            onPreferenceClick = onClick,
        )
    }
}

@Composable
private fun PreferenceDialog(preference: DialogPreference, onDismissRequest: () -> Unit) {
    val title = (preference.dialogTitle?.takeIf { it.isNotEmpty() } ?: preference.title)?.toString()
    val message = preference.dialogMessage?.toString()
    when (preference) {
        is ListPreference -> ListPreferenceDialog(
            title = title,
            message = message,
            value = preference.value,
            entries = entryMap(preference.entries, preference.entryValues),
            onValueChange = {
                if (preference.callChangeListener(it)) preference.value = it
            },
            onDismissRequest = onDismissRequest,
        )
        is MultiSelectListPreference -> MultiSelectListPreferenceDialog(
            title = title,
            message = message,
            values = preference.values,
            entries = entryMap(preference.entries, preference.entryValues),
            onValuesChange = {
                val values = it.toHashSet()
                if (values != preference.values && preference.callChangeListener(values)) {
                    preference.values = values
                }
            },
            onDismissRequest = onDismissRequest,
        )
        is EditTextPreference -> EditTextPreferenceDialog(preference, title, message, onDismissRequest)
        else -> LaunchedEffect(preference) { onDismissRequest() }
    }
}

private fun entryMap(entries: Array<CharSequence>?, entryValues: Array<CharSequence>?): Map<String, String> {
    return entryValues.orEmpty().zip(entries.orEmpty()) { value, entry -> value.toString() to entry.toString() }.toMap()
}

/**
 * Extensions get a real [EditText] through [EditTextPreference.OnBindEditTextListener], and some validate input by
 * disabling the dialog's `android.R.id.button1` found from [EditText.getRootView], so one stands in for the OK button.
 */
@Composable
private fun EditTextPreferenceDialog(
    preference: EditTextPreference,
    title: String?,
    message: String?,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // What's typed, kept across a configuration change
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf<EditText?>(null) }
    var confirmEnabled by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title?.let { { Text(text = it) } },
        text = {
            // Like the library's dialog, which shows the keyboard as it opens
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE) }
            Column {
                if (message != null) PreferenceDialogMessage(message)
                AndroidView(
                    factory = { viewContext ->
                        LinearLayout(viewContext).apply {
                            val confirmButton = object : Button(viewContext) {
                                init {
                                    id = android.R.id.button1
                                    visibility = GONE
                                }

                                override fun setEnabled(enabled: Boolean) {
                                    super.setEnabled(enabled)
                                    confirmEnabled = enabled
                                }
                            }
                            addView(confirmButton)
                            val field = EditText(viewContext).apply {
                                // In the order the library's own dialog binds it
                                setText(draft ?: preference.text)
                                setSelection(text.length)
                                setIncognito(context.appGraph.basePreferences, scope)
                                preference.getOnBindEditTextListener()?.onBindEditText(this)
                                doAfterTextChanged { draft = it?.toString() }
                                // Focus only takes once attached to the dialog's window
                                post { requestFocus() }
                            }
                            addView(field, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            editText = field
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmEnabled,
                onClick = {
                    val value = editText?.text?.toString()
                    if (value != null && preference.callChangeListener(value)) {
                        preference.text = value
                    }
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
