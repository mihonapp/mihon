package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.LocalBulletListHandler
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownBulletList
import com.mikepenz.markdown.compose.elements.MarkdownDivider
import com.mikepenz.markdown.compose.elements.MarkdownOrderedList
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.compose.elements.listDepth
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberMarkdownState
import org.intellij.markdown.MarkdownTokenTypes.Companion.HTML_TAG
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor
import org.intellij.markdown.flavours.gfm.table.GitHubTableMarkerProvider
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.providers.AtxHeaderProvider
import org.intellij.markdown.parser.markerblocks.providers.BlockQuoteProvider
import org.intellij.markdown.parser.markerblocks.providers.CodeBlockProvider
import org.intellij.markdown.parser.markerblocks.providers.CodeFenceProvider
import org.intellij.markdown.parser.markerblocks.providers.HorizontalRuleProvider
import org.intellij.markdown.parser.markerblocks.providers.ListMarkerProvider
import org.intellij.markdown.parser.markerblocks.providers.SetextHeaderProvider
import tachiyomi.presentation.core.components.material.padding

@Composable
fun MarkdownRender(
    content: String,
    modifier: Modifier = Modifier,
    flavour: MarkdownFlavourDescriptor = SimpleMarkdownFlavourDescriptor,
    annotator: MarkdownAnnotator = markdownAnnotator(),
) {
    val markdownState = rememberMarkdownState(
        content = content,
        flavour = flavour,
        immediate = true,
    )

    Markdown(
        markdownState = markdownState,
        annotator = annotator,
        typography = mihonMarkdownTypography(),
        padding = mihonMarkdownPadding(),
        components = mihonMarkdownComponents(),
        imageTransformer = Coil3ImageTransformerImpl,
        modifier = modifier,
    )
}

@Composable
private fun mihonMarkdownPadding() = markdownPadding(
    list = 0.dp,
    listItemTop = 2.dp,
    listItemBottom = 2.dp,
)

@Composable
private fun mihonMarkdownTypography() = markdownTypography(
    h1 = MaterialTheme.typography.headlineMedium,
    h2 = MaterialTheme.typography.headlineSmall,
    h3 = MaterialTheme.typography.titleLarge,
    h4 = MaterialTheme.typography.titleMedium,
    h5 = MaterialTheme.typography.titleSmall,
    h6 = MaterialTheme.typography.bodyLarge,
    paragraph = MaterialTheme.typography.bodyMedium,
    text = MaterialTheme.typography.bodyMedium,
    ordered = MaterialTheme.typography.bodyMedium,
    bullet = MaterialTheme.typography.bodyMedium,
    list = MaterialTheme.typography.bodyMedium,
    link = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    ),
)

@Composable
private fun mihonMarkdownComponents() = markdownComponents(
    horizontalRule = {
        MarkdownDivider(
            modifier = Modifier
                .padding(vertical = MaterialTheme.padding.extraSmall)
                .fillMaxWidth(),
        )
    },
    orderedList = { ol ->
        Column(modifier = Modifier.padding(start = MaterialTheme.padding.small)) {
            MarkdownOrderedList(
                content = ol.content,
                node = ol.node,
                style = ol.typography.ordered,
                depth = ol.listDepth,
                markerModifier = { Modifier.alignBy(FirstBaseline) },
                listModifier = { Modifier.alignBy(FirstBaseline) },
            )
        }
    },
    unorderedList = { ul ->
        val markers = listOf("•", "◦", "▸", "▹")

        CompositionLocalProvider(
            LocalBulletListHandler provides { _, _, _, _, _ -> "${markers[ul.listDepth % markers.size]} " },
        ) {
            Column(modifier = Modifier.padding(start = MaterialTheme.padding.small)) {
                MarkdownBulletList(
                    content = ul.content,
                    node = ul.node,
                    style = ul.typography.bullet,
                    markerModifier = { Modifier.alignBy(FirstBaseline) },
                    listModifier = { Modifier.alignBy(FirstBaseline) },
                )
            }
        }
    },
    table = { t ->
        MarkdownTable(
            content = t.content,
            node = t.node,
            style = t.typography.text,
            headerBlock = { content, header, tableWidth, style ->
                MarkdownTableHeader(
                    content = content,
                    header = header,
                    tableWidth = tableWidth,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                )
            },
            rowBlock = { content, header, tableWidth, style ->
                MarkdownTableRow(
                    content = content,
                    header = header,
                    tableWidth = tableWidth,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                )
            },
        )
    },
    custom = { type, model ->
        if (type in DISALLOWED_MARKDOWN_TYPES) {
            MarkdownText(
                content = model.content.substring(model.node.startOffset, model.node.endOffset),
                style = model.typography.text,
            )
        }
    },
)

private object SimpleMarkdownFlavourDescriptor : CommonMarkFlavourDescriptor() {
    override val markerProcessorFactory: MarkerProcessorFactory = SimpleMarkdownProcessFactory
}

private object SimpleMarkdownProcessFactory : MarkerProcessorFactory {
    override fun createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor<*> {
        return SimpleMarkdownMarkerProcessor(productionHolder, CommonMarkdownConstraints.BASE)
    }
}

/**
 * Like `CommonMarkFlavour`, but with html blocks and reference links removed and
 * table support added
 */
private class SimpleMarkdownMarkerProcessor(
    productionHolder: ProductionHolder,
    constraints: MarkdownConstraints,
) : CommonMarkMarkerProcessor(productionHolder, constraints) {
    private val markerBlockProviders = listOf(
        CodeBlockProvider(),
        HorizontalRuleProvider(),
        CodeFenceProvider(),
        SetextHeaderProvider(),
        BlockQuoteProvider(),
        ListMarkerProvider(),
        AtxHeaderProvider(),
        GitHubTableMarkerProvider(),
    )

    override fun getMarkerBlockProviders(): List<MarkerBlockProvider<StateInfo>> {
        return markerBlockProviders
    }
}

val DISALLOWED_MARKDOWN_TYPES = arrayOf(
    HTML_TAG,
)
