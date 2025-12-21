package eu.kanade.tachiyomi.ui.customsource

import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevinnzou.web.AccompanistWebChromeClient
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebContent
import com.kevinnzou.web.WebView
import com.kevinnzou.web.WebViewNavigator
import com.kevinnzou.web.WebViewState
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.Scaffold as TachiyomiScaffold

/**
 * Element Selector Wizard Steps
 * Reordered to collect essential selectors (cover/title/link) early for popular/latest/search
 */
enum class SelectorWizardStep(val title: String, val description: String, val detailedHelp: String = "") {
    // Step 1: Navigate to popular/trending section
    TRENDING(
        "Trending Section",
        "Navigate to the trending/popular novels section",
        "Find the section showing popular or trending novels on the homepage",
    ),

    // Step 2: ESSENTIAL - Select cover/title/link elements for novel cards (moved up!)
    NOVEL_CARD(
        "Novel Card Elements",
        "Select: 1) Cover IMAGE, 2) TITLE text, 3) Novel URL link",
        "These elements will be used to display novels in popular/latest/search lists",
    ),

    // Step 3: Select individual novels from trending
    TRENDING_NOVELS(
        "Trending Novels",
        "Select 3 novel items: Click on TITLE element of each novel",
        "Click on the title text of 3 different trending novels.",
    ),

    // Step 4: Navigate to latest section
    NEW_NOVELS_SECTION(
        "New Novels Section",
        "Navigate to the new/latest novels section",
        "Find the section showing recently updated novels",
    ),

    // Step 5: Select new novel items
    NEW_NOVELS(
        "New Novels",
        "Select 3 novel items: Select the TITLE element for each",
        "Click on the title text of 3 different latest novels",
    ),

    // Step 6-8: Optional search and pagination
    SEARCH(
        "Search Page (Optional)",
        "Skip or search for 'a' (single letter) to identify search URL pattern",
        "Search for a single letter like 'a' or 'the'. Look at the URL to find how the search term is passed (e.g., ?s=, ?q=, /search/)",
    ),
    SEARCH_URL_PATTERN(
        "Search URL Pattern",
        "Identify where the search keyword appears in the URL",
        "Look for your search term in the URL. Common patterns: ?s=keyword, ?q=keyword, /search/keyword",
    ),
    PAGINATION(
        "Pagination (Optional)",
        "Navigate to page 2 to identify pagination pattern",
        "Click on page 2 link. Look for page number in URL (e.g., /page/2, ?page=2, ?p=2)",
    ),

    // Step 9: Navigate to novel details page
    NOVEL_PAGE(
        "Novel Details Page",
        "Click on a novel to open its details page",
        "Navigate to any novel's main page",
    ),

    // Step 10: Select novel details elements
    NOVEL_DETAILS(
        "Novel Details",
        "Select: 1) Title, 2) Description/Summary, 3) Cover image, 4) Tags/Genres (optional)",
        "Select each element that shows novel information on the details page",
    ),

    // Step 11: Select chapter list elements
    CHAPTER_LIST(
        "Chapter List",
        "Select chapter items: Click on the TITLE/LINK of chapters",
        "Select at least one chapter item (more improves accuracy)",
    ),

    // Step 12: Navigate to chapter page
    CHAPTER_PAGE(
        "Chapter Page",
        "Open a chapter to identify the content element",
        "Click on any chapter to open its reading page",
    ),

    // Step 13: Select chapter content element
    CHAPTER_CONTENT(
        "Chapter Content",
        "Select the main text container element",
        "Click on the element containing the actual chapter text/content",
    ),

    // Step 14: Complete
    COMPLETE(
        "Complete",
        "Review and save your custom source configuration",
        "Verify your selections and save",
    ),
    ;

    companion object {
        fun fromOrdinal(ordinal: Int): SelectorWizardStep = entries.getOrElse(ordinal) { TRENDING }
        val totalSteps: Int get() = entries.size
    }
}

/**
 * Data class for storing selected CSS selectors
 */
