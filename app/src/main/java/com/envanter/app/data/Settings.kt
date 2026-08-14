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

/**
 * Videonun Gemini'ye ne yoğunlukta gönderileceği.
 *
 * ÖNEMLİ: Gemini video token'ını dosya boyutundan veya çözünürlükten DEĞİL, videonun
 * SÜRESİNDEN hesaplar. Video saniyede [fps] kare olarak örneklenir; her kare normalde
 * 258, "düşük çözünürlük" modunda 66 token eder. Sesin kendisi ayrıca 32 token/sn'dir.
 * Yani tokendan tasarruf etmenin yolu kısa video çekmek ve kare örnekleme sıklığını
 * düşürmektir; telefonun kayıt çözünürlüğünü düşürmek yalnızca yükleme süresini kısaltır.
 */
enum class VideoQuality(
    val label: String,
    /** Gemini'nin saniyede örnekleyeceği kare sayısı (videoMetadata.fps). */
    val fps: Double,
    /** true ise kareler MEDIA_RESOLUTION_LOW ile 66 token'a düşer (etiket okuma zorlaşır). */
    val lowRes: Boolean
) {
    HIGH("Yüksek", 1.0, false),
    BALANCED("Dengeli", 0.5, false),
    SAVER("Tasarruf", 0.5, true);

    /** Saniye başına yaklaşık token: kare maliyeti + 32 token/sn ses. */
    val tokensPerSecond: Int get() = ((if (lowRes) 66 else 258) * fps).toInt() + 32

    /** [seconds] saniyelik bir video için kabaca beklenen token tüketimi. */
    fun tokensFor(seconds: Int): Int = tokensPerSecond * seconds
}

object Settings {
    private val GEMINI_KEY = stringPreferencesKey("gemini_api_key")
    private val GEMINI_MODEL = stringPreferencesKey("gemini_model")
    private val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
    private val NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val VOICE_MODE = stringPreferencesKey("voice_mode")
    private val VIDEO_QUALITY = stringPreferencesKey("video_quality")
    private val ACTIVE_HOME = stringPreferencesKey("active_home")

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

    fun videoQuality(c: Context): Flow<VideoQuality> = c.dataStore.data.map { p ->
        runCatching { VideoQuality.valueOf(p[VIDEO_QUALITY] ?: VideoQuality.BALANCED.name) }
            .getOrDefault(VideoQuality.BALANCED)
    }

    /** En son bakılan ev; uygulama kapanıp açılınca aynı evle devam eder. */
    fun activeHome(c: Context): Flow<String> = c.dataStore.data.map { it[ACTIVE_HOME] ?: "" }

    suspend fun setActiveHome(c: Context, v: String) = c.dataStore.edit { it[ACTIVE_HOME] = v }

    suspend fun setGeminiKey(c: Context, v: String) = c.dataStore.edit { it[GEMINI_KEY] = v }
    suspend fun setGeminiModel(c: Context, v: String) = c.dataStore.edit { it[GEMINI_MODEL] = v }
    suspend fun setSyncEnabled(c: Context, v: Boolean) = c.dataStore.edit { it[SYNC_ENABLED] = v }
    suspend fun setNotifEnabled(c: Context, v: Boolean) = c.dataStore.edit { it[NOTIF_ENABLED] = v }
    suspend fun setThemeMode(c: Context, v: ThemeMode) = c.dataStore.edit { it[THEME_MODE] = v.name }
    suspend fun setVoiceMode(c: Context, v: VoiceMode) = c.dataStore.edit { it[VOICE_MODE] = v.name }
    suspend fun setVideoQuality(c: Context, v: VideoQuality) = c.dataStore.edit { it[VIDEO_QUALITY] = v.name }
}
