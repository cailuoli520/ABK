package com.abk.kernel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "abk_prefs")

class PreferencesRepository(private val context: Context) {

    companion object {
        val KEY_ACCESS_TOKEN = stringPreferencesKey("github_access_token")
        val KEY_USERNAME = stringPreferencesKey("github_username")
        val KEY_AVATAR_URL = stringPreferencesKey("github_avatar_url")
        val KEY_FORK_REPO_NAME = stringPreferencesKey("fork_repo_name")
        val KEY_AUTO_DOWNLOAD = booleanPreferencesKey("auto_download")
        val KEY_NOTIFY_BUILD = booleanPreferencesKey("notify_build")
        val KEY_LAST_RUN_ID = longPreferencesKey("last_run_id")
        val KEY_THEME = stringPreferencesKey("theme_mode") // "system" | "light" | "dark"
        val KEY_BUILD_CONFIG = stringPreferencesKey("build_config_json")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val username: Flow<String?> = context.dataStore.data.map { it[KEY_USERNAME] }
    val avatarUrl: Flow<String?> = context.dataStore.data.map { it[KEY_AVATAR_URL] }
    val forkRepoName: Flow<String?> = context.dataStore.data.map { it[KEY_FORK_REPO_NAME] }
    val autoDownload: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_DOWNLOAD] ?: true }
    val notifyBuild: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFY_BUILD] ?: true }
    val lastRunId: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_RUN_ID] ?: -1L }
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME] ?: "system" }
    val buildConfigJson: Flow<String?> = context.dataStore.data.map { it[KEY_BUILD_CONFIG] }

    suspend fun saveToken(token: String) = context.dataStore.edit { it[KEY_ACCESS_TOKEN] = token }
    suspend fun saveUsername(name: String) = context.dataStore.edit { it[KEY_USERNAME] = name }
    suspend fun saveAvatarUrl(url: String) = context.dataStore.edit { it[KEY_AVATAR_URL] = url }
    suspend fun saveForkRepoName(name: String) = context.dataStore.edit { it[KEY_FORK_REPO_NAME] = name }
    suspend fun setAutoDownload(v: Boolean) = context.dataStore.edit { it[KEY_AUTO_DOWNLOAD] = v }
    suspend fun setNotifyBuild(v: Boolean) = context.dataStore.edit { it[KEY_NOTIFY_BUILD] = v }
    suspend fun saveLastRunId(id: Long) = context.dataStore.edit { it[KEY_LAST_RUN_ID] = id }
    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[KEY_THEME] = mode }
    suspend fun saveBuildConfigJson(json: String) = context.dataStore.edit { it[KEY_BUILD_CONFIG] = json }

    suspend fun clearAuth() = context.dataStore.edit {
        it.remove(KEY_ACCESS_TOKEN)
        it.remove(KEY_USERNAME)
        it.remove(KEY_AVATAR_URL)
        it.remove(KEY_FORK_REPO_NAME)
        it.remove(KEY_LAST_RUN_ID)
    }
}
