package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import eu.kanade.tachiyomi.R

@Composable
fun DownloadCustomAmountDialog(
    maxAmount: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var amount by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm(amount.coerceIn(0, maxAmount))
                },
            ) {
                Text(text = stringResource(R.string.action_download))
            }
        },
        title = {
            Text(text = stringResource(R.string.custom_download))
        },
        text = {
            val setAmount: (Int) -> Unit = { amount = it.coerceIn(0, maxAmount) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { setAmount(amount - 10) },
                    enabled = amount > 0,
                ) {
                    Icon(imageVector = Icons.Outlined.KeyboardDoubleArrowLeft, contentDescription = "-10")
                }
                IconButton(
                    onClick = { setAmount(amount - 1) },
                    enabled = amount > 0,
                ) {
                    Icon(imageVector = Icons.Outlined.ChevronLeft, contentDescription = "-1")
                }
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = amount.toString(),
                    onValueChange = { setAmount(it.toIntOrNull() ?: 0) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                )
                IconButton(
                    onClick = { setAmount(amount + 1) },
                    enabled = amount < maxAmount,
                ) {
                    Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = "+1")
                }
                IconButton(
                    onClick = { setAmount(amount + 10) },
                    enabled = amount < maxAmount,
                ) {
                    Icon(imageVector = Icons.Outlined.KeyboardDoubleArrowRight, contentDescription = "+10")
                }
            }
        },
    )
}
