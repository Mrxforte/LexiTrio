package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-word cache bookkeeping. Word rows themselves are never evicted (they are
 * the offline search index); what this table governs is the lazily loaded
 * detail content (translations, examples, forms, collocations, synonyms).
 */
@Entity(
    tableName = "cache_metadata",
    indices = [Index("priority"), Index("lastAccessedAt"), Index("detailValidatedAt")]
)
data class CacheMetadataEntity(
    @PrimaryKey
    val entryId: Long,
    /** 0 = favourites / saved words, 1 = topic (core) words, 2 = accessed, 3 = other. */
    val priority: Int = PRIORITY_ADAPTIVE,
    val lastAccessedAt: Long = 0,
    val accessCount: Int = 0,
    /** When the detail content was last confirmed with the server; 0 = never loaded. */
    val detailValidatedAt: Long = 0,
    /** Server time stamped on the detail response the local copy came from. */
    val serverVersion: String? = null
) {
    companion object {
        const val PRIORITY_USER_DATA = 0
        const val PRIORITY_CORE = 1
        const val PRIORITY_ADAPTIVE = 2
        const val PRIORITY_LAZY = 3
    }
}
