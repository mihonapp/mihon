package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln

internal object RecommendationMetadata {
    private val creatorSeparator = Regex("""\s*(?:,|;|\n|\u3001|\uFF0C|\uFF1B)\s*""")
    private val explicitCirclePrefix = Regex(
        """^(?:circle|group|studio|collective|社团|社團|サークル|グループ|同人サークル|서클|그룹|동인서클|círculo|grupo|cercle|gruppe|gruppo|группа)\s*[:：]\s*""",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val punctuationOrSpace = Regex("""[\p{P}\p{S}\s]+""")
    private val absoluteUrlPrefix = Regex("""^(?:(https?):)?//([^/?#]*)""", RegexOption.IGNORE_CASE)
    private val genreSeparator = Regex("""\s*(?:,|;|\r?\n|\t|\u3001|\uFF0C|\uFF1B|\||/|\uFF0F|•)\s*""")
    private val whitespace = Regex("""\s+""")
    private val structuredTagLine = Regex(
        """(?im)^\s*[•▪●·・*\-]?\s*([a-z][a-z _-]{1,24})\s*:\s*((?:<[^<>\r\n]{1,100}>\s*)+)\s*$""",
    )
    private val structuredTagValue = Regex("""<([^<>\r\n]{1,100})>""")
    private val structuredContentNamespaces = setOf(
        "parody",
        "character",
        "female",
        "male",
        "mixed",
        "other",
    )
    private val typedGenrePrefix = Regex(
        """^(?:genre|tag|type|category|类型|類型|标签|標籤|题材|題材|分类|分類|ジャンル|タグ|カテゴリ|カテゴリー|分類|種類|장르|태그|카테고리|género|gênero|genre|жанр)\s*[:：]\s*""",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val explicitSeriesPrefix = Regex(
        """^(?:series|franchise|parody|作品系列|系列|原作|シリーズ|パロディ|시리즈|원작)\s*[:：]\s*(.+)$""",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val editionBracket = Regex(
        """[\[（(【]\s*(?:official|digital|web|webtoon|uncensored|censored|translated|translation|english|chinese|japanese|korean|简体|簡體|繁体|繁體|中文|汉化|漢化|英译|英譯|翻译|翻譯|日文|韩文|韓文|公式|デジタル|翻訳|영어|번역)\s*[]）)】]""",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val trailingEditionMarker = Regex(
        """(?:\s*[-–—:：]\s*)?(?:official|digital|translated|translation|english translation|chinese translation|简体版|簡體版|繁体版|繁體版|中文版|汉化版|漢化版|翻译版|翻譯版|翻訳版|번역판)\s*$""",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val trackingQueryNames = setOf("gclid", "fbclid")
    private val hanVariantFolds = mapOf(
        '愛' to '爱',
        '歡' to '欢',
        '樂' to '乐',
        '學' to '学',
        '園' to '园',
        '戀' to '恋',
        '懸' to '悬',
        '驚' to '惊',
        '險' to '险',
        '輕' to '轻',
        '鬆' to '松',
        '劇' to '剧',
        '歷' to '历',
        '體' to '体',
        '運' to '运',
        '動' to '动',
        '機' to '机',
        '靈' to '灵',
        '戰' to '战',
        '爭' to '争',
        '俠' to '侠',
        '後' to '后',
        '宮' to '宫',
        '異' to '异',
        '復' to '复',
        '國' to '国',
        '連' to '连',
        '載' to '载',
        '結' to '结',
        '臺' to '台',
        '灣' to '湾',
        '歐' to '欧',
        '類' to '类',
        '標' to '标',
        '籤' to '签',
        '別' to '别',
        '區' to '区',
        '語' to '语',
        '長' to '长',
        '讀' to '读',
        '顏' to '颜',
        '軍' to '军',
        '鬥' to '斗',
        '轉' to '转',
    )
    private val canonicalGenreDefinitions = listOf(
        canonicalGenre(
            "romance",
            GenreFacet.CONTENT,
            1.35,
            "love", "romantic", "恋爱", "爱情", "ロマンス", "恋愛", "로맨스", "연애",
            "romántico", "romántica", "romantique", "romantik", "romantico", "romantica",
            "romântico", "romântica", "романтика", "romans",
        ),
        canonicalGenre(
            "comedy",
            GenreFacet.TONE,
            1.20,
            "funny", "humor", "humour", "喜剧", "搞笑", "欢乐向", "轻松", "コメディ", "ギャグ",
            "ほのぼの", "コメディー", "코미디", "개그", "comedia", "comédie", "komödie", "commedia", "comédia",
            "комедия", "komedia",
        ),
        canonicalGenre(
            "school_life",
            GenreFacet.SETTING,
            1.30,
            "school", "school life", "campus", "校园", "校园生活", "学园", "学園", "学校生活",
            "学園もの", "학원", "학교", "vida escolar", "vie scolaire", "schule", "schulleben",
            "scolastico", "школа", "школьная жизнь", "szkoła",
        ),
        canonicalGenre(
            "action",
            GenreFacet.CONTENT,
            1.0,
            "动作", "アクション", "액션", "acción", "ação", "aktion", "azione", "боевик", "akcja",
        ),
        canonicalGenre(
            "adventure",
            GenreFacet.CONTENT,
            1.05,
            "冒险", "冒険", "모험", "aventura", "aventure", "abenteuer", "avventura", "приключения", "przygoda",
        ),
        canonicalGenre("drama", GenreFacet.CONTENT, 1.05, "剧情", "ドラマ", "드라마", "drame", "драма", "dramat"),
        canonicalGenre(
            "fantasy",
            GenreFacet.CONTENT,
            1.10,
            "奇幻", "魔幻", "ファンタジー", "판타지", "fantasía", "fantastique", "fantasia", "фэнтези", "fantastyka",
        ),
        canonicalGenre(
            "science_fiction",
            GenreFacet.CONTENT,
            1.15,
            "science fiction", "sci fi", "sf", "科幻", "サイエンスフィクション", "サイエンス・フィクション", "공상과학",
            "ciencia ficción", "ficção científica", "science-fiction", "science fiction", "научная фантастика",
            "science fiction",
        ),
        canonicalGenre(
            "mystery",
            GenreFacet.CONTENT,
            1.30,
            "detective", "悬疑", "推理", "ミステリー", "ミステリ", "미스터리", "misterio", "mystère", "krimi",
            "giallo", "детектив", "tajemnica",
        ),
        canonicalGenre(
            "thriller",
            GenreFacet.CONTENT,
            1.25,
            "suspense", "惊悚", "サスペンス", "スリラー", "스릴러", "tráiler", "thriller", "триллер",
        ),
        canonicalGenre("horror", GenreFacet.CONTENT, 1.30, "恐怖", "ホラー", "공포", "terror", "horreur", "ужасы", "groza"),
        canonicalGenre(
            "slice_of_life",
            GenreFacet.TONE,
            1.10,
            "slice of life", "日常", "日常系", "生活", "일상", "vida cotidiana", "tranche de vie", "alltag",
            "повседневность", "okruchy życia",
        ),
        canonicalGenre(
            "supernatural",
            GenreFacet.CONTENT,
            1.15,
            "灵异", "超自然", "オカルト", "超常現象", "초자연", "sobrenatural", "surnaturel", "übernatürlich",
            "сверхъестественное",
        ),
        canonicalGenre(
            "historical",
            GenreFacet.SETTING,
            1.05,
            "history", "历史", "歴史", "時代劇", "역사", "histórico", "historique", "historisch", "storico",
            "исторический", "historyczny",
        ),
        canonicalGenre(
            "psychological",
            GenreFacet.CONTENT,
            1.25,
            "psychology", "心理", "サイコロジカル", "심리", "psicológico", "psychologique", "psychologisch",
            "психология", "psychologiczny",
        ),
        canonicalGenre(
            "sports",
            GenreFacet.CONTENT,
            1.0,
            "体育", "运动", "スポーツ", "스포츠", "deportes", "sportif", "спорт", "sportowy",
        ),
        canonicalGenre(
            "music",
            GenreFacet.CONTENT,
            1.0,
            "音乐", "音楽", "음악", "música", "musique", "musik", "musica", "музыка", "muzyka",
        ),
        canonicalGenre(
            "martial_arts",
            GenreFacet.CONTENT,
            1.10,
            "martial arts", "武侠", "武术", "武術", "格闘技", "무협", "무술", "artes marciales", "arts martiaux",
            "kampfkunst", "arti marziali", "боевые искусства",
        ),
        canonicalGenre(
            "mecha",
            GenreFacet.CONTENT,
            1.05,
            "mech", "robot", "机甲", "メカ", "ロボット", "메카", "ロボット", "mecha", "меха",
        ),
        canonicalGenre(
            "crime",
            GenreFacet.CONTENT,
            1.15,
            "犯罪", "クライム", "범죄", "crimen", "crime", "verbrechen", "crimine", "преступление", "kryminał",
        ),
        canonicalGenre(
            "war",
            GenreFacet.SETTING,
            1.05,
            "military", "战争", "軍事", "戦争", "ミリタリー", "전쟁", "guerra", "guerre", "krieg", "война", "wojna",
        ),
        canonicalGenre(
            "isekai",
            GenreFacet.SETTING,
            1.25,
            "异世界", "異世界", "穿越", "異世界もの", "이세계", "otro mundo", "monde parallèle", "andere welt",
            "другой мир",
        ),
        canonicalGenre(
            "reincarnation",
            GenreFacet.CONTENT,
            1.15,
            "rebirth", "转生", "転生", "환생", "reencarnación", "réincarnation", "wiedergeburt", "реинкарнация",
        ),
        canonicalGenre(
            "girls_love",
            GenreFacet.CONTENT,
            1.30,
            "girls love", "yuri", "gl", "百合", "ガールズラブ", "걸즈 러브", "유리",
        ),
        canonicalGenre(
            "boys_love",
            GenreFacet.CONTENT,
            1.30,
            "boys love", "yaoi", "bl", "耽美", "ボーイズラブ", "보이즈 러브", "야오이",
        ),
        canonicalGenre("harem", GenreFacet.CONTENT, 1.05, "后宫", "ハーレム", "하렘", "harén", "гарем"),
        canonicalGenre(
            "revenge",
            GenreFacet.CONTENT,
            1.20,
            "复仇", "復讐", "복수", "venganza", "vengeance", "rache", "vendetta", "vingança", "месть", "zemsta",
        ),
        canonicalGenre("urban", GenreFacet.SETTING, 1.15, "都市", "现代都市", "現代都市", "도시"),
        canonicalGenre("workplace", GenreFacet.SETTING, 1.20, "职场", "職場", "办公室", "辦公室", "office romance"),
        canonicalGenre(
            "childhood_friends",
            GenreFacet.CONTENT,
            1.30,
            "青梅竹马", "青梅竹馬", "幼驯染", "幼馴染", "childhood friend", "childhood friends",
        ),
        canonicalGenre(
            "contract_marriage",
            GenreFacet.CONTENT,
            1.35,
            "先婚后爱",
            "先婚後愛",
            "契约婚姻",
            "契約婚姻",
            "contract marriage",
        ),
        canonicalGenre(
            "entertainment_industry",
            GenreFacet.SETTING,
            1.25,
            "娱乐圈",
            "娛樂圈",
            "演艺圈",
            "演藝圈",
            "show business",
        ),
        canonicalGenre("sweet_romance", GenreFacet.TONE, 1.25, "甜宠", "甜寵", "高甜", "溺爱", "溺愛"),
        canonicalGenre("cultivation", GenreFacet.CONTENT, 1.25, "修仙", "仙侠", "仙俠", "修真", "cultivation"),
        canonicalGenre("system", GenreFacet.CONTENT, 1.15, "系统", "系統", "系统流", "系統流"),
        canonicalGenre("shounen", GenreFacet.DEMOGRAPHIC, 0.70, "shonen", "少年", "少年漫画", "少年マンガ", "소년"),
        canonicalGenre("shoujo", GenreFacet.DEMOGRAPHIC, 0.70, "shojo", "少女", "少女漫画", "少女マンガ", "소녀"),
        canonicalGenre("seinen", GenreFacet.DEMOGRAPHIC, 0.70, "青年", "青年漫画", "青年マンガ", "청년"),
        canonicalGenre("josei", GenreFacet.DEMOGRAPHIC, 0.70, "女性", "女性漫画", "女性マンガ", "여성"),
    )
    private val canonicalGenresById = canonicalGenreDefinitions.associateBy(CanonicalGenre::id)
    private val broadCanonicalGenres = setOf(
        "romance",
        "comedy",
        "action",
        "drama",
        "fantasy",
        "school_life",
        "slice_of_life",
    )
    private val genreAliases = buildMap<String, Set<String>> {
        canonicalGenreDefinitions.forEach { definition ->
            (definition.aliases + definition.id.replace('_', ' ')).forEach { alias ->
                put(normalizeGenreKey(alias), linkedSetOf(definition.id))
            }
        }
        fun compound(vararg aliases: String, ids: Set<String>) {
            aliases.forEach { alias -> put(normalizeGenreKey(alias), ids) }
        }
        compound(
            "romantic comedy",
            "romcom",
            "love comedy",
            "爱情喜剧",
            "恋爱喜剧",
            "ラブコメ",
            "로맨틱 코미디",
            ids = linkedSetOf("romance", "comedy"),
        )
        compound(
            "action adventure",
            "动作冒险",
            "アクションアドベンチャー",
            "액션 어드벤처",
            ids = linkedSetOf("action", "adventure"),
        )
    }
    private val genericGenres = setOf(
        "日本",
        "日本漫画",
        "日本漫畫",
        "日漫",
        "japan",
        "japanese",
        "english",
        "英文",
        "英語",
        "translated",
        "中国",
        "中國",
        "中国漫画",
        "中國漫畫",
        "国漫",
        "國漫",
        "china",
        "chinese",
        "韩国",
        "韓國",
        "韩漫",
        "韓漫",
        "korea",
        "korean",
        "欧美",
        "歐美",
        "美漫",
        "港台",
        "香港",
        "台湾",
        "臺灣",
        "漫画",
        "漫畫",
        "manga",
        "comic",
        "comics",
        "连载",
        "連載",
        "ongoing",
        "新作",
        "新连载",
        "新連載",
        "신작",
        "new",
        "new release",
        "完结",
        "完結",
        "已完结",
        "已完結",
        "completed",
        "日本語",
        "英語",
        "日本マンガ",
        "マンガ",
        "コミック",
        "コミックス",
        "連載中",
        "完結",
        "完結済み",
        "カラー",
        "フルカラー",
        "モノクロ",
        "縦読み",
        "タテ読み",
        "한국어",
        "영어",
        "만화",
        "웹툰",
        "연재중",
        "완결",
        "español",
        "français",
        "deutsch",
        "italiano",
        "português",
        "русский",
        "polski",
        "webtoon",
        "doujinshi",
        "original",
        "full color",
        "black and white",
        "full censorship",
        "rough translation",
    ).mapTo(hashSetOf(), ::normalizeGenreKey)
    private val nonContentGenreNamespaces = setOf(
        "language",
        "lang",
        "语言",
        "語言",
        "言語",
        "언어",
        "artist",
        "artists",
        "group",
        "groups",
        "circle",
        "circles",
        "uploader",
        "reclass",
    ).mapTo(hashSetOf(), ::normalizeGenreKey)
    private val knownGenreKeys = genreAliases.keys + genericGenres

    private enum class GenreFacet {
        CONTENT,
        SETTING,
        TONE,
        DEMOGRAPHIC,
    }

    private data class CanonicalGenre(
        val id: String,
        val facet: GenreFacet,
        val baseWeight: Double,
        val aliases: Set<String>,
    )

    private data class GenreSpan(
        val start: Int,
        val endExclusive: Int,
    )

    private data class RawGenreLabel(
        val displayName: String,
        val semanticName: String,
    )

    private data class StructuredTag(
        val namespace: String,
        val value: String,
    )

    private fun canonicalGenre(
        id: String,
        facet: GenreFacet,
        baseWeight: Double,
        vararg aliases: String,
    ): CanonicalGenre = CanonicalGenre(id, facet, baseWeight, aliases.toSet())

    fun extractCreators(manga: SManga): List<CreatorIdentity> {
        val creators = linkedMapOf<String, CreatorIdentity>()

        fun addCreators(value: String?, role: CreatorRole) {
            value.orEmpty()
                .split(creatorSeparator)
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach { raw ->
                    val effectiveRole = if (explicitCirclePrefix.containsMatchIn(raw)) {
                        CreatorRole.GROUP
                    } else {
                        role
                    }
                    val displayName = raw.replaceFirst(explicitCirclePrefix, "").trim()
                    val normalizedName = normalize(displayName)
                    if (normalizedName.isBlank()) return@forEach
                    val existing = creators[normalizedName]
                    creators[normalizedName] = if (existing == null) {
                        CreatorIdentity(displayName, normalizedName, setOf(effectiveRole))
                    } else {
                        existing.copy(roles = existing.roles + effectiveRole)
                    }
                }
        }

        addCreators(manga.author, CreatorRole.AUTHOR)
        addCreators(manga.artist, CreatorRole.ARTIST)
        extractStructuredTags(manga.description.orEmpty()).forEach { tag ->
            when (tag.namespace) {
                "artist" -> addCreators(tag.value, CreatorRole.ARTIST)
                "group", "circle" -> addCreators("group:${tag.value}", CreatorRole.GROUP)
            }
        }
        return creators.values.toList()
    }

    fun extractGenres(manga: SManga): Set<String> {
        return extractGenreIdentities(manga).mapTo(linkedSetOf(), GenreIdentity::normalizedName)
    }

    fun extractGenreIdentities(manga: SManga): List<GenreIdentity> {
        val labels = buildList {
            splitGenreLabels(manga.genre.orEmpty()).forEach { displayName ->
                val namespace = normalize(displayName.substringBefore(':', missingDelimiterValue = ""))
                val semanticName = if (namespace in structuredContentNamespaces) {
                    displayName.substringAfter(':').trim()
                } else {
                    displayName
                }
                add(RawGenreLabel(displayName, semanticName))
            }
            extractStructuredTags(manga.description.orEmpty())
                .filter { it.namespace in structuredContentNamespaces }
                .forEach { tag ->
                    add(RawGenreLabel("${tag.namespace}:${tag.value}", tag.value))
                }
        }
        return labels
            .filterNot { isGenericGenre(it.semanticName) }
            .flatMap { label ->
                normalizeGenres(label.semanticName).map { canonicalId ->
                    GenreIdentity(label.displayName, canonicalId)
                }
            }
            .filter { it.normalizedName.isNotBlank() }
            .distinctBy { it.normalizedName to normalize(it.displayName) }
    }

    private fun extractStructuredTags(description: String): List<StructuredTag> {
        if (!description.contains("Tags:", ignoreCase = true)) return emptyList()
        return structuredTagLine.findAll(description).flatMap { line ->
            val namespace = normalize(line.groupValues[1]).replace(' ', '_')
            structuredTagValue.findAll(line.groupValues[2]).mapNotNull { valueMatch ->
                val value = Normalizer.normalize(valueMatch.groupValues[1], Normalizer.Form.NFKC).trim()
                value.takeIf(String::isNotBlank)?.let { StructuredTag(namespace, it) }
            }
        }.toList()
    }

    fun normalizeGenre(value: String): String {
        return normalizeGenres(value).firstOrNull().orEmpty()
    }

    fun normalizeGenres(value: String): Set<String> {
        val normalized = normalizeGenreKey(value)
        if (normalized.isBlank()) return emptySet()
        return genreAliases[normalized] ?: linkedSetOf(normalized.replace(' ', '_'))
    }

    fun genreSemanticWeight(canonicalId: String): Double {
        return canonicalGenresById[canonicalId]?.baseWeight ?: 1.0
    }

    /**
     * Broad catalogue genres are useful for recall but poor at distinguishing one work from
     * another. Source-native tags that survive the generic-tag filter often carry more detail
     * (for example a setting or relationship trope), so do not automatically place every known
     * translation ahead of them.
     */
    fun genreSpecificityWeight(canonicalId: String): Double = when {
        canonicalId in broadCanonicalGenres -> 0.72
        canonicalId in canonicalGenresById -> 1.0
        else -> 1.15
    }

    fun genreRoutePriority(canonicalId: String): Int {
        return when (canonicalGenresById[canonicalId]?.facet) {
            GenreFacet.CONTENT -> 0
            GenreFacet.SETTING -> 1
            GenreFacet.TONE -> 2
            GenreFacet.DEMOGRAPHIC -> 3
            null -> 4
        }
    }

    fun isKnownGenre(value: String): Boolean = value in canonicalGenresById

    fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(punctuationOrSpace, " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun normalizeGenreKey(value: String): String {
        return normalize(value).map { character -> hanVariantFolds[character] ?: character }.joinToString("")
    }

    private fun isGenericGenre(value: String): Boolean {
        val normalized = normalizeGenreKey(value)
        if (normalized in genericGenres) return true
        val namespace = value.substringBefore(':', missingDelimiterValue = "")
            .let(::normalizeGenreKey)
        if (namespace in nonContentGenreNamespaces) return true
        return nonContentGenreNamespaces.any { prefix ->
            normalized.startsWith("${prefix}_") || normalized.startsWith("$prefix ")
        }
    }

    private fun splitGenreLabels(value: String): List<String> {
        return value.split(genreSeparator)
            .map { it.replaceFirst(typedGenrePrefix, "").trim() }
            .filter(String::isNotBlank)
            .flatMap(::splitKnownGenreSequence)
    }

    private fun splitKnownGenreSequence(value: String): List<String> {
        val normalized = normalizeGenreKey(value)
        if (normalized.isBlank() || normalized in knownGenreKeys) return listOf(value)

        if ('・' in value) {
            val middleDotParts = value.split('・').map(String::trim).filter(String::isNotBlank)
            if (middleDotParts.count { normalizeGenreKey(it) in knownGenreKeys } >= 2) return middleDotParts
        }

        val originalTokens = value.trim().split(whitespace).filter(String::isNotBlank)
        if (originalTokens.size > 1) {
            val normalizedTokens = originalTokens.map(::normalizeGenreKey)
            segmentKnownTokens(normalizedTokens)?.takeIf { it.size > 1 }?.let { segmented ->
                return segmented.map { span ->
                    originalTokens.subList(span.start, span.endExclusive).joinToString(" ")
                }
            }
            segmentPartiallyKnownTokens(normalizedTokens)?.let { segmented ->
                return segmented.map { span ->
                    originalTokens.subList(span.start, span.endExclusive).joinToString(" ")
                }
            }
        }

        if (
            value.length == normalized.length &&
            normalized.none(Char::isWhitespace) &&
            normalized.all(::isCjkCharacter)
        ) {
            segmentKnownCharacters(normalized)?.takeIf { it.size > 1 }?.let { segmented ->
                return segmented.map { span -> value.substring(span.start, span.endExclusive) }
            }
            segmentPartiallyKnownCharacters(normalized)?.let { segmented ->
                return segmented.map { span -> value.substring(span.start, span.endExclusive) }
            }
        }
        return listOf(value)
    }

    private fun segmentKnownTokens(tokens: List<String>): List<GenreSpan>? {
        val best = arrayOfNulls<List<GenreSpan>>(tokens.size + 1)
        best[tokens.size] = emptyList()
        for (start in tokens.lastIndex downTo 0) {
            for (end in tokens.size downTo start + 1) {
                val suffix = best[end] ?: continue
                val key = tokens.subList(start, end).joinToString(" ")
                if (key !in knownGenreKeys) continue
                val candidate = listOf(GenreSpan(start, end)) + suffix
                if (best[start] == null || candidate.size < best[start]!!.size) best[start] = candidate
            }
        }
        return best[0]
    }

    private fun segmentPartiallyKnownTokens(tokens: List<String>): List<GenreSpan>? {
        val spans = mutableListOf<GenreSpan>()
        var start = 0
        while (start < tokens.size) {
            val end = (tokens.size downTo start + 1).firstOrNull { candidateEnd ->
                tokens.subList(start, candidateEnd).joinToString(" ") in knownGenreKeys
            }
            if (end == null) {
                start += 1
            } else {
                spans += GenreSpan(start, end)
                start = end
            }
        }
        return spans.takeIf(List<GenreSpan>::isNotEmpty)
    }

    private fun segmentKnownCharacters(value: String): List<GenreSpan>? {
        val best = arrayOfNulls<List<GenreSpan>>(value.length + 1)
        best[value.length] = emptyList()
        for (start in value.lastIndex downTo 0) {
            for (end in value.length downTo start + 1) {
                val suffix = best[end] ?: continue
                if (value.substring(start, end) !in knownGenreKeys) continue
                val candidate = listOf(GenreSpan(start, end)) + suffix
                if (best[start] == null || candidate.size < best[start]!!.size) best[start] = candidate
            }
        }
        return best[0]
    }

    private fun segmentPartiallyKnownCharacters(value: String): List<GenreSpan>? {
        val spans = mutableListOf<GenreSpan>()
        var start = 0
        while (start < value.length) {
            val end = (value.length downTo start + 1).firstOrNull { candidateEnd ->
                value.substring(start, candidateEnd) in knownGenreKeys
            }
            if (end == null) {
                start += 1
                while (
                    start < value.length &&
                    (value.length downTo start + 1).none { candidateEnd ->
                        value.substring(start, candidateEnd) in knownGenreKeys
                    }
                ) {
                    start += 1
                }
            } else {
                spans += GenreSpan(start, end)
                start = end
            }
        }
        return spans.takeIf(List<GenreSpan>::isNotEmpty)
    }

    private fun isCjkCharacter(character: Char): Boolean {
        if (character == 'ー' || character == '々') return true
        return when (Character.UnicodeScript.of(character.code)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL,
            -> true
            else -> false
        }
    }

    fun normalizeTitle(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .filter(Char::isLetterOrDigit)
    }

    fun normalizeBaseTitle(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        return normalizeTitle(
            normalized
                .replace(editionBracket, " ")
                .replace(trailingEditionMarker, " ")
                .trim(),
        )
    }

    fun safeUrl(manga: SManga): String = try {
        manga.url
    } catch (_: UninitializedPropertyAccessException) {
        ""
    }

    fun safeTitle(manga: SManga): String = try {
        manga.title
    } catch (_: UninitializedPropertyAccessException) {
        ""
    }

    fun recommendationUrlKey(value: String): String {
        return recommendationUrlIdentity(value).canonicalKey
    }

    fun sameRecommendationUrl(left: String, right: String): Boolean {
        return sameRecommendationUrl(
            recommendationUrlIdentity(left),
            recommendationUrlIdentity(right),
        )
    }

    private fun recommendationUrlIdentity(value: String): RecommendationUrlIdentity {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim()
            .substringBefore('#')
        if (normalized.isBlank()) return RecommendationUrlIdentity.EMPTY

        val uri = runCatching { URI(normalized) }.getOrNull()
        val absoluteMatch = absoluteUrlPrefix.find(normalized)
        val hasAuthority = uri?.rawAuthority != null || absoluteMatch != null
        val authority = if (hasAuthority) {
            normalizeUrlAuthority(uri, absoluteMatch?.groupValues?.get(2).orEmpty())
        } else {
            null
        }
        val withoutAuthority = absoluteMatch?.let { normalized.removePrefix(it.value) } ?: normalized
        val rawPath = if (uri?.rawAuthority != null) uri.rawPath.orEmpty() else withoutAuthority.substringBefore('?')
        val rawQuery = if (uri?.rawAuthority != null) {
            uri.rawQuery
        } else {
            withoutAuthority.substringAfter('?', "").ifBlank { null }
        }
        val path = rawPath
            .removePrefix("/")
            .trimEnd('/')
        val query = rawQuery
            ?.split('&')
            ?.filter(String::isNotBlank)
            ?.filterNot { parameter ->
                val name = parameter.substringBefore('=').lowercase(Locale.ROOT)
                name.startsWith("utm_") || name in trackingQueryNames
            }
            ?.sortedWith(
                compareBy<String> { it.substringBefore('=').lowercase(Locale.ROOT) }
                    .thenBy { it.lowercase(Locale.ROOT) },
            )
            .orEmpty()
        val pathAndQuery = buildString {
            append(path)
            if (query.isNotEmpty()) {
                append('?')
                append(query.joinToString("&"))
            }
        }
        val canonicalKey = if (authority == null) {
            pathAndQuery
        } else {
            buildString {
                append("//")
                append(authority)
                if (path.isNotEmpty()) {
                    append('/')
                    append(path)
                }
                if (query.isNotEmpty()) {
                    append('?')
                    append(query.joinToString("&"))
                }
            }
        }
        return RecommendationUrlIdentity(
            canonicalKey = canonicalKey,
            host = authority,
            pathAndQuery = pathAndQuery,
        )
    }

    private fun normalizeUrlAuthority(uri: URI?, fallback: String): String? {
        val host = uri?.host
            ?.lowercase(Locale.ROOT)
            ?.trimEnd('.')
            ?.takeIf(String::isNotBlank)
        if (host != null) {
            val scheme = uri.scheme
            val port = uri.port.takeIf { value ->
                val isDefaultPort =
                    (scheme.equals("http", ignoreCase = true) && value == 80) ||
                        (scheme.equals("https", ignoreCase = true) && value == 443)
                value >= 0 && !isDefaultPort
            }
            return if (port == null) host else "$host:$port"
        }
        return fallback
            .substringAfterLast('@')
            .lowercase(Locale.ROOT)
            .trimEnd('.')
            .takeIf(String::isNotBlank)
    }

    fun identity(sourceId: Long, manga: SManga): RecommendationIdentity {
        val urlIdentity = recommendationUrlIdentity(safeUrl(manga))
        return RecommendationIdentity(
            sourceId = sourceId,
            canonicalUrl = urlIdentity.canonicalKey,
            urlHost = urlIdentity.host,
            urlPath = urlIdentity.pathAndQuery,
            exactTitle = normalizeTitle(safeTitle(manga)),
            baseTitle = normalizeBaseTitle(safeTitle(manga)),
            creators = extractCreators(manga).mapTo(linkedSetOf(), CreatorIdentity::normalizedName),
            cover = manga.thumbnail_url
                ?.takeIf(String::isNotBlank)
                ?.let(::coverKey)
                ?.takeIf(String::isNotBlank),
            series = extractSeries(manga),
        )
    }

    fun sameWork(left: RecommendationIdentity, right: RecommendationIdentity): Boolean {
        if (left.sourceId != right.sourceId) return false
        if (sameRecommendationUrl(left, right)) return true

        val creatorsOverlap = left.creators.intersect(right.creators).isNotEmpty()
        val coverMatches = left.cover != null && left.cover == right.cover
        if (
            creatorsOverlap &&
            left.exactTitle.isNotBlank() &&
            left.exactTitle == right.exactTitle
        ) {
            return true
        }
        if (!creatorsOverlap || left.baseTitle.isBlank() || left.baseTitle != right.baseTitle) return false
        val seriesOverlap = left.series.intersect(right.series).isNotEmpty()
        return seriesOverlap || coverMatches
    }

    private fun sameRecommendationUrl(
        left: RecommendationIdentity,
        right: RecommendationIdentity,
    ): Boolean {
        return sameRecommendationUrlValues(
            leftCanonical = left.canonicalUrl,
            leftHost = left.urlHost,
            leftPath = left.urlPath,
            rightCanonical = right.canonicalUrl,
            rightHost = right.urlHost,
            rightPath = right.urlPath,
        )
    }

    private fun sameRecommendationUrl(
        left: RecommendationUrlIdentity,
        right: RecommendationUrlIdentity,
    ): Boolean {
        return sameRecommendationUrlValues(
            leftCanonical = left.canonicalKey,
            leftHost = left.host,
            leftPath = left.pathAndQuery,
            rightCanonical = right.canonicalKey,
            rightHost = right.host,
            rightPath = right.pathAndQuery,
        )
    }

    private fun sameRecommendationUrlValues(
        leftCanonical: String,
        leftHost: String?,
        leftPath: String,
        rightCanonical: String,
        rightHost: String?,
        rightPath: String,
    ): Boolean {
        if (leftCanonical.isBlank() || rightCanonical.isBlank()) return false
        if (leftCanonical == rightCanonical) return true
        if (leftPath.isBlank() || leftPath != rightPath) return false

        // A source-relative URL belongs to that source's host. Two explicit, different hosts must
        // remain distinct even when the source happens to return the same path from both.
        return (leftHost == null) != (rightHost == null)
    }

    private data class RecommendationUrlIdentity(
        val canonicalKey: String,
        val host: String?,
        val pathAndQuery: String,
    ) {
        companion object {
            val EMPTY = RecommendationUrlIdentity("", null, "")
        }
    }

    private fun extractSeries(manga: SManga): Set<String> {
        return manga.genre.orEmpty()
            .split(genreSeparator)
            .mapNotNull { label -> explicitSeriesPrefix.matchEntire(label.trim())?.groupValues?.get(1) }
            .flatMap { value -> value.split(creatorSeparator) }
            .map(::normalizeTitle)
            .filter(String::isNotBlank)
            .toCollection(linkedSetOf())
    }

    private fun coverKey(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim()
            .substringBefore('#')
        val uri = runCatching { URI(normalized) }.getOrNull()
        if (uri?.host == null) return normalized
        return buildString {
            append(uri.scheme?.lowercase(Locale.ROOT).orEmpty())
            append("://")
            append(uri.host.lowercase(Locale.ROOT))
            uri.port.takeIf { it >= 0 }?.let { append(":$it") }
            append(uri.rawPath.orEmpty())
            uri.rawQuery?.let { append("?$it") }
        }
    }

    fun hasExactCreator(manga: SManga, targetCreators: Set<String>): Boolean {
        if (targetCreators.isEmpty()) return false
        return extractCreators(manga).any { it.normalizedName in targetCreators }
    }

    fun isInformativeGenre(value: String): Boolean {
        return !isGenericGenre(value) && normalizeGenres(value).isNotEmpty()
    }

    /** Builds the exact AND syntax accepted by E-Hentai/ExHentai's f_search field. */
    fun ehentaiExactTagQuery(identities: List<GenreIdentity>): String {
        return identities
            .mapNotNull { identity ->
                val raw = Normalizer.normalize(identity.displayName, Normalizer.Form.NFKC)
                    .trim()
                    .removeSuffix("\$")
                    .takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val namespace = raw.substringBefore(':', missingDelimiterValue = "")
                    .trim()
                    .takeIf(String::isNotBlank)
                val value = if (namespace == null) raw else raw.substringAfter(':').trim()
                if (value.isBlank()) return@mapNotNull null
                val exactValue = if (value.any(Char::isWhitespace)) {
                    "\"${value.replace("\"", "")}\$\""
                } else {
                    "$value\$"
                }
                namespace?.let { "$it:$exactValue" } ?: exactValue
            }
            .distinct()
            .joinToString(" ")
    }
}

internal object RecommendationRanking {
    private const val RRF_K = 60.0
    private const val MIN_FINAL_SCORE = 0.20
    private const val MIN_RARE_COVERAGE = 0.25
    private const val MIN_SPARSE_CARD_COVERAGE = 0.10
    private const val MIN_CANDIDATE_PRECISION = 0.50
    private const val RARE_GENRE_RATIO = 0.10
    private const val MAX_RRF = 1.0 / (RRF_K + 1.0)

    fun documentFrequency(pool: Collection<SManga>): Map<String, Int> {
        return pool
            .flatMap { RecommendationMetadata.extractGenres(it) }
            .groupingBy(String::toString)
            .eachCount()
    }

    fun weightedJaccard(
        left: Set<String>,
        right: Set<String>,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val union = left + right
        val intersection = left intersect right
        if (intersection.isEmpty()) return 0.0

        val denominator = union.sumOf { tagWeight(it, documentFrequency, documentCount) }
        return if (denominator == 0.0) {
            0.0
        } else {
            intersection.sumOf { tagWeight(it, documentFrequency, documentCount) } / denominator
        }
    }

    fun tagProfile(
        targetGenres: Set<String>,
        targetGenreIdentities: List<GenreIdentity> = emptyList(),
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): TagProfile {
        val allTags = targetGenres
            .filter(RecommendationMetadata::isInformativeGenre)
            .toCollection(linkedSetOf())
        val originalIndex = allTags.withIndex().associate { it.value to it.index }
        // A sparse local index must not make source-native tags unusable. Rank by information
        // value instead of putting every known (and often very broad) translated tag first.
        val core = allTags
            .filter { tag ->
                RecommendationMetadata.isKnownGenre(tag) || (documentFrequency[tag] ?: 0) > 0
            }
            .sortedWith(
                compareByDescending<String> {
                    tagWeight(it, documentFrequency, documentCount) *
                        RecommendationMetadata.genreSpecificityWeight(it)
                }
                    .thenBy(RecommendationMetadata::genreRoutePriority)
                    .thenByDescending(RecommendationMetadata::genreSemanticWeight)
                    .thenBy { originalIndex.getValue(it) },
            )
            .take(TagProfile.MAX_CORE_TAGS)
            .toCollection(linkedSetOf())
        val secondary = allTags.filterNot(core::contains).toCollection(linkedSetOf())
        val identitiesByTag = targetGenreIdentities.groupBy(GenreIdentity::normalizedName)
        val coreRouteIdentities = core.flatMap { tag ->
            identitiesByTag[tag].orEmpty().ifEmpty {
                listOf(GenreIdentity(tag.replace('_', ' '), tag))
            }
        }.distinctBy { identity ->
            identity.normalizedName to RecommendationMetadata.normalize(identity.displayName)
        }
        val representedRoutes = coreRouteIdentities.mapTo(hashSetOf(), GenreIdentity::normalizedName)
        val provisionalRoutes = targetGenreIdentities.asSequence()
            .filter { it.normalizedName in allTags && it.normalizedName !in representedRoutes }
            .distinctBy { identity ->
                identity.normalizedName to RecommendationMetadata.normalize(identity.displayName)
            }
            .take((2 - coreRouteIdentities.size).coerceAtLeast(0))
            .toList()
        return TagProfile(
            allTags = allTags,
            coreTags = core,
            secondaryTags = secondary,
            routeIdentities = coreRouteIdentities + provisionalRoutes,
        )
    }

    fun weightedCoverage(
        targetTags: Set<String>,
        candidateTags: Set<String>,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Double {
        if (targetTags.isEmpty()) return 0.0
        val denominator = targetTags.sumOf { tagWeight(it, documentFrequency, documentCount) }
        if (denominator == 0.0) return 0.0
        return (targetTags intersect candidateTags)
            .sumOf { tagWeight(it, documentFrequency, documentCount) }
            .div(denominator)
            .coerceIn(0.0, 1.0)
    }

    fun contentScore(
        profile: TagProfile,
        candidateTags: Set<String>,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Double {
        if (profile.coreTags.isEmpty()) return 0.0
        val coverage = weightedCoverage(
            profile.coreTags,
            candidateTags,
            documentFrequency,
            documentCount,
        )
        val coreJaccard = weightedJaccard(
            profile.coreTags,
            candidateTags,
            documentFrequency,
            documentCount,
        )
        val reliableSecondaryTags = profile.secondaryTags.filterTo(linkedSetOf()) { tag ->
            RecommendationMetadata.isKnownGenre(tag) || (documentFrequency[tag] ?: 0) >= 2
        }
        val secondaryBonus = weightedCoverage(
            reliableSecondaryTags,
            candidateTags,
            documentFrequency,
            documentCount,
        )
        return (0.70 * coverage + 0.20 * coreJaccard + 0.10 * secondaryBonus)
            .coerceIn(0.0, 1.0)
    }

    fun isReliable(
        targetGenres: Set<String>,
        candidateGenres: Set<String>,
        evidence: CandidateEvidence,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
        contentScore: Double,
    ): Boolean {
        val profile = tagProfile(
            targetGenres = targetGenres,
            documentFrequency = documentFrequency,
            documentCount = documentCount,
        )
        val coverage = weightedCoverage(
            profile.coreTags,
            candidateGenres,
            documentFrequency,
            documentCount,
        )
        return isReliable(
            profile = profile,
            candidateGenres = candidateGenres,
            actualCandidateGenres = candidateGenres,
            evidence = evidence,
            documentFrequency = documentFrequency,
            documentCount = documentCount,
            coverage = if (profile.coreTags.isEmpty()) contentScore else coverage,
        )
    }

    fun scoreCandidates(
        profile: TagProfile,
        candidates: Collection<SimilarCandidate>,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
        minimumScore: Double = MIN_FINAL_SCORE,
    ): List<RankedSimilarCandidate> {
        require(minimumScore in 0.0..1.0)
        val scored = candidates.mapNotNull { candidate ->
            val sourceGenres = RecommendationMetadata.extractGenres(candidate.manga)
            val actualGenres = sourceGenres + candidate.evidence.externalGenres
            // A route is retrieval evidence, not candidate metadata. Only use it as content when
            // the source returned a bare card; otherwise score the tags the candidate really has.
            val scoringGenres = actualGenres.ifEmpty { candidate.evidence.strongRouteGenres }
            val reliabilityGenres = actualGenres + candidate.evidence.strongRouteGenres
            val coverage = weightedCoverage(
                profile.coreTags,
                scoringGenres,
                documentFrequency,
                documentCount,
            )
            val rawContent = contentScore(profile, scoringGenres, documentFrequency, documentCount)
            if (!isReliable(
                    profile = profile,
                    candidateGenres = reliabilityGenres,
                    actualCandidateGenres = actualGenres,
                    evidence = candidate.evidence,
                    documentFrequency = documentFrequency,
                    documentCount = documentCount,
                    coverage = coverage,
                )
            ) {
                return@mapNotNull null
            }

            val rrf = listOfNotNull(
                candidate.evidence.sourceRelatedRank?.let { 1.0 / (RRF_K + it + 1.0) },
                candidate.evidence.aniListRank?.let { 1.0 / (RRF_K + it + 1.0) },
                candidate.evidence.genreSearchRank?.let { 0.8 / (RRF_K + it + 1.0) },
                candidate.evidence.queryRank?.let { 0.5 / (RRF_K + it + 1.0) },
                candidate.evidence.popularRank?.let { 0.25 / (RRF_K + it + 1.0) },
            ).sum()
            val normalizedRrf = (rrf / MAX_RRF).coerceIn(0.0, 1.0)
            val content = if (profile.coreTags.isEmpty() && candidate.evidence.hasAuthoritativeEvidence) {
                1.0
            } else {
                rawContent
            }
            val score = 0.60 * content + 0.40 * normalizedRrf
            if (score < minimumScore) return@mapNotNull null
            RankedSimilarCandidate(
                manga = candidate.manga,
                genres = scoringGenres,
                evidence = candidate.evidence,
                contentScore = content,
                score = score,
            )
        }

        return scored.sortedWith(
            compareByDescending<RankedSimilarCandidate>(RankedSimilarCandidate::score)
                .thenBy { RecommendationMetadata.safeUrl(it.manga) },
        )
    }

    fun routeGenres(
        targetGenres: Set<String>,
        documentFrequency: Map<String, Int>,
        routeSeed: String = "",
    ): List<String> {
        val originalIndex = targetGenres.withIndex().associate { it.value to it.index }
        return targetGenres.sortedWith(
            compareBy<String> { documentFrequency[it] ?: 0 }
                .thenByDescending(RecommendationMetadata::genreSpecificityWeight)
                .thenBy(RecommendationMetadata::genreRoutePriority)
                .thenBy { stableRouteOrder(routeSeed, it) }
                .thenBy { originalIndex.getValue(it) },
        )
    }

    private fun isReliable(
        profile: TagProfile,
        candidateGenres: Set<String>,
        actualCandidateGenres: Set<String>,
        evidence: CandidateEvidence,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
        coverage: Double,
    ): Boolean {
        if (evidence.hasAuthoritativeEvidence) return true

        val strongRouteGenres = evidence.strongRouteGenres intersect profile.allTags
        if (strongRouteGenres.isNotEmpty()) {
            return actualCandidateGenres.isEmpty() || (actualCandidateGenres intersect strongRouteGenres).isNotEmpty()
        }

        if (
            evidence.queriedGenres.isNotEmpty() &&
            (actualCandidateGenres intersect evidence.queriedGenres).isEmpty()
        ) {
            return false
        }

        if (profile.coreTags.isEmpty()) return false
        val shared = (profile.allTags intersect actualCandidateGenres).filterTo(linkedSetOf()) { tag ->
            RecommendationMetadata.isKnownGenre(tag) ||
                (documentFrequency[tag] ?: 0) >= 2 ||
                tag in evidence.queriedGenres
        }
        if (shared.size >= 2) return true
        if (shared.size != 1) return false

        val only = shared.first()
        val rare = documentCount > 0 &&
            (documentFrequency[only] ?: 0).toDouble() / documentCount <= RARE_GENRE_RATIO
        val specific = RecommendationMetadata.genreSpecificityWeight(only) >= 1.0
        val verifiedWeakRoute = evidence.queriedGenres.isNotEmpty() &&
            (actualCandidateGenres intersect evidence.queriedGenres).isNotEmpty()

        // A single broad tag from popular/local data is not enough evidence: it makes every work
        // in that category share the same pool. An exact source query may still verify it.
        if (profile.coreTags.size == 1) return rare || specific || verifiedWeakRoute

        val informativeCandidateGenres = actualCandidateGenres
            .filterTo(linkedSetOf(), RecommendationMetadata::isInformativeGenre)
        val candidatePrecision = weightedCoverage(
            informativeCandidateGenres,
            profile.allTags,
            documentFrequency,
            documentCount,
        )
        val minimumCoverage = if (informativeCandidateGenres.size <= 1) {
            MIN_SPARSE_CARD_COVERAGE
        } else {
            MIN_RARE_COVERAGE
        }
        if (coverage < minimumCoverage || candidatePrecision < MIN_CANDIDATE_PRECISION) return false

        // Sparse cards often expose only one category. Accept that visible evidence when it is
        // precise, instead of requiring two tags the source never supplied.
        return actualCandidateGenres.size == 1 || rare || specific || verifiedWeakRoute
    }

    private fun stableRouteOrder(seed: String, tag: String): Int {
        return "$seed\u0000$tag".hashCode()
    }

    private fun tagWeight(
        value: String,
        documentFrequency: Map<String, Int>,
        documentCount: Int,
    ): Double {
        val idf = ln((documentCount + 1.0) / ((documentFrequency[value] ?: 0) + 1.0)) + 1.0
        return idf * RecommendationMetadata.genreSemanticWeight(value)
    }
}

private val artistCreatorFilterNames = setOf(
    "artist",
    "artists",
    "illustrator",
    "illustrators",
    "画师",
    "畫師",
    "绘师",
    "繪師",
    "作画",
    "作畫",
    "作画者",
    "絵師",
    "イラスト",
    "イラストレーター",
    "작화",
    "그림",
    "일러스트",
    "일러스트레이터",
    "artista",
    "ilustrador",
    "illustrateur",
    "künstler",
    "zeichner",
    "illustratore",
    "иллюстратор",
    "художник",
    "artysta",
    "ilustrator",
)
private val authorCreatorFilterNames = setOf(
    "author",
    "authors",
    "writer",
    "writers",
    "作者",
    "著者",
    "作家",
    "漫画家",
    "原作",
    "原作者",
    "编剧",
    "編劇",
    "脚本",
    "腳本",
    "작가",
    "저자",
    "원작",
    "글",
    "각본",
    "autor",
    "escritor",
    "guionista",
    "auteur",
    "scénariste",
    "schriftsteller",
    "autore",
    "scrittore",
    "sceneggiatore",
    "roteirista",
    "автор",
    "писатель",
    "сценарист",
    "pisarz",
    "scenarzysta",
)
private val groupCreatorFilterNames = setOf(
    "group",
    "groups",
    "circle",
    "circles",
    "社团",
    "社團",
    "同人社团",
    "同人社團",
    "サークル",
    "グループ",
    "同人サークル",
    "서클",
    "그룹",
    "동인서클",
    "círculo",
    "grupo",
    "cercle",
    "collectif",
    "gruppe",
    "kollektiv",
    "gruppo",
    "coletivo",
    "группа",
    "коллектив",
    "grupa",
)

internal fun applyExactCreatorTextFilter(
    filters: FilterList,
    creator: CreatorIdentity,
): CreatorFilterMatch? {
    if (creator.displayName.isBlank() || creator.normalizedName.isBlank()) return null

    fun fieldKind(name: String): CreatorFilterKind? {
        return when (RecommendationMetadata.normalize(name)) {
            in artistCreatorFilterNames -> CreatorFilterKind.ARTIST
            in authorCreatorFilterNames -> CreatorFilterKind.AUTHOR
            in groupCreatorFilterNames -> CreatorFilterKind.GROUP
            else -> null
        }
    }

    data class TextCandidate(
        val filter: Filter.Text,
        val kind: CreatorFilterKind,
        val semanticName: String,
    )

    val candidates = mutableListOf<TextCandidate>()
    fun collect(filter: Filter<*>, inheritedField: Pair<CreatorFilterKind, String>? = null) {
        when (filter) {
            is Filter.Text -> {
                val directField = fieldKind(filter.name)?.let { it to filter.name }
                val field = directField ?: inheritedField
                if (field != null) candidates += TextCandidate(filter, field.first, field.second)
            }
            is Filter.Group<*> -> {
                val directField = fieldKind(filter.name)?.let { it to filter.name }
                val field = directField ?: inheritedField
                filter.state.forEach { child ->
                    if (child is Filter<*>) collect(child, field)
                }
            }
            else -> Unit
        }
    }
    filters.forEach { collect(it) }

    val routes = buildList {
        if (CreatorRole.GROUP in creator.roles) {
            add(CreatorRole.GROUP to CreatorFilterKind.GROUP)
        }
        if (CreatorRole.ARTIST in creator.roles) {
            add(CreatorRole.ARTIST to CreatorFilterKind.ARTIST)
        }
        if (CreatorRole.AUTHOR in creator.roles) {
            add(CreatorRole.AUTHOR to CreatorFilterKind.AUTHOR)
        }
    }
    for ((role, kind) in routes) {
        val candidate = candidates.firstOrNull { it.kind == kind } ?: continue
        candidate.filter.state = creator.displayName
        return CreatorFilterMatch(role, kind, candidate.semanticName)
    }
    return null
}

internal enum class GenreFilterKind(
    val isText: Boolean,
    val isStrongEvidence: Boolean,
) {
    NONE(isText = false, isStrongEvidence = false),
    STRUCTURED(isText = false, isStrongEvidence = true),

    // A field explicitly named Tags/Labels is a source-side semantic filter. Results with
    // declared conflicting genres are still rejected before this evidence is recorded.
    TEXT_TAG(isText = true, isStrongEvidence = true),
    TEXT_GENRE(isText = true, isStrongEvidence = false),
    TEXT_CATEGORY(isText = true, isStrongEvidence = false),
}

internal fun applyGenreFilter(filters: FilterList, genre: GenreIdentity): GenreFilterKind {
    fun isExclusionField(name: String): Boolean {
        val normalized = RecommendationMetadata.normalize(name)
        return listOf(
            "exclude", "excluded", "excluding", "without",
            "排除", "不含", "剔除", "除外", "含めない", "제외",
            "excluir", "excluido", "excluídos", "exclure", "exclus", "sans",
            "ausschließen", "ausgeschlossen", "ohne", "escludi", "esclusi", "senza",
            "исключить", "исключенные", "без", "wyklucz",
        ).any { marker -> normalized == marker || normalized.contains(marker) }
    }

    fun applyStructured(filter: Filter<*>): Boolean {
        if (isExclusionField(filter.name)) return false
        when (filter) {
            is Filter.CheckBox -> if (
                genre.normalizedName in RecommendationMetadata.normalizeGenres(filter.name)
            ) {
                filter.state = true
                return true
            }
            is Filter.TriState -> if (
                genre.normalizedName in RecommendationMetadata.normalizeGenres(filter.name)
            ) {
                filter.state = Filter.TriState.STATE_INCLUDE
                return true
            }
            is Filter.Select<*> -> {
                val index = filter.values.indexOfFirst {
                    genre.normalizedName in RecommendationMetadata.normalizeGenres(it.toString())
                }
                if (index >= 0) {
                    filter.state = index
                    return true
                }
            }
            is Filter.Group<*> -> {
                filter.state.forEach { child ->
                    if (child is Filter<*> && applyStructured(child)) return true
                }
            }
            else -> Unit
        }
        return false
    }

    if (filters.any(::applyStructured)) return GenreFilterKind.STRUCTURED

    data class TextCandidate(val filter: Filter.Text, val priority: Int)

    fun textPriority(name: String): Int? {
        return when (RecommendationMetadata.normalize(name)) {
            "tag", "tags", "label", "labels", "标签", "標籤", "タグ", "タグ名", "태그",
            "etiqueta", "etiquetas", "étiquette", "étiquettes", "schlagwort", "schlagwörter",
            "etichetta", "etichette", "тег", "теги", "tagi",
            -> 0
            "genre", "genres", "type", "types", "题材", "題材", "类型", "類型", "ジャンル",
            "種別", "장르", "유형", "종류", "género", "géneros", "tipo", "tipos", "gênero",
            "gêneros", "typ", "typen", "genere", "generi", "жанр", "жанры", "тип",
            "типы", "gatunek", "gatunki", "typy",
            -> 1
            "category", "categories", "分类", "分類", "类别", "類別", "カテゴリ", "カテゴリー",
            "카테고리", "분류", "categoría", "categorías", "clasificación", "catégorie", "catégories",
            "classement", "kategorie", "kategorien", "categoria", "categorias", "categorie", "категория",
            "категории", "kategoria",
            -> 2
            else -> null
        }
    }

    val textCandidates = mutableListOf<TextCandidate>()
    fun collectText(filter: Filter<*>, parentPriority: Int?) {
        if (isExclusionField(filter.name)) return
        when (filter) {
            is Filter.Text -> {
                val priority = listOfNotNull(textPriority(filter.name), parentPriority).minOrNull()
                if (priority != null) textCandidates += TextCandidate(filter, priority)
            }
            is Filter.Group<*> -> {
                val priority = textPriority(filter.name) ?: parentPriority
                filter.state.forEach { child ->
                    if (child is Filter<*>) collectText(child, priority)
                }
            }
            else -> Unit
        }
    }
    filters.forEach { collectText(it, null) }
    val selected = textCandidates.minByOrNull(TextCandidate::priority) ?: return GenreFilterKind.NONE
    selected.filter.state = genre.displayName
    return when (selected.priority) {
        0 -> GenreFilterKind.TEXT_TAG
        1 -> GenreFilterKind.TEXT_GENRE
        else -> GenreFilterKind.TEXT_CATEGORY
    }
}

/**
 * Prefers a source-provided freshness order for recommendation routes. This is capability based:
 * sources without an explicit freshness sort remain untouched.
 */
internal fun preferFreshRecommendationSort(filters: FilterList): Boolean {
    data class SortCandidate(
        val filter: Filter.Sort,
        val index: Int,
        val priority: Int,
    )

    fun priority(value: String): Int? {
        val normalized = RecommendationMetadata.normalize(value)
        val compact = normalized.replace(" ", "")
        return when {
            listOf(
                "update time",
                "updated time",
                "last updated",
                "datetime updated",
                "更新时间",
                "更新時間",
                "更新日期",
                "更新日時",
                "更新順",
                "업데이트순",
                "업데이트시간",
            ).any { marker ->
                val normalizedMarker = RecommendationMetadata.normalize(marker)
                normalized.contains(normalizedMarker) || compact.contains(normalizedMarker.replace(" ", ""))
            } -> 0
            listOf(
                "latest",
                "newest",
                "recently updated",
                "最新",
                "最近更新",
                "新着順",
                "최신",
                "최근업데이트",
            ).any { marker ->
                val normalizedMarker = RecommendationMetadata.normalize(marker)
                normalized.contains(normalizedMarker) || compact.contains(normalizedMarker.replace(" ", ""))
            } -> 1
            else -> null
        }
    }

    val candidates = mutableListOf<SortCandidate>()
    fun collect(filter: Filter<*>) {
        when (filter) {
            is Filter.Sort -> filter.values.forEachIndexed { index, value ->
                priority(value)?.let { candidates += SortCandidate(filter, index, it) }
            }
            is Filter.Group<*> -> filter.state.forEach { child ->
                if (child is Filter<*>) collect(child)
            }
            else -> Unit
        }
    }
    filters.forEach(::collect)

    val selected = candidates.minWithOrNull(
        compareBy<SortCandidate>(SortCandidate::priority)
            .thenBy(SortCandidate::index),
    ) ?: return false
    selected.filter.state = Filter.Sort.Selection(selected.index, ascending = false)
    return true
}

/** Detects sources whose category/tag controls are populated asynchronously by the extension. */
internal fun hasDeferredGenreFilterMarker(filters: FilterList): Boolean {
    val genreMarkers = listOf(
        "genre", "category", "tag", "theme",
        "题材", "題材", "分类", "分類", "标签", "標籤",
        "ジャンル", "タグ", "장르", "태그",
    )
    val readinessMarkers = listOf(
        "refresh", "reload", "reset", "loading",
        "刷新", "重置", "加载", "載入",
        "再読み込み", "リセット", "새로고침", "불러오기",
    )
    return filters.filterIsInstance<Filter.Header>().any { header ->
        val normalized = RecommendationMetadata.normalize(header.name)
        genreMarkers.any { normalized.contains(RecommendationMetadata.normalize(it)) } &&
            readinessMarkers.any { normalized.contains(RecommendationMetadata.normalize(it)) }
    }
}

/** Applies an AND route only to multi-select-capable structured controls. */
internal fun applyCombinedStructuredGenreFilters(
    filters: FilterList,
    genres: List<GenreIdentity>,
): Boolean {
    if (genres.size < 2) return false
    val selected = mutableListOf<Filter<*>>()

    fun isExclusion(name: String): Boolean {
        val normalized = RecommendationMetadata.normalize(name)
        return listOf(
            "exclude",
            "excluding",
            "without",
            "排除",
            "不含",
            "除外",
            "含めない",
            "제외",
        ).any(normalized::contains)
    }

    fun find(filter: Filter<*>, genre: GenreIdentity): Filter<*>? {
        if (isExclusion(filter.name)) return null
        when (filter) {
            is Filter.CheckBox,
            is Filter.TriState,
            -> if (
                filter !in selected &&
                genre.normalizedName in RecommendationMetadata.normalizeGenres(filter.name)
            ) {
                return filter
            }
            is Filter.Group<*> -> filter.state.forEach { child ->
                if (child is Filter<*>) find(child, genre)?.let { return it }
            }
            else -> Unit
        }
        return null
    }

    for (genre in genres) {
        val match = filters.firstNotNullOfOrNull { find(it, genre) } ?: return false
        selected += match
    }
    selected.forEach { filter ->
        when (filter) {
            is Filter.CheckBox -> filter.state = true
            is Filter.TriState -> filter.state = Filter.TriState.STATE_INCLUDE
            else -> Unit
        }
    }
    return true
}

internal fun applyExactGenreFilter(
    filters: FilterList,
    normalizedGenre: String,
    displayGenre: String = normalizedGenre,
): Boolean {
    return applyGenreFilter(filters, GenreIdentity(displayGenre, normalizedGenre)) != GenreFilterKind.NONE
}
