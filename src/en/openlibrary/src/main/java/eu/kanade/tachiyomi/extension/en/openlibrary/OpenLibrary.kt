package eu.kanade.tachiyomi.extension.en.openlibrary

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

class OpenLibrary : HttpSource() {
    override val name = "Open Library"
    override val baseUrl = "https://openlibrary.org"
    override val lang = "en"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/search?q=subject:fiction&sort=editions&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = Jsoup.parse(response.body.string())
        val books = doc.select("li.searchResultItem").map { el ->
            SManga.create().apply {
                title = el.selectFirst("h3.booktitle a, .booktitle a")?.text() ?: ""
                author = el.selectFirst(".bookauthor a")?.text()
                thumbnail_url = el.selectFirst("img.bookcover")?.attr("abs:src")
                setUrlWithoutDomain(el.selectFirst("h3.booktitle a, .booktitle a")?.attr("href") ?: "")
                status = 2
            }
        }
        val hasNext = doc.selectFirst("a.ChoosePage:contains(Next)") != null
        return MangasPage(books, hasNext)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/search?q=subject:fiction&sort=new&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/search?q=${query.trim()}&page=$page", headers)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = Jsoup.parse(response.body.string())
        return SManga.create().apply {
            title = doc.selectFirst("h1[itemprop=name]")?.text() ?: ""
            author = doc.selectFirst("[itemprop=author]")?.text()
            thumbnail_url = doc.selectFirst("img[itemprop=image]")?.attr("abs:src")
            description = doc.selectFirst("#book-description-excerpt, [itemprop=description]")?.text()
            status = 2
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        val title = doc.selectFirst("h1[itemprop=name]")?.text() ?: "Read"
        return listOf(
            SChapter.create().apply {
                name = title
                setUrlWithoutDomain(response.request.url.toString())
                chapter_number = 1f
            },
        )
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(response.body.string())
        val links = doc.select("a.cta-btn--borrow, a[href*='/borrow'], a[href*='archive.org']")
        if (links.isEmpty()) return listOf(Page(0, "", ""))
        return links.mapIndexed { i, el -> Page(i, el.attr("abs:href"), "") }
    }

    override fun imageUrlParse(response: Response): String = ""
}
