package eu.kanade.tachiyomi.ui.reader.eink

import android.os.Build
import java.util.Locale

/**
 * Normalized Android build fields used for e-ink device detection.
 */
data class EinkBuildInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val hardware: String,
)

/**
 * Device table derived from koreader/android-luajit-launcher `DeviceInfo.kt`
 * at commit eaed834b6aaa98aa90eebd3253a057036ef31009.
 *
 * Only the e-ink relevant parts are kept.
 */
object EinkDeviceInfo {
    private const val STR_KOBO = "rakutenkobo"
    private const val STR_NTX = "ntx_6sl"
    private const val STR_ROCKCHIP = "rockchip"
    private const val STR_TOLINO = "tolino"

    enum class Id {
        NONE,
        BOYUE_C64P,
        BOYUE_K78W,
        BOYUE_K103,
        BOYUE_P6,
        BOYUE_P61,
        BOYUE_P78,
        BOYUE_P101,
        BOYUE_S62,
        BOYUE_T61,
        BOYUE_T62,
        BOYUE_T65S,
        BOYUE_T78D,
        BOYUE_T80D,
        BOYUE_T80S,
        BOYUE_T103D,
        CREMA,
        CREMA_0650L,
        CREMA_0660L,
        CREMA_0710C,
        CREMA_CARTA_G,
        ENERGY,
        FIDIBOOK,
        HANVON_960,
        HISENSE_TOUCH_LITE,
        HYREAD_GAZE_NOTE,
        HYREAD_GAZE_NOTE_CC,
        HYREAD_MINI6,
        IFLYTEK_AINOTE_X1,
        IFLYTEK_R3,
        INKBOOK,
        INKBOOKFOCUS,
        INKBOOKFOCUS_PLUS,
        INKPALM_PLUS,
        JDREAD,
        LENOVO_SMARTPAPER,
        LINFINY_ENOTE,
        MEEBOOK_M6,
        MEEBOOK_M6C,
        MEEBOOK_M7,
        MEEBOOK_P6,
        MOBISCRIBE_WAVE,
        MOAAN_MIX7,
        MOAAN_W7,
        MOOINKPLUS2C,
        NABUK,
        NOOK,
        NOOK_GL4,
        NOOK_GL4PLUS,
        NOOK_GLPLUS,
        OBOOK_P10D,
        OBOOK_P78D,
        ONYX_C67,
        ONYX_DARWIN5,
        ONYX_DARWIN7,
        ONYX_DARWIN9,
        ONYX_EDISON,
        ONYX_FARADAY2,
        ONYX_FAUST3,
        ONYX_GALILEO2,
        ONYX_GENERIC,
        ONYX_GO_103,
        ONYX_GO_103_2,
        ONYX_GO_COLOR7,
        ONYX_GO6,
        ONYX_GO6GEN2,
        ONYX_GO7,
        ONYX_GO7GEN2,
        ONYX_JDREAD,
        ONYX_KON_TIKI2,
        ONYX_LEAF,
        ONYX_LEAF2,
        ONYX_LEAF5C,
        ONYX_LIVINGSTONE3,
        ONYX_LOMONOSOV,
        ONYX_MAGICBOOK,
        ONYX_MAX,
        ONYX_MAX2_PRO,
        ONYX_MONTECRISTO3,
        ONYX_NOTE,
        ONYX_NOTE3,
        ONYX_NOTE4,
        ONYX_NOTE5,
        ONYX_NOTE_AIR,
        ONYX_NOTE_AIR2,
        ONYX_NOTE_AIR_3,
        ONYX_NOTE_AIR_3C,
        ONYX_NOTE_AIR_4C,
        ONYX_NOTE_AIR_5C,
        ONYX_NOTE_MAX,
        ONYX_NOTE_PRO,
        ONYX_NOTE_S,
        ONYX_NOTE_X2,
        ONYX_NOVA,
        ONYX_NOVA2,
        ONYX_NOVA3,
        ONYX_NOVA3_COLOR,
        ONYX_NOVA_AIR,
        ONYX_NOVA_AIR_2,
        ONYX_NOVA_AIR_C,
        ONYX_NOVA_PRO,
        ONYX_PAGE,
        ONYX_PALMA,
        ONYX_PALMA2,
        ONYX_PALMA2_PRO,
        ONYX_POKE2,
        ONYX_POKE3,
        ONYX_POKE4,
        ONYX_POKE5,
        ONYX_POKE5S,
        ONYX_POKE6,
        ONYX_POKE4LITE,
        ONYX_POKE_PRO,
        ONYX_ROBINSON_CRUSOE2,
        ONYX_TAB_ULTRA,
        ONYX_TAB_ULTRA_C,
        ONYX_TAB_ULTRA_C_PRO,
        ONYX_TAB_X_C,
        ONYX_VASCO_GAMA4,
        PUBU_PUBOOK,
        RIDI_PAPER_3,
        SONY_CP1,
        SONY_RP1,
        STORYTEL_READER2,
        TAGUS_GEA,
        TOLINO,
        TOLINO_EPOS1,
        TOLINO_EPOS2,
        TOLINO_EPOS3,
        TOLINO_PAGE2,
        TOLINO_SHINE3,
        TOLINO_SHINE4,
        TOLINO_VISION4,
        TOLINO_VISION5,
        TOLINO_VISION6,
        TOPJOY_E602,
        XIAOMI_READER,
    }

