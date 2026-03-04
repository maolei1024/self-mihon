package eu.kanade.tachiyomi.data.gorse

import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Repository for interacting with the Gorse like API via Komga's anonymous proxy.
 *
 * API endpoints:
 * - GET    /api/v1/gorse/like/anonymous/{seriesId}  → { "liked": true/false }
 * - PUT    /api/v1/gorse/like/anonymous/{seriesId}  → { "success": true }
 * - DELETE /api/v1/gorse/like/anonymous/{seriesId}  → { "success": true }
 */
class GorseLikeRepository(
    private val networkHelper: NetworkHelper = Injekt.get(),
) {

    private val client get() = networkHelper.client

    /**
     * Check if a series is liked.
     * @param baseUrl The Komga server base URL (e.g. "http://localhost:25600")
     * @param seriesId The Komga series ID
     * @return true if the series is liked, false otherwise
     */
    suspend fun isSeriesLiked(baseUrl: String, seriesId: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/gorse/like/anonymous/$seriesId")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext false

        val body = response.body?.string() ?: return@withContext false
        // Parse {"liked": true/false}
        body.contains("\"liked\":true") || body.contains("\"liked\": true")
    }

    /**
     * Send a positive feedback (like) for a series.
     * @param baseUrl The Komga server base URL
     * @param seriesId The Komga series ID
     * @return true if the operation was successful
     */
    suspend fun likeSeries(baseUrl: String, seriesId: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/gorse/like/anonymous/$seriesId")
            .put(ByteArray(0).toRequestBody(null, 0, 0))
            .build()

        client.newCall(request).execute().isSuccessful
    }

    /**
     * Remove positive feedback (unlike) for a series.
     * @param baseUrl The Komga server base URL
     * @param seriesId The Komga series ID
     * @return true if the operation was successful
     */
    suspend fun unlikeSeries(baseUrl: String, seriesId: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/gorse/like/anonymous/$seriesId")
            .delete()
            .build()

        client.newCall(request).execute().isSuccessful
    }
}
