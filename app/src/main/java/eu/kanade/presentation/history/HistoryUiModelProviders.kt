package eu.kanade.presentation.history

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import java.time.Instant
import java.util.Date

object HistoryUiModelProviders {

    class HeadNow : PreviewParameterProvider<HistoryUiModel> {
        override val values: Sequence<HistoryUiModel> =
            sequenceOf(HistoryUiModel.Header(Date.from(Instant.now())))
    }
}
