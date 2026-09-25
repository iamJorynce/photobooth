package com.example.photobooth.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream

@Composable
fun AdminSettingsScreen(
    currentPin: String,
    currentPrice: Int,
    currentLogoPath: String?,
    onSavePrice: (Int) -> Unit,
    onSaveLogoPath: (String) -> Unit,
    onExit: () -> Unit
) {
    var unlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    if (!unlocked) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Enter Admin PIN")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it; pinError = false },
                label = { Text("PIN") },
                isError = pinError
            )
            if (pinError) Text("Wrong PIN", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = {
                    if (pinInput == currentPin) unlocked = true else pinError = true
                }) { Text("Unlock") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onExit) { Text("Cancel") }
            }
        }
        return
    }

    val context = LocalContext.current
    var priceText by remember { mutableStateOf(currentPrice.toString()) }
    val logoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val file = File(context.filesDir, "establishment_logo.png")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        onSaveLogoPath(file.absolutePath)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Admin Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        Text("Establishment Logo")
        Spacer(Modifier.height(8.dp))
        if (currentLogoPath != null) {
            AsyncImage(
                model = currentLogoPath,
                contentDescription = "Logo",
                modifier = Modifier.size(120.dp)
            )
        } else {
            Text("(No logo set)")
        }
        Button(onClick = { logoPickerLauncher.launch("image/*") }) {
            Text("Choose Logo Image")
        }

        Spacer(Modifier.height(24.dp))
        Text("Price per session (₱)")
        OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it.filter { c -> c.isDigit() } },
            singleLine = true
        )

        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            onSavePrice(priceText.toIntOrNull() ?: currentPrice)
            onExit()
        }) { Text("Save & Exit") }
    }
}