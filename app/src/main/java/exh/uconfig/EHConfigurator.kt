package exh.uconfig

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.util.asJsoup
import exh.EH_SOURCE_ID
import exh.EXH_SOURCE_ID
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

class EHConfigurator {
    private val prefs: PreferencesHelper by injectLazy()
    private val sources: SourceManager by injectLazy()

    private val configuratorClient = OkHttpClient.Builder().build()

    private fun EHentai.requestWithCreds(sp: Int = 1) = Request.Builder()
            .addHeader("Cookie", cookiesHeader(sp))

    private fun EHentai.execProfileActions(action: String,
                                           name: String,
                                           set: String,
                                           sp: Int)
        = configuratorClient.newCall(requestWithCreds(sp)
                .url(uconfigUrl)
                .post(FormBody.Builder()
                        .add("profile_action", action)
                        .add("profile_name", name)
                        .add("profile_set", set)
                        .build())
                .build())
                .execute()

    private val EHentai.uconfigUrl get() = baseUrl + UCONFIG_URL

    fun configureAll() {
        val ehSource = sources.get(EH_SOURCE_ID) as EHentai
        val exhSource = sources.get(EXH_SOURCE_ID) as EHentai

        //Get hath perks
        val perksPage = configuratorClient.newCall(ehSource.requestWithCreds()
                .url(HATH_PERKS_URL)
                .build())
                .execute().asJsoup()

        val hathPerks = EHHathPerksResponse()

        perksPage.select(".stuffbox tr").forEach {
            val name = it.child(0).text().toLowerCase()
            val purchased = it.child(2).getElementsByTag("form").isEmpty()

            when(name) {
                //Thumbnail rows
                "more thumbs" -> hathPerks.moreThumbs = purchased
                "thumbs up" -> hathPerks.thumbsUp = purchased
                "all thumbs" -> hathPerks.allThumbs = purchased

                //Pagination sizing
                "paging enlargement i" -> hathPerks.pagingEnlargementI = purchased
                "paging enlargement ii" -> hathPerks.pagingEnlargementII = purchased
                "paging enlargement iii" -> hathPerks.pagingEnlargementIII = purchased
            }
        }

        Timber.d("Hath perks: $hathPerks")

        configure(ehSource, hathPerks)
        configure(exhSource, hathPerks)
    }

    fun configure(source: EHentai, hathPerks: EHHathPerksResponse) {
        //Delete old app profiles
        val scanReq = source.requestWithCreds().url(source.uconfigUrl).build()
        val resp = configuratorClient.newCall(scanReq).execute().asJsoup()
        var lastDoc = resp
        resp.select(PROFILE_SELECTOR).forEach {
            if(it.text() == PROFILE_NAME) {
                val id = it.attr("value")
                //Delete old profile
                lastDoc = source.execProfileActions("delete", "", id, id.toInt()).asJsoup()
            }
        }

        //Find available profile slot
        val availableProfiles = (1 .. 3).toMutableList()
        lastDoc.select(PROFILE_SELECTOR).forEach {
            availableProfiles.remove(it.attr("value").toInt())
        }

        //No profile slots left :(
        if(availableProfiles.isEmpty())
            throw IllegalStateException("You are out of profile slots on ${source.name}, please delete a profile!")

        //Create profile in available slot
        val slot = availableProfiles.first()
        val response = source.execProfileActions("create",
                PROFILE_NAME,
                slot.toString(),
                1)

        //Build new profile
        val form = EhUConfigBuilder().build(hathPerks)

        //Send new profile to server
        configuratorClient.newCall(source.requestWithCreds(sp = slot)
                .url(source.uconfigUrl)
                .post(form)
                .build()).execute()

        //Persist slot + sk
        source.spPref().set(slot)

        val keyCookie = response.headers().toMultimap()["Set-Cookie"]?.find {
            it.startsWith("sk=")
        }?.removePrefix("sk=")?.substringBefore(';')
        val sessionCookie = response.headers().toMultimap()["Set-Cookie"]?.find {
            it.startsWith("s=")
        }?.removePrefix("s=")?.substringBefore(';')
        val hathPerksCookie = response.headers().toMultimap()["Set-Cookie"]?.find {
            it.startsWith("hath_perks=")
        }?.removePrefix("hath_perks=")?.substringBefore(';')

        if(keyCookie != null)
            prefs.eh_settingsKey().set(keyCookie)
        if(sessionCookie != null)
            prefs.eh_sessionCookie().set(sessionCookie)
        if(hathPerksCookie != null)
            prefs.eh_hathPerksCookies().set(hathPerksCookie)
    }

    companion object {
        private const val PROFILE_NAME = "TachiyomiEH App"
        private const val UCONFIG_URL = "/uconfig.php"
        //Always use E-H here as EXH does not have a perks page
        private const val HATH_PERKS_URL = "https://e-hentai.org/hathperks.php"
        private const val PROFILE_SELECTOR = "[name=profile_set] > option"
    }
}