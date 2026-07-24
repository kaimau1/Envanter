package com.envanter.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object Settings {
    private val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
    private val GEMINI_MODEL = stringPreferencesKey("gemini_model")
    private val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
    private val NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")

    fun geminiKey(c: Context): Flow<String> = c.dataStore.data.map { it[GEMINI_KEY] ?: "" }
    fun geminiModel(c: Context): Flow<String> = c.dataStore.data.map { it[GEMINI_MODEL] ?: "gemini-2.0-flash" }
    fun syncEnabled(c: Context): Flow<Boolean> = c.dataStore.data.map { it[SYNC_ENABLED] ?: true }
    fun notifEnabled(c: Context): Flow<Boolean> = c.dataStore.data.map { it[NOTIF_ENABLED] ?: true }

    suspend fun setGeminiKey(c: Context, v: String) = c.dataStore.edit { it[GEMINI_KEY] = v }
    suspend fun setGeminiModel(c: Context, v: String) = c.dataStore.edit { it[GEMINI_MODEL] = v }
    suspend fun setSyncEnabled(c: Context, v: Boolean) = c.dataStore.edit { it[SYNC_ENABLED] = v }
    suspend fun setNotifEnabled(c: Context, v: Boolean) = c.dataStore.edit { it[NOTIF_ENABLED] = v }
}
