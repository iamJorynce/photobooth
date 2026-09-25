package com.example.photobooth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.photobooth.screens.CaptureScreen
import com.example.photobooth.screens.DownloadScreen
import com.example.photobooth.screens.IdleScreen
import com.example.photobooth.screens.PaymentScreen
import com.example.photobooth.screens.PrintingScreen

class MainActivity : ComponentActivity() {

    private val viewModel: KioskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: enable kiosk / lock-task mode here so customers can't leave the app.
        // See: https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KioskApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun KioskApp(viewModel: KioskViewModel) {
    val state by viewModel.state.collectAsState()

    when (val current = state) {
        is KioskState.Idle -> IdleScreen(
            onStart = { viewModel.startSession() }
        )
        is KioskState.Payment -> PaymentScreen(
            sessionId = current.sessionId,
            qrImageBase64 = current.qrImageBase64,
            amount = current.amount
        )
        is KioskState.Capture -> CaptureScreen(
            onPhotoCaptured = { path -> viewModel.onPhotoCaptured(path) }
        )
        is KioskState.Printing -> PrintingScreen()
        is KioskState.Download -> DownloadScreen(
            downloadUrl = current.downloadUrl,
            onDone = { viewModel.reset() }
        )
    }
}
