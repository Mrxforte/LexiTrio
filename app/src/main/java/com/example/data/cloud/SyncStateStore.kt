package com.example.data.cloud

import android.content.Context

/**
 * Persists dictionary sync cursors so a large paged download can resume after
 * the process dies or the device goes offline.
 */
internal class SyncStateStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("lexitrio_sync_state", Context.MODE_PRIVATE)

    val dictionaryCursor: Long
        get() = preferences.getLong(KEY_DICTIONARY_CURSOR, 0L)

    val lastSyncAt: String?
        get() = preferences.getString(KEY_LAST_SYNC_AT, null)

    fun saveDictionaryCursor(cursor: Long) {
        preferences.edit().putLong(KEY_DICTIONARY_CURSOR, cursor).apply()
    }

    fun saveLastSyncAt(timestamp: String?) {
        preferences.edit().apply {
            if (timestamp.isNullOrBlank()) {
                remove(KEY_LAST_SYNC_AT)
            } else {
                putString(KEY_LAST_SYNC_AT, timestamp)
            }
        }.apply()
    }

    private companion object {
        const val KEY_DICTIONARY_CURSOR = "dictionary_cursor"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
    }
}
