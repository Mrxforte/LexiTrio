package com.example

import com.example.data.cloud.CloudApi
import com.example.data.cloud.CredentialsRequest
import com.example.data.cloud.DictionaryDetailsResponse
import com.example.data.cloud.DictionarySearchResponseDto
import com.example.data.cloud.DictionarySyncPage
import com.example.data.cloud.SyncRequest

/** Scriptable CloudApi for tests; every call is a lambda so tests can count or fail them. */
internal class FakeCloudApi(
    private val search: suspend () -> DictionarySearchResponseDto = { DictionarySearchResponseDto() },
    private val sync: suspend (Long, Int, String?, Boolean?) -> DictionarySyncPage = { _, _, _, _ -> DictionarySyncPage() },
    private val details: suspend (String) -> DictionaryDetailsResponse = { DictionaryDetailsResponse() }
) : CloudApi {
    override suspend fun register(request: CredentialsRequest) = TODO()
    override suspend fun signIn(request: CredentialsRequest) = TODO()
    override suspend fun signOut(authorization: String) = TODO()
    override suspend fun getCatalog() = TODO()
    override suspend fun syncDictionary(afterId: Long, limit: Int, since: String?, lite: Boolean?) =
        sync(afterId, limit, since, lite)
    override suspend fun getDictionaryDetails(ids: String) = details(ids)
    override suspend fun searchDictionary(
        query: String,
        sourceLanguage: String?,
        targetLanguage: String?,
        partOfSpeech: String?,
        cefrLevel: String?,
        matchPartial: Boolean?,
        page: Int?,
        pageSize: Int?
    ) = search()
    override suspend fun synchronize(authorization: String, request: SyncRequest) = TODO()
}
