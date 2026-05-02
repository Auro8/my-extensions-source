package eu.kanade.tachiyomi.extension.en.standardebooks

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

class StandardEbooks : HttpSource() {
    override val name = "Standard Ebooks"
    override val baseUrl = "https://standardebooks.org"
    override val lang = "en"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/ebooks/?page=$page&sort=downloads", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = Jsoup.parse(response.body.string())
        val books = doc.select("li[typeof=schema:Book]").map { el ->
            SManga.create().apply {
                title = el.selectFirst("[property='schema:name']")?.text() ?: ""
                author = el.selectFirst("[property='schema:author'] [property='schema:name']")?.text()
                thumbnail_url = el.selectFirst("img")?.attr("abs:src")
                setUrlWithoutDomain(el.selectFirst("a")?.attr("href") ?: "")
                status = 2
            }
        }
        val hasNext = doc.selectFirst("a[rel=next]") != null
        return MangasPage(books, hasNext)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/ebooks/?page=$page&sort=newest", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/ebooks/?query=${query.trim()}&page=$page", headers)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = Jsoup.parse(response.body.string())
        return SManga.create().apply {
            title = doc.selectFirst("h1[property='schema:name']")?.text() ?: ""
            author = doc.selectFirst("[property='schema:author'] [property='schema:name']")?.text()
            thumbnail_url = doc.selectFirst("picture img")?.attr("abs:src")
            description = doc.selectFirst("[property='schema:description']")?.text()
            genre = doc.select("a[property='schema:genre']").joinToString(", ") { it.text() }
            status = 2
        }
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        val title = doc.selectFirst("h1[property='schema:name']")?.text() ?: "Download"
        return listOf(
            SChapter.create().apply {
                name = title
                setUrlWithoutDomain(response.request.url.toString())
                chapter_number = 1f
            },
        )
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(response.body.string())
        val links = doc.select("a[href$='.epub'], a[href$='.azw3'], a[href$='.kepub.epub']")
        if (links.isEmpty()) return listOf(Page(0, "", ""))
        return links.mapIndexed { i, el -> Page(i, el.attr("abs:href"), "") }
    }

    override fun imageUrlParse(response: Response): String = ""
}
