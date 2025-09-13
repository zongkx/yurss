package com.zongkx.yurss

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

// 定义 RssNode 数据类，用于存储 URL 和别名
data class RssNode(var title: String, val feedUrl: String) {
    override fun toString(): String {
        return title
    }
}

// ... (省略 RssArticle 和 RssService 类，因为它们没有变化) ...

// RssToolWindow 的 UI 内容
class RssToolWindowContent() {

    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val rssService = RssService()

    private val rootNode = DefaultMutableTreeNode("RSS")
    private val treeModel = DefaultTreeModel(rootNode)
    private val articleTree = Tree(treeModel)

    private val contentArea = JTextArea("...")
    private val loadingPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val loadingLabel = JBLabel("Loading...")

    private val addButton = JButton(MyIcons.ADD).apply {
        preferredSize = Dimension(24, 24)
        border = BorderFactory.createEmptyBorder()
        isBorderPainted = false
        isContentAreaFilled = false
        isFocusPainted = false
    }

    private val languageButton = JButton(MyIcons.EN).apply {
        preferredSize = Dimension(24, 24)
        border = BorderFactory.createEmptyBorder()
        isBorderPainted = false
        isContentAreaFilled = false
        isFocusPainted = false
    }

    private val addUrlTextField = JTextField()
    private val suggestionPopup = JPopupMenu()

    private var allLocalUrls: List<Map<String, String>> = emptyList()

    private var currentLanguage = "zh"

    companion object {
        private const val RSS_DATA_KEY = "my.rss.plugin.data"
    }

