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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.photobooth.screens.AdminSettingsScreen
import com.example.photobooth.screens.CaptureScreen
import com.example.photobooth.screens.DownloadScreen
import com.example.photobooth.screens.IdleScreen
import com.example.photobooth.screens.PaymentScreen
import com.example.photobooth.screens.PrintingScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: KioskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val context = LocalContext.current
    val store = remember { AdminSettingsStore(context) }
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = AdminSettings())
    var showAdmin by remember { mutableStateOf(false) }

    if (showAdmin) {
        AdminSettingsScreen(
            currentPin = settings.pin,
            currentPrice = settings.priceInPesos,
            currentLogoPath = settings.logoPath,
            onSavePrice = { price -> scope.launch { store.savePrice(price) } },
            onSaveLogoPath = { path -> scope.launch { store.saveLogoPath(path) } },
            onExit = { showAdmin = false }
        )
        return
    }

    val state by viewModel.state.collectAsState()

    when (val current = state) {
        is KioskState.Idle -> IdleScreen(
            priceInPesos = settings.priceInPesos,
            onStart = { viewModel.startSession(settings.priceInPesos) },
            onAdminLongPress = { showAdmin = true }
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