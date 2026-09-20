package eu.kanade.tachiyomi.ui.main

import android.animation.ValueAnimator
import android.app.SearchManager
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.result.ResultEventBus
import androidx.navigation3.runtime.result.rememberResultEventBus
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.zacsweers.metro.Inject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.components.DownloadedOnlyBannerBackgroundColor
import eu.kanade.presentation.components.IncognitoModeBannerBackgroundColor
import eu.kanade.presentation.components.IndexingBannerBackgroundColor
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.api.ExtensionApi
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.home.TabEvent
import eu.kanade.tachiyomi.ui.home.TopLevelRoute
import eu.kanade.tachiyomi.ui.setting.addSettingsRoute
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.isBenchmarkBuildType
import eu.kanade.tachiyomi.util.system.isNavigationBarNeedsScrim
import eu.kanade.tachiyomi.util.system.updaterEnabled
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.app.di.appGraph
import mihon.core.metro.metroGraph
import mihon.core.migration.Migrator
import mihon.core.navigation.AssistContentRoute
import mihon.core.navigation.BrowseSourceRoute
import mihon.core.navigation.DeepLinkRoute
import mihon.core.navigation.ExtensionStoresRoute
import mihon.core.navigation.GlobalSearchRoute
import mihon.core.navigation.HomeRoute
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.NewUpdateRoute
import mihon.core.navigation.OnboardingRoute
import mihon.core.navigation.RestoreBackupRoute
import mihon.core.navigation.SettingsRoute
import mihon.core.navigation.SupportUsRoute
import mihon.core.navigation.appEntries
import mihon.core.navigation.util.AssistContentManager
import mihon.core.navigation.util.LocalAssistContentManager
import mihon.core.navigation.util.LocalBackStack
import mihon.core.navigation.util.LocalTopLevelBackStack
import mihon.core.navigation.util.popUntilRoot
import mihon.core.navigation.util.rememberAdaptiveSheetSceneStrategy
import mihon.core.navigation.util.rememberTopLevelBackStack
import mihon.core.navigation.util.rememberTwoPaneSettingsSceneStrategy
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.OpenInNew
import mihon.icons.materialsymbols.rounded.VolunteerActivism
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance
import tachiyomi.core.common.Constants
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.times

class MainActivity : BaseActivity() {

    private val graph: AppGraph by lazy { metroGraph() }

    @Inject private lateinit var libraryPreferences: LibraryPreferences

    @Inject private lateinit var preferences: BasePreferences

    @Inject private lateinit var downloadCache: DownloadCache

    @Inject private lateinit var chapterCache: ChapterCache

    @Inject private lateinit var getIncognitoState: GetIncognitoState

    @Inject private lateinit var extensionApi: ExtensionApi

    @Inject private lateinit var extensionManager: ExtensionManager

    // To be checked by splash screen. If true then splash screen will be removed.
    var ready = false

    private var backStack: NavBackStack<NavKey>? = null

    private val assistContentManager = AssistContentManager()

    init {
        registerSecureActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        graph.inject(this)
        val isLaunch = savedInstanceState == null

        // Prevent splash screen showing up on configuration changes
        val splashScreen = if (isLaunch) installSplashScreen() else null

        super.onCreate(savedInstanceState)

        Migrator.awaitAndRelease()

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot) {
            finish()
            return
        }

        setComposeContent {
            val context = LocalContext.current

            var incognito by remember { mutableStateOf(false) }
            val downloadOnly by preferences.downloadedOnly.collectAsState()
            val indexing by downloadCache.isInitializing.collectAsState()

            val isTabletUi = isTabletUi()
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val statusBarBackgroundColor = when {
                indexing -> IndexingBannerBackgroundColor
                downloadOnly -> DownloadedOnlyBannerBackgroundColor
                incognito -> IncognitoModeBannerBackgroundColor
                else -> MaterialTheme.colorScheme.surface
            }
            LaunchedEffect(isSystemInDarkTheme, statusBarBackgroundColor) {
                // Draw edge-to-edge and set system bars color to transparent
                val lightStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK)
                val darkStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                enableEdgeToEdge(
                    statusBarStyle = if (statusBarBackgroundColor.luminance() > 0.5) lightStyle else darkStyle,
                    navigationBarStyle = if (isSystemInDarkTheme) darkStyle else lightStyle,
                )
            }