data class SelectorConfig(
    var sourceName: String = "",
    var baseUrl: String = "",
    var trendingSelector: String = "",
    var trendingNovels: MutableList<String> = mutableListOf(),
    var newNovelsSelector: String = "",
    var newNovels: MutableList<String> = mutableListOf(),
    var searchUrl: String = "",
    var searchKeyword: String = "",
    var paginationPattern: String = "",
    var novelCoverSelector: String = "",
    var novelTitleSelector: String = "",
    var novelPageTitleSelector: String = "",
    var novelDescriptionSelector: String = "",
    var novelCoverPageSelector: String = "",
    var novelTagsSelector: String = "",
    var chapterListSelector: String = "",
    var chapterItems: MutableList<String> = mutableListOf(),
    var chapterContentSelector: String = "",
)

/**
 * JavaScript interface for element selection communication
 */
class ElementSelectorJSInterface(
    private val onElementSelected: (String, String, String, String) -> Unit,
    private val onSelectionModeChanged: (Boolean) -> Unit,
) {
    @JavascriptInterface
    fun onElementClick(selector: String, outerHtml: String, textContent: String, parentSelectorsJson: String) {
        onElementSelected(selector, outerHtml, textContent, parentSelectorsJson)
    }

    @JavascriptInterface
    fun setSelectionMode(enabled: Boolean) {
        onSelectionModeChanged(enabled)
    }
}

/**
 * WebView Element Selector Screen
 * Guides user through selecting CSS selectors for custom source creation
 */
