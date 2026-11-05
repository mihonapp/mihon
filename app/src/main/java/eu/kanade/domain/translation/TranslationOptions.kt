package eu.kanade.domain.translation

/**
 * Constants for the chapter translation feature.
 *
 * Model ids are taken from the Gemini API "Nano Banana" docs (https://ai.google.dev/gemini-api/docs/nanobanana).
 * They change over time, so keep them all in this one place.
 */
object TranslationOptions {

    const val MODEL_FLASH_LITE_IMAGE = "gemini-3.1-flash-lite-image"
    const val MODEL_FLASH_IMAGE = "gemini-3.1-flash-image"
    const val MODEL_PRO_IMAGE = "gemini-3-pro-image"
    const val MODEL_LEGACY_FLASH_IMAGE = "gemini-2.5-flash-image"

    const val DEFAULT_MODEL = MODEL_FLASH_IMAGE

    /** Model id -> display name. */
    val MODELS: Map<String, String> = linkedMapOf(
        MODEL_FLASH_LITE_IMAGE to "Nano Banana 3.1 Flash Lite ($MODEL_FLASH_LITE_IMAGE)",
        MODEL_FLASH_IMAGE to "Nano Banana 3.1 Flash ($MODEL_FLASH_IMAGE)",
        MODEL_PRO_IMAGE to "Nano Banana 3 Pro ($MODEL_PRO_IMAGE)",
        MODEL_LEGACY_FLASH_IMAGE to "Nano Banana 2.5 Flash ($MODEL_LEGACY_FLASH_IMAGE)",
    )

    const val MODE_LOCAL_OCR = "local_ocr"
    const val MODE_REDRAW = "redraw"
    const val MODE_OVERLAY = "overlay"

    const val OCR_SCRIPT_JAPANESE = "japanese"
    const val OCR_SCRIPT_LATIN = "latin"
    const val OCR_SCRIPT_KOREAN = "korean"
    const val OCR_SCRIPT_CHINESE = "chinese"
    const val OCR_SCRIPT_AUTO = "auto"

    val OCR_SCRIPTS: List<String> = listOf(
        OCR_SCRIPT_JAPANESE,
        OCR_SCRIPT_LATIN,
        OCR_SCRIPT_KOREAN,
        OCR_SCRIPT_CHINESE,
        OCR_SCRIPT_AUTO,
    )

    /** Mode id -> string resource is resolved in the settings screen. */
    val MODES: List<String> = listOf(MODE_LOCAL_OCR, MODE_OVERLAY, MODE_REDRAW)

    /**
     * Text models for overlay mode. Which of them are free depends on Google's current free tier, so the list
     * is only a suggestion. Verify in Google AI Studio.
     */
    const val DEFAULT_TEXT_MODEL = "gemini-3.5-flash-lite"

    val TEXT_MODELS: Map<String, String> = linkedMapOf(
        "gemini-3.8-flash" to "Gemini 3.8 Flash",
        "gemini-3.6-flash" to "Gemini 3.6 Flash",
        "gemini-3.5-flash-lite" to "Gemini 3.5 Flash Lite",
        "gemini-3.1-flash-lite" to "Gemini 3.1 Flash Lite",
    )

    /** Pause between overlay requests, in seconds. */
    val REQUEST_DELAYS: List<Int> = listOf(0, 3, 6, 12)

    const val DEFAULT_LANGUAGE = "English"

    const val ARABIC_STYLE_BALADI = "baladi"
    const val ARABIC_STYLE_MODERN = "modern"
    const val ARABIC_STYLE_CLASSIC = "classic"

    val ARABIC_STYLES: List<String> = listOf(
        ARABIC_STYLE_BALADI,
        ARABIC_STYLE_MODERN,
        ARABIC_STYLE_CLASSIC,
    )

    fun isArabic(language: String): Boolean =
        language.equals("Arabic", ignoreCase = true) ||
        language.contains("ar", ignoreCase = true) ||
        language.contains("عرب", ignoreCase = true)

