package eu.kanade.presentation.more.settings.screen.data

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.update
import tachiyomi.domain.sync.SyncPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SyncOptionsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { SyncOptionsScreenModel() }
        val state by model.state.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_sync_options),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = MaterialTheme.padding.medium),
                ) {
                    SyncChoices.forEach { (k, v) ->
                        item {
                            LabeledCheckbox(
                                label = stringResource(v),
                                checked = state.flags.contains(k),
                                onCheckedChange = {
                                    model.toggleOptionFlag(k)
                                },
                            )
                        }
                    }
                }

                HorizontalDivider()
            }
        }
    }
}

private class SyncOptionsScreenModel : StateScreenModel<SyncOptionsScreenModel.State>(State()) {
    private val syncPreferences = Injekt.get<SyncPreferences>()

    init {
        loadInitialFlags()
    }

    private fun loadInitialFlags() {
        val savedFlags = syncPreferences.syncFlags().get()
        val flagSet = SyncPreferences.Flags.values().filter { flag ->
            savedFlags and flag > 0
        }.toSet().toPersistentSet()

        mutableState.update { State(flags = flagSet) }
    }

    fun toggleOptionFlag(option: Int) {
        mutableState.update { currentState ->
            val newFlags = if (currentState.flags.contains(option)) {
                currentState.flags - option
            } else {
                currentState.flags + option
            }
            saveFlags(newFlags)
            currentState.copy(flags = newFlags)
        }
    }

    private fun saveFlags(flags: PersistentSet<Int>) {
        val flagsInt = flags.fold(0) { acc, flag -> acc or flag }
        syncPreferences.syncFlags().set(flagsInt)
    }

    @Immutable
    data class State(
        val flags: PersistentSet<Int> = SyncChoices.keys.toPersistentSet(),
    )
}

private val SyncChoices = mapOf(
    SyncPreferences.Flags.SYNC_ON_CHAPTER_READ to MR.strings.sync_on_chapter_read,
    SyncPreferences.Flags.SYNC_ON_CHAPTER_OPEN to MR.strings.sync_on_chapter_open,
    SyncPreferences.Flags.SYNC_ON_APP_START to MR.strings.sync_on_app_start,
    SyncPreferences.Flags.SYNC_ON_APP_RESUME to MR.strings.sync_on_app_resume,
    SyncPreferences.Flags.SYNC_ON_LIBRARY_UPDATE to MR.strings.sync_on_library_update,
)
