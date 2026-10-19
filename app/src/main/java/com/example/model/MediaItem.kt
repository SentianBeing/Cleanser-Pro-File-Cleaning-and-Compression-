package com.example.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReviewAction {
    NONE,
    KEEP,
    DELETE,
    COMPRESS
}

enum class CompressionPreset(val label: String, val quality: Int, val description: String) {
    HIGH_VISUAL_QUALITY("High Visual Quality", 85, "Preserves original dimensions. High visual fidelity (~35% reduction)."),
    BALANCED("Balanced", 72, "Smart downsampling to max 1920px. Ideal daily storage saver (~65% reduction)."),
    CUSTOM("Custom", 60, "Configurable resolution, format & compression parameters.")
}

data class MediaFileItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val mimeType: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val dimensions: String = "",
    val durationSeconds: Int = 0,
    val sha256: String? = null,
    val reviewAction: ReviewAction = ReviewAction.NONE,
    val isExcluded: Boolean = false,
    val isSelectedForDelete: Boolean = false
) {
    val formattedSize: String get() = StorageMetrics.formatBytes(sizeBytes)
    val formattedDate: String get() {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(dateAdded))
    }
    val formattedDuration: String get() {
        if (!isVideo || durationSeconds <= 0) return ""
        val min = durationSeconds / 60
        val sec = durationSeconds % 60
        return "%d:%02d".format(min, sec)
    }
}

data class DuplicateGroup(
    val checksum: String,
    val byteSize: Long,
    val isVideo: Boolean,
    val files: List<MediaFileItem>,
    val recommendedKeeperId: String
) {
    val totalGroupSize: Long get() = files.sumOf { it.sizeBytes }
    val formattedTotalGroupSize: String get() = StorageMetrics.formatBytes(totalGroupSize)
    val removableSize: Long get() {
        val keepers = files.filter { !it.isSelectedForDelete }
        return if (keepers.isNotEmpty()) {
            files.filter { it.isSelectedForDelete }.sumOf { it.sizeBytes }
        } else {
            // Block deleting every copy in a group
            0L
        }
    }
    val formattedRemovableSize: String get() = StorageMetrics.formatBytes(removableSize)
}

data class CompressionJobResult(
    val sourceName: String,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val savedBytes: Long,
    val reductionPercent: Int,
    val isUsefulReduction: Boolean,
    val outputPath: String,
    val message: String
) {
    val formattedSavedBytes: String get() = StorageMetrics.formatBytes(savedBytes)
}

data class JunkItem(
    val id: String,
    val title: String,
    val path: String,
    val sizeBytes: Long,
    val reason: String,
    val isSelected: Boolean = false
) {
    val formattedSize: String get() = StorageMetrics.formatBytes(sizeBytes)
}

data class RamStats(
    val totalBytes: Long = 0L,
    val availBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val percentUsed: Int = 0
) {
    val formattedTotal: String get() = StorageMetrics.formatBytes(totalBytes)
    val formattedAvail: String get() = StorageMetrics.formatBytes(availBytes)
    val formattedUsed: String get() = StorageMetrics.formatBytes(usedBytes)
}
