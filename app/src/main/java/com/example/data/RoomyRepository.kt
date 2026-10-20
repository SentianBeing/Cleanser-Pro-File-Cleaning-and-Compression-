package com.example.data

import android.app.ActivityManager
import android.app.RecoverableSecurityException
import android.app.usage.StorageStatsManager
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.example.model.CompressionJobResult
import com.example.model.CompressionPreset
import com.example.model.DuplicateGroup
import com.example.model.JunkItem
import com.example.model.MediaFileItem
import com.example.model.RamStats
import com.example.model.StorageMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** What kind of system dialog a MediaStore request opens. */
enum class MediaRequestKind { TRASH, DELETE, RESTORE }

sealed interface DirectDeleteResult {
    data object Deleted : DirectDeleteResult
    data class NeedsConsent(val intentSender: IntentSender) : DirectDeleteResult
    data object Failed : DirectDeleteResult
}

class RoomyRepository(private val context: Context) {

    private val resolver = context.contentResolver
    private val database = AppDatabase.getInstance(context)
    private val mediaDao = database.mediaDao()
    private val historyDao = database.cleanHistoryDao()
    private val scanner = MediaScanner(context)
    private val duplicateFinder = DuplicateFinder(context, mediaDao)
    private val compressor = MediaCompressor(context)

    // Files added through the Android photo picker (read-only access, no permission needed)
    private val pickerMediaItems = mutableListOf<MediaFileItem>()

    val totalBytesCleaned: Flow<Long?> = historyDao.getTotalBytesFreed()

