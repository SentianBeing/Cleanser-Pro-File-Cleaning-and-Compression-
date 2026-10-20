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
        /** 1000-based units, the same way Android's Settings app shows storage. */
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            if (bytes < 1000) return "$bytes B"
            val units = arrayOf("KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var index = -1
            while (value >= 1000 && index < units.size - 1) {
                value /= 1000
                index++
            }
            return String.format(Locale.US, "%.1f %s", value, units[index])
        }
    }
}
