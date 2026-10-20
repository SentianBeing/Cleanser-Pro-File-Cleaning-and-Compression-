package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.model.StorageMetrics
import com.example.ui.components.MediaGrid
import com.example.ui.components.SelectionActionBar
import com.example.ui.theme.RoomyBackground
import com.example.ui.theme.RoomyCardBg
import com.example.ui.theme.RoomyKeep
import com.example.ui.theme.RoomyLavender
import com.example.ui.theme.RoomyTextMain
import com.example.ui.theme.RoomyTextSecondary

enum class MediaSort(val label: String) {
    LARGEST("Largest"),
    NEWEST("Newest"),
    OLDEST("Oldest")
}

/** Shared grid screen for Photos, Videos and folder collections (Screenshots, WhatsApp, ...). */
@Composable
fun MediaGridScreen(
    title: String,
    items: List<MediaFileItem>,
    onOpen: (List<MediaFileItem>, MediaFileItem) -> Unit,
    onCompress: (List<MediaFileItem>) -> Unit,
    onDelete: (List<MediaFileItem>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    sizeFiltersMb: List<Int> = emptyList(),
    emptyTitle: String = "Nothing here",
    testTag: String = "media_grid_screen"
) {
    var sort by rememberSaveable { mutableStateOf(MediaSort.LARGEST) }
    var minSizeMb by rememberSaveable { mutableStateOf(0) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selectedSet = remember(selectedIds) { selectedIds.toSet() }

    val shown = remember(items, sort, minSizeMb) {
        val minBytes = minSizeMb * 1_000_000L
        val filtered = if (minSizeMb > 0) items.filter { it.sizeBytes >= minBytes } else items
        when (sort) {
            MediaSort.LARGEST -> filtered.sortedByDescending { it.sizeBytes }
            MediaSort.NEWEST -> filtered.sortedByDescending { it.displayDate }
            MediaSort.OLDEST -> filtered.sortedBy { it.displayDate }
        }
    }
    // Selected files that still exist (deleted ones drop out automatically)
    val selectedItems = remember(shown, selectedSet) { shown.filter { it.id in selectedSet } }
    val totalBytes = remember(shown) { shown.sumOf { it.sizeBytes } }

    BackHandler(enabled = selectedIds.isNotEmpty()) { selectedIds = emptyList() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RoomyBackground)
            .testTag(testTag)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedItems.isNotEmpty()) {
                IconButton(onClick = { selectedIds = emptyList() }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = RoomyTextMain)
                }
                Text(
                    text = "${selectedItems.size} selected",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoomyTextMain,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { selectedIds = shown.map { it.id } }) {
                    Text("Select all", color = RoomyLavender, fontWeight = FontWeight.SemiBold)
                }
            } else {
                IconButton(onClick = onBack, modifier = Modifier.testTag("back_button_$testTag")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RoomyTextMain)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RoomyTextMain)
                    Text(
                        text = "${if (shown.size == 1) "1 item" else "${shown.size} items"} · ${StorageMetrics.formatBytes(totalBytes)}",
                        fontSize = 12.sp,
                        color = RoomyTextSecondary
                    )
                }
            }
        }

        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = RoomyTextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // Sort and size filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MediaSort.entries.forEach { option ->
                GridChip(option.label, sort == option) { sort = option }
            }
            if (sizeFiltersMb.isNotEmpty()) {
                Spacer(modifier = Modifier.size(4.dp))
                GridChip("Any size", minSizeMb == 0) { minSizeMb = 0 }
                sizeFiltersMb.forEach { mb ->
                    GridChip("Over $mb MB", minSizeMb == mb) { minSizeMb = mb }
                }
            }
        }

        if (shown.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RoomyKeep, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(emptyTitle, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RoomyTextMain, textAlign = TextAlign.Center)
                }
            }
        } else {
            MediaGrid(
                items = shown,
                selectedIds = selectedSet,
                onOpen = { onOpen(shown, it) },
                onToggleSelection = { item ->
                    selectedIds = if (item.id in selectedSet) selectedIds - item.id else selectedIds + item.id
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }

        if (selectedItems.isNotEmpty()) {
            SelectionActionBar(
                selected = selectedItems,
                onCompress = {
                    onCompress(selectedItems)
                    selectedIds = emptyList()
                },
                onDelete = {
                    onDelete(selectedItems)
                    selectedIds = emptyList()
                }
            )
        }
    }
}

@Composable
private fun GridChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) RoomyTextMain else RoomyCardBg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else RoomyTextSecondary
        )
    }
}
