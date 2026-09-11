package mihon.desktop.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import mihon.desktop.extension.DesktopCookieStore
import mihon.desktop.extension.DomainCookieConfig
import mihon.desktop.i18n.LocalStrings
import mihon.desktop.platform.DesktopBrowserHelper

@Composable
fun CookieManagerDialog(
    cookieStore: DesktopCookieStore,
    onDismissRequest: () -> Unit,
) {
    val strings = LocalStrings.current
    var configs by remember { mutableStateOf(cookieStore.listAll()) }
    var selectedDomain by remember { mutableStateOf<String?>(null) }

    var inputDomain by remember { mutableStateOf("") }
    var inputCookies by remember { mutableStateOf("") }
    var inputUserAgent by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun selectConfig(config: DomainCookieConfig?) {
        if (config == null) {
            selectedDomain = null
            inputDomain = ""
            inputCookies = ""
            inputUserAgent = ""
        } else {
            selectedDomain = config.domain
            inputDomain = config.domain
            inputCookies = config.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            inputUserAgent = config.customUserAgent ?: ""
        }
        statusMessage = null
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .width(760.dp)
                .height(560.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxHeight(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = strings.cookieManagerTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = strings.cookieManagerDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismissRequest) {
                        Text(strings.dialogClose)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Left column: Domain list
                    Column(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = strings.cookieConfiguredDomains(configs.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TextButton(onClick = { selectConfig(null) }) {
                                Text(strings.cookieAddDomain)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (configs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = strings.cookieNoCustomDomains,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(configs) { config ->
                                    val isSelected = config.domain == selectedDomain
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectConfig(config) },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            },
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = config.domain,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = strings.cookieCountSubtitle(
                                                    config.cookies.size,
                                                    !config.customUserAgent.isNullOrBlank(),
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Right column: Editor
                    Column(
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = inputDomain,
                            onValueChange = { inputDomain = it },
                            label = { Text(strings.cookieDomainLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = inputCookies,
                            onValueChange = { inputCookies = it },
                            label = { Text(strings.cookieRawCookiesLabel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            maxLines = 5,
                        )

                        OutlinedTextField(
                            value = inputUserAgent,
                            onValueChange = { inputUserAgent = it },
                            label = { Text(strings.cookieCustomUaLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        if (statusMessage != null) {
                            Text(
                                text = statusMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (inputDomain.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val url = if (inputDomain.startsWith("http")) {
                                            inputDomain
                                        } else {
                                            "https://$inputDomain"
                                        }
                                        DesktopBrowserHelper.openInBrowser(url)
                                    },
                                ) {
                                    Text(strings.openInBrowser)
                                }
                            }

                            if (selectedDomain != null) {
                                OutlinedButton(
                                    onClick = {
                                        cookieStore.removeCookies(selectedDomain!!)
                                        configs = cookieStore.listAll()
                                        selectConfig(null)
                                        statusMessage = strings.cookieRemovedStatus(selectedDomain!!)
                                    },
                                ) {
                                    Text(strings.cookieDelete)
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = {
                                    val domain = inputDomain.trim()
                                    if (domain.isNotEmpty()) {
                                        val parsed = DesktopCookieStore.parseRawCookies(inputCookies)
                                        val ua = inputUserAgent.trim().takeIf { it.isNotEmpty() }
                                        cookieStore.setCookies(domain, parsed, ua)
                                        configs = cookieStore.listAll()
                                        selectedDomain = DesktopCookieStore.normalizeDomain(domain)
                                        statusMessage = strings.cookieSavedStatus(parsed.size, domain)
                                    }
                                },
                                enabled = inputDomain.isNotBlank(),
                            ) {
                                Text(strings.cookieSave)
                            }
                        }
                    }
                }
            }
        }
    }
}
