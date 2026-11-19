package eu.kanade.tachiyomi.data.track

import android.content.Context
import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import tachiyomi.domain.track.interactor.UpsertTrack

@Inject
class TrackerDependencies(
    val context: Context,
    val trackPreferences: TrackPreferences,
    val networkHelper: Lazy<NetworkHelper>,
    val addTracks: Lazy<AddTracks>,
    val upsertTrack: Lazy<UpsertTrack>,
)
