package mihon.feature.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Screen
import tachiyomi.core.common.Constants
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Discord
import tachiyomi.presentation.core.icons.OpenCollective
import tachiyomi.presentation.core.icons.Patreon

class SupportUsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uriHandler = LocalUriHandler.current

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.label_support_us),
                    navigateUp = navigator::pop,
                )
            },
        ) { paddingValues ->
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(
                    remember(paddingValues) {
                        object : PaddingValues {
                            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
                                return paddingValues.calculateLeftPadding(layoutDirection) +
                                    MaterialTheme.padding.medium
                            }

                            override fun calculateTopPadding(): Dp {
                                return 0.dp
                            }

                            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
                                return paddingValues.calculateRightPadding(layoutDirection) +
                                    MaterialTheme.padding.medium
                            }

                            override fun calculateBottomPadding(): Dp {
                                return 0.dp
                            }
                        }
                    },
                ),
            ) {
                Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))

                Text(
                    text = stringResource(MR.strings.supportUsScreen_perks),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                )

                SupportItem(
                    icon = CustomIcons.Patreon,
                    title = stringResource(MR.strings.supportUsScreen_donationPlatform_patreon),
                    onClick = { uriHandler.openUri(Constants.URL_DONATE_PATREON) },
                )
                SupportItem(
                    icon = CustomIcons.OpenCollective,
                    title = stringResource(MR.strings.supportUsScreen_donationPlatform_opencollective),
                    onClick = { uriHandler.openUri(Constants.URL_DONATE_OPENCOLLECTIVE) },
                )

                Text(
                    text = stringResource(MR.strings.supportUsScreen_currentlySupportedBy, 200),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                )

                Text(
                    text = stringResource(MR.strings.supportUsScreen_contactForDetailsMessage),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                )

                SupportItem(
                    icon = CustomIcons.Discord,
                    title = stringResource(MR.strings.supportUsScreen_contactPlatform),
                    onClick = { uriHandler.openUri(Constants.URL_DISCORD) },
                )

                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }

    @Composable
    private fun SupportItem(
        icon: ImageVector,
        title: String,
        onClick: () -> Unit,
    ) {
        Card {
            TextPreferenceWidget(
                title = title,
                icon = icon,
                widget = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                    )
                },
                onPreferenceClick = onClick,
            )
        }
    }
}
