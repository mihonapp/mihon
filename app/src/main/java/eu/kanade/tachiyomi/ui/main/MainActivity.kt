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
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.result.ResultEventBus
import androidx.navigation3.runtime.result.rememberResultEventBus
import androidx.navigation3.runtime.result.rememberResultEventBusNavEntryDecorator
import dev.zacsweers.metro.Inject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.components.DownloadedOnlyBannerBackgroundColor
import eu.kanade.presentation.components.IncognitoModeBannerBackgroundColor
import eu.kanade.presentation.components.IndexingBannerBackgroundColor
import eu.kanade.presentation.more.AppMigratingScreen
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoreDeepLinkEvent
import eu.kanade.presentation.more.settings.screen.openSettings
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.sync.webdav.WebDavSyncWorker
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.home.TabEvent
import eu.kanade.tachiyomi.ui.home.TopLevelRoute
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.isBenchmarkBuildType
import eu.kanade.tachiyomi.util.system.isNavigationBarNeedsScrim
import eu.kanade.tachiyomi.util.system.updaterEnabled
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.app.di.appGraph
import mihon.core.metro.metroGraph
import mihon.core.migration.Migrator
import mihon.core.navigation.AssistContentManager
import mihon.core.navigation.BrowseSourceRoute
import mihon.core.navigation.DeepLinkRoute
import mihon.core.navigation.ExtensionStoresRoute
import mihon.core.navigation.GlobalSearchRoute
import mihon.core.navigation.HomeRoute
import mihon.core.navigation.LocalAssistContentManager
import mihon.core.navigation.LocalNavigator
import mihon.core.navigation.MangaRoute
import mihon.core.navigation.Navigator
import mihon.core.navigation.NavigatorDisplay
import mihon.core.navigation.NewUpdateRoute
import mihon.core.navigation.OnboardingRoute
import mihon.core.navigation.RestoreBackupRoute
import mihon.core.navigation.SupportUsRoute
import mihon.core.navigation.TrackInfoDialogRoute
import mihon.core.navigation.WebDavSyncRoute
import mihon.core.navigation.appEntries
import mihon.core.navigation.rememberNavigator
import mihon.core.navigation.rememberOverlaySheetSceneStrategy
import mihon.core.navigation.rememberTwoPaneSettingsSceneStrategy
import mihon.data.cache.ChapterCache
import mihon.domain.database.repository.DatabaseRepository
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.OpenInNew
import mihon.icons.materialsymbols.roundedfilled.VolunteerActivism
import mihon.presentation.core.util.isTabletUi
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance
import tachiyomi.core.common.Constants
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.time.times

class MainActivity : BaseActivity() {

    private val graph: AppGraph by lazy { metroGraph() }

    @Inject private lateinit var libraryPreferences: LibraryPreferences

    @Inject private lateinit var preferences: BasePreferences

    @Inject private lateinit var downloadCache: DownloadCache

    @Inject private lateinit var chapterCache: ChapterCache

    @Inject private lateinit var databaseRepository: DatabaseRepository

    private val isAppMigrating = MutableStateFlow(Migrator.isRunning)

    @Inject private lateinit var getIncognitoState: GetIncognitoState

    @Inject private lateinit var extensionManager: ExtensionManager

    // To be checked by splash screen. If true then splash screen will be removed.
    var ready = false

    private var navigator: Navigator? = null

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