    fun getArabicPromptGuidelines(arabicStyle: String): String {
        return when (arabicStyle) {
            ARABIC_STYLE_BALADI -> """
CRITICAL DIALOGUE GUIDELINES (العربية البلدية / العامية البيضاء السلسة للمانجا):
- Translate dialogue bubbles into natural, lively colloquial Arabic ('العربية البلدية / العامية البيضاء المفهومة لجميع العرب').
- STRICTLY FORBIDDEN: NEVER translate literally word-for-word (تجنب تماماً الترجمة الحرفية الركيكة).
- STRICTLY FORBIDDEN: NEVER use classical/archaic Fusha phrases in Baladi mode (ممنوع الفصحى القديمة مثل 'أتدرين لماذا / ما هذا الجحيم').

STRICT PRONOUN & GENDER AGREEMENT RULES (قواعد تطابق الضمائر والتذكير والتأنيث الصارمة - إجباري):
1. SEPARATION OF SPEAKER GENDER VS LISTENER GENDER (الفصل الحاسم بين جنس المتكلم وجنس المخاطب):
   - A character's gender only affects their OWN 1st-person self-reference ("I / me / my"):
     * Female speaking about herself: "أنا متأكدة / رايحة / مبسوطة / شفت" (NOT "متأكد / رايح").
   - 2nd-PERSON PRONOUNS AND VERBS ("you / your / yourself") MUST MATCH THE LISTENER (المخاطَب), NEVER THE SPEAKER!
     * When a female character speaks to a MALE character (هي تقول لشاب أو رجل):
       - ALL 2nd-person verbs and pronouns MUST be MASCULINE:
         * "Do you know why?" -> "بتعرف ليش؟" (STRICTLY FORBIDDEN: NEVER use feminine "أتدرين لماذا" or "بتعرفي ليش" when speaking to a male!).
         * "Are you okay?" -> "أنت منيح؟" (NOT "أنتِ منيحة").
         * "Are you sure?" -> "أنت متأكد؟" (NOT "أنتِ متأكدة").
         * "What do you mean?" -> "شو قصدك؟".
     * When speaking to a FEMALE character (الخطاب موجه لأنثى):
       - ALL 2nd-person verbs and pronouns MUST be FEMININE:
         * "Do you know why?" -> "بتعرفي ليش؟".
         * "Are you okay?" -> "أنتِ منيحة؟".
         * "Are you sure?" -> "أنتِ متأكدة؟".
   - CRITICAL WARNING: DO NOT confuse the speaker's gender with the listener's gender! (لا تخلط بين جنس المتحدث وجنس المستمع).

2. DEFAULT TO MASCULINE WHEN LISTENER GENDER IS UNKNOWN (التذكير الافتراضي عند عدم وضوح جنس المخاطب):
   - In Arabic, the universal default when the listener's gender is unknown or ambiguous is MASCULINE (الأصل في المخاطب هو التذكير):
     * Translate "Do you know why?" as "بتعرف ليش؟".
     * NEVER use feminine "أتدرين لماذا" or "بتعرفي" unless it is 100% clear from the scene that the listener is female!

3. 1st Person Speaker ("I / me / my / myself"):
   - When a character speaks about themselves, all attached pronouns, possessives, and verbs MUST be first person:
     * "on my way / on the way" -> "بطريقي" (FORBIDDEN: NEVER use 3rd person "طريقه" or "طريقها" when the speaker says "I").
     * "While I was on my way out" -> "وأنا طالع / وأنا بطريقي لبرا" (FORBIDDEN: NEVER say "بينما كنت انا في طريقه للخروج").
     * "my car" -> "سيارتي" (NEVER "سيارته").
     * "in my house" -> "ببيتي" (NEVER "ببيته").
   - NEVER mix first-person ("كنت / أنا") with third-person ("طريقه / معه") for the same subject!

4. Natural Phrasing (حذف الركاكة وحشو الضمائر):
   - Never use "بينما كنت أنا..." -> use natural spoken dialogue: "وأنا...", "لما كنت...", "وقت ما...".
   - Avoid clunky detached pronouns ("كنت أنا") when the verb already conveys the subject.

5. Common Spoken Dialogue & Comic Idioms (عامية بلدية بيضاء):
   * 'Do you know why?' -> 'بتعرف ليش؟' (للذكر) / 'بتعرفي ليش؟' (للأنثى) - (NEVER 'أتدرين لماذا'!)
   * 'Do you know...?' -> 'بتعرف...؟ / عارف...؟'
   * 'What do you think?' -> 'شو رأيك؟'
   * 'What are you talking about?' -> 'عن شو عم تحكي؟! / شو هالحكي?!'
   * 'What do you mean?' -> 'شو قصدك؟'
   * 'What are you doing?' -> 'شو عم تعمل؟' (للذكر) / 'شو عم تعملي؟' (للأنثى)
   * 'Do you understand?' -> 'فهمت علي؟' (للذكر) / 'فهمتي علي؟' (للأنثى)
   * 'Don't worry' -> 'لا تاكل هم / ولا يهمك'
   * 'What the heck / What the hell' -> 'شو هاد؟! / شو عم بصير؟! / شو في?!' (NEVER 'ما هذا الجحيم')
   * 'Are you kidding me?' -> 'عم تمزح معي؟! / بتمزح معي؟!'
   * 'Are you crazy / out of your mind?' -> 'أنت انجنّيت؟! / شو صاير بمخك؟!'
   * 'Give me a break' -> 'ارحمني شوي! / حل عني!'
   * 'Cut it out / Stop it' -> 'خلص عاد! / بلا هبل! / بيكفي!'
   * 'No way!' -> 'مستحيل! / ولا ممكن!'
   * 'Come on!' -> 'يلا عاد! / اخلص!'
   * 'I got this / Leave it to me' -> 'ولا يهمك، سيبها علي! / خليها علي!'
   * 'Hold on / Wait a second' -> 'استنى شوي! / لحظة بس!'
   * 'What's wrong with you?' -> 'شو مالك؟ / شو صاير معك؟'
   * 'Shut up' -> 'اخرس! / اسكت!'
   * 'I don't care' -> 'مش فارقة معي! / ما بيهمني!'
   * 'Screw you / Get lost' -> 'انقلع من هون! / طير من وجهي!'
   * 'Damn it' -> 'اللعنة! / يا الله! / تباً!'
   * 'Check this out' -> 'شوف هالشغلة! / اتفرج!'
- Dialogue must feel authentic, emotional, and punchy, like professional manga and anime dubbed into modern colloquial Arabic.
- Narration, inner thoughts, and game/system caption boxes should use clean, smooth simplified Arabic with consistent grammar.
""".trimIndent()
            ARABIC_STYLE_MODERN -> """
CRITICAL TRANSLATION GUIDELINES (فصحى معاصرة مبسطة وسلسة):
- Translate into modern, fluent, and simplified Standard Arabic (فصحى معاصرة وسلسة).
- STRICT PRONOUN & GENDER AGREEMENT RULES (قواعد تطابق الضمائر والتذكير والتأنيث الصارمة):
  1. SEPARATION OF SPEAKER VS LISTENER (الفصل الحاسم بين جنس المتحدث وجنس المستمع):
     - When a female speaks to a male character (هي تكلم شاباً أو رجلاً):
       * 2nd-person verbs and pronouns MUST be MASCULINE:
         - "Do you know why?" -> "أتدري لماذا؟ / هل تعلم السبب؟" (STRICTLY FORBIDDEN: NEVER say "أتدرين لماذا" when addressing a male!).
         - "Are you alright?" -> "هل أنت بخير؟" (NOT "هل أنتِ بخير؟").
         - "What do you mean?" -> "ماذا تقصد؟".
     - When speaking to a female character: use feminine 2nd person ("أتدرين لماذا؟ / هل أنتِ بخير؟").
  2. DEFAULT TO MASCULINE (التذكير الافتراضي):
     - When the listener's gender is unknown or ambiguous, always default to masculine: "أتدري لماذا؟ / هل تعلم".
     - NEVER use feminine "أتدرين" unless it is 100% confirmed that the listener is female!
  3. 1ST PERSON CONSISTENCY:
     - When speaking in the 1st person ("I / me / my"), keep all possessives and verbs strictly in the 1st person:
       * "on my way / on the way" -> "في طريقي" (NEVER "في طريقه" when the speaker is "أنا").
       * "While I was on my way out" -> "بينما كنتُ في طريقي للخروج / وأنا خارج".
  4. NATURAL EXPRESSIONS:
     * Never translate idioms word-for-word literally. Keep the flow natural, grammatical, and expressive.
""".trimIndent()
            ARABIC_STYLE_CLASSIC -> """
CRITICAL TRANSLATION GUIDELINES (فصحى فصيحة أدبية راقية ورصينة):
- Translate into eloquent, formal Classical Literary Arabic (فصحى أدبية بليغة ورفيعة المستوى تليق بأرقى الروايات والقصص المصورة).
- STRICTLY FORBIDDEN: NEVER translate word-for-word literally (تجنب تماماً الترجمة الحرفية الآلية الركيكة).

قواعد النحو والصرف وتطابق الضمائر والتذكير والتأنيث الصارمة (إجباري):
1. الفصل الحاسم بين جنس المتحدث (Speaker) وجنس المخاطَب (Listener):
   - جنس المتحدث يحدد ضمائر المتكلم فقط ("أنا"):
     * المتحدثة أنثى: "أنا واثقة / مستعدة / سعيدة / رأيت" (تأنيث المتكلم فقط).
   - ضمائر وأفعال المخاطب ("أنتَ / أنتِ / أتدري / أتدرين") تتبع الشخص المُستَمِع (المخاطَب) حصراً:
     * عندما تُخاطب الفتاة رجلاً أو شاباً (هي تقول له):
       - أفعال وضمائر المخاطب تكون للمذكر حصراً:
         * "Do you know why?" -> "أتدري لماذا؟ / هل تعلم السبب؟" (ممنوع منعاً باتاً: "أتدرين لماذا؟" عند مخاطبة رجل!).
         * "Are you alright?" -> "هل أنت بخير؟" (وليس: "هل أنتِ بخير؟").
         * "Are you sure?" -> "أأنت متأكد؟ / هل أنت واثق؟" (وليس: "متأكدة").
         * "What do you mean?" -> "ماذا تقصد؟ / ما الذي تعنيه؟".
     * عندما يُخاطَب شخص أنثى (الخطاب موجه لفتاة أو امرأة):
       - أفعال وضمائر المخاطب تكون للمؤنث:
         * "Do you know why?" -> "أتدرين لماذا؟ / هل تعلمين السبب؟".
         * "Are you alright?" -> "هل أنتِ بخير؟".
   - تحذير صارم: إياك والخلط بين جنس المتحدث وجنس المستمع! تأنيث المتحدث لا يُؤنث فعل المخاطب.

2. قاعدة التذكير الافتراضي عند عدم التيقن من جنس المخاطب (Default to Masculine):
   - الأصل في قواعد اللغة العربية عند عدم وضوح جنس المخاطب هو صيغة المذكر:
     * "Do you know why?" -> "أتدري لماذا؟".
     * لا يجوز بتاتاً استخدام صيغة المؤنث ("أتدرين / هل تعلمين") إلا إذا كان ثابتاً بصرياً أو نصياً 100% أن الشخص المُخاطَب أنثى.

3. تطابق ضمائر المتكلم ومنع التناقض (1st Person Consistency):
   - عند تحدث الشخصية عن نفسها، يجب أن تتطابق كل أفعالها وضمائر ملكيتها مع ضمير المتكلم:
     * "on my way / on the way" -> "في طريقي" (ممنوع منعاً باتاً استخدام ضمير الغائب "في طريقه" عند المتكلم).
     * "While I was on my way out" -> "بينما كنتُ في طريقي للخروج / وأنا خارجٌ" (ممنوع منعاً باتاً: "بينما كنت أنا في طريقه للخروج").
     * "my weapon / my hand" -> "سلاحي / يدي" (وليس: "سلاحه / يده").
   - ممنوع نهائياً الجمع بين ضمير المتكلم وضمير الغائب للشخص نفسه.

4. حذف الحشو وركاكة الترجمة الآلية:
   - في الفصحى الأدبية، تجنب التكرار الركيك للضمائر المنفصلة: قل "كنتُ في طريقي" أو "خرجتُ" بدل "أنا كنت أنا خرجت".
   - لا تترجم التعبيرات الاصطلاحية حرفياً:
     * 'What the heck / What on earth' -> 'ما هذا بحق السماء؟! / ما الذي يجري هنا؟! / ويحك ما هذا?!' (ممنوع: 'ما هذا الجحيم').
     * 'Are you kidding me?' -> 'أتمزح معي؟! / أتهزأ بي؟!'.
     * 'Are you out of your mind?' -> 'أفقدت صوابك؟! / أجننت؟!'.
     * 'Damn it' -> 'تباً! / سحقاً!'.
     * 'No way!' -> 'مستحيل! / كلا ولن يكون!'.
     * 'I got this' -> 'دع الأمر لي! / تكفلتُ به!'.
     * 'Shut up' -> 'اصمت! / اخرس!'.
     * 'Never mind' -> 'لا عليك / انسَ الأمر'.
- اجعل الحوار بليغاً، فصيحاً، متقناً أدبياً ونحوياً، يفيض بالحيوية والمشاعر مثل روائع المانجا والروايات الأدبية الرفيعة.
""".trimIndent()
            else -> ""
        }
    }

    /** Image file extensions (lowercase) that count as pages of a downloaded chapter. */
    val PAGE_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp")

    /**
     * Languages offered as translation targets. The stored value is the English language name,
     * which is inserted into the model prompt as-is.
     */
    val LANGUAGES: List<String> = listOf(
        "English",
        "Arabic",
        "Spanish",
        "French",
        "German",
        "Portuguese",
        "Italian",
        "Russian",
        "Japanese",
        "Korean",
        "Chinese (Simplified)",
        "Chinese (Traditional)",
        "Indonesian",
        "Vietnamese",
        "Thai",
        "Turkish",
        "Polish",
        "Dutch",
        "Ukrainian",
        "Czech",
        "Romanian",
        "Hungarian",
        "Greek",
        "Swedish",
        "Norwegian",
        "Danish",
        "Finnish",
        "Hebrew",
        "Persian",
        "Hindi",
        "Bengali",
        "Urdu",
        "Tamil",
        "Malay",
        "Filipino",
        "Bulgarian",
        "Serbian",
        "Croatian",
        "Catalan",
    )
}
