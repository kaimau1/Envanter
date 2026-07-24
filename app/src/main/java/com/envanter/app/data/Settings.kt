package com.envanter.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode(val label: String) {
    SYSTEM("Sistem"), LIGHT("Açık"), DARK("Koyu")
}

enum class VoiceMode(val label: String) {
    TAP("Dokun"), HOLD("Basılı Tut")
}

object Settings {
    private val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
    private val GEMINI_MODEL = stringPreferencesKey("gemini_model")
    private val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
    private val NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val VOICE_MODE = stringPreferencesKey("voice_mode")

    fun geminiKey(c: Context): Flow<String> = c.dataStore.data.map { it[GEMINI_KEY] ?: "" }
    fun geminiModel(c: Context): Flow<String> = c.dataStore.data.map { it[GEMINI_MODEL] ?: "gemini-2.0-flash" }
    fun syncEnabled(c: Context): Flow<Boolean> = c.dataStore.data.map { it[SYNC_ENABLED] ?: true }
    fun notifEnabled(c: Context): Flow<Boolean> = c.dataStore.data.map { it[NOTIF_ENABLED] ?: true }
    fun themeMode(c: Context): Flow<ThemeMode> = c.dataStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[THEME_MODE] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }
    fun voiceMode(c: Context): Flow<VoiceMode> = c.dataStore.data.map { p ->
        runCatching { VoiceMode.valueOf(p[VOICE_MODE] ?: VoiceMode.TAP.name) }.getOrDefault(VoiceMode.TAP)
    }

    suspend fun setGeminiKey(c: Context, v: String) = c.dataStore.edit { it[GEMINI_KEY] = v }
    suspend fun setGeminiModel(c: Context, v: String) = c.dataStore.edit { it[GEMINI_MODEL] = v }
    suspend fun setSyncEnabled(c: Context, v: Boolean) = c.dataStore.edit { it[SYNC_ENABLED] = v }
    suspend fun setNotifEnabled(c: Context, v: Boolean) = c.dataStore.edit { it[NOTIF_ENABLED] = v }
    suspend fun setThemeMode(c: Context, v: ThemeMode) = c.dataStore.edit { it[THEME_MODE] = v.name }
    suspend fun setVoiceMode(c: Context, v: VoiceMode) = c.dataStore.edit { it[VOICE_MODE] = v.name }
}
