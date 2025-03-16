package mihon.domain.extensionrepo.exception

import java.io.IOException

/**
 * Exception to abstract over SQLiteException and SQLiteConstraintException for multiplatform.
 *
 * @param throwable the source throwable to include for tracing.
 */
class SaveExtensionRepoException(throwable: Throwable) : IOException("Error Saving Repository to Database", throwable)
