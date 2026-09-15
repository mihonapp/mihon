package mihon.extension.model

import kotlinx.serialization.Serializable

@Serializable
data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val libVersion: Double,
    val lang: String,
    val isNsfw: Boolean = false,
    val sources: List<SourceDescriptor>,
    val declaredDomains: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
)

@Serializable
data class SourceDescriptor(
    val id: Long,
    val name: String,
    val lang: String,
    val className: String,
    val supportsLatest: Boolean = true,
    val baseUrl: String? = null,
)
