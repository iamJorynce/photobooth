package com.example.photobooth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "admin_settings")

data class AdminSettings(
    val priceInPesos: Int = 50,
    val logoPath: String? = null,
    val pin: String = "1234" // TODO: let admin change this later from within Settings
)

class AdminSettingsStore(private val context: Context) {
    private object Keys {
        val PRICE = intPreferencesKey("price_pesos")
        val LOGO_PATH = stringPreferencesKey("logo_path")
        val PIN = stringPreferencesKey("admin_pin")
    }

    val settings: Flow<AdminSettings> = context.dataStore.data.map { prefs ->
        AdminSettings(
            priceInPesos = prefs[Keys.PRICE] ?: 50,
            logoPath = prefs[Keys.LOGO_PATH],
            pin = prefs[Keys.PIN] ?: "1234"
        )
    }

    suspend fun savePrice(pesos: Int) {
        context.dataStore.edit { it[Keys.PRICE] = pesos.coerceIn(20, 1000) } // ₱20 min per PayMongo QRPh
    }

    suspend fun saveLogoPath(path: String) {
        context.dataStore.edit { it[Keys.LOGO_PATH] = path }
    }
}