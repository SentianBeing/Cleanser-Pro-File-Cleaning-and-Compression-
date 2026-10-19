package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RoomyCardBg
import com.example.ui.theme.RoomyCoral
import com.example.ui.theme.RoomyDivider
import com.example.ui.theme.RoomyLavender
import com.example.ui.theme.RoomyTextMain
import com.example.ui.theme.RoomyTextSecondary

@Composable
fun SmallStorageRing(
    usedPercent: Int,
    usedFormatted: String,
    totalFormatted: String,
    freeFormatted: String,
    modifier: Modifier = Modifier,
    ringSize: Dp = 110.dp,
    strokeWidth: Dp = 10.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = usedPercent.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "storageProgress"
    )

    val accessibilityDesc = "Device storage: $usedFormatted used out of $totalFormatted. $freeFormatted available."

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(RoomyCardBg)
            .padding(18.dp)
            .semantics { contentDescription = accessibilityDesc }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Restrained small storage ring
            Box(
                modifier = Modifier.size(ringSize),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(ringSize - 8.dp)) {
                    val stroke = strokeWidth.toPx()

                    // Background track
                    drawArc(
                        color = RoomyDivider,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )

                    // Active progress arc (Coral)
                    drawArc(
                        color = RoomyCoral,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$usedPercent%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoomyTextMain
                    )
                    Text(
                        text = "USED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = RoomyTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Labeled Volume Metrics
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Internal Storage",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoomyTextMain
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$freeFormatted free of $totalFormatted",
                    fontSize = 13.sp,
                    color = RoomyTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RoomyCoral)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$usedFormatted occupied",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = RoomyTextSecondary
                    )
                }
            }
        }
    }
}
