# GEO_LOCATION_SAVER_APP

GPS-tagged camera app for Android, built with Kotlin, Jetpack Compose, CameraX, and the Google Maps Compose SDK.

---

## Overview

GEO_LOCATION_SAVER_APP (internal package: `com.techpuram.app.gpsmapcamera`) is a single-module Android application that captures photographs and stamps them with verified GPS metadata: latitude, longitude, a reverse-geocoded postal address, a timestamp, and a small inset map of the location where the photo was taken. The result is a single composite JPEG that is suitable for field reports, site surveys, attendance / inspection workflows, insurance documentation, and any context where "where was this picture taken" needs to be answerable from the file alone.

The app is written end-to-end in Kotlin and uses Jetpack Compose for every screen. CameraX provides a consistent capture surface across vendors and Android versions, the Fused Location Provider supplies coordinates, and the Google Maps Static API renders the map overlay that is composited onto the captured frame. A local Room-backed cache and a SharedPreferences-backed `GeocodingCacheManager` reduce billable Geocoding API calls by reusing previously fetched addresses for nearby coordinates.

The project is structured for a BTech / final-year-project audience: a single `MainActivity` hosts a Compose `NavHost`, dedicated activities (`PhotoPreviewActivity`, `ResultActivity`, `SettingsActivity`) handle post-capture flows, and supporting utilities (`ImageProcessor`, `VerificationManager`, `OptimizedAddressFetcher`) keep the heavy I/O off the main thread using Kotlin coroutines.

---

## Features

The following features were verified directly against the source under `app/src/main/java/com/techpuram/app/gpsmapcamera/`:

- Live CameraX preview with front / back camera switching, flash toggle (on / off / auto), and tap-to-capture (`ui/CameraScreen.kt`).
- Photo capture pipeline that resolves the device location at capture time and forwards `(uri, lat, lon, address)` to the result screen (`ui/MainNavigation.kt`).
- Reverse geocoding via the Google Maps Geocoding API with an in-memory plus disk cache to skip duplicate look-ups when the user has not moved more than 50 meters in the last 24 hours (`GeocodingCacheManager.kt`).
- Composite image generation: the captured frame is overlaid with a translucent footer containing the address, coordinates, timestamp, and a Google Static Maps thumbnail centred on the GPS point (`ImageProcessor.kt`, `addText.kt`).
- Optional manual address override for the rare case where reverse geocoding mislabels a location (`LocationEdit.kt`).
- Photo preview screen with edit-address and re-share controls (`PhotoPreviewActivity.kt`).
- Result / share screen that exposes the composite image, supports JPG export to `MediaStore`, and ships the file via `FileProvider` for any share-sheet target (`ResultActivity.kt`).
- Settings screen with privacy policy, terms, and an about section (`SettingsActivity.kt`).
- Tamper-evidence hook: `VerificationManager.kt` builds a JSON payload with the device manufacturer, model, time, and coordinates so that a backend can sign the capture event.
- A horizontal level indicator rendered on top of the preview, driven by accelerometer / gyroscope data (`ui/HorizontalLevelIndicator.kt`, `ui/SensorHandler.kt`).
- Selectable composition aids: rule-of-thirds, 3x3, and 4x4 grid overlays plus 4:3 / 16:9 aspect-ratio toggles (string keys in `res/values/strings.xml`, logic in `ui/CameraScreen.kt`).
- Optional photo filters before capture (`ui/CameraFilters.kt`).
- Geotagged photo gallery with map open-out and share intents (`ui/GalleryScreen.kt`, `GalleryActivity.kt`, `GalleryComponents.kt`).
- Runtime permission handling for camera, fine location, microphone, and media-image access using Accompanist Permissions.

---

## Architecture

The application follows a thin-MVVM-style Compose layout: `MainActivity` is the only true entry point, and a Compose `NavHost` (`ui/MainNavigation.kt`) decides which screen to show. Heavy work (image compositing, network I/O, file writes) is delegated to plain Kotlin classes that take a `Context` and an `OkHttpClient` so they remain testable.

