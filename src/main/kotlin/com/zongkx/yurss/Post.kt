package com.zongkx.yurss

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

val list: MutableList<MutableMap<String, String>> = mutableListOf()

fun main() {
    val atomicStart = AtomicInteger(0)
    val limit = 199
    val createCustomCookieJar = createCustomCookieJar()
    val client = OkHttpClient.Builder()
        .cookieJar(createCustomCookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    // 初始请求
    sendRequest(client, createCustomCookieJar, atomicStart.get(), limit, atomicStart)
}

fun cookie(httpUrl: HttpUrl, createCustomCookieJar: CookieJar) {
    // 创建需要设置的 Cookie
    val authCookie = Cookie.Builder()
        .name("qireader_auth")
        .value("basic cGJSZTVrcUpSdnFhbTB2eTpIZU5mYWRHVyE3ZFdEMzNweWw2bHZ2d3V1enhpbUVuQ0lQL3c9")
        .domain("www.qireader.com")
        .path("/")
        .build()
    createCustomCookieJar.saveFromResponse(httpUrl, mutableListOf(authCookie))

}

/**
 * 封装发送请求的逻辑
 *
 * @param client OkHttp客户端
 * @param start 起始页码
 * @param limit 每页限制
 * @param atomicStart 原子整型，用于安全地更新页码
 */
private fun sendRequest(
    client: OkHttpClient,
    createCustomCookieJar: CookieJar,
    start: Int,
    limit: Int,
    atomicStart: AtomicInteger
) {
    val baseUrl = "https://www.qireader.com/api/search"
    val httpUrl = buildUrl(baseUrl, start, limit)
    // 手动将 Cookie 添加到 CookieJar 中，以便后续请求使用

    val request = Request.Builder()
        .url(httpUrl)
        .build()

    println("正在请求 startIndex=$start 的数据...")
    cookie(httpUrl, createCustomCookieJar)
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            e.printStackTrace()
            println("请求失败: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }

                val jsonString = response.body?.string().toString()
                val jsonElement = Json.parseToJsonElement(jsonString)
                val rootObject = jsonElement.jsonObject

                // 3. 安全地导航并提取数据
                val resultObject = rootObject["result"]?.jsonObject
                val feedsArray = resultObject?.get("feeds")?.jsonArray
                val hasNextPage = resultObject?.get("hasNextPage")?.toString().equals("true")

                feedsArray?.forEach { a ->
                    val map: MutableMap<String, String> = mutableMapOf()
                    val feedObject = a.jsonObject["feed"]?.jsonObject
                    val title = feedObject?.get("title")?.toString()?.removeSurrounding("\"")
                    val feedUrl = feedObject?.get("feedUrl")?.toString()?.removeSurrounding("\"")

                    map["title"] = title ?: ""
                    map["feedUrl"] = feedUrl ?: ""
                    list.add(map)
                }

                if (hasNextPage) {
                    // 如果还有下一页，递归调用 sendRequest，并递增页码
                    val nextStart = atomicStart.incrementAndGet() * limit
                    sendRequest(client, createCustomCookieJar, nextStart, limit, atomicStart)
                } else {
                    println("所有数据已获取完毕，退出循环.")
                    println(Json.encodeToString(list))
                }
            }
        }
    })
}

private fun createCustomCookieJar(): CookieJar {
    return object : CookieJar {
        private val cookieStore: HashMap<String, MutableList<Cookie>> = HashMap()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // 将从响应中获取的 Cookie 存到内存中
            cookieStore[url.host] = cookies.toMutableList()
        }

        override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {
            // 加载请求所需的 Cookie
            val cookies = cookieStore[url.host]
            return cookies ?: ArrayList()
        }
    }
}

private fun buildUrl(baseUrl: String, start: Int, limit: Int): HttpUrl {
    return baseUrl.toHttpUrl().newBuilder()
        .addQueryParameter("query", "#All")
        .addQueryParameter("language", "en")
        .addQueryParameter("lastUpdateDays", "30")
        .addQueryParameter("startIndex", start.toString())
        .addQueryParameter("limit", limit.toString())
        .build()
}
