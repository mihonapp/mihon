package eu.kanade.tachiyomi.ui.manga.chapter

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm(amount.coerceIn(0, maxAmount))
                },
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        title = {
            Text(text = stringResource(id = R.string.custom_download))
        },
        text = {
            val onChangeAmount: (Int) -> Unit = { amount = (amount + it).coerceIn(0, maxAmount) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onChangeAmount(-10) },
                    enabled = amount > 10,
                ) {
                    Icon(imageVector = Icons.Outlined.KeyboardDoubleArrowLeft, contentDescription = "")
                }
                IconButton(
                    onClick = { onChangeAmount(-1) },
                    enabled = amount > 0,
                ) {
                    Icon(imageVector = Icons.Outlined.ChevronLeft, contentDescription = "")
                }
                BasicTextField(
                    value = amount.toString(),
                    onValueChange = { onChangeAmount(it.toIntOrNull() ?: 0) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                IconButton(
                    onClick = { onChangeAmount(1) },
                    enabled = amount < maxAmount,
                ) {
                    Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = "")
                }
                IconButton(
                    onClick = { onChangeAmount(10) },
                    enabled = amount < maxAmount,
                ) {
                    Icon(imageVector = Icons.Outlined.KeyboardDoubleArrowRight, contentDescription = "")
                }
            }
        },
    )
}
