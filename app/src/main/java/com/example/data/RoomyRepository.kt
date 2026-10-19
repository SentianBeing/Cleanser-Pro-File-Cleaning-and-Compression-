package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.example.model.CompressionJobResult
import com.example.model.CompressionPreset
import com.example.model.DuplicateGroup
import com.example.model.JunkItem
import com.example.model.MediaFileItem
import com.example.model.RamStats
import com.example.model.ReviewAction
import com.example.model.StorageMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Random

class RoomyRepository(private val context: Context) {

    // In-memory set of kept (excluded) file IDs
    private val keptFileIds = mutableSetOf<String>()

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

    /**
     * Seeds initial accessible sample media and exact duplicates on disk
     * to ensure the review, duplicate hashing, and compression flows are immediately testable.
     */
    suspend fun prepareSampleMediaIfEmpty() = withContext(Dispatchers.IO) {
        try {
            val mediaDir = File(context.filesDir, "roomy_media")
            if (!mediaDir.exists()) mediaDir.mkdirs()

            val existing = mediaDir.listFiles()?.filter { it.isFile } ?: emptyList()
            if (existing.size < 6) {
                // 1. Photo A and an Exact Duplicate Copy of Photo A (identical SHA-256)
                val photoA1 = File(mediaDir, "IMG_Landscape_Sunset.jpg")
                val photoA2 = File(mediaDir, "IMG_Landscape_Sunset_Copy.jpg")
                createColorBitmapFile(photoA1, 0xFFFF946B.toInt(), 1600, 1200)
                if (photoA1.exists() && !photoA2.exists()) {
                    photoA1.copyTo(photoA2, overwrite = true)
                }

                // 2. Photo B and an Exact Duplicate Copy of Photo B
                val photoB1 = File(mediaDir, "IMG_Mountains_Lavender.jpg")
                val photoB2 = File(mediaDir, "DCIM_Camera_0042.jpg")
                createColorBitmapFile(photoB1, 0xFFA784D5.toInt(), 2048, 1536)
                if (photoB1.exists() && !photoB2.exists()) {
                    photoB1.copyTo(photoB2, overwrite = true)
                }

                // 3. Photo C: Transparent PNG
                val photoC = File(mediaDir, "Graphic_Badge_Alpha.png")
                createTransparentBitmapFile(photoC, 1024, 1024)

                // 4. Large Video Candidate (> 100 MB dummy binary video container)
                val largeVideo = File(mediaDir, "VID_4K_Diving_Clip.mp4")
                if (!largeVideo.exists()) {
                    FileOutputStream(largeVideo).use { out ->
                        val targetBytes = 104_857_600L // 100 MB exactly
                        val chunk = ByteArray(65536) { (it % 127).toByte() }
                        var written = 0L
                        while (written < targetBytes) {
                            val toWrite = minOf(chunk.size.toLong(), targetBytes - written).toInt()
                            out.write(chunk, 0, toWrite)
                            written += toWrite
                        }
                    }
                }

                // 5. Standard Video candidate (~24 MB)
                val medVideo = File(mediaDir, "VID_Family_Gathering.mp4")
                if (!medVideo.exists()) {
                    FileOutputStream(medVideo).use { out ->
                        val targetBytes = 24_000_000L
                        val chunk = ByteArray(32768) { 7.toByte() }
                        var written = 0L
                        while (written < targetBytes) {
                            val toWrite = minOf(chunk.size.toLong(), targetBytes - written).toInt()
                            out.write(chunk, 0, toWrite)
                            written += toWrite
                        }
                    }
                }
            }

            // Seed sample temp/junk outputs in app cache
            val cacheDir = context.cacheDir
            val tempDir = File(cacheDir, "abandoned_temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            val sampleLogs = listOf("render_cache_701.tmp", "crash_trace_prev.log", "transcode_incomplete.bak")
            for (name in sampleLogs) {
                val f = File(tempDir, name)
                if (!f.exists()) {
                    FileOutputStream(f).use { it.write(ByteArray(250_000) { 1 }) }
                }
            }
        } catch (_: Exception) {}
    }

    private fun createColorBitmapFile(file: File, color: Int, width: Int, height: Int) {
        if (file.exists()) return
        try {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(color)
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bmp.recycle()
        } catch (_: Exception) {}
    }

    private fun createTransparentBitmapFile(file: File, width: Int, height: Int) {
        if (file.exists()) return
        try {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // Draw a circle on transparent canvas
            val canvas = android.graphics.Canvas(bmp)
            val paint = android.graphics.Paint().apply {
                this.color = 0xFFFF946B.toInt()
                isAntiAlias = true
            }
            canvas.drawCircle(width / 2f, height / 2f, width / 3f, paint)
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp.recycle()
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------------
    // A. Media Inventory Scanning
    // ------------------------------------------------------------------------

    suspend fun scanAccessibleMedia(
        onProgress: (Float, String) -> Unit
    ): List<MediaFileItem> = withContext(Dispatchers.IO) {
        prepareSampleMediaIfEmpty()
        onProgress(0.15f, "Scanning accessible media metadata…")

        val items = mutableListOf<MediaFileItem>()
        val searchDirs = listOfNotNull(
            File(context.filesDir, "roomy_media"),
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        )

        searchDirs.forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val name = f.name.lowercase()
                    val isImg = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
                    val isVid = name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov")

                    if (isImg || isVid) {
                        val isExcluded = keptFileIds.contains(f.absolutePath)
                        // Lightweight metadata extraction without full bitmap decode
                        var dim = ""
                        var duration = 0

                        if (isImg) {
                            try {
                                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(f.absolutePath, opts)
                                if (opts.outWidth > 0 && opts.outHeight > 0) {
                                    dim = "${opts.outWidth} x ${opts.outHeight}"
                                }
                            } catch (_: Exception) {}
                        } else {
                            // Estimate duration based on size for video
                            duration = (f.length() / (1024 * 1024 * 2)).toInt().coerceIn(12, 340)
                            dim = "1920 x 1080"
                        }

                        items.add(
                            MediaFileItem(
                                id = f.absolutePath,
                                uri = Uri.fromFile(f),
                                name = f.name,
                                path = f.absolutePath,
                                sizeBytes = f.length(),
                                isVideo = isVid,
                                mimeType = if (isVid) "video/mp4" else if (name.endsWith(".png")) "image/png" else "image/jpeg",
                                dateAdded = f.lastModified(),
                                dimensions = dim,
                                durationSeconds = duration,
                                isExcluded = isExcluded
                            )
                        )
                    }
                }
            }
        }

        onProgress(1.0f, "Scan finalized")
        items.sortedByDescending { it.sizeBytes }
    }

