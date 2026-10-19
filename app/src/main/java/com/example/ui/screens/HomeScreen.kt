package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DuplicateGroup
import com.example.model.MediaFileItem
import com.example.model.RamStats
import com.example.model.StorageMetrics
import com.example.ui.RoomyScreen
import com.example.ui.components.CategoryTile
import com.example.ui.components.SmallStorageRing
import com.example.ui.theme.RoomyBackground
import com.example.ui.theme.RoomyCardBg
import com.example.ui.theme.RoomyCompress
import com.example.ui.theme.RoomyCompressLight
import com.example.ui.theme.RoomyCoral
import com.example.ui.theme.RoomyCoralLight
import com.example.ui.theme.RoomyDelete
import com.example.ui.theme.RoomyDeleteLight
import com.example.ui.theme.RoomyDivider
import com.example.ui.theme.RoomyLavender
import com.example.ui.theme.RoomyLavenderLight
import com.example.ui.theme.RoomyTextMain
import com.example.ui.theme.RoomyTextSecondary

@Composable
fun HomeScreen(
    storageMetrics: StorageMetrics,
    ramStats: RamStats,
    mediaList: List<MediaFileItem>,
    duplicateGroups: List<DuplicateGroup>,
    onNavigate: (RoomyScreen) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unreviewedCount = mediaList.count { !it.isExcluded }
    val photos = mediaList.filter { !it.isVideo }
    val videos = mediaList.filter { it.isVideo }
    val largeFiles = mediaList.filter { it.sizeBytes >= 100L * 1024L * 1024L }

    val photosBytes = photos.sumOf { it.sizeBytes }
    val videosBytes = videos.sumOf { it.sizeBytes }
    val duplicateSavingsBytes = duplicateGroups.sumOf { it.removableSize }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(RoomyBackground)
            .testTag("roomy_home_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Roomy",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RoomyTextMain
                    )
                    Text(
                        text = "Delete what you don't need. Shrink what you keep.",
                        fontSize = 12.sp,
                        color = RoomyTextSecondary
                    )
                }

                IconButton(
                    onClick = onRescan,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(RoomyCardBg)
                        .testTag("rescan_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rescan",
                        tint = RoomyTextMain,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Compact Storage Ring
        item {
            SmallStorageRing(
                usedPercent = storageMetrics.usedStoragePercent,
                usedFormatted = storageMetrics.usedStorageFormatted,
                totalFormatted = storageMetrics.totalStorageFormatted,
                freeFormatted = storageMetrics.freeStorageFormatted
            )
        }

        // Primary Hero Action: "Review files"
        item {
            Button(
                onClick = { onNavigate(RoomyScreen.REVIEW) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("review_files_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoomyCoral,
                    contentColor = RoomyTextMain
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Review files ($unreviewedCount ready)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Primary Core Category Entries
        item {
            Text(
                text = "CLEAN & SHRINK",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = RoomyTextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        item {
            CategoryTile(
                title = "Photos",
                subtitle = "${photos.size} items · ${StorageMetrics.formatBytes(photosBytes)}",
                icon = Icons.Default.Image,
                iconTint = RoomyLavender,
                iconBgColor = RoomyLavenderLight,
                onClick = { onNavigate(RoomyScreen.PHOTOS) },
                testTag = "tile_photos"
            )
        }

        item {
            CategoryTile(
                title = "Videos",
                subtitle = "${videos.size} clips · ${StorageMetrics.formatBytes(videosBytes)}",
                icon = Icons.Default.Videocam,
                iconTint = RoomyCoral,
                iconBgColor = RoomyCoralLight,
                onClick = { onNavigate(RoomyScreen.VIDEOS) },
                testTag = "tile_videos"
            )
        }

        item {
            CategoryTile(
                title = "Exact duplicates",
                subtitle = "${duplicateGroups.size} identical SHA-256 groups",
                icon = Icons.Default.CopyAll,
                iconTint = RoomyLavender,
                iconBgColor = RoomyLavenderLight,
                badgeText = if (duplicateSavingsBytes > 0) StorageMetrics.formatBytes(duplicateSavingsBytes) else "",
                onClick = { onNavigate(RoomyScreen.DUPLICATES) },
                testTag = "tile_duplicates"
            )
        }

        item {
            CategoryTile(
                title = "Large files",
                subtitle = "${largeFiles.size} items over 100 MB",
                icon = Icons.Default.FolderZip,
                iconTint = RoomyCoral,
                iconBgColor = RoomyCoralLight,
                onClick = { onNavigate(RoomyScreen.LARGE_FILES) },
                testTag = "tile_large_files"
            )
        }

        item {
            CategoryTile(
                title = "Bulk compress",
                subtitle = "On-device CPU & GPU reduction without server uploads",
                icon = Icons.Default.Compress,
                iconTint = RoomyCompress,
                iconBgColor = RoomyCompressLight,
                onClick = { onNavigate(RoomyScreen.BULK_COMPRESS) },
                testTag = "tile_bulk_compress"
            )
        }

        // Secondary Section (Separated)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = RoomyDivider, thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SUPPORTING TOOLS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = RoomyTextSecondary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        item {
            CategoryTile(
                title = "Junk cleanup",
                subtitle = "App temporary buffers & system storage management",
                icon = Icons.Default.DeleteSweep,
                iconTint = RoomyDelete,
                iconBgColor = RoomyDeleteLight,
                onClick = { onNavigate(RoomyScreen.JUNK) },
                testTag = "tile_junk"
            )
        }

        item {
            CategoryTile(
                title = "RAM",
                subtitle = "${ramStats.formattedAvail} free of ${ramStats.formattedTotal} (Android auto-managed)",
                icon = Icons.Default.Memory,
                iconTint = RoomyLavender,
                iconBgColor = RoomyLavenderLight,
                onClick = { onNavigate(RoomyScreen.RAM) },
                testTag = "tile_ram"
            )
        }
    }
}
