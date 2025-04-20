package eu.kanade.tachiyomi.ui.browse.source.blockrule.components

import androidx.compose.runtime.Composable
import tachiyomi.domain.blockrule.model.Blockrule
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Blockrule.Type.toShowName() =
    stringResource(
        when (this) {
            Blockrule.Type.AUTHOR_EQUALS        -> MR.strings.block_rule_type_author_equals
            Blockrule.Type.AUTHOR_CONTAINS      -> MR.strings.block_rule_type_author_contains
            Blockrule.Type.TITLE_REGEX          -> MR.strings.block_rule_type_title_regex
            Blockrule.Type.TITLE_CONTAINS       -> MR.strings.block_rule_type_title_contain
            Blockrule.Type.TITLE_STARTS_WITH    -> MR.strings.block_rule_type_title_starts_with
            Blockrule.Type.TITLE_ENDS_WITH      -> MR.strings.block_rule_type_title_ends_with
            Blockrule.Type.TITLE_EQUALS         -> MR.strings.block_rule_type_title_equals
            Blockrule.Type.DESCRIPTION_REGEX    -> MR.strings.block_rule_type_description_regex
            Blockrule.Type.DESCRIPTION_CONTAINS -> MR.strings.block_rule_type_description_contains
        },
    )
