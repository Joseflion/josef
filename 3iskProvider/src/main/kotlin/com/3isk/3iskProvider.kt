package com.3isk

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class CimaNow : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://3isk.biz/"
    override var name = "3isk" 
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = this.attr("href")
        val posterUrl = select("img")?.attr("data-src")
        var title = select("li[aria-label=\"title\"]").html().replace(" <em>.*|\\\\n".toRegex(), "").replace("&nbsp;", "")
        val year = select("li[aria-label=\"year\"]").text().toIntOrNull()
        val tvType = if (url.contains("فيلم|مسرحية|حفلات".toRegex())) TvType.Movie else TvType.TvSeries
        val quality = select("li[aria-label=\"ribbon\"]").first()?.text()?.replace(" |-|1080|720".toRegex(), "")
        val dubEl = select("li[aria-label=\"ribbon\"]:nth-child(2)").isNotEmpty()
        val dubStatus = if(dubEl) select("li[aria-label=\"ribbon\"]:nth-child(2)").text().contains("مدبلج")
        else select("li[aria-label=\"ribbon\"]:nth-child(1)").text().contains("مدبلج")
        if(dubStatus) title = "$title (مدبلج)"
        return MovieSearchResponse(
            "$title ${select("li[aria-label=\"ribbon\"]:contains(الموسم)").text()}",
            url,
            this@CimaNow.name,
            tvType,
            posterUrl,
            year,
            null,
            quality = getQualityFromString(quality)
        )
    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {

        val doc = app.get("$mainUrl/home", headers = mapOf("user-agent" to "MONKE")).document
        val pages = doc.select("section").not("section:contains(أختر وجهتك المفضلة)").not("section:contains(تم اضافته حديثاً)").apmap {
            val name = it.select("span").html().replace("<em>.*| <i c.*".toRegex(), "")
            val list = it.select("a").mapNotNull {
                if(it.attr("href").contains("$mainUrl/category/|$mainUrl/الاكثر-مشاهدة/".toRegex())) return@mapNotNull null
                it.toSearchResponse()
            }
            HomePageList(name, list)
        }
        return HomePageResponse(pages)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val result = arrayListOf<SearchResponse>()
        val doc = app.get("$mainUrl/page/1/?s=$query").document
        val paginationElement = doc.select("ul[aria-label=\"pagination\"]")
        doc.select("section article a").map {
            val postUrl = it.attr("href")
            if(it.select("li[aria-label=\"episode\"]").isNotEmpty()) return@map
            if(postUrl.contains("$mainUrl/expired-download/|$mainUrl/افلام-اون-لاين/".toRegex())) return@map
            result.add(it.toSearchResponse()!!)
        }
        if(paginationElement.isNotEmpty()) {
            val max = paginationElement.select("li").not("li.active").last()?.text()?.toIntOrNull()
            if (max != null) {
                if(max > 5) return result.distinct().sortedBy { it.name }
                (2..max!!).toList().apmap {
                    app.get("$mainUrl/page/$it/?s=$query\"").document.select("section article a").map { element ->
                        val postUrl = element.attr("href")
                        if(element.select("li[aria-label=\"episode\"]").isNotEmpty()) return@map
                        if(postUrl.contains("$mainUrl/expired-download/|$mainUrl/افلام-اون-لاين/".toRegex())) return@map
                        result.add(element.toSearchResponse()!!)
                    }
                }
            }
        }
        return result.distinct().sortedBy { it.name }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val posterUrl = doc.select("body > script:nth-child(3)").html().replace(".*,\"image\":\"|\".*".toRegex(),"").ifEmpty { doc.select("meta[property=\"og:image\"]").attr("content") }
        val year = doc.select("
