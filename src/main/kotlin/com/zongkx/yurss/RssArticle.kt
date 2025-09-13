package com.zongkx.yurss

// 定义一个数据类来存储 RSS 文章信息
data class RssArticle(
    val title: String,
    val link: String,
    val description: String,
    val content: String
) {
    override fun toString(): String {
        return title
    }
}

//data class RssNode(
//    val title: String,
//    val feedUrl: String
//) {
//    override fun toString(): String {
//        return title
//    }
//}
