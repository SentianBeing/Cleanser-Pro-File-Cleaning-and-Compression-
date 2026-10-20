package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.model.CompressionJobResult
import com.example.model.CompressionPreset
import com.example.model.CompressionStatus
import com.example.model.MediaFileItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Makes smaller copies of photos and videos and saves them to the gallery next to
 * the original. Originals are never touched here; replacing them is a separate,
 * user-confirmed step.
 */
class MediaCompressor(private val context: Context) {

    private val resolver = context.contentResolver
    private val tempDir: File get() = File(context.cacheDir, TEMP_DIR_NAME).apply { mkdirs() }

    suspend fun compress(
        item: MediaFileItem,
        preset: CompressionPreset,
        onProgress: (Float) -> Unit
    ): CompressionJobResult = try {
        if (item.isVideo) compressVideo(item, preset, onProgress) else compressImage(item, preset)
    } catch (e: CancellationException) {
        throw e
    } catch (e: SkipException) {
        CompressionJobResult(item, item.sizeBytes, CompressionStatus.SKIPPED, e.message.orEmpty())
    } catch (e: OutOfMemoryError) {
        CompressionJobResult(item, item.sizeBytes, CompressionStatus.FAILED, "Not enough memory to process this file")
    } catch (e: Exception) {
        CompressionJobResult(item, item.sizeBytes, CompressionStatus.FAILED, e.localizedMessage ?: "Unknown error")
    }

    // ------------------------------------------------------------------------
    // Photos
    // ------------------------------------------------------------------------

    private suspend fun compressImage(item: MediaFileItem, preset: CompressionPreset): CompressionJobResult =
        withContext(Dispatchers.Default) {
            val mime = item.mimeType.lowercase()
            if (mime == "image/gif") throw SkipException("GIFs are skipped so animations aren't lost")

            // Screenshots and graphics keep transparency as WebP; camera photos stay JPEG with their metadata
            val asWebp = mime == "image/png" || mime == "image/webp"
            val bitmap = decodeScaled(item.uri, preset.maxImageSide) ?: throw IOException("Couldn't read this image")
            val ext = if (asWebp) "webp" else "jpg"
            val temp = File(tempDir, "cmp_${System.nanoTime()}.$ext")
            try {
                temp.outputStream().buffered().use { out ->
                    bitmap.compress(if (asWebp) webpFormat() else Bitmap.CompressFormat.JPEG, preset.quality, out)
                }
                bitmap.recycle()
                if (!asWebp) copyExif(item.uri, temp)
                publishIfSmaller(item, temp, if (asWebp) "image/webp" else "image/jpeg", ext)
            } finally {
                temp.delete()
            }
        }

