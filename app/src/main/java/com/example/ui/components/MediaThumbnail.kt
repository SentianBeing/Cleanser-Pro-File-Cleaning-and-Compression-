package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.MediaFileItem
import com.example.ui.theme.RoomyCardBg
import com.example.ui.theme.RoomyTextSecondary
import com.example.ui.thumbnails.MediaThumb

/**
 * Fast gallery thumbnail for photos and videos. Shows a type icon while loading or
 * if no thumbnail exists, and a duration badge on videos.
 */
@Composable
fun MediaThumbnail(
    item: MediaFileItem,
    modifier: Modifier = Modifier,
    showVideoBadge: Boolean = true
) {
    val model = remember(item.uri) { MediaThumb(item.uri, item.isVideo) }
    Box(
        modifier = modifier.background(RoomyCardBg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (item.isVideo) Icons.Default.Videocam else Icons.Default.Image,
            contentDescription = null,
            tint = RoomyTextSecondary.copy(alpha = 0.35f),
            modifier = Modifier.size(22.dp)
        )
        AsyncImage(
            model = model,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.isVideo && showVideoBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                if (item.formattedDuration.isNotEmpty()) {
                    Text(
                        text = item.formattedDuration,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}
