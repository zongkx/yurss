package com.zongkx.yurss

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.IOException
import java.util.concurrent.TimeUnit

fun main() {
    // 自定义 CookieJar 来管理 Cookie
    // CookieJar 负责存储和加载 Cookie
    val myCookieJar = object : CookieJar {
        private val cookieStore: HashMap<String, MutableList<Cookie>> = HashMap()

        @JvmName("saveFromResponseWithList")
        fun saveFromResponse(url: HttpUrl, cookies: MutableList<Cookie>) {
            // 将从响应中获取的 Cookie 存到内存中
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {
            // 加载请求所需的 Cookie
            val cookies = cookieStore[url.host]
            return cookies ?: ArrayList()
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            TODO("Not yet implemented")
        }
    }

    // 创建 OkHttpClient 实例，并设置自定义的 CookieJar
    val client = OkHttpClient.Builder()
        .cookieJar(myCookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // 目标 URL
    val targetUrl =
        "https://www.qireader.com/api/search?query=%23All&language=zh&lastUpdateDays=30&startIndex=0&limit=25"

    // 创建需要设置的 Cookie
    val authCookie = Cookie.Builder()
        .name("qireader_auth")
        .value("")
        .domain("www.qireader.com")
        .path("/")
        .build()
    // 创建需要设置的第二个 Cookie
    val authSigCookie = Cookie.Builder()
        .name("qireader_auth.sig")
        .value("")
        .domain("www.qireader.com")
        .path("/")
        .build()
    val authSigCookie2 = Cookie.Builder()
        .name("qireader_user_id.sig")
        .value("")
        .domain("www.qireader.com")
        .path("/")
        .build()
    // 手动将 Cookie 添加到 CookieJar 中，以便后续请求使用
    val httpUrl = targetUrl.toHttpUrl()
    myCookieJar.saveFromResponse(httpUrl, mutableListOf(authCookie, authSigCookie, authSigCookie2))

    // 创建 GET 请求
    val request = Request.Builder()
        .url(targetUrl)
        .build()

    // 发送请求并处理响应
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

                val responseBody = response.body?.string()
                println("请求成功，响应体:")
                println(responseBody)
            }
        }
    })
}

