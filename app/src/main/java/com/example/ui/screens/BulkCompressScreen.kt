package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CompressionJobResult
import com.example.model.CompressionPreset
import com.example.model.CompressionStatus
import com.example.model.MediaFileItem
import com.example.model.StorageMetrics
import com.example.ui.BulkCompressOptions
import com.example.ui.CompressionState
import com.example.ui.components.MediaThumbnail
import com.example.ui.theme.RoomyBackground
import com.example.ui.theme.RoomyCardBg
import com.example.ui.theme.RoomyCompress
import com.example.ui.theme.RoomyCompressLight
import com.example.ui.theme.RoomyCoral
import com.example.ui.theme.RoomyDelete
import com.example.ui.theme.RoomyDivider
import com.example.ui.theme.RoomyKeep
import com.example.ui.theme.RoomyKeepLight
import com.example.ui.theme.RoomyTextMain
import com.example.ui.theme.RoomyTextSecondary

@Composable
fun BulkCompressScreen(
    state: CompressionState,
    selectedPreset: CompressionPreset,
    targets: List<MediaFileItem>?,
    bulkCandidates: List<MediaFileItem>,
    bulkOptions: BulkCompressOptions,
    onSelectPreset: (CompressionPreset) -> Unit,
    onBulkOptionsChange: (BulkCompressOptions) -> Unit,
    onStartCompression: (List<MediaFileItem>) -> Unit,
    onCancel: () -> Unit,
    onReplaceOriginals: () -> Unit,
    onDismissResults: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBulk = targets == null
    val toCompress = targets ?: bulkCandidates
    val totalBytes = toCompress.sumOf { it.sizeBytes }
    val showResults = !state.isRunning && state.results.isNotEmpty()

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
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_button_compress")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RoomyTextMain)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (isBulk) "Bulk compress" else "Compress",
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                state.isRunning -> item { ProgressCard(state, selectedPreset, onCancel) }
                showResults -> {
                    item { ResultsSummary(state, onReplaceOriginals, onDismissResults) }
                    items(state.results, key = { it.source.id }) { ResultRow(it) }
                }
                else -> {
                    item { InfoBanner() }
                    if (isBulk) {
                        item { BulkFilters(bulkOptions, onBulkOptionsChange) }
                    } else {
                        item { SelectedFilesStrip(toCompress) }
                    }
                    item {
                        Text(
                            text = "PRESET",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = RoomyTextSecondary
                        )
                    }
                    items(CompressionPreset.entries) { preset ->
                        PresetCard(preset, preset == selectedPreset) { onSelectPreset(preset) }
                    }
                }
            }
        }

        // Start button
        if (!state.isRunning && !showResults) {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Button(
                    onClick = { onStartCompression(toCompress) },
                    enabled = toCompress.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_compression_button"),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoomyCoral, contentColor = RoomyTextMain)
                ) {
                    Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (toCompress.isEmpty()) "Nothing to compress"
                        else "Compress ${countLabel(toCompress.size, "file")} (${StorageMetrics.formatBytes(totalBytes)})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RoomyCardBg)
            .border(1.dp, RoomyDivider, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(RoomyCompressLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = RoomyCompress, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("Your originals stay safe", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RoomyTextMain)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Smaller copies are saved to your gallery next to the originals, on this phone only. " +
                    "Nothing is deleted until you choose \"Replace originals\" afterwards.",
                fontSize = 12.sp,
                color = RoomyTextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun BulkFilters(options: BulkCompressOptions, onChange: (BulkCompressOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("WHAT TO COMPRESS", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = RoomyTextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionChip("Photos", options.includePhotos) { onChange(options.copy(includePhotos = !options.includePhotos)) }
            OptionChip("Videos", options.includeVideos) { onChange(options.copy(includeVideos = !options.includeVideos)) }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0, 1, 5, 20, 100).forEach { mb ->
                OptionChip(if (mb == 0) "Any size" else "Over $mb MB", options.minSizeMb == mb) {
                    onChange(options.copy(minSizeMb = mb))
                }
            }
        }
        Text(
            text = "Files you marked Keep, GIFs and copies Cleanser already made are skipped. Videos take longer than photos.",
            fontSize = 11.sp,
            color = RoomyTextSecondary
        )
    }
}

@Composable
private fun SelectedFilesStrip(files: List<MediaFileItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (files.size == 1) "1 SELECTED FILE" else "${files.size} SELECTED FILES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = RoomyTextSecondary
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            files.take(30).forEach { file ->
                MediaThumbnail(
                    item = file,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

@Composable
private fun PresetCard(preset: CompressionPreset, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) RoomyCardBg else Color.White)
            .border(1.dp, if (isSelected) RoomyTextMain else RoomyDivider, RoundedCornerShape(16.dp))
            .clickable { onClick() }
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
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RoomyTextMain)
                Spacer(modifier = Modifier.height(2.dp))
                Text(preset.description, fontSize = 12.sp, color = RoomyTextSecondary)
            }
        }
    }
}

