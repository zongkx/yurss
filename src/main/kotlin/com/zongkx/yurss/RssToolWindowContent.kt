package com.zongkx.yurss

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.EventQueue
import java.io.StringReader
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

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

// RssToolWindow 的 UI 内容
class RssToolWindowContent(private val project: Project) {

    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val rssService = RssService()
    private val rootNode = DefaultMutableTreeNode("RSS")
    private val treeModel = DefaultTreeModel(rootNode)
    private val articleTree = Tree(treeModel)
    private val contentArea = JTextArea("...")
    private val urlComboBox = JComboBox<String>()
    private val loadingPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val loadingLabel = JBLabel("Loading...")
    private val addButton = JButton("Add")
    private val removeButton = JButton("Remove") // 添加删除按钮
    private val addUrlTextField = JTextField()

    companion object {
        private const val RSS_URLS_KEY = "my.rss.plugin.urls"
    }

    init {
        // 创建顶部输入面板
        val inputPanel = JBPanel<JBPanel<*>>(BorderLayout())
        val urlInputPanel = JBPanel<JBPanel<*>>(BorderLayout())
        urlInputPanel.add(addUrlTextField, BorderLayout.CENTER)
        val buttonPanel = JBPanel<JBPanel<*>>()
        buttonPanel.add(addButton)
        buttonPanel.add(removeButton) // 将按钮添加到面板
        urlInputPanel.add(buttonPanel, BorderLayout.EAST)

        inputPanel.add(JBLabel("RSS URL:"), BorderLayout.NORTH)
        inputPanel.add(urlComboBox, BorderLayout.CENTER)
        inputPanel.add(urlInputPanel, BorderLayout.SOUTH)

        loadSavedRssUrls()

        // 添加一个监听器到“Add”按钮
        addButton.addActionListener {
            val newUrl = addUrlTextField.text
            if (newUrl.isNotBlank() && findUrlInComboBox(newUrl) == -1) {
                urlComboBox.addItem(newUrl)
                saveRssUrls()
                addUrlTextField.text = ""
                // 自动切换到新添加的URL并加载
                urlComboBox.selectedItem = newUrl
            }
        }

        // 添加一个监听器到“Remove”按钮
        removeButton.addActionListener {
            val selectedUrl = urlComboBox.selectedItem as? String
            if (selectedUrl != null) {
                urlComboBox.removeItem(selectedUrl)
                saveRssUrls()
                // 清空内容区
                displayArticles(emptyList())
                contentArea.text = "..."
            }
        }

        // 添加一个监听器到下拉框
        urlComboBox.addActionListener {
            val selectedUrl = urlComboBox.selectedItem as? String
            if (selectedUrl != null && selectedUrl.isNotBlank()) {
                loadRssFeed(selectedUrl)
            }
        }

        // 设置加载面板
        loadingPanel.add(loadingLabel, BorderLayout.CENTER)
        loadingPanel.isVisible = false

        // 配置文章树
        articleTree.addTreeSelectionListener {
            val node = it.path.lastPathComponent as? DefaultMutableTreeNode
            val article = node?.userObject as? RssArticle
            if (article != null) {
                // 在事件调度线程上更新UI
                EventQueue.invokeLater {
                    contentArea.text =
                        "${article.title}\n ${article.link}\n\n${article.description} \n\n${article.content}"
                    contentArea.caretPosition = 0 // 滚动到顶部
                }
            }
        }

        // 设置内容区域
        contentArea.lineWrap = true
        contentArea.wrapStyleWord = true
        contentArea.isEditable = false

        // 使用更现代的 OnePixelSplitter
        val splitter = OnePixelSplitter(false, 0.3f)
        splitter.firstComponent = JBScrollPane(articleTree)
        splitter.secondComponent = JBScrollPane(contentArea)

        mainPanel.preferredSize = Dimension(400, 600)
        mainPanel.add(inputPanel, BorderLayout.NORTH)
        mainPanel.add(splitter, BorderLayout.CENTER)
        mainPanel.add(loadingPanel, BorderLayout.SOUTH)
    }

    private fun loadRssFeed(url: String) {
        loadingPanel.isVisible = true

        // 在协程中加载RSS源
        CoroutineScope(Dispatchers.Main).launch {
            try {
                urlComboBox.isEnabled = false // 禁用下拉框
                loadingLabel.text = "Loading..."
                val articles = rssService.fetchRssFeed(url)
                displayArticles(articles)
                loadingLabel.text = "Loaded successfully!"
            } finally {
                urlComboBox.isEnabled = true
                delay(1500) // 短暂延迟后隐藏
                loadingPanel.isVisible = false
            }
        }
    }

    private fun loadSavedRssUrls() {
        val properties = PropertiesComponent.getInstance()
        val urlsString = properties.getValue(RSS_URLS_KEY, "")
        if (urlsString.isNotBlank()) {
            urlsString.split(",").forEach { url ->
                if (findUrlInComboBox(url) == -1) {
                    urlComboBox.addItem(url)
                }
            }
        }
    }

    private fun saveRssUrls() {
        val properties = PropertiesComponent.getInstance()
        val urls = (0 until urlComboBox.itemCount).map { urlComboBox.getItemAt(it) }.joinToString(",")
        properties.setValue(RSS_URLS_KEY, urls)
    }

    private fun findUrlInComboBox(url: String): Int {
        for (i in 0 until urlComboBox.itemCount) {
            if (urlComboBox.getItemAt(i) == url) {
                return i
            }
        }
        return -1
    }

    private fun displayArticles(articles: List<RssArticle>) {
        // 在事件调度线程上更新UI
        EventQueue.invokeLater {
            rootNode.removeAllChildren()
            articles.forEach { article ->
                val articleNode = DefaultMutableTreeNode(article.title)
                articleNode.userObject = article
                rootNode.add(articleNode)
            }
            treeModel.reload()
            articleTree.expandRow(0) // 自动展开根节点
        }
    }

    fun getContent(): JBPanel<*> {
        return mainPanel
    }
}

