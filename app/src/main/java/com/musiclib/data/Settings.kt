package com.musiclib.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val serverUrl: String,
    val authToken: String,
    /** Stable display name of the selected library, persisted across reinstalls. */
    val selectedLibraryName: String,
    /** Cached id for the selected library on this server. 0 means unresolved. */
    val selectedLibraryId: Long,
    /** When true, downloads only proceed on unmetered (Wi-Fi) networks. */
    val wifiOnlyDownload: Boolean = true,
) {
    /** True once server URL is set AND a library is resolved. */
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && selectedLibraryId > 0
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val SERVER_URL: Preferences.Key<String> = stringPreferencesKey("server_url")
        val AUTH_TOKEN: Preferences.Key<String> = stringPreferencesKey("auth_token")
        val SELECTED_LIBRARY_NAME: Preferences.Key<String> = stringPreferencesKey("selected_library_name")
        val SELECTED_LIBRARY_ID: Preferences.Key<Long> = longPreferencesKey("selected_library_id")
        val WIFI_ONLY_DOWNLOAD: Preferences.Key<Boolean> = booleanPreferencesKey("wifi_only_download")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            serverUrl = prefs[Keys.SERVER_URL].orEmpty(),
            authToken = prefs[Keys.AUTH_TOKEN].orEmpty(),
            selectedLibraryName = prefs[Keys.SELECTED_LIBRARY_NAME].orEmpty(),
            selectedLibraryId = prefs[Keys.SELECTED_LIBRARY_ID] ?: 0L,
            wifiOnlyDownload = prefs[Keys.WIFI_ONLY_DOWNLOAD] ?: true,
        )
    }

    suspend fun save(settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = settings.serverUrl.trim()
            prefs[Keys.AUTH_TOKEN] = settings.authToken.trim()
            prefs[Keys.SELECTED_LIBRARY_NAME] = settings.selectedLibraryName.trim()
            prefs[Keys.SELECTED_LIBRARY_ID] = settings.selectedLibraryId
            prefs[Keys.WIFI_ONLY_DOWNLOAD] = settings.wifiOnlyDownload
        }
    }

    suspend fun setWifiOnlyDownload(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WIFI_ONLY_DOWNLOAD] = enabled
        }
    }

    suspend fun setLibrary(library: Library) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_LIBRARY_NAME] = library.name
            prefs[Keys.SELECTED_LIBRARY_ID] = library.id
        }
    }
}
