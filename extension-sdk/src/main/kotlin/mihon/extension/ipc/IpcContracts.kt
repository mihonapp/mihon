package mihon.extension.ipc

import kotlinx.serialization.Serializable

@Serializable
sealed interface IpcMessage {
    val requestId: Long
}

@Serializable
data class IpcRequest(
    override val requestId: Long,
    val command: String,
    val payloadJson: String = "",
) : IpcMessage

@Serializable
data class IpcResponse(
    override val requestId: Long,
    val success: Boolean,
    val payloadJson: String = "",
    val error: String? = null,
) : IpcMessage

@Serializable
data class IpcCallbackRequest(
    override val requestId: Long,
    val callbackType: String,
    val payloadJson: String = "",
) : IpcMessage

@Serializable
data class IpcCallbackResponse(
    override val requestId: Long,
    val success: Boolean,
    val payloadJson: String = "",
    val error: String? = null,
) : IpcMessage

object IpcCommands {
    const val PING = "ping"
    const val LOAD_EXTENSION = "load_extension"
    const val GET_SOURCES = "get_sources"
    const val GET_POPULAR = "get_popular"
    const val GET_LATEST = "get_latest"
    const val SEARCH_MANGA = "search_manga"
    const val GET_MANGA_DETAILS = "get_manga_details"
    const val GET_CHAPTER_LIST = "get_chapter_list"
    const val GET_PAGE_LIST = "get_page_list"
    const val GET_IMAGE = "get_image"
    const val GET_FILTER_LIST = "get_filter_list"
    const val GET_SOURCE_PREFERENCES = "get_source_preferences"
    const val SET_SOURCE_PREFERENCE = "set_source_preference"

    /** Alias for [GET_SOURCE_PREFERENCES] matching "list" terminology. */
    const val LIST_SOURCE_PREFERENCES = GET_SOURCE_PREFERENCES
}

object IpcCallbacks {
    const val BROKER_HTTP = "broker_http"
}

@Serializable
data class LoadExtensionPayload(
    val packagePath: String,
    val workingDir: String,
    val grantedCapabilities: List<String> = emptyList(),
)

@Serializable
data class SourcePayload(
    val sourceId: Long,
)

@Serializable
data class GetPagePayload(
    val sourceId: Long,
    val page: Int,
)

@Serializable
data class SearchPayload(
    val sourceId: Long,
    val page: Int,
    val query: String,
    val filtersJson: String = "",
)

@Serializable
data class MangaPayload(
    val sourceId: Long,
    val mangaJson: String,
)

@Serializable
data class ChapterPayload(
    val sourceId: Long,
    val chapterJson: String,
)

@Serializable
data class ImagePayload(val sourceId: Long, val page: mihon.extension.source.model.Page)

/** Images use a host-owned temporary file so binary data never exceeds the IPC frame limit. */
@Serializable
data class ImageFilePayload(val fileName: String? = null)

@Serializable
data class BrokerHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

@Serializable
data class BrokerHttpResponse(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val error: String? = null,
)
