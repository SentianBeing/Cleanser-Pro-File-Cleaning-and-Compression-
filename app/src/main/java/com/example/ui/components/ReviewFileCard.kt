package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.MediaFileItem
import com.example.ui.theme.RoomyCardBg
import com.example.ui.theme.RoomyCompress
import com.example.ui.theme.RoomyCompressLight
import com.example.ui.theme.RoomyCoral
import com.example.ui.theme.RoomyDelete
import com.example.ui.theme.RoomyDeleteLight
import com.example.ui.theme.RoomyDivider
import com.example.ui.theme.RoomyKeep
import com.example.ui.theme.RoomyKeepLight
import com.example.ui.theme.RoomyLavender
import com.example.ui.theme.RoomyTextMain
import com.example.ui.theme.RoomyTextSecondary
import java.io.File

@Composable
fun ReviewFileCard(
    item: MediaFileItem,
    onKeep: () -> Unit,
    onUnkeep: () -> Unit,
    onCompress: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RoomyCardBg)
            .border(1.dp, RoomyDivider, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Fast system thumbnail; tap to open the full preview
                MediaThumbnail(
                    item = item,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, RoomyDivider, RoundedCornerShape(12.dp))
                        .clickable { onOpen() }
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoomyTextMain,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.formattedSize} · ${item.formattedDate}",
                        fontSize = 12.sp,
                        color = RoomyTextSecondary
                    )

                    // Dimension or duration metadata
                    if (item.dimensions.isNotEmpty() || item.formattedDuration.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (item.isVideo) "Duration: ${item.formattedDuration}" else "Resolution: ${item.dimensions}",
                            fontSize = 11.sp,
                            color = RoomyTextSecondary
                        )
                    }
                }

                // Kept Badge if excluded
                if (item.isExcluded) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RoomyKeepLight)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "KEPT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoomyKeep
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3-Way Action Row: Keep | Compress | Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Keep Button
                if (item.isExcluded) {
                    OutlinedButton(
                        onClick = onUnkeep,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Unkeep", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onKeep,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoomyKeepLight,
                            contentColor = RoomyKeep
                        )
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Keep", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Compress Button
                Button(
                    onClick = onCompress,
                    modifier = Modifier.weight(1.1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoomyCompressLight,
                        contentColor = RoomyCompress
                    )
                ) {
                    Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Compress", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Delete Button
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoomyDeleteLight,
                        contentColor = RoomyDelete
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
