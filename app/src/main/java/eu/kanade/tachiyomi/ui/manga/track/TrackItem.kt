package eu.kanade.tachiyomi.ui.manga.track

import eu.kanade.tachiyomi.data.track.Tracker
import tachiyomi.domain.track.model.Track

data class TrackItem(val track: Track?, val tracker: Tracker)
