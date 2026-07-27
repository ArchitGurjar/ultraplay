package com.ultrastream.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultrastream.app.data.models.MetaItem

@Composable
fun GridSection(
    items: List<MetaItem>,
    onItemClick: (id: String, type: String) -> Unit,
    modifier: Modifier = Modifier,
    progressMap: Map<String, Int> = emptyMap()
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val chunkedItems = items.chunked(3)
        chunkedItems.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    PosterCard(
                        meta = item,
                        onClick = { onItemClick(item.id, item.type) },
                        showProgress = true,
                        progressPercent = progressMap[item.id] ?: 0,
                        modifier = Modifier.weight(1f).aspectRatio(0.67f)
                    )
                }
                val emptySpaces = 3 - rowItems.size
                repeat(emptySpaces) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

