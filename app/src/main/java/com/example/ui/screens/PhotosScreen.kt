package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.MediaFileItem
import com.example.model.StorageMetrics
import com.example.ui.theme.RoomyBackground
import com.example.ui.theme.RoomyCardBg
import com.example.ui.theme.RoomyCompress
import com.example.ui.theme.RoomyCompressLight
import com.example.ui.theme.RoomyDelete
import com.example.ui.theme.RoomyDivider
import com.example.ui.theme.RoomyLavender
import com.example.ui.theme.RoomyLavenderLight
import com.example.ui.theme.RoomyTextMain
import com.example.ui.theme.RoomyTextSecondary
import java.io.File

@Composable
fun PhotosScreen(
    photosList: List<MediaFileItem>,
    onCompressPhoto: (MediaFileItem) -> Unit,
    onDeletePhoto: (MediaFileItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var itemPendingDelete by remember { mutableStateOf<MediaFileItem?>(null) }
    val totalSize = photosList.sumOf { it.sizeBytes }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RoomyBackground)
            .testTag("photos_screen")
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
                modifier = Modifier.testTag("back_button_photos")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = RoomyTextMain
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Photos",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RoomyTextMain
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Header info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${photosList.size} accessible images",
                fontSize = 12.sp,
                color = RoomyTextSecondary
            )
            Text(
                text = StorageMetrics.formatBytes(totalSize),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = RoomyLavender
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(photosList, key = { it.id }) { photo ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(RoomyCardBg)
                        .border(1.dp, RoomyDivider, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(1.dp, RoomyDivider, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(photo.uri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = photo.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = photo.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = RoomyTextMain
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${photo.formattedSize} · ${photo.dimensions}",
                                    fontSize = 11.sp,
                                    color = RoomyTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onCompressPhoto(photo) },
                                modifier = Modifier.weight(1.1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoomyCompressLight,
                                    contentColor = RoomyCompress
                                )
                            ) {
                                Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Compress", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { itemPendingDelete = photo },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", fontSize = 11.sp, color = RoomyDelete)
                            }
                        }
                    }
                }
            }
        }
    }

    itemPendingDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { itemPendingDelete = null },
            title = { Text("Delete Photo?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete \"${photo.name}\" (${photo.formattedSize})?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePhoto(photo)
                        itemPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoomyDelete)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