```
                          +-----------------------------+
                          |          User               |
                          +--------------+--------------+
                                         |
                                         v
+------------------------------------------------------------------+
|                       MainActivity                               |
|  (ComponentActivity, sets Compose content + GPSmapCameraTheme)   |
+------------------------------------------------------------------+
                                         |
                                         v
+------------------------------------------------------------------+
|              MainNavigation (NavHost, Compose)                   |
|   route "camera"  -->  CameraScreen                              |
|   route "gallery" -->  ResultActivity (Intent)                   |
+------------------------------------------------------------------+
              |                  |                       |
              v                  v                       v
+----------------------+  +-------------------+  +---------------------+
|  CameraScreen        |  | OptimizedAddress  |  | GeocodingCache      |
|  (Compose UI +       |  | Fetcher           |  | Manager             |
|   CameraX preview &  |  | (Geocoding API +  |  | (SharedPreferences, |
|   ImageCapture)      |  |  OkHttp)          |  |  50m / 24h policy)  |
+----------+-----------+  +---------+---------+  +---------+-----------+
           |                        |                      |
           v                        v                      v
+---------------------+    +------------------+    +-------------------+
| FusedLocationClient |    | Google Geocoding |    | On-device cache   |
| (play-services-     |    | REST (OkHttp)    |    | (key prefix       |
|  location)          |    |                  |    |  "geocache_")     |
+----------+----------+    +--------+---------+    +-------------------+
           |                        |
           +------------+-----------+
                        v
+------------------------------------------------------------------+
|        ResultActivity (composite + share)                        |
|   ImageProcessor.generateGoogleMapBitmap() -- Static Maps API    |
|   addText.addMapAndTextToImage()           -- Canvas overlay     |
|   VerificationManager.requestSessionKey()  -- backend hook       |
|   MediaStore + FileProvider                -- save & share       |
+------------------------------------------------------------------+
```

Threading model: every network or bitmap operation is wrapped in `withContext(Dispatchers.IO)` (see `ImageProcessor.kt`, `VerificationManager.kt`, `GeocodingCacheManager.kt`). The Compose layer only consumes the resulting `Uri`, `Bitmap`, or `String`.

API-key handling: the Maps key is **not** in source control. It lives in `local.properties` (gitignored), is read by `app/build.gradle.kts` at configuration time, and is published two ways:

1. As a manifest placeholder `${MAPS_API_KEY}` for the `com.google.android.geo.API_KEY` meta-data tag that the Maps SDK reads.
2. As `BuildConfig.MAPS_API_KEY` for the Kotlin code that calls the Static Maps and Geocoding REST APIs.

---

## Tech Stack

| Layer                | Library / Tool                          | Version             |
| -------------------- | --------------------------------------- | ------------------- |
| Language             | Kotlin                                  | 2.0.0               |
| Build                | Android Gradle Plugin                   | 8.9.2               |
| UI                   | Jetpack Compose BOM                     | 2024.04.01          |
| UI                   | Compose Material 3                      | 1.3.2               |
| UI                   | Material Icons Extended                 | 1.7.8               |
| Navigation           | androidx.navigation:navigation-compose  | 2.8.9               |
| Camera               | androidx.camera:camera-core / camera2   | 1.4.2               |
| Camera               | androidx.camera:camera-lifecycle / view | 1.4.2               |
| Location             | play-services-location                  | 21.3.0              |
| Maps SDK             | play-services-maps                      | 19.2.0              |
| Maps for Compose     | com.google.maps.android:maps-compose    | 3.1.0               |
| Maps Web Services    | com.google.maps:google-maps-services    | 2.2.0               |
| Networking           | OkHttp                                  | 4.12.0              |
| Coroutines           | kotlinx-coroutines (core / android)     | 1.7.3 / 1.10.1      |
| Permissions          | accompanist-permissions                 | 0.31.3-beta         |
| Image loading        | Coil for Compose                        | 2.5.0               |
| Local storage        | androidx.room                           | 2.7.0               |
| EXIF                 | androidx.exifinterface                  | 1.4.0               |
| Min SDK              | Android 7.0 (API 24)                    | -                   |
| Target / Compile SDK | Android 15 (API 35)                     | -                   |
| Java compatibility   | Java 11 source + target                 | -                   |

Versions above are pulled from `gradle/libs.versions.toml` and `app/build.gradle.kts`.

---

## Permissions Used

Declared in `app/src/main/AndroidManifest.xml`:

