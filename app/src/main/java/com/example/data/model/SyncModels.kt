package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_tombstones",
    indices = [Index(value = ["entityType", "clientId"], unique = true)]
)
data class SyncTombstoneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityType: String,
    val clientId: String,
    val deletedAt: Long = System.currentTimeMillis()
)
