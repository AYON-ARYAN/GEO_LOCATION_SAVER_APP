package com.techpuram.app.gpsmapcamera.ui.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import com.techpuram.app.gpsmapcamera.ResultActivity
import com.techpuram.app.gpsmapcamera.ui.CameraScreen

/**
 * Main navigation component for the app
 */
@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = "camera") {
        // Main camera screen
        composable("camera") {
            CameraScreen(
                // Handle captured images
                onImageCaptured = { uri, lat, lon, addr ->
                    // Navigate to GalleryActivity for photos
                    val intent = Intent(context, ResultActivity::class.java).apply {
                        putExtra("imageUri", uri.toString())
                        putExtra("latitude", lat)
                        putExtra("longitude", lon)
                        putExtra("address", addr)
                        putExtra("mediaType", "photo")
                    }
                    context.startActivity(intent)
                }
            )
        }

        // Gallery route for deep links or navigation
        composable(
            route = "gallery/{mediaUri}/{mediaType}",
            arguments = listOf(
                navArgument("mediaUri") { type = NavType.StringType },
                navArgument("mediaType") { type = NavType.StringType; defaultValue = "photo" }
            )
        ) { backStackEntry ->
            val mediaUri = backStackEntry.arguments?.getString("mediaUri")
            val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "photo"

            // Launch GalleryActivity with the media information
            val intent = Intent(context, ResultActivity::class.java).apply {
                if (mediaType == "photo") {
                    putExtra("imageUri", mediaUri)
                } else {
                    putExtra("videoUri", mediaUri)
                }
                putExtra("mediaType", mediaType)
            }
            context.startActivity(intent)
        }
    }
}