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

    /** True once a full dictionary pass finished and no later pass is still running. */
    val isDictionaryComplete: Boolean
        get() = lastSyncAt != null && !preferences.getBoolean(KEY_PASS_RUNNING, false)

    /** Set while a download/refresh pass is in flight; search then also asks the server. */
    var isPassRunning: Boolean
        get() = preferences.getBoolean(KEY_PASS_RUNNING, false)
        set(value) {
            preferences.edit().putBoolean(KEY_PASS_RUNNING, value).apply()
        }

    /** Server time at which the unfinished download pass began (see DictionaryDownloader). */
    var pendingPassStartedAt: String?
        get() = preferences.getString(KEY_PENDING_PASS_START, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_PENDING_PASS_START) else putString(KEY_PENDING_PASS_START, value)
            }.apply()
        }

    /** The `since` watermark the unfinished pass was started with (null for a first download). */
    var pendingPassSince: String?
        get() = preferences.getString(KEY_PENDING_PASS_SINCE, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_PENDING_PASS_SINCE) else putString(KEY_PENDING_PASS_SINCE, value)
            }.apply()
        }

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
        const val KEY_PENDING_PASS_START = "pending_pass_start"
        const val KEY_PENDING_PASS_SINCE = "pending_pass_since"
        const val KEY_PASS_RUNNING = "pass_running"
    }
}
