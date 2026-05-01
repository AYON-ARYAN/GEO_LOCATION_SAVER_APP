package com.techpuram.app.gpsmapcamera.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.techpuram.app.gpsmapcamera.R

/**
 * Screen for viewing photos
 */
@Composable
fun PhotoGalleryScreen(imageUri: Uri, latitude: String, longitude: String, address: String) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Photo viewer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            // Image
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Captured photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Location info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.location_information),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${stringResource(R.string.coordinates)}: $latitude, $longitude",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Google Maps button
            Button(
                onClick = {
                    val gmmIntentUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    if (mapIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(mapIntent)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.open_in_google_maps))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Share button
            Button(
                onClick = {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, imageUri)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${context.getString(R.string.photo_taken_at)}: $address\n${context.getString(R.string.coordinates)}: $latitude, $longitude"
                        )
                        type = "image/*"
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_photo_via)))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.share_photo))
            }
        }
    }
}

/**
 * Screen for viewing videos
 */
@Composable
fun VideoPlayerScreen(videoUri: Uri, latitude: String, longitude: String, address: String) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Video Player
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(videoUri)
                        setMediaController(MediaController(ctx))
                        requestFocus()
                        start()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Location info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.location_information),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${stringResource(R.string.coordinates)}: $latitude, $longitude",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Google Maps button
            Button(
                onClick = {
                    val gmmIntentUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    if (mapIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(mapIntent)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.open_in_google_maps))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Share button
            Button(
                onClick = {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, videoUri)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${context.getString(R.string.video_recorded_at)}: $address\n${context.getString(R.string.coordinates)}: $latitude, $longitude"
                        )
                        type = "video/*"
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_video_via)))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.share_video))
            }
        }
    }
}