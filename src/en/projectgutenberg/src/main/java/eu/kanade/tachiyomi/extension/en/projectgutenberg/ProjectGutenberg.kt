package eu.kanade.tachiyomi.extension.en.projectgutenberg

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

class ProjectGutenberg : HttpSource() {
    override val name = "Project Gutenberg"
    override val baseUrl = "https://www.gutenberg.org"
    override val lang = "en"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ebooks/search/?sort_order=downloads&start_index=${(page - 1) * 25 + 1}", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = Jsoup.parse(response.body.string())
        val books = doc.select("li.booklink").map { el ->
            SManga.create().apply {
                title = el.selectFirst(".title")?.text() ?: ""
                author = el.selectFirst(".subtitle")?.text()
                thumbnail_url = el.selectFirst("img")?.attr("abs:src")
                setUrlWithoutDomain(el.selectFirst("a")?.attr("href") ?: "")
                status = 2
            }
        }
        val hasNext = doc.selectFirst("a[accesskey=+]") != null
        return MangasPage(books, hasNext)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/ebooks/search/?sort_order=release_date&start_index=${(page - 1) * 25 + 1}", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/ebooks/search/?query=${query.trim()}&start_index=${(page - 1) * 25 + 1}", headers)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = Jsoup.parse(response.body.string())
        return SManga.create().apply {
            title = doc.selectFirst("h1[itemprop=name]")?.text() ?: ""
            author = doc.selectFirst("[itemprop=creator] a")?.text()
            thumbnail_url = doc.selectFirst("img.cover-art")?.attr("abs:src")
            description = doc.selectFirst(".subject")?.text()
            status = 2
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        val title = doc.selectFirst("h1[itemprop=name]")?.text() ?: "Download"
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
        val links = doc.select("a[href$='.epub'], a[href$='.pdf'], a[href$='.txt']")
        if (links.isEmpty()) return listOf(Page(0, "", ""))
        return links.mapIndexed { i, el -> Page(i, el.attr("abs:href"), "") }
    }

    override fun imageUrlParse(response: Response): String = ""
}
