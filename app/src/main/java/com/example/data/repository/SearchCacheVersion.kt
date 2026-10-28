package com.example.data.repository

import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide stamp mixed into in-memory search cache keys. Whatever writes
 * dictionary rows in the background (download, sync, import) bumps it so a
 * cached result list can never hide words that arrived later.
 */
object SearchCacheVersion {
    private val value = AtomicLong(0)

    val current: Long get() = value.get()

    fun bump() {
        value.incrementAndGet()
    }
}
