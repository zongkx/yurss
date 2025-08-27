package com.example.yurss

import com.intellij.openapi.project.Project
import com.intellij.ui.components.*
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.*
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.awt.BorderLayout
import java.awt.Dimension
import java.net.URL
import javax.swing.JButton
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JSplitPane
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.xml.parsers.DocumentBuilderFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import java.awt.EventQueue
import com.intellij.ui.OnePixelSplitter
import com.intellij.ide.util.PropertiesComponent
import javax.swing.JComboBox
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader

// 定义一个数据类来存储 RSS 文章信息
data class RssArticle(
    val title: String,
    val link: String,
    val description: String
)

// 使用 Rome 解析RSS网络请求和解析服务
class RssService {
    // 正则表达式用于匹配并移除所有HTML标签
    private val htmlTagRegex = "<[^>]*>".toRegex()

    suspend fun fetchRssFeed(url: String): List<RssArticle> {
        return withContext(Dispatchers.IO) {
            val articles = mutableListOf<RssArticle>()
            try {
                // 使用Rome库加载和解析RSS源
                val feedUrl = URL(url)
                val input = SyndFeedInput()
                val feed = input.build(XmlReader(feedUrl))

                for (entry in feed.entries) {
                    // 获取原始标题和描述
                    val originalTitle = (entry as? SyndEntry)?.title ?: "No Title"
                    val originalDescription = (entry as? SyndEntry)?.description?.value ?: "No Description"
                    val link = (entry as? SyndEntry)?.link ?: ""

                    // 使用正则表达式移除HTML标签
                    val cleanedTitle = originalTitle.replace(htmlTagRegex, "").trim()
                    val cleanedDescription = originalDescription.replace(htmlTagRegex, "").trim()

                    articles.add(RssArticle(cleanedTitle, link, cleanedDescription))
                }
            } catch (e: Exception) {
                // 处理解析或网络错误
                e.printStackTrace()
            }
            articles
        }
    }
}

// RssToolWindow 的 UI 内容
class RssToolWindowContent(private val project: Project) {

    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val rssService = RssService()
    private val rootNode = DefaultMutableTreeNode("RSS Feeds")
    private val treeModel = DefaultTreeModel(rootNode)
    private val articleTree = Tree(treeModel)
    private val contentArea = JTextArea("文章内容将在此处显示...")
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
                contentArea.text = "文章内容将在此处显示..."
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
                    contentArea.text = "标题: ${article.title}\n链接: ${article.link}\n\n${article.description}"
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