            val backStack = rememberNavBackStack(HomeRoute)
            val topLevelBackStack = rememberTopLevelBackStack(TopLevelRoute.Library)
            val twoPaneStrategy = rememberTwoPaneSettingsSceneStrategy<NavKey>()
            val adaptiveSheetSceneStrategy = rememberAdaptiveSheetSceneStrategy<NavKey>()
            val resultEventBus = rememberResultEventBus()
            val resultEventBusNavEntryDecorator = rememberResultEventBusNavEntryDecorator<NavKey>(
                resultEventBus = resultEventBus,
            )

            CompositionLocalProvider(
                LocalBackStack provides backStack,
                LocalTopLevelBackStack provides topLevelBackStack,
                LocalAssistContentManager provides assistContentManager,
            ) {
                LaunchedEffect(backStack) {
                    this@MainActivity.backStack = backStack

                    if (isLaunch) {
                        // Set start screen
                        handleIntentAction(intent, backStack, resultEventBus, isTabletUi)

                        // Reset Incognito Mode on relaunch
                        preferences.incognitoMode.set(false)
                    }
                }

                val currentRoute = backStack.lastOrNull()
                LaunchedEffect(currentRoute) {
                    (currentRoute as? BrowseSourceRoute)?.sourceId
                        .let(getIncognitoState::subscribe)
                        .collectLatest { incognito = it }
                }

                val scaffoldInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                Scaffold(
                    topBar = {
                        AppStateBanners(
                            downloadedOnlyMode = downloadOnly,
                            incognitoMode = incognito,
                            indexing = indexing,
                            modifier = Modifier.windowInsetsPadding(scaffoldInsets),
                        )
                    },
                    contentWindowInsets = scaffoldInsets,
                ) { contentPadding ->
                    // Consume insets already used by app state banners
                    Box {
                        val slideDistance = rememberSlideDistance()
                        NavDisplay(
                            backStack = backStack,
                            onBack = { backStack.removeLastOrNull() },
                            sceneStrategies = listOf(twoPaneStrategy, adaptiveSheetSceneStrategy),
                            transitionSpec = {
                                materialSharedAxisXIn(
                                    forward = true,
                                    slideDistance = slideDistance,
                                ) togetherWith materialSharedAxisXOut(
                                    forward = true,
                                    slideDistance = slideDistance,
                                )
                            },
                            popTransitionSpec = {
                                materialSharedAxisXIn(
                                    forward = false,
                                    slideDistance = slideDistance,
                                ) togetherWith materialSharedAxisXOut(
                                    forward = false,
                                    slideDistance = slideDistance,
                                )
                            },
                            predictivePopTransitionSpec = {
                                materialSharedAxisXIn(
                                    forward = false,
                                    slideDistance = slideDistance,
                                ) togetherWith materialSharedAxisXOut(
                                    forward = false,
                                    slideDistance = slideDistance,
                                )
                            },
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                                resultEventBusNavEntryDecorator,
                            ),
                            entryProvider = entryProvider {
                                appEntries()
                            },
                            modifier = Modifier
                                .padding(contentPadding)
                                .consumeWindowInsets(contentPadding),
                        )

                        // Draw navigation bar scrim when needed
                        if (remember { isNavigationBarNeedsScrim() }) {
                            Spacer(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                    .alpha(0.8f)
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                            )
                        }
                    }
                }

                // Pop source-related routes when incognito mode is turned off
                LaunchedEffect(Unit) {
                    preferences.incognitoMode.changes()
                        .drop(1)
                        .filter { !it }
                        .onEach {
                            val currentRoute = backStack.lastOrNull()
                            if (currentRoute is BrowseSourceRoute ||
                                (currentRoute is MangaRoute && currentRoute.fromSource)
                            ) {
                                while (backStack.size > 1) {
                                    backStack.removeAll { it != HomeRoute }
                                }
                            }
                        }
                        .launchIn(this)
                }

                HandleOnNewIntent(
                    context = context,
                    backStack = backStack,
                    resultEventBus = resultEventBus,
                    isTabletUi = isTabletUi,
                )

