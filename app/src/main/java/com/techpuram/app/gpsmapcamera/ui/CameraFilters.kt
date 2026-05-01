package com.techpuram.app.gpsmapcamera.ui

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techpuram.app.gpsmapcamera.R
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint

// Define filter types
enum class CameraFilter(val displayName: String) {
    NORMAL("Normal"),
    GRAYSCALE("Grayscale"),
    SEPIA("Sepia"),
    WARM("Warm"),
    COOL("Cool"),
    VINTAGE("Vintage");

    companion object {
        fun applyFilter(bitmap: Bitmap, filter: CameraFilter): Bitmap {
            // Create mutable copy of bitmap to apply filter
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = AndroidCanvas(result)
            val paint = AndroidPaint()

            when (filter) {
                NORMAL -> {
                    // No filter applied
                    return bitmap
                }
                GRAYSCALE -> {
                    val colorMatrix = ColorMatrix().apply {
                        setSaturation(0f) // 0 means grayscale
                    }
                    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                }
                SEPIA -> {
                    val colorMatrix = ColorMatrix().apply {
                        set(floatArrayOf(
                            0.393f, 0.769f, 0.189f, 0f, 0f,
                            0.349f, 0.686f, 0.168f, 0f, 0f,
                            0.272f, 0.534f, 0.131f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                }
                WARM -> {
                    val colorMatrix = ColorMatrix().apply {
                        set(floatArrayOf(
                            1.1f, 0f, 0f, 0f, 10f,
                            0f, 1f, 0f, 0f, 10f,
                            0f, 0f, 0.9f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                }
                COOL -> {
                    val colorMatrix = ColorMatrix().apply {
                        set(floatArrayOf(
                            0.9f, 0f, 0f, 0f, 0f,
                            0f, 1f, 0f, 0f, 0f,
                            0f, 0f, 1.1f, 0f, 10f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                }
                VINTAGE -> {
                    val colorMatrix = ColorMatrix().apply {
                        set(floatArrayOf(
                            0.9f, 0.5f, 0.1f, 0f, 0f,
                            0.3f, 0.8f, 0.1f, 0f, 0f,
                            0.2f, 0.3f, 0.5f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                }
            }

            if (filter != NORMAL) {
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }

            return result
        }
    }
}

@Composable
fun CameraFilterSelector(
    selectedFilter: CameraFilter,
    onFilterSelected: (CameraFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(vertical = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CameraFilter.values().forEach { filter ->
                    FilterOption(
                        filter = filter,
                        isSelected = filter == selectedFilter,
                        onSelect = { onFilterSelected(filter) }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterOption(
    filter: CameraFilter,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(70.dp)
            .clickable(onClick = onSelect)
    ) {
        // Circle preview of the filter
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(getFilterPreviewColor(filter))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) Color.White else Color.Gray,
                    shape = CircleShape
                )
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Filter name
        Text(
            text = filter.displayName,
            color = if (isSelected) Color.White else Color.LightGray,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// Helper to get preview colors for filters
private fun getFilterPreviewColor(filter: CameraFilter): Color {
    return when (filter) {
        CameraFilter.NORMAL -> Color(0xFF5B9BD5)
        CameraFilter.GRAYSCALE -> Color(0xFF7F7F7F)
        CameraFilter.SEPIA -> Color(0xFFBF8F65)
        CameraFilter.WARM -> Color(0xFFFF9800)
        CameraFilter.COOL -> Color(0xFF00BCD4)
        CameraFilter.VINTAGE -> Color(0xFF8D6E63)
    }
}