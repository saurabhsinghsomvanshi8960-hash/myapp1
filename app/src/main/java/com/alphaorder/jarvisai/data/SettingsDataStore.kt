package com.alphaorder.jarvisai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "jarvis_settings")

/**
 * Handles persistence of user preferences.
 * - Gemini API key -> stored in EncryptedSharedPreferences (AES-256, Android Keystore backed)
 * - Other light preferences (voice, language, model) -> Jetpack DataStore
 */
class SettingsDataStore(private val context: Context) {

    // ---------- Encrypted storage for API key ----------

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "jarvis_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun getApiKey(): String {
        return encryptedPrefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun clearApiKey() {
        encryptedPrefs.edit().remove(KEY_API_KEY).apply()
    }

    // ---------- DataStore for light preferences ----------

    private object Keys {
        val MODEL = stringPreferencesKey("gemini_model")
        val VOICE_GENDER = stringPreferencesKey("voice_gender")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val geminiModel: Flow<String> = context.dataStore.data.map {
        it[Keys.MODEL] ?: "gemini-2.5-flash"
    }

    val voiceGender: Flow<String> = context.dataStore.data.map {
        it[Keys.VOICE_GENDER] ?: "female"
    }

    val language: Flow<String> = context.dataStore.data.map {
        it[Keys.LANGUAGE] ?: "en-IN"
    }

    suspend fun setGeminiModel(model: String) {
        context.dataStore.edit { it[Keys.MODEL] = model }
    }

    suspend fun setVoiceGender(gender: String) {
        context.dataStore.edit { it[Keys.VOICE_GENDER] = gender }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = lang }
    }

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
    }
}
