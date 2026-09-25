package com.example.photobooth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * All the screens/states the kiosk cycles through.
 * Idle -> Payment -> Capture -> Printing -> Download -> (back to) Idle
 */
sealed class KioskState {
    object Idle : KioskState()
    data class Payment(val sessionId: String, val qrImageBase64: String, val amount: Int) : KioskState()
    object Capture : KioskState()
    object Printing : KioskState()
    data class Download(val downloadUrl: String) : KioskState()
}

// TODO: replace with your deployed Render URL, e.g. https://photobooth-backend.onrender.com
private const val BACKEND_BASE_URL = "https://photobooth-fcm9.onrender.com"
private const val POLL_INTERVAL_MS = 3000L
private const val POLL_TIMEOUT_MS = 5 * 60 * 1000L // give up after 5 min of no payment

class KioskViewModel : ViewModel() {

    private val _state = MutableStateFlow<KioskState>(KioskState.Idle)
    val state: StateFlow<KioskState> = _state

    private val httpClient = OkHttpClient()
    private var sessionId: String? = null
    private var pollingJob: kotlinx.coroutines.Job? = null

    /** Customer taps "Start" on the idle screen. */
    fun startSession(priceInPesos: Int) {
        viewModelScope.launch {
            try {
                val (newSessionId, qrImageBase64) = requestPaymentQrFromBackend(priceInPesos)
                sessionId = newSessionId
                _state.value = KioskState.Payment(
                    sessionId = newSessionId,
                    qrImageBase64 = qrImageBase64,
                    amount = priceInPesos
                )
                pollForPaymentConfirmation(newSessionId)
            } catch (e: Exception) {
                _state.value = KioskState.Idle
            }
        }
    }

    /** Polls the backend every few seconds -- no button needed, moves on automatically. */
    private fun pollForPaymentConfirmation(sessionId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                delay(POLL_INTERVAL_MS)
                val status = runCatching { fetchSessionStatus(sessionId) }.getOrNull()
                when (status) {
                    "paid" -> {
                        _state.value = KioskState.Capture
                        return@launch
                    }
                    "failed", "expired" -> {
                        // TODO: show a real "payment failed" screen with a retry button.
                        _state.value = KioskState.Idle
                        return@launch
                    }
                    else -> { /* still pending, keep polling */ }
                }
            }
            // Timed out waiting for payment.
            _state.value = KioskState.Idle
        }
    }

    /** Called after CameraX captures and saves the photo. */
    fun onPhotoCaptured(photoPath: String) {
        val currentSessionId = sessionId ?: return
        _state.value = KioskState.Printing
        viewModelScope.launch {
            try {
                printPhoto(photoPath)
                val downloadUrl = uploadPhotoToBackend(currentSessionId, photoPath)
                _state.value = KioskState.Download(downloadUrl = downloadUrl)
            } catch (e: Exception) {
                // TODO: show a real error screen instead of silently going back.
                _state.value = KioskState.Idle
            }
        }
    }

    /** Customer is done viewing/scanning the download QR -> reset for next customer. */
    fun reset() {
        pollingJob?.cancel()
        sessionId = null
        _state.value = KioskState.Idle
    }

    // ---- Real backend calls (plain HTTP to the Render server) ----

    private suspend fun requestPaymentQrFromBackend(priceInPesos: Int): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val json = JSONObject().put("amountPesos", priceInPesos)
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BACKEND_BASE_URL/createPaymentSession")
                .post(body)
                .build()
            val result = executeForJson(request)
            result.getString("sessionId") to result.getString("qrImageBase64")
        }

    private suspend fun fetchSessionStatus(sessionId: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BACKEND_BASE_URL/sessionStatus/$sessionId")
                .get()
                .build()
            executeForJson(request).getString("status")
        }

    private suspend fun uploadPhotoToBackend(sessionId: String, photoPath: String): String =
        withContext(Dispatchers.IO) {
            val file = File(photoPath)
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "photo",
                    file.name,
                    file.asRequestBody("image/jpeg".toMediaType())
                )
                .build()
            val request = Request.Builder()
                .url("$BACKEND_BASE_URL/uploadPhoto/$sessionId")
                .post(requestBody)
                .build()
            executeForJson(request).getString("downloadUrl")
        }

    private suspend fun executeForJson(request: Request): JSONObject =
        suspendCancellableCoroutine { cont ->
            httpClient.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            cont.resumeWithException(IOException("HTTP ${it.code}"))
                            return
                        }
                        cont.resume(JSONObject(it.body?.string() ?: "{}"))
                    }
                }
            })
        }

    // ---- Still stubbed: hardware call, not a backend call. ----

    private suspend fun printPhoto(photoPath: String) {
        delay(1500) // TODO: replace with real ESC/POS print job
    }
}
