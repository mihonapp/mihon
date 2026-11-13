package eu.kanade.tachiyomi.ui.reader.eink

import android.util.Log
import eu.kanade.tachiyomi.ui.reader.eink.epd.OnyxEPDController
import eu.kanade.tachiyomi.ui.reader.eink.epd.RK3026EPDController
import eu.kanade.tachiyomi.ui.reader.eink.epd.RK3368EPDController
import eu.kanade.tachiyomi.ui.reader.eink.epd.RK3566EPDController

/**
 * Maps a detected [EinkDeviceInfo.Id] to a backend.
 *
 * Adding a backend is intentionally a two-line change: register the ID group here and wrap the
 * ported controller in [EpdEinkRefreshBackend].
 */
object EinkBackendFactory {
    private const val TAG = "EinkRefresh"

    fun create(id: EinkDeviceInfo.Id = EinkDeviceInfo.fromAndroid()): EinkRefreshBackend {
        val backend = when (id) {
            EinkDeviceInfo.Id.BOYUE_T61,
            EinkDeviceInfo.Id.BOYUE_T62,
            EinkDeviceInfo.Id.BOYUE_T80S,
            EinkDeviceInfo.Id.CREMA_0650L,
            EinkDeviceInfo.Id.ENERGY,
            EinkDeviceInfo.Id.FIDIBOOK,
            EinkDeviceInfo.Id.INKBOOK,
            EinkDeviceInfo.Id.INKBOOKFOCUS,
            EinkDeviceInfo.Id.MEEBOOK_P6,
            EinkDeviceInfo.Id.ONYX_C67,
            EinkDeviceInfo.Id.ONYX_MAGICBOOK,
            EinkDeviceInfo.Id.ONYX_MONTECRISTO3,
            EinkDeviceInfo.Id.ONYX_ROBINSON_CRUSOE2,
            EinkDeviceInfo.Id.TOPJOY_E602,
            -> EpdEinkRefreshBackend(RK3026EPDController())

            EinkDeviceInfo.Id.BOYUE_C64P,
            EinkDeviceInfo.Id.BOYUE_K78W,
            EinkDeviceInfo.Id.BOYUE_K103,
            EinkDeviceInfo.Id.BOYUE_P6,
            EinkDeviceInfo.Id.BOYUE_P61,
            EinkDeviceInfo.Id.BOYUE_P78,
            EinkDeviceInfo.Id.BOYUE_P101,
            EinkDeviceInfo.Id.BOYUE_S62,
            EinkDeviceInfo.Id.BOYUE_T78D,
            EinkDeviceInfo.Id.BOYUE_T80D,
            EinkDeviceInfo.Id.BOYUE_T103D,
            EinkDeviceInfo.Id.HYREAD_GAZE_NOTE,
            -> EpdEinkRefreshBackend(RK3368EPDController())

            EinkDeviceInfo.Id.HYREAD_GAZE_NOTE_CC,
            EinkDeviceInfo.Id.HYREAD_MINI6,
            EinkDeviceInfo.Id.IFLYTEK_R3,
            EinkDeviceInfo.Id.INKBOOKFOCUS_PLUS,
            EinkDeviceInfo.Id.INKPALM_PLUS,
            EinkDeviceInfo.Id.MEEBOOK_M6,
            EinkDeviceInfo.Id.MEEBOOK_M6C,
            EinkDeviceInfo.Id.MEEBOOK_M7,
            EinkDeviceInfo.Id.MOAAN_MIX7,
            EinkDeviceInfo.Id.OBOOK_P10D,
            EinkDeviceInfo.Id.OBOOK_P78D,
            EinkDeviceInfo.Id.PUBU_PUBOOK,
            EinkDeviceInfo.Id.XIAOMI_READER,
            -> EpdEinkRefreshBackend(RK3566EPDController())

            EinkDeviceInfo.Id.ONYX_DARWIN5,
            EinkDeviceInfo.Id.ONYX_DARWIN9,
            EinkDeviceInfo.Id.ONYX_EDISON,
            EinkDeviceInfo.Id.ONYX_FARADAY2,
            EinkDeviceInfo.Id.ONYX_GALILEO2,
            EinkDeviceInfo.Id.ONYX_GO_103,
            EinkDeviceInfo.Id.ONYX_GO_103_2,
            EinkDeviceInfo.Id.ONYX_GO6,
            EinkDeviceInfo.Id.ONYX_GO6GEN2,
            EinkDeviceInfo.Id.ONYX_GO7,
            EinkDeviceInfo.Id.ONYX_GO7GEN2,
            EinkDeviceInfo.Id.ONYX_KON_TIKI2,
            EinkDeviceInfo.Id.ONYX_LEAF,
            EinkDeviceInfo.Id.ONYX_LEAF2,
            EinkDeviceInfo.Id.ONYX_LEAF5C,
            EinkDeviceInfo.Id.ONYX_LIVINGSTONE3,
            EinkDeviceInfo.Id.ONYX_LOMONOSOV,
            EinkDeviceInfo.Id.ONYX_MAX,
            EinkDeviceInfo.Id.ONYX_MAX2_PRO,
            EinkDeviceInfo.Id.ONYX_NOTE,
            EinkDeviceInfo.Id.ONYX_NOTE3,
            EinkDeviceInfo.Id.ONYX_NOTE4,
            EinkDeviceInfo.Id.ONYX_NOTE5,
            EinkDeviceInfo.Id.ONYX_NOTE_AIR,
            EinkDeviceInfo.Id.ONYX_NOTE_AIR2,
            EinkDeviceInfo.Id.ONYX_NOTE_AIR_3,
            EinkDeviceInfo.Id.ONYX_NOTE_AIR_3C,
            EinkDeviceInfo.Id.ONYX_NOTE_AIR_4C,
            EinkDeviceInfo.Id.ONYX_NOTE_AIR_5C,
            EinkDeviceInfo.Id.ONYX_NOTE_MAX,
            EinkDeviceInfo.Id.ONYX_NOTE_PRO,
            EinkDeviceInfo.Id.ONYX_NOTE_S,
            EinkDeviceInfo.Id.ONYX_NOTE_X2,
            EinkDeviceInfo.Id.ONYX_NOVA,
            EinkDeviceInfo.Id.ONYX_NOVA2,
            EinkDeviceInfo.Id.ONYX_NOVA3,
            EinkDeviceInfo.Id.ONYX_NOVA3_COLOR,
            EinkDeviceInfo.Id.ONYX_NOVA_AIR,
            EinkDeviceInfo.Id.ONYX_NOVA_AIR_2,
            EinkDeviceInfo.Id.ONYX_NOVA_AIR_C,
            EinkDeviceInfo.Id.ONYX_NOVA_PRO,
            EinkDeviceInfo.Id.ONYX_PAGE,
            EinkDeviceInfo.Id.ONYX_PALMA,
            EinkDeviceInfo.Id.ONYX_PALMA2,
            EinkDeviceInfo.Id.ONYX_PALMA2_PRO,
            EinkDeviceInfo.Id.ONYX_POKE2,
            EinkDeviceInfo.Id.ONYX_POKE3,
            EinkDeviceInfo.Id.ONYX_POKE4,
            EinkDeviceInfo.Id.ONYX_POKE5,
            EinkDeviceInfo.Id.ONYX_POKE5S,
            EinkDeviceInfo.Id.ONYX_POKE6,
            EinkDeviceInfo.Id.ONYX_POKE4LITE,
            EinkDeviceInfo.Id.ONYX_POKE_PRO,
            EinkDeviceInfo.Id.ONYX_TAB_ULTRA,
            EinkDeviceInfo.Id.ONYX_TAB_ULTRA_C,
            EinkDeviceInfo.Id.ONYX_TAB_ULTRA_C_PRO,
            EinkDeviceInfo.Id.ONYX_TAB_X_C,
            EinkDeviceInfo.Id.ONYX_VASCO_GAMA4,
            EinkDeviceInfo.Id.STORYTEL_READER2,
            -> EpdEinkRefreshBackend(OnyxEPDController())

            else -> NoOpEinkRefreshBackend
        }

        Log.i(TAG, "backend=${backend.platform} modes=${backend.supportedModes}")
        return backend
    }
}