    init {
        val urlInputPanel = JBPanel<JBPanel<*>>(BorderLayout())
        urlInputPanel.add(addUrlTextField, BorderLayout.CENTER)
        val buttonPanel = JBPanel<JBPanel<*>>()
        buttonPanel.add(addButton)
        buttonPanel.add(languageButton)
        urlInputPanel.add(buttonPanel, BorderLayout.EAST)

        val topPanel = JBPanel<JBPanel<*>>(BorderLayout())
        topPanel.add(urlInputPanel, BorderLayout.NORTH)

        languageButton.addActionListener {
            currentLanguage = if (currentLanguage == "zh") "en" else "zh"
            languageButton.icon = if (currentLanguage == "zh") MyIcons.CN else MyIcons.EN
            loadLocalRssFile()
        }

        loadSavedRssUrls()

        addButton.addActionListener {
            val newUrl = addUrlTextField.text
            if (newUrl.isNotBlank()) {
                if (!isUrlInTree(newUrl)) {
                    val title = allLocalUrls.find { it["feedUrl"] == newUrl }?.get("title") ?: newUrl
                    addUrlToTree(title, newUrl)
                    saveRssUrls()
                    addUrlTextField.text = ""
                }
            }
        }

        addUrlTextField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                showSuggestions(addUrlTextField.text)
            }
        })

        loadingPanel.add(loadingLabel, BorderLayout.CENTER)
        loadingPanel.isVisible = false

        articleTree.addTreeSelectionListener {
            val node = it.path.lastPathComponent as? DefaultMutableTreeNode
            when (val userObject = node?.userObject) {
                is RssNode -> {
                    loadRssFeed(userObject.feedUrl)
                }

                is RssArticle -> {
                    EventQueue.invokeLater {
                        contentArea.text =
                            "${userObject.title}\n ${userObject.link}\n\n${userObject.description} \n\n${userObject.content}"
                        contentArea.caretPosition = 0
                    }
                }
            }
        }

        articleTree.toolTipText = null
        articleTree.showsRootHandles = false
        articleTree.rowHeight = 24
        articleTree.putClientProperty("JTree.lineStyle", "None")

        articleTree.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = articleTree.getPathForLocation(e.x, e.y)
                    if (path != null) {
                        val node = path.lastPathComponent as? DefaultMutableTreeNode
                        if (node != null && node.userObject is RssNode) {
                            val rssNode = node.userObject as RssNode
                            val popupMenu = JPopupMenu()

                            val deleteItem = JMenuItem("del")
                            deleteItem.addActionListener {
                                treeModel.removeNodeFromParent(node)
                                saveRssUrls()
                                contentArea.text = "..."
                            }

                            val refreshItem = JMenuItem("refresh")
                            refreshItem.addActionListener {
                                loadRssFeed(rssNode.feedUrl)
                            }

                            val editAliasItem = JMenuItem("rename")
                            editAliasItem.addActionListener {
                                val newTitle = JOptionPane.showInputDialog(
                                    mainPanel,
                                    "name:",
                                    "name",
                                    JOptionPane.QUESTION_MESSAGE,
                                    null,
                                    null,
                                    rssNode.title
                                ) as String?

                                if (!newTitle.isNullOrBlank()) {
                                    rssNode.title = newTitle
                                    treeModel.nodeChanged(node)
                                    saveRssUrls()
                                }
                            }

                            popupMenu.add(editAliasItem)
                            popupMenu.add(deleteItem)
                            popupMenu.add(refreshItem)
                            popupMenu.show(e.component, e.x, e.y)
                        }
                    }
                }
            }
        })

        contentArea.lineWrap = true
        contentArea.wrapStyleWord = true
        contentArea.isEditable = false
        contentArea.toolTipText = null

        val splitter = OnePixelSplitter(false, 0.3f)
        splitter.firstComponent = JBScrollPane(articleTree)
        splitter.secondComponent = JBScrollPane(contentArea)

        mainPanel.preferredSize = Dimension(400, 600)
        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(splitter, BorderLayout.CENTER)
        mainPanel.add(loadingPanel, BorderLayout.SOUTH)

        loadLocalRssFile()
    }


    private fun loadLocalRssFile() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fileName = if (currentLanguage == "en") "rss-en.json" else "rss.json"
                val inputStream = object {}.javaClass.getResourceAsStream("/$fileName")
                    ?: throw IllegalArgumentException("No File: $fileName")
                val content = inputStream.bufferedReader().use { it.readText() }
                val jsonElement = Json.parseToJsonElement(content)
                if (jsonElement is JsonArray) {
                    allLocalUrls = jsonElement.map { it.jsonObject }
                        .map { map ->
                            map.mapValues { (_, value) ->
                                value.toString().trim { it == '"' }
                            }
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showSuggestions(query: String) {
        suggestionPopup.removeAll()
        if (query.isBlank()) {
            suggestionPopup.isVisible = false
            return
        }

        val filteredUrls = allLocalUrls.filter {
            it["title"]?.contains(query, ignoreCase = true) ?: false ||
                    it["feedUrl"]?.contains(query, ignoreCase = true) ?: false
        }.take(10)

        if (filteredUrls.isNotEmpty()) {
            filteredUrls.forEach { urlMap ->
                val title = urlMap["title"]
                val url = urlMap["feedUrl"]
                val item = JMenuItem(title)
                item.addActionListener {
                    if (url != null) {
                        addUrlTextField.text = url
                    }
                    suggestionPopup.isVisible = false
                    addUrlTextField.requestFocusInWindow()
                }
                suggestionPopup.add(item)
            }
            suggestionPopup.show(addUrlTextField, 0, addUrlTextField.height)
        } else {
            suggestionPopup.isVisible = false
        }
    }

    private fun loadRssFeed(url: String) {
        loadingPanel.isVisible = true
        CoroutineScope(Dispatchers.Main).launch {
            try {
                loadingLabel.text = "Loading..."
                val articles = rssService.fetchRssFeed(url)
                val urlNode = findNodeForUrl(url)
                if (urlNode != null) {
                    urlNode.removeAllChildren()
                    articles.forEach { article ->
                        val articleNode = DefaultMutableTreeNode(article)
                        urlNode.add(articleNode)
                    }
                    treeModel.reload(urlNode)
                    articleTree.expandPath(TreePath(urlNode.path))
                }
                loadingLabel.text = "Loaded successfully!"
            } finally {
                delay(1500)
                loadingPanel.isVisible = false
            }
        }
    }

    private fun loadSavedRssUrls() {
        val properties = PropertiesComponent.getInstance()
        val dataString = properties.getValue(RSS_DATA_KEY, "")
        if (dataString.isNotBlank()) {
            val lines = dataString.split("\n")
            lines.forEach { line ->
                val parts = line.split("|", limit = 2)
                if (parts.size == 2) {
                    val url = parts[0]
                    val title = parts[1]
                    if (!isUrlInTree(url)) {
                        addUrlToTree(title, url)
                    }
                }
            }
        }
    }

    private fun saveRssUrls() {
        val properties = PropertiesComponent.getInstance()
        val data = mutableListOf<String>()

        val enumeration = rootNode.children()
        while (enumeration.hasMoreElements()) {
            val node = enumeration.nextElement() as DefaultMutableTreeNode
            val rssNode = node.userObject as RssNode
            val url = rssNode.feedUrl
            val title = rssNode.title
            data.add("$url|$title")
        }
        properties.setValue(RSS_DATA_KEY, data.joinToString("\n"))
    }

    private fun isUrlInTree(url: String): Boolean {
        return findNodeForUrl(url) != null
    }

    private fun findNodeForUrl(url: String): DefaultMutableTreeNode? {
        val enumeration = rootNode.children()
        while (enumeration.hasMoreElements()) {
            val node = enumeration.nextElement() as DefaultMutableTreeNode
            if (node.userObject is RssNode) {
                val rssNode = node.userObject as RssNode
                if (rssNode.feedUrl == url) {
                    return node
                }
            }
        }
        return null
    }

    private fun addUrlToTree(title: String, url: String) {
        val urlNode = DefaultMutableTreeNode(RssNode(title, url))
        treeModel.insertNodeInto(urlNode, rootNode, rootNode.childCount)
        val path = TreePath(urlNode.path)
        articleTree.expandPath(path)
    }

    fun getContent(): JBPanel<*> {
        return mainPanel
    }
}