| Permission                                    | Purpose                                                        |
| --------------------------------------------- | -------------------------------------------------------------- |
| `android.permission.CAMERA`                   | Required to drive the CameraX preview and capture photos.     |
| `android.permission.ACCESS_FINE_LOCATION`     | High-accuracy GPS for the geotag and the inset map.           |
| `android.permission.ACCESS_COARSE_LOCATION`   | Coarse fallback when GPS fix is delayed or denied.            |
| `android.permission.ACCESS_MEDIA_LOCATION`    | Reads location metadata embedded in existing media (Q+).      |
| `android.permission.READ_MEDIA_IMAGES`        | Browses the on-device gallery on Android 13+.                 |
| `android.permission.READ_EXTERNAL_STORAGE`    | Legacy gallery read on pre-13 devices.                        |
| `android.permission.WRITE_EXTERNAL_STORAGE`   | Legacy save path on Android 9 and older (`maxSdkVersion=28`). |
| `android.permission.RECORD_AUDIO`             | Reserved for the optional video-mode capture path.            |
| `android.permission.INTERNET`                 | Calls Google Static Maps and Geocoding endpoints.             |
| `android.permission.ACCESS_NETWORK_STATE`     | Skips network calls when offline to avoid blocking captures.  |

Hardware features declared (via `<uses-feature>`): `android.hardware.camera`, `android.hardware.camera.autofocus`, `android.hardware.sensor.accelerometer`, `android.hardware.sensor.gyroscope`. `camera` and `autofocus` are flagged `required="false"` so the app installs on tablets and emulators that lack a real camera.

---

## Project Structure

```
GEO_LOCATION_SAVER_APP/
├── app/
│   ├── build.gradle.kts                    # module build script + Maps key injection
│   ├── proguard-rules.pro
│   └── src/
│       ├── androidTest/                    # instrumentation tests (placeholder)
│       ├── test/                           # JVM unit tests (placeholder)
│       └── main/
│           ├── AndroidManifest.xml         # permissions, activities, ${MAPS_API_KEY}
│           ├── ic_launcher-playstore.png
│           ├── java/com/techpuram/app/gpsmapcamera/
│           │   ├── MainActivity.kt          # Compose entry point + theme
│           │   ├── PhotoPreviewActivity.kt  # post-capture preview + edit
│           │   ├── ResultActivity.kt        # final composite, save & share
│           │   ├── SettingsActivity.kt      # settings, privacy, about
│           │   ├── GalleryActivity.kt       # geotagged media browser
│           │   ├── GalleryComponents.kt     # Compose pieces for the gallery
│           │   ├── ImageProcessor.kt        # composite generation + Static Maps
│           │   ├── addText.kt               # Canvas-based footer overlay
│           │   ├── pdfCreate.kt             # PDF export scaffold (commented)
│           │   ├── getAddress.kt            # OptimizedAddressFetcher
│           │   ├── GeocodingCacheManager.kt # 50m / 24h reverse-geocoding cache
│           │   ├── LocationEdit.kt          # manual address override UI
│           │   ├── VerificationManager.kt   # signing payload for backend
│           │   ├── ui/
│           │   │   ├── CameraScreen.kt       # main camera Compose UI
│           │   │   ├── CameraFilters.kt      # filter selection sheet
│           │   │   ├── CameraUtils.kt        # CameraX helpers
│           │   │   ├── GalleryScreen.kt      # Compose gallery
│           │   │   ├── HorizontalLevelIndicator.kt
│           │   │   ├── MainNavigation.kt     # NavHost wiring
│           │   │   ├── SensorHandler.kt      # accelerometer / gyroscope
│           │   │   ├── UIComponents.kt
│           │   │   ├── UtilityFunctions.kt
│           │   │   └── theme/                # Color, Type, Theme.kt
│           │   └── util/
│           │       └── AddressParser.kt
│           └── res/
│               ├── drawable/                # launcher vectors
│               ├── mipmap-anydpi-v26/
│               ├── values/                  # strings, colors, themes
│               └── xml/                     # backup_rules, file_paths, data_extraction
├── gradle/
│   ├── libs.versions.toml                  # version catalog
│   └── wrapper/
├── build.gradle.kts                        # root build script
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── local.properties.example                # template for MAPS_API_KEY
├── .gitignore
└── README.md                               # this file
```

---

## Prerequisites

To build and run the project you will need:

