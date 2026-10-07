package mihon.data.database

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import mihon.domain.database.repository.DatabaseRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DatabaseRepositoryImpl : DatabaseRepository {

    override val isMigrating = MutableStateFlow(false)
}
