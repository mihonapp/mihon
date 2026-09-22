package mihon.core.metro

import dev.zacsweers.metro.Qualifier

/**
 * Qualifies the single application wide [kotlinx.coroutines.CoroutineScope].
 *
 * It lives for as long as the process does and is backed by a [kotlinx.coroutines.SupervisorJob] on
 * [kotlinx.coroutines.Dispatchers.IO], so a failure in one child never takes the others down. Use it for work that is
 * owned by an application scoped component and must outlive any screen. Work that belongs to a screen, a view model or
 * another cancellable lifecycle keeps its own scope.
 */
@Qualifier
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class AppCoroutineScope
