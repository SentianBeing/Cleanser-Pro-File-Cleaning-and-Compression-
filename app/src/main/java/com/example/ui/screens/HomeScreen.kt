package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DuplicateGroup
import com.example.model.MediaFileItem
import com.example.model.RamStats
import com.example.model.StorageMetrics
import com.example.ui.MediaAccess
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
import com.example.ui.theme.RoomyKeep
import com.example.ui.theme.RoomyLavender
import com.example.ui.theme.RoomyLavenderLight
import com.example.ui.theme.RoomyTextMain
import com.example.ui.theme.RoomyTextSecondary

@Composable
fun HomeScreen(
    storageMetrics: StorageMetrics,
    ramStats: RamStats,
    photos: List<MediaFileItem>,
    videos: List<MediaFileItem>,
    duplicateGroups: List<DuplicateGroup>,
    largeFileThresholdMb: Int,
    mediaAccess: MediaAccess,
    permissionPermanentlyDenied: Boolean,
    isScanning: Boolean,
    scanProgressText: String,
    hasScanned: Boolean,
    duplicatesChecked: Boolean,
    totalCleaned: Long,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchPhotoPicker: () -> Unit,
    onNavigate: (RoomyScreen) -> Unit,
    onOpenBulkCompress: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCount = photos.size + videos.size
    val unreviewedCount = remember(photos, videos) { photos.count { !it.isExcluded } + videos.count { !it.isExcluded } }
    val photosBytes = remember(photos) { photos.sumOf { it.sizeBytes } }
    val videosBytes = remember(videos) { videos.sumOf { it.sizeBytes } }
    val largeFileCount = remember(photos, videos, largeFileThresholdMb) {
        val threshold = largeFileThresholdMb * 1_000_000L
        photos.count { it.sizeBytes >= threshold } + videos.count { it.sizeBytes >= threshold }
    }
    val duplicateSavingsBytes = remember(duplicateGroups) {
        // What deleting every extra copy (keeping one per group) would free
        duplicateGroups.sumOf { it.byteSize * (it.files.size - 1) }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Brand logo; the soft shadow keeps the near-white tile visible on the white background
                    Image(
                        painter = painterResource(R.drawable.cleanser_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(percent = 28),
                                clip = false,
                                ambientColor = RoomyLavender,
                                spotColor = RoomyLavender
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Cleanser",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RoomyTextMain
                        )
                        Text(
                            text = stringResource(R.string.tagline),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = RoomyTextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onRescan,
                    enabled = !isScanning,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(RoomyCardBg)
                        .testTag("rescan_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rescan",
                        tint = if (isScanning) RoomyTextSecondary else RoomyTextMain,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Compact Real Storage Ring
        item {
            SmallStorageRing(
                usedPercent = storageMetrics.usedStoragePercent,
                usedFormatted = storageMetrics.usedStorageFormatted,
                totalFormatted = storageMetrics.totalStorageFormatted,
                freeFormatted = storageMetrics.freeStorageFormatted
            )
        }

        if (totalCleaned > 0) {
            item {
                Text(
                    text = "Cleanser has cleaned up ${StorageMetrics.formatBytes(totalCleaned)} so far",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RoomyKeep,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Live scan status instead of a misleading "0 found" while the scan runs
        if (isScanning) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RoomyCardBg)
                        .padding(14.dp)
                        .testTag("scan_progress")
                ) {
                    Text(
                        text = scanProgressText.ifEmpty { "Scanning…" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoomyTextMain
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = RoomyCoral,
                        trackColor = RoomyDivider
                    )
                }
            }
        }

        // Permission banners
        if (mediaAccess != MediaAccess.FULL) {
            item {
                PermissionBanner(
                    mediaAccess = mediaAccess,
                    permanentlyDenied = permissionPermanentlyDenied,
                    onRequestPermission = onRequestPermission,
                    onOpenSettings = onOpenSettings,
                    onLaunchPhotoPicker = onLaunchPhotoPicker
                )
            }
        }

        // Primary Hero Action: "Review files"
        item {
            Button(
                onClick = { onNavigate(RoomyScreen.REVIEW) },
                enabled = totalCount > 0,
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
                    text = when {
                        totalCount > 0 -> "Review files ($unreviewedCount to review)"
                        isScanning || !hasScanned -> "Review files"
                        else -> "Review files (0 found)"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Honest empty state, only once a scan has actually finished
        if (totalCount == 0 && hasScanned && !isScanning && mediaAccess != MediaAccess.NONE) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(RoomyCardBg)
                        .border(1.dp, RoomyDivider, RoundedCornerShape(18.dp))
                        .padding(18.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = RoomyLavender,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No photos or videos found",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoomyTextMain
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your gallery looks empty. You can also pick files manually.",
                            fontSize = 12.sp,
                            color = RoomyTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onLaunchPhotoPicker,
                            colors = ButtonDefaults.buttonColors(containerColor = RoomyTextMain, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick photos or videos", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
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
                title = "Duplicates",
                subtitle = when {
                    !duplicatesChecked -> "Checking…"
                    duplicateGroups.isEmpty() -> "No exact copies found"
                    duplicateGroups.size == 1 -> "1 set of exact copies"
                    else -> "${duplicateGroups.size} sets of exact copies"
                },
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
                subtitle = "$largeFileCount items over $largeFileThresholdMb MB",
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
                subtitle = "Smaller copies made on your phone; originals stay until you confirm",
                icon = Icons.Default.Compress,
                iconTint = RoomyCompress,
                iconBgColor = RoomyCompressLight,
                onClick = onOpenBulkCompress,
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
                subtitle = "Screenshots, recordings, chat media & leftovers",
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

        // The on-device promise, visible where people decide whether to trust the app
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = RoomyTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.on_device_promise),
                    fontSize = 12.sp,
                    color = RoomyTextSecondary,
                    textAlign = TextAlign.Center,
                    // Wrap to the text's own width so the lock icon stays right beside it
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(
    mediaAccess: MediaAccess,
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchPhotoPicker: () -> Unit
) {
    val limited = mediaAccess == MediaAccess.PARTIAL
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RoomyCardBg)
            .border(1.dp, RoomyDivider, RoundedCornerShape(18.dp))
            .padding(16.dp)
            .testTag("permission_banner")
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(RoomyCoralLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = RoomyCoral,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (limited) "Limited access" else "Gallery access needed",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoomyTextMain
                    )
                    Text(
                        text = when {
                            limited -> "Cleanser only sees the photos you selected. Allow all photos to find duplicates and large files across your gallery."
                            permanentlyDenied -> "Access was turned off. Turn on \"Photos and videos\" for Cleanser in Android settings, or pick files manually."
                            else -> "Allow access to scan your photos and videos, or pick specific files manually."
                        },
                        fontSize = 12.sp,
                        color = RoomyTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = if (permanentlyDenied || limited) onOpenSettings else onRequestPermission,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoomyCoral,
                        contentColor = RoomyTextMain
                    )
                ) {
                    Text(
                        text = if (permanentlyDenied || limited) "Open settings" else "Allow access",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = if (limited) onRequestPermission else onLaunchPhotoPicker,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (limited) "Change selection" else "Pick files", fontSize = 12.sp, color = RoomyTextMain)
                }
            }
        }
    }
}
