package eu.kanade.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.track.TrackService

@Composable
fun TrackLogoIcon(
    service: TrackService,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(color = Color(service.getLogoColor()), shape = MaterialTheme.shapes.medium)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(service.getLogo()),
            contentDescription = stringResource(service.nameRes()),
        )
    }
}
