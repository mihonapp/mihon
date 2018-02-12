package exh.uconfig

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import okhttp3.FormBody
import uy.kohesive.injekt.injectLazy

class EhUConfigBuilder {
    private val prefs: PreferencesHelper by injectLazy()

    fun build(hathPerks: EHHathPerksResponse): FormBody {
        val configItems = mutableListOf<ConfigItem>()

        configItems += when(prefs.imageQuality()
                .getOrDefault()
                .toLowerCase()) {
            "ovrs_2400" -> Entry.ImageSize.`2400`
            "ovrs_1600" -> Entry.ImageSize.`1600`
            "high" -> Entry.ImageSize.`1280`
            "med" -> Entry.ImageSize.`980`
            "low" -> Entry.ImageSize.`780`
            "auto" -> Entry.ImageSize.AUTO
            else -> Entry.ImageSize.AUTO
        }

        configItems += if(prefs.useHentaiAtHome().getOrDefault())
            Entry.UseHentaiAtHome.YES
        else
            Entry.UseHentaiAtHome.NO

        configItems += if(prefs.useJapaneseTitle().getOrDefault())
            Entry.TitleDisplayLanguage.JAPANESE
        else
            Entry.TitleDisplayLanguage.DEFAULT

        configItems += if(prefs.eh_useOriginalImages().getOrDefault())
            Entry.UseOriginalImages.YES
        else
            Entry.UseOriginalImages.NO

        configItems += when {
            hathPerks.allThumbs -> Entry.ThumbnailRows.`40`
            hathPerks.thumbsUp -> Entry.ThumbnailRows.`20`
            hathPerks.moreThumbs -> Entry.ThumbnailRows.`10`
            else -> Entry.ThumbnailRows.`4`
        }

        configItems += when {
            hathPerks.pagingEnlargementIII -> Entry.SearchResultsCount.`200`
            hathPerks.pagingEnlargementII -> Entry.SearchResultsCount.`100`
            hathPerks.pagingEnlargementI -> Entry.SearchResultsCount.`50`
            else -> Entry.SearchResultsCount.`25`
        }

        configItems += Entry.DisplayMode()
        configItems += Entry.UseMPV()
        configItems += Entry.ShowPopularRightNowPane()

        //Actually build form body
        val formBody = FormBody.Builder()
        configItems.forEach {
            formBody.add(it.key, it.value)
        }
        formBody.add("apply", "Apply")
        return formBody.build()
    }
}

object Entry {
    enum class UseHentaiAtHome(override val value: String): ConfigItem {
        YES("0"),
        NO("1");

        override val key = "uh"
    }

    enum class ImageSize(override val value: String): ConfigItem {
        AUTO("0"),
        `2400`("5"),
        `1600`("4"),
        `1280`("3"),
        `980`("2"),
        `780`("1");

        override val key = "xr"
    }

    enum class TitleDisplayLanguage(override val value: String): ConfigItem {
        DEFAULT("0"),
        JAPANESE("1");

        override val key = "tl"
    }

    //Locked to list mode as that's what the parser and toplists use
    class DisplayMode: ConfigItem {
        override val key = "dm"
        override val value = "0"
    }

    enum class SearchResultsCount(override val value: String): ConfigItem {
        `25`("0"),
        `50`("1"),
        `100`("2"),
        `200`("3");

        override val key = "rc"
    }

    enum class ThumbnailRows(override val value: String): ConfigItem {
        `4`("0"),
        `10`("1"),
        `20`("2"),
        `40`("3");

        override val key = "tr"
    }

    enum class UseOriginalImages(override val value: String): ConfigItem {
        NO("0"),
        YES("1");

        override val key = "oi"
    }

    //Locked to no MPV as that's what the parser uses
    class UseMPV: ConfigItem {
        override val key = "qb"
        override val value = "0"
    }

    //Locked to no popular pane as we can't parse it
    class ShowPopularRightNowPane: ConfigItem {
        override val key = "pp"
        override val value = "1"
    }
}

interface ConfigItem {
    val key: String
    val value: String
}