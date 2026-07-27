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
import com.ultrastream.app.utils.StreamParser

@Composable
fun StreamCard(
    stream: StreamItem,
    onClick: () -> Unit
) {
    val metadata = StreamParser().parseMetadata(
        (stream.title ?: "") + " " + (stream.name ?: "") + " " + (stream.description ?: "")
    )

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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stream.addonName ?: "Addon",
                        color = AccentBlue,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                    // Prominent Quality Badge
                    val topQual = metadata.quals.firstOrNull() ?: "SD"
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = topQual,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (metadata.isLive) {
                    Surface(
                        color = AccentRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "LIVE",
                            color = AccentRed,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = metadata.cleanText,
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
                if (metadata.size != null) {
                    Tag(text = metadata.size, icon = Icons.Default.Storage, color = AccentGold)
                }
                if (metadata.seeds != null) {
                    Tag(text = metadata.seeds + " seeds", icon = Icons.Default.Group, color = AccentGreen)
                }
                if (metadata.parsedSeason != null && metadata.parsedEpisode != null) {
                    Tag(text = "S${metadata.parsedSeason} E${metadata.parsedEpisode}", icon = Icons.Default.Tv, color = AccentBlue)
                }
                if (metadata.parsedYear != null) {
                    Tag(text = metadata.parsedYear, icon = Icons.Default.Event, color = TextMuted)
                }
                metadata.quals.forEach { qual ->
                    when {
                        qual.contains("4K") || qual.contains("2160p") -> Tag(text = qual, icon = Icons.Default.Monitor, color = Color.White)
                        qual.contains("HDR") -> Tag(text = qual, icon = Icons.Default.BrightnessHigh, color = AccentOrange)
                        qual.contains("1080p") -> Tag(text = "1080p", icon = Icons.Default.Monitor, color = AccentBlue)
                        qual.contains("720p") -> Tag(text = "720p", icon = Icons.Default.Monitor, color = AccentBlue)
                        qual.contains("DV") -> Tag(text = "DV", icon = Icons.Default.BrightnessHigh, color = AccentPurple)
                        else -> Tag(text = qual, icon = Icons.Default.Monitor, color = TextMuted)
                    }
                }
                metadata.langs.forEach { lang ->
                    if (lang.contains("Hindi", ignoreCase = true) || lang.contains("हिंदी") || lang.contains("हिन्दी")) {
                        Tag(text = lang, icon = Icons.Default.Translate, color = AccentOrange)
                    } else {
                        Tag(text = lang, icon = Icons.Default.Translate, color = AccentBlue)
                    }
                }
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

