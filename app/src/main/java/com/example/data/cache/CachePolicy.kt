package com.example.data.cache

import kotlin.math.ln
import kotlin.math.pow

/**
 * Tunable cache behaviour. Defaults are conservative; every value can be
 * overridden where the policy is constructed.
 *
 * - [detailTtlMs]: how long downloaded detail content (translations, examples,
 *   forms, collocations, synonyms) is trusted before it is revalidated.
 * - [maxDetailedEntries]: detail content is kept for at most this many words;
 *   the lowest-scored unprotected ones are evicted beyond it.
 * - score = [frequencyWeight] * ln(1 + accesses) + [recencyWeight] * 0.5^(age / [recencyHalfLifeMs])
 */
data class CachePolicy(
    val detailTtlMs: Long = 7L * DAY_MS,
    val maxDetailedEntries: Int = 25_000, // ~30 MB dedicated detail cache (~1.2 KB per word detail)
    val prefetchBatchSize: Int = 50,
    val prefetchMaxRequestsPerRun: Int = 2,
    val candidatePool: Int = 500,
    val recencyHalfLifeMs: Long = 7L * DAY_MS,
    val frequencyWeight: Double = 1.0,
    val recencyWeight: Double = 2.0
) {
    fun score(accessCount: Int, lastAccessedAt: Long, now: Long): Double {
        if (accessCount <= 0 && lastAccessedAt <= 0) return 0.0
        val age = (now - lastAccessedAt).coerceAtLeast(0).toDouble()
        return frequencyWeight * ln(1.0 + accessCount) +
            recencyWeight * 0.5.pow(age / recencyHalfLifeMs)
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
