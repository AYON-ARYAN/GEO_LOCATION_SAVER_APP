package com.techpuram.app.gpsmapcamera.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A horizontal level indicator showing left/right tilt (roll) like Google Camera
 * Shows when device is tilted left or right from horizontal position
 */
@Composable
fun HorizontalLevelIndicator(
    roll: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isActive) return

    // Calculate the rounded angle value to display (left/right tilt only)
    val angleValue = abs(roll.roundToInt())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(110.dp)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val circleRadius = 35.dp.toPx()
            val lineLength = 70.dp.toPx()

            // Draw the circle outline (static)
            drawCircle(
                color = Color.LightGray.copy(alpha = 0.7f),
                radius = circleRadius,
                center = center,
                style = Stroke(width = 1.5f.dp.toPx())
            )

            // Draw a fixed horizontal reference line in the center (gray)
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(center.x - lineLength/2, center.y),
                end = Offset(center.x + lineLength/2, center.y),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Calculate horizontal offset based on roll (left/right tilt)
            // Constrain the movement within the circle
            val maxOffset = circleRadius * 0.8f // Stay within circle bounds
            val horizontalOffset = (roll * 2f).coerceIn(-maxOffset, maxOffset)
            
            // Draw the level indicator line (yellow) that moves left/right with device tilt
            val indicatorX = center.x + horizontalOffset
            drawLine(
                color = Color(0xFFFFCC00).copy(alpha = 0.9f),  // Yellow color
                start = Offset(indicatorX - lineLength/2, center.y),
                end = Offset(indicatorX + lineLength/2, center.y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Draw center marker dots for better reference
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = 2.dp.toPx(),
                center = center
            )
        }

        // Angle text in the center
        Text(
            text = "$angleValue°",
            color = Color.LightGray.copy(alpha = 0.9f),
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
    }
}