        lifecycleScope.launch {
            Migrator.awaitAndRelease()
            isAppMigrating.value = false
        }
        // Uncaught on purpose: a failed database migration otherwise leaves the migrating screen up for good,
        // while the crash screen tells what went wrong and lets the logs be shared
        lifecycleScope.launch { databaseRepository.awaitOpen() }

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot) {
            finish()
            return
        }

        if (isLaunch) {
            WebDavSyncWorker.enqueueOnAppStartIfNeeded(this)
        }

        setComposeContent {
            val appMigrating by isAppMigrating.collectAsState()
            val databaseMigrating by databaseRepository.isMigrating.collectAsState()
            val migrating = appMigrating || databaseMigrating
            var migratingSince by remember { mutableStateOf(if (migrating) TimeSource.Monotonic.markNow() else null) }
            LaunchedEffect(migrating) {
                val since = migratingSince
                if (migrating) {
                    if (since == null) migratingSince = TimeSource.Monotonic.markNow()
                } else if (since != null) {
                    // A migration that finishes right away would otherwise only flash the screen
                    delay(MIGRATING_SCREEN_MIN_DURATION - since.elapsedNow())
                    migratingSince = null
                }
            }
            if (migrating || migratingSince != null) {
                AppMigratingScreen()
                // Release the splash screen so the reason for the wait shows
                LaunchedEffect(Unit) { ready = true }
                return@setComposeContent
            }

            val context = LocalContext.current

            var incognito by remember { mutableStateOf(false) }
            val downloadOnly by preferences.downloadedOnly.collectAsState()
            val indexing by downloadCache.isInitializing.collectAsState()

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

            val navigator = rememberNavigator(HomeRoute)
            val twoPaneStrategy = rememberTwoPaneSettingsSceneStrategy<NavKey>()
            val overlaySheetSceneStrategy = rememberOverlaySheetSceneStrategy<NavKey>()
            val resultEventBus = rememberResultEventBus()
            val resultEventBusNavEntryDecorator = rememberResultEventBusNavEntryDecorator<NavKey>(
                resultEventBus = resultEventBus,
            )

            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalAssistContentManager provides assistContentManager,
            ) {
                LaunchedEffect(navigator) {
                    this@MainActivity.navigator = navigator

                    if (isLaunch) {
                        // Set start screen
                        handleIntentAction(intent, navigator, resultEventBus)

                        // Reset Incognito Mode on relaunch
                        preferences.incognitoMode.set(false)
                    }
                }

                val currentRoute = navigator.current
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
                        NavigatorDisplay(
                            navigator = navigator,
                            sceneStrategies = listOf(twoPaneStrategy, overlaySheetSceneStrategy),
                            transitionSpec = { materialSharedAxisX(forward = true, slideDistance = slideDistance) },
                            popTransitionSpec = { materialSharedAxisX(forward = false, slideDistance = slideDistance) },
                            predictivePopTransitionSpec = {
                                materialSharedAxisX(forward = false, slideDistance = slideDistance)
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
                            val currentRoute = navigator.current
                            if (currentRoute is BrowseSourceRoute ||
                                (currentRoute is MangaRoute && currentRoute.fromSource)
                            ) {
                                navigator.popToRoot()
                            }
                        }
                        .launchIn(this)
                }

                HandleOnNewIntent(
                    context = context,
                    navigator = navigator,
                    resultEventBus = resultEventBus,
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
            lifecycleScope.launch { chapterCache.clear() }
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        val navigator = navigator ?: return
        val route = navigator.routes.lastOrNull { it !is TrackInfoDialogRoute } ?: return
        assistContentManager.urlFor(route, navigator.routes)?.let { outContent.webUri = it.toUri() }
    }

    override fun reportFullyDrawn() {
        try {
            super.reportFullyDrawn()
        } catch (e: SecurityException) {
            // Some Samsung Android 9 to 11 builds deny the platform's fully drawn broadcast
            // and the report only feeds startup metrics, so it shouldn't crash the app
            logcat(LogPriority.WARN, e)
        }
    }

    @Composable
    private fun HandleOnNewIntent(
        context: Context,
        navigator: Navigator,
        resultEventBus: ResultEventBus,
    ) {
        LaunchedEffect(Unit) {
            callbackFlow {
                val componentActivity = context as ComponentActivity
                val consumer = Consumer<Intent> { trySend(it) }
                componentActivity.addOnNewIntentListener(consumer)
                awaitClose { componentActivity.removeOnNewIntentListener(consumer) }
            }
                .collectLatest {
                    handleIntentAction(it, navigator, resultEventBus)
                }
        }
    }

    @Composable
    private fun CheckForUpdates() {
        val context = LocalContext.current
        val navigator = LocalNavigator.current

        // App updates
        LaunchedEffect(Unit) {
            if (!updaterEnabled) return@LaunchedEffect
            var lastCheck: Long? = null
            // The app can stay open for days now, so a cold start alone rarely checks.
            // Elapsed realtime keeps counting while the device sleeps, unlike the monotonic clock.
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    lastCheck?.let {
                        delay(it + APP_UPDATE_CHECK_INTERVAL.inWholeMilliseconds - SystemClock.elapsedRealtime())
                    }
                    lastCheck = SystemClock.elapsedRealtime()
                    try {
                        val result = context.appGraph.updateChecker.checkForUpdate()
                        if (
                            result is GetApplicationRelease.Result.NewUpdate &&
                            result.release.version != preferences.skippedAppUpdate.get() &&
                            navigator.routes.none { it is NewUpdateRoute }
                        ) {
                            val updateRoute = NewUpdateRoute(
                                versionName = result.release.version,
                                changelogInfo = result.release.info,
                                releaseLink = result.release.releaseLink,
                                downloadLink = result.release.downloadLink,
                            )
                            navigator.push(updateRoute)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e)
                    }
                }
            }
        }

        // Extensions updates
        LaunchedEffect(Unit) {
            try {
                extensionManager.checkForUpdates()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    @Composable
    private fun ShowOnboarding() {
        val navigator = LocalNavigator.current

        LaunchedEffect(Unit) {
            if (!preferences.shownOnboardingFlow.get() && OnboardingRoute !in navigator.routes) {
                navigator.push(OnboardingRoute)
            }
        }
    }

    @Composable
    private fun ShowDonationCampaign() {
        val navigator = LocalNavigator.current

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
                            navigator.push(SupportUsRoute)
                            dismissSupportMessage()
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.RoundedFilled.VolunteerActivism,
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
        navigator: Navigator,
        resultEventBus: ResultEventBus,
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
                navigator.popToRoot()
                TabEvent.Library(idToOpen)
            }
            Constants.SHORTCUT_UPDATES -> TabEvent.Updates
            Constants.SHORTCUT_HISTORY -> TabEvent.History
            Constants.SHORTCUT_SOURCES -> TabEvent.Browse(false)
            Constants.SHORTCUT_EXTENSIONS -> TabEvent.Browse(true)
            Constants.SHORTCUT_DOWNLOADS -> {
                navigator.popToRoot()
                TabEvent.More(toDownloads = true)
            }
            Constants.SHORTCUT_WEBDAV_SYNC -> {
                navigator.popToRoot()
                navigator.push(WebDavSyncRoute)
                null
            }
            Intent.ACTION_APPLICATION_PREFERENCES -> {
                navigator.popToRoot()
                navigator.openSettings(twoPane = resources.configuration.isTabletUi())
                null
            }
            Intent.ACTION_SEARCH, Intent.ACTION_SEND, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                // If the intent match the "standard" Android search intent
                // or the Google-specific search intent (triggered by saying or typing "search *query* on *Tachiyomi*" in Google Search/Google Assistant)

                // Get the search query provided in extras, and if not null, perform a global search with it.
                val query = intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!query.isNullOrEmpty()) {
                    navigator.replaceAll(DeepLinkRoute(query))
                }
                null
            }
            INTENT_SEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (!query.isNullOrEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    navigator.replaceAll(GlobalSearchRoute(query, filter))
                }
                null
            }
            Intent.ACTION_VIEW -> {
                // Handling opening of backup files
                if (intent.data.toString().endsWith(".tachibk")) {
                    navigator.replaceAll(RestoreBackupRoute(intent.data.toString()))
                }
                // Deep link to add extension store
                else if (intent.isAddExtensionStoreIntent()) {
                    intent.data?.getQueryParameter("url")?.let { repoUrl ->
                        navigator.replaceAll(ExtensionStoresRoute)
                        resultEventBus.sendResult(ExtensionStoreDeepLinkEvent(repoUrl))
                    }
                }
                null
            }
            else -> return false
        }

        if (tabToOpen != null) {
            resultEventBus.sendResult<TabEvent>(tabToOpen)
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

private val MIGRATING_SCREEN_MIN_DURATION = 1.seconds
private val APP_UPDATE_CHECK_INTERVAL = 6.hours
