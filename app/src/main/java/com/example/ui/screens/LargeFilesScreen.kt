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
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import com.example.model.MediaFileItem
import com.example.model.StorageMetrics
import com.example.ui.components.MediaThumbnail
import com.example.ui.theme.RoomyBackground
import com.example.ui.theme.RoomyCardBg
import com.example.ui.theme.RoomyCompress
import com.example.ui.theme.RoomyCompressLight
import com.example.ui.theme.RoomyCoral
import com.example.ui.theme.RoomyCoralLight
import com.example.ui.theme.RoomyDelete
import com.example.ui.theme.RoomyDivider
import com.example.ui.theme.RoomyKeep
import com.example.ui.theme.RoomyTextMain
import com.example.ui.theme.RoomyTextSecondary

@Composable
fun LargeFilesScreen(
    mediaList: List<MediaFileItem>,
    thresholdMb: Int,
    onSetThresholdMb: (Int) -> Unit,
    onCompress: (MediaFileItem) -> Unit,
    onDelete: (MediaFileItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (List<MediaFileItem>, MediaFileItem) -> Unit = { _, _ -> }
) {
    val largeFiles = remember(mediaList, thresholdMb) {
        val thresholdBytes = thresholdMb * 1_000_000L
        mediaList.filter { it.sizeBytes >= thresholdBytes }.sortedByDescending { it.sizeBytes }
    }
    val totalSize = remember(largeFiles) { largeFiles.sumOf { it.sizeBytes } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RoomyBackground)
            .testTag("large_files_screen")
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
                modifier = Modifier.testTag("back_button_large_files")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = RoomyTextMain
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Large files",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RoomyTextMain
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Adjustable Threshold Chips (PRD requirement: "Suggested large-file threshold: 100 MB, adjustable by the user")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text(
                text = "MINIMUM FILE SIZE THRESHOLD",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = RoomyTextSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50, 100, 250).forEach { mb ->
                    val isSelected = mb == thresholdMb
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) RoomyTextMain else RoomyCardBg)
                            .clickable { onSetThresholdMb(mb) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${mb} MB",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else RoomyTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Total count & size header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${largeFiles.size} files over $thresholdMb MB",
                fontSize = 12.sp,
                color = RoomyTextSecondary
            )
            Text(
                text = StorageMetrics.formatBytes(totalSize),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = RoomyCoral
            )
        }

        if (largeFiles.isEmpty()) {
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
                        text = "No Files Over $thresholdMb MB",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoomyTextMain
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lower the threshold above to review smaller files.",
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
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(largeFiles, key = { it.id }) { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(RoomyCardBg)
                            .border(1.dp, RoomyDivider, RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MediaThumbnail(
                                    item = item,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpen(largeFiles, item) }
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = RoomyTextMain
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${item.formattedSize} · ${item.formattedDate}",
                                        fontSize = 12.sp,
                                        color = RoomyTextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onCompress(item) },
                                    modifier = Modifier.weight(1.1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RoomyCompressLight,
                                        contentColor = RoomyCompress
                                    )
                                ) {
                                    Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Compress", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { onDelete(item) },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", fontSize = 11.sp, color = RoomyDelete)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
