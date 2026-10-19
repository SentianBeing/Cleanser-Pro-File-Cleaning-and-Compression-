package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.StorageMetrics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "clean_history")
data class CleanHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val bytesFreed: Long,
    val itemsRemoved: Int,
    val categorySummary: String
) {
    val formattedBytes: String get() = StorageMetrics.formatBytes(bytesFreed)
    val formattedDate: String get() {
        val sdf = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
