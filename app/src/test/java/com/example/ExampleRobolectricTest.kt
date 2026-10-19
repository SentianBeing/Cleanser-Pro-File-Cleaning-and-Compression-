package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DuplicateGroup
import com.example.model.MediaFileItem
import com.example.model.StorageMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context verifies Roomy app name`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Roomy", appName)
  }

  @Test
  fun `test byte formatting algorithm`() {
    assertEquals("0 B", StorageMetrics.formatBytes(0))
    assertEquals("512 B", StorageMetrics.formatBytes(512))
    assertEquals("1.0 KB", StorageMetrics.formatBytes(1024))
    assertEquals("1.5 MB", StorageMetrics.formatBytes((1.5 * 1024 * 1024).toLong()))
    assertEquals("2.0 GB", StorageMetrics.formatBytes((2L * 1024 * 1024 * 1024)))
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
}