    /** Decodes directly at the target size (no full-resolution bitmap) with EXIF rotation applied. */
    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            var animated = false
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                animated = info.isAnimated
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (w, h) = scaledSize(info.size.width, info.size.height, maxSide)
                if (w < info.size.width) decoder.setTargetSize(w, h)
            }
            if (animated) {
                bitmap.recycle()
                throw SkipException("Animated images are skipped so animations aren't lost")
            }
            return bitmap
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = readRotation(uri)
        val scale = min(1f, maxSide.toFloat() / max(decoded.width, decoded.height))
        if (rotation == 0 && scale >= 1f) return decoded
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private fun readRotation(uri: Uri): Int = try {
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) {
        0
    }

    /** Keeps date taken and camera details. Pixels are already upright, so orientation becomes "normal". */
    private fun copyExif(source: Uri, dest: File) {
        try {
            val from = resolver.openInputStream(source)?.use { ExifInterface(it) } ?: return
            val to = ExifInterface(dest.absolutePath)
            EXIF_TAGS_TO_KEEP.forEach { tag -> from.getAttribute(tag)?.let { to.setAttribute(tag, it) } }
            to.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            to.saveAttributes()
        } catch (_: Exception) {
            // Metadata is best effort; the image itself is already correct
        }
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    // ------------------------------------------------------------------------
    // Videos (hardware encoder via Media3 Transformer)
    // ------------------------------------------------------------------------

    private suspend fun compressVideo(
        item: MediaFileItem,
        preset: CompressionPreset,
        onProgress: (Float) -> Unit
    ): CompressionJobResult {
        val info = withContext(Dispatchers.IO) { readVideoInfo(item.uri) } ?: throw IOException("Couldn't read this video")
        val shortSide = min(info.width, info.height)
        val needsResize = shortSide > preset.maxVideoShortSide
        val scale = if (needsResize) preset.maxVideoShortSide.toFloat() / shortSide else 1f

        // The preset bitrate is for a full-size 16:9 frame at its max resolution; smaller frames get proportionally less
        val outPixels = info.width * scale * info.height * scale
        val presetPixels = preset.maxVideoShortSide.toFloat() * preset.maxVideoShortSide * 16f / 9f
        val qualityBitrate = (preset.videoBitrate * min(1f, outPixels / presetPixels)).toInt()

        // Phones often record at 2-3x the bitrate needed, so even videos at the right resolution can shrink
        // a lot. Leave them alone only when they're already close to a high-quality bitrate.
        if (!needsResize && (info.bitrate <= 0 || info.bitrate < qualityBitrate * 1.3)) {
            return CompressionJobResult(
                item, item.sizeBytes, CompressionStatus.NO_GAIN,
                "Already efficiently encoded at ${shortSide}p; shrinking it further would lower its quality"
            )
        }
        val bitrate = if (info.bitrate > 0) min(qualityBitrate, (info.bitrate * 0.7).toInt()) else qualityBitrate

        val temp = File(tempDir, "cmp_${System.nanoTime()}.mp4")
        try {
            transcode(item.uri, temp, scale, bitrate, onProgress)
            if (withContext(Dispatchers.IO) { readVideoInfo(Uri.fromFile(temp)) } == null) {
                throw IOException("The compressed video didn't pass verification")
            }
            return withContext(Dispatchers.IO) { publishIfSmaller(item, temp, "video/mp4", "mp4") }
        } finally {
            temp.delete()
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun transcode(
        input: Uri,
        output: File,
        scale: Float,
        bitrate: Int,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            lateinit var poll: Runnable
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                        .build()
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        handler.removeCallbacks(poll)
                        if (cont.isActive) cont.resumeWithException(IOException("Video encoder error: ${exportException.errorCodeName}", exportException))
                    }
                })
                .build()

            // Without a resize, the requested bitrate alone makes Transformer re-encode instead of copying the stream
            val videoEffects = if (scale < 1f) listOf(ScaleAndRotateTransformation.Builder().setScale(scale, scale).build()) else emptyList()
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(input))
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
            transformer.start(edited, output.absolutePath)

            val holder = ProgressHolder()
            poll = Runnable {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                handler.postDelayed(poll, 300)
            }
            handler.post(poll)
            cont.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacks(poll)
                    transformer.cancel()
                }
            }
        }
    }

    private class VideoInfo(val width: Int, val height: Int, val bitrate: Int)

    private fun readVideoInfo(uri: Uri): VideoInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            if (w > 0 && h > 0) VideoInfo(w, h, bitrate) else null
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------------------------------
    // Saving the copy to the gallery
    // ------------------------------------------------------------------------

    private suspend fun publishIfSmaller(item: MediaFileItem, temp: File, mime: String, ext: String): CompressionJobResult {
        val newSize = temp.length()
        val saved = item.sizeBytes - newSize
        if (newSize <= 0 || item.sizeBytes <= 0 || saved < max(MIN_SAVING_BYTES, item.sizeBytes / 10)) {
            return CompressionJobResult(item, newSize, CompressionStatus.NO_GAIN, "Already well compressed; nothing worth saving")
        }
        // Once encoding is done, finish writing the copy even if the user cancels, so nothing is left half-written
        val uri = withContext(NonCancellable) { publish(item, temp, mime, ext) }
        val percent = (saved * 100 / item.sizeBytes).toInt()
        return CompressionJobResult(item, newSize, CompressionStatus.SAVED, "Saved a $percent% smaller copy", uri)
    }

    private suspend fun publish(item: MediaFileItem, temp: File, mime: String, ext: String): Uri {
        val displayName = "${item.name.substringBeforeLast('.')}.$ext"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (item.isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, targetFolder(item))
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (item.dateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTaken)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create the compressed copy")
            try {
                resolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out, COPY_BUFFER) } }
                    ?: throw IOException("Couldn't write the compressed copy")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri
        }

        // Android 9 and older: write next to the other media and let the media scanner index it
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStoragePublicDirectory(
            if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        )
        val dir = File(root, "Cleanser").apply { mkdirs() }
        var dest = File(dir, displayName)
        var n = 1
        while (dest.exists()) dest = File(dir, "${displayName.substringBeforeLast('.')} ($n).$ext").also { n++ }
        temp.copyTo(dest)
        return suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime)) { _, uri ->
                if (uri != null) cont.resume(uri) else cont.resumeWithException(IOException("Couldn't add the copy to the gallery"))
            }
        }
    }

    /** Same album as the original when Android allows it there, otherwise Pictures/Cleanser or Movies/Cleanser. */
    private fun targetFolder(item: MediaFileItem): String {
        val allowed = if (item.isVideo) listOf("DCIM/", "Movies/", "Pictures/") else listOf("DCIM/", "Pictures/")
        return if (allowed.any { item.folder.startsWith(it, ignoreCase = true) }) {
            item.folder
        } else if (item.isVideo) "Movies/Cleanser/" else "Pictures/Cleanser/"
    }

    private class SkipException(message: String) : Exception(message)

    companion object {
        const val TEMP_DIR_NAME = "compress_tmp"
        private const val MIN_SAVING_BYTES = 20L * 1000L
        private const val COPY_BUFFER = 256 * 1024
        private val EXIF_TAGS_TO_KEEP = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_TIMESTAMP
        )
    }
}

/** Scales (width, height) so the longer side is at most [maxSide], keeping the aspect ratio. */
fun scaledSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
    val longSide = max(width, height)
    if (longSide <= maxSide || longSide <= 0) return width to height
    val factor = maxSide.toDouble() / longSide
    return (width * factor).roundToInt().coerceAtLeast(1) to (height * factor).roundToInt().coerceAtLeast(1)
}
