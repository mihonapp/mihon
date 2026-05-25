package eu.kanade.translation.data
import eu.kanade.tachiyomi.R
import tachiyomi.core.common.preference.Preference

enum class TranslationFont(var label: String, var res: Int) {
    ANIME_ACE("Anime Ace", R.font.animeace),
    MANGA_MASTER_BB("Manga Master BB", R.font.manga_master_bb),
    COMIC_FONT("Comic Font", R.font.comic_book),
    ;

    companion object {
        fun fromPref(pref: Preference<Int>): TranslationFont {
            var font = TranslationFont.entries.getOrNull(pref.get())
            if (font == null) {
                pref.set(0)
                return ANIME_ACE
            }
            return font
        }
    }
}
