package com.example.photobooth.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

/**
 * Shows the dynamic PayMongo QR Ph code. No button here on purpose --
 * KioskViewModel listens to Firestore and moves to Capture automatically
 * once the webhook marks the session "paid".
 */
@Composable
fun PaymentScreen(
    sessionId: String,
    qrImageBase64: String,
    amount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Scan to pay \u20b1$amount")

        val base64Data = remember(qrImageBase64) {
            qrImageBase64.substringAfter("base64,", qrImageBase64)
        }
        val bitmap = remember(base64Data) {
            runCatching {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Payment QR code",
                modifier = Modifier.size(260.dp)
            )
        } else {
            CircularProgressIndicator()
        }

        Text(text = "Waiting for payment...")
        // TODO: add a visible countdown (QR expires after 30 min) and a
        // timeout screen that lets the customer go back to Idle / retry.
    }
}
