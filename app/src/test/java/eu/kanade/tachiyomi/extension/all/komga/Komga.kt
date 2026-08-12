package eu.kanade.tachiyomi.extension.all.komga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response

class Komga : HttpSource() {
    override val name = "Komga"
    override val lang = "all"
    override val baseUrl = "https://komga.example"
    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = Headers.Builder().set("X-API-Key", "standard-key")
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
