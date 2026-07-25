package com.ultrastream.app.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Singleton
class LinkVerifier @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private data class CacheEntry(val result: Boolean, val timestamp: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val TTL_MS = 5 * 60 * 1000L // 5 minutes

    suspend fun verifyLink(url: String): Boolean {
        cache[url]?.let { entry ->
            if (System.currentTimeMillis() - entry.timestamp < TTL_MS) {
                return entry.result
            } else {
                cache.remove(url)
            }
        }
        val result = withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .addHeader("User-Agent", "UltraStream/1.0 (Android)")
                    .addHeader("Referer", "https://ultrastream.app/")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val isValid = response.code in 200..299
                response.close()
                isValid
            } catch (e: Exception) {
                false
            }
        }
        cache[url] = CacheEntry(result, System.currentTimeMillis())
        return result
    }
}
