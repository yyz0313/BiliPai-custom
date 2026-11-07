package com.android.purebilibili.core.store.home

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.android.purebilibili.core.store.settingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val dynamicCommentPreviewVisiblePreferencesKey =
    booleanPreferencesKey("dynamic_comment_preview_visible")

object DynamicSettingsStore {
    internal fun resolveCommentPreviewVisible(preferences: Preferences): Boolean =
        preferences[dynamicCommentPreviewVisiblePreferencesKey] ?: false

    fun observeCommentPreviewVisible(context: Context): Flow<Boolean> =
        context.settingsDataStore.data
            .map(::resolveCommentPreviewVisible)
            .distinctUntilChanged()

    suspend fun setCommentPreviewVisible(context: Context, visible: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[dynamicCommentPreviewVisiblePreferencesKey] = visible
        }
    }
}
