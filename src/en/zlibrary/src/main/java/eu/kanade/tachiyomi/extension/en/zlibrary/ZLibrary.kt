package eu.kanade.tachiyomi.extension.en.zlibrary

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

class ZLibrary : HttpSource() {
    override val name = "Z-Library"
    override val baseUrl = "https://z-library.im"
    override val lang = "en"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = Jsoup.parse(response.body.string())
        val books = doc.select("z-bookcard, .resItemBox, .book-item").map { el ->
            SManga.create().apply {
                title = el.selectFirst("h3[slot=title], .title, h3, [slot=title]")?.text()
                    ?: el.attr("title").ifBlank { "Unknown" }
                author = el.selectFirst("[slot=author], .authors, .author")?.text()
                thumbnail_url = el.selectFirst("img[slot=cover], img.cover, img")?.attr("abs:src")
                val href = el.attr("href").ifBlank { el.selectFirst("a")?.attr("href") ?: "" }
                setUrlWithoutDomain(href)
                status = 2
            }
        }
        val hasNext = doc.selectFirst("a[rel=next], .page-next, a:contains(Next)") != null
        return MangasPage(books, hasNext)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/s/?order=date&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/s/?q=${query.trim()}&page=$page", headers)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = Jsoup.parse(response.body.string())
        return SManga.create().apply {
            title = doc.selectFirst("h1[itemprop=name], .book-title h1")?.text() ?: ""
            author = doc.selectFirst("[itemprop=author], .authors a")?.text()
            thumbnail_url = doc.selectFirst("img.cover, img[itemprop=image]")?.attr("abs:src")
            description = doc.selectFirst(".book-description-content")?.text()
            genre = doc.select("[itemprop=genre], .property_categories a")
                .joinToString(", ") { it.text() }
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
        val links = doc.select("a[href*='/dl/'], a.addDownloadedBook, a.btn-primary[href*='download']")
        if (links.isEmpty()) {
            val cover = doc.selectFirst("img.cover")?.attr("abs:src") ?: ""
            return listOf(Page(0, "", cover))
        }
        return links.mapIndexed { i, el -> Page(i, el.attr("abs:href"), "") }
    }

    override fun imageUrlParse(response: Response): String = ""
}
