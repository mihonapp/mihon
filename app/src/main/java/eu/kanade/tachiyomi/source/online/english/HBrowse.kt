package eu.kanade.tachiyomi.source.online.english

import android.net.Uri
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import com.lvla.rxjava.interopkt.toV1Single
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.HBROWSE_SOURCE_ID
import exh.metadata.metadata.HBrowseSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.search.Namespace
import exh.search.SearchEngine
import exh.search.Text
import exh.util.await
import exh.util.dropBlank
import exh.util.urlImportFetchSearchManga
import info.debatty.java.stringsimilarity.Levenshtein
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.rx2.asSingle
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import rx.schedulers.Schedulers
import kotlin.math.ceil

class HBrowse : HttpSource(), LewdSource<HBrowseSearchMetadata, Document>, UrlImportableSource {
    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang: String = "en"
    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    override val baseUrl = HBrowseSearchMetadata.BASE_URL

    override val name: String = "HBrowse"

    override val supportsLatest = true

    override val metaClass = HBrowseSearchMetadata::class

    override val id: Long = HBROWSE_SOURCE_ID

    override fun headersBuilder() = Headers.Builder()
            .add("Cookie", BASE_COOKIES)

    private val clientWithoutCookies = client.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .build()

    private val nonRedirectingClientWithoutCookies = clientWithoutCookies.newBuilder()
            .followRedirects(false)
            .build()

    private val searchEngine = SearchEngine()

