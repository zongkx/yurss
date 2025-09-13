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

// RssToolWindow 的 UI 内容
class RssToolWindowContent() {

    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val rssService = RssService()

    // 根节点，现在将用于存储 RSS 订阅 URL
    private val rootNode = DefaultMutableTreeNode("RSS")
    private val treeModel = DefaultTreeModel(rootNode)
    private val articleTree = Tree(treeModel)

    private val contentArea = JTextArea("...")
    private val loadingPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val loadingLabel = JBLabel("Loading...")

    // 使用 IconLoader 加载 SVG 图标并设置无边框
    private val addButton = JButton(MyIcons.ADD).apply {
        preferredSize = Dimension(24, 24)
        border = BorderFactory.createEmptyBorder()
        isBorderPainted = false
        isContentAreaFilled = false
        isFocusPainted = false
    }


    private val addUrlTextField = JTextField()
    private val suggestionPopup = JPopupMenu()

    private var allLocalUrls: List<Map<String, String>> = emptyList()

    companion object {
        private const val RSS_URLS_KEY = "my.rss.plugin.urls"
    }

    init {
        // 创建顶部输入面板
        val urlInputPanel = JBPanel<JBPanel<*>>(BorderLayout())
        urlInputPanel.add(addUrlTextField, BorderLayout.CENTER)
        val buttonPanel = JBPanel<JBPanel<*>>()
        buttonPanel.add(addButton)
        urlInputPanel.add(buttonPanel, BorderLayout.EAST)

        // 创建顶层面板来容纳输入面板
        val topPanel = JBPanel<JBPanel<*>>(BorderLayout())
        topPanel.add(urlInputPanel, BorderLayout.NORTH)

        // 加载已保存的URL到树中
        loadSavedRssUrls()

        // 添加一个监听器到“Add”按钮
        addButton.addActionListener {
            val newUrl = addUrlTextField.text
            if (newUrl.isNotBlank()) {
                // 检查是否已存在
                if (!isUrlInTree(newUrl)) {
                    addUrlToTree(newUrl)
                    saveRssUrls()
                    addUrlTextField.text = ""
                }
            }
        }
        // 新增：为 addUrlTextField 添加实时联想搜索监听器
        addUrlTextField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                showSuggestions(addUrlTextField.text)
            }
        })

        // 设置加载面板
        loadingPanel.add(loadingLabel, BorderLayout.CENTER)
        loadingPanel.isVisible = false

        // 配置文章树
        articleTree.addTreeSelectionListener {
            val node = it.path.lastPathComponent as? DefaultMutableTreeNode
            when (val userObject = node?.userObject) {
                is String -> { // 如果选中了一个 URL 节点
                    loadRssFeed(userObject)
                }

                is RssArticle -> { // 如果选中了一篇文章节点
                    // 在事件调度线程上更新UI
                    EventQueue.invokeLater {
                        contentArea.text =
                            "${userObject.title}\n ${userObject.link}\n\n${userObject.description} \n\n${userObject.content}"
                        contentArea.caretPosition = 0 // 滚动到顶部
                    }
                }
            }
        }
        // 设置内容区域
        contentArea.lineWrap = true
        contentArea.wrapStyleWord = true
        contentArea.isEditable = false
        // ***** 确保内容区域没有悬浮显示 *****
        contentArea.toolTipText = null
        // 禁用树的 tooltip
        articleTree.toolTipText = null
        // ***** 调整这里，取消缩进和连接线 *****
        articleTree.showsRootHandles = false
        articleTree.rowHeight = 24 // 可选：设置行高，让列表看起来更整洁
        articleTree.putClientProperty("JTree.lineStyle", "None") // 移除连接线

        // ***** 新增：为树添加右键菜单功能 *****
        articleTree.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = articleTree.getPathForLocation(e.x, e.y)
                    if (path != null) {
                        val node = path.lastPathComponent as? DefaultMutableTreeNode
                        // 确保选中的是顶级 URL 节点
                        if (node != null && node.userObject is String) {
                            val url = node.userObject as String
                            val popupMenu = JPopupMenu()

                            val deleteItem = JMenuItem("DELETE")
                            deleteItem.addActionListener {
                                treeModel.removeNodeFromParent(node)
                                saveRssUrls()
                                contentArea.text = "..."
                            }

                            val refreshItem = JMenuItem("REFRESH")
                            refreshItem.addActionListener {
                                loadRssFeed(url)
                            }

                            popupMenu.add(deleteItem)
                            popupMenu.add(refreshItem)

                            popupMenu.show(e.component, e.x, e.y)
                        }
                    }
                }
            }
        })
        // 使用更现代的 OnePixelSplitter
        val splitter = OnePixelSplitter(false, 0.3f)
        splitter.firstComponent = JBScrollPane(articleTree)
        splitter.secondComponent = JBScrollPane(contentArea)

        mainPanel.preferredSize = Dimension(400, 600)
        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(splitter, BorderLayout.CENTER)
        mainPanel.add(loadingPanel, BorderLayout.SOUTH)

        // 新增：在初始化时加载本地 RSS 文件
        loadLocalRssFile()
    }


    // 新增：加载本地 rss.json 文件
    private fun loadLocalRssFile(): String {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fileName = "rss.json"
                // 通过类加载器获取文件流
                val inputStream = object {}.javaClass.getResourceAsStream("/$fileName")
                    ?: throw IllegalArgumentException("文件未找到: $fileName")
                // 从输入流中读取内容
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
        return ""
    }

    // 新增：显示联想建议的弹出菜单
    // RssToolWindowContent 类内部
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
                    // 点击后，将URL填充到输入框中
                    if (url != null) {
                        addUrlTextField.text = url
                    }
                    suggestionPopup.isVisible = false // 隐藏弹出菜单

                    // ***** 关键修改：重新请求焦点 *****
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

        // 在协程中加载RSS源
        CoroutineScope(Dispatchers.Main).launch {
            try {
                loadingLabel.text = "Loading..."
                val articles = rssService.fetchRssFeed(url)

                // 找到对应的 URL 节点
                val urlNode = findNodeForUrl(url)
                if (urlNode != null) {
                    // 清除旧的文章
                    urlNode.removeAllChildren()
                    // 添加新文章
                    articles.forEach { article ->
                        val articleNode = DefaultMutableTreeNode(article)
                        urlNode.add(articleNode)
                    }
                    treeModel.reload(urlNode)
                    articleTree.expandPath(TreePath(urlNode.path))
                }

                loadingLabel.text = "Loaded successfully!"
            } finally {
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
                if (!isUrlInTree(url)) {
                    addUrlToTree(url)
                }
            }
        }
    }

    private fun saveRssUrls() {
        val properties = PropertiesComponent.getInstance()
        val urls = (0 until rootNode.childCount).joinToString(",") {
            (rootNode.getChildAt(it) as DefaultMutableTreeNode).userObject as String
        }
        properties.setValue(RSS_URLS_KEY, urls)
    }

    private fun isUrlInTree(url: String): Boolean {
        return findNodeForUrl(url) != null
    }

    private fun findNodeForUrl(url: String): DefaultMutableTreeNode? {
        val enumeration = rootNode.children()
        while (enumeration.hasMoreElements()) {
            val node = enumeration.nextElement() as DefaultMutableTreeNode
            if (node.userObject == url) {
                return node
            }
        }
        return null
    }

    private fun addUrlToTree(url: String) {
        val urlNode = DefaultMutableTreeNode(url)
        urlNode.userObject = url // 将 URL 存储为用户对象
        treeModel.insertNodeInto(urlNode, rootNode, rootNode.childCount)
        // ***** 关键修改：获取新节点的路径并展开它 *****
        val path = TreePath(urlNode.path)
        articleTree.expandPath(path)
    }

    fun getContent(): JBPanel<*> {
        return mainPanel
    }
}