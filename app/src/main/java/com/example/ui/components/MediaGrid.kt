package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MediaFileItem
import com.example.model.StorageMetrics
import com.example.ui.theme.RoomyBackground
import com.example.ui.theme.RoomyCompress
import com.example.ui.theme.RoomyCompressLight
import com.example.ui.theme.RoomyCoral
import com.example.ui.theme.RoomyDelete
import com.example.ui.theme.RoomyDeleteLight
import com.example.ui.theme.RoomyDivider
import com.example.ui.theme.RoomyKeep

/**
 * Photo/video grid. Tap opens the preview; long-press starts multi-select and
 * taps then toggle selection.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGrid(
    items: List<MediaFileItem>,
    selectedIds: Set<String>,
    onOpen: (MediaFileItem) -> Unit,
    onToggleSelection: (MediaFileItem) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    showSizes: Boolean = true
) {
    val selectionMode = selectedIds.isNotEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items, key = { it.id }, contentType = { "media" }) { item ->
            val selected = item.id in selectedIds
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { if (selectionMode) onToggleSelection(item) else onOpen(item) },
                        onLongClick = { onToggleSelection(item) }
                    )
                    .let { if (selected) it.border(3.dp, RoomyCoral, RoundedCornerShape(8.dp)) else it }
            ) {
                MediaThumbnail(item = item, modifier = Modifier.fillMaxSize())

                if (showSizes) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(item.formattedSize, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
                if (item.isExcluded) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Kept",
                        tint = RoomyKeep,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.Black.copy(alpha = 0.25f))
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = RoomyCoral, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Bottom bar shown while files are selected. */
@Composable
fun SelectionActionBar(
    selected: List<MediaFileItem>,
    onCompress: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bytes = selected.sumOf { it.sizeBytes }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RoomyBackground)
            .border(1.dp, RoomyDivider)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${selected.size} selected\n${StorageMetrics.formatBytes(bytes)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onCompress,
            modifier = Modifier.height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RoomyCompressLight, contentColor = RoomyCompress)
        ) {
            Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Compress", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onDelete,
            modifier = Modifier.height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RoomyDeleteLight, contentColor = RoomyDelete)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Delete", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
