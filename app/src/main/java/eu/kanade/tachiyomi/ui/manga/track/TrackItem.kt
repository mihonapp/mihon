package eu.kanade.tachiyomi.ui.manga.track

import eu.kanade.tachiyomi.data.track.TrackService
import tachiyomi.domain.track.model.Track

data class TrackItem(val track: Track?, val service: TrackService)
