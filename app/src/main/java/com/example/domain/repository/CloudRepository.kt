package com.example.domain.repository

import com.example.domain.model.CloudSession
import com.example.domain.model.SyncSummary
import kotlinx.coroutines.flow.Flow

interface CloudRepository {
    val session: Flow<CloudSession?>

    suspend fun register(email: String, password: String)

    suspend fun signIn(email: String, password: String)

    suspend fun signOut()

    suspend fun synchronize(): SyncSummary
}
