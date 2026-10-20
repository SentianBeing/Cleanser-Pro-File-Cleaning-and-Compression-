package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** Per-file choices that must survive restarts. [id] is the MediaStore content URI. */
@Entity(tableName = "media_flags")
data class MediaFlagEntity(
    @PrimaryKey val id: String,
    val kept: Boolean = false,
    val compressedCopy: Boolean = false
)

/**
 * Cached duplicate-check hashes. A row is only reused while the file's size and
 * modification time still match, so edited files are always re-hashed.
 */
@Entity(tableName = "file_hashes")
data class FileHashEntity(
    @PrimaryKey val id: String,
    val sizeBytes: Long,
    val dateModified: Long,
    val partialHash: String,
    val fullHash: String?
)

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_flags")
    suspend fun getFlags(): List<MediaFlagEntity>

    @Query("SELECT * FROM media_flags WHERE id = :id")
    suspend fun getFlag(id: String): MediaFlagEntity?

    @Upsert
    suspend fun upsertFlags(flags: List<MediaFlagEntity>)

    @Query("DELETE FROM media_flags WHERE id IN (:ids)")
    suspend fun deleteFlags(ids: List<String>)

    @Query("SELECT * FROM file_hashes")
    suspend fun getHashes(): List<FileHashEntity>

    @Upsert
    suspend fun upsertHashes(hashes: List<FileHashEntity>)

    @Query("DELETE FROM file_hashes WHERE id IN (:ids)")
    suspend fun deleteHashes(ids: List<String>)
}
