package com.example.photobooth.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

@Composable
fun CaptureScreen(onPhotoCaptured: (String) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "[Camera preview goes here]")
        Button(onClick = {
            // Temporary: generates a real dummy JPEG so upload/printing can be tested.
            val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.CYAN)
            val file = File(context.cacheDir, "test_photo.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            onPhotoCaptured(file.absolutePath)
        }) {
            Text("Simulate capture")
        }
    }
}