package eu.kanade.tachiyomi.ui.main

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mihon.core.dualscreen.DualScreenState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DualScreenActivity : BaseActivity() {

    private val preferences = Injekt.get<BasePreferences>()
    private var rotationState by mutableStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerSecureActivity(this)

        if (intent?.action == ACTION_FINISH) {
            finish()
            return
        }

        setupRotation()

        setComposeContent {
            val activeScreen by DualScreenState.activeScreen.collectAsState()
            val rotation = rotationState
            val isRotated = rotation == 90f || rotation == 270f

            androidx.compose.runtime.LaunchedEffect(Unit) {
                DualScreenState.rotationEvents.collect {
                    setupRotation()
                }
            }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                preferences.swapPresentationRotation().changes().collect {
                    setupRotation()
                }
            }

            CompositionLocalProvider(
                LocalOnBackPressedDispatcherOwner provides this,
                LocalActivityResultRegistryOwner provides this,
                DualScreenState.LocalNavigateUp provides {
                    DualScreenState.close()
                }
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (isRotated) {
                                val metrics = resources.displayMetrics
                                val widthDp = (metrics.widthPixels / metrics.density).dp
                                val heightDp = (metrics.heightPixels / metrics.density).dp
                                Modifier
                                    .size(heightDp, widthDp)
                                    .rotate(rotation)
                            } else {
                                Modifier
                                    .fillMaxSize()
                                    .rotate(rotation)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Navigator(
                        screen = CompanionDashboardScreen,
                    ) { navigator ->
                        DefaultNavigatorScreenTransition(navigator = navigator)

                        androidx.compose.runtime.LaunchedEffect(navigator) {
                            launch {
                                DualScreenState.activeScreen.collectLatest { screen ->
                                    val currentScreen = navigator.lastItem
                                    if (screen != null) {
                                        if (currentScreen != screen) {
                                            navigator.replace(screen)
                                        }
                                    } else {
                                        if (currentScreen != CompanionDashboardScreen) {
                                            navigator.replaceAll(CompanionDashboardScreen)
                                        }
                                    }
                                }
                            }
                            
                            launch {
                                DualScreenState.mainScreenEvents.collectLatest {
                                    val intent = Intent(this@DualScreenActivity, MainActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                    startActivity(intent)
                                }
                            }
                        }
                    }

                    if (activeScreen != null) {
                        BackHandler {
                            DualScreenState.close()
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupRotation()
    }

    override fun onResume() {
        super.onResume()
        setupRotation()
    }

    private fun setupRotation() {
        try {
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
            val primaryDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return
            val currentDisplay = display ?: return

            val activityRotation = primaryDisplay.rotation
            val secondaryRotation = currentDisplay.rotation

            val actDeg = when (activityRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            val secDeg = when (secondaryRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }

            var deg = (actDeg - secDeg + 360) % 360
            if (preferences.swapPresentationRotation().get()) {
                deg = (deg + 180) % 360
            }
            // Correct the rotation difference between the two physical screens
            rotationState = deg.toFloat()
        } catch (e: Exception) {
            // Silence errors during rapid configuration changes to prevent crashes
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_FINISH) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        DualScreenState.close()
    }

    companion object {
        const val ACTION_FINISH = "mihon.core.dualscreen.FINISH"

        fun start(context: Context) {
            val intent = Intent(context, DualScreenActivity::class.java)
            context.startActivity(intent)
        }
    }
}
