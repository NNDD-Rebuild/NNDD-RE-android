package com.nndd_re.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "server_prefs")

data class FontEntry(val name: String, val filename: String)

object ServerPreferences {

    private val KEY_HOST = stringPreferencesKey("host")
    private val KEY_PORT = intPreferencesKey("port")
    private val KEY_SKIP_SEC = intPreferencesKey("skip_duration_sec")
    private val KEY_COMMENT_FONT_PATH = stringPreferencesKey("comment_font_path")
    private val KEY_FONT_LIST = stringPreferencesKey("font_list")

    private val gson = Gson()
    private val fontListType = object : TypeToken<List<FontEntry>>() {}.type

    fun hostFlow(ctx: Context): Flow<String> =
        ctx.dataStore.data.map { it[KEY_HOST] ?: "" }

    fun portFlow(ctx: Context): Flow<Int> =
        ctx.dataStore.data.map { it[KEY_PORT] ?: 12345 }

    fun skipDurationFlow(ctx: Context): Flow<Int> =
        ctx.dataStore.data.map { it[KEY_SKIP_SEC] ?: 10 }

    suspend fun save(ctx: Context, host: String, port: Int) {
        ctx.dataStore.edit { prefs ->
            prefs[KEY_HOST] = host
            prefs[KEY_PORT] = port
        }
    }

    suspend fun saveSkipDuration(ctx: Context, seconds: Int) {
        ctx.dataStore.edit { prefs ->
            prefs[KEY_SKIP_SEC] = seconds
        }
    }

    fun commentFontPathFlow(ctx: Context): Flow<String> =
        ctx.dataStore.data.map { it[KEY_COMMENT_FONT_PATH] ?: "" }

    suspend fun saveCommentFontPath(ctx: Context, path: String) {
        ctx.dataStore.edit { prefs ->
            prefs[KEY_COMMENT_FONT_PATH] = path
        }
    }

    fun fontListFlow(ctx: Context): Flow<List<FontEntry>> =
        ctx.dataStore.data.map { prefs ->
            val json = prefs[KEY_FONT_LIST] ?: return@map emptyList()
            runCatching {
                gson.fromJson<List<FontEntry>>(json, fontListType) ?: emptyList()
            }.getOrDefault(emptyList())
        }

    suspend fun saveFontList(ctx: Context, list: List<FontEntry>) {
        ctx.dataStore.edit { prefs ->
            prefs[KEY_FONT_LIST] = gson.toJson(list)
        }
    }
}
