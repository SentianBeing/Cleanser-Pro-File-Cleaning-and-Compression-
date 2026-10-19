package com.example.model

import java.util.Locale

data class StorageMetrics(
    val totalStorageBytes: Long = 0L,
    val freeStorageBytes: Long = 0L,
    val usedStorageBytes: Long = 0L,
    val usedStoragePercent: Int = 0,
    val totalRamBytes: Long = 0L,
    val availRamBytes: Long = 0L,
    val usedRamBytes: Long = 0L,
    val usedRamPercent: Int = 0,
    val isLowMemory: Boolean = false
) {
    val totalStorageFormatted: String get() = formatBytes(totalStorageBytes)
    val freeStorageFormatted: String get() = formatBytes(freeStorageBytes)
    val usedStorageFormatted: String get() = formatBytes(usedStorageBytes)

    val totalRamFormatted: String get() = formatBytes(totalRamBytes)
    val availRamFormatted: String get() = formatBytes(availRamBytes)
    val usedRamFormatted: String get() = formatBytes(usedRamBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val index = digitGroups.coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, index.toDouble())
            return if (index == 0) {
                "$bytes B"
            } else {
                String.format(Locale.US, "%.1f %s", value, units[index])
            }
        }
    }
}