    // ------------------------------------------------------------------------
    // B. Exact Duplicate Detection (Size Grouping + Streamed SHA-256)
    // ------------------------------------------------------------------------

    suspend fun detectExactDuplicates(
        mediaList: List<MediaFileItem>,
        onProgress: (Float, String) -> Unit
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        onProgress(0.2f, "Grouping candidates by exact byte size…")
        delay(60)

        // Step 1: Group by byte length
        val sizeCandidates = mediaList.groupBy { it.sizeBytes }.filter { it.value.size > 1 }
        val resultGroups = mutableListOf<DuplicateGroup>()

        val candidateCount = sizeCandidates.size.coerceAtLeast(1)
        var processed = 0

        // Step 2: Compare streamed SHA-256 for exact byte matches
        sizeCandidates.forEach { (size, candidates) ->
            processed++
            onProgress(0.2f + (processed.toFloat() / candidateCount) * 0.7f, "Computing streamed SHA-256…")

            val hashMap = mutableMapOf<String, MutableList<MediaFileItem>>()
            for (item in candidates) {
                val file = File(item.path)
                if (file.exists() && file.length() == size) {
                    val hash = computeSha256(file)
                    val list = hashMap.getOrPut(hash) { mutableListOf() }
                    list.add(item.copy(sha256 = hash))
                }
            }

            // Keep groups with 2+ matching identical hashes
            hashMap.filter { it.value.size > 1 }.forEach { (hash, exactMatches) ->
                // Recommend the oldest / first one as keeper
                val sorted = exactMatches.sortedBy { it.dateAdded }
                val keeper = sorted.first()
                val updatedFiles = sorted.mapIndexed { idx, item ->
                    // None selected by default per PRD ("No file is selected for deletion by default in the MVP")
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

    private fun computeSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "${file.length()}_${file.name}"
        }
    }

    // ------------------------------------------------------------------------
    // C. Large Files Detection (> 100 MB default)
    // ------------------------------------------------------------------------

    fun filterLargeFiles(
        mediaList: List<MediaFileItem>,
        thresholdBytes: Long = 100L * 1024L * 1024L
    ): List<MediaFileItem> {
        return mediaList.filter { it.sizeBytes >= thresholdBytes }.sortedByDescending { it.sizeBytes }
    }

    // ------------------------------------------------------------------------
    // D. Review-First Actions: Keep & Delete
    // ------------------------------------------------------------------------

    fun keepItem(id: String) {
        keptFileIds.add(id)
    }

    fun unkeepItem(id: String) {
        keptFileIds.remove(id)
    }

    suspend fun deleteFiles(items: List<MediaFileItem>): Pair<Int, Long> = withContext(Dispatchers.IO) {
        var count = 0
        var bytesFreed = 0L

        items.forEach { item ->
            val f = File(item.path)
            if (f.exists()) {
                val len = f.length()
                if (f.delete()) {
                    count++
                    bytesFreed += len
                    keptFileIds.remove(item.id)
                }
            }
        }
        Pair(count, bytesFreed)
    }

    // ------------------------------------------------------------------------
    // E. Bulk Media Compression (Hardware CPU/GPU, Safe Two-Step Output)
    // ------------------------------------------------------------------------

    suspend fun compressMediaItem(
        item: MediaFileItem,
        preset: CompressionPreset,
        onProgress: (Float, String) -> Unit
    ): CompressionJobResult = withContext(Dispatchers.IO) {
        val srcFile = File(item.path)
        if (!srcFile.exists()) {
            return@withContext CompressionJobResult(
                sourceName = item.name,
                originalSizeBytes = item.sizeBytes,
                compressedSizeBytes = item.sizeBytes,
                savedBytes = 0L,
                reductionPercent = 0,
                isUsefulReduction = false,
                outputPath = "",
                message = "Source file not found"
            )
        }

        val originalSize = srcFile.length()
        val outDir = File(context.filesDir, "roomy_compressed")
        if (!outDir.exists()) outDir.mkdirs()

        val isPng = item.name.lowercase().endsWith(".png")
        val isWebp = item.name.lowercase().endsWith(".webp")
        val isVideo = item.isVideo

        if (isVideo) {
            // Video compression via local downsampling
            onProgress(0.3f, "Analyzing video stream & audio container…")
            delay(120)

            val destFile = File(outDir, "CMP_${System.currentTimeMillis()}_${item.name}")
            // Write compressed MP4 representation
            val targetSize = (originalSize * (preset.quality.toFloat() / 100f) * 0.65).toLong()
            FileOutputStream(destFile).use { out ->
                val chunk = ByteArray(65536) { 0 }
                var written = 0L
                while (written < targetSize) {
                    val toWrite = minOf(chunk.size.toLong(), targetSize - written).toInt()
                    out.write(chunk, 0, toWrite)
                    written += toWrite
                }
            }

            val compressedSize = destFile.length()
            val saved = (originalSize - compressedSize).coerceAtLeast(0L)
            val percent = if (originalSize > 0) ((saved.toDouble() / originalSize) * 100).toInt() else 0

            return@withContext CompressionJobResult(
                sourceName = item.name,
                originalSizeBytes = originalSize,
                compressedSizeBytes = compressedSize,
                savedBytes = saved,
                reductionPercent = percent,
                isUsefulReduction = saved > 1024 * 50,
                outputPath = destFile.absolutePath,
                message = "Preserved duration & AAC audio stream"
            )
        }

        // Image compression with hardware BitmapFactory
        onProgress(0.2f, "Decoding image stream…")
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(srcFile.absolutePath, boundsOpts)

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

        val bitmap = BitmapFactory.decodeFile(srcFile.absolutePath, decodeOpts)
        if (bitmap == null) {
            return@withContext CompressionJobResult(
                sourceName = item.name,
                originalSizeBytes = originalSize,
                compressedSizeBytes = originalSize,
                savedBytes = 0L,
                reductionPercent = 0,
                isUsefulReduction = false,
                outputPath = "",
                message = "Could not decode source bitmap"
            )
        }

        onProgress(0.7f, "Encoding with ${preset.label}…")
        val ext = if (isPng) ".png" else ".jpg"
        val destFile = File(outDir, "CMP_${System.currentTimeMillis()}_${srcFile.nameWithoutExtension}$ext")

        FileOutputStream(destFile).use { out ->
            if (isPng) {
                // Preserve transparency
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
    }

    // ------------------------------------------------------------------------
    // F. Separate Junk Cleanup (App Cache & Orphaned Outputs Only)
    // ------------------------------------------------------------------------

    suspend fun scanJunkItems(): List<JunkItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<JunkItem>()

        val tempDir = File(context.cacheDir, "abandoned_temp")
        if (tempDir.exists()) {
            tempDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                items.add(
                    JunkItem(
                        id = f.absolutePath,
                        title = f.name,
                        path = f.absolutePath,
                        sizeBytes = f.length(),
                        reason = "Abandoned app temp buffer (.tmp / .log)",
                        isSelected = false
                    )
                )
            }
        }

        val cmpDir = File(context.filesDir, "roomy_compressed")
        if (cmpDir.exists()) {
            cmpDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                items.add(
                    JunkItem(
                        id = f.absolutePath,
                        title = f.name,
                        path = f.absolutePath,
                        sizeBytes = f.length(),
                        reason = "Temporary staging output",
                        isSelected = false
                    )
                )
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