    /**
     * Returns the request for the popular manga given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun popularMangaRequest(page: Int)
            = GET("$baseUrl/browse/title/rank/DESC/$page", headers)

    private fun parseListing(response: Response): MangasPage {
        val doc = response.asJsoup()
        val main = doc.selectFirst("#main")
        val items = main.select(".thumbTable > tbody")
        val manga = items.map { mangaEle ->
            SManga.create().apply {
                val thumbElement = mangaEle.selectFirst(".thumbImg")
                url = "/" + thumbElement.parent().attr("href").split("/").dropBlank().first()
                title = thumbElement.parent().attr("title").substringAfter('\'').substringBeforeLast('\'')
                thumbnail_url = baseUrl + thumbElement.attr("src")
            }
        }

        val hasNextPage = doc.selectFirst("#main > p > a[title~=jump]:nth-last-child(1)") != null
        return MangasPage(
                manga,
                hasNextPage
        )
    }

    /**
     * Returns an observable containing a page with a list of manga. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return urlImportFetchSearchManga(query) {
            fetchSearchMangaInternal(page, query, filters)
        }
    }

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun popularMangaParse(response: Response) = parseListing(response)

    /**
     * Returns the request for the search manga given the page.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList)
            = throw UnsupportedOperationException("Should not be called!")

    private fun fetchSearchMangaInternal(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return GlobalScope.async(Dispatchers.IO) {
            val modeFilter = filters.filterIsInstance<ModeFilter>().firstOrNull()
            val sortFilter = filters.filterIsInstance<SortFilter>().firstOrNull()

            var base: String? = null
            var isSortFilter = false
            // <NS, VALUE, EXCLUDED>
            var tagQuery: List<Triple<String, String, Boolean>>? = null

            if(sortFilter != null) {
                sortFilter.state?.let { state ->
                    if(query.isNotBlank()) {
                        throw IllegalArgumentException("Cannot use sorting while text/tag search is active!")
                    }

                    isSortFilter = true
                    base = "/browse/title/${SortFilter.SORT_OPTIONS[state.index].first}/${if(state.ascending) "ASC" else "DESC"}"
                }
            }

            if(base == null) {
                base = if(modeFilter != null && modeFilter.state == 1) {
                    tagQuery = searchEngine.parseQuery(query, false).map {
                        when (it) {
                            is Text -> {
                                var minDist = Int.MAX_VALUE.toDouble()
                                // ns, value
                                var minContent: Pair<String, String> = "" to ""
                                for(ns in ALL_TAGS) {
                                    val (v, d) = ns.value.nearest(it.rawTextOnly(), minDist)
                                    if(d < minDist) {
                                        minDist = d
                                        minContent = ns.key to v
                                    }
                                }
                                minContent
                            }
                            is Namespace -> {
                                // Map ns aliases
                                val mappedNs = NS_MAPPINGS[it.namespace] ?: it.namespace

                                var key = mappedNs
                                if(!ALL_TAGS.containsKey(key)) key = ALL_TAGS.keys.sorted().nearest(mappedNs).first

                                // Find nearest NS
                                val nsContents = ALL_TAGS[key]

                                key to nsContents!!.nearest(it.tag?.rawTextOnly() ?: "").first
                            }
                            else -> error("Unknown type!")
                        }.let { p ->
                            Triple(p.first, p.second, it.excluded)
                        }
                    }


                    "/result"
                } else {
                    "/search"
                }
            }

            base += "/$page"

            if(isSortFilter) {
                parseListing(client.newCall(GET(baseUrl + base, headers))
                        .asObservableSuccess()
                        .toSingle()
                        .await(Schedulers.io()))
            } else {
                val body = if(tagQuery != null) {
                    FormBody.Builder()
                            .add("type", "advance")
                            .apply {
                                tagQuery.forEach {
                                    add(it.first + "_" + it.second, if(it.third) "n" else "y")
                                }
                            }
                } else {
                    FormBody.Builder()
                            .add("type", "search")
                            .add("needle", query)
                }
                val processRequest = POST(
                        "$baseUrl/content/process.php",
                        headers,
                        body = body.build()
                )
                val processResponse = nonRedirectingClientWithoutCookies.newCall(processRequest)
                        .asObservable()
                        .toSingle()
                        .await(Schedulers.io())

                if(!processResponse.isRedirect)
                    throw IllegalStateException("Unexpected process response code!")

                val sessId = processResponse.headers("Set-Cookie").find {
                    it.startsWith("PHPSESSID")
                } ?: throw IllegalStateException("Missing server session cookie!")

                val response = clientWithoutCookies.newCall(GET(baseUrl + base,
                        headersBuilder()
                                .set("Cookie", BASE_COOKIES + " " + sessId.substringBefore(';'))
                                .build()))
                        .asObservableSuccess()
                        .toSingle()
                        .await(Schedulers.io())

                val doc = response.asJsoup()
                val manga = doc.select(".browseDescription").map {
                    SManga.create().apply {
                        val first = it.child(0)
                        url = first.attr("href")
                        title = first.attr("title").substringAfter('\'').removeSuffix("'").replace('_', ' ')
                        thumbnail_url = HBrowseSearchMetadata.guessThumbnailUrl(url.substring(1))
                    }
                }
                val hasNextPage = doc.selectFirst("#main > p > a[title~=jump]:nth-last-child(1)") != null
                MangasPage(
                        manga,
                        hasNextPage
                )
            }
        }.asSingle(GlobalScope.coroutineContext).toV1Single().toObservable()
    }

    // Collection must be sorted and cannot be sorted
    private fun List<String>.nearest(string: String, maxDist: Double = Int.MAX_VALUE.toDouble()): Pair<String, Double> {
        val idx = binarySearch(string)
        return if(idx < 0) {
            val l = Levenshtein()
            var minSoFar = maxDist
            var minIndexSoFar = 0
            forEachIndexed { index, s ->
                val d = l.distance(string, s, ceil(minSoFar).toInt())
                if(d < minSoFar) {
                    minSoFar = d
                    minIndexSoFar = index
                }
            }
            get(minIndexSoFar) to minSoFar
        } else {
            get(idx) to 0.0
        }
    }

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun searchMangaParse(response: Response) = parseListing(response)

    /**
     * Returns the request for latest manga given the page.
     *
     * @param page the page number to retrieve.
     */
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/browse/title/date/DESC/$page", headers)

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     *
     * @param response the response from the site.
     */
    override fun latestUpdatesParse(response: Response) = parseListing(response)

