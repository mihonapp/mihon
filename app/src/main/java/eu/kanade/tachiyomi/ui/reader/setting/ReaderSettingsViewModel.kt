package eu.kanade.tachiyomi.ui.reader.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReaderSettingsViewModel(
    readerState: StateFlow<ReaderViewModel.State>,
    val onChangeReadingMode: (ReadingMode) -> Unit,
    val onChangeOrientation: (ReaderOrientation) -> Unit,
    val onChangeSpread: (Spread) -> Unit,
    val onChangeSpreadForcePairing: (SpreadForcePairing) -> Unit,
    val onChangeSpreadWidePairing: (SpreadWidePairing) -> Unit,
    val onChangeSpreadShift: (SpreadShift) -> Unit,
    val onChangeSpreadVerticalFit: (SpreadVerticalFit) -> Unit,
    val onChangeSpreadSoloPage: (SpreadSoloPage) -> Unit,
    // The per-manga shift override, held at reader-session scope in ReaderViewModel (not in State's manga,
    // which would rebuild the viewer on change); the chip reads its selection from here.
    val spreadShiftForce: StateFlow<SpreadShift>,
    val preferences: ReaderPreferences = Injekt.get(),
) : ViewModel() {

    val viewerFlow = readerState
        .map { it.viewer }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val mangaFlow = readerState
        .map { it.manga }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
}
