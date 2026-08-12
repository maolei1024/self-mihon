package eu.kanade.tachiyomi.data.gorse

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URI

enum class GorsePreference {
    NONE,
    LIKE,
    DISLIKE,
}

internal fun GorsePreference.toggle(selected: GorsePreference): GorsePreference {
    require(selected != GorsePreference.NONE) { "NONE is not a selectable explicit preference" }
    return if (this == selected) GorsePreference.NONE else selected
}

@Serializable
private data class GorsePreferenceResponse(
    val seriesId: String,
    val preference: GorsePreference,
)

@Serializable
private data class GorsePreferenceUpdate(
    val preference: GorsePreference,
)

class GorseLikeRepository(
    private val json: Json = Injekt.get(),
) {
    suspend fun getPreference(source: HttpSource, manga: SManga): GorsePreference? = withContext(Dispatchers.IO) {
        val request = statusRequest(source, manga) ?: return@withContext null
        source.client.newCall(request).awaitSuccess().use { response ->
            with(json) { response.parseAs<GorsePreferenceResponse>() }.also(::validate).preference
        }
    }

    suspend fun setPreference(
        source: HttpSource,
        manga: SManga,
        preference: GorsePreference,
    ): GorsePreference? = withContext(Dispatchers.IO) {
        val request = updateRequest(source, manga, preference) ?: return@withContext null
        source.client.newCall(request).awaitSuccess().use { response ->
            with(json) { response.parseAs<GorsePreferenceResponse>() }.also(::validate).preference
        }
    }

    internal fun statusRequest(source: HttpSource, manga: SManga): Request? {
        return source.gorsePreferenceStatusRequest(manga)
            ?: standardKomgaRequest(source, manga, preference = null)
    }

    internal fun updateRequest(
        source: HttpSource,
        manga: SManga,
        preference: GorsePreference,
    ): Request? {
        return source.gorsePreferenceUpdateRequest(manga, preference.name)
            ?: standardKomgaRequest(source, manga, preference)
    }

    private fun standardKomgaRequest(
        source: HttpSource,
        manga: SManga,
        preference: GorsePreference?,
    ): Request? {
        if (!source.isStandardKomga()) return null
        val target = preferenceTarget(manga.url) ?: return null
        val serverRoot = source.baseUrl.substringBefore("/api/v1").trimEnd('/')
        val url = "$serverRoot/api/v1/gorse/preference/${target.first}/${target.second}"
        return if (preference == null) {
            GET(url, source.headers)
        } else {
            val body = json.encodeToString(GorsePreferenceUpdate(preference)).toRequestBody(jsonMime)
            PUT(url, source.headers, body)
        }
    }

    private fun preferenceTarget(url: String): Pair<String, String>? {
        val path = runCatching { URI(url).path }.getOrNull() ?: return null
        val segments = path.split('/').filter(String::isNotBlank)
        val apiIndex = segments.indexOfLast { it == "api" }
        if (apiIndex < 0 || segments.drop(apiIndex).size != 4 || segments[apiIndex + 1] != "v1") return null
        val id = segments[apiIndex + 3].takeIf(String::isNotBlank) ?: return null
        return when (segments[apiIndex + 2]) {
            "series" -> "series" to id
            "books" -> "book" to id
            else -> null
        }
    }

    private fun validate(response: GorsePreferenceResponse) {
        require(response.seriesId.isNotBlank()) { "Komga returned an empty Gorse preference seriesId" }
    }

    private companion object {
        const val STANDARD_KOMGA_CLASS = "eu.kanade.tachiyomi.extension.all.komga.Komga"
    }

    private fun HttpSource.isStandardKomga(): Boolean =
        generateSequence(javaClass as Class<*>?) { it.superclass }
            .any { it.name == STANDARD_KOMGA_CLASS }
}