                if (!isBenchmarkBuildType) {
                    if (isLaunch) CheckForUpdates()
                    ShowOnboarding()
                    ShowDonationCampaign()
                }
            }
        }

        val startTime = System.currentTimeMillis()
        splashScreen?.setKeepOnScreenCondition {
            val elapsed = System.currentTimeMillis() - startTime
            elapsed <= SPLASH_MIN_DURATION || (!ready && elapsed <= SPLASH_MAX_DURATION)
        }
        setSplashScreenExitAnimation(splashScreen)

        if (isLaunch && libraryPreferences.autoClearChapterCache.get()) {
            lifecycleScope.launchIO {
                chapterCache.clear()
            }
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        when (backStack?.lastOrNull()) {
            is AssistContentRoute -> {
                assistContentManager.currentAssistUrl?.let {
                    outContent.webUri = it.toUri()
                }
            }
        }
    }

    @Composable
    private fun HandleOnNewIntent(
        context: Context,
        backStack: NavBackStack<NavKey>,
        resultEventBus: ResultEventBus,
        isTabletUi: Boolean,
    ) {
        LaunchedEffect(Unit) {
            callbackFlow {
                val componentActivity = context as ComponentActivity
                val consumer = Consumer<Intent> { trySend(it) }
                componentActivity.addOnNewIntentListener(consumer)
                awaitClose { componentActivity.removeOnNewIntentListener(consumer) }
            }
                .collectLatest {
                    handleIntentAction(it, backStack, resultEventBus, isTabletUi)
                }
        }
    }

    @Composable
    private fun CheckForUpdates() {
        val context = LocalContext.current
        val backStack = LocalBackStack.current

        // App updates
        LaunchedEffect(Unit) {
            if (updaterEnabled) {
                try {
                    val result = context.appGraph.updateChecker.checkForUpdate()
                    if (result is GetApplicationRelease.Result.NewUpdate) {
                        val updateRoute = NewUpdateRoute(
                            versionName = result.release.version,
                            changelogInfo = result.release.info,
                            releaseLink = result.release.releaseLink,
                            downloadLink = result.release.downloadLink,
                        )
                        backStack.add(updateRoute)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                }
            }
        }

        // Extensions updates
        LaunchedEffect(Unit) {
            try {
                extensionApi.checkForUpdates(extensionManager.getLoadedExtensions())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    @Composable
    private fun ShowOnboarding() {
        val backStack = LocalBackStack.current

        LaunchedEffect(Unit) {
            if (!preferences.shownOnboardingFlow.get() && backStack.lastOrNull() !is OnboardingRoute) {
                backStack.add(OnboardingRoute)
            }
        }
    }

    @Composable
    private fun ShowDonationCampaign() {
        val backStack = LocalBackStack.current

        var showCampaign by remember { mutableStateOf(false) }
        if (showCampaign) {
            val uriHandler = LocalUriHandler.current
            val dismissSupportMessage = {
                preferences.donationCampaignShown.set(true)
                showCampaign = false
            }
            AdaptiveSheet(
                onDismissRequest = dismissSupportMessage,
                enableImplicitDismiss = false,
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(MR.strings.donationCampaign_title),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = stringResource(MR.strings.donationCampaign_paragraph1),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(MR.strings.donationCampaign_paragraph2),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(MR.strings.donationCampaign_paragraph3),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    HorizontalDivider()

                    Button(
                        modifier = Modifier
                            .padding(top = MaterialTheme.padding.small)
                            .padding(horizontal = MaterialTheme.padding.medium)
                            .fillMaxWidth(),
                        onClick = {
                            backStack.add(SupportUsRoute)
                            dismissSupportMessage()
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.VolunteerActivism,
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(MR.strings.label_support_us),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        modifier = Modifier
                            .padding(bottom = MaterialTheme.padding.small)
                            .padding(horizontal = MaterialTheme.padding.medium),
                    ) {
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onClick = { uriHandler.openUri(Constants.URL_DISCORD) },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                Text(
                                    text = stringResource(MR.strings.donationCampaign_contactPlatform),
                                )
                                Icon(
                                    imageVector = MaterialSymbols.AutoMirroredRounded.OpenInNew,
                                    contentDescription = null,
                                )
                            }
                        }
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onClick = dismissSupportMessage,
                        ) {
                            Text(
                                text = stringResource(MR.strings.donationCampaign_dismiss),
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            try {
                val firstInstallTime = packageManager.getPackageInfo(packageName, 0).firstInstallTime
                val eligibleTime = Instant.fromEpochMilliseconds(firstInstallTime).plus(6 * 30.days)
                showCampaign = (Clock.System.now() >= eligibleTime && !preferences.donationCampaignShown.get())
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
    }

    /**
     * Sets custom splash screen exit animation on devices prior to Android 12.
     *
     * When custom animation is used, status and navigation bar color will be set to transparent and will be restored
     * after the animation is finished.
     */
    @Suppress("Deprecation")
    private fun setSplashScreenExitAnimation(splashScreen: SplashScreen?) {
        val root = findViewById<View>(android.R.id.content)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && splashScreen != null) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            splashScreen.setOnExitAnimationListener { splashProvider ->
                // For some reason the SplashScreen applies (incorrect) Y translation to the iconView
                splashProvider.iconView.translationY = 0F

                val activityAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = LinearOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        root.translationY = value * 16.dpToPx
                    }
                }

                val splashAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = FastOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        splashProvider.view.alpha = value
                    }
                    doOnEnd {
                        splashProvider.remove()
                    }
                }

                activityAnim.start()
                splashAnim.start()
            }
        }
    }

    private fun handleIntentAction(
        intent: Intent,
        backStack: NavBackStack<NavKey>,
        resultEventBus: ResultEventBus,
        isTabletUi: Boolean,
    ): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(
                applicationContext,
                notificationId,
                intent.getIntExtra("groupId", 0),
            )
        }

        val tabToOpen = when (intent.action) {
            Constants.SHORTCUT_LIBRARY -> TabEvent.Library()
            Constants.SHORTCUT_MANGA -> {
                val idToOpen = intent.extras?.getLong(Constants.MANGA_EXTRA) ?: return false
                backStack.popUntilRoot()
                TabEvent.Library(idToOpen)
            }
            Constants.SHORTCUT_UPDATES -> TabEvent.Updates
            Constants.SHORTCUT_HISTORY -> TabEvent.History
            Constants.SHORTCUT_SOURCES -> TabEvent.Browse(false)
            Constants.SHORTCUT_EXTENSIONS -> TabEvent.Browse(true)
            Constants.SHORTCUT_DOWNLOADS -> {
                backStack.popUntilRoot()
                TabEvent.More(toDownloads = true)
            }
            Intent.ACTION_APPLICATION_PREFERENCES -> {
                backStack.popUntilRoot()
                backStack.addSettingsRoute(SettingsRoute(), isTabletUi)
                null
            }
            Intent.ACTION_SEARCH, Intent.ACTION_SEND, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                // If the intent match the "standard" Android search intent
                // or the Google-specific search intent (triggered by saying or typing "search *query* on *Tachiyomi*" in Google Search/Google Assistant)

                // Get the search query provided in extras, and if not null, perform a global search with it.
                val query = intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!query.isNullOrEmpty()) {
                    backStack.popUntilRoot()
                    backStack.add(DeepLinkRoute(query))
                }
                null
            }
            INTENT_SEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (!query.isNullOrEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    backStack.popUntilRoot()
                    backStack.add(GlobalSearchRoute(query, filter))
                }
                null
            }
            Intent.ACTION_VIEW -> {
                // Handling opening of backup files
                if (intent.data.toString().endsWith(".tachibk")) {
                    backStack.popUntilRoot()
                    backStack.add(RestoreBackupRoute(intent.data.toString()))
                }
                // Deep link to add extension store
                else if (intent.isAddExtensionStoreIntent()) {
                    intent.data?.getQueryParameter("url")?.let { repoUrl ->
                        backStack.popUntilRoot()
                        backStack.add(ExtensionStoresRoute(repoUrl))
                    }
                }
                null
            }
            else -> return false
        }

        if (tabToOpen != null) {
            resultEventBus.sendResult(tabToOpen)
        }

        ready = true
        return true
    }

    private fun Intent.isAddExtensionStoreIntent(): Boolean {
        return (scheme == "tachiyomi" && data?.host == "add-repo") ||
            (scheme == "mihon" && data?.host == "extension-store")
    }

    companion object {
        const val INTENT_SEARCH = "eu.kanade.tachiyomi.SEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"
    }
}

// Splash screen
private const val SPLASH_MIN_DURATION = 500 // ms
private const val SPLASH_MAX_DURATION = 5000 // ms
private const val SPLASH_EXIT_ANIM_DURATION = 400L // ms
