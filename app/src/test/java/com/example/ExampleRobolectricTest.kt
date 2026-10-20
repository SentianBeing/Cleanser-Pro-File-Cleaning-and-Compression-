package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.scaledSize
import com.example.model.DuplicateGroup
import com.example.model.MediaCollection
import com.example.model.MediaFileItem
import com.example.model.StorageMetrics
import com.example.model.recommendKeeper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private fun media(id: String, folder: String = "DCIM/Camera/", date: Long = 0L, video: Boolean = false) =
    MediaFileItem(id, android.net.Uri.EMPTY, "$id.jpg", folder + id, 5000L, video, "image/jpeg", dateAdded = date, folder = folder)

  @Test
  fun `read string from context verifies Cleanser app name`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Cleanser: Clean & Compress", context.getString(R.string.app_name))
    assertEquals("Cleanser", context.getString(R.string.launcher_name))
  }

  @Test
  fun `test byte formatting algorithm`() {
    assertEquals("0 B", StorageMetrics.formatBytes(0))
    assertEquals("512 B", StorageMetrics.formatBytes(512))
    assertEquals("1.0 KB", StorageMetrics.formatBytes(1000))
    assertEquals("1.5 MB", StorageMetrics.formatBytes(1_500_000))
    assertEquals("2.0 GB", StorageMetrics.formatBytes(2_000_000_000))
  }

  @Test
  fun `test duplicate group prevents deleting every copy`() {
    val file1 = MediaFileItem("1", android.net.Uri.EMPTY, "photo.jpg", "/p1", 5000L, false, "image/jpeg", isSelectedForDelete = true)
    val file2 = MediaFileItem("2", android.net.Uri.EMPTY, "photo_copy.jpg", "/p2", 5000L, false, "image/jpeg", isSelectedForDelete = true)

    val group = DuplicateGroup("hash123", 5000L, false, listOf(file1, file2), "1")
    // If every copy is checked for delete, removable size should be 0 to block complete deletion
    assertEquals(0L, group.removableSize)

    // If only one is selected, removable size is 5000
    val safeGroup = DuplicateGroup("hash123", 5000L, false, listOf(file1.copy(isSelectedForDelete = false), file2), "1")
    assertEquals(5000L, safeGroup.removableSize)
  }

  @Test
  fun `camera original is recommended over a newer-dated WhatsApp copy and an older download`() {
    val camera = media("camera", "DCIM/Camera/", date = 2_000)
    val whatsapp = media("wa", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", date = 1_000)
    val download = media("dl", "Download/", date = 500)
    assertEquals("camera", recommendKeeper(listOf(whatsapp, download, camera)).id)
  }

  @Test
  fun `oldest copy wins when all copies are in camera folders`() {
    val newer = media("newer", date = 2_000)
    val older = media("older", date = 1_000)
    assertEquals("older", recommendKeeper(listOf(newer, older)).id)
  }

  @Test
  fun `scaled size keeps aspect ratio and never upscales`() {
    assertEquals(1920 to 1440, scaledSize(4000, 3000, 1920))
    assertEquals(1440 to 1920, scaledSize(3000, 4000, 1920))
    assertEquals(800 to 600, scaledSize(800, 600, 1920))
  }

  @Test
  fun `clutter collections match the right folders`() {
    assertTrue(MediaCollection.SCREENSHOTS.matches(media("s", "Pictures/Screenshots/")))
    assertFalse(MediaCollection.SCREENSHOTS.matches(media("c", "DCIM/Camera/")))
    assertTrue(MediaCollection.SCREEN_RECORDINGS.matches(media("r", "Movies/Screen recordings/", video = true)))
    assertTrue(MediaCollection.WHATSAPP.matches(media("w", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/")))
    assertTrue(MediaCollection.DOWNLOADS.matches(media("d", "Download/")))
  }
}
