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

enum class CompressionPreset(
    val label: String,
    val quality: Int,
    val maxImageSide: Int,
    val maxVideoShortSide: Int,
    val videoBitrate: Int,
    val description: String
) {
    HIGH_VISUAL_QUALITY(
        "High visual quality", 85, 4096, 1080, 12_000_000,
        "Photos up to 4096 px at quality 85, videos up to 1080p. Hard to tell apart from the original."
    ),
    BALANCED(
        "Balanced", 75, 1920, 720, 3_000_000,
        "Photos up to 1920 px, videos up to 720p. The best everyday space saver."
    ),
    MAXIMUM_SAVINGS(
        "Maximum savings", 60, 1280, 480, 1_200_000,
        "Photos up to 1280 px, videos up to 480p. Smallest files; quality loss is visible when zoomed in."
    )
}

data class MediaFileItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val mimeType: String,
    val dateAdded: Long = System.currentTimeMillis(), // last modified, in ms
    val dimensions: String = "",
    val durationSeconds: Int = 0,
    val sha256: String? = null,
    val reviewAction: ReviewAction = ReviewAction.NONE,
    val isExcluded: Boolean = false,
    val isSelectedForDelete: Boolean = false,
    val dateTaken: Long = 0L,
    val folder: String = "",            // e.g. "DCIM/Camera/"
    val isPicked: Boolean = false,      // added through the photo picker, read-only
    val isCompressedCopy: Boolean = false
) {
    /** Date the photo was taken when known, otherwise when the file was last modified. */
    val displayDate: Long get() = if (dateTaken > 0) dateTaken else dateAdded

    val formattedSize: String get() = StorageMetrics.formatBytes(sizeBytes)
    val formattedDate: String get() = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(displayDate))
    val formattedDuration: String get() {
        if (!isVideo || durationSeconds <= 0) return ""
        val min = durationSeconds / 60
        val sec = durationSeconds % 60
        return "%d:%02d".format(min, sec)
    }

    /** Copies kept in chat apps or downloads are worse "keepers" than camera originals. */
    val isSecondaryCopyLocation: Boolean get() {
        val f = folder.lowercase()
        return "whatsapp" in f || "telegram" in f || f.startsWith("download") || "/download/" in f
    }
}

/** Picks the copy to keep in a duplicate group: camera originals first, then the oldest. */
fun recommendKeeper(files: List<MediaFileItem>): MediaFileItem =
    files.minWith(compareBy<MediaFileItem>({ it.isSecondaryCopyLocation }, { it.displayDate }, { it.name.length }))

/** Folders worth reviewing that pile up without people noticing. */
enum class MediaCollection(val title: String, val description: String) {
    SCREENSHOTS("Screenshots", "Usually only needed for a moment"),
    SCREEN_RECORDINGS("Screen recordings", "Large videos that are easy to forget"),
    WHATSAPP("WhatsApp media", "Photos and videos received in chats"),
    TELEGRAM("Telegram media", "Photos and videos received in chats"),
    DOWNLOADS("Downloads", "Images and videos saved from the web or apps");

    fun matches(item: MediaFileItem): Boolean {
        val f = item.folder.lowercase()
        return when (this) {
            SCREENSHOTS -> !item.isVideo && "screenshot" in f
            SCREEN_RECORDINGS -> item.isVideo && ("screen record" in f || "screenrecord" in f || "screen_record" in f)
            WHATSAPP -> "whatsapp" in f
            TELEGRAM -> "telegram" in f
            DOWNLOADS -> f.startsWith("download") || "/download/" in f
        }
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

enum class CompressionStatus {
    SAVED,      // a smaller copy was saved to the gallery
    NO_GAIN,    // the file is already small, nothing saved
    SKIPPED,    // not a format that is safe to re-encode
    FAILED
}

data class CompressionJobResult(
    val source: MediaFileItem,
    val compressedSizeBytes: Long,
    val status: CompressionStatus,
    val message: String,
    val outputUri: Uri? = null
) {
    val sourceName: String get() = source.name
    val originalSizeBytes: Long get() = source.sizeBytes
    val isUsefulReduction: Boolean get() = status == CompressionStatus.SAVED
    val savedBytes: Long get() = if (isUsefulReduction) (originalSizeBytes - compressedSizeBytes).coerceAtLeast(0L) else 0L
    val reductionPercent: Int get() = if (originalSizeBytes > 0) ((savedBytes * 100) / originalSizeBytes).toInt() else 0
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