@Composable
fun ElementSelectorScreen(
    initialUrl: String,
    onNavigateUp: () -> Unit,
    onSaveConfig: (SelectorConfig) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(SelectorWizardStep.TRENDING) }
    var config by remember { mutableStateOf(SelectorConfig(baseUrl = initialUrl)) }
    var selectionModeEnabled by remember { mutableStateOf(false) }
    var lastSelectedElement by remember { mutableStateOf<SelectedElement?>(null) }
    var showSelectorDialog by remember { mutableStateOf(false) }
    var showSourceNameDialog by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(initialUrl) }

    // WebView state
    val webViewState = rememberWebViewState(url = initialUrl)
    val navigator = rememberWebViewNavigator()
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Selected elements for current step
    val selectedElements = remember { mutableStateListOf<SelectedElement>() }

    val jsInterface = remember {
        ElementSelectorJSInterface(
            onElementSelected = { selector, html, text, parentSelectorsJson ->
                val parentSelectors = try {
                    val json = org.json.JSONObject(parentSelectorsJson)
                    val map = mutableMapOf<String, String>()
                    json.keys().forEach { key ->
                        if (!json.isNull(key)) {
                            map[key] = json.getString(key)
                        }
                    }
                    map
                } catch (e: Exception) {
                    emptyMap()
                }
                lastSelectedElement = SelectedElement(selector, html, text, parentSelectors)
                showSelectorDialog = true
            },
            onSelectionModeChanged = { enabled ->
                selectionModeEnabled = enabled
            },
        )
    }

    // Inject JavaScript for element selection
    fun injectSelectionScript() {
        webView?.evaluateJavascript(ELEMENT_SELECTOR_JS, null)
    }

    // Enable selection mode
    fun enableSelectionMode() {
        webView?.evaluateJavascript("window.enableSelectionMode(true);", null)
        selectionModeEnabled = true
    }

    // Disable selection mode
    fun disableSelectionMode() {
        webView?.evaluateJavascript("window.enableSelectionMode(false);", null)
        selectionModeEnabled = false
    }

    // Highlight elements matching a selector
    fun highlightSelector(selector: String) {
        webView?.evaluateJavascript(
            "window.highlightElements('$selector');",
            null,
        )
    }

    // Clear all highlights
    fun clearHighlights() {
        webView?.evaluateJavascript("window.clearHighlights();", null)
    }

    val webClient = remember {
        object : AccompanistWebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { currentUrl = it }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                url?.let { newUrl ->
                    currentUrl = newUrl

                    // Auto-detect search URL when on SEARCH step
                    if (currentStep == SelectorWizardStep.SEARCH ||
                        currentStep == SelectorWizardStep.SEARCH_URL_PATTERN
                    ) {
                        val detectedSearchUrl = detectSearchUrl(newUrl, config.baseUrl)
                        if (detectedSearchUrl != null) {
                            config.searchUrl = detectedSearchUrl.first
                            config.searchKeyword = detectedSearchUrl.second
                        }
                    }
                }
                injectSelectionScript()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http") || url.startsWith("https")) {
                    view?.loadUrl(url)
                    return true
                }
                return false
            }
        }
    }

    BackHandler(enabled = true) {
        if (selectionModeEnabled) {
            disableSelectionMode()
        } else if (navigator.canGoBack) {
            navigator.navigateBack()
        } else {
            onNavigateUp()
        }
    }

    TachiyomiScaffold(
        topBar = {
            Column {
                // Top App Bar
                AppBar(
                    title = "Element Selector - ${currentStep.title}",
                    subtitle = "${currentStep.ordinal + 1}/${SelectorWizardStep.totalSteps}",
                    navigateUp = onNavigateUp,
                    navigationIcon = Icons.Outlined.Close,
                    actions = {
                        IconButton(onClick = { navigator.reload() }) {
                            Icon(Icons.Outlined.Refresh, "Refresh")
                        }
                        IconButton(onClick = {
                            // Show name dialog before saving
                            showSourceNameDialog = true
                        }) {
                            Icon(Icons.Outlined.Save, "Save")
                        }
                    },
                )

                // Progress indicator
                val loadingState = webViewState.loadingState
                if (loadingState is LoadingState.Loading) {
                    LinearProgressIndicator(
                        progress = { loadingState.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Step indicator bar
                StepIndicatorBar(
                    currentStep = currentStep,
                    onStepClick = { step ->
                        currentStep = step
                    },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (selectionModeEnabled) {
                        disableSelectionMode()
                    } else {
                        enableSelectionMode()
                    }
                },
                containerColor = if (selectionModeEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                icon = {
                    Icon(
                        if (selectionModeEnabled) Icons.Filled.TouchApp else Icons.Filled.Edit,
                        contentDescription = "Select Element",
                    )
                },
                text = {
                    Text(if (selectionModeEnabled) "Selection ON" else "Select Element")
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Step instruction card
            StepInstructionCard(
                step = currentStep,
                selectedCount = selectedElements.size,
                onClearSelections = {
                    selectedElements.clear()
                    clearHighlights()
                },
                onSkipStep = if (currentStep == SelectorWizardStep.SEARCH ||
                    currentStep == SelectorWizardStep.SEARCH_URL_PATTERN ||
                    currentStep == SelectorWizardStep.PAGINATION
                ) {
                    {
                        // Move to next step, skipping current
                        val nextOrdinal = currentStep.ordinal + 1
                        if (nextOrdinal < SelectorWizardStep.totalSteps) {
                            currentStep = SelectorWizardStep.fromOrdinal(nextOrdinal)
                        }
                    }
                } else {
                    null
                },
            )

            // Navigation bar
            NavigationBar(
                canGoBack = navigator.canGoBack,
                canGoForward = navigator.canGoForward,
                onBack = { navigator.navigateBack() },
                onForward = { navigator.navigateForward() },
                currentUrl = currentUrl,
                onUrlSubmit = { url ->
                    navigator.loadUrl(url)
                },
            )

            // WebView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(
                        width = if (selectionModeEnabled) 3.dp else 0.dp,
                        color = if (selectionModeEnabled) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ),
            ) {
                WebView(
                    state = webViewState,
                    navigator = navigator,
                    modifier = Modifier.fillMaxSize(),
                    onCreated = { wv ->
                        webView = wv
                        wv.setDefaultSettings()
                        wv.settings.javaScriptEnabled = true
                        wv.settings.domStorageEnabled = true
                        wv.addJavascriptInterface(jsInterface, "AndroidSelector")
                    },
                    client = webClient,
                )

                // Selection mode overlay indicator
                if (selectionModeEnabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "Tap an element to select it",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // Selected elements panel
            AnimatedVisibility(
                visible = selectedElements.isNotEmpty(),
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                SelectedElementsPanel(
                    elements = selectedElements,
                    onRemove = { element ->
                        selectedElements.remove(element)
                        clearHighlights()
                        selectedElements.forEach { highlightSelector(it.selector) }
                    },
                    onHighlight = { element ->
                        highlightSelector(element.selector)
                    },
                )
            }

            // Step navigation
            StepNavigationBar(
                currentStep = currentStep,
                canProceed = canProceedToNextStep(currentStep, selectedElements, config),
                onPrevious = {
                    if (currentStep.ordinal > 0) {
                        currentStep = SelectorWizardStep.fromOrdinal(currentStep.ordinal - 1)
                    }
                },
                onNext = {
                    saveSelectionsForStep(currentStep, selectedElements, config)
                    selectedElements.clear()
                    clearHighlights()
                    if (currentStep.ordinal < SelectorWizardStep.totalSteps - 1) {
                        currentStep = SelectorWizardStep.fromOrdinal(currentStep.ordinal + 1)
                    }
                },
                onComplete = {
                    saveSelectionsForStep(currentStep, selectedElements, config)
                    // Show name dialog before saving
                    showSourceNameDialog = true
                },
            )
        }
    }

    // Selector confirmation dialog
    if (showSelectorDialog && lastSelectedElement != null) {
        SelectorConfirmDialog(
            element = lastSelectedElement!!,
            onConfirm = { selector ->
                selectedElements.add(lastSelectedElement!!.copy(selector = selector))
                showSelectorDialog = false
                lastSelectedElement = null
            },
            onDismiss = {
                showSelectorDialog = false
                lastSelectedElement = null
            },
        )
    }

    // Source name dialog (shown before saving)
    if (showSourceNameDialog) {
        SourceNameDialog(
            currentName = config.sourceName,
            baseUrl = config.baseUrl,
            onSave = { name ->
                config = config.copy(sourceName = name)
                showSourceNameDialog = false
                onSaveConfig(config)
            },
            onDismiss = { showSourceNameDialog = false },
        )
    }
}

@Composable
private fun StepIndicatorBar(
    currentStep: SelectorWizardStep,
    onStepClick: (SelectorWizardStep) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SelectorWizardStep.entries.forEach { step ->
            val isCompleted = step.ordinal < currentStep.ordinal
            val isCurrent = step == currentStep

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            isCompleted -> MaterialTheme.colorScheme.primary
                            isCurrent -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                    )
                    .clickable { onStepClick(step) },
            )
        }
    }
}

@Composable
private fun StepInstructionCard(
    step: SelectorWizardStep,
    selectedCount: Int,
    onClearSelections: () -> Unit,
    onSkipStep: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (step.detailedHelp.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = step.detailedHelp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    if (selectedCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$selectedCount element(s) selected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Row {
                    if (onSkipStep != null) {
                        TextButton(onClick = onSkipStep) {
                            Text("Skip", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    if (selectedCount > 0) {
                        IconButton(onClick = onClearSelections) {
                            Icon(Icons.Filled.Delete, "Clear selections")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    currentUrl: String,
    onUrlSubmit: (String) -> Unit,
) {
    var editingUrl by remember { mutableStateOf(false) }
    var urlText by remember(currentUrl) { mutableStateOf(currentUrl) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
        }
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Forward")
        }

        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            trailingIcon = {
                IconButton(
                    onClick = {
                        onUrlSubmit(urlText)
                    },
                ) {
                    Icon(Icons.Outlined.Search, "Go")
                }
            },
        )
    }
}

@Composable
private fun SelectedElementsPanel(
    elements: List<SelectedElement>,
    onRemove: (SelectedElement) -> Unit,
    onHighlight: (SelectedElement) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "Selected Elements",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))

            elements.forEach { element ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = element.selector,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = element.textContent.take(50),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row {
                        IconButton(onClick = { onHighlight(element) }) {
                            Icon(Icons.Filled.TouchApp, "Highlight", Modifier.height(20.dp))
                        }
                        IconButton(onClick = { onRemove(element) }) {
                            Icon(Icons.Filled.Delete, "Remove", Modifier.height(20.dp))
                        }
                    }
                }
                Divider()
            }
        }
    }
}

@Composable
private fun StepNavigationBar(
    currentStep: SelectorWizardStep,
    canProceed: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit,
) {
    val isLastStep = currentStep == SelectorWizardStep.COMPLETE
    val isFirstStep = currentStep == SelectorWizardStep.TRENDING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = !isFirstStep,
        ) {
            Text("Previous")
        }

        if (isLastStep) {
            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Filled.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Save Source")
            }
        } else {
            Button(
                onClick = onNext,
                enabled = canProceed,
            ) {
                Text("Next Step")
            }
        }
    }
}

