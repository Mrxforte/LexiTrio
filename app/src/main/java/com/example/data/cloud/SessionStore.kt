package com.example.data.cloud

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.data.preferences.dataStore
import com.example.domain.model.CloudSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class SessionStore(private val context: Context) {
    private object Keys {
        val token = stringPreferencesKey("cloud_token")
        val email = stringPreferencesKey("cloud_email")
    }

    val session: Flow<CloudSession?> = context.dataStore.data.map { preferences ->
        val token = preferences[Keys.token]
        val email = preferences[Keys.email]
        if (token.isNullOrBlank() || email.isNullOrBlank()) {
            null
        } else {
            CloudSession(email = email, token = token)
        }
    }

    suspend fun save(session: CloudSession) {
        context.dataStore.edit { preferences ->
            preferences[Keys.token] = session.token
            preferences[Keys.email] = session.email
        }
    }

    suspend fun current(): CloudSession? = session.first()

    suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.remove(Keys.token)
            preferences.remove(Keys.email)
        }
    }
}
