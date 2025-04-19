package eu.kanade.tachiyomi.ui.browse.source.blockrule.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAny
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.blockrule.model.Blockrule
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BlockruleCreateDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String, Blockrule.Type, String) -> Unit,
    blockrules: ImmutableList<Blockrule>,

    title: String,
    editBlockrule: Blockrule? = null,
) {
    var name by remember { mutableStateOf(editBlockrule?.name ?: "New") }
    var rule by remember { mutableStateOf(editBlockrule?.rule ?: "") }
    var type by remember { mutableStateOf(editBlockrule?.type ?: Blockrule.Type.TITLE_CONTAINS) }

    val ruleAlreadyExists = remember(name, type, rule) {
        blockrules.fastAny { it.name == name && it.type == type && it.rule == rule }
    }
    val ruleError by derivedStateOf {
        rule.isBlank() || (rule.isNotEmpty() && ruleAlreadyExists)
    }
    val regexError by derivedStateOf {
        when(type){
            Blockrule.Type.TITLE_REGEX, Blockrule.Type.DESCRIPTION_REGEX -> {
                try { rule.toRegex() ; false }
                catch (e: Exception) { true }
            }
            else -> false
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = name.isNotEmpty() && !regexError && !ruleError,
                onClick = {
                    onConfirm(name, type, rule)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = title)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(MR.strings.block_rule_name)) },
                    supportingText = { Text(text = stringResource(MR.strings.information_required_plain)) },
                    isError = name.isEmpty(),
                    singleLine = true,
                )
                var expanded by remember { mutableStateOf(false) }
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { expanded = !expanded },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(stringResource(MR.strings.block_rule_type))
                    Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
                    Text(text = type.toShowName())
                    Spacer(modifier = Modifier.weight(1f))
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = onDismissRequest,
                ) {
                    Blockrule.Type.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(text = t.toShowName()) },
                            onClick = {
                                expanded = false
                                type = t
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = rule,
                    onValueChange = { rule = it },
                    label = {
                        Text(text = stringResource(MR.strings.block_rule_rule))
                    },
                    supportingText = {
                        val msgRes = when {
                            rule.isBlank() -> MR.strings.block_rule_cannot_be_empty
                            ruleError      -> MR.strings.block_rule_already_exist
                            regexError     -> MR.strings.block_rule_invalid_regex
                            else           -> MR.strings.information_required_plain
                        }
                        Text(text = stringResource(msgRes))
                    },
                    isError = ruleError || regexError,
                    singleLine = true,
                )
            }
        },
    )
}

@Composable
fun BlockruleDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    blockrule: Blockrule,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onDelete()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.are_you_sure))
        },
        text = {
            Column {
                Text(text = stringResource(MR.strings.block_rule_confirm_delete))
                Text(text = stringResource(MR.strings.block_rule_name) + ": " + blockrule.name)
                Text(text = stringResource(MR.strings.block_rule_type) + ": " + blockrule.type.toShowName())
                Text(text = stringResource(MR.strings.block_rule_rule) + ": " + blockrule.rule)
            }
        },
    )
}

@Composable
fun BlockruleSortAlphabeticallyDialog(
    onDismissRequest: () -> Unit,
    onSort: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onSort()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.block_rule_sort))
        },
        text = {
            Text(text = stringResource(MR.strings.block_rule_confirm_sort_alphabetically))
        },
    )
}
