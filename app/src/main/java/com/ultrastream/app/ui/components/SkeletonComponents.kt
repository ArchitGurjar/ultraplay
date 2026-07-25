package com.ultrastream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SkeletonPosterCard() {
    Card(
        modifier = Modifier
            .width(130.dp)
            .height(195.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun SkeletonContinueWatchingCard() {
    Card(
        modifier = Modifier
            .width(280.dp)
            .height(120.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun SkeletonRecommendedAddonCard() {
    Card(
        modifier = Modifier
            .width(200.dp)
            .height(120.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    )
}
