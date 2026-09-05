package mihon.desktop.category

data class DesktopCategory(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long = 0,
)

val SYSTEM_ALL_CATEGORY = DesktopCategory(
    id = -1L,
    name = "All",
    order = -1L,
)
