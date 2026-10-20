package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.JunkItem
import com.example.model.MediaCollection
import com.example.model.MediaFileItem
import com.example.ui.components.CategoryTile
import com.example.ui.theme.RoomyCoralLight
import com.example.model.StorageMetrics
import com.example.ui.theme.RoomyBackground
import com.example.ui.theme.RoomyCardBg
import com.example.ui.theme.RoomyCoral
import com.example.ui.theme.RoomyDelete
import com.example.ui.theme.RoomyDeleteLight
import com.example.ui.theme.RoomyDivider
import com.example.ui.theme.RoomyKeep
import com.example.ui.theme.RoomyTextMain
import com.example.ui.theme.RoomyTextSecondary

@Composable
fun JunkScreen(
    junkItems: List<JunkItem>,
    onToggleJunk: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    collections: Map<MediaCollection, List<MediaFileItem>> = emptyMap(),
    onOpenCollection: (MediaCollection) -> Unit = {}
) {
    val context = LocalContext.current
    val selectedItems = junkItems.filter { it.isSelected }
    val selectedBytes = selectedItems.sumOf { it.sizeBytes }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RoomyBackground)
            .testTag("junk_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("back_button_junk")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = RoomyTextMain
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Junk cleanup",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RoomyTextMain
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Explanation of Scoped Storage Boundaries per PRD
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(RoomyCardBg)
                        .border(1.dp, RoomyDivider, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(RoomyDeleteLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = RoomyDelete,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "What Cleanser can clean",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoomyTextMain
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Android doesn't let apps clear other apps' caches. Cleanser finds clutter in your gallery and its own leftovers; for other apps' caches, use Android's storage settings below.",
                                fontSize = 12.sp,
                                color = RoomyTextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Folders that quietly fill up: each opens a grid to review and clean
            item {
                Text(
                    text = "CLUTTER TO REVIEW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = RoomyTextSecondary
                )
            }
            items(MediaCollection.entries, key = { it.name }) { collection ->
                val files = collections[collection].orEmpty()
                CategoryTile(
                    title = collection.title,
                    subtitle = if (files.isEmpty()) "Nothing found" else
                        "${if (files.size == 1) "1 item" else "${files.size} items"} · ${StorageMetrics.formatBytes(files.sumOf { it.sizeBytes })} · ${collection.description}",
                    icon = when (collection) {
                        MediaCollection.SCREENSHOTS -> Icons.Default.Screenshot
                        MediaCollection.SCREEN_RECORDINGS -> Icons.Default.Videocam
                        MediaCollection.WHATSAPP, MediaCollection.TELEGRAM -> Icons.Default.Forum
                        MediaCollection.DOWNLOADS -> Icons.Default.Download
                    },
                    iconTint = RoomyCoral,
                    iconBgColor = RoomyCoralLight,
                    onClick = { onOpenCollection(collection) },
                    testTag = "collection_${collection.name.lowercase()}"
                )
            }

            item {
                Text(
                    text = "APP LEFTOVERS (${junkItems.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = RoomyTextSecondary
                )
            }

            if (junkItems.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = RoomyKeep,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Leftover Files",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoomyTextMain
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cleanser has no temporary files to clean.",
                            fontSize = 12.sp,
                            color = RoomyTextSecondary
                        )
                    }
                }
            } else {
                items(junkItems, key = { it.id }) { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(RoomyCardBg)
                            .border(1.dp, RoomyDivider, RoundedCornerShape(16.dp))
                            .clickable { onToggleJunk(item.id) }
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.isSelected,
                                onCheckedChange = { onToggleJunk(item.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = RoomyCoral,
                                    checkmarkColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RoomyTextMain
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.reason,
                                    fontSize = 11.sp,
                                    color = RoomyTextSecondary
                                )
                            }

                            Text(
                                text = item.formattedSize,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoomyCoral
                            )
                        }
                    }
                }
            }

            // System Storage Deep Link Card
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openSystemStorage(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage System & App Storage in Android", fontSize = 13.sp)
                }
            }
        }

        // Action CTA
        if (selectedItems.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Button(
                    onClick = onDeleteSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoomyDelete)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clean ${selectedItems.size} files (${StorageMetrics.formatBytes(selectedBytes)})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun openSystemStorage(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val appIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appIntent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}
