@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
package com.ultrastream.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.ui.theme.*

@Composable
fun StreamCard(
    stream: StreamItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stream.addonName ?: "Addon",
                    color = AccentBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "4KHDHub 4K",
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stream.title ?: stream.name ?: "Stream",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Tag(text = "2024", icon = Icons.Default.CalendarToday, color = AccentGold)
                Tag(text = "Hindi", icon = Icons.Default.Translate, color = AccentOrange)
                Tag(text = "18.74 GB", icon = Icons.Default.Storage, color = AccentOrange)
                Tag(text = "2160P", icon = Icons.Default.Monitor, color = Color.White)
                
                OutlinedTag(text = "HDR", color = TextMuted)
                OutlinedTag(text = "DV", color = TextMuted)
                OutlinedTag(text = "English", color = AccentBlue)
            }
        }
    }
}

@Composable
private fun Tag(text: String, icon: ImageVector, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OutlinedTag(text: String, color: Color) {
    Surface(
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

