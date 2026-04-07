package eu.kanade.tachiyomi.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Serves as a secure wrapper for storing API keys for OCR Translation using EncryptedSharedPreferences.
 */
class SecureOcrPreferences(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "mihon_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(key: String) {
        sharedPreferences.edit().putString(KEY_OCR_API, key).apply()
    }

    fun getApiKey(): String? {
        return sharedPreferences.getString(KEY_OCR_API, null)
    }

    companion object {
        private const val KEY_OCR_API = "ocr_api_key"
    }
}