@Composable
private fun ProgressCard(state: CompressionState, preset: CompressionPreset, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RoomyCardBg)
            .padding(18.dp)
    ) {
        Text(
            text = "Compressing ${minOf(state.done + 1, state.total)} of ${state.total} · ${preset.label}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = RoomyTextMain
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(state.currentName, fontSize = 12.sp, color = RoomyTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { state.overallProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = RoomyCoral,
            trackColor = RoomyDivider
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(state.overallProgress * 100).toInt()}% · Keep Cleanser open; the screen stays on until it finishes.",
            fontSize = 11.sp,
            color = RoomyTextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(12.dp)) {
            Text("Cancel", color = RoomyDelete, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ResultsSummary(state: CompressionState, onReplaceOriginals: () -> Unit, onDismiss: () -> Unit) {
    val saved = state.savedResults
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
            imageVector = if (saved.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (saved.isNotEmpty()) RoomyKeep else RoomyTextSecondary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        val headline = when {
            state.originalsReplaced -> "Done: originals replaced"
            saved.isNotEmpty() -> "${countLabel(saved.size, "smaller copy", "smaller copies")} saved"
            else -> "No files got meaningfully smaller"
        }
        Text(headline, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RoomyTextMain)
        Spacer(modifier = Modifier.height(4.dp))
        val detail = when {
            state.wasCancelled -> "Cancelled after ${state.done} of ${state.total} files. Copies made so far are kept."
            state.originalsReplaced -> "The smaller copies are now in your gallery."
            saved.isNotEmpty() -> "Replace the originals to free ${StorageMetrics.formatBytes(state.potentialSavings)}. Until then both versions are in your gallery."
            else -> "These files are already well compressed."
        }
        Text(detail, fontSize = 12.sp, color = RoomyTextSecondary)

        Spacer(modifier = Modifier.height(14.dp))
        if (saved.isNotEmpty() && !state.originalsReplaced) {
            Button(
                onClick = onReplaceOriginals,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoomyCoral, contentColor = RoomyTextMain)
            ) {
                Text("Replace originals · free ${StorageMetrics.formatBytes(state.potentialSavings)}", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (saved.isNotEmpty() && !state.originalsReplaced) "Keep both versions" else "Done", color = RoomyTextMain)
        }
    }
}

@Composable
private fun ResultRow(result: CompressionJobResult) {
    val (icon: ImageVector, tint: Color) = when (result.status) {
        CompressionStatus.SAVED -> Icons.Default.CheckCircle to RoomyKeep
        CompressionStatus.NO_GAIN, CompressionStatus.SKIPPED -> Icons.Default.RemoveCircleOutline to RoomyTextSecondary
        CompressionStatus.FAILED -> Icons.Default.ErrorOutline to RoomyDelete
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (result.isUsefulReduction) RoomyKeepLight.copy(alpha = 0.5f) else RoomyCardBg)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaThumbnail(
            item = result.source,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(result.sourceName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = RoomyTextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(result.message, fontSize = 11.sp, color = RoomyTextSecondary, maxLines = 2)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (result.isUsefulReduction) {
            Text(
                text = "${StorageMetrics.formatBytes(result.originalSizeBytes)} → ${StorageMetrics.formatBytes(result.compressedSizeBytes)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = RoomyKeep
            )
        } else {
            Icon(icon, contentDescription = result.status.name, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
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

private fun countLabel(count: Int, one: String, many: String = "${one}s") = if (count == 1) "1 $one" else "$count $many"