    /**
     * Parses the response from the site and returns the details of a manga.
     *
     * @param response the response from the site.
     */
    override fun mangaDetailsParse(response: Response): SManga {
        throw UnsupportedOperationException("Should not be called!")
    }

    override fun parseIntoMetadata(metadata: HBrowseSearchMetadata, input: Document) {
        val tables = parseIntoTables(input)
        with(metadata) {
            hbId = Uri.parse(input.location()).pathSegments.first().toLong()

            tags.clear()
            (tables[""]!! + tables["categories"]!!).forEach { (k, v) ->
                when(val lowercaseNs = k.toLowerCase()) {
                    "title" -> title = v.text()
                    "length" -> length = v.text().substringBefore(" ").toInt()
                    else -> {
                        v.getElementsByTag("a").forEach {
                            tags += RaisedTag(
                                    lowercaseNs,
                                    it.text(),
                                    HBrowseSearchMetadata.TAG_TYPE_DEFAULT
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns an observable with the updated details for a manga. Normally it's not needed to
     * override this method.
     *
     * @param manga the manga to be updated.
     */
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
                .asObservableSuccess()
                .flatMap {
                    parseToManga(manga, it.asJsoup()).andThen(Observable.just(manga))
                }
    }

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * @param response the response from the site.
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        return parseIntoTables(response.asJsoup())["read manga online"]?.map { (key, value) ->
            SChapter.create().apply {
                url = value.selectFirst(".listLink").attr("href")

                name = key
            }
        } ?: emptyList()
    }

    private fun parseIntoTables(doc: Document): Map<String, Map<String, Element>> {
        return doc.select("#main > .listTable").map { ele ->
            val tableName = ele.previousElementSibling()?.text()?.toLowerCase() ?: ""
            tableName to ele.select("tr").map {
                it.child(0).text() to it.child(1)
            }.toMap()
        }.toMap()
    }

    /**
     * Parses the response from the site and returns a list of pages.
     *
     * @param response the response from the site.
     */
    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        val basePath = listOf("data") + response.request().url().pathSegments()
        val scripts = doc.getElementsByTag("script").map { it.data() }
        for(script in scripts) {
            val totalPages = TOTAL_PAGES_REGEX.find(script)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
            val pageList = PAGE_LIST_REGEX.find(script)?.groupValues?.getOrNull(1) ?: continue

            return jsonParser.parse(pageList).array.take(totalPages).map {
                it.string
            }.mapIndexed { index, pageName ->
                Page(
                        index,
                        pageName,
                        "$baseUrl/${basePath.joinToString("/")}/$pageName"
                )
            }
        }

        return emptyList()
    }

    class HelpFilter : Filter.HelpDialog("Usage instructions", markdown = """
        ### Modes
        There are three available filter modes:
        - Text search
        - Tag search
        - Sort mode
        
        You can only use a single mode at a time. Switch between the text and tag search modes using the dropdown menu. Switch to sorting mode by selecting a sorting option.
        
        ### Text search
        Search for galleries by title, artist or origin.
        
        ### Tag search
        Search for galleries by tag (e.g. search for a specific genre, type, setting, etc). Uses nhentai/e-hentai syntax. Refer to the "Search" section on [this page](https://nhentai.net/info/) for more information.
        
        ### Sort mode
        View a list of all galleries sorted by a specific parameter. Exit sorting mode by resetting the filters using the reset button near the bottom of the screen.
        
        ### Tag list
    """.trimIndent() + "\n$TAGS_AS_MARKDOWN")

    class ModeFilter : Filter.Select<String>("Mode", arrayOf(
            "Text search",
            "Tag search"
    ))

    class SortFilter : Filter.Sort("Sort", SORT_OPTIONS.map { it.second }.toTypedArray()) {
        companion object {
            // internal to display
            val SORT_OPTIONS = listOf(
                    "length" to "Length",
                    "date" to "Date added",
                    "rank" to "Rank"
            )
        }
    }

    override fun getFilterList() = FilterList(
            HelpFilter(),
            ModeFilter(),
            SortFilter()
    )

    /**
     * Parses the response from the site and returns the absolute url to the source image.
     *
     * @param response the response from the site.
     */
    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Should not be called!")
    }

    override val matchingHosts = listOf(
            "www.hbrowse.com",
            "hbrowse.com"
    )

    override fun mapUrlToMangaUrl(uri: Uri): String? {
        return "$baseUrl/${uri.pathSegments.first()}"
    }

    companion object {
        private val PAGE_LIST_REGEX = Regex("list *= *(\\[.*]);")
        private val TOTAL_PAGES_REGEX = Regex("totalPages *= *([0-9]*);")

        private val jsonParser by lazy { JsonParser() }

        private const val BASE_COOKIES = "thumbnails=1;"

        private val NS_MAPPINGS = mapOf(
                "set" to "setting",
                "loc" to "setting",
                "location" to "setting",
                "fet" to "fetish",
                "relation" to "relationship",
                "male" to "malebody",
                "female" to "femalebody",
                "pos" to "position"
        )

        private val ALL_TAGS = mapOf(
                "genre" to listOf(
                        "action",
                        "adventure",
                        "anime",
                        "bizarre",
                        "comedy",
                        "drama",
                        "fantasy",
                        "gore",
                        "historic",
                        "horror",
                        "medieval",
                        "modern",
                        "myth",
                        "psychological",
                        "romance",
                        "school_life",
                        "scifi",
                        "supernatural",
                        "video_game",
                        "visual_novel"
                ),
                "type" to listOf(
                        "anthology",
                        "bestiality",
                        "dandere",
                        "deredere",
                        "deviant",
                        "fully_colored",
                        "furry",
                        "futanari",
                        "gender_bender",
                        "guro",
                        "harem",
                        "incest",
                        "kuudere",
                        "lolicon",
                        "long_story",
                        "netorare",
                        "non-con",
                        "partly_colored",
                        "reverse_harem",
                        "ryona",
                        "short_story",
                        "shotacon",
                        "transgender",
                        "tsundere",
                        "uncensored",
                        "vanilla",
                        "yandere",
                        "yaoi",
                        "yuri"
                ),
                "setting" to listOf(
                        "amusement_park",
                        "attic",
                        "automobile",
                        "balcony",
                        "basement",
                        "bath",
                        "beach",
                        "bedroom",
                        "cabin",
                        "castle",
                        "cave",
                        "church",
                        "classroom",
                        "deck",
                        "dining_room",
                        "doctors",
                        "dojo",
                        "doorway",
                        "dream",
                        "dressing_room",
                        "dungeon",
                        "elevator",
                        "festival",
                        "gym",
                        "haunted_building",
                        "hospital",
                        "hotel",
                        "hot_springs",
                        "kitchen",
                        "laboratory",
                        "library",
                        "living_room",
                        "locker_room",
                        "mansion",
                        "office",
                        "other",
                        "outdoor",
                        "outer_space",
                        "park",
                        "pool",
                        "prison",
                        "public",
                        "restaurant",
                        "restroom",
                        "roof",
                        "sauna",
                        "school",
                        "school_nurses_office",
                        "shower",
                        "shrine",
                        "storage_room",
                        "store",
                        "street",
                        "teachers_lounge",
                        "theater",
                        "tight_space",
                        "toilet",
                        "train",
                        "transit",
                        "virtual_reality",
                        "warehouse",
                        "wilderness"
                ),
                "fetish" to listOf(
                        "androphobia",
                        "apron",
                        "assertive_girl",
                        "bikini",
                        "bloomers",
                        "breast_expansion",
                        "business_suit",
                        "chastity_device",
                        "chinese_dress",
                        "christmas",
                        "collar",
                        "corset",
                        "cosplay_(female)",
                        "cosplay_(male)",
                        "crossdressing_(female)",
                        "crossdressing_(male)",
                        "eye_patch",
                        "food",
                        "giantess",
                        "glasses",
                        "gothic_lolita",
                        "gyaru",
                        "gynophobia",
                        "high_heels",
                        "hot_pants",
                        "impregnation",
                        "kemonomimi",
                        "kimono",
                        "knee_high_socks",
                        "lab_coat",
                        "latex",
                        "leotard",
                        "lingerie",
                        "maid_outfit",
                        "mother_and_daughter",
                        "none",
                        "nonhuman_girl",
                        "olfactophilia",
                        "pregnant",
                        "rich_girl",
                        "school_swimsuit",
                        "shy_girl",
                        "sisters",
                        "sleeping_girl",
                        "sporty",
                        "stockings",
                        "strapon",
                        "student_uniform",
                        "swimsuit",
                        "tanned",
                        "tattoo",
                        "time_stop",
                        "twins_(coed)",
                        "twins_(female)",
                        "twins_(male)",
                        "uniform",
                        "wedding_dress"
                ),
                "role" to listOf(
                        "alien",
                        "android",
                        "angel",
                        "athlete",
                        "bride",
                        "bunnygirl",
                        "cheerleader",
                        "delinquent",
                        "demon",
                        "doctor",
                        "dominatrix",
                        "escort",
                        "foreigner",
                        "ghost",
                        "housewife",
                        "idol",
                        "magical_girl",
                        "maid",
                        "mamono",
                        "massagist",
                        "miko",
                        "mythical_being",
                        "neet",
                        "nekomimi",
                        "newlywed",
                        "ninja",
                        "normal",
                        "nun",
                        "nurse",
                        "office_lady",
                        "other",
                        "police",
                        "priest",
                        "princess",
                        "queen",
                        "school_nurse",
                        "scientist",
                        "sorcerer",
                        "student",
                        "succubus",
                        "teacher",
                        "tomboy",
                        "tutor",
                        "waitress",
                        "warrior",
                        "witch"
                ),
                "relationship" to listOf(
                        "acquaintance",
                        "anothers_daughter",
                        "anothers_girlfriend",
                        "anothers_mother",
                        "anothers_sister",
                        "anothers_wife",
                        "aunt",
                        "babysitter",
                        "childhood_friend",
                        "classmate",
                        "cousin",
                        "customer",
                        "daughter",
                        "daughter-in-law",
                        "employee",
                        "employer",
                        "enemy",
                        "fiance",
                        "friend",
                        "friends_daughter",
                        "friends_girlfriend",
                        "friends_mother",
                        "friends_sister",
                        "friends_wife",
                        "girlfriend",
                        "landlord",
                        "manager",
                        "master",
                        "mother",
                        "mother-in-law",
                        "neighbor",
                        "niece",
                        "none",
                        "older_sister",
                        "patient",
                        "pet",
                        "physician",
                        "relative",
                        "relatives_friend",
                        "relatives_girlfriend",
                        "relatives_wife",
                        "servant",
                        "server",
                        "sister-in-law",
                        "slave",
                        "stepdaughter",
                        "stepmother",
                        "stepsister",
                        "stranger",
                        "student",
                        "teacher",
                        "tutee",
                        "tutor",
                        "twin",
                        "underclassman",
                        "upperclassman",
                        "wife",
                        "workmate",
                        "younger_sister"
                ),
                "maleBody" to listOf(
                        "adult",
                        "animal",
                        "animal_ears",
                        "bald",
                        "beard",
                        "dark_skin",
                        "elderly",
                        "exaggerated_penis",
                        "fat",
                        "furry",
                        "goatee",
                        "hairy",
                        "half_animal",
                        "horns",
                        "large_penis",
                        "long_hair",
                        "middle_age",
                        "monster",
                        "muscular",
                        "mustache",
                        "none",
                        "short",
                        "short_hair",
                        "skinny",
                        "small_penis",
                        "tail",
                        "tall",
                        "tanned",
                        "tan_line",
                        "teenager",
                        "wings",
                        "young"
                ),
                "femaleBody" to listOf(
                        "adult",
                        "animal_ears",
                        "bald",
                        "big_butt",
                        "chubby",
                        "dark_skin",
                        "elderly",
                        "elf_ears",
                        "exaggerated_breasts",
                        "fat",
                        "furry",
                        "hairy",
                        "hair_bun",
                        "half_animal",
                        "halo",
                        "hime_cut",
                        "horns",
                        "large_breasts",
                        "long_hair",
                        "middle_age",
                        "monster_girl",
                        "muscular",
                        "none",
                        "pigtails",
                        "ponytail",
                        "short",
                        "short_hair",
                        "skinny",
                        "small_breasts",
                        "tail",
                        "tall",
                        "tanned",
                        "tan_line",
                        "teenager",
                        "twintails",
                        "wings",
                        "young"
                ),
                "grouping" to listOf(
                        "foursome_(1_female)",
                        "foursome_(1_male)",
                        "foursome_(mixed)",
                        "foursome_(only_female)",
                        "one_on_one",
                        "one_on_one_(2_females)",
                        "one_on_one_(2_males)",
                        "orgy_(1_female)",
                        "orgy_(1_male)",
                        "orgy_(mainly_female)",
                        "orgy_(mainly_male)",
                        "orgy_(mixed)",
                        "orgy_(only_female)",
                        "orgy_(only_male)",
                        "solo_(female)",
                        "solo_(male)",
                        "threesome_(1_female)",
                        "threesome_(1_male)",
                        "threesome_(only_female)",
                        "threesome_(only_male)"
                ),
                "scene" to listOf(
                        "adultery",
                        "ahegao",
                        "anal_(female)",
                        "anal_(male)",
                        "aphrodisiac",
                        "armpit_sex",
                        "asphyxiation",
                        "blackmail",
                        "blowjob",
                        "bondage",
                        "breast_feeding",
                        "breast_sucking",
                        "bukkake",
                        "cheating_(female)",
                        "cheating_(male)",
                        "chikan",
                        "clothed_sex",
                        "consensual",
                        "cunnilingus",
                        "defloration",
                        "discipline",
                        "dominance",
                        "double_penetration",
                        "drunk",
                        "enema",
                        "exhibitionism",
                        "facesitting",
                        "fingering_(female)",
                        "fingering_(male)",
                        "fisting",
                        "footjob",
                        "grinding",
                        "groping",
                        "handjob",
                        "humiliation",
                        "hypnosis",
                        "intercrural",
                        "interracial_sex",
                        "interspecies_sex",
                        "lactation",
                        "lotion",
                        "masochism",
                        "masturbation",
                        "mind_break",
                        "nonhuman",
                        "orgy",
                        "paizuri",
                        "phone_sex",
                        "props",
                        "rape",
                        "reverse_rape",
                        "rimjob",
                        "sadism",
                        "scat",
                        "sex_toys",
                        "spanking",
                        "squirt",
                        "submission",
                        "sumata",
                        "swingers",
                        "tentacles",
                        "voyeurism",
                        "watersports",
                        "x-ray_blowjob",
                        "x-ray_sex"
                ),
                "position" to listOf(
                        "69",
                        "acrobat",
                        "arch",
                        "bodyguard",
                        "butterfly",
                        "cowgirl",
                        "dancer",
                        "deck_chair",
                        "deep_stick",
                        "doggy",
                        "drill",
                        "ex_sex",
                        "jockey",
                        "lap_dance",
                        "leg_glider",
                        "lotus",
                        "mastery",
                        "missionary",
                        "none",
                        "other",
                        "pile_driver",
                        "prison_guard",
                        "reverse_piggyback",
                        "rodeo",
                        "spoons",
                        "standing",
                        "teaspoons",
                        "unusual",
                        "victory"
                )
        ).mapValues { it.value.sorted() }

        private val TAGS_AS_MARKDOWN = ALL_TAGS.map { (ns, values) ->
            "#### $ns\n" + values.map { "- $it" }.joinToString("\n") }.joinToString("\n\n")
    }
}