@Composable
private fun SelectorConfirmDialog(
    element: SelectedElement,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editedSelector by remember { mutableStateOf(element.selector) }
    var showHtml by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Selection") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // Visual preview card - shows what the selected element looks like
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Selected Text:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = element.textContent.ifEmpty { "(No text content)" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Parent selectors options
                if (element.parentSelectors.isNotEmpty()) {
                    Text(
                        text = "Select Parent Element:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    element.parentSelectors.forEach { (tag, selector) ->
                        if (selector.isNotEmpty() && selector != element.selector) {
                            OutlinedButton(
                                onClick = { editedSelector = selector },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Text("Select Parent <${tag.uppercase()}>")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // CSS Selector input
                OutlinedTextField(
                    value = editedSelector,
                    onValueChange = { editedSelector = it },
                    label = { Text("CSS Selector") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Toggle to show/hide HTML
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHtml = !showHtml }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (showHtml) Icons.Filled.Code else Icons.Filled.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showHtml) "Hide HTML" else "Show HTML",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // HTML preview (collapsible)
                AnimatedVisibility(visible = showHtml) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(8.dp),
                    ) {
                        Text(
                            text = element.outerHtml.take(800),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            ),
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(editedSelector) }) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SourceNameDialog(
    currentName: String,
    baseUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Extract domain name as default suggestion
    val suggestedName = remember(baseUrl) {
        try {
            val host = java.net.URI(baseUrl).host ?: baseUrl
            host.removePrefix("www.")
                .split(".")
                .firstOrNull()
                ?.replaceFirstChar { it.uppercase() }
                ?: "Custom Source"
        } catch (e: Exception) {
            "Custom Source"
        }
    }

    var sourceName by remember { mutableStateOf(currentName.ifEmpty { suggestedName }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Custom Source") },
        text = {
            Column {
                Text(
                    text = "Enter a name for your custom source:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = sourceName,
                    onValueChange = { sourceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Source Name *") },
                    placeholder = { Text(suggestedName) },
                    singleLine = true,
                    isError = sourceName.isBlank(),
                )

                if (sourceName.isBlank()) {
                    Text(
                        text = "Name is required",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Base URL: $baseUrl",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(sourceName) },
                enabled = sourceName.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

data class SelectedElement(
    val selector: String,
    val outerHtml: String,
    val textContent: String,
    val parentSelectors: Map<String, String> = emptyMap(),
)

private fun canProceedToNextStep(
    step: SelectorWizardStep,
    selectedElements: List<SelectedElement>,
    config: SelectorConfig,
): Boolean {
    return when (step) {
        SelectorWizardStep.TRENDING -> true // Navigation step
        SelectorWizardStep.NOVEL_CARD -> selectedElements.size >= 2 // Cover + Title (essential!)
        SelectorWizardStep.TRENDING_NOVELS -> selectedElements.size >= 1
        SelectorWizardStep.NEW_NOVELS_SECTION -> true
        SelectorWizardStep.NEW_NOVELS -> selectedElements.size >= 1
        SelectorWizardStep.SEARCH -> true // Optional - can skip
        SelectorWizardStep.SEARCH_URL_PATTERN -> true // Optional - can skip (removed keyword requirement)
        SelectorWizardStep.PAGINATION -> true // Optional - can skip
        SelectorWizardStep.NOVEL_PAGE -> true // Navigation step
        SelectorWizardStep.NOVEL_DETAILS -> selectedElements.size >= 1
        // Fixed: Allow proceeding with at least 1 chapter selected (was requiring 3)
        SelectorWizardStep.CHAPTER_LIST -> selectedElements.isNotEmpty() || config.chapterItems.isNotEmpty()
        SelectorWizardStep.CHAPTER_PAGE -> true // Navigation step
        // Fixed: Check both current selections AND saved config for chapter content
        SelectorWizardStep.CHAPTER_CONTENT -> selectedElements.isNotEmpty() ||
            config.chapterContentSelector.isNotEmpty()
        SelectorWizardStep.COMPLETE -> true
    }
}

private fun saveSelectionsForStep(
    step: SelectorWizardStep,
    selectedElements: List<SelectedElement>,
    config: SelectorConfig,
) {
    when (step) {
        SelectorWizardStep.TRENDING_NOVELS -> {
            config.trendingNovels.clear()
            config.trendingNovels.addAll(selectedElements.map { it.selector })
            // Generate a common selector from selected elements
            if (selectedElements.isNotEmpty()) {
                config.trendingSelector = findCommonSelector(selectedElements)
            }
        }
        SelectorWizardStep.NEW_NOVELS -> {
            config.newNovels.clear()
            config.newNovels.addAll(selectedElements.map { it.selector })
            if (selectedElements.isNotEmpty()) {
                config.newNovelsSelector = findCommonSelector(selectedElements)
            }
        }
        SelectorWizardStep.NOVEL_CARD -> {
            // Expect cover first, then title
            selectedElements.getOrNull(0)?.let { config.novelCoverSelector = it.selector }
            selectedElements.getOrNull(1)?.let { config.novelTitleSelector = it.selector }
        }
        SelectorWizardStep.NOVEL_DETAILS -> {
            selectedElements.forEachIndexed { index, element ->
                when (index) {
                    0 -> config.novelPageTitleSelector = element.selector
                    1 -> config.novelDescriptionSelector = element.selector
                    2 -> config.novelCoverPageSelector = element.selector
                    3 -> config.novelTagsSelector = element.selector
                }
            }
        }
        SelectorWizardStep.CHAPTER_LIST -> {
            config.chapterItems.clear()
            config.chapterItems.addAll(selectedElements.map { it.selector })
            if (selectedElements.isNotEmpty()) {
                config.chapterListSelector = findCommonSelector(selectedElements)
            }
        }
        SelectorWizardStep.CHAPTER_CONTENT -> {
            selectedElements.firstOrNull()?.let { config.chapterContentSelector = it.selector }
        }
        else -> { /* Navigation steps, no selections to save */ }
    }
}

private fun findCommonSelector(elements: List<SelectedElement>): String {
    if (elements.isEmpty()) return ""
    if (elements.size == 1) return elements.first().selector

    // Find common parent path
    val selectors = elements.map { it.selector }
    val parts = selectors.map { it.split(" > ", " ").toMutableList() }

    val commonParts = mutableListOf<String>()
    val minLength = parts.minOf { it.size }

    for (i in 0 until minLength) {
        val part = parts.first()[i]
        if (parts.all { it[i] == part }) {
            commonParts.add(part)
        } else {
            break
        }
    }

    return commonParts.joinToString(" > ")
}

/**
 * JavaScript code for element selection
 */
private val ELEMENT_SELECTOR_JS = """
(function() {
    let selectionMode = false;
    let highlightedElements = [];

    // Style for highlighted elements
    const highlightStyle = 'outline: 3px solid #4CAF50 !important; background-color: rgba(76, 175, 80, 0.2) !important;';
    const hoverStyle = 'outline: 3px dashed #2196F3 !important; background-color: rgba(33, 150, 243, 0.2) !important;';

    // Create style element
    const styleEl = document.createElement('style');
    styleEl.textContent = '.element-selector-highlight { ' + highlightStyle + ' } .element-selector-hover { ' + hoverStyle + ' }';
    document.head.appendChild(styleEl);

    // Generate CSS selector for element
    function getSelector(element) {
        if (element.id) {
            return '#' + CSS.escape(element.id);
        }

        let path = [];
        while (element && element.nodeType === Node.ELEMENT_NODE) {
            let selector = element.nodeName.toLowerCase();

            if (element.className) {
                const classes = element.className.toString().trim().split(/\s+/)
                    .filter(c => c && !c.startsWith('element-selector'))
                    .slice(0, 2);
                if (classes.length > 0) {
                    selector += '.' + classes.map(c => CSS.escape(c)).join('.');
                }
            }

            // Add nth-child if needed
            if (element.parentNode) {
                const siblings = Array.from(element.parentNode.children)
                    .filter(e => e.nodeName === element.nodeName);
                if (siblings.length > 1) {
                    const index = siblings.indexOf(element) + 1;
                    selector += ':nth-child(' + index + ')';
                }
            }

            path.unshift(selector);
            element = element.parentNode;

            // Limit depth
            if (path.length > 6) break;
        }

        return path.join(' > ');
    }

    // Handle element hover
    let lastHovered = null;
    document.addEventListener('mouseover', function(e) {
        if (!selectionMode) return;

        if (lastHovered) {
            lastHovered.classList.remove('element-selector-hover');
        }

        e.target.classList.add('element-selector-hover');
        lastHovered = e.target;
    }, true);

    // Handle element click
    document.addEventListener('click', function(e) {
        if (!selectionMode) return;

        e.preventDefault();
        e.stopPropagation();

        const element = e.target;
        const selector = getSelector(element);
        const outerHtml = element.outerHTML.substring(0, 500);
        const textContent = element.textContent.substring(0, 200);

        // Find closest block parents
        const closestA = element.closest('a');
        const closestLi = element.closest('li');
        const closestDiv = element.closest('div');

        const parentSelectors = {
            a: closestA ? getSelector(closestA) : null,
            li: closestLi ? getSelector(closestLi) : null,
            div: closestDiv ? getSelector(closestDiv) : null
        };

        // Notify Android
        if (window.AndroidSelector) {
            window.AndroidSelector.onElementClick(selector, outerHtml, textContent, JSON.stringify(parentSelectors));
        }

        // Highlight selected element
        element.classList.add('element-selector-highlight');
        highlightedElements.push(element);
    }, true);

    // Enable/disable selection mode
    window.enableSelectionMode = function(enabled) {
        selectionMode = enabled;
        if (!enabled && lastHovered) {
            lastHovered.classList.remove('element-selector-hover');
        }
        if (window.AndroidSelector) {
            window.AndroidSelector.setSelectionMode(enabled);
        }
    };

    // Highlight elements by selector
    window.highlightElements = function(selector) {
        try {
            const elements = document.querySelectorAll(selector);
            elements.forEach(el => {
                el.classList.add('element-selector-highlight');
                highlightedElements.push(el);
            });
        } catch (e) {
            console.error('Invalid selector:', selector);
        }
    };

    // Clear highlights
    window.clearHighlights = function() {
        highlightedElements.forEach(el => {
            el.classList.remove('element-selector-highlight');
        });
        highlightedElements = [];
    };

})();
"""

/**
 * Detect search URL pattern from current URL
 * Returns Pair(searchUrlPattern, detectedKeyword) or null if not detected
 */
private fun detectSearchUrl(url: String, baseUrl: String): Pair<String, String>? {
    val baseUrlTrimmed = baseUrl.trimEnd('/')

    // Common search parameter patterns
    val searchParams = listOf(
        "s" to Regex("""[?&]s=([^&]+)"""),
        "q" to Regex("""[?&]q=([^&]+)"""),
        "query" to Regex("""[?&]query=([^&]+)"""),
        "keyword" to Regex("""[?&]keyword=([^&]+)"""),
        "search" to Regex("""[?&]search=([^&]+)"""),
        "k" to Regex("""[?&]k=([^&]+)"""),
        "term" to Regex("""[?&]term=([^&]+)"""),
    )

    // Try each pattern
    for ((param, regex) in searchParams) {
        val match = regex.find(url)
        if (match != null) {
            val keyword = java.net.URLDecoder.decode(match.groupValues[1], "UTF-8")
            // Build the search URL pattern
            val searchUrlPattern = url
                .replace(match.groupValues[1], "{query}")
                .replace(Regex("""[?&]page=\d+"""), "&page={page}")
                .let { if (!it.contains("{page}")) "$it&page={page}" else it }
                .replace("&&", "&")
                .replace("?&", "?")

            return Pair(searchUrlPattern, keyword)
        }
    }

    // Check for path-based search patterns like /search/keyword or /s/keyword
    val pathPatterns = listOf(
        Regex("""(/search/)([^/?]+)"""),
        Regex("""(/s/)([^/?]+)"""),
        Regex("""(/find/)([^/?]+)"""),
    )

    for (regex in pathPatterns) {
        val match = regex.find(url)
        if (match != null) {
            val keyword = java.net.URLDecoder.decode(match.groupValues[2], "UTF-8")
            val searchUrlPattern = url
                .replace(match.groupValues[2], "{query}")
                .let { if (!it.contains("{page}")) "$it?page={page}" else it }

            return Pair(searchUrlPattern, keyword)
        }
    }

    return null
}
