package com.haseebflix.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED black") }
enum class AccentColor(val label: String) { CRIMSON("Crimson"), EMBER("Ember"), VIOLET("Violet"), OCEAN("Ocean"), FOREST("Forest") }
enum class Resolution(val label: String) {
    AUTO("Adaptive bitrate"), P480("480p"), P720("720p HD"), P1080("1080p Full HD"), P2160("4K Ultra HD")
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val accent: AccentColor = AccentColor.CRIMSON,
    val language: String = "en-US",
    val region: String = "US",
    val hideAdult: Boolean = true,
    val resolution: Resolution = Resolution.AUTO,
    val audioLanguage: String = "Original",
    val wifiOnly: Boolean = false,
    val dataSaver: Boolean = false,
    val newEpisodeAlerts: Boolean = true,
    val watchlistAlerts: Boolean = true,
    val onboardingDone: Boolean = false,
    val genres: Set<Int> = emptySet(),
)

object Options {
    val languages = listOf(
        "en-US" to "English", "es-ES" to "Español", "fr-FR" to "Français", "de-DE" to "Deutsch", "pt-BR" to "Português",
        "ar-SA" to "العربية", "hi-IN" to "हिन्दी", "ja-JP" to "日本語", "ko-KR" to "한국어", "zh-CN" to "中文",
    )
    val regions = listOf(
        "US" to "United States", "GB" to "United Kingdom", "CA" to "Canada", "AU" to "Australia", "DE" to "Germany",
        "FR" to "France", "ES" to "Spain", "BR" to "Brazil", "IN" to "India", "JP" to "Japan", "KR" to "South Korea",
        "AE" to "UAE", "SA" to "Saudi Arabia", "PK" to "Pakistan",
    )
    val audio = listOf("Original", "English", "Español", "Français", "Deutsch", "العربية", "हिन्दी", "日本語", "한국어")
}

object Keys {
    val theme = stringPreferencesKey("theme")
    val dynamic = booleanPreferencesKey("dynamic")
    val accent = stringPreferencesKey("accent")
    val language = stringPreferencesKey("language")
    val region = stringPreferencesKey("region")
    val hideAdult = booleanPreferencesKey("hide_adult")
    val resolution = stringPreferencesKey("resolution")
    val audio = stringPreferencesKey("audio")
    val wifiOnly = booleanPreferencesKey("wifi_only")
    val dataSaver = booleanPreferencesKey("data_saver")
    val epAlerts = booleanPreferencesKey("ep_alerts")
    val watchAlerts = booleanPreferencesKey("watch_alerts")
    val onboarding = booleanPreferencesKey("onboarding_done")
    val genres = stringSetPreferencesKey("genres")
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E = enumValues<E>().firstOrNull { it.name == this } ?: default

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val flow: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            theme = p[Keys.theme].toEnum(d.theme), dynamicColor = p[Keys.dynamic] ?: d.dynamicColor,
            accent = p[Keys.accent].toEnum(d.accent), language = p[Keys.language] ?: d.language,
            region = p[Keys.region] ?: d.region, hideAdult = p[Keys.hideAdult] ?: d.hideAdult,
            resolution = p[Keys.resolution].toEnum(d.resolution),
            audioLanguage = p[Keys.audio] ?: d.audioLanguage, wifiOnly = p[Keys.wifiOnly] ?: d.wifiOnly,
            dataSaver = p[Keys.dataSaver] ?: d.dataSaver, newEpisodeAlerts = p[Keys.epAlerts] ?: d.newEpisodeAlerts,
            watchlistAlerts = p[Keys.watchAlerts] ?: d.watchlistAlerts, onboardingDone = p[Keys.onboarding] ?: d.onboardingDone,
            genres = p[Keys.genres].orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
        )
    }.distinctUntilChanged()

    suspend fun current(): AppSettings = flow.first()
    suspend fun <T> set(key: Preferences.Key<T>, value: T) { store.edit { it[key] = value } }
    suspend fun setGenres(ids: Set<Int>) { store.edit { it[Keys.genres] = ids.map(Int::toString).toSet() } }
}
