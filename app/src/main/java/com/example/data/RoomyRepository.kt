package com.example.data

import android.app.ActivityManager
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.example.model.CompressionJobResult
import com.example.model.CompressionPreset
import com.example.model.DuplicateGroup
import com.example.model.JunkItem
import com.example.model.MediaFileItem
import com.example.model.RamStats
import com.example.model.StorageMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class RoomyRepository(private val context: Context) {

    // Persistent in-memory set of kept (excluded) file IDs
    private val keptFileIds = mutableSetOf<String>()

    // Additional media URIs explicitly added via Android Photo Picker
    private val pickerMediaItems = mutableListOf<MediaFileItem>()

    fun getStorageMetrics(): StorageMetrics {
        return try {
            val dataDir = Environment.getDataDirectory()
            val statFs = StatFs(dataDir.path)
            val blockSize = statFs.blockSizeLong
            val totalBlocks = statFs.blockCountLong
            val availBlocks = statFs.availableBlocksLong

            val totalStorageBytes = totalBlocks * blockSize
            val freeStorageBytes = availBlocks * blockSize
            val usedStorageBytes = (totalStorageBytes - freeStorageBytes).coerceAtLeast(0L)
            val usedStoragePercent = if (totalStorageBytes > 0) {
                ((usedStorageBytes.toDouble() / totalStorageBytes) * 100).toInt().coerceIn(0, 100)
            } else 0

            StorageMetrics(
                totalStorageBytes = totalStorageBytes,
                freeStorageBytes = freeStorageBytes,
                usedStorageBytes = usedStorageBytes,
                usedStoragePercent = usedStoragePercent
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
    // Real Android MediaStore Queries via ContentResolver (Zero Dummy Data)
    // ------------------------------------------------------------------------

    suspend fun scanRealMedia(
        onProgress: (Float, String) -> Unit
    ): List<MediaFileItem> = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Querying Android MediaStore for photos…")
        val items = mutableListOf<MediaFileItem>()

        // 1. Query Real Device Photos
        try {
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)

                if (idCol != -1) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = if (nameCol != -1) cursor.getString(nameCol) ?: "Image_$id" else "Image_$id"
                        val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                        val dateModified = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                        val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                        val w = if (widthCol != -1) cursor.getInt(widthCol) else 0
                        val h = if (heightCol != -1) cursor.getInt(heightCol) else 0

                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        val dimStr = if (w > 0 && h > 0) "$w x $h" else ""

                        items.add(
                            MediaFileItem(
                                id = uri.toString(),
                                uri = uri,
                                name = name,
                                path = uri.toString(),
                                sizeBytes = size,
                                isVideo = false,
                                mimeType = mime,
                                dateAdded = dateModified,
                                dimensions = dimStr,
                                durationSeconds = 0,
                                isExcluded = keptFileIds.contains(uri.toString())
                            )
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted or partial
        } catch (_: Exception) {}

        onProgress(0.5f, "Querying Android MediaStore for videos…")

        // 2. Query Real Device Videos
        try {
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            )

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                if (idCol != -1) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = if (nameCol != -1) cursor.getString(nameCol) ?: "Video_$id" else "Video_$id"
                        val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                        val dateModified = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else System.currentTimeMillis()
                        val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                        val durationMs = if (durCol != -1) cursor.getLong(durCol) else 0L
                        val w = if (widthCol != -1) cursor.getInt(widthCol) else 0
                        val h = if (heightCol != -1) cursor.getInt(heightCol) else 0

                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        val dimStr = if (w > 0 && h > 0) "$w x $h" else ""

                        items.add(
                            MediaFileItem(
                                id = uri.toString(),
                                uri = uri,
                                name = name,
                                path = uri.toString(),
                                sizeBytes = size,
                                isVideo = true,
                                mimeType = mime,
                                dateAdded = dateModified,
                                dimensions = dimStr,
                                durationSeconds = (durationMs / 1000).toInt(),
                                isExcluded = keptFileIds.contains(uri.toString())
                            )
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted or partial
        } catch (_: Exception) {}

        // 3. Append any Photo Picker selected items (fallback / zero-permission selections)
        synchronized(pickerMediaItems) {
            val existingIds = items.map { it.id }.toSet()
            pickerMediaItems.forEach { pickerItem ->
                if (!existingIds.contains(pickerItem.id)) {
                    items.add(pickerItem.copy(isExcluded = keptFileIds.contains(pickerItem.id)))
                }
            }
        }

        onProgress(1.0f, "Scan finalized (${items.size} files found)")
        items.sortedByDescending { it.sizeBytes }
    }

    /**
     * Adds files picked via Android Photo Picker to the active scan inventory
     */
    fun addPickerUris(uris: List<Uri>) {
        uris.forEach { uri ->
            try {
                var name = "Picked_${System.currentTimeMillis()}"
                var size = 0L
                var mime = context.contentResolver.getType(uri) ?: "image/jpeg"

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }

                val isVid = mime.startsWith("video/")
                val item = MediaFileItem(
                    id = uri.toString(),
                    uri = uri,
                    name = name,
                    path = uri.toString(),
                    sizeBytes = size,
                    isVideo = isVid,
                    mimeType = mime,
                    dateAdded = System.currentTimeMillis()
                )

                synchronized(pickerMediaItems) {
                    pickerMediaItems.removeAll { it.id == uri.toString() }
                    pickerMediaItems.add(item)
                }
            } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------------------------------
    // Exact Duplicate Detection (Size Grouping + Streamed SHA-256)
    // ------------------------------------------------------------------------

    suspend fun detectExactDuplicates(
        mediaList: List<MediaFileItem>,
        onProgress: (Float, String) -> Unit
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        onProgress(0.2f, "Grouping candidates by exact byte size…")

        // Only compare files that have a valid size (> 0)
        val sizeCandidates = mediaList.filter { it.sizeBytes > 0 }.groupBy { it.sizeBytes }.filter { it.value.size > 1 }
        val resultGroups = mutableListOf<DuplicateGroup>()

        val candidateCount = sizeCandidates.size.coerceAtLeast(1)
        var processed = 0

        sizeCandidates.forEach { (size, candidates) ->
            processed++
            onProgress(0.2f + (processed.toFloat() / candidateCount) * 0.7f, "Computing streamed SHA-256…")

            val hashMap = mutableMapOf<String, MutableList<MediaFileItem>>()
            for (item in candidates) {
                val hash = computeSha256(item.uri)
                if (hash.isNotEmpty()) {
                    val list = hashMap.getOrPut(hash) { mutableListOf() }
                    list.add(item.copy(sha256 = hash))
                }
            }

            hashMap.filter { it.value.size > 1 }.forEach { (hash, exactMatches) ->
                val sorted = exactMatches.sortedBy { it.dateAdded }
                val keeper = sorted.first()
                val updatedFiles = sorted.map { item ->
                    item.copy(isSelectedForDelete = false)
                }

                resultGroups.add(
                    DuplicateGroup(
                        checksum = hash,
                        byteSize = size,
                        isVideo = exactMatches.first().isVideo,
                        files = updatedFiles,
                        recommendedKeeperId = keeper.id
                    )
                )
            }
        }

        onProgress(1.0f, "Duplicate detection complete")
        resultGroups
    }

    private fun computeSha256(uri: Uri): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            } ?: return ""
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    // ------------------------------------------------------------------------
    // Review-First Actions: Keep & Deletion
    // ------------------------------------------------------------------------

    fun keepItem(id: String) {
        keptFileIds.add(id)
    }

    fun unkeepItem(id: String) {
        keptFileIds.remove(id)
    }

    /**
     * Builds official Android MediaStore system deletion consent request on API 30+
     * Returns an IntentSender for the system dialog ("Allow Roomy to delete X photos?")
     */
    fun createSystemDeleteIntent(uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val deleteRequest = MediaStore.createDeleteRequest(context.contentResolver, uris)
                return deleteRequest.intentSender
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Deletes items directly where allowed (app-owned files or granted URIs)
     */
    suspend fun deleteDirect(uris: List<Uri>): Pair<Int, Long> = withContext(Dispatchers.IO) {
        var count = 0
        var bytes = 0L

        uris.forEach { uri ->
            try {
                if (uri.scheme == "file") {
                    val f = File(uri.path ?: "")
                    if (f.exists()) {
                        val len = f.length()
                        if (f.delete()) {
                            count++
                            bytes += len
                        }
                    }
                } else {
                    val deleted = context.contentResolver.delete(uri, null, null)
                    if (deleted > 0) count++
                }
            } catch (_: Exception) {}
        }

        synchronized(pickerMediaItems) {
            val uriStrings = uris.map { it.toString() }.toSet()
            pickerMediaItems.removeAll { uriStrings.contains(it.id) }
        }

        Pair(count, bytes)
    }

    // ------------------------------------------------------------------------
    // Real Local Media Compression (CPU & GPU)
    // ------------------------------------------------------------------------

    suspend fun compressMediaItem(
        item: MediaFileItem,
        preset: CompressionPreset,
        onProgress: (Float, String) -> Unit
    ): CompressionJobResult = withContext(Dispatchers.IO) {
        val originalSize = item.sizeBytes
        val outDir = File(context.filesDir, "roomy_compressed")
        if (!outDir.exists()) outDir.mkdirs()

        onProgress(0.2f, "Reading source media via ContentResolver…")

        val isPng = item.mimeType == "image/png" || item.name.lowercase().endsWith(".png")

        try {
            // First decode bounds
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOpts)
            }

            var sampleSize = 1
            if (preset == CompressionPreset.BALANCED) {
                val maxDim = 1920
                if (boundsOpts.outHeight > maxDim || boundsOpts.outWidth > maxDim) {
                    val halfH = boundsOpts.outHeight / 2
                    val halfW = boundsOpts.outWidth / 2
                    while (halfH / sampleSize >= maxDim && halfW / sampleSize >= maxDim) {
                        sampleSize *= 2
                    }
                }
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            onProgress(0.5f, "Decoding bitmap frames…")
            val bitmap = context.contentResolver.openInputStream(item.uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOpts)
            }

            if (bitmap == null) {
                return@withContext CompressionJobResult(
                    sourceName = item.name,
                    originalSizeBytes = originalSize,
                    compressedSizeBytes = originalSize,
                    savedBytes = 0L,
                    reductionPercent = 0,
                    isUsefulReduction = false,
                    outputPath = "",
                    message = "Could not decode source bitmap stream"
                )
            }

            onProgress(0.8f, "Encoding hardware output with ${preset.label}…")
            val ext = if (isPng) ".png" else ".jpg"
            val destFile = File(outDir, "CMP_${System.currentTimeMillis()}_${item.name.substringBeforeLast('.')}$ext")

            FileOutputStream(destFile).use { out ->
                if (isPng) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, preset.quality, out)
                }
            }
            bitmap.recycle()

            val compressedSize = destFile.length()
            val saved = (originalSize - compressedSize).coerceAtLeast(0L)
            val percent = if (originalSize > 0) ((saved.toDouble() / originalSize) * 100).toInt() else 0
            val isUseful = saved > 1024 * 10 // At least 10KB reduction

            if (!isUseful) {
                destFile.delete() // Clean temporary output if no reduction
            }

            CompressionJobResult(
                sourceName = item.name,
                originalSizeBytes = originalSize,
                compressedSizeBytes = if (isUseful) compressedSize else originalSize,
                savedBytes = saved,
                reductionPercent = percent,
                isUsefulReduction = isUseful,
                outputPath = if (isUseful) destFile.absolutePath else "",
                message = if (isUseful) "Reduced by $percent% on device" else "No useful reduction"
            )
        } catch (e: Exception) {
            CompressionJobResult(
                sourceName = item.name,
                originalSizeBytes = originalSize,
                compressedSizeBytes = originalSize,
                savedBytes = 0L,
                reductionPercent = 0,
                isUsefulReduction = false,
                outputPath = "",
                message = "Encoding failed: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    // ------------------------------------------------------------------------
    // Real App-Owned Cache & Temporary Outputs
    // ------------------------------------------------------------------------

    suspend fun scanJunkItems(): List<JunkItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<JunkItem>()

        val cacheDirs = listOfNotNull(context.cacheDir, context.externalCacheDir)
        cacheDirs.forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val n = f.name.lowercase()
                    if (n.endsWith(".tmp") || n.endsWith(".log") || n.endsWith(".bak") || n.startsWith("cmp_")) {
                        items.add(
                            JunkItem(
                                id = f.absolutePath,
                                title = f.name,
                                path = f.absolutePath,
                                sizeBytes = f.length(),
                                reason = "App temporary buffer or encoding staging cache",
                                isSelected = false
                            )
                        )
                    }
                }
            }
        }

        items
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
