package com.zongkx.yurss


import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 实例化你的 UI 内容组件
        val myToolWindowContent = RssToolWindowContent(project)

        // 使用 ContentFactory 创建一个 Content 实例
        val content = ContentFactory.SERVICE.getInstance()
            .createContent(myToolWindowContent.getContent(), "", false)

        // 将 Content 添加到 ToolWindow 中
        toolWindow.contentManager.addContent(content)
    }
}