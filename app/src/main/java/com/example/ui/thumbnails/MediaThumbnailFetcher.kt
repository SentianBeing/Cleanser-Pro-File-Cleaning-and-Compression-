package com.example.ui.thumbnails

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.pxOrElse
import java.io.IOException
import kotlin.math.max

/** Coil model for a gallery thumbnail. Use it instead of the raw Uri for grid and list cells. */
data class MediaThumb(val uri: Uri, val isVideo: Boolean)

class MediaThumbKeyer : Keyer<MediaThumb> {
    override fun key(data: MediaThumb, options: Options): String = "thumb:${data.uri}"
}

/**
 * Loads thumbnails the way the Gallery app does: from Android's thumbnail cache
 * (ContentResolver.loadThumbnail, Android 10+), which is far faster than decoding
 * the original file and also works for videos. Older Android versions fall back to
 * a downsampled decode or a video frame.
 */
class MediaThumbnailFetcher(
    private val data: MediaThumb,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val width = options.size.width.pxOrElse { DEFAULT_SIZE }.coerceIn(MIN_SIZE, MAX_SIZE)
        val height = options.size.height.pxOrElse { DEFAULT_SIZE }.coerceIn(MIN_SIZE, MAX_SIZE)
        val bitmap = systemThumbnail(width, height)
            ?: if (data.isVideo) videoFrame(width, height) else sampledImage(width, height)
            ?: throw IOException("No thumbnail available for ${data.uri}")
        return DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    private fun systemThumbnail(width: Int, height: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            options.context.contentResolver.loadThumbnail(data.uri, Size(width, height), null)
        } catch (_: Exception) {
            null
        }
    }

    private fun videoFrame(width: Int, height: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(options.context, data.uri)
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(FRAME_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height)
            } else {
                retriever.getFrameAtTime(FRAME_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            frame ?: retriever.frameAtTime
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun sampledImage(width: Int, height: Int): Bitmap? {
        val resolver = options.context.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(data.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return null
            var sample = 1
            val target = max(width, height)
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
            val bitmap = resolver.openInputStream(data.uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val rotation = resolver.openInputStream(data.uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (rotation == 0f) bitmap else Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true
            )
        } catch (_: Exception) {
            null
        }
    }

    class Factory : Fetcher.Factory<MediaThumb> {
        override fun create(data: MediaThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbnailFetcher(data, options)
    }

    private companion object {
        const val DEFAULT_SIZE = 256
        const val MIN_SIZE = 96
        const val MAX_SIZE = 512
        const val FRAME_TIME_US = 1_000_000L
    }
}
