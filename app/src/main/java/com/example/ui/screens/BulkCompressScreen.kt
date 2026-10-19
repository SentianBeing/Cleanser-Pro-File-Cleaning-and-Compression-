package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CompressionJobResult
import com.example.model.CompressionPreset
import com.example.model.MediaFileItem
import com.example.model.StorageMetrics
import com.example.ui.theme.RoomyBackground
import com.example.ui.theme.RoomyCardBg
import com.example.ui.theme.RoomyCompress
import com.example.ui.theme.RoomyCompressLight
import com.example.ui.theme.RoomyCoral
import com.example.ui.theme.RoomyDivider
import com.example.ui.theme.RoomyKeep
import com.example.ui.theme.RoomyLavender
import com.example.ui.theme.RoomyLavenderLight
import com.example.ui.theme.RoomyTextMain
import com.example.ui.theme.RoomyTextSecondary

@Composable
fun BulkCompressScreen(
    mediaList: List<MediaFileItem>,
    selectedPreset: CompressionPreset,
    isCompressing: Boolean,
    progress: Float,
    results: List<CompressionJobResult>,
    onSelectPreset: (CompressionPreset) -> Unit,
    onStartCompression: (List<MediaFileItem>) -> Unit,
    onDismissResults: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val compressibleCandidates = mediaList.filter { !it.isExcluded }
    val totalOriginalBytes = compressibleCandidates.sumOf { it.sizeBytes }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RoomyBackground)
            .testTag("bulk_compress_screen")
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
                modifier = Modifier.testTag("back_button_compress")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = RoomyTextMain
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Bulk compress",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RoomyTextMain
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Local Processing Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(RoomyCardBg)
                        .border(1.dp, RoomyDivider, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(RoomyCompressLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Compress,
                                contentDescription = null,
                                tint = RoomyCompress,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "100% Local Hardware Compression",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoomyTextMain
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Encodes using on-device CPU & GPU codecs. Media files are never uploaded. Originals are preserved untouched until you review output quality.",
                                fontSize = 12.sp,
                                color = RoomyTextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Preset Selection Section
            item {
                Text(
                    text = "SELECT COMPRESSION PRESET",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = RoomyTextSecondary
                )
            }

            items(CompressionPreset.values()) { preset ->
                val isSelected = preset == selectedPreset

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) RoomyCardBg else Color.White)
                        .border(
                            1.dp,
                            if (isSelected) RoomyTextMain else RoomyDivider,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectPreset(preset) }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) RoomyTextMain else RoomyCardBg),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoomyTextMain
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = preset.description,
                                fontSize = 12.sp,
                                color = RoomyTextSecondary
                            )
                        }
                    }
                }
            }

            // Results summary card if completed
            if (results.isNotEmpty()) {
                item {
                    val totalSaved = results.sumOf { it.savedBytes }
                    val usefulCount = results.count { it.isUsefulReduction }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(RoomyCardBg)
                            .border(1.dp, RoomyDivider, RoundedCornerShape(18.dp))
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = RoomyKeep,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${StorageMetrics.formatBytes(totalSaved)} Saved Locally!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoomyTextMain
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$usefulCount of ${results.size} files yielded useful reductions.",
                            fontSize = 12.sp,
                            color = RoomyTextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        results.take(4).forEach { res ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = res.sourceName,
                                    fontSize = 12.sp,
                                    color = RoomyTextMain,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (res.isUsefulReduction) "-${res.reductionPercent}% (${res.formattedSavedBytes})" else "No useful reduction",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (res.isUsefulReduction) RoomyKeep else RoomyTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onDismissResults,
                            colors = ButtonDefaults.buttonColors(containerColor = RoomyTextMain, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }

        // Bottom CTA Action
        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                if (isCompressing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(RoomyCardBg)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = RoomyCoral,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Compressing locally with ${selectedPreset.label}…",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = RoomyTextMain
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = RoomyCoral,
                            trackColor = RoomyDivider
                        )
                    }
                } else {
                    Button(
                        onClick = { onStartCompression(compressibleCandidates) },
                        enabled = compressibleCandidates.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoomyCoral,
                            contentColor = RoomyTextMain
                        )
                    ) {
                        Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compress ${compressibleCandidates.size} files (${StorageMetrics.formatBytes(totalOriginalBytes)})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
