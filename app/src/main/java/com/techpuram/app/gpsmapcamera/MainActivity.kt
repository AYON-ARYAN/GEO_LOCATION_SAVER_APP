package com.techpuram.app.gpsmapcamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.techpuram.app.gpsmapcamera.ui.navigation.MainNavigation
import com.techpuram.app.gpsmapcamera.ui.theme.GPSmapCameraTheme

/**
 * Main activity for GPS Map Camera app
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GPSmapCameraTheme {
                MainNavigation()
            }
        }
    }
}