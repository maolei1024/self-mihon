package eu.kanade.tachiyomi.data.gorse

import eu.kanade.tachiyomi.extension.all.komga.Komga
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GorseLikeRepositoryTest {
    private val repository = GorseLikeRepository(Json)

    @Test
    fun `preference reducer supports cancellation and direct mutually exclusive switching`() {
        assertEquals(GorsePreference.LIKE, GorsePreference.NONE.toggle(GorsePreference.LIKE))
        assertEquals(GorsePreference.NONE, GorsePreference.LIKE.toggle(GorsePreference.LIKE))
        assertEquals(GorsePreference.DISLIKE, GorsePreference.LIKE.toggle(GorsePreference.DISLIKE))
        assertEquals(GorsePreference.LIKE, GorsePreference.DISLIKE.toggle(GorsePreference.LIKE))
        assertEquals(GorsePreference.NONE, GorsePreference.DISLIKE.toggle(GorsePreference.DISLIKE))
        assertThrows(IllegalArgumentException::class.java) {
            GorsePreference.LIKE.toggle(GorsePreference.NONE)
        }
    }

    @Test
    fun `source hook requests take priority and preserve their authentication headers`() {
        val source = HookSource()
        val manga = manga("https://komga.example/api/v1/series/series-id")

        val status = repository.statusRequest(source, manga)
        val update = repository.updateRequest(source, manga, GorsePreference.DISLIKE)

        assertEquals("https://komga.example/custom/status", status?.url.toString())
        assertEquals("secret", status?.header("X-API-Key"))
        assertEquals("https://komga.example/custom/DISLIKE", update?.url.toString())
        assertEquals("secret", update?.header("X-API-Key"))
    }

    @Test
    fun `unsupported source and read list return no request`() {
        val source = UnsupportedSource()

        assertNull(repository.statusRequest(source, manga("https://komga.example/api/v1/series/series-id")))
        assertNull(repository.statusRequest(source, manga("https://komga.example/api/v1/readlists/read-list-id")))
    }

    @Test
    fun `standard Komga adapter supports exact series and book paths but hides read lists`() {
        val source = Komga()

        val series = repository.statusRequest(source, manga("https://komga.example/api/v1/series/series-id"))
        val book = repository.updateRequest(
            source,
            manga("https://komga.example/api/v1/books/book-id"),
            GorsePreference.LIKE,
        )
        val readList = repository.statusRequest(source, manga("https://komga.example/api/v1/readlists/list-id"))

        assertEquals("https://komga.example/api/v1/gorse/preference/series/series-id", series?.url.toString())
        assertEquals("standard-key", series?.header("X-API-Key"))
        assertEquals("https://komga.example/api/v1/gorse/preference/book/book-id", book?.url.toString())
        assertEquals("PUT", book?.method)
        assertNull(readList)
    }

    @Test
    fun `preference response is parsed structurally using the source client`() = runBlocking {
        val source = HookSource(
            clientOverride = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"seriesId":"series","preference":"DISLIKE"}""".toResponseBody())
                        .build()
                }
                .build(),
        )

        assertEquals(
            GorsePreference.DISLIKE,
            repository.getPreference(source, manga("https://komga.example/api/v1/series/series")),
        )
    }

    @Test
    fun `preference source Basic authenticator produces the retry credentials`() {
        val source = BasicSource()
        val request = repository.statusRequest(source, manga("https://komga.example/api/v1/series/series"))!!
        val unauthorized = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .header("WWW-Authenticate", "Basic realm=\"Komga\"")
            .body(ByteArray(0).toResponseBody())
            .build()

        val retry = source.client.authenticator.authenticate(null, unauthorized)

        assertEquals(Credentials.basic("user", "password"), retry?.header("Authorization"))
    }

    private fun manga(url: String) = SManga.create().apply { this.url = url }
}

private class HookSource(
    private val clientOverride: OkHttpClient? = null,
) : TestHttpSource() {
    override val client: OkHttpClient
        get() = clientOverride ?: super.client

    override fun gorsePreferenceStatusRequest(manga: SManga): Request =
        Request.Builder().url("https://komga.example/custom/status").header("X-API-Key", "secret").build()

    override fun gorsePreferenceUpdateRequest(manga: SManga, preference: String): Request =
        Request.Builder().url("https://komga.example/custom/$preference").header("X-API-Key", "secret").build()
}

private class UnsupportedSource : TestHttpSource()

private class BasicSource : TestHttpSource() {
    override val client = OkHttpClient.Builder()
        .authenticator { _, response ->
            response.request.newBuilder()
                .header("Authorization", Credentials.basic("user", "password"))
                .build()
        }
        .build()

    override fun gorsePreferenceStatusRequest(manga: SManga): Request =
        Request.Builder().url("https://komga.example/preference").build()
}

private abstract class TestHttpSource : HttpSource() {
    override val name = "test"
    override val lang = "en"
    override val baseUrl = "https://komga.example"
    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request = error("unused")
    override fun popularMangaParse(response: Response): MangasPage = error("unused")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = error("unused")
    override fun searchMangaParse(response: Response): MangasPage = error("unused")
    override fun latestUpdatesRequest(page: Int): Request = error("unused")
    override fun latestUpdatesParse(response: Response): MangasPage = error("unused")
    override fun mangaDetailsParse(response: Response): SManga = error("unused")
    override fun chapterListParse(response: Response): List<SChapter> = error("unused")
    override fun chapterPageParse(response: Response): SChapter = error("unused")
    override fun pageListParse(response: Response): List<Page> = error("unused")
    override fun imageUrlParse(response: Response): String = error("unused")
}