- **Android Studio Ladybug (2024.2.x)** or newer. The project targets `compileSdk = 35` and uses the Compose Compiler shipped with Kotlin 2.0.0, so older IDE versions will not import cleanly.
- **JDK 17** for Gradle. The compiled bytecode targets Java 11 (`compileOptions.targetCompatibility = JavaVersion.VERSION_11`), but AGP 8.9.x itself requires JDK 17 to run.
- **Android SDK Platform 35** and **Build-Tools 35.x** installed via the SDK Manager.
- A physical Android device (recommended) or an emulator with **Google Play Services** so that fused location and Maps work. A bare AOSP emulator image will not have Play Services.
- A **Google Cloud project** with the following APIs enabled:
  - Maps SDK for Android
  - Maps Static API
  - Geocoding API
- An API key from that Cloud project. Restrict the key to your app's package name (`com.techpuram.app.gpsmapcamera`) and your debug / release signing-certificate SHA-1 before sharing builds.

---

## Setup

1. **Clone the repository**

   ```bash
   git clone https://github.com/AYON-ARYAN/GEO_LOCATION_SAVER_APP.git
   cd GEO_LOCATION_SAVER_APP
   ```

2. **Create your `local.properties`**

   Copy the template and add your Maps key:

   ```bash
   cp local.properties.example local.properties
   ```

   Then edit `local.properties` and set:

   ```
   MAPS_API_KEY=AIzaSy...your-real-key...
   ```

   `local.properties` is listed in `.gitignore` and will not be committed. Android Studio fills in `sdk.dir` automatically the first time you open the project.

3. **Open in Android Studio** and let Gradle sync. The first sync will download AGP 8.9.2, the Compose BOM, and all dependencies, which can take several minutes on a fresh machine.

4. **Run the app** on a device or Play-Services emulator. Android Studio's Run button will install the debug APK and launch `MainActivity`. Grant the camera and location permissions when prompted; the camera preview and address line will populate within a second or two on a device with a recent GPS fix.

If the inset map shows up grey or the address says "Unknown", double-check that your Maps key is set in `local.properties`, that all three Google Cloud APIs are enabled, and that the key restriction matches the package + SHA-1 you are running.

---

## Build and Install

Build a debug APK from the command line:

```bash
./gradlew assembleDebug
```

The artefact will be at `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a minified release build (R8 + resource shrinking are enabled in `app/build.gradle.kts`):

```bash
./gradlew assembleRelease
```

You will need to configure a `signingConfigs.release` block before a release build will install on a device.

Useful auxiliary tasks:

```bash
./gradlew lint                         # static analysis
./gradlew test                         # JVM unit tests
./gradlew connectedDebugAndroidTest    # instrumentation tests on a connected device
./gradlew clean                        # wipe build outputs
```

---

## Screenshots

<!-- Add device screenshots of capture, map view, gallery -->

---

## Limitations and Future Work

- **No backend yet.** `VerificationManager.requestSessionKey()` builds the JSON payload but is not wired to a deployed signer. The next milestone is a small Cloud Run / Firebase Function that returns a HMAC over `(time, lat, lon, address, mobile_id)` so that the composite image can be cryptographically tied to the device that captured it.
- **PDF export is a stub.** `pdfCreate.kt` contains a commented-out implementation. The Result screen currently advertises "Save as PDF" via a string resource but the action is disabled.
- **Video mode is partial.** Microphone permission and string resources for record / stop are wired up, but `CameraScreen.kt` only commits the `ImageCapture` use-case. Adding `VideoCapture` and a recording overlay is a logical next step.
- **No offline maps.** When the device is offline the Static Maps overlay falls back to a placeholder. Bundling Mapbox tiles or pre-fetching the tile around the last known location would solve this.
- **Tests are scaffolding only.** `app/src/test/...ExampleUnitTest.kt` and `app/src/androidTest/...ExampleInstrumentedTest.kt` are the IDE defaults. Adding Robolectric tests for `GeocodingCacheManager` and a screenshot test for `CameraScreen` is on the roadmap.
- **Hard-coded thresholds.** `GeocodingCacheManager` uses `MIN_DISTANCE_METERS = 50.0` and `CACHE_EXPIRY_HOURS = 24`. Exposing these in `SettingsActivity` would make the cache policy user-tunable.
- **Single-locale strings.** `res/values/strings.xml` is English-only. An `res/values-hi/` translation is the obvious follow-up given the target user base.

---

## License

This project is released under the MIT License.

```
MIT License

Copyright (c) 2025 Ayon Aryan

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```
