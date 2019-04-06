package exh.metadata.sql.models

data class SearchTitle(
        // Title identifier, unique
        val id: Long?,

        // Metadata this title is attached to
        val mangaId: Long,

        // Title
        val title: String,

        // Title type, useful for distinguishing between main/alt titles
        val type: Int
)
