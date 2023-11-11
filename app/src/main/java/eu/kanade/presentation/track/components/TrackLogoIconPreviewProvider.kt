package eu.kanade.presentation.track.components

import android.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.test.DummyTracker

internal class TrackLogoIconPreviewProvider : PreviewParameterProvider<Tracker> {

    override val values: Sequence<Tracker>
        get() = sequenceOf(
            DummyTracker(
                id = 1L,
                name = "Dummy Tracker",
                valLogoColor = Color.rgb(18, 25, 35),
                valLogo = R.drawable.ic_tracker_anilist,
            ),
        )
}
