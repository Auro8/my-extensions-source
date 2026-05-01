package eu.kanade.tachiyomi.extension.en.zlibrary

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

class ZLibrary : HttpSource() {
    override val name = "Z-Library"
    override val baseUrl = "https://z-library.im"
    override val lang = "en"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)
        .add("User-Agent", "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36")

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/popular?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = Jsoup.parse(response.body.string())
        val books = doc.select("z-bookcard, .resItemBox").map { el ->
            SManga.create().apply {
                title = el.selectFirst("h3[slot=title], .title, h3")?.text() ?: el.attr("title")
                author = el.selectFirst("[slot=author], .authors")?.text()
                thumbnail_url = el.selectFirst("img")?.attr("abs:src")
                val href = el.attr("href").ifBlank { el.selectFirst("a")?.attr("href") ?: "" }
                setUrlWithoutDomain(href)
                status = SManga.COMPLETE
            }
        }
        val hasNext = doc.selectFirst("a[rel=next]") != null
        return MangasPage(books, hasNext)
    }

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/s/?order=date&page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/s/?q=${query.trim()}&page=$page", headers)

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = Jsoup.parse(response.body.string())
        return SManga.create().apply {
            title = doc.selectFirst("h1[itemprop=name], .book-title h1")?.text() ?: ""
            author = doc.selectFirst("[itemprop=author], .authors a")?.text()
            thumbnail_url = doc.selectFirst("img.cover, img[itemprop=image]")?.attr("abs:src")
            description = doc.selectFirst(".book-description-content")?.text()
            genre = doc.select("[itemprop=genre], .property_categories a")
                .joinToString(", ") { it.text() }
            status = SManga.COMPLETE
        }
    }

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body.string())
        val title = doc.selectFirst("h1[itemprop=name]")?.text() ?: "Download"
        return listOf(SChapter.create().apply {
            name = title
            setUrlWithoutDomain(response.request.url.toString())
            chapter_number = 1f
        })
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(response.body.string())
        val links = doc.select("a[href*='/dl/'], a.addDownloadedBook")
        if (links.isEmpty()) {
            val cover = doc.selectFirst("img.cover")?.attr("abs:src") ?: ""
            return listOf(Page(0, "", cover))
        }
        return links.mapIndexed { i, el -> Page(i, el.attr("abs:href"), "") }
    }

    override fun imageUrlParse(response: Response) = ""
}