    fun getStorageMetrics(): StorageMetrics {
        return try {
            // StorageStatsManager reports the same totals as Android Settings (e.g. "128 GB")
            val (total, free) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val stats = context.getSystemService(StorageStatsManager::class.java)
                stats.getTotalBytes(StorageManager.UUID_DEFAULT) to stats.getFreeBytes(StorageManager.UUID_DEFAULT)
            } else {
                val statFs = StatFs(Environment.getDataDirectory().path)
                statFs.totalBytes to statFs.availableBytes
            }
            val used = (total - free).coerceAtLeast(0L)
            StorageMetrics(
                totalStorageBytes = total,
                freeStorageBytes = free,
                usedStorageBytes = used,
                usedStoragePercent = if (total > 0) ((used.toDouble() / total) * 100).toInt().coerceIn(0, 100) else 0
            )
        } catch (e: Exception) {
            StorageMetrics()
        }
    }

    fun getRamStats(): RamStats {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)

            val total = memInfo.totalMem
            val avail = memInfo.availMem
            val used = (total - avail).coerceAtLeast(0L)
            val percent = if (total > 0) {
                ((used.toDouble() / total) * 100).toInt().coerceIn(0, 100)
            } else 0

            RamStats(
                totalBytes = total,
                availBytes = avail,
                usedBytes = used,
                percentUsed = percent
            )
        } catch (e: Exception) {
            RamStats()
        }
    }

    // ------------------------------------------------------------------------
    // Saved choices (Keep, compressed copies)
    // ------------------------------------------------------------------------

    suspend fun loadFlags(): Pair<Set<String>, Set<String>> = withContext(Dispatchers.IO) {
        val flags = mediaDao.getFlags()
        flags.filter { it.kept }.mapTo(HashSet()) { it.id } to flags.filter { it.compressedCopy }.mapTo(HashSet()) { it.id }
    }

    suspend fun setKept(id: String, kept: Boolean) = withContext(Dispatchers.IO) {
        val current = mediaDao.getFlag(id) ?: MediaFlagEntity(id)
        mediaDao.upsertFlags(listOf(current.copy(kept = kept)))
    }

    suspend fun markCompressedCopies(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) mediaDao.upsertFlags(ids.map { MediaFlagEntity(it, compressedCopy = true) })
    }

    suspend fun forgetFiles(ids: List<String>) = withContext(Dispatchers.IO) {
        ids.chunked(500).forEach {
            mediaDao.deleteFlags(it)
            mediaDao.deleteHashes(it)
        }
    }

    // ------------------------------------------------------------------------
    // Scanning
    // ------------------------------------------------------------------------

    suspend fun scanMedia(keptIds: Set<String>, compressedCopyIds: Set<String>): List<MediaFileItem> =
        withContext(Dispatchers.IO) {
            val items = scanner.queryAll(keptIds, compressedCopyIds).toMutableList()
            val existing = items.mapTo(HashSet()) { it.id }
            synchronized(pickerMediaItems) {
                pickerMediaItems.filter { it.id !in existing }.forEach { items.add(it.copy(isExcluded = it.id in keptIds)) }
            }
            items.sortedByDescending { it.sizeBytes }
        }

    fun addPickerUris(uris: List<Uri>) {
        uris.forEach { uri ->
            try {
                var name = "Picked_${System.currentTimeMillis()}"
                var size = 0L
                val mime = resolver.getType(uri) ?: "image/jpeg"
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }
                val item = MediaFileItem(
                    id = uri.toString(),
                    uri = uri,
                    name = name,
                    path = name,
                    sizeBytes = size,
                    isVideo = mime.startsWith("video/"),
                    mimeType = mime,
                    dateAdded = System.currentTimeMillis(),
                    folder = "Picked files/",
                    isPicked = true
                )
                synchronized(pickerMediaItems) {
                    pickerMediaItems.removeAll { it.id == item.id }
                    pickerMediaItems.add(item)
                }
            } catch (_: Exception) {}
        }
    }

    /** Once full gallery access is granted, picked files would show up twice (picker + gallery), so drop them. */
    fun clearPickedItems() = synchronized(pickerMediaItems) { pickerMediaItems.clear() }

    suspend fun findDuplicates(media: List<MediaFileItem>, onProgress: (String) -> Unit): List<DuplicateGroup> =
        duplicateFinder.find(media, onProgress)

    // ------------------------------------------------------------------------
    // Deleting (Android's own confirmation dialog on Android 11+)
    // ------------------------------------------------------------------------

    fun createMediaRequest(uris: List<Uri>, kind: MediaRequestKind): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) return null
        return try {
            when (kind) {
                MediaRequestKind.TRASH -> MediaStore.createTrashRequest(resolver, uris, true)
                MediaRequestKind.RESTORE -> MediaStore.createTrashRequest(resolver, uris, false)
                MediaRequestKind.DELETE -> MediaStore.createDeleteRequest(resolver, uris)
            }.intentSender
        } catch (_: Exception) {
            null
        }
    }

    /** Android 10 and older: delete directly, or get the consent dialog Android 10 asks for. */
    suspend fun deleteDirect(uri: Uri): DirectDeleteResult = withContext(Dispatchers.IO) {
        try {
            if (resolver.delete(uri, null, null) > 0) DirectDeleteResult.Deleted else DirectDeleteResult.Failed
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                DirectDeleteResult.NeedsConsent(e.userAction.actionIntent.intentSender)
            } else {
                DirectDeleteResult.Failed
            }
        } catch (_: Exception) {
            DirectDeleteResult.Failed
        }
    }

    /** Returns the items that no longer exist in the gallery (deleted or moved to trash). */
    suspend fun filterGone(items: List<MediaFileItem>): List<MediaFileItem> = withContext(Dispatchers.IO) {
        items.filter { item ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Looking a file up by ID still returns it after it's trashed, so read the flag itself
                    val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }
                    resolver.query(item.uri, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_TRASHED), args, null)
                        ?.use { c -> !c.moveToFirst() || c.getInt(1) == 1 } ?: true
                } else {
                    resolver.query(item.uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                        ?.use { it.count == 0 } ?: true
                }
            } catch (_: Exception) {
                true
            }
        }
    }

    suspend fun recordCleanup(bytes: Long, count: Int, summary: String) = withContext(Dispatchers.IO) {
        if (count > 0) historyDao.insert(CleanHistoryEntity(bytesFreed = bytes, itemsRemoved = count, categorySummary = summary))
    }

    // ------------------------------------------------------------------------
    // Compression
    // ------------------------------------------------------------------------

    suspend fun compress(item: MediaFileItem, preset: CompressionPreset, onProgress: (Float) -> Unit): CompressionJobResult =
        compressor.compress(item, preset, onProgress)

    /** After the originals are gone, give the copies the original file names ("IMG_1.jpg" not "IMG_1 (1).jpg"). */
    suspend fun renameCopiesToOriginalNames(results: List<CompressionJobResult>) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext
        results.forEach { result ->
            val uri = result.outputUri ?: return@forEach
            val ext = when {
                result.source.isVideo -> "mp4"
                result.source.mimeType == "image/png" || result.source.mimeType == "image/webp" -> "webp"
                else -> "jpg"
            }
            val wanted = "${result.source.name.substringBeforeLast('.')}.$ext"
            try {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, wanted) }, null, null)
            } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------------------------------
    // Junk: leftovers this app can safely remove
    // ------------------------------------------------------------------------

    suspend fun scanJunkItems(includeActiveTempFiles: Boolean): List<JunkItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<JunkItem>()
        val staleBefore = System.currentTimeMillis() - 10 * 60 * 1000L

        // Temp files from compressions that were interrupted (e.g. the app was closed mid-way)
        File(context.cacheDir, MediaCompressor.TEMP_DIR_NAME).listFiles()?.forEach { f ->
            if (f.isFile && (includeActiveTempFiles || f.lastModified() < staleBefore)) {
                items += JunkItem(f.absolutePath, f.name, f.absolutePath, f.length(), "Leftover from an interrupted compression")
            }
        }

        // Copies saved privately by earlier versions of Cleanser (never visible in the gallery)
        File(context.filesDir, "roomy_compressed").walkTopDown().filter { it.isFile }.forEach { f ->
            items += JunkItem(f.absolutePath, f.name, f.absolutePath, f.length(), "Hidden copy from an older version of Cleanser")
        }

        listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { dir ->
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                val n = f.name.lowercase()
                if (n.endsWith(".tmp") || n.endsWith(".log") || n.endsWith(".bak")) {
                    items += JunkItem(f.absolutePath, f.name, f.absolutePath, f.length(), "App temporary file")
                }
            }
        }
        items.distinctBy { it.id }
    }

    suspend fun deleteJunk(items: List<JunkItem>): Long = withContext(Dispatchers.IO) {
        var total = 0L
        items.forEach {
            val f = File(it.path)
            if (f.exists()) {
                val len = f.length()
                if (f.delete()) total += len
            }
        }
        total
    }
}
