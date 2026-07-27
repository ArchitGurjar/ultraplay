@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ultrastream.app.ui.components.bottomsheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ultrastream.app.data.models.StreamItem
import com.ultrastream.app.ui.components.ShimerPlaceholder
import com.ultrastream.app.ui.components.StreamCard

@Composable
fun StreamsSheet(
    streams: List<StreamItem>,
    isLoading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onStreamClick: (StreamItem) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Available Streams", style = MaterialTheme.typography.headlineSmall)
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            when {
                // ⏳ Loading state with shimmer
                isLoading && streams.isEmpty() -> {
                    LazyColumn {
                        items(5) {
                            ShimmerPlaceholder(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                // ❌ Error state
                error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error: $error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            if (onRetry != null) {
                                Button(
                                    onClick = onRetry,
                                    modifier = Modifier.fillMaxWidth(0.6f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Retry")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
                // 📭 No streams available (but no error)
                !isLoading && streams.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No streams available for this episode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // ✅ Streams list with stable keys
                else -> {
                    LazyColumn {
                        items(
                            items = streams,
                            key = { stream ->
                                // Use URL + addonName as a stable unique identifier
                                val url = stream.url ?: stream.streamUrl ?: stream.externalUrl ?: ""
                                "${url}_${stream.addonName ?: "unknown"}"
                            }
                        ) { stream ->
                            StreamCard(
                                stream = stream,
                                onClick = { onStreamClick(stream) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}