package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MediaFileItem
import com.example.ui.components.ReviewFileCard
import com.example.ui.theme.RoomyBackground
import com.example.ui.theme.RoomyCardBg
import com.example.ui.theme.RoomyCoral
import com.example.ui.theme.RoomyDivider
import com.example.ui.theme.RoomyKeep
import com.example.ui.theme.RoomyTextMain
import com.example.ui.theme.RoomyTextSecondary

enum class ReviewFilter {
    ALL,
    PHOTOS_ONLY,
    VIDEOS_ONLY
}

@Composable
fun ReviewScreen(
    mediaList: List<MediaFileItem>,
    onKeep: (MediaFileItem) -> Unit,
    onUnkeep: (MediaFileItem) -> Unit,
    onCompress: (MediaFileItem) -> Unit,
    onDelete: (MediaFileItem) -> Unit,
    onOpen: (List<MediaFileItem>, MediaFileItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by rememberSaveable { mutableStateOf(ReviewFilter.ALL) }
    var showKept by rememberSaveable { mutableStateOf(false) }

    val typeFiltered = remember(mediaList, selectedFilter) {
        when (selectedFilter) {
            ReviewFilter.ALL -> mediaList
            ReviewFilter.PHOTOS_ONLY -> mediaList.filter { !it.isVideo }
            ReviewFilter.VIDEOS_ONLY -> mediaList.filter { it.isVideo }
        }
    }
    val keptCount = remember(typeFiltered) { typeFiltered.count { it.isExcluded } }
    val shown = remember(typeFiltered, showKept) { if (showKept) typeFiltered else typeFiltered.filter { !it.isExcluded } }
    val pendingCount = typeFiltered.size - keptCount

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RoomyBackground)
            .testTag("roomy_review_screen")
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("back_button_review")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = RoomyTextMain
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Review files",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RoomyTextMain
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Progress through the review: kept files count as reviewed
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Largest first · tap a thumbnail to preview",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = RoomyTextSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$pendingCount to review · $keptCount kept",
                    fontSize = 12.sp,
                    color = RoomyTextSecondary
                )
            }
            if (typeFiltered.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { keptCount.toFloat() / typeFiltered.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = RoomyKeep,
                    trackColor = RoomyDivider
                )
            }
        }

        // Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                label = "All (${mediaList.size})",
                selected = selectedFilter == ReviewFilter.ALL,
                onClick = { selectedFilter = ReviewFilter.ALL }
            )
            FilterChip(
                label = "Photos",
                selected = selectedFilter == ReviewFilter.PHOTOS_ONLY,
                onClick = { selectedFilter = ReviewFilter.PHOTOS_ONLY }
            )
            FilterChip(
                label = "Videos",
                selected = selectedFilter == ReviewFilter.VIDEOS_ONLY,
                onClick = { selectedFilter = ReviewFilter.VIDEOS_ONLY }
            )
            FilterChip(
                label = if (showKept) "Showing kept" else "Show kept ($keptCount)",
                selected = showKept,
                onClick = { showKept = !showKept }
            )
        }

        // Stream of Review Cards
        if (shown.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = RoomyCoral,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "All Files Reviewed",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoomyTextMain
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Everything here is marked Keep. Tap \"Show kept\" to see those files again.",
                        fontSize = 13.sp,
                        color = RoomyTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(shown, key = { it.id }) { item ->
                    ReviewFileCard(
                        item = item,
                        onKeep = { onKeep(item) },
                        onUnkeep = { onUnkeep(item) },
                        onCompress = { onCompress(item) },
                        onDelete = { onDelete(item) },
                        onOpen = { onOpen(shown, item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) RoomyTextMain else RoomyCardBg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else RoomyTextSecondary
        )
    }
}
