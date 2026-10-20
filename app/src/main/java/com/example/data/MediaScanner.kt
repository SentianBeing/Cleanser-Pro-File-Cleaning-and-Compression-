package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.model.MediaFileItem

/**
 * Reads the photo and video index straight from MediaStore. This only touches
 * the database, never the files, so it stays fast even for very large galleries.
 */
class MediaScanner(private val context: Context) {

    fun queryAll(keptIds: Set<String>, compressedCopyIds: Set<String>): List<MediaFileItem> {
        val items = ArrayList<MediaFileItem>()
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false, keptIds, compressedCopyIds, items)
        query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true, keptIds, compressedCopyIds, items)
        return items
    }

    @Suppress("DEPRECATION") // DATA is only read on Android 9 and older, where RELATIVE_PATH does not exist
    private fun query(
        collection: Uri,
        isVideo: Boolean,
        keptIds: Set<String>,
        compressedCopyIds: Set<String>,
        into: MutableList<MediaFileItem>
    ) {
        val folderColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(if (isVideo) MediaStore.Video.VideoColumns.DATE_TAKEN else MediaStore.Images.ImageColumns.DATE_TAKEN)
            add(folderColumn)
            if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.ORIENTATION)
        }.toTypedArray()

        try {
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modifiedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val takenCol = cursor.getColumnIndex(projection[7])
                val folderCol = cursor.getColumnIndex(folderColumn)
                val durationCol = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
                val orientationCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val key = uri.toString()
                    val fallbackName = if (isVideo) "Video_$id" else "Image_$id"
                    val name = cursor.stringOrNull(nameCol) ?: fallbackName
                    // WIDTH/HEIGHT are stored before rotation; show what the user actually sees
                    val sideways = cursor.intOrZero(orientationCol) % 180 == 90
                    val w = cursor.intOrZero(if (sideways) heightCol else widthCol)
                    val h = cursor.intOrZero(if (sideways) widthCol else heightCol)
                    val folder = folderOf(cursor.stringOrNull(folderCol).orEmpty())

                    into.add(
                        MediaFileItem(
                            id = key,
                            uri = uri,
                            name = name,
                            path = folder + name,
                            sizeBytes = cursor.longOrZero(sizeCol),
                            isVideo = isVideo,
                            mimeType = cursor.stringOrNull(mimeCol) ?: if (isVideo) "video/mp4" else "image/jpeg",
                            dateAdded = cursor.longOrZero(modifiedCol) * 1000L,
                            dimensions = if (w > 0 && h > 0) "$w x $h" else "",
                            durationSeconds = (cursor.longOrZero(durationCol) / 1000L).toInt(),
                            isExcluded = key in keptIds,
                            dateTaken = cursor.longOrZero(takenCol),
                            folder = folder,
                            isCompressedCopy = key in compressedCopyIds
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // No gallery permission: the caller shows the permission banner instead
        }
    }

    /** RELATIVE_PATH is already a folder; DATA (Android 9 and older) is a full path that needs trimming. */
    private fun folderOf(raw: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return raw
        val parent = raw.substringBeforeLast('/', "")
        val relative = parent.substringAfter("/emulated/0/", parent)
        return if (relative.isEmpty()) "" else "$relative/"
    }

    private fun android.database.Cursor.stringOrNull(col: Int): String? = if (col >= 0) getString(col) else null
    private fun android.database.Cursor.longOrZero(col: Int): Long = if (col >= 0 && !isNull(col)) getLong(col) else 0L
    private fun android.database.Cursor.intOrZero(col: Int): Int = if (col >= 0 && !isNull(col)) getInt(col) else 0
}