    fun fromAndroid(): Id = detect(
        EinkBuildInfo(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
        ),
    )

    fun detect(build: EinkBuildInfo): Id {
        val manufacturer = build.manufacturer.lowercase(Locale.US)
        val brand = build.brand.lowercase(Locale.US)
        val model = build.model.lowercase(Locale.US)
        val device = build.device.lowercase(Locale.US)
        val product = build.product.lowercase(Locale.US)
        val hardware = build.hardware.lowercase(Locale.US)

        val boyue = manufacturer == "boeye" || manufacturer == "boyue"
        val crema = brand == "crema"

        return when {
            // Boyue C64P (Boyue P6 Clone)
            brand == "c64p" && product == "c64p"
            -> Id.BOYUE_C64P

            // Boyue Likebook Ares
            boyue && (product == "k78w" || product == "ares")
            -> Id.BOYUE_K78W

            // Boyue Likebook Alita
            boyue && (product == "k103" || product == "alita")
            -> Id.BOYUE_K103

            // Boyue Likebook P6
            boyue && product == "p6"
            -> Id.BOYUE_P6

            // Boyue Lemon
            boyue && product == "p61-k12-l"
            -> Id.BOYUE_P61

            // Boyue Likebook P78
            boyue && product == "p78"
            -> Id.BOYUE_P78

            // Boyue Likebook P101
            boyue && product == "p101"
            -> Id.BOYUE_P101

            // Boyue Likebook LemonRead S62A
            boyue && product == "s62"
            -> Id.BOYUE_S62

            // Boyue T61
            boyue &&
                (product.startsWith("t61") || model == "rk30sdk") &&
                device.startsWith("t61")
            -> Id.BOYUE_T61

            // Boyue T62
            (boyue && product.startsWith("t62")) || (model == "rk30sdk" && device.startsWith("t62"))
            -> Id.BOYUE_T62

            // Boyue/JDRead T65S
            boyue && product == "t65s"
            -> Id.BOYUE_T65S

            // Boyue Likebook Muses
            boyue && (product == "t78d" || product == "muses")
            -> Id.BOYUE_T78D

            // Boyue Likebook Mars
            boyue && (product == "t80d" || product == "mars")
            -> Id.BOYUE_T80D

            // Boyue Likebook Plus
            boyue && product == "t80s"
            -> Id.BOYUE_T80S

            // Boyue Likebook Mimas
            boyue && (product == "t103d" || product == "mimas")
            -> Id.BOYUE_T103D

            // Crema Note (1010P)
            crema && product == "note"
            -> Id.CREMA

            // Crema Carta+
            crema && product == "keplerb"
            -> Id.CREMA_0650L

            // Crema Soundup
            crema && product == "sound2"
            -> Id.CREMA_0660L

            // Crema Grande
            crema && model == "crema-0710c"
            -> Id.CREMA_0710C

            // Crema Carta G
            crema && model == "crema-0670c"
            -> Id.CREMA_CARTA_G

            // Energy Sistem eReaders. Tested on Energy Ereader Pro 4
            (brand == "energysistem" || brand == "energy_sistem") && model.startsWith("ereader")
            -> Id.ENERGY

            // Fidibook
            manufacturer == "fidibo" && model == "fidibook"
            -> Id.FIDIBOOK

            // Hanvon 960
            brand == "freescale" && product == "evk_6sl_eink"
            -> Id.HANVON_960

            // Hisense Touch Lite
            brand == "hisense" && model == "hitv205n"
            -> Id.HISENSE_TOUCH_LITE

            // Hyread Gaze Note
            manufacturer == "hyread" && model == "r08p"
            -> Id.HYREAD_GAZE_NOTE

            // Hyread Gaze Note Plus CC
            manufacturer == "hyread" && model == "k08cc"
            -> Id.HYREAD_GAZE_NOTE_CC

            // Hyread Mini 6
            manufacturer == "hyread" && model == "k06nu"
            -> Id.HYREAD_MINI6

            // iFlytek AiNote X1 (rk3288, HTFYUN panel).
            // The stock reader identifies it purely as Build.MODEL == "AInote"
            // (DevTypeUtils.isAINoteX1), so match on that plus the rockchip
            // board to stay unambiguous.
            model == "ainote" && manufacturer == STR_ROCKCHIP && hardware == "rk30board"
            -> Id.IFLYTEK_AINOTE_X1

            // Iflytek ebook r3
            manufacturer == "iflytek" && model == "iflytek ebook r3"
            -> Id.IFLYTEK_R3

            // Artatech Inkbook Prime/Prime HD.
            manufacturer == "artatech" && brand == "inkbook" && model.startsWith("prime")
            -> Id.INKBOOK

            // InkBook Focus
            device == "px30_eink" && model == "focus"
            -> Id.INKBOOKFOCUS

            // InkBook Focus Plus
            device == "rk3566_eink" && model == "focus plus"
            -> Id.INKBOOKFOCUS_PLUS

            // InkPalm Plus
            manufacturer == STR_ROCKCHIP && model == "inkpalmplus"
            -> Id.INKPALM_PLUS

            // JDRead1
            manufacturer == "onyx" && model == "jdread"
            -> Id.JDREAD

            // Lenovo SmartPaper
            "lenovo".equals(brand, ignoreCase = true) && "lenovo sp101fu".equals(model, ignoreCase = true)
            -> Id.LENOVO_SMARTPAPER

            // Linfiny A4 (13.3") eNote / Avalue ENT-13T1 / QuirkLogic Papyr
            manufacturer == "linfiny" && model == "ent-13t1"
            -> Id.LINFINY_ENOTE

            // Meebook M6
            manufacturer == "haoqing" && model == "m6"
            -> Id.MEEBOOK_M6

            // Meebook M6C
            manufacturer == "haoqing" && model == "m6c"
            -> Id.MEEBOOK_M6C

            // Meebook M7
            manufacturer == "haoqing" && model == "m7"
            -> Id.MEEBOOK_M7

            // Meebook P6
            manufacturer == "haoqing" && model == "p6"
            -> Id.MEEBOOK_P6

            // Mobiscribe Wave
            brand == "allwinner" && model == "wave" && device == "e70p24_android"
            -> Id.MOBISCRIBE_WAVE

            // Moaan Mix7
            manufacturer == STR_ROCKCHIP && model == "moaanmix7"
            -> Id.MOAAN_MIX7

            // Moaan W7
            brand == "allwinner" && model == "epd103" && device == "virgo-perf1" && hardware == "sun8iw15p1"
            -> Id.MOAAN_W7

            // Mooink Plus 2c
            brand == "allwinner" && model == "mooink plus 2c"
            -> Id.MOOINKPLUS2C

            // Nabuk Regal HD
            manufacturer == "onyx" && model == "nabukreg_hd"
            -> Id.NABUK

            // Nook Glowlight 4 / 4e
            manufacturer == "barnesandnoble" &&
                (model == "bnrv1000" || model == "bnrv1100")
            -> Id.NOOK_GL4

            // Nook Glowlight 4 Plus (AllWinner "Emperor" platform)
            manufacturer == "barnesandnoble" && model == "bnrv1300"
            -> Id.NOOK_GL4PLUS

            // Nook Glowlight plus 7.8" (2019)
            manufacturer == "barnesandnoble" && model == "bnrv700" && product == "ntx_6sl"
            -> Id.NOOK_GLPLUS

            // Nook (catch them all fallback for all other models)
            (manufacturer == "barnesandnoble" || manufacturer == "freescale") &&
                (
                    model == "bnrv510" || model == "bnrv520" || model == "bnrv700" ||
                        model == "evk_mx6sl" || model.startsWith("ereader")
                    )
            -> Id.NOOK

            // OBOOK P10D
            manufacturer == STR_ROCKCHIP && model == "p10d"
            -> Id.OBOOK_P10D

            // OBOOK P78D
            manufacturer == STR_ROCKCHIP && product == "rk3566_78d" && model == "p78d"
            -> Id.OBOOK_P78D

            // Onyx C67
            manufacturer == "onyx" &&
                (product.startsWith("c67") || model == "rk30sdk") &&
                device.startsWith("c67")
            -> Id.ONYX_C67

            // ONYX DARWIN 5
            (manufacturer == "onyx" && brand == "maccentre") || model == "mc_c68pctm"
            -> Id.ONYX_DARWIN5

            // ONYX DARWIN 7
            manufacturer == "onyx" &&
                (product == "mc_darwin7" || product == "darwin7") &&
                (device == "mc_darwin7" || device == "darwin7")
            -> Id.ONYX_DARWIN7

            // ONYX DARWIN 9
            manufacturer == "onyx" &&
                (product == "mc_darwin9" || product == "darwin9") &&
                (device == "mc_darwin9" || device == "darwin9")
            -> Id.ONYX_DARWIN9

            // Onyx Edison
            manufacturer == "onyx" && product == "edison" && device == "edison"
            -> Id.ONYX_EDISON

            // Onyx Faust 3
            manufacturer == "onyx" && product == "mc_faust3" && device == "mc_faust3"
            -> Id.ONYX_FAUST3

            // Onyx Boox Faraday 2
            manufacturer == "onyx" && model == "faraday2"
            -> Id.ONYX_FARADAY2

            // Onyx Boox Galileo 2
            brand == "onyx" && model == "galileo2"
            -> Id.ONYX_GALILEO2

            // Onyx Boox Go 10.3
            brand == "onyx" && model == "go103"
            -> Id.ONYX_GO_103

            // Onyx Boox Go 10.3 Gen II ("Lumi")
            brand == "onyx" && model == "go103_2lumi"
            -> Id.ONYX_GO_103_2

            // Onyx Boox Go Color 7
            brand == "onyx" && model == "gocolor7"
            -> Id.ONYX_GO_COLOR7

            // Onyx Boox Go Color 7 Gen II
            brand == "onyx" && model == "gocolor7_2"
            -> Id.ONYX_GO7GEN2

            // Onyx Boox Go 6
            brand == "onyx" && model == "go6"
            -> Id.ONYX_GO6

            // Onyx Boox Go 6 Gen II
            brand == "onyx" && model == "go6_2"
            -> Id.ONYX_GO6GEN2

            // Onyx Boox Go 7
            brand == "onyx" && model == "go7"
            -> Id.ONYX_GO7

            // Onyx JDRead
            brand == "onyx" && model == "jdread"
            -> Id.ONYX_JDREAD

            // Onyx Kon-Tiki 2
            manufacturer == "onyx" && product == "kon_tiki2" && device == "kon_tiki2"
            -> Id.ONYX_KON_TIKI2

            // Onyx Leaf
            manufacturer == "onyx" && product == "leaf" && device == "leaf"
            -> Id.ONYX_LEAF

            // Onyx Leaf 2 && Onyx Leaf 2 Plus
            manufacturer == "onyx" &&
                (product == "leaf2" || product == "leaf2_p") &&
                (device == "leaf2" || device == "leaf2_p")
            -> Id.ONYX_LEAF2

            // Onyx Boox Leaf 5c
            manufacturer == "onyx" && device == "leaf5c"
            -> Id.ONYX_LEAF5C

            // Onyx Boox Livingstone3
            manufacturer == "onyx" && device == "livingstone3"
            -> Id.ONYX_LIVINGSTONE3

            // Onyx Lomonosov
            manufacturer == "onyx" && device == "lomonosov"
            -> Id.ONYX_LOMONOSOV

            // Onyx MagicBook
            manufacturer == "onyx" && brand == "magicbook"
            -> Id.ONYX_MAGICBOOK

            // Onyx Max
            manufacturer == "onyx" && product == "max" && device == "max"
            -> Id.ONYX_MAX

            // Onyx Max2 Pro
            manufacturer == "onyx" && product == "max2pro" && device == "max2pro"
            -> Id.ONYX_MAX2_PRO

            // Onyx Montecristo 3
            manufacturer == "onyx" && product == "mc_kepler_c" && device == "mc_kepler_c"
            -> Id.ONYX_MONTECRISTO3

            // Onyx Robinson Crusoe 2 (RK3026, same platform as Montecristo 3)
            manufacturer == "onyx" && product == "mc_kepler_r2" && device == "mc_kepler_r2"
            -> Id.ONYX_ROBINSON_CRUSOE2

            // Onyx Note
            manufacturer == "onyx" && product == "note" && device == "note"
            -> Id.ONYX_NOTE

            // Onyx Note 3
            manufacturer == "onyx" && product == "note3" && device == "note3"
            -> Id.ONYX_NOTE3

            // Onyx Note 4
            manufacturer == "onyx" && model == "mc_note4"
            -> Id.ONYX_NOTE4

            // Onyx Note 5
            brand == "onyx" && product == "note5" && device == "note5"
            -> Id.ONYX_NOTE5

            // Onyx Note Air
            manufacturer == "onyx" && product == "noteair" && device == "noteair"
            -> Id.ONYX_NOTE_AIR

            // Onyx Note Air 2 && Note Air 2 Plus
            brand == "onyx" && (model == "noteair2" || model == "noteair2p")
            -> Id.ONYX_NOTE_AIR2

            // Onyx Note Air 3
            brand == "onyx" && model == "noteair3"
            -> Id.ONYX_NOTE_AIR_3

            // Onyx Note Air 3C
            brand == "onyx" && model == "noteair3c"
            -> Id.ONYX_NOTE_AIR_3C

            // Onyx Note Air 4C
            brand == "onyx" && model == "noteair4c"
            -> Id.ONYX_NOTE_AIR_4C

            // Onyx Note Air 5C
            brand == "onyx" && model == "noteair5c"
            -> Id.ONYX_NOTE_AIR_5C

            // Onyx Boox Note Max
            brand == "onyx" && product == "notemax" && device == "notemax"
            -> Id.ONYX_NOTE_MAX

            // Onyx Note Pro
            manufacturer == "onyx" && product == "notepro" && device == "notepro"
            -> Id.ONYX_NOTE_PRO

            // Onyx Note S
            manufacturer == "onyx" && model == "notes"
            -> Id.ONYX_NOTE_S

            // Onyx Note X2
            manufacturer == "onyx" && model == "notex2"
            -> Id.ONYX_NOTE_X2

            // Onyx Nova
            manufacturer == "onyx" && product == "nova" && device == "nova"
            -> Id.ONYX_NOVA

            // Onyx Nova 2
            manufacturer == "onyx" && product == "nova2" && device == "nova2"
            -> Id.ONYX_NOVA2

            // Onyx Nova 3
            manufacturer == "onyx" && product == "nova3" && device == "nova3"
            -> Id.ONYX_NOVA3

            // Onyx Nova 3 Color
            manufacturer == "onyx" && model == "nova3color"
            -> Id.ONYX_NOVA3_COLOR

            // Onyx Nova Air
            manufacturer == "onyx" && model == "novaair"
            -> Id.ONYX_NOVA_AIR

            // Onyx Nova Air 2
            manufacturer == "onyx" && model == "novaair2"
            -> Id.ONYX_NOVA_AIR_2

            // Onyx Nova Air C
            brand == "onyx" && model == "novaairc"
            -> Id.ONYX_NOVA_AIR_C

            // Onyx Nova Pro
            brand == "onyx" && model == "novapro"
            -> Id.ONYX_NOVA_PRO

            // Onyx Page
            brand == "onyx" && model == "page"
            -> Id.ONYX_PAGE

            // Onyx Palma
            brand == "onyx" && model == "palma"
            -> Id.ONYX_PALMA

            // Onyx Palma2
            brand == "onyx" && model == "palma2"
            -> Id.ONYX_PALMA2

            // Onyx Palma2 Pro (Palma2_Pro_C)
            brand == "onyx" && model == "palma2_pro_c"
            -> Id.ONYX_PALMA2_PRO

            // Onyx Poke 2
            manufacturer == "onyx" && product == "poke2"
            -> Id.ONYX_POKE2

            // Onyx Poke 3
            manufacturer == "onyx" && product == "poke3" && device == "poke3"
            -> Id.ONYX_POKE3

            // Onyx Poke 4
            brand == "onyx" && model == "poke4"
            -> Id.ONYX_POKE4

            // Onyx Poke 4 lite
            brand == "onyx" && model == "poke4lite"
            -> Id.ONYX_POKE4LITE

            // Onyx Poke 5
            brand == "onyx" && model == "poke5p"
            -> Id.ONYX_POKE5

            // Onyx Poke 5s
            brand == "onyx" && model == "poke5s"
            -> Id.ONYX_POKE5S

            // Onyx Poke 6
            brand == "onyx" && model == "poke6"
            -> Id.ONYX_POKE6

            // Onyx Poke Pro
            manufacturer == "onyx" && product == "poke_pro"
            -> Id.ONYX_POKE_PRO

            // Onyx Tab Ultra
            manufacturer == "onyx" && model == "tabultra"
            -> Id.ONYX_TAB_ULTRA

            // Onyx Tab Ultra C
            manufacturer == "onyx" && model == "tabultrac"
            -> Id.ONYX_TAB_ULTRA_C

            // Onyx Tab Ultra C Pro
            brand == "onyx" && product == "tabultracpro"
            -> Id.ONYX_TAB_ULTRA_C_PRO

            // Onyx Tab X C
            brand == "onyx" && product == "tabxc" && device == "tabxc"
            -> Id.ONYX_TAB_X_C

            // Onyx Boox Vasco da Gama 4
            manufacturer == "onyx" && device == "mc_gama4"
            -> Id.ONYX_VASCO_GAMA4

            // Pubu Pubook
            manufacturer == STR_ROCKCHIP && brand == STR_ROCKCHIP && model == "pubook" && device == "pubook" &&
                hardware == "rk30board"
            -> Id.PUBU_PUBOOK

            // Ridi Paper 3
            brand == "ridi" && model == "ridipaper" && product == "rp1"
            -> Id.RIDI_PAPER_3

            // Sony DPT-CP1
            manufacturer == "sony" && model == "dpt-cp1"
            -> Id.SONY_CP1

            // Sony DPT-RP1
            manufacturer == "sony" && model == "dpt-rp1"
            -> Id.SONY_RP1

            // Storytel Reader 2
            manufacturer == "storytel" && model == "reader 2"
            -> Id.STORYTEL_READER2

            // Tagus Gea
            manufacturer == "onyx" && model == "tagus_pokep"
            -> Id.TAGUS_GEA

            // Tolino Epos 1
            brand == STR_KOBO && model == STR_TOLINO && device == STR_NTX && hardware == "e70q20"
            -> Id.TOLINO_EPOS1

            // Tolino Epos 2
            brand == STR_KOBO && model == STR_TOLINO && device == STR_NTX && hardware == "e80k00"
            -> Id.TOLINO_EPOS2

            // Tolino Epos 3
            brand == STR_KOBO && model == "tolino epos 3"
            -> Id.TOLINO_EPOS3

            // Tolino Page 2 has no warmth lights
            brand == STR_KOBO && model == STR_TOLINO && device == STR_NTX && hardware == "e60qv0"
            -> Id.TOLINO_PAGE2

            // Tolino Shine 3 also has warmth lights, but with ntx_io file
            brand == STR_KOBO && model == STR_TOLINO && device == STR_NTX && hardware == "e60k00"
            -> Id.TOLINO_SHINE3

            // Tolino Shine 4
            brand == STR_KOBO && model == "tolino shine 4" && device == STR_TOLINO && hardware == "sun8iw15p1"
            -> Id.TOLINO_SHINE4

            // Tolino Vision 4 also has warmth lights, but with ntx_io file
            brand == STR_KOBO && model == STR_TOLINO && device == STR_NTX && hardware == "e60q50"
            -> Id.TOLINO_VISION4

            // Tolino Vision 5 also has warmth lights, but with ntx_io file
            brand == STR_KOBO && model == STR_TOLINO && device == STR_NTX && hardware == "e70k00"
            -> Id.TOLINO_VISION5

            // Tolino Vision 6
            brand == STR_KOBO && model == "tolino vision 6" && device == STR_TOLINO && hardware == "sun8iw15p1"
            -> Id.TOLINO_VISION6

            // Tolino (catch them all fallback for all other models)
            (brand == STR_TOLINO && model == "imx50_rdp") ||
                (model == STR_TOLINO && (device == "tolino_vision2" || device == STR_NTX))
            -> Id.TOLINO

            // Topjoy
            manufacturer == "topjoy" && model == "e602"
            -> Id.TOPJOY_E602

            // Xiaomi
            manufacturer == "xiaomi" && brand == "xiaomi" && model == "xiaomi_reader" && device == "rk3566_eink" &&
                hardware == "rk30board"
            -> Id.XIAOMI_READER

            // Generic Onyx fallback: unlisted/newer models expose the same Onyx EPD SDK.
            manufacturer == "onyx" || brand == "onyx"
            -> Id.ONYX_GENERIC

            // ???
            else -> Id.NONE
        }
    }
}
