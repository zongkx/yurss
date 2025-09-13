package com.zongkx.yurss

import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.StringReader

// 使用 OkHttp 替换 URL.openStream() 来进行网络请求
class RssService {
    // OkHttp 客户端实例，建议在整个应用中重用以提高效率
    private val httpClient = OkHttpClient.Builder().build()
    private val htmlTagRegex = "<[^>]*>".toRegex()

    suspend fun fetchRssFeed(url: String): List<RssArticle> {
        return withContext(Dispatchers.IO) {
            val articles = mutableListOf<RssArticle>()
            try {
                // 使用 OkHttp 构建请求
                val request = Request.Builder()
                    .url(url)
                    .build()

                // 执行请求并获取响应
                val response: Response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val feedContent = response.body?.string() ?: ""

                    // 使用 StringReader 将内容传递给 Rome
                    if (feedContent.isNotBlank()) {
                        val input = SyndFeedInput()
                        val feed: SyndFeed = input.build(StringReader(feedContent))

                        for (entry in feed.entries) {
                            val originalTitle = entry.title
                            val originalDescription = entry.description?.value ?: ""
                            val originalDescriptionContents =
                                entry.contents?.joinToString(separator = " ") { it.value.toString() } ?: ""
                            val link = entry.link
                            val cleanedTitle = originalTitle.replace(htmlTagRegex, "").trim()
                            val cleanedDescription = originalDescription.replace(htmlTagRegex, "").trim()
                            val content = originalDescriptionContents.replace(htmlTagRegex, "").trim()
                            articles.add(RssArticle(cleanedTitle, link, cleanedDescription, content))
                        }
                    }
                } else {
                    // 如果请求失败，添加错误信息
                    articles.add(RssArticle("HTTP Error: ${response.code}", "", response.message, ""))
                }
            } catch (e: Exception) {
                // 捕获并处理所有异常，包括网络和解析错误
                articles.add(RssArticle("Error: ${e.message}", "", "", ""))
            }
            articles
        }
    }
}