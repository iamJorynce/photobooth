package com.example.photobooth.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * TODO: replace this whole screen with a real CameraX PreviewView +
 * countdown timer + ImageCapture.takePicture(). Call onPhotoCaptured(path)
 * once the file is saved to local storage.
 *
 * Add these dependencies to app/build.gradle first:
 *   implementation "androidx.camera:camera-core:1.3.4"
 *   implementation "androidx.camera:camera-camera2:1.3.4"
 *   implementation "androidx.camera:camera-lifecycle:1.3.4"
 *   implementation "androidx.camera:camera-view:1.3.4"
 */
@Composable
fun CaptureScreen(onPhotoCaptured: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "[Camera preview goes here]")
        Button(onClick = { onPhotoCaptured("/storage/emulated/0/Pictures/placeholder.jpg") }) {
            Text("Simulate capture")
        }
    }
}
