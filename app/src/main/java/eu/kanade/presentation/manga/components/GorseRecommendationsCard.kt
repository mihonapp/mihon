package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.gorse.GorseRecommendationItem
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.presentation.core.components.material.padding

// 清洗函数：仅保留英文与中文字符
private fun sanitizeForCopy(raw: String): String {
    if (raw.isEmpty()) return raw
    val sb = StringBuilder(raw.length)
    for (c in raw) {
        if (c == ' ' || c in 'a'..'z' || c in 'A'..'Z' || c in '\u4e00'..'\u9fff') {
            sb.append(c)
        }
    }
    return sb.toString()
}

@Composable
fun GorseRecommendationsCard(
    recommendations: List<GorseRecommendationItem>,
    isLoading: Boolean,
    onRecommendationClick: (GorseRecommendationItem) -> Unit,
    onRecommendationHide: (GorseRecommendationItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
        ) {
            Text(
                text = "Gorse 个性化推荐",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                
                recommendations.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无推荐内容",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                
                else -> {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(
                            items = recommendations.take(20), // 限制显示最多20个推荐
                            key = { it.itemId },
                        ) { recommendation ->
                            GorseRecommendationItem(
                                recommendation = recommendation,
                                onClick = {
                                    val sanitized = sanitizeForCopy(recommendation.itemId)
                                    context.copyToClipboard("Gorse推荐", sanitized)
                                    onRecommendationClick(recommendation)
                                },
                                onHideClick = {
                                    onRecommendationHide(recommendation)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GorseRecommendationItem(
    recommendation: GorseRecommendationItem,
    onClick: () -> Unit,
    onHideClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    
    Box(modifier = modifier.width(160.dp)) {
        // 主卡片（可点击跳转搜索）
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = recommendation.itemId,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 6,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        
        // 右上角"不感兴趣"按钮
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = CircleShape
                )
        ) {
            IconButton(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "不感兴趣",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
    
    // 确认对话框
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("不感兴趣") },
            text = { 
                Text("确定不再推荐「${recommendation.itemId}」吗？\n\n此操作会将该项标记为隐藏，下次刷新后将不再显示。") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onHideClick()
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
