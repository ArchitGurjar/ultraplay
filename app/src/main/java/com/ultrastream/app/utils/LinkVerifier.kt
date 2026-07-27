package com.ultrastream.app.utils

import android.net.Uri
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
                // 1. Token Expiry Check
                if (isTokenExpired(url)) return@withContext false

                // 2. Try HEAD request
                val headRequest = Request.Builder()
                    .url(url)
                    .head()
                    .addHeader("User-Agent", "UltraStream/1.0 (Android)")
                    .addHeader("Referer", "https://ultrastream.app/")
                    .build()
                
                val headResponse = okHttpClient.newCall(headRequest).execute()
                if (headResponse.isSuccessful) {
                    headResponse.close()
                    return@withContext true
                }
                headResponse.close()

                // 3. Fallback to GET with Range: bytes=0-0 (Bandwidth Saver)
                val rangeRequest = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Range", "bytes=0-0")
                    .addHeader("User-Agent", "UltraStream/1.0 (Android)")
                    .addHeader("Referer", "https://ultrastream.app/")
                    .build()
                
                val rangeResponse = okHttpClient.newCall(rangeRequest).execute()
                val isValid = rangeResponse.code == 206 || rangeResponse.code == 200
                rangeResponse.close()
                isValid
            } catch (e: Exception) {
                false
            }
        }
        cache[url] = CacheEntry(result, System.currentTimeMillis())
        return result
    }

    private fun isTokenExpired(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val expires = uri.getQueryParameter("expires")?.toLongOrNull()
            if (expires != null) {
                expires < System.currentTimeMillis() / 1000
            } else false
        } catch (e: Exception) {
            false
        }
    }
}

