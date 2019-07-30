package exh.source

import exh.MERGED_SOURCE_ID

object BlacklistedSources {
    val NHENTAI_EXT_SOURCES = listOf(3122156392225024195)
    val PERVEDEN_EN_EXT_SOURCES = listOf(4673633799850248749)
    val PERVEDEN_IT_EXT_SOURCES = listOf(1433898225963724122)
    val EHENTAI_EXT_SOURCES = listOf(
            8100626124886895451,
            57122881048805941,
            4678440076103929247,
            1876021963378735852,
            3955189842350477641,
            4348288691341764259,
            773611868725221145,
            5759417018342755550,
            825187715438990384,
            6116711405602166104,
            7151438547982231541,
            2171445159732592630,
            3032959619549451093,
            5980349886941016589,
            6073266008352078708,
            5499077866612745456,
            6140480779421365791
    )

    val BLACKLISTED_EXT_SOURCES = NHENTAI_EXT_SOURCES +
            PERVEDEN_EN_EXT_SOURCES +
            PERVEDEN_IT_EXT_SOURCES +
            EHENTAI_EXT_SOURCES

    val BLACKLISTED_EXTENSIONS = listOf(
            "eu.kanade.tachiyomi.extension.all.ehentai",
            "eu.kanade.tachiyomi.extension.all.nhentai",
            "eu.kanade.tachiyomi.extension.en.perveden",
            "eu.kanade.tachiyomi.extension.it.perveden"
    )

    val HIDDEN_SOURCES = listOf(
            MERGED_SOURCE_ID
    )
}