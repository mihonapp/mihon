package mihon.domain.extension.model

data class ExtensionStore(
    val indexUrl: String,
    val name: String,
    val badgeLabel: String,
    val signingKey: String,
    val contact: Contact,
    val isLegacy: Boolean,
    val extensionListUrl: String?,
) {
    data class Contact(
        val website: String,
        val discord: String?,
    )
}
