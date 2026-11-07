package mihon.sync.drive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriveFile(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String = "",
    @SerialName("mimeType") val mimeType: String = "",
    /**
     * Monotonically increasing counter Drive bumps on every change.
     */
    @SerialName("version") val version: String = "",
    /**
     * RFC 3339. Used to decide whether a shard changed since this device last synced.
     */
    @SerialName("modifiedTime") val modifiedTime: String = "",
    @SerialName("size") val size: String = "",
    /**
     * MD5 of the content, lowercase hex. Unlike [version], it only moves when the content does: Drive
     * bumps the version again shortly after an upload, with nothing changed.
     */
    @SerialName("md5Checksum") val md5Checksum: String = "",
)

@Serializable
data class DriveFileList(
    @SerialName("files") val files: List<DriveFile> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null,
)

/**
 * How much of the account's Drive is in use.
 *
 * Sizes arrive as strings because they can exceed what JSON numbers safely hold. [limit] is absent
 * on accounts with unlimited storage.
 */
@Serializable
data class DriveQuota(
    @SerialName("limit") val limit: String? = null,
    @SerialName("usage") val usage: String? = null,
    @SerialName("usageInDrive") val usageInDrive: String? = null,
) {
    val limitBytes: Long? get() = limit?.toLongOrNull()
    val usageBytes: Long? get() = usage?.toLongOrNull()
}

@Serializable
data class DriveAbout(
    @SerialName("storageQuota") val storageQuota: DriveQuota? = null,
)

/**
 * Raised when Drive itself refuses the request (server error, request limit, no connectivity).
 * Distinct from [mihon.sync.auth.SyncAuthRequiredException]: these are worth retrying later.
 */
open class DriveException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The account's storage is full. Retrying cannot help until the user frees space, so this is
 * reported rather than retried.
 */
class DriveStorageFullException(message: String) : DriveException(message)

/**
 * The body Drive answers a failed request with, of which only the reasons matter here.
 */
@Serializable
data class DriveErrorResponse(
    @SerialName("error") val error: DriveError = DriveError(),
)

@Serializable
data class DriveError(
    @SerialName("code") val code: Int = 0,
    @SerialName("errors") val errors: List<DriveErrorReason> = emptyList(),
)

@Serializable
data class DriveErrorReason(
    @SerialName("domain") val domain: String = "",
    @SerialName("reason") val reason: String = "",
)

/**
 * What a failed Drive request means for the sync.
 *
 * Drive answers 403 for very different things: a missing permission, but also a full account or a
 * request limit. Treating every 403 as "link the account again" sent the user to re-link an account
 * that was fine, and gave up on a round that a short wait would have completed.
 */
enum class DriveFailure {
    /** Worth retrying shortly: a request limit, or a server having a bad moment. */
    Transient,

    /** The account is full. */
    StorageFull,

    /** The grant is missing, revoked or lacks the Drive permission: only re-linking helps. */
    Unauthorized,

    /** Anything else: reported, and retried with the next round. */
    Other,
    ;

    companion object {
        private val RATE_LIMITS = setOf("rateLimitExceeded", "userRateLimitExceeded", "sharingRateLimitExceeded")
        private val STORAGE_FULL = setOf("storageQuotaExceeded")
        private val DAILY_LIMITS = setOf("dailyLimitExceeded", "dailyLimitExceededUnreg")

        fun of(code: Int, reasons: List<String>): DriveFailure = when {
            code == 429 || code in 500..599 -> Transient
            code == 401 -> Unauthorized
            code != 403 -> Other
            reasons.any { it in RATE_LIMITS } -> Transient
            reasons.any { it in STORAGE_FULL } -> StorageFull
            // Resets by itself within the day; the next scheduled round will get through.
            reasons.any { it in DAILY_LIMITS } -> Other
            else -> Unauthorized
        }
    }
}
