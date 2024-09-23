package nekotachi.domain.extensionrepo.interactor

import kotlinx.coroutines.flow.Flow
import nekotachi.domain.extensionrepo.model.ExtensionRepo
import nekotachi.domain.extensionrepo.repository.ExtensionRepoRepository

class GetExtensionRepo(
    private val repository: ExtensionRepoRepository,
) {
    fun subscribeAll(): Flow<List<ExtensionRepo>> = repository.subscribeAll()

    suspend fun getAll(): List<ExtensionRepo> = repository.getAll()
}
