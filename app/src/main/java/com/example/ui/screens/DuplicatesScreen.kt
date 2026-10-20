package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DuplicateGroup
import com.example.model.MediaFileItem
import com.example.model.StorageMetrics
import com.example.ui.components.MediaThumbnail
import com.example.ui.theme.RoomyBackground
import com.example.ui.theme.RoomyCardBg
import com.example.ui.theme.RoomyCoral
import com.example.ui.theme.RoomyDelete
import com.example.ui.theme.RoomyDivider
import com.example.ui.theme.RoomyKeep
import com.example.ui.theme.RoomyKeepLight
import com.example.ui.theme.RoomyLavender
import com.example.ui.theme.RoomyLavenderLight
import com.example.ui.theme.RoomyTextMain
import com.example.ui.theme.RoomyTextSecondary

@Composable
fun DuplicatesScreen(
    groups: List<DuplicateGroup>,
    onToggleSelection: (String, String) -> Unit,
    onDeleteSelected: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isChecking: Boolean = false,
    onSelectExtraCopies: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onOpen: (List<MediaFileItem>, MediaFileItem) -> Unit = { _, _ -> }
) {
    val totalRemovableBytes = groups.sumOf { it.removableSize }
    val totalSelectedCount = groups.sumOf { g -> if (g.removableSize > 0) g.files.count { it.isSelectedForDelete } else 0 }
    val anySelected = groups.any { g -> g.files.any { it.isSelectedForDelete } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RoomyBackground)
            .testTag("duplicates_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("back_button_duplicates")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = RoomyTextMain
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Duplicates",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RoomyTextMain
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Info Scope Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isChecking) "Checking files…" else (if (groups.size == 1) "1 set of exact copies" else "${groups.size} sets of exact copies"),
                fontSize = 12.sp,
                color = RoomyTextSecondary,
                modifier = Modifier.weight(1f)
            )
            if (groups.isNotEmpty()) {
                TextButton(onClick = if (anySelected) onClearSelection else onSelectExtraCopies) {
                    Text(
                        text = if (anySelected) "Clear selection" else "Select extra copies",
                        color = RoomyLavender,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = RoomyKeep,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isChecking) "Still checking…" else "No Duplicates Found",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoomyTextMain
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isChecking) "Duplicates will appear here when the check finishes."
                        else "No exact copies among the photos and videos Cleanser can see.",
                        fontSize = 13.sp,
                        color = RoomyTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(groups, key = { it.checksum }) { group ->
                    val isAllSelectedInGroup = group.files.all { it.isSelectedForDelete }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(RoomyCardBg)
                            .border(1.dp, RoomyDivider, RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(RoomyLavenderLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CopyAll,
                                        contentDescription = null,
                                        tint = RoomyLavender,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${group.files.size} identical copies (${StorageMetrics.formatBytes(group.byteSize)} each)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoomyTextMain
                                    )
                                    Text(
                                        text = "Total: ${group.formattedTotalGroupSize} · Removable: ${group.formattedRemovableSize}",
                                        fontSize = 12.sp,
                                        color = RoomyTextSecondary
                                    )
                                }
                            }

                            // Warning if user tries to check all copies
                            if (isAllSelectedInGroup) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RoomyLavenderLight)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "At least one copy must be retained in each group to prevent accidental data loss.",
                                        fontSize = 11.sp,
                                        color = RoomyLavender,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            group.files.forEach { file ->
                                val isKeeper = file.id == group.recommendedKeeperId

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onToggleSelection(group.checksum, file.id) }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = file.isSelectedForDelete,
                                        onCheckedChange = { onToggleSelection(group.checksum, file.id) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = RoomyCoral,
                                            checkmarkColor = Color.White
                                        )
                                    )

                                    MediaThumbnail(
                                        item = file,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onOpen(group.files, file) }
                                    )

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontSize = 13.sp,
                                            fontWeight = if (isKeeper) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = RoomyTextMain
                                        )
                                        Text(
                                            text = "${file.formattedDate} · ${file.folder.trimEnd('/').ifEmpty { "Unknown folder" }}",
                                            fontSize = 11.sp,
                                            color = RoomyTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (isKeeper) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(RoomyKeepLight)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = RoomyKeep,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "Recommended",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RoomyKeep
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Button
        if (totalSelectedCount > 0 && totalRemovableBytes > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Button(
                    onClick = onDeleteSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoomyCoral, contentColor = RoomyTextMain)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete $totalSelectedCount copies · Free ${StorageMetrics.formatBytes(totalRemovableBytes)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

}
