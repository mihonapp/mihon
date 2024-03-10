package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.extension.interactor.CreateExtensionRepo
import eu.kanade.domain.extension.interactor.DeleteExtensionRepo
import eu.kanade.domain.extension.interactor.GetExtensionRepos
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mihon.domain.extensionrepo.interactor.GetExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class ExtensionReposScreenModel(
    private val getExtensionRepo: GetExtensionRepo = Injekt.get(),
    private val getExtensionRepos: GetExtensionRepos = Injekt.get(),
    private val createExtensionRepo: CreateExtensionRepo = Injekt.get(),
    private val deleteExtensionRepo: DeleteExtensionRepo = Injekt.get(),
) : StateScreenModel<RepoScreenState>(RepoScreenState.Loading) {

    private val _events: Channel<RepoEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    private val json: Json by injectLazy()
    val networkService: NetworkHelper by injectLazy()

    val client: OkHttpClient
        get() = networkService.client

    init {
        screenModelScope.launchIO {

            getExtensionRepo.subscribeAll()
                .collectLatest { repos ->
                    mutableState.update {
                        RepoScreenState.Success(
                            repos = repos.map { it.baseUrl }.toImmutableSet(),
                        )
                    }
                }

            getExtensionRepos.subscribe()
                .collectLatest { repos ->
                    for (repo in repos) {
                        val extRepo = fetchRepoDetails(repo)
                        if (extRepo != null) {
                            createExtensionRepo
                            deleteExtensionRepo.await(repo)
                            _events.send(RepoEvent.MigrationSuccessful)
                        } else {
                            _events.send(RepoEvent.MigrationFailed)
                        }
                    }
//
//                    mutableState.update {
//                        RepoScreenState.Success(
//                            repos = repos.toImmutableSet(),
//                        )
//                    }
                }
        }
    }

    private suspend fun fetchRepoDetails(repo: String): ExtensionRepo? {
        return withIOContext {
            val url = "${repo}/repo.json".toUri()
            with(json) {
                client.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        it["meta"]
                            ?.jsonObject
                            ?.let { it1 -> jsonToExtensionRepo(baseUrl = repo, it1) }
                    }
            }
        }
    }

    private fun jsonToExtensionRepo(baseUrl: String, obj: JsonObject): ExtensionRepo? {
        return try {
            ExtensionRepo(
                baseUrl = baseUrl,
                name = obj["name"]!!.toString(),
                shortName = obj["shortName"]?.toString(),
                website = obj["website"]!!.toString(),
                fingerprint = obj["signingKeyFingerprint"]!!.toString(),
            )
        } catch (_: NullPointerException) {
            null
        }
    }

    /**
     * Creates and adds a new repo to the database.
     *
     * @param name The name of the repo to create.
     */
    fun createRepo(name: String) {
        screenModelScope.launchIO {
            when (createExtensionRepo.await(name)) {
                is CreateExtensionRepo.Result.InvalidUrl -> _events.send(RepoEvent.InvalidUrl)
                else -> {}
            }
        }
    }

    /**
     * Deletes the given repo from the database.
     *
     * @param repo The repo to delete.
     */
    fun deleteRepo(repo: String) {
        screenModelScope.launchIO {
            deleteExtensionRepo.await(repo)
        }
    }

    fun showDialog(dialog: RepoDialog) {
        mutableState.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed class RepoEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : RepoEvent()
    data object InvalidUrl : LocalizedMessage(MR.strings.invalid_repo_name)
    data object MigrationFailed : LocalizedMessage(MR.strings.repo_migrate_failure)
    data object MigrationSuccessful : LocalizedMessage(MR.strings.repo_migrate_success)
}

sealed class RepoDialog {
    data object Create : RepoDialog()
    data class Delete(val repo: String) : RepoDialog()
}

sealed class RepoScreenState {

    @Immutable
    data object Loading : RepoScreenState()

    @Immutable
    data class Success(
        val repos: ImmutableSet<String>,
        val dialog: RepoDialog? = null,
    ) : RepoScreenState() {

        val isEmpty: Boolean
            get() = repos.isEmpty()
    }
}
