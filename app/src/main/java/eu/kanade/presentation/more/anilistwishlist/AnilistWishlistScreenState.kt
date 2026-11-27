package eu.kanade.presentation.more.anilistwishlist

import androidx.compose.runtime.Immutable
import tachiyomi.domain.anilist.model.AnilistWishlistEntry

sealed interface AnilistWishlistScreenState {
    @Immutable
    data object Loading : AnilistWishlistScreenState

    @Immutable
    data object NotLoggedIn : AnilistWishlistScreenState

    @Immutable
    data class Success(
        val entries: List<AnilistWishlistEntry>,
    ) : AnilistWishlistScreenState

    @Immutable
    data class Error(
        val message: String,
    ) : AnilistWishlistScreenState
}
