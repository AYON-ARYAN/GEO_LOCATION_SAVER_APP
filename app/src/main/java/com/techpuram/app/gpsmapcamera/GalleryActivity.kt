package com.techpuram.app.gpsmapcamera

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.techpuram.app.gpsmapcamera.ui.screens.PhotoGalleryScreen
import com.techpuram.app.gpsmapcamera.ui.screens.VideoPlayerScreen
import com.techpuram.app.gpsmapcamera.ui.theme.GPSmapCameraTheme

/**
 * Activity for viewing captured photos and videos with location information
 */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get data from intent
        val imageUri = intent.getStringExtra("imageUri")?.let { Uri.parse(it) }
        val videoUri = intent.getStringExtra("videoUri")?.let { Uri.parse(it) }
        val latitude = intent.getStringExtra("latitude") ?: "0.0"
        val longitude = intent.getStringExtra("longitude") ?: "0.0"
        val address = intent.getStringExtra("address") ?: "Unknown location"
        val mediaType = intent.getStringExtra("mediaType") ?: "photo"

        setContent {
            GPSmapCameraTheme {
                Surface(
                    modifier = Modifier.Companion.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        mediaType == "photo" && imageUri != null -> {
                            PhotoGalleryScreen(imageUri, latitude, longitude, address)
                        }

                        mediaType == "video" && videoUri != null -> {
                            VideoPlayerScreen(videoUri, latitude, longitude, address)
                        }

                        else -> {
                            // Fallback if no media is provided
                            Text(
                                text = stringResource(R.string.no_media_provided),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}